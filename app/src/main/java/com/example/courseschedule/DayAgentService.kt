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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
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
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap

private val DayAgentJson = Json { ignoreUnknownKeys = true; isLenient = true }

object DayAgentPreferences {
    private const val Prefs = "day_agent_preferences"
    private val mutableChanges = MutableStateFlow(0L)
    val changes: Flow<Long> = mutableChanges

    fun hasDecision(context: Context): Boolean = prefs(context).getBoolean("has_decision", false)
    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean("enabled", false)
    fun isDailyAiEnabled(context: Context): Boolean = prefs(context).getBoolean("daily_ai_enabled", true)
    fun isWeatherEnabled(context: Context): Boolean = prefs(context).getBoolean("weather_enabled", true)

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
        onDelta: (String) -> Unit
    ): String = withContext(Dispatchers.IO) {
        val settings = AiImportSettingsStore.load(context)
        require(settings.profile.id != AiProviderPresets.none.id) { "请先在 AI 设置中选择服务商" }
        require(settings.apiKey.isNotBlank()) { "请先在 AI 设置中配置 API Key" }
        val messages = buildList {
            add("system" to DayAgentPrompts.ChatSystem)
            add("system" to AgentSettingRegistry.promptCatalog())
            add("system" to conversationContext(facts))
            if (needsSemesterCourseContext(question)) {
                add("system" to semesterCourseContext(facts))
            }
            history.sortedBy { it.createdAt }.takeLast(20).forEach { message ->
                add((if (message.role == "assistant") "assistant" else "user") to message.content)
            }
            add("user" to question)
        }
        val body = chatBody(settings, messages, stream = true)
        streamChat(settings, body, onDelta)
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
        BufferedReader(InputStreamReader(connection.inputStream, Charsets.UTF_8)).useLines { lines ->
            lines.forEach { line ->
                if (!line.startsWith("data:")) return@forEach
                val data = line.removePrefix("data:").trim()
                if (data == "[DONE]" || data.isBlank()) return@forEach
                val delta = runCatching {
                    DayAgentJson.parseToJsonElement(data).jsonObject["choices"]?.jsonArray?.firstOrNull()
                        ?.jsonObject?.get("delta")?.jsonObject?.get("content")?.jsonPrimitive?.contentOrNull
                }.getOrNull().orEmpty()
                if (delta.isNotEmpty()) {
                    result.append(delta)
                    onDelta(delta)
                }
            }
        }
        require(result.isNotEmpty()) { "AI 没有返回正文" }
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

    private fun chatBody(settings: AiImportSettings, messages: List<Pair<String, String>>, stream: Boolean): String = buildJsonObject {
        put("model", settings.profile.defaultModel)
        put("stream", stream)
        put("temperature", 0.55)
        put("max_tokens", 4096)
        put("messages", buildJsonArray {
            messages.forEach { (role, content) ->
                add(buildJsonObject { put("role", role); put("content", content) })
            }
        })
        if (!stream && settings.profile.structuredOutputMode == StructuredOutputMode.JSON_OBJECT) {
            put("response_format", buildJsonObject { put("type", "json_object") })
        }
    }.toString()
}

class DayAgentRepository(private val context: Context) {
    companion object {
        private val generationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val sessionCache = ConcurrentHashMap<String, AgentDailySessionEntity>()
    }

    private val dao = (context.applicationContext as CourseScheduleApp).database.agentDao()
    private val service = DayAgentService(context.applicationContext)
    private val locks = ConcurrentHashMap<String, Mutex>()

    fun observeSession(scheduleId: Int, date: LocalDate): Flow<AgentDailySessionEntity?> {
        val key = "$scheduleId:$date"
        return dao.observeSession(scheduleId, date.toString()).onEach { session ->
            if (session != null) sessionCache[key] = session
        }
    }

    fun cachedSession(scheduleId: Int, date: LocalDate): AgentDailySessionEntity? = sessionCache["$scheduleId:$date"]

