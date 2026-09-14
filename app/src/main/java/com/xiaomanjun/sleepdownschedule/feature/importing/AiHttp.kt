package com.xiaomanjun.sleepdownschedule.feature.importing

import com.xiaomanjun.sleepdownschedule.core.remoteconfig.*
import com.xiaomanjun.sleepdownschedule.*
import com.xiaomanjun.sleepdownschedule.feature.backup.*
import android.os.SystemClock
import android.util.Log
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.intOrNull
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicInteger


private fun request(url: String, apiKey: String, method: String, body: ByteArray, contentType: String): String {
    val connection = (URL(url).openConnection() as HttpURLConnection).apply {
        requestMethod = method
        connectTimeout = 30_000
        readTimeout = 600_000
        doOutput = true
        setRequestProperty("Authorization", "Bearer $apiKey")
        setRequestProperty("Content-Type", contentType)
        setRequestProperty("Accept", "application/json")
    }
    return try {
        connection.outputStream.use { it.write(body) }
        val status = connection.responseCode
        val stream = if (status in 200..299) connection.inputStream else connection.errorStream
        val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (status !in 200..299) throw AiServiceResponseException("AI 请求失败 ($status)：${text.take(300)}", text)
        text
    } finally {
        connection.disconnect()
    }
}

private val AiImportRequestSequence = AtomicInteger()

private class AiImportHttpTrace(
    url: String,
    private val providerId: String?,
    private val endpointStyle: AiEndpointStyle,
    private val requestContext: AiImportNetworkContext,
    private val requestBodyBytes: Int
) {
    private val endpoint = URL(url)
    private val startedAt = SystemClock.elapsedRealtime()
    private val requestId = AiImportRequestSequence.incrementAndGet()
        .toString(36)
        .padStart(6, '0')
    private var currentPhase = AiImportHttpPhase.REQUEST_CREATED
    private var eventCount = 0
    private var firstEventElapsedMs: Long? = null
    private var lastEventElapsedMs: Long? = null

    init {
        logPhase(AiImportHttpPhase.REQUEST_CREATED)
    }

    /** Counts a received SSE data payload without logging each event. */
    fun onEvent() {
        eventCount++
        if (firstEventElapsedMs == null) firstEventElapsedMs = elapsedMs()
        lastEventElapsedMs = elapsedMs()
    }

    fun mark(phase: AiImportHttpPhase) {
        currentPhase = phase
        logPhase(phase)
        requestContext.onPhase(phase)
    }

    fun fail(error: Throwable) {
        val cause = error.cause
        Log.e(
            AiImportLogTag,
            commonFields() +
                " phase=REQUEST_FAILED failedAt=${currentPhase.name}" +
                " elapsedMs=${elapsedMs()}" +
                eventStats() +
                processImportanceField() +
                " failure=${error.javaClass.name}" +
                " message=${error.message.orEmpty().replace('\n', ' ').take(240)}" +
                " cause=${cause?.javaClass?.name.orEmpty()}" +
                " causeMessage=${cause?.message.orEmpty().replace('\n', ' ').take(240)}"
        )
    }

    private fun logPhase(phase: AiImportHttpPhase) {
        val withStats = phase == AiImportHttpPhase.FIRST_EVENT || phase == AiImportHttpPhase.STREAM_END
        Log.d(
            AiImportLogTag,
            commonFields() + " phase=${phase.name} elapsedMs=${elapsedMs()}" +
                if (withStats) eventStats() else ""
        )
    }

    private fun eventStats(): String {
        val first = firstEventElapsedMs?.let { " firstEventElapsedMs=$it" }.orEmpty()
        val last = lastEventElapsedMs?.let { " lastEventElapsedMs=$it" }.orEmpty()
        val sinceLast = if (lastEventElapsedMs != null) {
            " msSinceLastEvent=${elapsedMs() - lastEventElapsedMs!!}"
        } else {
            " msSinceLastEvent=no-event"
        }
        return " eventCount=$eventCount$first$last$sinceLast"
    }

    private fun processImportanceField(): String =
        " processImportance=${requestContext.processImportanceProvider()?.let { "IMPORTANCE_$it" } ?: "unavailable"}"

    private fun commonFields(): String =
        "request=$requestId" +
            " provider=${providerId.orEmpty()}" +
            " endpoint=${endpointStyle.name}" +
            " host=${endpoint.host}" +
            " path=${endpoint.path}" +
            " input=${requestContext.inputType}" +
            " bodyBytes=$requestBodyBytes" +
            " images=${requestContext.imageCount}" +
            " screenshots=${requestContext.screenshotCount}"

    private fun elapsedMs(): Long = SystemClock.elapsedRealtime() - startedAt
}

