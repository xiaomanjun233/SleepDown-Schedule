package com.example.courseschedule

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap
import java.util.UUID

private val DayAgentJson = Json { ignoreUnknownKeys = true; isLenient = true }
private const val MaxAgentToolRounds = 6

internal data class AgentToolDecision(
    val assistantMessage: JsonObject,
    val calls: List<AgentToolCall>,
    val reasoning: String,
    val content: String,
    val finishReason: String,
    val unparsedToolCallCount: Int,
    val webSearchUsed: Boolean
)

private fun agentTextMessage(role: String, content: String): JsonObject = buildJsonObject {
    put("role", role)
    put("content", content)
}

private fun agentUserMessage(
    content: String,
    imageAttachment: AgentImageAttachment?
): JsonObject = if (imageAttachment == null) {
    agentTextMessage("user", content)
} else {
    buildJsonObject {
        put("role", "user")
        put("content", buildJsonArray {
            add(buildJsonObject {
                put("type", "text")
                put("text", content)
            })
            add(buildJsonObject {
                put("type", "image_url")
                put("image_url", buildJsonObject {
                    put(
                        "url",
                        "data:${imageAttachment.mimeType};base64,${imageAttachment.base64}"
                    )
                })
            })
        })
    }
}

internal fun parseAgentToolDecision(response: String): AgentToolDecision {
    val root = DayAgentJson.parseToJsonElement(response).jsonObject
    val choice = (root["choices"] as? JsonArray)
        ?.firstOrNull()
        ?.let { it as? JsonObject }
        ?: throw IllegalStateException("AI 没有返回有效选项")
    val message = choice["message"] as? JsonObject
        ?: throw IllegalStateException("AI 没有返回有效消息")
    val annotations = message["annotations"] as? JsonArray
    val webSearchUsage = (root["usage"] as? JsonObject)
        ?.get("web_search_usage") as? JsonObject
    val webSearchUsed =
        annotations?.isNotEmpty() == true ||
            webSearchUsage?.get("tool_usage")?.jsonPrimitive?.intOrNull?.let { it > 0 } == true
    val content = agentTextFromJson(message["content"])
    val reasoning = agentTextFromJson(
        message["reasoning_content"] ?: message["reasoning"]
    )
    val nativeToolCalls = (message["tool_calls"] as? JsonArray).orEmpty()
    val legacyFunctionCall = (message["function_call"] as? JsonObject)?.let { function ->
        buildJsonObject {
            put(
                "id",
                "legacy-${function["name"]?.let(::agentTextFromJson).orEmpty().hashCode().toUInt()}"
            )
            put("type", "function")
            put("function", function)
        }
    }
    val toolCalls = buildList {
        addAll(nativeToolCalls)
        legacyFunctionCall?.let(::add)
    }
    val calls = toolCalls.mapNotNull { element ->
        val call = element as? JsonObject ?: return@mapNotNull null
        val function = call["function"] as? JsonObject ?: return@mapNotNull null
        val name = (function["name"] as? JsonPrimitive)?.contentOrNull
            ?.trim()
            ?.replace('-', '_')
            ?.uppercase()
            ?.let { normalized -> AgentToolName.entries.firstOrNull { it.name == normalized } }
            ?: return@mapNotNull null
        val argumentsElement = function["arguments"]
        val argumentsObject = when (argumentsElement) {
            is JsonObject -> argumentsElement
            is JsonPrimitive -> argumentsElement.contentOrNull?.let { raw ->
                runCatching { DayAgentJson.parseToJsonElement(raw) as? JsonObject }
                    .getOrNull()
            }
            else -> null
        }
        val arguments = argumentsObject
            ?.mapValues { (_, value) ->
                (value as? JsonPrimitive)?.contentOrNull ?: value.toString()
            }
            .orEmpty()
        AgentToolCall(
            id = call["id"]?.jsonPrimitive?.contentOrNull
                ?: "${name.name.lowercase()}-${callsHashSeed(response, name)}",
            name = name,
            arguments = arguments
        )
    }
    val assistantMessage = buildJsonObject {
        put("role", "assistant")
        put("content", content)
        if (reasoning.isNotBlank()) put("reasoning_content", reasoning)
        if (nativeToolCalls.isNotEmpty()) put("tool_calls", JsonArray(nativeToolCalls))
        if (nativeToolCalls.isEmpty() && legacyFunctionCall != null) {
            put("function_call", legacyFunctionCall["function"] ?: buildJsonObject {})
        }
    }
    return AgentToolDecision(
        assistantMessage = assistantMessage,
        calls = calls,
        reasoning = reasoning,
        content = content,
        finishReason = (choice["finish_reason"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
        unparsedToolCallCount = toolCalls.size - calls.size,
        webSearchUsed = webSearchUsed
    )
}

private fun callsHashSeed(response: String, name: AgentToolName): String =
    (31 * response.hashCode() + name.hashCode()).toUInt().toString(16)

private fun String.escapeAgentMemory(): String =
    replace("<", "＜").replace(">", "＞")

object DayAgentPreferences {
    private const val Prefs = "day_agent_preferences"
    private const val MemoryMaxLength = 1200
    private const val MemoryTurnsBeforeUpdate = 3
    private val mutableChanges = MutableStateFlow(0L)
    val changes: Flow<Long> = mutableChanges

    fun hasDecision(context: Context): Boolean = prefs(context).getBoolean("has_decision", false)
    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean("enabled", false)
    fun isDailyAiEnabled(context: Context): Boolean = prefs(context).getBoolean("daily_ai_enabled", true)
    fun isWeatherEnabled(context: Context): Boolean = prefs(context).getBoolean("weather_enabled", true)
    fun isMemoryEnabled(context: Context): Boolean = prefs(context).getBoolean("memory_enabled", false)
    fun memory(context: Context): String = prefs(context).getString("memory", null).orEmpty()

    fun noteConversationTurn(context: Context, date: LocalDate) {
        val today = date.toString()
        val storage = prefs(context)
        val previousDay = storage.getString("memory_turn_day", null)
        val nextCount = if (previousDay == today) {
            storage.getInt("memory_turn_count", 0) + 1
        } else {
            1
        }
        storage.edit()
            .putString("memory_turn_day", today)
            .putInt("memory_turn_count", nextCount)
            .apply()
    }

    fun shouldOfferMemoryUpdate(context: Context, date: LocalDate): Boolean {
        if (!isMemoryEnabled(context)) return false
        val storage = prefs(context)
        val today = date.toString()
        return storage.getString("memory_turn_day", null) == today &&
            storage.getInt("memory_turn_count", 0) >= MemoryTurnsBeforeUpdate &&
            storage.getString("memory_last_agent_update_day", null) != today
    }

    fun setEnabled(context: Context, enabled: Boolean, markDecided: Boolean = true) {
        prefs(context).edit()
            .putBoolean("enabled", enabled)
            .putBoolean("has_decision", markDecided)
            .apply()
        mutableChanges.value += 1
    }

    fun saveOptions(context: Context, dailyAiEnabled: Boolean, weatherEnabled: Boolean) {
        prefs(context).edit()
            .putBoolean("daily_ai_enabled", dailyAiEnabled)
            .putBoolean("weather_enabled", weatherEnabled)
            .apply()
        mutableChanges.value += 1
    }

    fun setMemoryEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit()
            .putBoolean("memory_enabled", enabled)
            .apply()
        mutableChanges.value += 1
    }

    fun saveMemory(context: Context, memory: String) {
        saveMemoryInternal(context, memory)
    }

    fun saveMemoryFromAgent(context: Context, memory: String, date: LocalDate) {
        saveMemoryInternal(context, memory)
        prefs(context).edit()
            .putString("memory_last_agent_update_day", date.toString())
            .apply()
    }

    private fun saveMemoryInternal(context: Context, memory: String) {
        val normalized = memory
            .replace("\r\n", "\n")
            .replace(Regex("[ \t]+"), " ")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
            .take(MemoryMaxLength)
        prefs(context).edit()
            .putString("memory", normalized)
            .apply()
        mutableChanges.value += 1
    }

    fun clearMemory(context: Context) {
        prefs(context).edit().remove("memory").apply()
        mutableChanges.value += 1
    }

    fun getAppliedActions(context: Context, scheduleId: Int): Set<String> {
        return prefs(context).getStringSet("applied_actions_$scheduleId", emptySet()) ?: emptySet()
    }

    fun markActionApplied(context: Context, scheduleId: Int, actionKey: String) {
        val existing = getAppliedActions(context, scheduleId).toMutableSet()
        if (existing.add(actionKey)) {
            prefs(context).edit()
                .putStringSet("applied_actions_$scheduleId", existing)
                .apply()
        }
    }

    fun unmarkActionApplied(context: Context, scheduleId: Int, actionKey: String) {
        val existing = getAppliedActions(context, scheduleId).toMutableSet()
        if (existing.remove(actionKey)) {
            prefs(context).edit()
                .putStringSet("applied_actions_$scheduleId", existing)
                .apply()
        }
    }

    private fun prefs(context: Context) = context.getSharedPreferences(Prefs, Context.MODE_PRIVATE)
}

object DayAgentWeatherStore {
    private const val Prefs = "day_agent_weather"
    private const val CacheMillis = 30 * 60 * 1000L

    fun load(context: Context): AgentWeatherSnapshot? {
        val prefs = context.getSharedPreferences(Prefs, Context.MODE_PRIVATE)
        val fetchedAt = prefs.getLong("fetched_at", 0L)
        if (System.currentTimeMillis() - fetchedAt > CacheMillis) return null
        val summary = prefs.getString("summary", null) ?: return null
        return AgentWeatherSnapshot(
            summary = summary,
            temperature = prefs.getInt("temperature", 0),
            apparentTemperature = prefs.getInt("apparent_temperature", 0),
            precipitationProbability = prefs.getInt("precipitation_probability", 0),
            windSpeed = prefs.getInt("wind_speed", 0),
            fetchedAt = fetchedAt
        )
    }

    fun save(context: Context, weather: AgentWeatherSnapshot) {
        context.getSharedPreferences(Prefs, Context.MODE_PRIVATE).edit()
            .putLong("fetched_at", weather.fetchedAt)
            .putString("summary", weather.summary)
            .putInt("temperature", weather.temperature)
            .putInt("apparent_temperature", weather.apparentTemperature)
            .putInt("precipitation_probability", weather.precipitationProbability)
            .putInt("wind_speed", weather.windSpeed)
            .apply()
    }

}

class DayAgentWeatherRepository(private val context: Context) {
    fun hasLocationPermission(): Boolean = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    suspend fun getWeather(forceRefresh: Boolean = false): AgentWeatherSnapshot? = withContext(Dispatchers.IO) {
        if (!forceRefresh) DayAgentWeatherStore.load(context)?.let { return@withContext it }
        runCatching {
            val location = if (hasLocationPermission()) lastKnownLocation() else null
            val coordinates = location?.let { it.latitude to it.longitude } ?: return@runCatching null
            val weather = fetchOpenMeteo(coordinates.first, coordinates.second) ?: return@runCatching null
            DayAgentWeatherStore.save(context, weather)
            weather
        }.getOrNull()
    }

    @SuppressLint("MissingPermission")
    private fun lastKnownLocation(): Location? {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        return manager.getProviders(true)
            .mapNotNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }
            .maxByOrNull { it.time }
    }

    private fun fetchOpenMeteo(latitude: Double, longitude: Double): AgentWeatherSnapshot? {
        val endpoint = buildString {
            append("https://api.open-meteo.com/v1/forecast?latitude=")
            append(latitude)
            append("&longitude=").append(longitude)
            append("&current=temperature_2m,apparent_temperature,weather_code,wind_speed_10m")
            append("&hourly=precipitation_probability&forecast_days=1&timezone=auto")
        }
        val root = DayAgentJson.parseToJsonElement(httpGet(endpoint)).jsonObject
        val current = root["current"]?.jsonObject ?: return null
        val code = current["weather_code"]?.jsonPrimitive?.intOrNull ?: 0
        val temperature = current["temperature_2m"]?.jsonPrimitive?.content?.toDoubleOrNull()?.toInt() ?: return null
        val apparent = current["apparent_temperature"]?.jsonPrimitive?.content?.toDoubleOrNull()?.toInt() ?: temperature
        val wind = current["wind_speed_10m"]?.jsonPrimitive?.content?.toDoubleOrNull()?.toInt() ?: 0
        val hourly = root["hourly"]?.jsonObject
        val times = hourly?.get("time")?.jsonArray.orEmpty()
        val probabilities = hourly?.get("precipitation_probability")?.jsonArray.orEmpty()
        val currentHour = current["time"]?.jsonPrimitive?.contentOrNull?.take(13)
        val index = times.indexOfFirst { it.jsonPrimitive.contentOrNull?.startsWith(currentHour.orEmpty()) == true }
        val probability = probabilities.getOrNull(index)?.jsonPrimitive?.intOrNull ?: 0
        val summary = "${weatherCodeLabel(code)}，${temperature}°C，体感 ${apparent}°C，降雨概率 ${probability}%"
        return AgentWeatherSnapshot(summary, temperature, apparent, probability, wind, System.currentTimeMillis())
    }
}

