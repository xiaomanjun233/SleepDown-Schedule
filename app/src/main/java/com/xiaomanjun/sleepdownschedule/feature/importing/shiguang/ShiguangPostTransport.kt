package com.xiaomanjun.sleepdownschedule.feature.importing.shiguang

import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

/** The caller owns the returned connection until its response stream closes. */
internal fun openShiguangPostConnection(
    post: RegisteredWebPost,
    headers: Map<String, String>,
    mainFrame: Boolean,
    cookieForUrl: (String) -> String?,
    receiveCookie: (String, String) -> Unit
): HttpURLConnection {
    var url = post.url
    var method = post.method
    var body: ByteArray? = post.body.toByteArray(Charsets.UTF_8)
    repeat(6) { redirect ->
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 30_000
            connection.readTimeout = 60_000
            connection.instanceFollowRedirects = false
            connection.requestMethod = method
            headers.forEach { (key, value) ->
                if (key.lowercase() !in OmittedPostHeaders && (body != null || !key.equals("Content-Type", true))) {
                    connection.setRequestProperty(key, value)
                }
            }
            connection.setRequestProperty("Accept-Encoding", "identity")
            if (post.allowCookies) cookieForUrl(url)?.takeIf(String::isNotBlank)?.let {
                connection.setRequestProperty("Cookie", it)
            }
            body?.let { bytes ->
                if (post.contentType.isNotBlank()) connection.setRequestProperty("Content-Type", post.contentType)
                connection.doOutput = true
                connection.setFixedLengthStreamingMode(bytes.size)
                connection.outputStream.use { it.write(bytes) }
            }
            val status = connection.responseCode
            if (post.allowCookies) connection.headerFields.entries.filter { it.key.equals("Set-Cookie", true) }
                .flatMap { it.value }.forEach { receiveCookie(url, it) }
            if (mainFrame || status !in setOf(301, 302, 303, 307, 308)) return connection
            require(redirect < 5) { "Too many redirects" }
            val destination = URI(url).resolve(requireNotNull(connection.getHeaderField("Location"))).toString()
            // Cross-origin redirect bodies need WebView's CORS checks; never expose them through this bridge.
            require(sameWebOrigin(post.url, destination)) { "Cross-origin redirect requires browser transport" }
            if (status == 303 || (status in setOf(301, 302) && method == "POST")) {
                method = "GET"
                body = null
            }
            connection.disconnect()
            url = destination
        } catch (error: Exception) {
            connection.disconnect()
            throw error
        }
    }
    error("Redirect limit exceeded")
}

private val OmittedPostHeaders = setOf("x-requested-with", "x-webview-post-id", "accept-encoding", "content-length", "cookie")