private fun safeRequest(
    url: String,
    apiKey: String,
    method: String,
    body: ByteArray,
    contentType: String,
    authType: AiAuthType = AiAuthType.ApiKeyBearer,
    providerId: String? = null,
    endpointStyle: AiEndpointStyle,
    requestContext: AiImportNetworkContext
): String {
    val trace = AiImportHttpTrace(url, providerId, endpointStyle, requestContext, body.size)
    val connection = (URL(url).openConnection() as HttpURLConnection).apply {
        requestMethod = method
        connectTimeout = 30_000
        readTimeout = 600_000
        doOutput = true
        setAiAuthHeader(apiKey, authType)
        setRequestProperty("Content-Type", contentType)
        setRequestProperty("Accept", "application/json")
    }
    return try {
        trace.mark(AiImportHttpPhase.BODY_WRITE_START)
        connection.outputStream.use { it.write(body) }
        trace.mark(AiImportHttpPhase.BODY_WRITE_END)
        val status = connection.responseCode
        trace.mark(AiImportHttpPhase.HEADERS_RECEIVED)
        if (status !in 200..299) {
            val text = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            throw AiServiceResponseException(formatAiRequestError(status, text, providerId), text)
        }
        trace.mark(AiImportHttpPhase.BODY_READ_START)
        val text = connection.inputStream.bufferedReader().use { it.readText() }
        trace.mark(AiImportHttpPhase.STREAM_END)
        text
    } catch (throwable: Throwable) {
        trace.fail(throwable)
        if (throwable is AiServiceResponseException) throw throwable
        throw IllegalStateException(formatAiNetworkError(url, throwable), throwable)
    } finally {
        connection.disconnect()
    }
}

internal fun HttpURLConnection.setAiAuthHeader(apiKey: String, authType: AiAuthType) {
    when (authType) {
        AiAuthType.ApiKeyBearer,
        AiAuthType.OpenAIProjectKey -> setRequestProperty("Authorization", "Bearer $apiKey")
        AiAuthType.CustomHeader -> setRequestProperty("api-key", apiKey)
    }
}

internal fun redactAiUrl(value: String): String {
    return runCatching {
        val url = URL(value)
        "${url.protocol}://${url.host}${url.path}"
    }.getOrDefault(value.substringBefore('?'))
}

private fun formatAiNetworkError(url: String, throwable: Throwable): String {
    val host = runCatching { URL(url).host }.getOrDefault(url)
    val message = throwable.message.orEmpty()
    val hint = when {
        throwable is IllegalArgumentException ->
            "AI 请求或响应格式不符合接口协议，请检查模型与接口类型。"
        message.contains("Unacceptable certificate", ignoreCase = true) ||
            message.contains("SSLHandshakeException", ignoreCase = true) ||
            message.contains("Trust anchor", ignoreCase = true) ||
            message.contains("certificate", ignoreCase = true) ->
            buildString {
                append("$host 的 HTTPS 证书链没有被 Android 信任。")
                if (host.contains("xiaomimimo.com", ignoreCase = true)) {
                    append("小米 MiMo 普通按量接口应使用 https://api.xiaomimimo.com/v1；Token Plan 应改选“小米 MiMo Token Plan”。")
                }
                append("如果正在使用代理/VPN/抓包工具，请关闭 HTTPS 检查，或确认代理证书已被系统信任；不要在 App 内跳过证书校验。")
            }
        message.contains("failed to connect", ignoreCase = true) ||
            message.contains("connect timed out", ignoreCase = true) ||
            message.contains("Connection refused", ignoreCase = true) ->
            "手机当前网络无法连接到 $host。请尝试切换蜂窝/其他 Wi-Fi，或给手机配置能访问该 API 的代理/VPN。"
        message.contains("Unable to resolve host", ignoreCase = true) ->
            "手机当前网络无法解析 $host。请检查 DNS、网络或代理设置。"
        message.contains("timeout", ignoreCase = true) ->
            "连接 $host 超时。请检查网络可达性，或稍后重试。"
        else -> "无法连接到 $host。请检查手机网络、代理/VPN、接口地址和服务商状态。"
    }
    return "$hint 原始错误：$message"
}