class DayAgentService(private val context: Context) {
    suspend fun generateDailyPack(facts: DayAgentFacts): DailyAgentPack = withContext(Dispatchers.IO) {
        val settings = AiImportSettingsStore.load(context)
        require(settings.profile.id != AiProviderPresets.none.id) { "请先在 AI 设置中选择服务商" }
        require(settings.apiKey.isNotBlank()) { "请先在 AI 设置中配置 API Key" }
        val prompt = dailyPackPrompt(facts)
        val body = chatBody(settings, listOf("system" to DayAgentPrompts.DailySystem, "user" to prompt), stream = false)
        val response = postChat(settings, body)
        val content = parseFullChatContent(response)
        val objectText = content.substringAfter('{', missingDelimiterValue = "")
            .let { if (it.isBlank()) "" else "{$it" }
            .substringBeforeLast('}', missingDelimiterValue = "")
            .let { if (it.isBlank()) "" else "$it}" }
        val root = DayAgentJson.parseToJsonElement(objectText).jsonObject
        val templates = root["templates"]?.jsonObject
            ?.mapValues { it.value.jsonPrimitive.content }
            .orEmpty()
        val quickQuestions = root["quickQuestions"]?.jsonArray
            ?.mapNotNull { it.jsonPrimitive.contentOrNull?.trim() }
            ?.filter { it.isNotBlank() && it.length <= 24 }
            ?.distinct()
            ?.take(3)
            .orEmpty()
        val valid = validateAgentTemplates(templates)
        require(valid.isNotEmpty()) { "AI 没有返回可用的文案模板" }
        DailyAgentPack(
            generatedAt = System.currentTimeMillis(),
            providerId = settings.profile.id,
            model = settings.profile.defaultModel,
            sourceHash = facts.sourceHash,
            templates = defaultAgentTemplates() + valid,
            quickQuestions = quickQuestions.takeIf { it.size >= 2 }
                ?: defaultAgentQuickQuestions(facts),
            generationStatus = "READY"
        )
    }

