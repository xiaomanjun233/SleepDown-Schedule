package com.xiaomanjun.sleepdownschedule.feature.importing

import com.xiaomanjun.sleepdownschedule.core.remoteconfig.*
import com.xiaomanjun.sleepdownschedule.*
import com.xiaomanjun.sleepdownschedule.feature.backup.*
import android.app.ActivityManager
import android.content.Context
import android.os.Process
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.intOrNull
import java.io.File
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL


class AiScheduleImportService(private val context: Context) {
    suspend fun parseScheduleFile(
        file: AiImportFile,
        settings: AiImportSettings,
        onHttpPhase: (AiImportHttpPhase) -> Unit,
        onReasoningUpdate: (String) -> Unit = {}
    ): Result<AiScheduleImportResult> {
        return withContext(Dispatchers.IO) {
            runCatching {
                require(settings.apiKey.isNotBlank()) { "请先在设置中配置 AI API Key" }
                require(settings.profile.baseUrl.isNotBlank()) { "请先配置接口地址" }
                require(settings.profile.defaultModel.isNotBlank()) { "请先配置模型名称" }
                require(file.bytes.size <= MaxAiImportFileBytes) { "文件不能超过 20MB" }
                val config = settings.toProviderConfig().normalizedForRequest()
                val preprocess = DefaultScheduleFilePreprocessor(context).preprocess(file, config)
                val input = preprocess.toScheduleInput(file)
                val networkContext = input.networkContext(
                    context,
                    if (file.isImage) "IMAGE" else "FILE",
                    onHttpPhase,
                    onReasoningUpdate = onReasoningUpdate
                )
                val result = when {
                    config.endpointStyle == AiEndpointStyle.RESPONSES -> OpenAiResponsesProvider().parseSchedule(config, input, networkContext)
                    else -> OpenAiCompatibleChatProvider().parseSchedule(config, input, networkContext)
                }
                AiScheduleImportResult(
                    output = result.content,
                    routeMessage = preprocess.routeMessage,
                    rawOutput = result.content,
                    reasoningOutput = result.reasoning
                )
            }
        }
    }

    suspend fun parseScheduleText(
        text: String,
        sourceName: String,
        settings: AiImportSettings,
        onHttpPhase: (AiImportHttpPhase) -> Unit,
        onReasoningUpdate: (String) -> Unit = {}
    ): Result<AiScheduleImportResult> {
        return withContext(Dispatchers.IO) {
            runCatching {
                require(settings.apiKey.isNotBlank()) { "请先在设置中配置 AI API Key" }
                require(settings.profile.baseUrl.isNotBlank()) { "请先配置接口地址" }
                require(settings.profile.defaultModel.isNotBlank()) { "请先配置模型名称" }
                val cleaned = text.trim().take(60_000)
                require(cleaned.count { !it.isWhitespace() } >= 40) { "当前页面可提取文本太少，请确认已经进入课表页面" }
                val config = settings.toProviderConfig().normalizedForRequest()
                val input = AiScheduleInput.ExtractedText(cleaned, sourceName)
                val networkContext = input.networkContext(context, "TEXT", onHttpPhase, onReasoningUpdate = onReasoningUpdate)
                val result = when {
                    config.endpointStyle == AiEndpointStyle.RESPONSES -> OpenAiResponsesProvider().parseSchedule(config, input, networkContext)
                    else -> OpenAiCompatibleChatProvider().parseSchedule(config, input, networkContext)
                }
                AiScheduleImportResult(
                    output = result.content,
                    routeMessage = "已提取当前教务页面文本，使用 AI 解析。",
                    rawOutput = result.content,
                    reasoningOutput = result.reasoning
                )
            }
        }
    }