internal fun formatAiRequestError(status: Int, text: String, providerId: String? = null): String {
    if (providerId == AiProviderPresets.dailyFree.id && isManagedFreeLimitError(status, text)) {
        return "今日免费 AI 共享额度已用完，请明天再试，或在 AI 设置中配置自己的 AI 服务。"
    }
    val compact = sanitizeAiOutputForDisplay(text).replace(Regex("\\s+"), " ").take(240)
    val hint = if (
        text.contains("404 page not found", ignoreCase = true) ||
        text.contains("\"code\":\"service_unavailable_error\"", ignoreCase = true)
    ) {
        "接口路径不匹配。若使用第三方兼容站，请确认接口地址包含它要求的版本路径（通常是 /v1），并优先关闭“严格 JSON”。"
    } else {
        null
    }
    return buildString {
        append("AI 请求失败 ($status)")
        hint?.let { append("：").append(it) }
        if (compact.isNotBlank()) append(" 服务返回：").append(compact)
    }
}

private fun isManagedFreeLimitError(status: Int, text: String): Boolean {
    if (status == 429) return true
    val normalized = text.lowercase()
    return listOf(
        "模型超限",
        "额度已用完",
        "额度不足",
        "quota",
        "rate_limit",
        "rate limit",
        "limit exceeded",
        "too many requests"
    ).any(normalized::contains)
}

internal fun postJson(
    url: String,
    apiKey: String,
    body: String,
    authType: AiAuthType = AiAuthType.ApiKeyBearer,
    providerId: String? = null,
    requestContext: AiImportNetworkContext = AiImportNetworkContext("TEXT")
): String {
    return when {
        url.contains("/chat/completions") ->
            postChatCompletionStreaming(url, apiKey, body, authType, providerId, requestContext)
        url.contains("/responses") ->
            postResponsesStreaming(url, apiKey, body, authType, providerId, requestContext)
        else -> safeRequest(
            url,
            apiKey,
            "POST",
            body.toByteArray(Charsets.UTF_8),
            "application/json; charset=utf-8",
            authType,
            providerId,
            AiEndpointStyle.KIMI_FILE_EXTRACT,
            requestContext
        )
    }
}

private fun postChatCompletionStreaming(
    url: String,
    apiKey: String,
    body: String,
    authType: AiAuthType = AiAuthType.ApiKeyBearer,
    providerId: String? = null,
    requestContext: AiImportNetworkContext
): String {
    val streamedBody = runCatching {
        val jsonObject = Json.parseToJsonElement(body).jsonObject
        val mutable = jsonObject.toMutableMap()
        mutable["stream"] = JsonPrimitive(true)
        JsonObject(mutable).toString()
    }.getOrDefault(body)
    val bodyBytes = streamedBody.toByteArray(Charsets.UTF_8)
    val trace = AiImportHttpTrace(
        url,
        providerId,
        AiEndpointStyle.CHAT_COMPLETIONS,
        requestContext,
        bodyBytes.size
    )
    val connection = (URL(url).openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"
        connectTimeout = 30_000
        readTimeout = 600_000
        doOutput = true
        setAiAuthHeader(apiKey, authType)
        setRequestProperty("Content-Type", "application/json; charset=utf-8")
        setRequestProperty("Accept", "text/event-stream")
    }
    return try {
        trace.mark(AiImportHttpPhase.BODY_WRITE_START)
        connection.outputStream.use { it.write(bodyBytes) }
        trace.mark(AiImportHttpPhase.BODY_WRITE_END)
        val status = connection.responseCode
        trace.mark(AiImportHttpPhase.HEADERS_RECEIVED)
        if (status !in 200..299) {
            val text = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            throw AiServiceResponseException(formatAiRequestError(status, text, providerId), text)
        }
        if (!connection.contentType.orEmpty().contains("text/event-stream", ignoreCase = true)) {
            trace.mark(AiImportHttpPhase.BODY_READ_START)
            connection.inputStream.bufferedReader().use { it.readText() }
                .also { trace.mark(AiImportHttpPhase.STREAM_END) }
        } else {
            val accumulator = ChatCompletionSseAccumulator()
            val reasoningPublisher = AiReasoningStreamPublisher(requestContext.onReasoningUpdate)
            var firstEvent = true
            BufferedReader(InputStreamReader(connection.inputStream, Charsets.UTF_8)).useLines { lines ->
                lines.forEach { line ->
                    if (!line.startsWith("data:")) return@forEach
                    val payload = line.removePrefix("data:").trim()
                    if (payload.isBlank() || payload == "[DONE]") return@forEach
                    trace.onEvent()
                    if (firstEvent) {
                        firstEvent = false
                        trace.mark(AiImportHttpPhase.FIRST_EVENT)
                    }
                    accumulator.consume(payload)
                    reasoningPublisher.publish(accumulator.reasoning)
                }
            }
            trace.mark(AiImportHttpPhase.STREAM_END)
            reasoningPublisher.publish(accumulator.reasoning, force = true)
            accumulator.toCompletionJson()
        }
    } catch (throwable: Throwable) {
        trace.fail(throwable)
        if (throwable is AiServiceResponseException) throw throwable
        throw IllegalStateException(formatAiNetworkError(url, throwable), throwable)
    } finally {
        connection.disconnect()
    }
}