    suspend fun chat(
        facts: DayAgentFacts,
        history: List<AgentMessageEntity>,
        question: String,
        imageAttachment: AgentImageAttachment? = null,
        onStatus: (AgentRunStatus) -> Unit,
        onDelta: (String) -> Unit
    ): String = withContext(Dispatchers.IO) {
        require(facts.scheduleId > 0) { "当前课表尚未就绪" }
        require(facts.semesterCourses.all { it.scheduleId == facts.scheduleId }) {
            "当前课表数据边界异常，请返回首页后重试"
        }
        val settings = AiImportSettingsStore.load(context)
        require(settings.profile.id != AiProviderPresets.none.id) { "请先在 AI 设置中选择服务商" }
        require(settings.apiKey.isNotBlank()) { "请先在 AI 设置中配置 API Key" }
        val miMoWebSearchAvailable = supportsMiMoOfficialWebSearch(
            providerId = settings.profile.id,
            baseUrl = normalizeAiBaseUrlForProvider(
                settings.profile.id,
                settings.profile.baseUrl
            ),
            model = settings.profile.defaultModel
        )
        val memoryEnabled = DayAgentPreferences.isMemoryEnabled(context)
        val savedMemory = DayAgentPreferences.memory(context)
        val memoryToolAvailable = DayAgentPreferences.shouldOfferMemoryUpdate(context, facts.date)
        if (imageAttachment != null) {
            require(settings.profile.supportsVision) {
                "当前模型没有启用图片理解能力，请切换支持视觉输入的模型"
            }
        }
        val messages = mutableListOf<JsonObject>().apply {
            add(agentTextMessage("system", DayAgentPrompts.ChatSystem))
            add(
                agentTextMessage(
                    "system",
                    if (memoryEnabled) {
                        """
                        用户已启用助手记忆。以下是跨天保存的简短长期记忆：
                        <user_memory>
                        ${savedMemory.escapeAgentMemory()}
                        </user_memory>
                        将它视为可被用户当前表达修正的背景，当前消息永远优先。
                        本轮${if (memoryToolAvailable) "允许" else "不允许"}执行自动记忆维护。
                        只有在用户明确表达了长期偏好，或同一稳定偏好经过多轮对话得到确认时，才调用 UPDATE_MEMORY。
                        不要仅因为工具可用就更新，也不要从旧任务、旧提示词或助手自己的推测中提炼新记忆。
                        当前用户消息是判断本轮是否需要更新的首要依据；与当前消息无关的历史请求不得写进记忆。
                        UPDATE_MEMORY 的 memory 必须是完整替换后的简短记忆，而不是增量片段；无变化不要调用。
                        不要保存临时任务、当天课程、一次性安排、聊天复述、API Key、密码或其他敏感凭据。
                        记忆应保持精炼、可编辑，建议不超过 800 个汉字。用户明确要求忘记全部内容时传入空字符串。
                        """.trimIndent()
                    } else {
                        "用户未启用助手记忆。不要声称会跨天记住信息，也不要尝试更新记忆。"
                    }
                )
            )
            add(
                agentTextMessage(
                    "system",
                    if (miMoWebSearchAvailable) {
                        "本轮已向你提供 MiMo 官方 web_search 工具。用户明确要求搜索、" +
                            "查询最新公开事实或核对校方公开安排时，必须真实使用该工具；" +
                            "不要声称没有联网能力。是否搜索由你结合任务自主判断。"
                    } else {
                        "本轮没有提供服务器联网搜索工具。不要伪造搜索结果；本地课程、" +
                            "节次和设置仍应使用已提供的本地函数工具读取。"
                    }
                )
            )
            compactAgentHistory(history).forEach { message ->
                add(
                    agentTextMessage(
                        if (message.role == "assistant") "assistant" else "user",
                        message.content
                    )
                )
            }
            add(agentUserMessage(question, imageAttachment))
        }
        val visibleReasoning = StringBuilder()

        for (round in 0 until MaxAgentToolRounds) {
            onStatus(AgentRunStatus(AgentRunStatusIcon.THINKING, "正在思考"))
            val decisionBody = chatBodyFromObjects(
                settings = settings,
                messages = messages,
                stream = false,
                includeTools = true,
                includeMemoryTool = memoryToolAvailable
            )
            val decision = parseAgentToolDecision(postChat(settings, decisionBody))
            if (decision.webSearchUsed) {
                onStatus(AgentRunStatus(AgentRunStatusIcon.SEARCH, "联网搜索"))
            }
            if (decision.unparsedToolCallCount > 0) {
                throw IllegalStateException(
                    "模型返回了 ${decision.unparsedToolCallCount} 个无法识别的工具调用，请重试"
                )
            }
            decision.reasoning.takeIf(String::isNotBlank)?.let { reasoning ->
                val rendered = "<think>$reasoning</think>"
                visibleReasoning.append(rendered)
                onDelta(rendered)
            }
            if (decision.calls.isEmpty()) {
                if (decision.content.isNotBlank()) {
                    onDelta(decision.content)
                    return@withContext visibleReasoning.toString() + decision.content
                }
                break
            }

            val proposalCalls = decision.calls.filter {
                it.name == AgentToolName.PROPOSE_ACTION_PLAN
            }
            val readCalls = decision.calls.filterNot {
                it.name == AgentToolName.PROPOSE_ACTION_PLAN
            }
            if (proposalCalls.isNotEmpty() && readCalls.isEmpty()) {
                val proposal = proposalCalls.firstNotNullOfOrNull(::renderProposedAgentActionPlan)
                if (proposal != null) {
                    onDelta(proposal)
                    return@withContext visibleReasoning.toString() + proposal
                }
            }

            messages += decision.assistantMessage
            /*
             * Execute and report each function call in the exact order emitted by the model.
             * Do not batch status labels before execution: that made the UI look like a canned
             * workflow and hid repeated calls of the same tool.
             */
            decision.calls.forEach { call ->
                onStatus(call.name.runStatus())
                val result = if (call.name == AgentToolName.PROPOSE_ACTION_PLAN) {
                    AgentToolResult(
                        callId = call.id,
                        name = call.name,
                        success = false,
                        content = if (readCalls.isNotEmpty()) {
                            "本轮仍有事实查询。请先阅读查询结果，下一轮再提交完整操作计划。"
                        } else {
                            "操作计划 JSON 无效，请修正后重新提交。"
                        }
                    )
                } else if (call.name == AgentToolName.UPDATE_MEMORY) {
                    val nextMemory = call.arguments["memory"].orEmpty()
                    if (!DayAgentPreferences.shouldOfferMemoryUpdate(context, facts.date)) {
                        AgentToolResult(
                            callId = call.id,
                            name = call.name,
                            success = false,
                            content = "本日自动记忆已经维护过，或尚未达到低频维护条件；请继续当前任务，不要再次更新记忆。"
                        )
                    } else {
                        DayAgentPreferences.saveMemoryFromAgent(context, nextMemory, facts.date)
                        AgentToolResult(
                            callId = call.id,
                            name = call.name,
                            success = true,
                            content = if (nextMemory.isBlank()) {
                                "助手记忆已清空。"
                            } else {
                                "助手记忆已更新。后续对话会使用这份简短长期记忆。"
                            }
                        )
                    }
                } else {
                    executeAgentReadTools(listOf(call), facts).single()
                }
                messages += result.asAgentToolMessage()
            }
        }

        /*
         * A provider that keeps requesting tools forever is cut off deterministically. The final
         * answer still receives every trusted result gathered in previous rounds, but tools are
         * disabled for this last response so the user never gets stuck in an orchestration loop.
         */
        messages += agentTextMessage(
            "system",
            "工具调用轮次已经结束。请根据已有工具结果直接输出最终答复；" +
                "如需用户确认操作，在正文末尾输出合法的 <agent_actions> 标记。"
        )
        onStatus(AgentRunStatus(AgentRunStatusIcon.THINKING, "整理结果"))
        val finalBody = chatBodyFromObjects(
            settings = settings,
            messages = messages,
            stream = true,
            includeTools = false
        )
        try {
            visibleReasoning.toString() + streamChat(settings, finalBody, onDelta)
        } catch (missing: MissingAgentBodyException) {
            val retryMessages = messages + agentTextMessage(
                "system",
                "上一轮没有生成最终正文。停止继续分析，直接输出简洁结论和必要的操作标记。"
            )
            val retryBody = chatBodyFromObjects(
                settings = settings,
                messages = retryMessages,
                stream = false,
                includeTools = false
            )
            val retryContent = parseFullChatContent(postChat(settings, retryBody))
            onDelta(retryContent)
            visibleReasoning.toString() + missing.renderedReasoning + retryContent
        }
    }

