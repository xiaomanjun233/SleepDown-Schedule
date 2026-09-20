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

private fun toolDecisionReasoningEffort(profile: AiProviderProfile): AiReasoningEffort {
    val supported = AiProviderPresets.reasoningEfforts(profile)
    return when {
        AiReasoningEffort.MINIMAL in supported -> AiReasoningEffort.MINIMAL
        AiReasoningEffort.LOW in supported -> AiReasoningEffort.LOW
        else -> profile.reasoningEffort
    }
}

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
internal class OpenAiResponsesAgentRunner {
    fun chat(
        settings: AiImportSettings,
        chatMessages: List<JsonObject>,
        includeMemoryTool: Boolean,
        onStatus: (AgentRunStatus) -> Unit,
        onDelta: (String) -> Unit,
        onStreamReset: () -> Unit,
        executeTool: (AgentToolCall) -> AgentToolResult,
        cachedTools: Set<AgentToolName> = emptySet(),
        allowCachedAnswer: Boolean = false,
        telemetry: DayAgentTurnTelemetry
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
        val decisionEffort = toolDecisionReasoningEffort(settings.profile)

        toolRounds@ for (round in 0 until MaxAgentToolRounds) {
            onStatus(AgentRunStatus(AgentRunStatusIcon.THINKING, "正在思考"))
            telemetry.requestStarted()
            val decision = parseAgentResponsesTurn(
                post(
                    settings,
                    responsesBody(
                        settings = settings,
                        instructions = instructions + "\n\n" +
                            (if (allowCachedAnswer) DayAgentPrompts.CachedFactsStage else DayAgentPrompts.ToolDecisionStage),
                        input = input,
                        stream = false,
                        includeTools = true,
                        includeMemoryTool = includeMemoryTool,
                        excludedTools = completedOneShotTools,
                        reasoningEffort = if (allowCachedAnswer) settings.profile.reasoningEffort else decisionEffort
                    )
                )
            )
            telemetry.recordUsage(decision.usage)
            telemetry.recordDecisionRound(decision.calls.size)
            if (decision.unparsedToolCallCount > 0) {
                throw IllegalStateException(
                    "模型返回了 ${decision.unparsedToolCallCount} 个无法识别的工具调用，请重试"
                )
            }
            if (decision.calls.isNotEmpty()) {
                val note = decision.content.trim().take(120).ifBlank {
                    "我先调用所需工具确认当前信息，再继续处理。"
                }
                onStatus(
                    AgentRunStatus(
                        icon = AgentRunStatusIcon.THINKING,
                        text = "准备下一步",
                        detail = note
                    )
                )
            }
            if (decision.calls.isEmpty()) {
                if (allowCachedAnswer) usableCachedAgentAnswer(decision.content)?.let { answer ->
                    onDelta(answer)
                    return answer
                }
                return streamFinal(
                    settings = settings,
                    instructions = instructions,
                    input = input,
                    onStatus = onStatus,
                    onDelta = onDelta,
                    onStreamReset = onStreamReset,
                    telemetry = telemetry
                )
            }

            // Stateless Responses continuation requires every output item, not just visible text.
            input += decision.outputItems
            val results = decision.calls.map { call ->
                onStatus(call.name.runStatus())
                val result = executeTool(call)
                input += buildJsonObject {
                    put("type", "function_call_output")
                    put("call_id", result.callId)
                    put("output", result.content)
                }
                if (result.success && call.name.isOneShotPerTurn) completedOneShotTools += call.name
                result
            }
            telemetry.recordToolResults(results)
            val addedEvidence = decision.calls
                .map { call -> evidenceKeys.add(call.cacheKey()) }
                .any { it }
            if (!addedEvidence) break@toolRounds
        }

        return streamFinal(
            settings = settings,
            instructions = instructions,
            input = input,
            onStatus = onStatus,
            onDelta = onDelta,
            onStreamReset = onStreamReset,
            telemetry = telemetry
        )
    }

