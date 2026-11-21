package com.xiaomanjun.sleepdownschedule.feature.schedule.autorefresh

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.xiaomanjun.sleepdownschedule.AppState
import com.xiaomanjun.sleepdownschedule.ImportDraft
import com.xiaomanjun.sleepdownschedule.core.web.releaseSleepDownWebView
import com.xiaomanjun.sleepdownschedule.feature.importing.EduBridgeInteractionRequest
import com.xiaomanjun.sleepdownschedule.feature.importing.configureEduImportSecurity
import com.xiaomanjun.sleepdownschedule.feature.importing.desktopWebUserAgent
import com.xiaomanjun.sleepdownschedule.feature.importing.eduImportCookieManager
import com.xiaomanjun.sleepdownschedule.feature.importing.shiguang.ShiguangBridgeHost
import com.xiaomanjun.sleepdownschedule.feature.importing.shiguang.injectShiguangRuntime
import com.xiaomanjun.sleepdownschedule.feature.importing.shiguang.installShiguangRuntime
import com.xiaomanjun.sleepdownschedule.feature.importing.shiguang.isolatedShiguangScript
import com.xiaomanjun.sleepdownschedule.feature.importing.shiguang.uninstallShiguangRuntime
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

internal data class AutoRefreshFetch(val draft: ImportDraft, val answers: Map<String, String>)

internal data class AutoRefreshLoginInteraction(
    val request: EduBridgeInteractionRequest,
    val bridge: ShiguangBridgeHost,
    val resolve: (String) -> Unit
)

internal class RefreshFailure(
    message: String,
    val connection: RefreshConnection? = null,
    val retryable: Boolean = false
) : IllegalStateException(message)