    private fun postChat(settings: AiImportSettings, body: String): String {
        val connection = openChatConnection(settings, body)
        return connection.readResponse()
    }

    private fun streamChat(settings: AiImportSettings, body: String, onDelta: (String) -> Unit): String {
        val connection = openChatConnection(settings, body)
        val code = connection.responseCode
        if (code !in 200..299) throw IllegalStateException("AI 请求失败 ($code)：${connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty().take(300)}")
        val contentType = connection.contentType.orEmpty()
        if (!contentType.contains("text/event-stream", ignoreCase = true)) {
            val content = parseFullChatContent(connection.inputStream.bufferedReader().use { it.readText() })
            onDelta(content)
            return content
        }
        val result = StringBuilder()
        var reasoningOpen = false
        var hasFinalContent = false
        BufferedReader(InputStreamReader(connection.inputStream, Charsets.UTF_8)).useLines { lines ->
            lines.forEach { line ->
                if (!line.startsWith("data:")) return@forEach
                val data = line.removePrefix("data:").trim()
                if (data == "[DONE]" || data.isBlank()) return@forEach
                val parts = runCatching {
                    val choice = DayAgentJson.parseToJsonElement(data)
                        .jsonObject["choices"]
                        ?.jsonArray
                        ?.firstOrNull()
                        ?.jsonObject
                        ?: return@runCatching "" to ""
                    val streamed = choice["delta"]?.jsonObject
                    val content = agentTextFromJson(streamed?.get("content"))
                        .ifBlank { agentTextFromJson(choice["text"]) }
                    val reasoning = agentTextFromJson(
                        streamed?.get("reasoning_content") ?: streamed?.get("reasoning")
                    )
                    reasoning to content
                }.getOrNull() ?: ("" to "")
                val (reasoning, content) = parts
                if (reasoning.isNotEmpty()) {
                    if (!reasoningOpen) {
                        reasoningOpen = true
                        result.append("<think>")
                        onDelta("<think>")
                    }
                    result.append(reasoning)
                    onDelta(reasoning)
                }
                if (content.isNotEmpty()) {
                    if (reasoningOpen) {
                        reasoningOpen = false
                        result.append("</think>")
                        onDelta("</think>")
                    }
                    hasFinalContent = true
                    result.append(content)
                    onDelta(content)
                }
            }
        }
        if (reasoningOpen) {
            result.append("</think>")
            onDelta("</think>")
        }
        if (!hasFinalContent) {
            throw MissingAgentBodyException(renderedReasoning = result.toString())
        }
        return result.toString()
    }

    private fun openChatConnection(settings: AiImportSettings, body: String): HttpURLConnection {
        val base = normalizeAiBaseUrlForProvider(settings.profile.id, settings.profile.baseUrl)
        val path = settings.profile.chatCompletionsPath.ifBlank { "/chat/completions" }
        val connection = URL(base.trimEnd('/') + "/" + path.trimStart('/')).openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.connectTimeout = 30_000
        connection.readTimeout = 120_000
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        if (settings.profile.authType == AiAuthType.CustomHeader) {
            connection.setRequestProperty("api-key", settings.apiKey)
        } else {
            connection.setRequestProperty("Authorization", "Bearer ${settings.apiKey}")
        }
        connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        return connection
    }