    suspend fun parseScheduleCapturedPage(
        text: String,
        screenshots: List<RenderedPageImage>,
        sourceName: String,
        warnings: List<String>,
        settings: AiImportSettings,
        onHttpPhase: (AiImportHttpPhase) -> Unit,
        onReasoningUpdate: (String) -> Unit = {}
    ): Result<AiScheduleImportResult> {
        return withContext(Dispatchers.IO) {
            runCatching {
                require(settings.apiKey.isNotBlank()) { "请先在设置中配置 AI API Key" }
                require(settings.profile.baseUrl.isNotBlank()) { "请先配置接口地址" }
                require(settings.profile.defaultModel.isNotBlank()) { "请先配置模型名称" }
                val cleaned = text.trim().take(60_000)
                val config = settings.toProviderConfig().normalizedForRequest()
                if (screenshots.isNotEmpty()) {
                    require(config.supportsVision) {
                        "当前模型无法识别截图，请换视觉模型，或手动进入可复制文本课表页后重试。"
                    }
                } else {
                    require(cleaned.count { !it.isWhitespace() } >= 40) { "当前页面可提取文本太少，请确认已经进入课表页面" }
                }
                val input = if (screenshots.isEmpty()) {
                    AiScheduleInput.ExtractedText(cleaned, sourceName)
                } else {
                    AiScheduleInput.CapturedPage(cleaned, screenshots, sourceName, warnings)
                }
                val networkContext = input.networkContext(
                    context,
                    inputType = "CAPTURED_PAGE",
                    onHttpPhase = onHttpPhase,
                    screenshotCount = screenshots.size,
                    onReasoningUpdate = onReasoningUpdate
                )
                val result = when {
                    config.endpointStyle == AiEndpointStyle.RESPONSES -> OpenAiResponsesProvider().parseSchedule(config, input, networkContext)
                    else -> OpenAiCompatibleChatProvider().parseSchedule(config, input, networkContext)
                }
                val routeMessage = if (screenshots.isEmpty()) {
                    "已提取当前教务页面文本，使用 AI 解析。"
                } else {
                    "已抓取页面文本并生成 ${screenshots.size} 张截图，交给视觉模型解析。"
                }
                AiScheduleImportResult(
                    output = result.content,
                    routeMessage = routeMessage,
                    rawOutput = result.content,
                    reasoningOutput = result.reasoning
                )
            }
        }
    }

    internal suspend fun repairScheduleJson(
        output: String,
        failure: AiImportParseFailure,
        settings: AiImportSettings,
        onHttpPhase: (AiImportHttpPhase) -> Unit = {},
        onReasoningUpdate: (String) -> Unit = {}
    ): Result<AiScheduleImportResult> = withContext(Dispatchers.IO) {
        runCatching {
            require(settings.apiKey.isNotBlank()) { "请先在设置中配置 AI API Key" }
            require(settings.profile.baseUrl.isNotBlank()) { "请先配置接口地址" }
            require(settings.profile.defaultModel.isNotBlank()) { "请先配置模型名称" }
            val config = settings.toProviderConfig().normalizedForRequest()
            val repairPrompt = AiImportRepairManager.buildRepairPrompt(output, failure)
            val input = AiScheduleInput.ExtractedText(repairPrompt, "上轮 AI JSON")
            val networkContext = input.networkContext(context, "REPAIR", onHttpPhase, onReasoningUpdate = onReasoningUpdate)
            val result = when {
                config.endpointStyle == AiEndpointStyle.RESPONSES ->
                    OpenAiResponsesProvider().parseSchedule(config, input, networkContext)
                else -> OpenAiCompatibleChatProvider().parseSchedule(config, input, networkContext)
            }
            AiScheduleImportResult(
                output = result.content,
                routeMessage = "已修复课程数据格式。",
                rawOutput = result.content,
                reasoningOutput = result.reasoning
            )
        }
    }

    suspend fun reviseSchedule(
        draft: ImportDraft,
        instruction: String,
        history: AiEduImportProgress,
        settings: AiImportSettings,
        onHttpPhase: (AiImportHttpPhase) -> Unit = {},
        onReasoningUpdate: (String) -> Unit = {}
    ): Result<AiScheduleImportResult> = withContext(Dispatchers.IO) {
        runCatching {
            require(settings.apiKey.isNotBlank()) { "请先在设置中配置 AI API Key" }
            require(settings.profile.baseUrl.isNotBlank()) { "请先配置接口地址" }
            require(settings.profile.defaultModel.isNotBlank()) { "请先配置模型名称" }
            val config = settings.toProviderConfig().normalizedForRequest()
            val request = buildAiRevisionInput(draft, instruction, history)
            val networkContext = AiImportNetworkContext(
                inputType = "REVISION",
                imageCount = history.screenshotPreviews.size,
                screenshotCount = history.screenshotPreviews.size,
                onPhase = onHttpPhase,
                onReasoningUpdate = onReasoningUpdate,
                processImportanceProvider = { currentAiProcessImportance(context) }
            )
            val result = when {
                config.endpointStyle == AiEndpointStyle.RESPONSES ->
                    OpenAiResponsesProvider().reviseSchedule(config, request, history, networkContext)
                else -> OpenAiCompatibleChatProvider().reviseSchedule(config, request, history, networkContext)
            }
            val revisedDraft = applyAiSchedulePatch(draft, result.content)
            AiScheduleImportResult(
                output = draftToPayload(revisedDraft).toString(),
                routeMessage = "已按要求修改课表。",
                rawOutput = result.content,
                reasoningOutput = result.reasoning
            )
        }
    }
}

