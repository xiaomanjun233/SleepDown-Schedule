package com.xiaomanjun.sleepdownschedule.core.web

import java.net.HttpURLConnection
import java.net.URL

private val RedirectResponses = setOf(301, 302, 303, 307, 308)

internal fun openWebDownloadConnection(
    url: String,
    userAgent: String?,
    referer: String?,
    cookieForUrl: (String) -> String?,
    receiveCookie: (String, String) -> Unit
): HttpURLConnection {
    var target = URL(url)
    repeat(11) { attempt ->
        require(target.protocol == "http" || target.protocol == "https") { "不支持的下载协议" }
        val connection = target.openConnection() as HttpURLConnection
        try {
            connection.apply {
                instanceFollowRedirects = false
                connectTimeout = 15_000
                readTimeout = 30_000
                requestMethod = "GET"
                if (!userAgent.isNullOrBlank()) setRequestProperty("User-Agent", userAgent)
                cookieForUrl(target.toExternalForm())?.takeIf(String::isNotBlank)?.let {
                    setRequestProperty("Cookie", it)
                }
                downloadReferer(referer, target)?.let { setRequestProperty("Referer", it) }
            }
            val code = connection.responseCode
            connection.headerFields.filterKeys { it.equals("Set-Cookie", ignoreCase = true) }
                .values.flatten().forEach { receiveCookie(target.toExternalForm(), it) }
            if (code !in RedirectResponses) {
                check(code in 200..299) { "HTTP $code" }
                return connection
            }
            check(attempt < 10) { "下载重定向次数过多" }
            val location = connection.getHeaderField("Location") ?: error("下载重定向缺少地址")
            val next = URL(target, location)
            check(target.protocol != "https" || next.protocol == "https") { "下载链接降级为不安全连接" }
            target = next
        } catch (error: Throwable) {
            connection.disconnect()
            throw error
        }
        connection.disconnect()
    }
    error("下载重定向次数过多")
}

internal fun downloadReferer(referer: String?, target: URL): String? {
    val source = referer?.let { runCatching { URL(it) }.getOrNull() } ?: return null
    if (source.protocol !in setOf("http", "https") || source.userInfo != null) return null
    if (source.protocol == "https" && target.protocol != "https") return null
    val sameOrigin = source.protocol == target.protocol && source.host.equals(target.host, true) &&
        source.effectivePort() == target.effectivePort()
    return if (sameOrigin) source.toExternalForm().substringBefore('#')
    else URL(source.protocol, source.host, source.port, "/").toExternalForm()
}

private fun URL.effectivePort(): Int = if (port == -1) defaultPort else port