private fun postResponsesStreaming(
    url: String,
    apiKey: String,
    body: String,
    authType: AiAuthType,
    providerId: String?,
    requestContext: AiImportNetworkContext
): String {
    val streamedBody = runCatching {
        val values = Json.parseToJsonElement(body).jsonObject.toMutableMap()
        values["stream"] = JsonPrimitive(true)
        JsonObject(values).toString()
    }.getOrDefault(body)
    val bodyBytes = streamedBody.toByteArray(Charsets.UTF_8)
    val trace = AiImportHttpTrace(
        url,
        providerId,
        AiEndpointStyle.RESPONSES,
        requestContext,
        bodyBytes.size
    )
    val connection = (URL(url).openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"
        connectTimeout = 30_000
        readTimeout = 600_000
        doOutput = true
        setAiAuthHeader(apiKey, authType)
        setRequestProperty("Content-Type", "application/json; charset=utf-8")
        setRequestProperty("Accept", "text/event-stream")
    }
    return try {
        trace.mark(AiImportHttpPhase.BODY_WRITE_START)
        connection.outputStream.use { it.write(bodyBytes) }
        trace.mark(AiImportHttpPhase.BODY_WRITE_END)
        val status = connection.responseCode
        trace.mark(AiImportHttpPhase.HEADERS_RECEIVED)
        if (status !in 200..299) {
            val text = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            throw AiServiceResponseException(formatAiRequestError(status, text, providerId), text)
        }
        if (!connection.contentType.orEmpty().contains("text/event-stream", ignoreCase = true)) {
            trace.mark(AiImportHttpPhase.BODY_READ_START)
            connection.inputStream.bufferedReader().use { it.readText() }
                .also { trace.mark(AiImportHttpPhase.STREAM_END) }
        } else {
            val accumulator = ResponsesSseAccumulator()
            val reasoningPublisher = AiReasoningStreamPublisher(requestContext.onReasoningUpdate)
            var firstEvent = true
            BufferedReader(InputStreamReader(connection.inputStream, Charsets.UTF_8)).useLines { lines ->
                lines.forEach { line ->
                    if (!line.startsWith("data:")) return@forEach
                    val payload = line.removePrefix("data:").trim()
                    if (payload.isBlank() || payload == "[DONE]") return@forEach
                    trace.onEvent()
                    if (firstEvent) {
                        firstEvent = false
                        trace.mark(AiImportHttpPhase.FIRST_EVENT)
                    }
                    accumulator.consume(payload)
                    reasoningPublisher.publish(accumulator.reasoning)
                }
            }
            trace.mark(AiImportHttpPhase.STREAM_END)
            reasoningPublisher.publish(accumulator.reasoning, force = true)
            accumulator.toResponseJson()
        }
    } catch (throwable: Throwable) {
        trace.fail(throwable)
        if (throwable is AiServiceResponseException) throw throwable
        throw IllegalStateException(formatAiNetworkError(url, throwable), throwable)
    } finally {
        connection.disconnect()
    }
}