private fun AiScheduleInput.networkContext(
    context: Context,
    inputType: String,
    onHttpPhase: (AiImportHttpPhase) -> Unit,
    screenshotCount: Int = 0,
    onReasoningUpdate: (String) -> Unit = {}
): AiImportNetworkContext = AiImportNetworkContext(
    inputType = inputType,
    imageCount = when (this) {
        is AiScheduleInput.ImageBase64 -> 1
        is AiScheduleInput.Images -> images.size
        is AiScheduleInput.CapturedPage -> images.size
        else -> 0
    },
    screenshotCount = screenshotCount,
    onPhase = onHttpPhase,
    onReasoningUpdate = onReasoningUpdate,
    processImportanceProvider = { currentAiProcessImportance(context) }
)

private fun currentAiProcessImportance(context: Context): Int? = runCatching {
    context.getSystemService(ActivityManager::class.java)
        ?.runningAppProcesses
        ?.firstOrNull { it.pid == Process.myPid() }
        ?.importance
}.getOrNull()

suspend fun testAiProviderConnection(settings: AiImportSettings): Result<String> {
    return withContext(Dispatchers.IO) {
        runCatching {
            require(settings.apiKey.isNotBlank()) { "请先配置 AI API Key" }
            require(settings.profile.baseUrl.isNotBlank()) { "请先配置接口地址" }
            require(settings.profile.defaultModel.isNotBlank()) { "请先配置模型名称" }
            val config = settings.toProviderConfig().normalizedForRequest()
            val response = if (config.endpointStyle == AiEndpointStyle.RESPONSES) {
                val body = buildJsonObject {
                    put("model", JsonPrimitive(config.model))
                    put("store", JsonPrimitive(false))
                    put("input", JsonPrimitive("请只回复 OK"))
                    putResponsesReasoning(config)
                    put("max_output_tokens", JsonPrimitive(32))
                }
                postJson(config.resolveRequestEndpoint(), config.apiKey, body.toString(), config.authType, config.providerId)
            } else {
                val body = buildJsonObject {
                    put("model", JsonPrimitive(config.model))
                    put("messages", buildJsonArray {
                        add(buildJsonObject {
                            put("role", JsonPrimitive("user"))
                            put("content", JsonPrimitive("请只回复 OK"))
                        })
                    })
                    putChatSamplingAndReasoning(config)
                    if (config.usesMimoProtocol()) {
                        put("max_completion_tokens", JsonPrimitive(32))
                    } else {
                        put("max_tokens", JsonPrimitive(32))
                    }
                }
                postJson(config.resolveRequestEndpoint(), config.apiKey, body.toString(), config.authType, config.providerId)
            }
            "连接测试成功\n" + response.compactForSettingsResult()
        }
    }
}