internal object AutoRefreshShiguangRunner {
    suspend fun fetch(
        context: Context,
        profile: AutoRefreshScheduleProfile,
        target: AppState,
        source: String,
        onInteraction: ((AutoRefreshLoginInteraction?) -> Unit)? = null
    ): AutoRefreshFetch = withTimeout(if (onInteraction == null) 90_000L else 360_000L) {
        withContext(Dispatchers.Main.immediate) {
            val name = profile.browserProfileName
            if (name == null || !AutoRefreshWebSession.isSupported()) {
                throw RefreshFailure("当前 WebView 需要在可见教务页面核对", RefreshConnection.BrowserOnly)
            }
            val url = requireNotNull(profile.authenticatedUrl)
            runWebView(context.applicationContext, profile, target, source, name, url, onInteraction)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun runWebView(
        context: Context,
        profile: AutoRefreshScheduleProfile,
        target: AppState,
        source: String,
        profileName: String,
        verifiedUrl: String,
        onInteraction: ((AutoRefreshLoginInteraction?) -> Unit)?
    ): AutoRefreshFetch = suspendCancellableCoroutine { continuation ->
        val handler = Handler(Looper.getMainLooper())
        val answers = profile.interactionAnswers.toMutableMap()
        val token = UUID.randomUUID().toString()
        var webView: WebView? = null
        var finished = false
        var started = false
        var document = 0
        lateinit var bridge: ShiguangBridgeHost

        fun close() {
            if (finished) return
            finished = true
            handler.removeCallbacksAndMessages(null)
            onInteraction?.invoke(null)
            bridge.close()
            webView?.let { view ->
                runCatching { view.uninstallShiguangRuntime() }
                view.releaseSleepDownWebView(clearResourceCache = false)
            }
        }

        fun fail(failure: RefreshFailure) {
            if (finished) return
            close()
            if (continuation.isActive) continuation.resumeWithException(failure)
        }

        fun signInRequired() = RefreshFailure("教务登录已失效，请重新连接", RefreshConnection.SignInRequired)
        fun report(message: String, diagnostic: String = message) {
            when {
                LoginFailure.containsMatchIn(diagnostic) -> fail(signInRequired())
                TransportFailure.containsMatchIn(diagnostic) ->
                    fail(RefreshFailure("教务网络请求失败，请检查网络后重试", retryable = true))
                AdapterFailure.containsMatchIn(message) ->
                    fail(RefreshFailure("教务适配未能返回有效课表，可打开教务页面检查"))
            }
        }
        bridge = ShiguangBridgeHost(
            onDraft = { draft ->
                if (!finished) {
                    if (draft.courses.isEmpty()) {
                        fail(RefreshFailure("教务未返回课程，请打开教务页面核对"))
                    } else {
                        webView?.let { runCatching { AutoRefreshWebSession.flush(it) } }
                        close()
                        if (continuation.isActive) continuation.resume(AutoRefreshFetch(draft, answers.toMap()))
                    }
                }
            },
            onMessage = { report(it) },
            onInteractionRequest = interaction@{ request ->
                if (finished) return@interaction
                val answer = AutoRefreshAnswers.resolve(answers, request)
                when {
                    answer != null -> bridge.resolveInteraction(request.requestId, answer)
                    request is EduBridgeInteractionRequest.Alert -> {
                        report(request.title, request.title + " " + request.message)
                        if (!finished) fail(RefreshFailure("教务适配未能返回有效课表，可打开教务页面检查"))
                    }
                    onInteraction != null -> onInteraction(AutoRefreshLoginInteraction(request, bridge) { value ->
                        if (!finished) {
                            val invalid = request is EduBridgeInteractionRequest.SingleSelection &&
                                value.toIntOrNull()?.let { it in request.options.indices } != true
                            if (value == "null" || invalid) fail(RefreshFailure("已取消连接"))
                            else {
                                AutoRefreshAnswers.record(answers, request, value)
                                bridge.resolveInteraction(request.requestId, value)
                            }
                        }
                    })
                    else -> fail(RefreshFailure("教务选项已变化，请重新确认学期或校区", RefreshConnection.OptionsRequired))
                }
            }
        )

        continuation.invokeOnCancellation { handler.post { close() } }
        try {
            val view = WebView(context)
            webView = view
            val cookies = view.eduImportCookieManager(standaloneImport = false, profileName = profileName)
            view.settings.javaScriptEnabled = true
            view.settings.domStorageEnabled = true
            view.settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            if (profile.desktopMode) view.settings.userAgentString =
                desktopWebUserAgent(WebSettings.getDefaultUserAgent(context))
            view.configureEduImportSecurity()
            view.settings.allowFileAccess = false
            view.settings.allowContentAccess = false
            cookies.setAcceptThirdPartyCookies(view, true)
            view.installShiguangRuntime(bridge)
            bridge.bindWebView(view)
            view.webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                    document++
                    if (started && !finished) fail(RefreshFailure("取课期间页面发生跳转，请在教务页面核对"))
                }

                override fun onPageFinished(view: WebView, url: String?) {
                    if (finished || started) return
                    val expectedDocument = document
                    handler.postDelayed({
                        if (finished || started || document != expectedDocument) return@postDelayed
                        if (!AutoRefreshLoginRoutes.isVerifiedOrigin(verifiedUrl, view.url)) {
                            fail(signInRequired())
                            return@postDelayed
                        }
                        started = true
                        try {
                            bridge.beginTask(target.config, target.periods, allowImportedBellTimes = true, executionToken = token)
                            view.injectShiguangRuntime(profile.desktopMode)
                            view.evaluateJavascript(isolatedShiguangScript(source, token)) { status ->
                                if (!finished && status != "\"started\"") fail(RefreshFailure("无法运行教务适配，可打开教务页面检查"))
                            }
                        } catch (_: Exception) {
                            fail(RefreshFailure("无法运行教务适配，可打开教务页面检查"))
                        }
                    }, 1_500L)
                }

                override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                    if (request?.isForMainFrame == true) fail(RefreshFailure("教务页面加载失败，请检查网络",
                        retryable = error?.errorCode in setOf(ERROR_HOST_LOOKUP, ERROR_CONNECT, ERROR_TIMEOUT, ERROR_IO)))
                }

                override fun onReceivedHttpError(view: WebView?, request: WebResourceRequest?, response: WebResourceResponse?) {
                    if (request?.isForMainFrame != true) return
                    val status = response?.statusCode ?: return
                    when {
                        status == 401 || status == 403 -> fail(signInRequired())
                        status == 429 || status in 500..599 ->
                            fail(RefreshFailure("教务系统暂时不可用，请稍后重试", retryable = true))
                        status >= 400 -> fail(RefreshFailure("绑定的教务页面已无法打开", RefreshConnection.AdapterChanged))
                    }
                }

                override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                    webView = null
                    runCatching { view.destroy() }
                    fail(RefreshFailure("教务浏览器已停止，请重试", retryable = true))
                    return true
                }
            }
            view.loadUrl(verifiedUrl)
        } catch (_: Exception) {
            fail(RefreshFailure("无法打开后台教务页面，可打开教务页面检查"))
        }
    }

    private val LoginFailure = Regex("重新登录|请.{0,4}登录|未登录|登录.{0,8}失效|unauthori[sz]ed", RegexOption.IGNORE_CASE)
    private val TransportFailure = Regex("超时|网络请求失败|ERR_(CONNECTION|NETWORK)|failed to fetch|timeout", RegexOption.IGNORE_CASE)
    private val AdapterFailure = Regex("失败|错误|无效|无法|未找到|未获取到|无课程数据|无课表数据|数据为空|导入已取消|error|exception|invalid|forbidden", RegexOption.IGNORE_CASE)
}
