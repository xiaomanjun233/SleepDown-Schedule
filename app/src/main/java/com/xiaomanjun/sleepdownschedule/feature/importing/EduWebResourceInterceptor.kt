package com.xiaomanjun.sleepdownschedule.feature.importing

import android.content.Context
import android.os.Looper
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.net.URI
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
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

/**
 * 诊断用：保留地址与参数名，丢掉 ticket / code 等一次性凭证的值。
 * 只在排查跳转链时进入 logcat，不含可直接重放的凭据。
 */
internal fun eduWebLogUrl(url: String?): String = runCatching {
    val uri = URI(url ?: return "null")
    val keys = uri.rawQuery.orEmpty().split('&')
        .map { it.substringBefore('=') }
        .filter { it.isNotBlank() }
    val suffix = if (keys.isEmpty()) "" else "?[${keys.joinToString(",")}]"
    "${uri.scheme}://${uri.host}${uri.path}$suffix"
}.getOrDefault("invalid")

private data class AuthUrlParts(
    val host: String,
    val path: String,
    val fragmentPath: String,
    val queryKeys: Set<String>
)

private fun parseAuthUrlParts(url: String?): AuthUrlParts? {
    val normalized = normalizeEduUrl(url ?: return null)
    if (normalized.isBlank()) return null
    val uri = runCatching { URI(normalized) }.getOrNull() ?: return null
    val rawFragment = uri.rawFragment.orEmpty()
    val fragmentPath = rawFragment.substringBefore('?').lowercase(Locale.ROOT)
    val fragmentQuery = rawFragment.substringAfter('?', "").lowercase(Locale.ROOT)
    val mergedQuery = listOf(uri.rawQuery.orEmpty().lowercase(Locale.ROOT), fragmentQuery)
        .filter { it.isNotBlank() }
        .joinToString("&")
    val queryKeys = mergedQuery.split('&')
        .map { it.substringBefore('=').trim() }
        .filter { it.isNotBlank() }
        .toSet()
    return AuthUrlParts(
        host = uri.host.orEmpty().lowercase(Locale.ROOT),
        path = uri.path.orEmpty().lowercase(Locale.ROOT),
        fragmentPath = fragmentPath,
        queryKeys = queryKeys
    )
}

private val OneTimeAuthTokenKeys = setOf(
    "ticket", "samlresponse", "samlart", "relaystate", "id_token", "access_token", "lt", "execution"
)

private val AuthHandshakeQueryKeys = setOf(
    "service", "redirect_uri", "cas_login", "samlrequest"
)

private val AuthProtocolPathTokens = listOf(
    "/cas/", "/oauth/", "/oauth2", "/authorize", "/connect/authorize", "/oidc/",
    "/saml", "/idp/", "/authserver", "/lyuapserver", "/tpass/", "/esc-sso",
    "/ucas-sso", "/unified-login", "/zfca/", "/am/ui/login", "/am/oauth2/",
    "/passport/v1/auth/", "/sso/", "/sso.", "/caslogin", "/zllogin", "/ddlogin",
    "/lyiotlogin", "/driotlogin", "/jziotlogin", "/callback/cas",
    "/ids/", "/uap/", "/uaa/", "/iam/"
)

private val CodeAuthPathTokens = listOf(
    "/oauth", "/cas", "/sso", "/auth", "/login", "/callback", "/oidc",
    "/am/", "/ids/", "/uap/", "/uaa/", "/iam/", "/connect/", "/portal"
)

private val AuthHostPrefixes = listOf(
    "cas.", "auth.", "authserver.", "sso.", "ids.", "iam.", "uap.", "uaa.",
    "idm.", "tysf.", "mysso.", "esc-sso.", "passport.", "webvpn.", "atrust."
)

/**
 * 携带一次性回调凭据（CAS ticket / SAML / OAuth code 等）的 URL：绝不能被重发、reload 或回退入口。
 */
internal fun eduWebHasOneTimeToken(url: String?): Boolean {
    val parts = parseAuthUrlParts(url) ?: return false
    return hasOneTimeToken(parts)
}

private fun hasOneTimeToken(parts: AuthUrlParts): Boolean {
    if (parts.queryKeys.any { it in OneTimeAuthTokenKeys }) return true
    if ("code" !in parts.queryKeys) return false
    val hasOAuthState = "state" in parts.queryKeys || "session_state" in parts.queryKeys
    if (hasOAuthState) return true
    return CodeAuthPathTokens.any { token ->
        parts.path.contains(token) || parts.fragmentPath.contains(token)
    }
}

/**
 * SSO / CAS / OAuth / SAML / WebVPN 认证握手或一次性回调地址。
 *
 * 这类地址只能由 WebView 原生网络栈按浏览器正常状态机处理：转发、自动 reload 或
 * 客户端桩页重放会打断 CSRF / Referer / Set-Cookie 链或二次消费一次性凭证，导致认证网关
 * 返回“未经授权的服务”或“未经授权的访问”。
 */