suspend fun diagnoseAiProviderNetwork(settings: AiImportSettings): Result<String> {
    return withContext(Dispatchers.IO) {
        runCatching {
            require(settings.profile.baseUrl.isNotBlank()) { "请先配置接口地址" }
            require(settings.profile.defaultModel.isNotBlank()) { "请先配置模型名称" }
            val config = settings.toProviderConfig().normalizedForRequest()
            val endpoint = config.resolveRequestEndpoint()
            val endpointUrl = URL(endpoint)
            val port = if (endpointUrl.port > 0) endpointUrl.port else endpointUrl.defaultPort.takeIf { it > 0 } ?: 443
            val result = StringBuilder()
            result.appendLine("接口：${redactAiUrl(endpoint)}")
            result.appendLine("模型：${config.model}")

            val addresses = runCatching { InetAddress.getAllByName(endpointUrl.host).toList() }
                .getOrElse {
                    result.appendLine("DNS：失败，${it.message.orEmpty()}")
                    return@runCatching result.toString().trim()
                }
            result.appendLine("DNS：${addresses.joinToString { it.hostAddress.orEmpty() }}")

            val tcpAddress = addresses.firstOrNull() ?: error("DNS 没有返回可用地址")
            runCatching {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(tcpAddress, port), 6_000)
                }
            }.onSuccess {
                result.appendLine("TCP $port：已连通 ${tcpAddress.hostAddress}")
            }.onFailure {
                result.appendLine("TCP $port：失败，${it.message.orEmpty()}")
                return@runCatching result.toString().trim()
            }

            runCatching {
                val rootUrl = URL("${endpointUrl.protocol}://${endpointUrl.host}${if (endpointUrl.port > 0) ":${endpointUrl.port}" else ""}")
                val connection = (rootUrl.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 6_000
                    readTimeout = 6_000
                    setRequestProperty("Accept", "application/json,text/plain,*/*")
                }
                try {
                    val status = connection.responseCode
                    result.appendLine("HTTPS：握手成功，根路径 HTTP $status")
                } finally {
                    connection.disconnect()
                }
            }.onFailure {
                result.appendLine("HTTPS：失败，${it.message.orEmpty()}")
            }

            if (settings.apiKey.isBlank()) {
                result.appendLine("请求测试：跳过，未配置 API Key")
            } else {
                testAiProviderConnection(settings)
                    .onSuccess { result.appendLine("请求测试：成功") }
                    .onFailure { result.appendLine("请求测试：失败，${it.message.orEmpty()}") }
            }
            result.toString().trim()
        }
    }
}

private fun String.compactForSettingsResult(maxLength: Int = 360): String {
    val compact = replace(Regex("\\s+"), " ").trim()
    return if (compact.length <= maxLength) compact else compact.take(maxLength) + "..."
}

private fun AiImportSettings.toProviderConfig(): AiProviderConfig {
    return AiProviderConfig(
        providerId = profile.id,
        displayName = profile.displayName,
        apiKey = apiKey,
        baseUrl = profile.baseUrl,
        model = profile.defaultModel,
        endpointStyle = profile.endpointStyle,
        structuredOutputMode = profile.structuredOutputMode,
        supportsVision = AiProviderPresets.supportsImageInput(profile),
        supportsFileUpload = profile.supportsFileUpload || profile.capabilities.supportsFileUpload,
        supportsPdfDirect = profile.supportsPdfDirect || profile.capabilities.supportsPdfFileInput,
        supportsResponses = AiProviderPresets.supportsResponses(profile),
        inputMode = profile.inputMode,
        reasoningEffort = profile.reasoningEffort,
        authType = profile.authType,
        responsesPath = profile.responsesPath,
        chatCompletionsPath = profile.chatCompletionsPath
    )
}

/**
 * Debug-only Chat/Responses A/B override for the background-disconnect investigation.
 * Forces the effective endpoint style for the same provider/model/key/input so the two
 * transports can be compared on device. MUST be removed (and reset to null) after the
 * verification round; it is intentionally not surfaced in any user-facing setting.
 */