    private fun chatBody(
        settings: AiImportSettings,
        messages: List<Pair<String, String>>,
        stream: Boolean
    ): String = buildJsonObject {
        put("model", settings.profile.defaultModel)
        put("stream", stream)
        put("temperature", 0.55)
        put("messages", buildJsonArray {
            messages.forEach { (role, content) ->
                add(buildJsonObject { put("role", role); put("content", content) })
            }
        })
        if (!stream && settings.profile.structuredOutputMode == StructuredOutputMode.JSON_OBJECT) {
            put("response_format", buildJsonObject { put("type", "json_object") })
        }
    }.toString()

    private fun chatBodyFromObjects(
        settings: AiImportSettings,
        messages: List<JsonObject>,
        stream: Boolean,
        includeTools: Boolean,
        includeMemoryTool: Boolean = false
    ): String = buildJsonObject {
        put("model", settings.profile.defaultModel)
        put("stream", stream)
        put("temperature", 0.35)
        put("messages", buildJsonArray {
            messages.forEach(::add)
        })
        if (includeTools) {
            put(
                "tools",
                agentToolDefinitions(
                    includeMiMoWebSearch = supportsMiMoOfficialWebSearch(
                        providerId = settings.profile.id,
                        baseUrl = normalizeAiBaseUrlForProvider(
                            settings.profile.id,
                            settings.profile.baseUrl
                        ),
                        model = settings.profile.defaultModel
                    ),
                    includeMemoryTool = includeMemoryTool
                )
            )
            put("tool_choice", "auto")
        }
    }.toString()
}

private fun AgentToolResult.asAgentToolMessage(): JsonObject = buildJsonObject {
    put("role", "tool")
    put("tool_call_id", callId)
    put("name", name.name)
    put("content", content)
}

class DayAgentRepository(private val context: Context) {
    companion object {
        private val generationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val sessionCache = ConcurrentHashMap<String, AgentDailySessionEntity>()
    }

    private val database = (context.applicationContext as CourseScheduleApp).database
    private val dao = database.agentDao()
    private val scheduleRepository = ScheduleRepository(database)
    private val service = DayAgentService(context.applicationContext)
    private val locks = ConcurrentHashMap<String, Mutex>()

    fun observeSession(scheduleId: Int, date: LocalDate): Flow<AgentDailySessionEntity?> {
        val key = "$scheduleId:$date"
        return dao.observeSession(scheduleId, date.toString())
            .distinctUntilChanged()
            .onEach { session ->
                if (session != null) sessionCache[key] = session
            }
    }

    fun cachedSession(scheduleId: Int, date: LocalDate): AgentDailySessionEntity? = sessionCache["$scheduleId:$date"]

    fun observeMessages(scheduleId: Int, date: LocalDate): Flow<List<AgentMessageEntity>> =
        dao.observeMessages(scheduleId, date.toString()).distinctUntilChanged()

    suspend fun cleanup(today: LocalDate) {
        val oldest = today.minusDays(2).toString()
        dao.deleteMessagesBefore(oldest)
        dao.deleteSessionsBefore(oldest)
    }

    suspend fun ensureDailyPack(scheduleId: Int, facts: DayAgentFacts, force: Boolean = false): Result<DailyAgentPack> {
        val key = "$scheduleId:${facts.date}"
        return generationScope.async {
            locks.getOrPut(key) { Mutex() }.withLock {
                var preservedPackJson: String? = null
                runCatching {
                val settings = AiImportSettingsStore.load(context)
                if (settings.apiKey.isBlank()) return@runCatching DailyAgentPack(sourceHash = facts.sourceHash)
                val existing = daoCurrentSession(scheduleId, facts)
                preservedPackJson = existing?.dailyPackJson
                if (!force && existing != null) return@runCatching DailyAgentPack.decodeOrDefault(existing.dailyPackJson)
                val now = System.currentTimeMillis()
                saveSession(
                    AgentDailySessionEntity(
                        scheduleId = scheduleId,
                        date = facts.date.toString(),
                        dailyPackJson = existing?.dailyPackJson
                            ?: DailyAgentPack(sourceHash = facts.sourceHash, generationStatus = "GENERATING").encode(),
                        providerId = settings.profile.id,
                        model = settings.profile.defaultModel,
                        createdAt = existing?.createdAt ?: now,
                        updatedAt = now,
                        generationStatus = "GENERATING"
                    )
                )
                val pack = service.generateDailyPack(facts)
                saveSession(
                    AgentDailySessionEntity(
                        scheduleId = scheduleId,
                        date = facts.date.toString(),
                        dailyPackJson = pack.encode(),
                        providerId = pack.providerId,
                        model = pack.model,
                        createdAt = existing?.createdAt ?: now,
                        updatedAt = System.currentTimeMillis(),
                        generationStatus = "READY"
                    )
                )
                pack
            }.onFailure { error ->
                val settings = AiImportSettingsStore.load(context)
                val now = System.currentTimeMillis()
                saveSession(
                    AgentDailySessionEntity(
                        scheduleId,
                        facts.date.toString(),
                        preservedPackJson
                            ?: DailyAgentPack(sourceHash = facts.sourceHash, generationStatus = "FAILED", lastError = error.message).encode(),
                        settings.profile.id,
                        settings.profile.defaultModel,
                        now,
                        now,
                        "FAILED",
                        error.message
                    )
                )
                }
            }
        }.await()
    }

