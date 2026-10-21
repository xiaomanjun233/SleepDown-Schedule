package com.xiaomanjun.sleepdownschedule.feature.importing.shiguang

import java.net.URI

internal data class RegisteredWebPost(
    val body: String,
    val contentType: String,
    val url: String,
    val method: String,
    val allowCookies: Boolean,
    val bytes: Int,
    val createdAt: Long
)

/** Pending bodies belong to one WebView and are consumed once, before network I/O. */
internal class ShiguangPostRegistry(private val nowMillis: () -> Long = { System.nanoTime() / 1_000_000 }) {
    private val pending = linkedMapOf<String, RegisteredWebPost>()
    private var closed = false

    @Synchronized
    fun register(id: String, body: String, contentType: String, url: String, method: String, allowCookies: Boolean): Boolean {
        if (closed || !id.matches(Regex("[A-Za-z0-9_-]{1,96}")) || contentType.length > 256 ||
            contentType.any { it == '\r' || it == '\n' } || body.length > MaxBodyBytes) return false
        val parsed = runCatching { URI(url) }.getOrNull() ?: return false
        if (parsed.scheme !in setOf("http", "https") || parsed.host == null || parsed.rawUserInfo != null) return false
        if (method !in setOf("POST", "PUT", "PATCH", "DELETE", "OPTIONS")) return false
        val now = nowMillis()
        expire(now)
        val bytes = body.toByteArray(Charsets.UTF_8).size
        if (id in pending || bytes > MaxBodyBytes || pending.size >= MaxCount ||
            pending.values.sumOf { it.bytes } + bytes > MaxPendingBytes) return false
        pending[id] = RegisteredWebPost(body, contentType, url.substringBefore('#'), method, allowCookies, bytes, now)
        return true
    }

    @Synchronized
    fun take(id: String, url: String, method: String): RegisteredWebPost? {
        expire(nowMillis())
        return pending.remove(id)?.takeIf { it.url == url.substringBefore('#') && it.method == method }
    }

    @Synchronized
    fun close() {
        closed = true
        pending.clear()
    }

    private fun expire(now: Long) {
        pending.entries.removeAll { now - it.value.createdAt >= LifetimeMillis }
    }

    companion object {
        const val MaxCount = 32
        const val MaxBodyBytes = 2 * 1024 * 1024
        const val MaxPendingBytes = 8 * 1024 * 1024
        const val LifetimeMillis = 120_000L
    }
}

internal fun sameWebOrigin(first: String, second: String?): Boolean = runCatching {
    val left = URI(first)
    val right = URI(second ?: return false)
    fun port(uri: URI) = uri.port.takeIf { it >= 0 } ?: if (uri.scheme.equals("https", true)) 443 else 80
    left.host != null && left.scheme.equals(right.scheme, true) && left.host.equals(right.host, true) && port(left) == port(right)
}.getOrDefault(false)

internal fun withoutInternalPostId(url: String): String {
    val fragment = url.substringAfter('#', "")
    val base = url.substringBefore('#')
    if ('?' !in base) return url
    val query = base.substringAfter('?').split('&').filterNot {
        it.substringBefore('=') == "_webview_post_id"
    }.joinToString("&")
    return base.substringBefore('?') + (if (query.isEmpty()) "" else "?$query") +
        (if (fragment.isEmpty()) "" else "#$fragment")
}
