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
        val text = errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
        throw AiServiceResponseException(formatAiRequestError(status, text, providerId), text)
    }
    return inputStream.bufferedReader().use { it.readText() }
}

/**
 * Consumes an SSE body, handing each decoded `data:` payload to [onPayload]. Blank payloads and
 * `[DONE]` are skipped; JSON decoding is left to the caller because the two pipelines disagree on
 * how to treat malformed events.
 */
internal fun HttpURLConnection.forEachSseDataLine(onPayload: (String) -> Unit) {
    BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).useLines { lines ->
        lines.forEach { line ->
            if (!line.startsWith("data:")) return@forEach
            val payload = line.removePrefix("data:").trim()
            if (payload.isBlank() || payload == "[DONE]") return@forEach
            onPayload(payload)
        }
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