    suspend fun sendMessage(
        scheduleId: Int,
        facts: DayAgentFacts,
        question: String,
        imageAttachment: AgentImageAttachment? = null,
        onStatus: (AgentRunStatus) -> Unit,
        onDelta: (String) -> Unit
    ): Result<String> = runCatching {
        require(scheduleId == facts.scheduleId) {
            "课表已切换，请重新发送这条消息"
        }
        cleanup(facts.date)
        val persistedAttachmentName = imageAttachment?.let { attachment ->
            runCatching {
                val directory = File(context.filesDir, "agent_attachments").apply { mkdirs() }
                val extension = when (attachment.mimeType.lowercase()) {
                    "image/png" -> "png"
                    "image/webp" -> "webp"
                    else -> "jpg"
                }
                val fileName = "${UUID.randomUUID()}.$extension"
                File(directory, fileName).writeBytes(
                    android.util.Base64.decode(attachment.base64, android.util.Base64.DEFAULT)
                )
                fileName
            }.getOrNull()
        }
        dao.insertMessage(
            AgentMessageEntity(
                scheduleId = scheduleId,
                sessionDate = facts.date.toString(),
                role = "user",
                content = agentMessageContent(question, persistedAttachmentName),
                createdAt = System.currentTimeMillis(),
                status = "READY"
            )
        )
        DayAgentPreferences.noteConversationTurn(context, facts.date)
        val history = dao.getRecentMessages(scheduleId, facts.date.toString(), 20).reversed().dropLast(1)
        /*
         * UI facts deliberately stay cheap. Rich period-scheme facts are read here, immediately
         * before the model turn, so GET_PERIODS always reflects the active schedule's persisted
         * database state instead of a stale Compose snapshot.
         */
        val currentFacts = runCatching {
            val schemes = scheduleRepository.loadPeriodSchemes(scheduleId)
            facts.copy(
                periodSchemes = schemes.schemes.map { draft ->
                    AgentPeriodSchemeSnapshot(
                        id = draft.scheme.id,
                        name = draft.scheme.name,
                        mode = draft.scheme.mode,
                        isActive = draft.scheme.id == schemes.activeSchemeId,
                        classDurationMinutes = draft.scheme.classDurationMinutes,
                        breakDurationMinutes = draft.scheme.breakDurationMinutes,
                        morningStartTime = draft.scheme.morningStartTime,
                        noonStartTime = draft.scheme.noonStartTime,
                        afternoonStartTime = draft.scheme.afternoonStartTime,
                        eveningStartTime = draft.scheme.eveningStartTime,
                        specialBreaks = draft.specialBreaks,
                        overriddenPeriods = draft.overriddenPeriods,
                        times = draft.times.sortedBy { it.periodIndex }
                    )
                },
                activePeriodSchemeId = schemes.activeSchemeId
            )
        }.getOrElse { facts }
        val answer = service.chat(
            facts = currentFacts,
            history = history,
            question = question,
            imageAttachment = imageAttachment,
            onStatus = onStatus,
            onDelta = onDelta
        )
        val cleanAnswer = sanitizeAgentToolOutput(answer)
        dao.insertMessage(AgentMessageEntity(scheduleId = scheduleId, sessionDate = facts.date.toString(), role = "assistant", content = cleanAnswer, createdAt = System.currentTimeMillis(), status = "READY"))
        cleanAnswer
    }

    private suspend fun daoCurrentSession(scheduleId: Int, facts: DayAgentFacts): AgentDailySessionEntity? {
        return dao.observeSession(
            scheduleId,
            facts.date.toString()
        ).first()
    }

    private suspend fun saveSession(session: AgentDailySessionEntity) {
        sessionCache["${session.scheduleId}:${session.date}"] = session
        dao.upsertSession(session)
    }
}

private fun compactAgentHistory(history: List<AgentMessageEntity>): List<AgentMessageEntity> {
    /*
     * Only the immediately preceding exchange is sent back to the model. The durable memory is
     * already injected separately, so replaying the whole day's prompts only makes an unrelated
     * new request inherit stale goals and parameters.
     */
    return history
        .sortedBy { it.createdAt }
        .takeLast(2)
        .mapNotNull { message ->
            val clean = parseAgentMessageContent(message.content).text
                .replace(
                    Regex(
                        "<think\\s*>[\\s\\S]*?(?:</think\\s*>|$)",
                        RegexOption.IGNORE_CASE
                    ),
                    ""
                )
                .replace(
                    Regex(
                        "<agent_actions\\s*>[\\s\\S]*?(?:</agent_actions\\s*>|$)",
                        RegexOption.IGNORE_CASE
                    ),
                    ""
                )
                .trim()
                .take(1_200)
            clean.takeIf(String::isNotBlank)?.let { message.copy(content = it) }
        }
}

