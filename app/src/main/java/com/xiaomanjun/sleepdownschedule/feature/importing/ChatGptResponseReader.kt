package com.xiaomanjun.sleepdownschedule.feature.importing

import java.io.BufferedReader
import java.net.HttpURLConnection
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** Inspect the body as well as Content-Type: neither a mislabeled SSE nor a complete JSON is empty output. */
internal fun HttpURLConnection.readChatGptResponse(
    checkActive: () -> Unit = {},
    onEvent: (String) -> Unit = {}
): String = inputStream.bufferedReader(Charsets.UTF_8).use {
    it.readChatGptResponse(checkActive, onEvent)
}.also { ChatGptInferenceSessions.checkActive(this) }

internal fun BufferedReader.readChatGptResponse(
    checkActive: () -> Unit = {},
    onEvent: (String) -> Unit = {}
): String {
    checkActive()
    mark(512)
    val prefix = StringBuilder()
    while (prefix.length < 256) {
        val next = read()
        if (next < 0) break
        prefix.append(next.toChar())
        if (prefix.toString().trimStart('\uFEFF', ' ', '\r', '\n', '\t').length >= 8) break
    }
    reset()
    val start = prefix.toString().trimStart('\uFEFF', ' ', '\r', '\n', '\t')
    if (listOf("data:", "event:", "id:", "retry:", ":").any(start::startsWith)) {
        val accumulator = ResponsesSseAccumulator(requireCompleted = true)
        forEachAiSsePayload(checkActive) { payload ->
            accumulator.consume(payload)
            onEvent(payload)
        }
        return accumulator.toResponseJson()
    }
    val body = readAiBoundedText().removePrefix("\uFEFF")
    checkActive()
    val root = runCatching { Json.parseToJsonElement(body) as? JsonObject }.getOrNull()
        ?: throw AiServiceResponseException(
            if (start.startsWith("<")) "ChatGPT 返回了网页而非 AI 响应，请检查网络或代理后重试。"
            else "ChatGPT 返回的内容无法识别，请稍后重试。", "")
    if (root["error"]?.let { it != JsonNull } == true) {
        throw AiServiceResponseException(chatGptInferenceError(0, body), "")
    }
    // A full response is independently terminal. Do not invent a response.completed SSE event.
    if ((root["status"] as? JsonPrimitive)?.contentOrNull != "completed" ||
        (root["output"] !is JsonArray && root["output_text"] !is JsonPrimitive)) {
        ChatGptUsageStatus.failed()
        throw AiServiceResponseException("ChatGPT 未返回完整结果，请重试。", "")
    }
    ChatGptUsageStatus.completed()
    return root.toString()
}
