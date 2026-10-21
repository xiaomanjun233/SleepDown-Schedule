package com.xiaomanjun.sleepdownschedule.feature.importing

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.net.URI
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

internal fun eduWebOrigin(url: String?): String? = runCatching {
    val uri = URI(url ?: return null)
    val scheme = uri.scheme?.lowercase(Locale.ROOT)
    if (scheme != "http" && scheme != "https") return null
    val host = uri.host?.lowercase(Locale.ROOT) ?: return null
    val port = if (uri.port >= 0) uri.port else if (scheme == "https") 443 else 80
    "$scheme://$host:$port"
}.getOrNull()

internal fun Map<String, String>.webHeader(name: String): String? =
    entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value

internal fun isEduStyleOrScript(url: String, headers: Map<String, String>): Boolean {
    val destination = headers.webHeader("Sec-Fetch-Dest")?.lowercase(Locale.ROOT)
    if (destination != null) return destination == "script" || destination == "style"
    val path = runCatching { URI(url).path.orEmpty().lowercase(Locale.ROOT) }.getOrDefault("")
    return path.endsWith(".js") || path.endsWith(".css")
}

/** One browser window's recovery state; never replay a form submission or persist a guess. */
internal class EduWebResourcePolicy {
    private data class Navigation(val url: String, val method: String)
    @Volatile private var navigation: Navigation? = null
    @Volatile private var closed = false
    private val origins = ConcurrentHashMap.newKeySet<String>()

    fun recordNavigation(url: String, method: String) {
        if (!closed) navigation = Navigation(url.substringBefore('#'), method)
    }

    fun canReload(url: String): Boolean {
        val current = navigation ?: return false
        return !closed && current.method.equals("GET", true) && current.url == url.substringBefore('#')
    }

    fun enable(url: String): Boolean = canReload(url) &&
        eduWebOrigin(url)?.let(origins::add) == true

    fun shouldForward(url: String, method: String, mainFrame: Boolean, headers: Map<String, String>): Boolean {
        if (closed || method.uppercase(Locale.ROOT) !in setOf("GET", "HEAD")) return false
        val origin = eduWebOrigin(url) ?: return false
        if (origin !in origins || headers.webHeader("Range") != null) return false
        if (mainFrame) return true
        if (headers.webHeader("X-Requested-With").equals("XMLHttpRequest", true)) return false
        // Keep cookies, CORS, downloads and streaming in Chromium. Only same-origin display
        // resources use the recovery transport; the main document's origin is checked too.
        if (eduWebOrigin(navigation?.url) != eduWebOrigin(url)) return false
        return when (headers.webHeader("Sec-Fetch-Dest")?.lowercase(Locale.ROOT)) {
            "script", "style", "image", "font" -> true
            null -> isEduStyleOrScript(url, headers)
            else -> false
        }
    }

    fun close() {
        closed = true
        navigation = null
        origins.clear()
    }
}

/** Nexio's opt-in resource forwarding, scoped to this window and read-only requests. */
internal class EduWebResourceInterceptor(
    private val client: OkHttpClient,
    private val cookies: CookieManager = CookieManager.getInstance(),
    val policy: EduWebResourcePolicy = EduWebResourcePolicy()
) {
    fun intercept(request: WebResourceRequest, userAgent: String): WebResourceResponse? {
        val url = request.url.toString()
        if (request.isForMainFrame) policy.recordNavigation(url, request.method)
        if (!policy.shouldForward(url, request.method, request.isForMainFrame, request.requestHeaders)) return null
        val builder = Request.Builder().url(url).method(request.method, null)
        request.requestHeaders.forEach { (name, value) ->
            if (name.lowercase(Locale.ROOT) !in OmittedHeaders) builder.header(name, value)
        }
        builder.header("User-Agent", userAgent)
        cookies.getCookie(url)?.takeIf(String::isNotBlank)?.let { builder.header("Cookie", it) }
        return try {
            val response = client.newCall(builder.build()).execute()
            // WebResourceResponse cannot represent redirects. Let Chromium perform them so
            // the document URL, relative resources and cross-origin checks stay correct.
            if (response.code in 300..399 || response.header("Content-Disposition")
                    ?.contains("attachment", ignoreCase = true) == true) {
                response.close()
                return null
            }
            val body = response.body ?: run { response.close(); return null }
            val updatedCookies = response.headers.values("Set-Cookie")
            updatedCookies.forEach { cookies.setCookie(url, it) }
            if (updatedCookies.isNotEmpty()) cookies.flush()
            val type = body.contentType()
            val headers = response.headers.toMultimap()
                .filterKeys { !it.equals("Set-Cookie", true) }
                .mapValues { it.value.joinToString(", ") }
            // OkHttp handles gzip and removes its encoding/length headers together.
            // Closing this stream also closes the response body and releases the connection.
            WebResourceResponse(
                type?.let { "${it.type}/${it.subtype}" } ?: "application/octet-stream",
                type?.charset()?.name(), response.code, response.message.ifBlank { "OK" },
                headers, body.byteStream()
            )
        } catch (_: java.io.IOException) {
            // Only GET/HEAD reach here; the native request can safely handle transport errors.
            null
        }
    }

    companion object {
        private val OmittedHeaders = setOf(
            "x-requested-with", "x-webview-post-id", "accept-encoding", "content-length", "cookie", "host"
        )
        @Volatile private var sharedClient: OkHttpClient? = null

        fun create(context: Context): EduWebResourceInterceptor {
            val client = sharedClient ?: synchronized(this) {
                sharedClient ?: OkHttpClient.Builder()
                    .followRedirects(false).followSslRedirects(false)
                    .connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS)
                    .cache(Cache(File(context.applicationContext.cacheDir, "edu-web-resources"), 32L * 1024 * 1024))
                    .build().also { sharedClient = it }
            }
            return EduWebResourceInterceptor(client)
        }
    }
}