private object DayAgentPrompts {
    const val DailySystem = """你是课程表应用的日程文案助手。你只负责生成简洁、自然的中文文案模板和快捷问题，不计算时间，不编造课程、地点、教师或天气。只返回 JSON 对象，格式为 {\"templates\":{\"MORNING_OVERVIEW\":\"...\"},\"quickQuestions\":[\"...\",\"...\"]}。模板键只能使用请求给出的枚举，占位符只能使用请求给出的白名单。每条文案按“天气与体感；当前或下一节课程；一条可执行建议；一句自然关心”的固定顺序组织，控制在 35 到 100 个汉字。快捷问题生成2至3条，每条不超过12个汉字，必须结合当天课程或空档且适合用户直接点击。"""
    const val ChatSystem = """你是 SleepDown 课程表的任务型智能体，而不是功能菜单或客服。每一条新的用户消息默认视为一个新的当前任务：只有消息中存在明确的指代、追问或承接关系时，才使用最近一轮对话补全含义；若当前消息可以独立理解，必须忽略上一轮的任务目标、参数、操作范围和临时要求，绝不能把旧提示词拼接进新任务。你必须先理解用户想要的最终状态，再自主查询事实、分解目标并组合原子操作完成任务。你已获得一组只读工具，必须根据用户目标自主决定是否调用、调用哪个以及是否继续调用；涉及当前课程、日期、节次、天气或设置的事实时，必须先调用相应本地工具读取最新状态，禁止根据聊天历史或网络内容猜测，也禁止声称自己不能调用工具。需要读取事实时，必须在当前响应中直接发出 OpenAI 原生 function tool_call，并让 content 保持为空；禁止只在正文或思考中写“先查看、准备调用、需要获取”后结束响应。拿到 tool 角色返回的结果后再继续思考，信息不足就继续发出下一轮原生 tool_call，充分后才输出正文。GET_SETTINGS 会返回当前课表和应用的完整可读设置快照，GET_PERIODS 会返回当前课表的节次拓扑、当前物化时间以及所有作息方案；只要工具已返回字段，就必须直接据此回答，不得再说“工具数据有限”或把查询降级成打开页面。如果当前请求涉及新闻、政策、公开资料或其他可能变化的外部信息，并且网络搜索工具可用，你可以自主决定是否搜索；网络搜索只能补充公开外部事实，绝不能替代本地课表、节次和设置工具。每次拿到工具结果后先判断信息是否充分：不足则继续调用其他工具或向用户澄清，充分后再输出最终答复或完整操作计划。工具只能读取当前正在使用的课表，工具结果是唯一可信的本地事实。工具返回的数据库课程记录不等同于用户视角下的课程门数；同名记录通常是同一门课程在教师、地点、时间、周次或单双周上的不同安排，回答时应由你理解并自然归并，不要机械重复，同时不能丢失确有差异的安排。信息不足或存在多个候选对象时简洁询问用户，不要自行猜测。不要向用户展示工具原文、字段清单、协议、能力列表或“让我查看/正在调用”等过程旁白，应用会单独展示思考和工具状态；最终只输出整理后的自然语言结论及必要操作。
你可以准备课程操作和设置跳转，但绝不能声称已经执行。读取工具只负责提供事实，不负责定义或限制你的写入能力。事实充分后，把完整修改 JSON 放在正文末尾唯一的 <agent_actions>[...]</agent_actions> 标记中交给应用确认。下面的操作是可自由组合的规划原语，不是彼此孤立的功能：用户目标不必与某一个操作一一对应。没有同名的专用操作时，必须先推导目标状态，再用若干新增、修改和删除组成一个完整计划；不得仅以“没有合适工具/协议不直接支持”为由拒绝。替换、合并、拆分、交换、批量调整等目标都应使用现有原语表达，并放在同一个 JSON 数组中，由应用统一预演、确认、事务执行和验证。例如，把多条记录归并为一条时，应保留并合并用户要求的有效信息，删除被替代的真实记录并新增目标记录，而不是要求存在 MERGE_COURSE。修改普通设置时，先调用 GET_SETTINGS 获取合法键和当前值，再提交规范化 SET_SETTING。修改节次数量、四个时段分配、自动匹配参数、时段起点、特殊课间或完整逐节时间时，先调用 GET_PERIODS 和 GET_SETTINGS，然后提交一个 SET_PERIOD_SETTINGS，其 periodSettings 直接描述完整目标 JSON；不要把这类请求降级成打开设置页。只有工具事实为空、对象不明确或目标状态本身有歧义时才向用户澄清。
在生成任何修改计划前，必须先在内部完成一次“依赖与迁移自检”，这是一项由你根据目标和工具事实主动完成的规划步骤，不是要求应用用关键词规则替你判断。先比较修改前后的语义基础，识别哪些现有数据依赖将被改变的结构，例如课程对节次编号的引用、作息方案中的逐节时间、特殊课间、周次范围以及与课程时间相关的设置；再判断用户真正希望保持的是原编号、原实际日期时间、原课程顺序，还是新的结构含义。只要结构变化可能让旧引用改变含义，就必须把必要的数据迁移一并纳入同一个操作计划，不能只修改设置本身。
尤其在修改节次数量、逐节时间、时段分配或作息方案时，应先用 GET_PERIODS 取得旧节次的真实起止时间，并按需读取当前课表课程。对于原来绑定旧节次的课程，先推演修改后继续保留原节次编号是否仍符合用户目标；若不符合，应依据课程修改前的实际授课时间、连续时长和时段归属，推导它在新时间线中的目标节次，并同时生成相应的课程迁移动作。不要把“第几节”天然视为永远不变，也不要未经判断就把所有课程机械重编号。若新时间线无法唯一承接原课程、会截断连续课程或存在多个合理映射，必须先向用户说明受影响对象并澄清选择。
提交计划前再做一次完整性复核：确认所有受影响记录都被考虑且至多迁移一次，没有越界节次、遗漏课程、意外扩大周次范围、重复记录或未经说明的新撞课；确认迁移后的实际时间和用户目标一致，并确认所有相互依赖的 SET_PERIOD_SETTINGS、UPDATE_COURSE、ADD_COURSE 与 DELETE_COURSE 已放进同一个 <agent_actions> 数组供应用统一确认。应用的本地预演只会返回修改差异、影响范围和冲突等客观事实，不会替你猜测冲突是否符合用户意图，也不会仅因发现冲突自动否决完整计划；是否应保留、修正或向用户澄清，由你结合目标和事实自检决定。正文只需向用户概括迁移原因、影响范围和需要确认的关键变化，不要泄露内部思维过程；数据库结构合法性、当前课表边界和执行后回读验证仍由应用保证，不能提前声称修改成功。
可组合的操作原语：
1. 新增：{\"type\":\"ADD_COURSE\",\"scope\":\"ALL_WEEKS\",\"course\":{\"name\":\"课程名\",\"teacher\":null,\"location\":null,\"weekday\":1,\"periods\":[1,2],\"weeks\":[1,2],\"weekParity\":\"ALL\",\"note\":null},\"summary\":\"添加课程\"}
2. 修改或移动：{\"type\":\"UPDATE_COURSE\",\"courseId\":123,\"scope\":\"CURRENT_WEEK\",\"course\":{\"weekday\":2,\"periods\":[3,4]},\"summary\":\"移动课程\"}
3. 删除：{\"type\":\"DELETE_COURSE\",\"courseId\":123,\"scope\":\"CURRENT_WEEK\",\"summary\":\"删除课程\"}
4. 打开设置：{\"type\":\"OPEN_SETTINGS\",\"settingsPage\":\"SCHEDULE\",\"summary\":\"打开课表设置\"}
5. 修改设置：{\"type\":\"SET_SETTING\",\"settingKey\":\"REALTIME_ACTIVITY\",\"settingValue\":\"TRUE\",\"summary\":\"开启实时活动\"}
6. 修改节次与作息：{\"type\":\"SET_PERIOD_SETTINGS\",\"periodSettings\":{\"mode\":\"AUTO_MATCH\",\"morningPeriodCount\":4,\"noonPeriodCount\":2,\"afternoonPeriodCount\":4,\"eveningPeriodCount\":2,\"classDurationMinutes\":45,\"breakDurationMinutes\":10,\"morningStartTime\":\"08:00\",\"noonStartTime\":\"12:10\",\"afternoonStartTime\":\"14:00\",\"eveningStartTime\":\"19:20\",\"specialBreaks\":{\"2\":20}},\"summary\":\"调整当前课表节次与作息\"}
交换两门课程必须输出两条 UPDATE_COURSE。courseId 只能使用只读工具刚刚返回的真实ID。scope 可为 CURRENT_WEEK 或 ALL_WEEKS。星期一为1、星期日为7。
设置目录：GENERAL=通用与深色模式；PERSONALIZATION=首页个性化弹窗（壁纸、玻璃、课程卡片外观、字体和行高）；AI_IMPORT=模型与API；DAY_AGENT=今日助手；SCHEDULE=周数、开学日期、节次；NOTIFICATIONS=课程提醒、提前分钟、通知样式、实时活动、实时活动缩略文字、保活权限与测试；SCHEDULE_MANAGER=多课表；ABOUT/CHANGELOG/DOWNLOAD/DONATE=关于、日志、更新、捐赠。
GET_SETTINGS 返回可修改设置的完整键、类型、范围和当前值。这里列出的 ADD/UPDATE/DELETE/SET_SETTING/SET_PERIOD_SETTINGS 是通用 JSON 写入原语，不是“每个功能一把工具”的能力白名单；模型负责产生目标状态 JSON，应用负责预演、确认、事务执行、回读验证与撤销。用户说“打开/开启实时活动”时使用 SET_SETTING，而用户问“在哪里/怎么设置”时使用 OPEN_SETTINGS 指向 NOTIFICATIONS。若只是回答问题，不输出机器标记。只要回复中提出了一个可供用户确认的实际操作，就必须同时输出机器标记，不能只在自然语言里声称“已准备”“请确认”。机器标记必须严格位于正文末尾，只包含使用英文双引号的合法 JSON 数组，不加 Markdown 代码围栏、注释或尾随逗号；type、scope、weekParity 和字段名必须与上述协议完全一致。"""
}

