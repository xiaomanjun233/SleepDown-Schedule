package com.xiaomanjun.sleepdownschedule.feature.agent

import com.xiaomanjun.sleepdownschedule.feature.importing.*

import com.xiaomanjun.sleepdownschedule.*

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

private val AgentResponsesJson = Json { ignoreUnknownKeys = true; isLenient = true }

internal data class AgentResponsesTurn(
    val outputItems: List<JsonObject>,
    val calls: List<AgentToolCall>,
    val content: String,
    val unparsedToolCallCount: Int,
    val usage: AgentTokenUsage
)

/**
 * Responses API Agent transport for official and compatible providers.
 *
 * The app deliberately uses `store=false` and replays complete Responses output items, including
 * opaque reasoning items, so schedule context stays stateless without breaking reasoning/tool
 * continuity.
 */
internal class OpenAiResponsesAgentRunner(
    private val interaction: AiImportInteraction? = null,
    private val onReasoning: ((String) -> Unit)? = null
) {
    fun chat(
        settings: AiImportSettings,
        chatMessages: List<JsonObject>,
        includeMemoryTool: Boolean,
        onStatus: (AgentRunStatus) -> Unit,
        onDelta: (String) -> Unit,
        onStreamReset: () -> Unit,
        executeTool: (AgentToolCall) -> AgentToolResult,
        cachedTools: Set<AgentToolName> = emptySet(),
        cachedResults: Map<String, AgentToolResult> = emptyMap(),
        validateAnswer: (String) -> String? = { null },
        telemetry: DayAgentTurnTelemetry,
        importWorkspace: Boolean = false
    ): String {
        val instructions = chatMessages
            .filter { it["role"]?.jsonPrimitive?.contentOrNull == "system" }
            .mapNotNull { it["content"]?.jsonPrimitive?.contentOrNull }
            .joinToString("\n\n")
        val input = chatMessages
            .filterNot { it["role"]?.jsonPrimitive?.contentOrNull == "system" }
            .map(::toResponsesInputMessage)
            .toMutableList()
        val completedOneShotTools = cachedTools.toMutableSet()
        val evidenceKeys = mutableSetOf<String>()
        val turnTools = AgentTurnToolSession(cachedResults, executeTool)
        var outputRetryRequested = false

        toolRounds@ for (round in 0 until MaxAgentToolRounds) {
            interaction?.checkActive()
            onStatus(AgentRunStatus(AgentRunStatusIcon.THINKING, "正在思考"))
            telemetry.requestStarted()
            val decision = parseAgentResponsesTurn(
                post(
                    settings,
                    responsesBody(
                        settings = settings,
                        instructions = instructions + "\n\n" +
                            (if (importWorkspace) AgentImportWorkspace.TaskStage else DayAgentPrompts.TaskStage) + if (outputRetryRequested) {
                                "\n\n" + if (importWorkspace) {
                                    "上一轮未返回完整正文。继续使用导入工具核对材料并修改，或根据已有校验结果给出简短总结；不要输出应用操作计划。"
                                } else DayAgentPrompts.TaskOutputRetry
                            } else "",
                        input = input,
                        stream = false,
                        includeTools = true,
                        includeMemoryTool = includeMemoryTool,
                        excludedTools = completedOneShotTools,
                        reasoningEffort = settings.profile.reasoningEffort,
                        importWorkspace = importWorkspace
                    )
                )
            )
            telemetry.recordUsage(decision.usage)
            telemetry.recordDecisionRound(decision.calls.size)
            if (decision.calls.isNotEmpty()) {
                val note = if (importWorkspace) decision.content.takeIf(String::isNotBlank)
                else decision.content.trim().take(120).ifBlank { "我先调用所需工具确认当前信息，再继续处理。" }
                onStatus(
                    AgentRunStatus(
                        icon = AgentRunStatusIcon.THINKING,
                        text = "准备下一步",
                        detail = note
                    )
                )
            }
            // Preserve opaque reasoning even when retrying an empty response without tool calls.
            input += decision.outputItems
            if (decision.calls.isEmpty()) {
                val answer = usableAgentAnswer(decision.content)
                if (answer != null) {
                    val feedback = validateAnswer(answer)
                    if (feedback != null) {
                        onStatus(AgentRunStatus(AgentRunStatusIcon.THINKING, "根据自检结果修正计划"))
                        input += buildJsonObject { put("role", "user"); put("content", feedback) }
                        continue@toolRounds
                    }
                    onDelta(answer)
                    return answer
                }
                if (!outputRetryRequested) {
                    outputRetryRequested = true
                    continue@toolRounds
                }
                break@toolRounds
            }

            val results = decision.calls.map { call ->
                onStatus(call.name.runStatus())
                val result = if (call.name == AgentToolName.PROPOSE_ACTIONS && decision.calls.size != 1) {
                    AgentToolResult(call.id, call.name, false, "请在读完事实后单独调用 PROPOSE_ACTIONS，一次提交完整计划。")
                } else turnTools.run(call)
                input += buildJsonObject {
                    put("type", "function_call_output")
                    put("call_id", result.callId)
                    put("output", result.content)
                }
                if (result.success && call.name.isOneShotPerTurn) completedOneShotTools += call.name
                result
            }
            telemetry.recordToolResults(results)
            results.singleOrNull()?.proposedAnswer?.let { answer ->
                onDelta(answer)
                return answer
            }
            val addedEvidence = decision.calls
                .map { call -> evidenceKeys.add(call.cacheKey()) }
                .any { it }
            if (!addedEvidence && !importWorkspace) break@toolRounds
        }

        return streamFinal(
            settings = settings,
            instructions = instructions,
            input = input,
            onStatus = onStatus,
            onDelta = onDelta,
            onStreamReset = onStreamReset,
            validateAnswer = validateAnswer,
            telemetry = telemetry,
            importWorkspace = importWorkspace
        )
    }

    private fun streamFinal(
        settings: AiImportSettings,
        instructions: String,
        input: List<JsonObject>,
        onStatus: (AgentRunStatus) -> Unit,
        onDelta: (String) -> Unit,
        onStreamReset: () -> Unit,
        validateAnswer: (String) -> String?,
        telemetry: DayAgentTurnTelemetry,
        importWorkspace: Boolean = false
    ): String {
        onStatus(AgentRunStatus(AgentRunStatusIcon.THINKING, "整理结果"))
        telemetry.finalAnswerStarted()
        val finalInstructions = instructions + "\n\n" + if (importWorkspace) {
            "根据已通过本地校验的导入阶段总结结果；尚未保存到课表。不要输出工具协议或未执行的新计划。"
        } else DayAgentPrompts.FinalAnswerStage
        val body = responsesBody(
            settings = settings,
            instructions = finalInstructions,
            input = input,
            stream = true,
            includeTools = false,
            includeMemoryTool = false,
            excludedTools = emptySet(),
            reasoningEffort = settings.profile.reasoningEffort
        )
        return try {
            telemetry.requestStarted()
            val gate = AgentFinalOutputGate(onDelta)
            val answer = gate.finish(stream(settings, body, gate::accept, telemetry::recordUsage))
            validateAnswer(answer)?.let { throw AgentPlanValidationException(answer, it) }
            answer
        } catch (error: Throwable) {
            if (error !is MissingResponsesBodyException &&
                error !is MissingAgentBodyException &&
                error !is AgentProtocolViolationException && error !is AgentPlanValidationException
            ) {
                throw error
            }
            onStreamReset()
            onStatus(AgentRunStatus(AgentRunStatusIcon.THINKING, if (error is AgentPlanValidationException) "根据自检结果修正计划" else "修正输出格式"))
            val retry = responsesBody(
                settings = settings,
                instructions = finalInstructions + "\n\n" + if (importWorkspace) {
                    "上一轮没有有效正文。请只总结已经完成的导入核对与修改，不输出 DSML、函数调用、agent_actions 或数据库保存声明。"
                } else DayAgentPrompts.FinalAnswerProtocolRetry,
                input = if (error is AgentPlanValidationException) input + listOf(
                    buildJsonObject { put("role", "assistant"); put("content", error.answer) },
                    buildJsonObject { put("role", "user"); put("content", error.feedback) }
                ) else input,
                stream = false,
                includeTools = false,
                includeMemoryTool = false,
                excludedTools = emptySet(),
                reasoningEffort = settings.profile.reasoningEffort
            )
            telemetry.requestStarted()
            val retryTurn = parseAgentResponsesTurn(post(settings, retry))
            telemetry.recordUsage(retryTurn.usage)
            val content = retryTurn.content
                .takeIf(String::isNotBlank)
                ?: throw MissingResponsesBodyException()
            if (containsLeakedAgentFunctionProtocol(content)) {
                throw AgentProtocolViolationException()
            }
            val checked = if (validateAnswer(content) == null) content else AgentPlanRepairLimitMessage
            onDelta(checked)
            checked
        }
    }

    private fun responsesBody(
        settings: AiImportSettings,
        instructions: String,
        input: List<JsonObject>,
        stream: Boolean,
        includeTools: Boolean,
        includeMemoryTool: Boolean,
        excludedTools: Set<AgentToolName>,
        reasoningEffort: AiReasoningEffort,
        importWorkspace: Boolean = false
    ): JsonObject = buildJsonObject {
        put("model", settings.profile.defaultModel)
        put("store", false)
        put("stream", stream)
        put("instructions", instructions)
        put("input", JsonArray(input))
        put("reasoning", buildJsonObject {
            put("effort", if (isOfficialMimoEndpoint(settings.profile.baseUrl)) mimoResponsesEffort(reasoningEffort) else reasoningEffort.apiValue)
            if (
                settings.profile.id == AiProviderPresets.openAI.id &&
                isOfficialOpenAIBaseUrl(settings.profile.baseUrl)
            ) {
                put("summary", "auto")
            }
        })
        if (includeTools) {
            put("tools", agentResponsesToolDefinitions(includeMemoryTool, excludedTools, importWorkspace = importWorkspace))
            put("tool_choice", "auto")
        }
    }

    private fun post(settings: AiImportSettings, body: JsonObject): String {
        if (interaction != null) {
            val path = settings.profile.responsesPath.trim('/')
            val base = if (path.isEmpty()) settings.profile.baseUrl.trim().trimEnd('/')
                else normalizeAiBaseUrlForProvider(settings.profile.id, settings.profile.baseUrl).trimEnd('/')
            return postJson(if (path.isEmpty()) base else "$base/$path", settings.apiKey, body.toString(),
                settings.profile.authType, settings.profile.id,
                AiImportNetworkContext("AGENT", interaction = interaction, onReasoningUpdate = onReasoning, onPhase = interaction.onHttpPhase))
        }
        val connection = open(settings, body)
        val code = connection.responseCode
        val source = if (code in 200..299) connection.inputStream else connection.errorStream
        val response = source?.bufferedReader()?.use { it.readAiBoundedText() }.orEmpty()
        connection.disconnect()
        if (code !in 200..299) {
            throw AiServiceResponseException(formatAiRequestError(code, response, settings.profile.id), response, httpStatus = code)
        }
        return response
    }

    private fun stream(
        settings: AiImportSettings,
        body: JsonObject,
        onDelta: (String) -> Unit,
        onUsage: (AgentTokenUsage) -> Unit
    ): String {
        fun phase(value: AiImportHttpPhase) {
            interaction?.checkActive()
            interaction?.onTransportPhase?.invoke(value)
        }
        phase(AiImportHttpPhase.REQUEST_CREATED)
        phase(AiImportHttpPhase.BODY_WRITE_START)
        val connection = open(settings, body)
        try {
        phase(AiImportHttpPhase.BODY_WRITE_END)
        val code = connection.responseCode
        phase(AiImportHttpPhase.HEADERS_RECEIVED)
        if (code !in 200..299) {
            val error = connection.errorStream?.bufferedReader()?.use { it.readAiBoundedText() }.orEmpty()
            throw AiServiceResponseException(formatAiRequestError(code, error, settings.profile.id), error, httpStatus = code)
        }
        if (!connection.contentType.orEmpty().contains("text/event-stream", ignoreCase = true)) {
            phase(AiImportHttpPhase.BODY_READ_START)
            val response = connection.inputStream.bufferedReader().use { it.readAiBoundedText() }
            interaction?.checkActive()
            val turn = parseAgentResponsesTurn(response)
            onUsage(turn.usage)
            val content = turn.content
                .takeIf(String::isNotBlank)
                ?: throw MissingResponsesBodyException()
            onDelta(content)
            return finishAiImportStream(content, publishFinal = {
                interaction?.publishStream(false, content, force = true)
            }, onEnd = { phase(AiImportHttpPhase.STREAM_END) })
        }

        val result = AgentResponsesTextAccumulator()
        val importStream = interaction?.let { ResponsesSseAccumulator() }
        var firstEvent = true
        connection.forEachSseDataLine(checkActive = { interaction?.checkActive() }) { data ->
            if (firstEvent) {
                firstEvent = false
                phase(AiImportHttpPhase.FIRST_EVENT)
            }
            importStream?.consume(data)
            importStream?.activities?.forEach { interaction?.publishActivity(it) }
            importStream?.let { interaction?.publishStream(it.reasoning.isNotEmpty(), it.courseOutput) }
            val event = parseSseJsonObject(data) ?: return@forEachSseDataLine
            val usage = agentTokenUsage(event)
            if (!usage.isEmpty) onUsage(usage)
            result.consume(event).takeIf(String::isNotEmpty)?.let(onDelta)
        }
        val answer = result.finish()
        return finishAiImportStream(answer, publishFinal = {
            interaction?.publishStream(importStream?.reasoning?.isNotEmpty() == true, answer, force = true)
        }, onEnd = { phase(AiImportHttpPhase.STREAM_END) })
        } catch (error: Throwable) {
            interaction?.checkActive()
            throw error
        } finally {
            connection.disconnect()
        }
    }

    private fun open(settings: AiImportSettings, body: JsonObject): HttpURLConnection {
        val path = settings.profile.responsesPath.trim('/')
        val base = if (path.isEmpty()) {
            // 未显式配置路径：直接使用下发的完整地址，不再 normalize 剥掉 /responses 等后缀
            settings.profile.baseUrl.trim().trimEnd('/')
        } else {
            normalizeAiBaseUrlForProvider(settings.profile.id, settings.profile.baseUrl).trimEnd('/')
        }
        val endpoint = if (path.isEmpty()) base else "$base/$path"
        return openAiPostConnection(
            url = endpoint,
            apiKey = settings.apiKey,
            authType = settings.profile.authType,
            contentType = "application/json; charset=utf-8",
            accept = null
        ).apply {
            try {
                interaction?.attach(this)
                outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            } catch (error: Throwable) {
                disconnect()
                interaction?.checkActive()
                throw error
            }
        }
    }
}