/** Publish a bounded reading window at most ten times per second; parsing retains the full result. */
internal class AiReasoningStreamPublisher(
    private val onUpdate: ((String) -> Unit)?,
    private val nanoTime: () -> Long = System::nanoTime
) {
    private var lastPublishedAt: Long? = null
    private var lastLength = 0

    fun publish(reasoning: CharSequence, force: Boolean = false) {
        if (onUpdate == null || reasoning.isEmpty() || reasoning.length == lastLength) return
        val now = nanoTime()
        if (!force && lastPublishedAt?.let { now - it < 100_000_000L } == true) return
        lastPublishedAt = now
        lastLength = reasoning.length
        onUpdate(reasoning.takeLast(4_000).toString())
    }
}

internal class ChatCompletionSseAccumulator {
    val content = StringBuilder()
    val reasoning = StringBuilder()
    var finishReason = ""
    var sawChunk = false
    private var fullMessage: JsonObject? = null
    private val toolCalls = linkedMapOf<Int, ChatToolCallAccumulator>()

    fun consume(payload: String) {
        val chunk = try {
            Json.parseToJsonElement(payload).jsonObject
        } catch (error: IllegalArgumentException) {
            throw AiServiceResponseException("AI 已连接，但流式响应不是有效的 JSON 对象。", "", error)
        }
        if (chunk["error"] != null && chunk["error"] != JsonNull) {
            throw AiServiceResponseException("AI 服务在流式响应中返回错误。", payload)
        }
        val choice = chunk.optionalArray("choices").firstOrNull()?.jsonObject ?: return
        sawChunk = true
        val delta = choice["delta"].takeUnless { it == JsonNull }?.jsonObject
        if (delta != null) {
            runCatching { delta["content"]?.jsonPrimitive?.contentOrNull }
                .getOrNull()?.takeIf { it.isNotEmpty() }?.let(content::append)
            listOf("reasoning_content", "reasoning").forEach { key ->
                delta[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotEmpty() }?.let { reasoning.append(it) }
            }
            delta.optionalArray("tool_calls").forEachIndexed { fallbackIndex, rawCall ->
                val call = rawCall.jsonObject
                val index = call["index"]?.jsonPrimitive?.intOrNull ?: fallbackIndex
                toolCalls.getOrPut(index, ::ChatToolCallAccumulator).consume(call)
            }
            delta["function_call"].takeUnless { it == JsonNull }?.jsonObject?.let { function ->
                toolCalls.getOrPut(0, ::ChatToolCallAccumulator).consumeFunction(function)
            }
        } else {
            choice["message"].takeUnless { it == JsonNull }?.jsonObject?.let { fullMessage = it }
        }
        choice["finish_reason"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }?.let { finishReason = it }
    }

    fun toCompletionJson(): String {
        if (!sawChunk) throw AiServiceResponseException("AI 流式响应里没有收到任何内容。", "")
        return buildJsonObject {
        put("choices", buildJsonArray {
            add(buildJsonObject {
                put("message", fullMessage ?: buildJsonObject {
                    put("role", JsonPrimitive("assistant"))
                    put("content", JsonPrimitive(content.toString()))
                    if (reasoning.isNotEmpty()) {
                        put("reasoning_content", JsonPrimitive(reasoning.toString()))
                    }
                    if (toolCalls.isNotEmpty()) {
                        put("tool_calls", buildJsonArray {
                            toolCalls.toSortedMap().values.forEach { add(it.toJson()) }
                        })
                    }
                })
                put("finish_reason", JsonPrimitive(finishReason.ifBlank { "stop" }))
            })
        })
        }.toString()
    }
}

/** Optional streaming arrays may be JSON null in provider deltas and usage-only chunks. */
internal fun JsonObject.optionalArray(field: String): List<JsonElement> = when (val value = get(field)) {
    null, JsonNull -> emptyList()
    is JsonArray -> value
    else -> throw AiServiceResponseException("AI 已连接，但响应字段 $field 应为数组或 null。", "")
}