private fun dailyPackPrompt(facts: DayAgentFacts): String = buildString {
    appendLine("请生成今天不同时间段使用的文案模板。")
    appendLine("每条模板必须依次包含天气或体感、当前/下一节课程状态、具体建议和一句自然关心；无课程时明确写无课再给建议。不同模板尽量使用不同关怀角度。")
    appendLine("模板键：${AgentTemplateKind.entries.joinToString { it.name }}")
    appendLine("占位符白名单：${AgentAllowedPlaceholders.joinToString { "{{$it}}" }}")
    appendLine("另生成2至3条适合此刻直接点击的快捷问题，写入 quickQuestions 数组。")
    appendLine(conversationContext(facts))
}

private fun conversationContext(facts: DayAgentFacts): String = buildString {
    val weekday = weekdayLabel(facts.date.dayOfWeek.toChineseWeekday())
    val tomorrowDate = facts.date.plusDays(1)
    val tomorrowWeekday = weekdayLabel(tomorrowDate.dayOfWeek.toChineseWeekday())
    appendLine("本地日期与星期：今天是 ${facts.date} 星期$weekday；明天是 $tomorrowDate 星期$tomorrowWeekday；当前时间：${facts.now.toLocalTime()}")
    appendLine("天气：${facts.weather?.summary ?: "不可用"}")
    appendLine("今日课程：${facts.today.joinToString("；") { "${it.start}-${it.end} ${it.course.name}，地点 ${it.course.location ?: "待确认"}，教师 ${it.course.teacher ?: "待确认"}" }.ifBlank { "无" }}")
    appendLine("明日课程：${facts.tomorrow.joinToString("；") { "${it.start}-${it.end} ${it.course.name}，地点 ${it.course.location ?: "待确认"}" }.ifBlank { "无" }}")
    appendLine("课表ID：${facts.scheduleId}；当前教学周：第${facts.currentWeek}周；本学期总周数：${facts.totalWeeks}")
    appendLine("节次定义：${facts.periodDefinitions.joinToString("；") { "第${it.periodIndex}节 ${it.startTime}-${it.endTime}" }.ifBlank { "不可用" }}")
    appendLine("本周课程（这是发送请求时重新读取的最新数据）：${facts.week.joinToString("；") { "课程ID ${it.course.id}，${it.date.dayOfWeek} ${it.start}-${it.end} ${it.course.name}，节次 ${it.course.periods.joinToString(",")}，周次 ${it.course.weeks.joinToString(",")}，地点 ${it.course.location ?: "待确认"}，教师 ${it.course.teacher ?: "待确认"}" }.ifBlank { "无" }}")
    appendLine("当前应用设置：${facts.settingSnapshot.entries.joinToString("；") { "${it.key}=${it.value}" }}")
}

internal fun needsSemesterCourseContext(question: String): Boolean {
    val normalized = question.lowercase()
    val explicitSemesterIntent = listOf(
        "整个学期", "全学期", "本学期", "这学期", "所有课程", "全部课程",
        "课程总览", "学期安排", "哪几周", "哪一周", "最忙的周", "学期课表",
        "semester", "all courses"
    ).any(normalized::contains)
    if (explicitSemesterIntent) return true

    val isNearTermQuestion = listOf(
        "今天", "今日", "明天", "明日", "后天", "本周", "这周", "下周",
        "周一", "周二", "周三", "周四", "周五", "周六", "周日", "星期"
    ).any(normalized::contains)
    if (isNearTermQuestion) return false

    return listOf(
        "哪些课", "哪几门课", "有什么课", "有几门课", "课程列表"
    ).any(normalized::contains)
}

private fun semesterCourseContext(facts: DayAgentFacts): String = buildString {
    appendLine("用户的问题涉及整个学期，已临时授权读取当前课表的全学期课程。以下数据只用于本次回答：")
    appendLine("当前课表ID：${facts.scheduleId}；总周数：${facts.totalWeeks}；课程记录数：${facts.semesterCourses.size}")
    facts.semesterCourses.forEach { course ->
        append("课程ID ${course.id}；名称 ${course.name}；星期${course.weekday}；节次 ${course.periods.joinToString(",")}")
        append("；周次 ${course.weeks.joinToString(",")}；单双周 ${course.weekParity}")
        append("；地点 ${course.location ?: "待确认"}；教师 ${course.teacher ?: "待确认"}")
        course.note?.takeIf { it.isNotBlank() }?.let { append("；备注 $it") }
        appendLine()
    }
}

private fun parseFullChatContent(response: String): String {
    val root = DayAgentJson.parseToJsonElement(response).jsonObject
    val choice = root["choices"]?.jsonArray?.firstOrNull()?.jsonObject
        ?: throw MissingAgentBodyException()
    val message = choice["message"]?.jsonObject
    return agentTextFromJson(message?.get("content"))
        .ifBlank { agentTextFromJson(choice["text"]) }
        .takeIf(String::isNotBlank)
        ?: throw MissingAgentBodyException()
}

/**
 * OpenAI-compatible providers may encode message content as a string, a content-part array, or a
 * legacy choice text field. Keep this transport normalization separate from Agent protocol parsing.
 */
internal fun agentTextFromJson(element: JsonElement?): String = when (element) {
    null -> ""
    is JsonPrimitive -> element.contentOrNull.orEmpty()
    is JsonArray -> element.joinToString("") { part ->
        when (part) {
            is JsonPrimitive -> part.contentOrNull.orEmpty()
            is JsonObject -> agentTextFromJson(part["text"] ?: part["content"])
            else -> ""
        }
    }

    is JsonObject -> agentTextFromJson(element["text"] ?: element["content"])
}

private class MissingAgentBodyException(
    val renderedReasoning: String = ""
) : IllegalStateException("AI 没有返回最终正文")

private fun HttpURLConnection.readResponse(): String {
    val code = responseCode
    val stream = if (code in 200..299) inputStream else errorStream
    val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
    if (code !in 200..299) throw IllegalStateException("AI 请求失败 ($code)：${text.take(300)}")
    return text
}

private fun httpGet(url: String): String {
    val connection = URL(url).openConnection() as HttpURLConnection
    connection.connectTimeout = 15_000
    connection.readTimeout = 15_000
    connection.setRequestProperty("Accept", "application/json")
    return connection.readResponse()
}

private fun weatherCodeLabel(code: Int): String = when (code) {
    0 -> "晴"
    1, 2 -> "晴间多云"
    3 -> "阴"
    45, 48 -> "有雾"
    51, 53, 55, 56, 57 -> "毛毛雨"
    61, 63, 65, 66, 67 -> "有雨"
    71, 73, 75, 77 -> "有雪"
    80, 81, 82 -> "阵雨"
    85, 86 -> "阵雪"
    95, 96, 99 -> "雷雨"
    else -> "天气变化"
}