    private fun streamFinal(
        settings: AiImportSettings,
        instructions: String,
        input: List<JsonObject>,
        onStatus: (AgentRunStatus) -> Unit,
        onDelta: (String) -> Unit,
        onStreamReset: () -> Unit,
        telemetry: DayAgentTurnTelemetry
    ): String {
        onStatus(AgentRunStatus(AgentRunStatusIcon.THINKING, "整理结果"))
        telemetry.finalAnswerStarted()
        val finalInstructions = instructions + "\n\n" + DayAgentPrompts.FinalAnswerStage
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
            gate.finish(stream(settings, body, gate::accept, telemetry::recordUsage))
        } catch (error: Throwable) {
            if (error !is MissingResponsesBodyException &&
                error !is MissingAgentBodyException &&
                error !is AgentProtocolViolationException
            ) {
                throw error
            }
            onStreamReset()
            onStatus(AgentRunStatus(AgentRunStatusIcon.THINKING, "修正输出格式"))
            val retry = responsesBody(
                settings = settings,
                instructions = finalInstructions + "\n\n" + DayAgentPrompts.FinalAnswerProtocolRetry,
                input = input,
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
            onDelta(content)
            content
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
        reasoningEffort: AiReasoningEffort
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
            put("tools", agentResponsesToolDefinitions(includeMemoryTool, excludedTools))
            put("tool_choice", "auto")
        }
    }

    private fun post(settings: AiImportSettings, body: JsonObject): String {
        val connection = open(settings, body)
        val code = connection.responseCode
        val source = if (code in 200..299) connection.inputStream else connection.errorStream
        val response = source?.bufferedReader()?.use { it.readText() }.orEmpty()
        connection.disconnect()
        if (code !in 200..299) {
            throw IllegalStateException(formatAiRequestError(code, response, settings.profile.id))
        }
        return response
    }

    private fun stream(
        settings: AiImportSettings,
        body: JsonObject,
        onDelta: (String) -> Unit,
        onUsage: (AgentTokenUsage) -> Unit
    ): String {
        val connection = open(settings, body)
        val code = connection.responseCode
        if (code !in 200..299) {
            val error = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            connection.disconnect()
            throw IllegalStateException(formatAiRequestError(code, error, settings.profile.id))
        }
        if (!connection.contentType.orEmpty().contains("text/event-stream", ignoreCase = true)) {
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()
            val turn = parseAgentResponsesTurn(response)
            onUsage(turn.usage)
            val content = turn.content
                .takeIf(String::isNotBlank)
                ?: throw MissingResponsesBodyException()
            onDelta(content)
            return content
        }

        val result = AgentResponsesTextAccumulator()
        try {
        connection.forEachSseDataLine { data ->
            val event = parseSseJsonObject(data) ?: return@forEachSseDataLine
            val usage = agentTokenUsage(event)
            if (!usage.isEmpty) onUsage(usage)
            result.consume(event).takeIf(String::isNotEmpty)?.let(onDelta)
        }
        return result.finish()
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
            outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
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
    val outputItems = root.optionalArray("output")
        .mapNotNull { it as? JsonObject }
    val functionItems = outputItems.filter {
        it["type"]?.jsonPrimitive?.contentOrNull == "function_call"
    }
    val calls = functionItems.mapNotNull { item ->
        val rawName = item["name"]?.jsonPrimitive?.contentOrNull
            ?.trim()
            ?.replace('-', '_')
            ?.uppercase()
            ?: return@mapNotNull null
        val name = AgentToolName.entries.firstOrNull { it.name == rawName }
            ?: return@mapNotNull null
        val arguments = item["arguments"]?.jsonPrimitive?.contentOrNull
            ?.let { raw ->
                runCatching { AgentResponsesJson.parseToJsonElement(raw) as? JsonObject }
                    .getOrNull()
            }
            ?.mapValues { (_, value) ->
                (value as? JsonPrimitive)?.contentOrNull ?: value.toString()
            }
            .orEmpty()
        AgentToolCall(
            id = item["call_id"]?.jsonPrimitive?.contentOrNull
                ?: item["id"]?.jsonPrimitive?.contentOrNull
                ?: "${name.name.lowercase()}-${response.hashCode().toUInt()}",
            name = name,
            arguments = arguments
        )
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
        unparsedToolCallCount = functionItems.size - calls.size,
        usage = agentTokenUsage(root)
    )
}

/** Some compatible endpoints deliver final text only in the completed event. */
internal class AgentResponsesTextAccumulator {
    private val text = StringBuilder()

    fun consume(event: JsonObject): String = when (event["type"]?.jsonPrimitive?.contentOrNull) {
        "response.output_text.delta" -> event["delta"]?.jsonPrimitive?.contentOrNull.orEmpty()
            .also(text::append)
        "response.completed" -> {
            val response = event["response"] as? JsonObject
                ?: error("AI 完成事件缺少响应正文")
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

    fun finish(): String = text.toString().takeIf(String::isNotBlank) ?: throw MissingResponsesBodyException()
}

private class MissingResponsesBodyException : IllegalStateException("AI 没有返回最终正文")