private class ChatToolCallAccumulator {
    private var id = ""
    private var type = "function"
    private val name = StringBuilder()
    private val arguments = StringBuilder()

    fun consume(call: JsonObject) {
        call["id"]?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank)?.let { id = it }
        call["type"]?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank)?.let { type = it }
        call["function"]?.jsonObject?.let(::consumeFunction)
    }

    fun consumeFunction(function: JsonObject) {
        function["name"]?.jsonPrimitive?.contentOrNull?.let(name::append)
        function["arguments"]?.jsonPrimitive?.contentOrNull?.let(arguments::append)
    }

    fun toJson(): JsonObject = buildJsonObject {
        put("id", JsonPrimitive(id.ifBlank { "call_sleepdown" }))
        put("type", JsonPrimitive(type))
        put("function", buildJsonObject {
            put("name", JsonPrimitive(name.toString()))
            put("arguments", JsonPrimitive(arguments.toString()))
        })
    }
}

internal class ResponsesSseAccumulator {
    private var completedResponse: JsonObject? = null
    private val outputItems = linkedMapOf<String, JsonObject>()
    private val functionCalls = linkedMapOf<String, ResponsesFunctionCallAccumulator>()
    private val outputText = StringBuilder()
    val reasoning = StringBuilder()
    private var sawEvent = false

    fun consume(payload: String) {
        val event = runCatching { Json.parseToJsonElement(payload).jsonObject }.getOrNull() ?: return
        sawEvent = true
        when (event["type"]?.jsonPrimitive?.contentOrNull.orEmpty()) {
            "response.completed", "response.incomplete" -> completedResponse = event["response"]?.jsonObject
            "response.output_item.added", "response.output_item.done" -> {
                val item = event["item"]?.jsonObject ?: return
                val key = item["id"]?.jsonPrimitive?.contentOrNull
                    ?: event["output_index"]?.jsonPrimitive?.intOrNull?.toString()
                    ?: outputItems.size.toString()
                outputItems[key] = item
                if (item["type"]?.jsonPrimitive?.contentOrNull == "function_call") {
                    functionCalls.getOrPut(key, ::ResponsesFunctionCallAccumulator).seed(item)
                }
            }
            "response.function_call_arguments.delta" -> {
                val key = event["item_id"]?.jsonPrimitive?.contentOrNull
                    ?: event["output_index"]?.jsonPrimitive?.intOrNull?.toString()
                    ?: "0"
                functionCalls.getOrPut(key, ::ResponsesFunctionCallAccumulator)
                    .append(event["delta"]?.jsonPrimitive?.contentOrNull.orEmpty())
            }
            "response.function_call_arguments.done" -> {
                val key = event["item_id"]?.jsonPrimitive?.contentOrNull
                    ?: event["output_index"]?.jsonPrimitive?.intOrNull?.toString()
                    ?: "0"
                functionCalls.getOrPut(key, ::ResponsesFunctionCallAccumulator)
                    .finish(event["arguments"]?.jsonPrimitive?.contentOrNull.orEmpty())
            }
            "response.output_text.delta" ->
                event["delta"]?.jsonPrimitive?.contentOrNull?.let(outputText::append)
            "response.reasoning_summary_text.delta", "response.reasoning_text.delta" ->
                event["delta"]?.jsonPrimitive?.contentOrNull?.let(reasoning::append)
            "response.failed", "error" -> {
                val detail = event["error"]?.jsonObject?.get("message")
                    ?.jsonPrimitive?.contentOrNull.orEmpty()
                throw AiServiceResponseException(detail.ifBlank { "AI Responses 流式请求失败。" }, payload)
            }
        }
    }

