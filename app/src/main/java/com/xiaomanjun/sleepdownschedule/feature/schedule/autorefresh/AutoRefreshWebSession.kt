package com.xiaomanjun.sleepdownschedule.feature.schedule.autorefresh

import android.webkit.CookieManager
import android.webkit.WebView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import kotlin.coroutines.resume

/** Persist only session fields consumed by reviewed adapters, encrypted with the profile. */
internal object AutoRefreshWebSession {
    const val RestoreSessionExtra = "restore_retained_edu_session"

    suspend fun restore(webView: WebView, profile: AutoRefreshScheduleProfile) {
        val manager = CookieManager.getInstance().apply { setAcceptCookie(true) }
        for (cookie in profile.cookies) {
            if (origin(cookie.url) == null) continue
            // A live browser session may be newer than the encrypted snapshot.
            val currentNames = manager.getCookie(cookie.url).orEmpty().split(';')
                .map { it.substringBefore('=').trim() }.toSet()
            for (entry in cookie.value.split(';').map(String::trim).filter { it.contains('=') }) {
                if (entry.substringBefore('=') in currentNames) continue
                suspendCancellableCoroutine<Unit> { continuation ->
                    manager.setCookie(cookie.url, "$entry; Path=/") {
                        if (continuation.isActive) continuation.resume(Unit)
                    }
                }
            }
        }
        manager.flush()
        installStorage(webView, profile.schoolId, profile.webStorage)
    }

    private fun keys(schoolId: String): List<String> = when (schoolId) {
        "AEPU", "BBGU" -> listOf("Authorization", "access_token", "token", "X-Access-Token", "x-token", "auth_token", "jwt", "user_token")
        "HUAT" -> listOf("Admin-Token", "adminToken", "token", "X-Token", "x-token", "admin_token")
        "CQIE" -> listOf("cqu_edu_ACCESS_TOKEN", "cqu_edu_CURRENT_TOKEN", "USER_INFO", "cqu_edu_USER_INFO")
        "CQU" -> listOf("cqu_edu_ACCESS_TOKEN")
        "GDOU" -> listOf("current-user", "visitorObj", "semester")
        "GZTRC" -> listOf("user_info")
        "HNNU" -> listOf("token")
        "SWJTU" -> listOf("ytoken", "YToken", "token", "TOKEN")
        "QHNU" -> listOf("sys-auth-token")
        "YIBINU" -> listOf("ampUserId")
        else -> emptyList()
    }

    fun captureCookies(urls: Collection<String>): List<AutoRefreshCookie> {
        val manager = CookieManager.getInstance()
        manager.flush()
        return urls.filter { origin(it) != null }.distinct().mapNotNull { url ->
            manager.getCookie(url)?.takeIf(String::isNotBlank)?.let { AutoRefreshCookie(url, it) }
        }
    }

    suspend fun captureStorage(webView: WebView, schoolId: String): AutoRefreshWebStorage? =
        suspendCancellableCoroutine { continuation ->
            captureStorage(webView, schoolId) { if (continuation.isActive) continuation.resume(it) }
        }

    fun captureStorage(webView: WebView, schoolId: String, onCaptured: (AutoRefreshWebStorage?) -> Unit) {
        val origin = origin(webView.url)
        val keys = keys(schoolId)
        if (origin == null || keys.isEmpty()) { onCaptured(null); return }
        val script = """
            (function() {
                var keys = ${JSONArray(keys)}, values = {};
                [['local', localStorage], ['session', sessionStorage]].forEach(function(pair) {
                    keys.forEach(function(key) {
                        var value = pair[1].getItem(key);
                        if (value) values[pair[0] + ':' + key] = value;
                    });
                });
                if (!Object.keys(values).length && ${schoolId == "HUAT"} && (window.token || window.userToken)) {
                    values['session:token'] = window.token || window.userToken;
                }
                if (!Object.keys(values).length && ${schoolId in setOf("AEPU", "BBGU")}) {
                    [localStorage, sessionStorage].some(function(storage) {
                        return Object.keys(storage).some(function(key) {
                            var value = storage.getItem(key);
                            if (/^eyJ[A-Za-z0-9_-]+\.eyJ[A-Za-z0-9_-]+\./.test(value || '')) {
                                values['session:token'] = value; return true;
                            }
                            return false;
                        });
                    });
                }
                return JSON.stringify({ values: values, windowName: ${schoolId == "YIBINU"} ? window.name : null });
            })()
        """.trimIndent()
        webView.evaluateJavascript(script) { encoded ->
            val storage = runCatching {
                val json = JSONObject(JSONArray("[$encoded]").getString(0))
                val values = json.getJSONObject("values")
                val entries = values.keys().asSequence().filter { it.substringAfter(':') in keys }
                    .associateWith(values::getString)
                val windowName = json.optString("windowName").takeIf { it.isNotBlank() && it != "null" }
                if (entries.isEmpty() && windowName == null) null else AutoRefreshWebStorage(origin, entries, windowName)
            }.getOrNull()
            onCaptured(storage)
        }
    }

    fun installStorage(webView: WebView, schoolId: String, storage: AutoRefreshWebStorage?) {
        storage ?: return
        require(WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            "请更新 Android System WebView 后重新登录，以恢复教务会话"
        }
        require(origin(storage.origin) == storage.origin) { "教务会话来源无效，请重新登录" }
        val permitted = keys(schoolId)
        val values = JSONObject(storage.values.filterKeys { it.substringAfter(':') in permitted })
        val windowName = if (schoolId == "YIBINU") storage.windowName else null
        WebViewCompat.addDocumentStartJavaScript(
            webView,
            """
                (function() {
                    var values = $values;
                    Object.keys(values).forEach(function(key) {
                        var index = key.indexOf(':');
                        var name = index < 0 ? key : key.slice(index + 1);
                        var storage = key.indexOf('local:') === 0 ? localStorage : sessionStorage;
                        storage.setItem(name, values[key]);
                    });
                    var savedName = ${windowName?.let(JSONObject::quote) ?: "null"};
                    if (savedName !== null) window.name = savedName;
                })()
            """.trimIndent(),
            setOf(storage.origin)
        )
    }

    fun origin(url: String?): String? = runCatching {
        val uri = URI(url.orEmpty())
        if (uri.scheme !in setOf("http", "https") || uri.host.isNullOrBlank() || uri.userInfo != null) null
        else "${uri.scheme}://${uri.host}" + if (uri.port >= 0) ":${uri.port}" else ""
    }.getOrNull()
}
