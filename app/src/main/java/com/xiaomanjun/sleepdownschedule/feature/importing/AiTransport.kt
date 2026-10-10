package com.xiaomanjun.sleepdownschedule.feature.importing

import com.xiaomanjun.sleepdownschedule.core.remoteconfig.*
import com.xiaomanjun.sleepdownschedule.*
import com.xiaomanjun.sleepdownschedule.feature.backup.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * Provider-neutral HTTP/SSE primitives shared by the AI import pipeline and the day agent.
 *
 * This layer owns only wire mechanics: connection construction, authentication headers, timeouts,
 * status-code handling, error formatting and the `data:` line loop. It deliberately does not parse
 * provider semantics (SSE payload shapes, tool calls, reasoning, usage), does not create trace
 * events and does not choose an exception type for callers. Both callers keep their own behaviour:
 * the import pipeline wraps failures in [AiServiceResponseException] and records
 * [AiImportHttpTrace] phases, while the day agent keeps its [IllegalStateException] wrappers.
 */
internal const val AiDefaultConnectTimeoutMs = 30_000
internal const val AiDefaultReadTimeoutMs = 600_000
internal const val AiStreamResponseLimitChars = 2 * 1024 * 1024

/** A hard failure keeps oversized/truncated JSON from being mistaken for a usable artifact. */
internal class AiStreamResponseBudget {
    private var receivedChars = 0L
    fun record(payload: CharSequence) {
        receivedChars += payload.length
        if (receivedChars > AiStreamResponseLimitChars) throw AiServiceResponseException(
            "AI 响应超过安全大小限制，未采用未完成的数据。请缩小导入范围后重试。", "")
    }
}

internal fun HttpURLConnection.setAiAuthHeader(apiKey: String, authType: AiAuthType) {
    when (authType) {
        AiAuthType.ApiKeyBearer,
        AiAuthType.OpenAIProjectKey -> setRequestProperty("Authorization", "Bearer $apiKey")
        AiAuthType.CustomHeader -> setRequestProperty("api-key", apiKey)
    }
}

/**
 * Builds a configured POST connection. Body writing stays with the caller so the import pipeline
 * can keep its `BODY_WRITE_START`/`BODY_WRITE_END` trace phases around the actual write.
 */
internal fun openAiPostConnection(
    url: String,
    apiKey: String,
    authType: AiAuthType = AiAuthType.ApiKeyBearer,
    contentType: String = "application/json; charset=utf-8",
    accept: String? = "application/json",
    method: String = "POST",
    connectTimeoutMs: Int = AiDefaultConnectTimeoutMs,
    readTimeoutMs: Int = AiDefaultReadTimeoutMs
): HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
    requestMethod = method
    connectTimeout = connectTimeoutMs
    // Streaming providers may legitimately pause while reasoning. This is an inactivity timeout,
    // not a total request deadline; keep it long enough for those pauses.
    readTimeout = readTimeoutMs
    doOutput = true
    setAiAuthHeader(apiKey, authType)
    setRequestProperty("Content-Type", contentType)
    accept?.let { setRequestProperty("Accept", it) }
}

/** Reads a non-streaming body, converting any non-2xx status into a provider-facing error. */
internal fun HttpURLConnection.readAiBodyOrThrow(providerId: String? = null): String {
    val status = responseCode
    if (status !in 200..299) {
        val text = errorStream?.bufferedReader()?.use { it.readAiBoundedText() }.orEmpty()
        throw AiServiceResponseException(formatAiRequestError(status, text, providerId), text, httpStatus = status)
    }
    return inputStream.bufferedReader().use { it.readAiBoundedText() }
}

/**
 * Consumes framed SSE events, joining multiple data lines and stopping at `[DONE]` even if the
 * server leaves its socket open. Comments and event metadata never become model output.
 */
internal fun HttpURLConnection.forEachSseDataLine(
    checkActive: () -> Unit = {},
    onPayload: (String) -> Unit
) {
    BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).use {
        it.forEachAiSsePayload(checkActive, onPayload)
    }
}

internal fun BufferedReader.forEachAiSsePayload(
    checkActive: () -> Unit = {},
    onPayload: (String) -> Unit
) {
    val data = StringBuilder()
    val budget = AiStreamResponseBudget()
    fun dispatch(): Boolean {
        val payload = data.toString().trim()
        data.clear()
        if (payload == "[DONE]") return false
        if (payload.isNotEmpty()) {
            checkActive()
            budget.record(payload)
            onPayload(payload)
        }
        return true
    }
    var firstLine = true
    while (true) {
        checkActive()
        val raw = readBoundedAiSseLine() ?: break
        val line = if (firstLine) raw.removePrefix("\uFEFF") else raw
        firstLine = false
        if (line.isEmpty()) {
            if (!dispatch()) return
        } else if (line == "data" || line.startsWith("data:")) {
            val value = line.removePrefix("data").removePrefix(":").removePrefix(" ")
            // Several compatible APIs omit blank separators between complete JSON events.
            if (data.isNotEmpty() && (data.toString().trim() == "[DONE]" || parseSseJsonObject(data.toString()) != null)) {
                if (!dispatch()) return
            }
            if (data.isNotEmpty()) data.append('\n')
            if (data.length.toLong() + value.length > AiStreamResponseLimitChars) {
                throw AiServiceResponseException("AI 流式事件超过安全大小限制，请缩小导入范围后重试。", "")
            }
            data.append(value)
            if (data.toString().trim() == "[DONE]") return
        }
    }
    dispatch()
}

/** BufferedReader.readLine itself has no bound; cap before a hostile single line can grow freely. */
private fun BufferedReader.readBoundedAiSseLine(): String? {
    val line = StringBuilder()
    while (true) {
        val next = read()
        if (next == -1) return line.toString().takeIf { line.isNotEmpty() }
        if (next == '\n'.code) return line.toString()
        if (next == '\r'.code) {
            mark(1)
            if (read() != '\n'.code) reset()
            return line.toString()
        }
        if (line.length >= AiStreamResponseLimitChars) {
            throw AiServiceResponseException("AI 流式事件超过安全大小限制，请缩小导入范围后重试。", "")
        }
        line.append(next.toChar())
    }
}

internal fun BufferedReader.readAiBoundedText(): String {
    val result = StringBuilder()
    val buffer = CharArray(8_192)
    while (true) {
        val count = read(buffer)
        if (count < 0) return result.toString()
        if (result.length.toLong() + count > AiStreamResponseLimitChars) {
            throw AiServiceResponseException("AI 响应超过安全大小限制，请缩小导入范围后重试。", "")
        }
        result.append(buffer, 0, count)
    }
}

/** SSE payloads that fail to decode are treated as keep-alive noise by every caller. */
internal fun parseSseJsonObject(payload: String): JsonObject? =
    runCatching { Json.parseToJsonElement(payload).jsonObject }.getOrNull()

internal fun redactAiUrl(value: String): String {
    return runCatching {
        val url = URL(value)
        "${url.protocol}://${url.host}${url.path}"
    }.getOrDefault(value.substringBefore('?'))
}

internal fun formatAiNetworkError(url: String, throwable: Throwable): String {
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
    // Managed credentials still call the provider directly. An HTTP failure cannot tell us
    // that SleepDown's daily allowance is exhausted, nor when the provider will recover.
    // Preserve the status and response for diagnosis just as with a user's own credentials.
    val service = if (providerId == AiProviderPresets.dailyFree.id) "免费 AI" else "AI"
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
        append("$service 请求失败 ($status)")
        hint?.let { append("：").append(it) }
        if (compact.isNotBlank()) append(" 服务返回：").append(compact)
    }
}
