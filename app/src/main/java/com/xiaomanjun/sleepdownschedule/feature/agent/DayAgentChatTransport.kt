package com.xiaomanjun.sleepdownschedule.feature.agent

import com.xiaomanjun.sleepdownschedule.feature.importing.*

import com.xiaomanjun.sleepdownschedule.*

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.net.HttpURLConnection
import java.net.URL

private val AgentChatJson = Json { ignoreUnknownKeys = true; isLenient = true }

internal fun AiImportSettings.usesOfficialOpenAiEndpoint(): Boolean =
    profile.id == AiProviderPresets.openAI.id &&
        isOfficialOpenAIBaseUrl(normalizeAiBaseUrlForProvider(profile.id, profile.baseUrl))

internal fun AiImportSettings.usesDeepSeekChatEndpoint(): Boolean =
    profile.id == AiProviderPresets.deepSeek.id ||
        runCatching {
            URL(normalizeAiBaseUrlForProvider(profile.id, profile.baseUrl)).host
                .equals("api.deepseek.com", ignoreCase = true)
        }.getOrDefault(false)

/**
 * OpenAI-compatible Chat Completions wire transport.
 *
 * Agent orchestration and local tool execution stay in [DayAgentService]; this class owns request
 * serialization, authentication, HTTP, and streaming response normalization.
 */