    fun toResponseJson(): String {
        completedResponse?.let { return it.toString() }
        if (!sawEvent) throw AiServiceResponseException("AI Responses 流式响应里没有收到任何事件。", "")
        val functionKeys = functionCalls.keys
        val items = outputItems.filterKeys { it !in functionKeys }.values.toMutableList()
        items += functionCalls.values.map(ResponsesFunctionCallAccumulator::toJson)
        if (reasoning.isNotEmpty()) {
            items += buildJsonObject {
                put("type", JsonPrimitive("reasoning"))
                put("summary", buildJsonArray {
                    add(buildJsonObject {
                        put("type", JsonPrimitive("summary_text"))
                        put("text", JsonPrimitive(reasoning.toString()))
                    })
                })
            }
        }
        if (outputText.isNotEmpty()) {
            items += buildJsonObject {
                put("type", JsonPrimitive("message"))
                put("role", JsonPrimitive("assistant"))
                put("content", buildJsonArray {
                    add(buildJsonObject {
                        put("type", JsonPrimitive("output_text"))
                        put("text", JsonPrimitive(outputText.toString()))
                    })
                })
            }
        }
        return buildJsonObject {
            put("status", JsonPrimitive("completed"))
            put("output", JsonArray(items))
            if (outputText.isNotEmpty()) put("output_text", JsonPrimitive(outputText.toString()))
        }.toString()
    }
}

private class ResponsesFunctionCallAccumulator {
    private var id = ""
    private var callId = ""
    private var name = ""
    private val arguments = StringBuilder()

    fun seed(item: JsonObject) {
        id = item["id"]?.jsonPrimitive?.contentOrNull.orEmpty()
        callId = item["call_id"]?.jsonPrimitive?.contentOrNull.orEmpty()
        name = item["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
        item["arguments"]?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotEmpty)?.let {
            arguments.clear()
            arguments.append(it)
        }
    }

    fun append(delta: String) {
        arguments.append(delta)
    }

    fun finish(value: String) {
        if (value.isNotEmpty()) {
            arguments.clear()
            arguments.append(value)
        }
    }

    fun toJson(): JsonObject = buildJsonObject {
        put("type", JsonPrimitive("function_call"))
        if (id.isNotBlank()) put("id", JsonPrimitive(id))
        put("call_id", JsonPrimitive(callId.ifBlank { id.ifBlank { "call_sleepdown" } }))
        put("name", JsonPrimitive(name))
        put("arguments", JsonPrimitive(arguments.toString()))
    }
}

internal fun parseChatCompletionTextResult(response: String, requireContent: Boolean = true): AiProviderTextResult {
    val choice = Json.parseToJsonElement(response)
        .jsonObject["choices"]?.jsonArray?.firstOrNull()
        ?.jsonObject
        ?: throw AiServiceResponseException("AI 响应里没有找到 choices[0]。", response)
    val message = choice["message"]?.jsonObject
        ?: throw AiServiceResponseException("AI 响应里没有找到 choices[0].message。", response)
    val content = message["content"]?.jsonPrimitive?.contentOrNull.orEmpty()
    val finishReason = choice["finish_reason"]?.jsonPrimitive?.contentOrNull.orEmpty()
    val reasoning = buildList {
        listOf("reasoning_content", "reasoning", "thoughts", "chain_of_thought").forEach { key ->
            message[key]?.let { addAll(collectTextLeaves(it)) }
        }
        addAll(collectInlineReasoningBlocks(content))
    }.map { it.trim() }.filter { it.isNotBlank() }.distinct().joinToString("\n\n")
    val finalContent = content.stripInlineReasoningBlocks().trim()
    if (requireContent && finalContent.isBlank()) {
        val detail = if (finishReason == "length") {
            "思考过程耗尽了输出额度，最终正文被截断。"
        } else {
            "模型没有返回最终正文。"
        }
        throw AiServiceResponseException("AI 只返回了思考过程，$detail 当前已保留 DeepSeek thinking；请重试，或提高输出额度后再试。", response)
    }
    return AiProviderTextResult(content = finalContent, reasoning = reasoning, finishReason = finishReason)
}