private fun toResponsesInputMessage(message: JsonObject): JsonObject {
    val role = message["role"]?.jsonPrimitive?.contentOrNull ?: "user"
    val content = message["content"]
    return buildJsonObject {
        put("role", role)
        when (content) {
            is JsonArray -> put("content", buildJsonArray {
                content.forEach { part ->
                    val item = part as? JsonObject ?: return@forEach
                    when (item["type"]?.jsonPrimitive?.contentOrNull) {
                        "text" -> add(buildJsonObject {
                            put("type", "input_text")
                            put("text", item["text"]?.jsonPrimitive?.contentOrNull.orEmpty())
                        })
                        "image_url" -> add(buildJsonObject {
                            put("type", "input_image")
                            put(
                                "image_url",
                                item["image_url"]?.jsonObject
                                    ?.get("url")?.jsonPrimitive?.contentOrNull.orEmpty()
                            )
                        })
                    }
                }
            })
            is JsonPrimitive -> put("content", content.contentOrNull.orEmpty())
            else -> put("content", "")
        }
    }
}

internal fun parseAgentResponsesTurn(response: String): AgentResponsesTurn {
    val root = AgentResponsesJson.parseToJsonElement(response).jsonObject
    check(root["status"]?.jsonPrimitive?.contentOrNull != "incomplete") {
        "AI 回复未完成，未生成可执行计划，请重试。"
    }
    val rawOutputItems = root.optionalArray("output")
        .mapNotNull { it as? JsonObject }
    val calls = mutableListOf<AgentToolCall>()
    val outputItems = rawOutputItems.mapIndexed { index, item ->
        if (item["type"]?.jsonPrimitive?.contentOrNull != "function_call") return@mapIndexed item
        val call = decodeAgentToolCall(
            item["call_id"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                ?: "local-${response.hashCode().toUInt()}-$index",
            item["name"]?.jsonPrimitive?.contentOrNull.orEmpty(), item["arguments"]
        )
        calls += call
        val rawArguments = item["arguments"]
        JsonObject(item + mapOf(
            "call_id" to JsonPrimitive(call.id),
            "arguments" to if (rawArguments is JsonPrimitive && rawArguments.isString) rawArguments
                else JsonPrimitive(rawArguments?.toString() ?: "{}")
        ))
    }
    val content = buildList {
        root["output_text"]?.jsonPrimitive?.contentOrNull
            ?.takeIf(String::isNotBlank)
            ?.let(::add)
        outputItems.forEach { item ->
            if (item["type"]?.jsonPrimitive?.contentOrNull != "message") return@forEach
            item.optionalArray("content").forEach { part ->
                val partObject = part as? JsonObject ?: return@forEach
                if (partObject["type"]?.jsonPrimitive?.contentOrNull == "output_text") {
                    partObject["text"]?.jsonPrimitive?.contentOrNull
                        ?.takeIf(String::isNotBlank)
                        ?.let(::add)
                }
            }
        }
    }.distinct().joinToString("\n")
    return AgentResponsesTurn(
        outputItems = outputItems,
        calls = calls,
        content = content,
        unparsedToolCallCount = calls.count { it.name == AgentToolName.UNKNOWN },
        usage = agentTokenUsage(root)
    )
}

/** Some compatible endpoints deliver final text only in the completed event. */
internal class AgentResponsesTextAccumulator {
    private val text = StringBuilder()
    private var completed = false

    fun consume(event: JsonObject): String = when (event["type"]?.jsonPrimitive?.contentOrNull) {
        "response.output_text.delta" -> event["delta"]?.jsonPrimitive?.contentOrNull.orEmpty()
            .also(text::append)
        "response.completed" -> {
            val response = event["response"] as? JsonObject
                ?: error("AI 完成事件缺少响应正文")
            requireCompletedResponsesStatus(response, response.toString())
            completed = true
            val complete = parseAgentResponsesTurn(response.toString()).content
            if (complete.isBlank()) "" else {
                check(complete.startsWith(text.toString())) { "AI 最终正文与流式内容不一致，请重试。" }
                complete.substring(text.length).also(text::append)
            }
        }
        "response.incomplete" -> error("AI 回复未完成，未生成可执行计划，请重试。")
        "response.failed", "error" -> {
            val response = event["response"] as? JsonObject
            val error = (event["error"] as? JsonObject) ?: (response?.get("error") as? JsonObject)
            error(error?.get("message")?.jsonPrimitive?.contentOrNull ?: "AI 流式响应失败")
        }
        else -> ""
    }

    fun finish(): String {
        check(completed) { "AI 最终回复未收到完成事件，请重试。" }
        return text.toString().takeIf(String::isNotBlank) ?: throw MissingResponsesBodyException()
    }
}

private class MissingResponsesBodyException : IllegalStateException("AI 没有返回最终正文")