internal class DayAgentChatTransport(
    private val interaction: AiImportInteraction? = null,
    private val onReasoning: ((String) -> Unit)? = null
) {
    fun post(settings: AiImportSettings, body: String): String {
        if (interaction != null) {
            val path = settings.profile.chatCompletionsPath.trim('/')
            val base = if (path.isEmpty()) settings.profile.baseUrl.trim().trimEnd('/')
                else normalizeAiBaseUrlForProvider(settings.profile.id, settings.profile.baseUrl).trimEnd('/')
            return postJson(if (path.isEmpty()) base else "$base/$path", settings.apiKey, body,
                settings.profile.authType, settings.profile.id,
                AiImportNetworkContext("AGENT", interaction = interaction, onReasoningUpdate = onReasoning, onPhase = interaction.onHttpPhase))
        }
        val connection = openConnection(settings, body)
        return try {
            connection.readResponse(settings.profile.id)
        } catch (error: Throwable) {
            interaction?.checkActive()
            throw error
        } finally {
            connection.disconnect()
        }
    }

    fun stream(
        settings: AiImportSettings,
        body: String,
        onDelta: (String) -> Unit,
        onUsage: (AgentTokenUsage) -> Unit
    ): String {
        val connection = openConnection(settings, body)
        return try {
            val code = connection.responseCode
            if (code !in 200..299) {
                val error = connection.errorStream
                    ?.bufferedReader()
                    ?.use { it.readAiBoundedText() }
                    .orEmpty()
                    .take(300)
                throw AiServiceResponseException(formatAiRequestError(code, error, settings.profile.id), error, httpStatus = code)
            }
            if (!connection.contentType.orEmpty().contains("text/event-stream", ignoreCase = true)) {
                val response = connection.inputStream.bufferedReader().use { it.readAiBoundedText() }
                interaction?.checkActive()
                onUsage(parseAgentTokenUsage(response))
                val content = parseFullChatContent(response)
                onDelta(content)
                content
            } else {
                val result = StringBuilder()
                val importStream = interaction?.let { ChatCompletionSseAccumulator() }
                var hasFinalContent = false
                var finishReason = ""
                connection.forEachSseDataLine(checkActive = { interaction?.checkActive() }) { data ->
                    importStream?.consume(data)
                    interaction?.publishActivity(importStream?.activity)
                    val event = parseSseJsonObject(data) ?: return@forEachSseDataLine
                    val usage = agentTokenUsage(event)
                    if (!usage.isEmpty) onUsage(usage)
                    val content = runCatching {
                        val choice = event["choices"]
                            ?.jsonArray
                            ?.firstOrNull()
                            ?.jsonObject
                            ?: return@runCatching ""
                        (choice["finish_reason"] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull
                            ?.takeIf(String::isNotBlank)?.let { finishReason = it }
                        val streamed = choice["delta"]?.jsonObject
                        agentTextFromJson(streamed?.get("content"))
                            .ifBlank { agentTextFromJson(choice["text"]) }
                    }.getOrNull().orEmpty()
                    if (content.isNotEmpty()) {
                        hasFinalContent = true
                        result.append(content)
                        onDelta(content)
                    }
                }
                check(finishReason == "stop") { "AI 最终回复未正常结束，请重试。" }
                if (!hasFinalContent) throw MissingAgentBodyException()
                result.toString()
            }
        } catch (error: Throwable) {
            interaction?.checkActive()
            throw error
        } finally {
            connection.disconnect()
        }
    }

    fun body(
        settings: AiImportSettings,
        messages: List<Pair<String, String>>,
        stream: Boolean
    ): String = buildJsonObject {
        put("model", settings.profile.defaultModel)
        put("stream", stream)
        put("temperature", 0.55)
        put("messages", buildJsonArray {
            messages.forEach { (role, content) ->
                add(buildJsonObject {
                    put("role", role)
                    put("content", content)
                })
            }
        })
        if (!stream && settings.profile.structuredOutputMode == StructuredOutputMode.JSON_OBJECT) {
            put("response_format", buildJsonObject { put("type", "json_object") })
        }
    }.toString()

    fun agentBody(
        settings: AiImportSettings,
        messages: List<JsonObject>,
        stream: Boolean,
        includeTools: Boolean,
        includeMemoryTool: Boolean = false,
        forceMiMoWebSearch: Boolean = false,
        excludedTools: Set<AgentToolName> = emptySet(),
        importWorkspace: Boolean = false
    ): String = buildJsonObject {
        put("model", settings.profile.defaultModel)
        put("stream", stream)
        put("temperature", 0.35)
        if (settings.usesOfficialOpenAiEndpoint()) put("store", false)
        put("messages", buildJsonArray { messages.forEach(::add) })
        if (includeTools) {
            put(
                "tools",
                agentToolDefinitions(
                    includeMiMoWebSearch = !importWorkspace && supportsMiMoOfficialWebSearch(
                        providerId = settings.profile.id,
                        baseUrl = normalizeAiBaseUrlForProvider(
                            settings.profile.id,
                            settings.profile.baseUrl
                        ),
                        model = settings.profile.defaultModel
                    ),
                    forceMiMoWebSearch = !importWorkspace && forceMiMoWebSearch,
                    includeMemoryTool = includeMemoryTool,
                    // Several compatible providers reject this Chat Completions extension.
                    strictFunctions = settings.usesOfficialOpenAiEndpoint(),
                    excludedTools = excludedTools,
                    importWorkspace = importWorkspace
                )
            )
            put("tool_choice", "auto")
        } else if (settings.usesDeepSeekChatEndpoint()) {
            // DeepSeek otherwise occasionally serializes an imagined function as DSML text even
            // though this final-answer request intentionally exposes no native tools.
            put("tool_choice", "none")
        }
    }.toString()

    private fun openConnection(
        settings: AiImportSettings,
        body: String
    ): HttpURLConnection {
        val path = settings.profile.chatCompletionsPath.trim('/')
        val base = if (path.isEmpty()) {
            // 未显式配置路径：直接使用下发的完整地址，不再 normalize 剥掉 /chat/completions 等后缀
            settings.profile.baseUrl.trim().trimEnd('/')
        } else {
            normalizeAiBaseUrlForProvider(settings.profile.id, settings.profile.baseUrl).trimEnd('/')
        }
        val connection = openAiPostConnection(
            url = if (path.isEmpty()) base else "$base/$path",
            apiKey = settings.apiKey,
            authType = settings.profile.authType,
            contentType = "application/json; charset=utf-8",
            accept = null
        )
        try {
            interaction?.attach(connection)
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            return connection
        } catch (error: Throwable) {
            connection.disconnect()
            interaction?.checkActive()
            throw error
        }
    }
}

internal fun parseFullChatContent(response: String): String {
    val root = AgentChatJson.parseToJsonElement(response).jsonObject
    val choice = root["choices"]?.jsonArray?.firstOrNull()?.jsonObject
        ?: throw MissingAgentBodyException()
    val message = choice["message"]?.jsonObject
    return agentTextFromJson(message?.get("content"))
        .ifBlank { agentTextFromJson(choice["text"]) }
        .takeIf(String::isNotBlank)
        ?: throw MissingAgentBodyException()
}

internal class MissingAgentBodyException : IllegalStateException("AI 没有返回最终正文")

private fun HttpURLConnection.readResponse(providerId: String): String {
    val code = responseCode
    val stream = if (code in 200..299) inputStream else errorStream
    val text = stream?.bufferedReader()?.use { it.readAiBoundedText() }.orEmpty()
    if (code !in 200..299) {
        throw AiServiceResponseException(formatAiRequestError(code, text, providerId), text, httpStatus = code)
    }
    return text
}
