package com.example.courseschedule

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
    val unparsedToolCallCount: Int
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
        executeTool: (AgentToolCall) -> AgentToolResult
    ): String {
        val instructions = chatMessages
            .filter { it["role"]?.jsonPrimitive?.contentOrNull == "system" }
            .mapNotNull { it["content"]?.jsonPrimitive?.contentOrNull }
            .joinToString("\n\n")
        val input = chatMessages
            .filterNot { it["role"]?.jsonPrimitive?.contentOrNull == "system" }
            .map(::toResponsesInputMessage)
            .toMutableList()

        repeat(6) {
            onStatus(AgentRunStatus(AgentRunStatusIcon.THINKING, "正在思考"))
            val decision = parseAgentResponsesTurn(
                post(
                    settings,
                    responsesBody(
                        settings = settings,
                        instructions = instructions + "\n\n" +
                            DayAgentPrompts.ToolDecisionStage,
                        input = input,
                        stream = false,
                        includeTools = true,
                        includeMemoryTool = includeMemoryTool
                    )
                )
            )
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
                return streamFinal(
                    settings = settings,
                    instructions = instructions,
                    input = input,
                    onStatus = onStatus,
                    onDelta = onDelta,
                    onStreamReset = onStreamReset
                )
            }

            // Stateless Responses continuation requires every output item, not just visible text.
            input += decision.outputItems
            decision.calls.forEach { call ->
                onStatus(call.name.runStatus())
                val result = executeTool(call)
                input += buildJsonObject {
                    put("type", "function_call_output")
                    put("call_id", result.callId)
                    put("output", result.content)
                }
            }
        }

        return streamFinal(
            settings = settings,
            instructions = instructions,
            input = input,
            onStatus = onStatus,
            onDelta = onDelta,
            onStreamReset = onStreamReset
        )
    }

    private fun streamFinal(
        settings: AiImportSettings,
        instructions: String,
        input: List<JsonObject>,
        onStatus: (AgentRunStatus) -> Unit,
        onDelta: (String) -> Unit,
        onStreamReset: () -> Unit
    ): String {
        onStatus(AgentRunStatus(AgentRunStatusIcon.THINKING, "整理结果"))
        val finalInstructions = instructions + "\n\n" + DayAgentPrompts.FinalAnswerStage
        val body = responsesBody(
            settings = settings,
            instructions = finalInstructions,
            input = input,
            stream = true,
            includeTools = false,
            includeMemoryTool = false
        )
        return try {
            val gate = AgentFinalOutputGate(onDelta)
            gate.finish(stream(settings, body, gate::accept))
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
                includeMemoryTool = false
            )
            val content = parseAgentResponsesTurn(post(settings, retry)).content
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
        includeMemoryTool: Boolean
    ): JsonObject = buildJsonObject {
        put("model", settings.profile.defaultModel)
        put("store", false)
        put("stream", stream)
        put("instructions", instructions)
        put("input", JsonArray(input))
        put("reasoning", buildJsonObject {
            put("effort", settings.profile.reasoningEffort.apiValue)
            if (
                settings.profile.id == AiProviderPresets.openAI.id &&
                isOfficialOpenAIBaseUrl(settings.profile.baseUrl)
            ) {
                put("summary", "auto")
            }
        })
        if (includeTools) {
            put("tools", agentResponsesToolDefinitions(includeMemoryTool))
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
        onDelta: (String) -> Unit
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
            val content = parseAgentResponsesTurn(response).content
                .takeIf(String::isNotBlank)
                ?: throw MissingResponsesBodyException()
            onDelta(content)
            return content
        }

        val result = StringBuilder()
        BufferedReader(InputStreamReader(connection.inputStream, Charsets.UTF_8)).useLines { lines ->
            lines.forEach { line ->
                if (!line.startsWith("data:")) return@forEach
                val data = line.removePrefix("data:").trim()
                if (data.isBlank() || data == "[DONE]") return@forEach
                val event = runCatching {
                    AgentResponsesJson.parseToJsonElement(data).jsonObject
                }.getOrNull() ?: return@forEach
                when (event["type"]?.jsonPrimitive?.contentOrNull) {
                    "response.output_text.delta" -> {
                        val delta = event["delta"]?.jsonPrimitive?.contentOrNull.orEmpty()
                        if (delta.isNotEmpty()) {
                            result.append(delta)
                            onDelta(delta)
                        }
                    }
                    "response.failed", "error" -> {
                        val message = event["error"]?.jsonObject
                            ?.get("message")?.jsonPrimitive?.contentOrNull
                            ?: "AI 流式响应失败"
                        throw IllegalStateException(message)
                    }
                }
            }
        }
        connection.disconnect()
        return result.toString().takeIf(String::isNotBlank)
            ?: throw MissingResponsesBodyException()
    }

    private fun open(settings: AiImportSettings, body: JsonObject): HttpURLConnection {
        val base = normalizeAiBaseUrlForProvider(settings.profile.id, settings.profile.baseUrl)
        val path = settings.profile.responsesPath.ifBlank { "/responses" }
        return (URL(base.trimEnd('/') + "/" + path.trimStart('/')).openConnection() as HttpURLConnection)
            .apply {
                requestMethod = "POST"
                connectTimeout = 30_000
                readTimeout = 600_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setAiAuthHeader(settings.apiKey, settings.profile.authType)
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
    val outputItems = root["output"]?.jsonArray.orEmpty()
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
            item["content"]?.jsonArray.orEmpty().forEach { part ->
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
        unparsedToolCallCount = functionItems.size - calls.size
    )
}

private class MissingResponsesBodyException : IllegalStateException("AI 没有返回最终正文")
