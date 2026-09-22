package com.xiaomanjun.sleepdownschedule.feature.schedule.autorefresh

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import com.xiaomanjun.sleepdownschedule.AppState
import com.xiaomanjun.sleepdownschedule.ImportDraft
import com.xiaomanjun.sleepdownschedule.app.ui.releaseSleepDownWebView
import com.xiaomanjun.sleepdownschedule.feature.importing.EduAdapter
import com.xiaomanjun.sleepdownschedule.feature.importing.EduBridgeInteractionRequest
import com.xiaomanjun.sleepdownschedule.feature.importing.configureEduImportSecurity
import com.xiaomanjun.sleepdownschedule.feature.importing.DesktopWebUserAgent
import com.xiaomanjun.sleepdownschedule.feature.importing.shiguang.ShiguangWebRequestInterceptor
import com.xiaomanjun.sleepdownschedule.feature.importing.shiguang.ShiguangBridgeHost
import com.xiaomanjun.sleepdownschedule.feature.importing.shiguang.injectShiguangRuntime
import com.xiaomanjun.sleepdownschedule.feature.importing.shiguang.installShiguangRuntime
import com.xiaomanjun.sleepdownschedule.feature.importing.shiguang.uninstallShiguangRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal data class AutoRefreshFetch(
    val draft: ImportDraft,
    val cookies: List<AutoRefreshCookie>,
    val storage: AutoRefreshWebStorage?,
    val interactionAnswers: Map<String, String>
)

internal data class AutoRefreshLoginInteraction(
    val request: EduBridgeInteractionRequest,
    val bridge: ShiguangBridgeHost,
    val resolve: (String) -> Unit
)

