package com.xiaomanjun.sleepdownschedule.feature.schedule.autorefresh

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.xiaomanjun.sleepdownschedule.AppState
import com.xiaomanjun.sleepdownschedule.ImportDraft
import com.xiaomanjun.sleepdownschedule.app.ui.releaseSleepDownWebView
import com.xiaomanjun.sleepdownschedule.feature.importing.EduAdapter
import com.xiaomanjun.sleepdownschedule.feature.importing.EduBridgeInteractionRequest
import com.xiaomanjun.sleepdownschedule.feature.importing.configureEduImportSecurity
import com.xiaomanjun.sleepdownschedule.feature.importing.shiguang.ShiguangBridgeHost
import com.xiaomanjun.sleepdownschedule.feature.importing.shiguang.injectShiguangRuntime
import com.xiaomanjun.sleepdownschedule.feature.importing.shiguang.installShiguangRuntime
import com.xiaomanjun.sleepdownschedule.feature.importing.shiguang.uninstallShiguangRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.util.Calendar
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal data class AutoRefreshFetch(
    val draft: ImportDraft,
    val cookies: List<AutoRefreshCookie>
)

internal object AutoRefreshShiguangRunner {
    private const val TimeoutMillis = 90_000L
    private const val StablePageDelayMillis = 2_500L
    private const val SwuCoursePageUrl = SwuAuthRoutes.CoursePageUrl
    private const val SwuSsoUrl = SwuAuthRoutes.SsoUrl