internal fun eduWebIsTransientAuthUrl(url: String?): Boolean {
    val parts = parseAuthUrlParts(url) ?: return false
    if (hasOneTimeToken(parts)) return true
    if (parts.queryKeys.any { it in AuthHandshakeQueryKeys }) return true
    val routes = listOf(parts.path, parts.fragmentPath)
    if (routes.any { route ->
            route.endsWith("/cas") || AuthProtocolPathTokens.any { route.contains(it) }
        }
    ) {
        return true
    }
    val isAuthHost = AuthHostPrefixes.any { parts.host.startsWith(it) } ||
        parts.host.contains("-cas-") || parts.host.contains("-auth-")
    if (isAuthHost && routes.any { route ->
            route.contains("/login") || route.contains("/auth") ||
                route.contains("/authorize") || route.contains("/sign_in") ||
                route.contains("/passport")
        }
    ) {
        return true
    }
    return false
}

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
        return !closed && current.method.equals("GET", true) && current.url == url.substringBefore('#') &&
            !eduWebIsTransientAuthUrl(url)
    }

    fun enable(url: String): Boolean = canReload(url) &&
        eduWebOrigin(url)?.let(origins::add) == true

    fun shouldForward(url: String, method: String, mainFrame: Boolean, headers: Map<String, String>): Boolean {
        if (closed || method.uppercase(Locale.ROOT) !in setOf("GET", "HEAD")) return false
        // 认证回跳全程交给 Chromium：WebResourceResponse 表达不了 3xx，主框架一旦转发就只能
        // 返回 null 让 WebView 重发原地址，ticket / code 会被消费两次。
        if (eduWebIsTransientAuthUrl(url)) return false
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
        val forward = policy.shouldForward(url, request.method, request.isForMainFrame, request.requestHeaders)
        if (request.isForMainFrame) {
            Log.i("EduNav", "policy forward=$forward transient=${eduWebIsTransientAuthUrl(url)}")
        }
        if (!forward) return null
        val builder = Request.Builder().url(url).method(request.method, null)
        request.requestHeaders.forEach { (name, value) ->
            if (name.lowercase(Locale.ROOT) !in OmittedHeaders) builder.header(name, value)
        }
        if (userAgent.isNotBlank()) builder.header("User-Agent", userAgent)
        cookies.getCookie(url)?.takeIf(String::isNotBlank)?.let { builder.header("Cookie", it) }
        return try {
            val response = client.newCall(builder.build()).execute()
            val status = response.code
            if (request.isForMainFrame) {
                Log.i("EduNav", "forwarded status=$status location=${eduWebLogUrl(response.header("Location"))}")
            }
            val updatedCookies = response.headers.values("Set-Cookie")
            commitCookies(url, updatedCookies)
            // WebResourceResponse 表达不了重定向。子资源交回 Chromium 重发是安全的，但主框架
            // 重发会再打一次原地址：CAS 的 ticket、OAuth 的 code 都是一次性凭证，第二次必然
            // 被 IdP 拒绝，门户于是把已成功的登录显示成“未经授权的访问 / 服务”。主框架改用回跳桩页，
            // 让 Chromium 直接请求 Location，地址栏、历史记录与相对路径都不受影响。
            val disposition = response.header("Content-Disposition")
            if (status in 300..399 && request.isForMainFrame && disposition == null) {
                val destination = response.header("Location")
                    ?.let { response.request.url.resolve(it)?.toString() }
                    ?.takeIf { it.startsWith("http://", true) || it.startsWith("https://", true) }
                if (destination != null) {
                    response.close()
                    return redirectStub(destination)
                }
            }
            if (status in 300..399 || disposition?.contains("attachment", ignoreCase = true) == true) {
                response.close()
                return null
            }
            val body = response.body ?: run { response.close(); return null }
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

    private fun commitCookies(url: String, setCookieHeaders: List<String>) {
        if (setCookieHeaders.isEmpty()) return
        if (Looper.myLooper() == Looper.getMainLooper()) {
            setCookieHeaders.forEach { cookies.setCookie(url, it) }
            cookies.flush()
            return
        }
        val latch = CountDownLatch(setCookieHeaders.size)
        setCookieHeaders.forEach { header ->
            cookies.setCookie(url, header) { latch.countDown() }
        }
        runCatching { latch.await(500, TimeUnit.MILLISECONDS) }
        cookies.flush()
    }

    /** 200 的本地跳转页：不重发被转发请求的地址，避免一次性凭证被二次消费。 */
    private fun redirectStub(destination: String): WebResourceResponse {
        val html = "<html><script>window.location.replace(${JSONObject.quote(destination)});</script></html>"
        return WebResourceResponse(
            "text/html", "UTF-8", 200, "OK", mapOf("Cache-Control" to "no-store"),
            html.byteInputStream(Charsets.UTF_8)
        )
    }

    companion object {
        private val OmittedHeaders = setOf(
            "x-requested-with", "x-webview-post-id", "accept-encoding", "content-length", "cookie", "host"
        )
        @Volatile private var sharedClient: OkHttpClient? = null

        fun create(context: Context, cookies: CookieManager): EduWebResourceInterceptor {
            val client = sharedClient ?: synchronized(this) {
                sharedClient ?: OkHttpClient.Builder()
                    .followRedirects(false).followSslRedirects(false)
                    .connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS)
                    .cache(Cache(File(context.applicationContext.cacheDir, "edu-web-resources"), 32L * 1024 * 1024))
                    .build().also { sharedClient = it }
            }
            // Never use a response cached by the auto-refresh browser in the standalone profile.
            val profileClient = if (cookies === CookieManager.getInstance()) client
                else client.newBuilder().cache(null).build()
            return EduWebResourceInterceptor(profileClient, cookies)
        }
    }
}