internal object AutoRefreshShiguangRunner {
    suspend fun fetch(
        context: Context,
        adapter: EduAdapter,
        profile: AutoRefreshScheduleProfile,
        targetState: AppState,
        onInteraction: ((AutoRefreshLoginInteraction?) -> Unit)? = null
    ): AutoRefreshFetch {
        val source = ShiguangApiAdapterCatalog.resolveScript(context, adapter)
        return withTimeout(if (onInteraction == null) 90_000L else 600_000L) {
            withContext(Dispatchers.Main.immediate) {
                runWebView(context.applicationContext, adapter, profile, targetState, source, onInteraction)
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun runWebView(
        context: Context,
        adapter: EduAdapter,
        profile: AutoRefreshScheduleProfile,
        targetState: AppState,
        source: String,
        onInteraction: ((AutoRefreshLoginInteraction?) -> Unit)?
    ): AutoRefreshFetch = suspendCancellableCoroutine { continuation ->
        val handler = Handler(Looper.getMainLooper())
        var target: WebView? = null
        var scriptStarted = false
        var finished = false
        var completing = false
        var pageGeneration = 0
        var coursePageAttempts = 0
        var ssoAttempts = 0
        val visitedUrls = (profile.cookies.map(AutoRefreshCookie::url) + adapter.importUrl).toMutableSet()
        val answers = profile.interactionAnswers.toMutableMap()

        fun release() {
            handler.removeCallbacksAndMessages(null)
            onInteraction?.invoke(null)
            target?.let { webView ->
                runCatching { webView.uninstallShiguangRuntime() }
                runCatching { webView.stopLoading() }
                runCatching { webView.releaseSleepDownWebView(clearResourceCache = false) }
            }
            target = null
        }

        fun fail(message: String) {
            if (finished) return
            finished = true
            release()
            if (continuation.isActive) continuation.resumeWithException(IllegalStateException(message))
        }

        fun complete(draft: ImportDraft) {
            if (finished || completing) return
            completing = true
            val webView = target ?: return
            webView.url?.let(visitedUrls::add)
            val cookies = AutoRefreshWebSession.captureCookies(visitedUrls)
            AutoRefreshWebSession.captureStorage(webView, adapter.school.id) { storage ->
                if (finished) return@captureStorage
                finished = true
                release()
                if (continuation.isActive) continuation.resume(AutoRefreshFetch(draft, cookies, storage, answers.toMap()))
            }
        }

        fun isFailureMessage(message: String): Boolean = listOf(
            "失败", "错误", "无效", "无法", "未登录", "请登录", "登录失效", "超时",
            "未找到任何课程数据", "导入已取消", "用户取消了导入",
            "error", "exception", "invalid", "unauthorized", "forbidden"
        ).any { message.contains(it, ignoreCase = true) }

        lateinit var bridge: ShiguangBridgeHost
        bridge = ShiguangBridgeHost(
            context = context,
            onDraft = ::complete,
            onMessage = { message -> if (isFailureMessage(message)) fail(message) },
            onInteractionRequest = { request ->
                val answer = AutoRefreshAnswers.resolve(answers, request, adapter.school.id)
                if (request is EduBridgeInteractionRequest.Alert && answer == null) {
                    fail("教务校验失败，请确认登录状态后重试")
                } else if (answer != null) {
                    // Introductory import alerts are protocol acknowledgements. Only unresolved
                    // school options need user input; never show the adapter's import workflow.
                    AutoRefreshAnswers.record(answers, request, answer)
                    bridge.resolveInteraction(request.requestId, answer)
                } else if (onInteraction != null) {
                    onInteraction(AutoRefreshLoginInteraction(request, bridge) { value ->
                        if (!finished) {
                            val invalidSelection = request is EduBridgeInteractionRequest.SingleSelection &&
                                (value.toIntOrNull()?.let { it in request.options.indices } != true)
                            if (value == "null" || invalidSelection) {
                                fail("已取消连接")
                            } else {
                                AutoRefreshAnswers.record(answers, request, value)
                                bridge.resolveInteraction(request.requestId, value)
                            }
                        }
                    })
                } else {
                    fail("教务系统需要确认选项，请重新登录并确认学期、校区等信息")
                }
            }
        )
        bridge.beginTask(targetState.config, targetState.periods)

        fun startAdapterScript(webView: WebView, expectedGeneration: Int) {
            handler.postDelayed({
                if (finished || scriptStarted || pageGeneration != expectedGeneration) return@postDelayed
                scriptStarted = true
                webView.injectShiguangRuntime(profile.desktopMode)
                // Isolate top-level declarations so a failed connection can be retried in the same page.
                webView.evaluateJavascript("(async function() {\n" + source + "\n})().catch(function() { window.shiguangBridge.showToast('刷新失败，请重新登录后重试'); });", null)
            }, 2_500L)
        }

        fun continueAfterLogin(webView: WebView) {
            if (!ShiguangApiAdapterCatalog.isSwuAdapter(adapter) || SwuAuthRoutes.isCoursePage(webView.url)) {
                startAdapterScript(webView, pageGeneration)
            } else when {
                SwuAuthRoutes.isAuthenticatedTeachingPage(webView.url) && coursePageAttempts++ < 3 ->
                    webView.loadUrl(SwuAuthRoutes.CoursePageUrl)
                SwuAuthRoutes.isTeachingLoginPage(webView.url) && ssoAttempts++ < 1 ->
                    webView.loadUrl(SwuAuthRoutes.SsoUrl)
                SwuAuthRoutes.isTeachingLoginPage(webView.url) || SwuAuthRoutes.isUnifiedAuthPage(webView.url) ||
                    SwuAuthRoutes.isSsoEntry(webView.url) ->
                    fail("教务会话已失效，请重新登录；校外访问请连接校园 VPN")
                else -> fail("尚未进入教务课表页面，请重新完成学校登录")
            }
        }

        continuation.invokeOnCancellation {
            handler.post { finished = true; release() }
        }
        try {
            val webView = WebView(context)
            target = webView
            val cookieManager = CookieManager.getInstance().apply { setAcceptCookie(true) }
            profile.cookies.forEach { cookie ->
                cookie.value.split(';').map(String::trim).filter { it.contains('=') }.forEach { value ->
                    cookieManager.setCookie(cookie.url, "$value; Path=/")
                }
            }
            cookieManager.flush()
            webView.settings.javaScriptEnabled = true
            webView.settings.domStorageEnabled = true
            // Match the visible education browser: legacy portals load scripts over HTTP.
            webView.settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            if (profile.desktopMode) webView.settings.userAgentString = DesktopWebUserAgent
            webView.configureEduImportSecurity()
            AutoRefreshWebSession.installStorage(webView, adapter.school.id, profile.webStorage)
            cookieManager.setAcceptThirdPartyCookies(webView, true)
            val interceptor = ShiguangWebRequestInterceptor()
            webView.webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? =
                    request?.let { interceptor.intercept(it, profile.desktopMode) }

                override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                    pageGeneration++
                    url?.let(visitedUrls::add)
                }

                override fun onPageFinished(view: WebView, url: String?) {
                    if (finished) return
                    url?.let(visitedUrls::add)
                    view.injectShiguangRuntime(profile.desktopMode)
                    continueAfterLogin(view)
                }

                override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                    if (request?.isForMainFrame == true) fail("教务页面加载失败，请检查校园网或 VPN 连接")
                }
            }
            webView.installShiguangRuntime(bridge)
            bridge.bindWebView(webView)
            webView.loadUrl(
                profile.authenticatedUrl ?: if (ShiguangApiAdapterCatalog.isSwuAdapter(adapter)) SwuAuthRoutes.CoursePageUrl else adapter.importUrl
            )
        } catch (error: Exception) {
            fail(error.message ?: "无法打开教务页面")
        }
    }
}