private fun applyAiSchedulePatch(base: ImportDraft, patchText: String): ImportDraft {
    val root = Json.parseToJsonElement(patchText).jsonObject
    val operations = root["operations"]?.jsonArray ?: error("PATCH_SCHEDULE 缺少 operations")
    var courses = base.courses.toMutableList()
    var periods = base.periods.toMutableList()
    var totalWeeks = base.config.totalWeeks
    fun courseAt(index: Int): CourseEntity = courses.getOrNull(index - 1)
        ?: error("PATCH_SCHEDULE 引用了不存在的课程索引 #$index")
    operations.forEach { raw ->
        val operation = raw.jsonObject
        val type = operation["type"]?.jsonPrimitive?.contentOrNull ?: error("PATCH_SCHEDULE 缺少操作类型")
        val index = operation["index"]?.jsonPrimitive?.intOrNull ?: 0
        when (type) {
            "replace_course" -> {
                val previous = courseAt(index)
                courses[index - 1] = revisionCourseFromJson(
                    operation["course"]?.jsonObject ?: error("replace_course 缺少 course"),
                    previous
                )
            }
            "add_course" -> courses += revisionCourseFromJson(
                operation["course"]?.jsonObject ?: error("add_course 缺少 course"),
                CourseEntity(scheduleId = base.config.id, name = "", teacher = null, location = null, weekday = 1, periods = listOf(1), weeks = listOf(1), weekParity = WeekParity.ALL, note = null)
            ).copy(id = 0)
            "remove_course" -> {
                courseAt(index)
                courses.removeAt(index - 1)
            }
            "replace_periods" -> {
                val values = operation["periods"]?.jsonArray ?: error("replace_periods 缺少 periods")
                periods = values.map { value ->
                    val item = value.jsonObject
                    PeriodEntity(
                        periodIndex = item["index"]?.jsonPrimitive?.intOrNull ?: error("节次缺少 index"),
                        startTime = item["startTime"]?.jsonPrimitive?.contentOrNull ?: error("节次缺少 startTime"),
                        endTime = item["endTime"]?.jsonPrimitive?.contentOrNull ?: error("节次缺少 endTime"),
                        scheduleId = base.config.id
                    )
                }.sortedBy { it.periodIndex }.toMutableList()
            }
            "set_total_weeks" -> totalWeeks = (operation["totalWeeks"]?.jsonPrimitive?.intOrNull ?: 0).also {
                require(it in 1..60) { "总周数必须在 1 到 60 之间" }
            }
            else -> error("不支持的 PATCH_SCHEDULE 操作：$type")
        }
    }
    val candidate = base.copy(
        config = base.config.copy(totalWeeks = totalWeeks),
        periods = periods,
        courses = courses
    )
    val validated = ScheduleImportParser.parse(draftToPayload(candidate).toString(), base.config).getOrThrow()
    return validated.copy(
        courses = validated.courses.zip(candidate.courses).map { (course, source) ->
            course.copy(customPeriodTimes = source.customPeriodTimes?.takeIf {
                course.periods == source.periods && course.customStartTime == source.customStartTime &&
                    course.customEndTime == source.customEndTime
            })
        },
        source = ImportDraftSource.AI_EDU
    )
}

private fun revisionCourseFromJson(value: JsonObject, previous: CourseEntity): CourseEntity {
    val start = value["customStartTime"]?.jsonPrimitive?.contentOrNull?.trim()
    val end = value["customEndTime"]?.jsonPrimitive?.contentOrNull?.trim()
    val customRange = when {
        start == null && end == null -> previous.customStartTime to previous.customEndTime
        start == "" && end == "" -> null to null
        !start.isNullOrBlank() && !end.isNullOrBlank() -> start to end
        else -> error("课程真实开始和结束时间必须同时提供")
    }
    val revisedPeriods = value["periods"]?.jsonArray?.mapNotNull { it.jsonPrimitive.intOrNull }?.distinct()?.sorted()
        ?.takeIf { it.isNotEmpty() } ?: error("课程 periods 不能为空")
    val periodTimes = previous.customPeriodTimes?.takeIf {
        revisedPeriods == previous.periods && customRange == (previous.customStartTime to previous.customEndTime)
    }
    return previous.copy(
        name = value["name"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty().ifBlank { error("课程名称不能为空") },
        teacher = value["teacher"]?.jsonPrimitive?.contentOrNull?.trim()?.ifBlank { null },
        location = value["location"]?.jsonPrimitive?.contentOrNull?.trim()?.ifBlank { null },
        weekday = value["weekday"]?.jsonPrimitive?.intOrNull?.also { require(it in 1..7) } ?: error("课程缺少 weekday"),
        periods = revisedPeriods,
        weeks = value["weeks"]?.jsonArray?.mapNotNull { it.jsonPrimitive.intOrNull }?.distinct()?.sorted()
            ?.takeIf { it.isNotEmpty() } ?: error("课程 weeks 不能为空"),
        weekParity = value["weekParity"]?.jsonPrimitive?.contentOrNull?.let { WeekParity.valueOf(it) }
            ?: error("课程缺少 weekParity"),
        note = value["note"]?.jsonPrimitive?.contentOrNull?.trim()?.ifBlank { null },
        customStartTime = customRange.first,
        customEndTime = customRange.second,
        customPeriodTimes = periodTimes
    )
}