    suspend fun fetch(
        context: Context,
        adapter: EduAdapter,
        profile: AutoRefreshScheduleProfile,
        targetState: AppState
    ): AutoRefreshFetch {
        val source = ShiguangApiAdapterCatalog.resolveScript(context, adapter)
        require(ShiguangApiAdapterCatalog.isApiOnlyScript(adapter.school.id, adapter.adapterId, source)) {
            "拾光适配器已变更，不再满足纯接口刷新条件"
        }
        return withTimeout(TimeoutMillis) {
            withContext(Dispatchers.Main.immediate) {
                runWebView(context.applicationContext, adapter, profile, targetState, source)
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun runWebView(
        context: Context,
        adapter: EduAdapter,
        profile: AutoRefreshScheduleProfile,
        targetState: AppState,
        source: String
    ): AutoRefreshFetch = suspendCancellableCoroutine { continuation ->
        val handler = Handler(Looper.getMainLooper())
        var target: WebView? = null
        var scriptStarted = false
        var finished = false
        var pageGeneration = 0
        var loginAttempts = 0
        var swuCoursePageAttempts = 0
        var swuSsoAttempts = 0
        var lastUrl = adapter.importUrl

        fun release() {
            handler.removeCallbacksAndMessages(null)
            target?.let { webView ->
                runCatching { webView.stopLoading() }
                runCatching { webView.uninstallShiguangRuntime() }
                runCatching { webView.releaseSleepDownWebView(clearResourceCache = false) }
            }
            target = null
        }

        fun fail(message: String, cause: Throwable? = null) {
            if (finished) return
            finished = true
            release()
            if (continuation.isActive) {
                continuation.resumeWithException(cause ?: IllegalStateException(message))
            }
        }

        fun complete(draft: ImportDraft) {
            if (finished) return
            val cookieManager = CookieManager.getInstance()
            val urls = listOf(adapter.importUrl, lastUrl, target?.url.orEmpty())
                .filter { it.startsWith("http://") || it.startsWith("https://") }
                .distinct()
            val cookies = urls.mapNotNull { url ->
                cookieManager.getCookie(url)?.takeIf(String::isNotBlank)?.let { AutoRefreshCookie(url, it) }
            }
            cookieManager.flush()
            finished = true
            release()
            if (continuation.isActive) continuation.resume(AutoRefreshFetch(draft, cookies))
        }

        fun isFailureMessage(message: String): Boolean {
            val normalized = message.lowercase()
            return listOf(
                "失败", "错误", "无效", "无法", "未登录", "请登录", "登录失效", "超时",
                "error", "exception", "invalid", "unauthorized", "forbidden"
            ).any(normalized::contains)
        }

        lateinit var bridge: ShiguangBridgeHost
        bridge = ShiguangBridgeHost(
            context = context,
            onDraft = ::complete,
            onMessage = { message ->
                if (isFailureMessage(message)) fail(message)
            },
            onInteractionRequest = { request ->
                when (request) {
                    is EduBridgeInteractionRequest.Alert -> {
                        val detail = listOf(request.title, request.message)
                            .filter(String::isNotBlank)
                            .joinToString("：")
                        if (isFailureMessage(detail) || detail.contains("未获取到")) {
                            fail(detail)
                        } else {
                            bridge.resolveInteraction(request.requestId, "true")
                        }
                    }

                    is EduBridgeInteractionRequest.Prompt -> {
                        bridge.resolveInteraction(
                            request.requestId,
                            JSONObject.quote(resolvePromptValue(request, profile.username))
                        )
                    }

                    is EduBridgeInteractionRequest.SingleSelection -> {
                        val index = resolveSelectionIndex(request)
                        if (index == null) {
                            fail("${request.title}没有可用选项")
                        } else {
                            bridge.resolveInteraction(request.requestId, index.toString())
                        }
                    }
                }
            }
        )
        bridge.beginTask(targetState.config, targetState.periods)

        fun startAdapterScript(webView: WebView, expectedGeneration: Int) {
            handler.postDelayed({
                if (finished || scriptStarted || pageGeneration != expectedGeneration) return@postDelayed
                scriptStarted = true
                webView.injectShiguangRuntime(desktopMode = false)
                webView.evaluateJavascript(source, null)
            }, StablePageDelayMillis)
        }

        fun continueAfterLogin(webView: WebView, expectedGeneration: Int) {
            if (!ShiguangApiAdapterCatalog.isSwuDirectAdapter(adapter)) {
                startAdapterScript(webView, expectedGeneration)
                return
            }
            when {
                SwuAuthRoutes.isCoursePage(webView.url) -> {
                    startAdapterScript(webView, expectedGeneration)
                }

                SwuAuthRoutes.isAuthenticatedTeachingPage(webView.url) -> {
                    if (swuCoursePageAttempts >= 3) {
                        fail("无法进入西南大学教务课表；请确认已连接校园网或 aTrust")
                        return
                    }
                    swuCoursePageAttempts += 1
                    handler.postDelayed({
                        if (finished || scriptStarted || pageGeneration != expectedGeneration) {
                            return@postDelayed
                        }
                        webView.loadUrl(SwuCoursePageUrl)
                    }, 1_200L)
                }

                SwuAuthRoutes.isTeachingLoginPage(webView.url) -> {
                    if (swuSsoAttempts >= 1) {
                        fail("西南大学统一认证会话已失效，请在自动刷新页重新完成统一认证")
                        return
                    }
                    swuSsoAttempts += 1
                    handler.postDelayed({
                        if (finished || scriptStarted || pageGeneration != expectedGeneration) {
                            return@postDelayed
                        }
                        webView.loadUrl(SwuSsoUrl)
                    }, 600L)
                }

                SwuAuthRoutes.isUnifiedAuthPage(webView.url) ||
                    SwuAuthRoutes.isSsoEntry(webView.url) -> {
                    fail("西南大学统一认证会话已失效，请在自动刷新页重新完成统一认证")
                }

                else -> {
                    fail("无法识别西南大学认证页面，请重新完成统一认证")
                }
            }
        }

        fun tryCredentialLogin(webView: WebView, expectedGeneration: Int) {
            if (ShiguangApiAdapterCatalog.isSwuDirectAdapter(adapter)) {
                continueAfterLogin(webView, expectedGeneration)
                return
            }
            if (loginAttempts >= 2 || profile.username.isBlank() || profile.password.isBlank()) {
                continueAfterLogin(webView, expectedGeneration)
                return
            }
            val loginScript = buildCredentialLoginScript(profile.username, profile.password)
            webView.evaluateJavascript(loginScript) { result ->
                if (finished || pageGeneration != expectedGeneration) return@evaluateJavascript
                if (result == "true") {
                    loginAttempts += 1
                    // Covers SPA logins that authenticate without a full page navigation.
                    handler.postDelayed({
                        if (!finished && !scriptStarted && pageGeneration == expectedGeneration) {
                            continueAfterLogin(webView, expectedGeneration)
                        }
                    }, 5_000L)
                } else {
                    continueAfterLogin(webView, expectedGeneration)
                }
            }
        }

        val cookieManager = CookieManager.getInstance().apply { setAcceptCookie(true) }
        profile.cookies.forEach { cookie ->
            cookie.value.split(';')
                .map(String::trim)
                .filter { it.contains('=') }
                .forEach { value -> cookieManager.setCookie(cookie.url, "$value; Path=/") }
        }
        cookieManager.flush()

        target = WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            configureEduImportSecurity()
            cookieManager.setAcceptThirdPartyCookies(this, true)
            installShiguangRuntime(bridge)
            bridge.bindWebView(this)
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    pageGeneration += 1
                    url?.let { lastUrl = it }
                    view.injectShiguangRuntime(desktopMode = false)
                }

                override fun onPageFinished(view: WebView, url: String?) {
                    super.onPageFinished(view, url)
                    url?.let { lastUrl = it }
                    view.injectShiguangRuntime(desktopMode = false)
                    tryCredentialLogin(view, pageGeneration)
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?
                ) {
                    super.onReceivedError(view, request, error)
                    if (request?.isForMainFrame == true) {
                        fail("教务页面加载失败：${error?.description ?: "网络连接异常"}")
                    }
                }
            }
        }
        target?.loadUrl(
            if (ShiguangApiAdapterCatalog.isSwuDirectAdapter(adapter)) SwuCoursePageUrl
            else adapter.importUrl
        )
        continuation.invokeOnCancellation {
            handler.post {
                if (!finished) {
                    finished = true
                    release()
                }
            }
        }
    }