    fun observeMessages(scheduleId: Int, date: LocalDate): Flow<List<AgentMessageEntity>> = dao.observeMessages(scheduleId, date.toString())

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
        onDelta: (String) -> Unit
    ): Result<String> = runCatching {
        cleanup(facts.date)
        dao.insertMessage(AgentMessageEntity(scheduleId = scheduleId, sessionDate = facts.date.toString(), role = "user", content = question, createdAt = System.currentTimeMillis(), status = "READY"))
        val history = dao.getRecentMessages(scheduleId, facts.date.toString(), 20).reversed().dropLast(1)
        val answer = service.chat(facts, history, question, onDelta)
        dao.insertMessage(AgentMessageEntity(scheduleId = scheduleId, sessionDate = facts.date.toString(), role = "assistant", content = answer, createdAt = System.currentTimeMillis(), status = "READY"))
        answer
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

private object DayAgentPrompts {
    const val DailySystem = """你是课程表应用的日程文案助手。你只负责生成简洁、自然的中文文案模板和快捷问题，不计算时间，不编造课程、地点、教师或天气。只返回 JSON 对象，格式为 {\"templates\":{\"MORNING_OVERVIEW\":\"...\"},\"quickQuestions\":[\"...\",\"...\"]}。模板键只能使用请求给出的枚举，占位符只能使用请求给出的白名单。每条文案按“天气与体感；当前或下一节课程；一条可执行建议；一句自然关心”的固定顺序组织，控制在 35 到 100 个汉字。快捷问题生成2至3条，每条不超过12个汉字，必须结合当天课程或空档且适合用户直接点击。"""
    const val ChatSystem = """你是 SleepDown 课程表的今日助手。每次回答只能依据本次请求提供的最新本周课程、课程ID、节次定义、今日/明日安排、天气和当前时间；当用户询问整个学期时，还会提供仅限本次请求使用的全学期课程。不得沿用旧课表事实，不得编造课程、教室、教师、天气或时间。缺少信息时直接追问。回答简洁、可执行。
你可以准备课程操作和设置跳转，但绝不能声称已经执行。操作必须放在正文末尾的唯一机器标记 <agent_actions>[...]</agent_actions> 中，等待用户在应用内确认。
支持的操作：
1. 新增：{\"type\":\"ADD_COURSE\",\"scope\":\"ALL_WEEKS\",\"course\":{\"name\":\"课程名\",\"teacher\":null,\"location\":null,\"weekday\":1,\"periods\":[1,2],\"weeks\":[1,2],\"weekParity\":\"ALL\",\"note\":null},\"summary\":\"添加课程\"}
2. 修改或移动：{\"type\":\"UPDATE_COURSE\",\"courseId\":123,\"scope\":\"CURRENT_WEEK\",\"course\":{\"weekday\":2,\"periods\":[3,4]},\"summary\":\"移动课程\"}
3. 删除：{\"type\":\"DELETE_COURSE\",\"courseId\":123,\"scope\":\"CURRENT_WEEK\",\"summary\":\"删除课程\"}
4. 打开设置：{\"type\":\"OPEN_SETTINGS\",\"settingsPage\":\"SCHEDULE\",\"summary\":\"打开课表设置\"}
5. 修改设置：{\"type\":\"SET_SETTING\",\"settingKey\":\"REALTIME_ACTIVITY\",\"settingValue\":\"TRUE\",\"summary\":\"开启实时活动\"}
交换两门课程必须输出两条 UPDATE_COURSE。courseId 只能使用请求中提供的真实ID。scope 可为 CURRENT_WEEK 或 ALL_WEEKS。星期一为1、星期日为7。
设置目录：GENERAL=通用与深色模式；PERSONALIZATION=首页个性化弹窗（壁纸、玻璃、课程卡片外观、字体和行高）；AI_IMPORT=模型与API；DAY_AGENT=今日助手；SCHEDULE=周数、开学日期、节次；NOTIFICATIONS=课程提醒、提前分钟、通知样式、实时活动、实时活动缩略文字、保活权限与测试；SCHEDULE_MANAGER=多课表；ABOUT/CHANGELOG/DOWNLOAD/DONATE=关于、日志、更新、捐赠。
可修改设置的完整键、类型、范围和当前值由后续系统消息中的设置注册表提供。用户说“打开/开启实时活动”时使用 SET_SETTING，而用户问“在哪里/怎么设置”时使用 OPEN_SETTINGS 指向 NOTIFICATIONS。若只是回答问题，不输出机器标记。"""
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
    appendLine("日期：${facts.date}；当前时间：${facts.now.toLocalTime()}")
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
    return listOf(
        "整个学期", "全学期", "本学期", "这学期", "所有课程", "全部课程",
        "课程总览", "学期安排", "哪几周", "哪一周", "最忙的周", "学期课表",
        "semester", "all courses"
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
    return root["choices"]?.jsonArray?.firstOrNull()?.jsonObject
        ?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.contentOrNull
        ?.takeIf { it.isNotBlank() }
        ?: throw IllegalStateException("AI 没有返回正文")
}

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