internal fun parseResponsesTextResult(response: String): AiProviderTextResult {
    val root = Json.parseToJsonElement(response).jsonObject
    val rootText = root["output_text"]?.jsonPrimitive?.contentOrNull.orEmpty()
    val contentParts = mutableListOf<String>()
    val reasoningParts = collectResponsesReasoning(root).toMutableList()
    val output = root.optionalArray("output")
    output.forEach { item ->
        val itemObject = item.jsonObject
        if (itemObject["type"]?.jsonPrimitive?.contentOrNull == "reasoning") return@forEach
        itemObject.optionalArray("content").forEach { responseContent ->
            val contentObject = responseContent.jsonObject
            contentObject["text"]?.jsonPrimitive?.contentOrNull
                ?.takeIf(String::isNotBlank)
                ?.let(contentParts::add)
        }
    }
    val finalContent = rootText.ifBlank { contentParts.joinToString("\n") }.trim()
    if (finalContent.isBlank()) {
        throw AiServiceResponseException("AI 没有返回课程表内容。", response)
    }
    return AiProviderTextResult(
        content = finalContent,
        reasoning = reasoningParts.distinct().joinToString("\n\n"),
        finishReason = root["status"]?.jsonPrimitive?.contentOrNull.orEmpty()
    )
}

internal fun collectResponsesReasoning(root: JsonObject): List<String> = buildList {
    root.optionalArray("output").forEach { item ->
        val itemObject = item.jsonObject
        if (itemObject["type"]?.jsonPrimitive?.contentOrNull != "reasoning") return@forEach
        itemObject.optionalArray("summary").forEach { summary ->
            summary.jsonObject["text"]?.jsonPrimitive?.contentOrNull
                ?.takeIf(String::isNotBlank)
                ?.let(::add)
        }
        itemObject.optionalArray("content").forEach { content ->
            content.jsonObject["text"]?.jsonPrimitive?.contentOrNull
                ?.takeIf(String::isNotBlank)
                ?.let(::add)
        }
    }
}.map(String::trim).filter(String::isNotBlank).distinct()

internal fun redactReasoningFields(output: String): String {
    return runCatching {
        redactReasoningFields(Json.parseToJsonElement(output)).toString()
    }.getOrElse { output }
}

private fun redactReasoningFields(element: JsonElement): JsonElement {
    return when (element) {
        is JsonObject -> JsonObject(element.mapValues { (key, value) ->
            if (isReasoningKey(key)) JsonPrimitive("【思考过程已拆分到单独区域】") else redactReasoningFields(value)
        })
        is JsonArray -> JsonArray(element.map { redactReasoningFields(it) })
        else -> element
    }
}

internal fun collectReasoningText(output: String): List<String> {
    val texts = mutableListOf<String>()
    texts += collectInlineReasoningBlocks(output)
    runCatching { Json.parseToJsonElement(output) }
        .onSuccess { texts += collectReasoningFields(it) }
    return texts.map { it.trim() }.filter { it.isNotBlank() }.distinct()
}

private fun collectReasoningFields(element: JsonElement): List<String> {
    return when (element) {
        is JsonObject -> element.flatMap { (key, value) ->
            if (isReasoningKey(key)) collectTextLeaves(value) else collectReasoningFields(value)
        }
        is JsonArray -> element.flatMap { collectReasoningFields(it) }
        else -> emptyList()
    }
}

private fun collectTextLeaves(element: JsonElement): List<String> {
    return when (element) {
        is JsonPrimitive -> listOfNotNull(element.contentOrNull)
        is JsonObject -> element.values.flatMap { collectTextLeaves(it) }
        is JsonArray -> element.flatMap { collectTextLeaves(it) }
    }
}

private fun isReasoningKey(key: String): Boolean {
    val normalized = key.lowercase()
    return normalized == "reasoning_content" ||
        normalized == "reasoning" ||
        normalized == "thoughts" ||
        normalized == "chain_of_thought" ||
        normalized.contains("reasoning_content")
}

private val ThinkBlockRegexes = listOf(
    Regex("<think[^>]*>.*?</think>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)),
    Regex("<thinking[^>]*>.*?</thinking>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
)

internal fun String.stripInlineReasoningBlocks(): String {
    return ThinkBlockRegexes.fold(this) { current, regex -> regex.replace(current, "") }
}

private fun collectInlineReasoningBlocks(text: String): List<String> {
    return ThinkBlockRegexes.flatMap { regex ->
        regex.findAll(text).map { match ->
            match.value
                .replace(Regex("</?thinking?[^>]*>", RegexOption.IGNORE_CASE), "")
                .trim()
        }.toList()
    }.filter { it.isNotBlank() }
}

