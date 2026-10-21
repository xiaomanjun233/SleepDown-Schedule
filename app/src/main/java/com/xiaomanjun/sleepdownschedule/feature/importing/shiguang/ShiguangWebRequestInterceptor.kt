package com.xiaomanjun.sleepdownschedule.feature.importing.shiguang

import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import org.json.JSONObject
import java.io.FilterInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.util.zip.GZIPInputStream

internal class ShiguangWebRequestInterceptor {
    private val registry = ShiguangPostRegistry()
    private val cookies = CookieManager.getInstance()
    val bridge = WebPostService(registry)
    @Volatile var documentUrl: String? = null

    fun close() = registry.close()

    fun intercept(request: WebResourceRequest): WebResourceResponse? {
        val rawUrl = request.url.toString()
        if (request.url.scheme !in setOf("http", "https")) return null
        val requestId = request.requestHeaders.entries.firstOrNull {
            it.key.equals("X-WebView-Post-Id", ignoreCase = true)
        }?.value ?: request.url.getQueryParameter("_webview_post_id") ?: return null
        val url = withoutInternalPostId(rawUrl)
        val data = registry.take(requestId, url, request.method.uppercase())
            ?: return failure(409, "Conflict", "请求内容已失效，请返回页面后重新提交。")
        // JavaScript cannot use the native transport to bypass cross-origin fetch restrictions.
        if (!request.isForMainFrame && !sameWebOrigin(url, documentUrl)) {
            return failure(403, "Forbidden", "此请求需要由网页直接发送。")
        }
        var connection: HttpURLConnection? = null
        return try {
            val opened = openShiguangPostConnection(
                data, request.requestHeaders, request.isForMainFrame,
                cookieForUrl = cookies::getCookie,
                receiveCookie = { destination, value -> cookies.setCookie(destination, value) }
            )
            connection = opened
            cookies.flush()
            val status = opened.responseCode
            if (status in 300..399 && request.isForMainFrame) {
                // Only redirects that switch POST to GET can be represented as page navigation.
                require(status in setOf(301, 302, 303)) { "Redirect requires retaining the request body" }
                val destination = URI(url).resolve(requireNotNull(opened.getHeaderField("Location")))
                require(destination.scheme in setOf("http", "https"))
                opened.disconnect()
                val html = "<html><script>window.location.replace(${JSONObject.quote(destination.toString())});</script></html>"
                return WebResourceResponse("text/html", "UTF-8", 200, "OK",
                    mapOf("Cache-Control" to "no-store"), html.byteInputStream())
            }
            val contentType = opened.contentType.orEmpty()
            val mime = contentType.substringBefore(';').ifBlank { "text/html" }
            val encoding = Regex("charset=([^;]+)", RegexOption.IGNORE_CASE)
                .find(contentType)?.groupValues?.get(1)?.trim()?.trim('"') ?: "UTF-8"
            val headers = opened.headerFields.filterKeys {
                it != null && it.lowercase() !in setOf("content-encoding", "content-length", "set-cookie")
            }.mapValues { (_, values) -> values.joinToString(", ") }
            val stream = (if (status >= 400) opened.errorStream else opened.inputStream)
                ?: ByteArray(0).inputStream()
            val decoded = if (opened.contentEncoding.equals("gzip", true)) GZIPInputStream(stream) else stream
            WebResourceResponse(mime, encoding, status, opened.responseMessage.orEmpty().ifBlank { "OK" },
                headers, decoded.disconnectWith(opened))
        } catch (_: Exception) {
            connection?.disconnect()
            // The server may already have received the POST. Returning null would send it again.
            failure(502, "Bad Gateway", "请求未能完成，请检查网络；重新提交前请先确认页面中的操作结果。")
        }
    }

    private fun failure(status: Int, reason: String, message: String) = WebResourceResponse(
        "text/plain", "UTF-8", status, reason, mapOf("Cache-Control" to "no-store"), message.byteInputStream()
    )

    private fun InputStream.disconnectWith(connection: HttpURLConnection): InputStream =
        object : FilterInputStream(this) {
            override fun close() {
                try { super.close() } finally { connection.disconnect() }
            }
        }

    internal class WebPostService(private val registry: ShiguangPostRegistry) {
        @JavascriptInterface
        fun register(id: String, body: String, contentType: String, url: String, method: String, allowCookies: Boolean): Boolean =
            registry.register(id, body, contentType, url, method, allowCookies)
    }

}