    private fun buildCredentialLoginScript(username: String, password: String): String = """
        (function () {
          function visible(node) {
            if (!node || node.disabled) return false;
            var style = window.getComputedStyle(node);
            return style.display !== 'none' && style.visibility !== 'hidden';
          }
          function setValue(node, value) {
            var prototype = Object.getPrototypeOf(node);
            var descriptor = Object.getOwnPropertyDescriptor(prototype, 'value') ||
              Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value');
            if (descriptor && descriptor.set) descriptor.set.call(node, value); else node.value = value;
            node.dispatchEvent(new Event('input', { bubbles: true }));
            node.dispatchEvent(new Event('change', { bubbles: true }));
          }
          var passwords = Array.prototype.slice.call(document.querySelectorAll('input[type="password"]')).filter(visible);
          if (!passwords.length) return false;
          var passwordNode = passwords[0];
          var form = passwordNode.form || passwordNode.closest('form');
          var scope = form || document;
          var users = Array.prototype.slice.call(scope.querySelectorAll(
            'input[type="text"],input[type="email"],input[type="tel"],input:not([type])'
          )).filter(function (node) {
            if (!visible(node)) return false;
            var hint = ((node.name || '') + ' ' + (node.id || '') + ' ' + (node.placeholder || '')).toLowerCase();
            return !/(captcha|verify|code|验证码)/.test(hint);
          });
          if (!users.length) return false;
          setValue(users[0], ${JSONObject.quote(username)});
          setValue(passwordNode, ${JSONObject.quote(password)});
          var submit = scope.querySelector('button[type="submit"],input[type="submit"],button:not([type]),[role="button"]');
          if (submit) submit.click();
          else if (form && form.requestSubmit) form.requestSubmit();
          else if (form) form.submit();
          return true;
        })();
    """.trimIndent()

    private fun resolvePromptValue(
        request: EduBridgeInteractionRequest.Prompt,
        username: String
    ): String {
        val hint = "${request.title} ${request.message}"
        return when {
            hint.contains("学年") -> currentAcademicStartYear().toString()
            request.defaultValue.isNotBlank() -> request.defaultValue
            hint.contains("学号") || hint.contains("账号") -> username
            else -> request.defaultValue
        }
    }

    private fun resolveSelectionIndex(request: EduBridgeInteractionRequest.SingleSelection): Int? {
        if (request.options.isEmpty()) return null
        if (request.defaultIndex in request.options.indices) return request.defaultIndex
        val hint = buildString {
            append(request.title)
            request.options.forEach(::append)
        }
        if (hint.contains("学期")) {
            val month = Calendar.getInstance().get(Calendar.MONTH)
            val preferred = if (month in Calendar.MARCH..Calendar.AUGUST) 1 else 0
            return preferred.coerceAtMost(request.options.lastIndex)
        }
        return 0
    }

    private fun currentAcademicStartYear(): Int {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        return if (calendar.get(Calendar.MONTH) >= Calendar.AUGUST) year else year - 1
    }

}
