package com.xiaomanjun.sleepdownschedule.feature.schedule.autorefresh

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xiaomanjun.sleepdownschedule.CourseScheduleApp
import com.xiaomanjun.sleepdownschedule.CourseScheduleTheme
import com.xiaomanjun.sleepdownschedule.app.state.ScheduleViewModel
import com.xiaomanjun.sleepdownschedule.app.state.ScheduleViewModelFactory
import com.xiaomanjun.sleepdownschedule.app.ui.DetailActivityScaffold
import com.xiaomanjun.sleepdownschedule.app.ui.detailContentTopPadding
import com.xiaomanjun.sleepdownschedule.app.ui.releaseSleepDownWebView
import com.xiaomanjun.sleepdownschedule.feature.importing.configureEduImportSecurity
import com.xiaomanjun.sleepdownschedule.feature.importing.enableSystemCredentialAutofill
import java.util.Locale

/** Visible authentication surface used only by the experimental SWU auto-refresh flow. */
class SwuUnifiedAuthActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            val app = application as CourseScheduleApp
            val scheduleViewModel: ScheduleViewModel = viewModel(
                factory = ScheduleViewModelFactory(app, app.repository)
            )
            val state by scheduleViewModel.state.collectAsStateWithLifecycle()
            CourseScheduleTheme(config = state.config) {
                DetailActivityScaffold(
                    title = "西南大学统一认证",
                    config = state.config,
                    onBack = ::finish,
                    isolateContentFromBackdrop = true,
                    compactTopBar = true,
                    centerCompactTitle = true,
                    compactTitleMatchesSettings = true,
                    preserveStatusBarSpace = true
                ) {
                    SwuUnifiedAuthBrowser(
                        topPadding = detailContentTopPadding(),
                        onAuthenticated = { currentUrl ->
                            CookieManager.getInstance().flush()
                            setResult(
                                Activity.RESULT_OK,
                                Intent().putExtra(CurrentUrlExtra, currentUrl)
                            )
                            finish()
                        }
                    )
                }
            }
        }
    }

    companion object {
        internal const val PortalUrl = "https://i.swu.edu.cn/"
        internal const val TeachingRootUrl = "https://jw.swu.edu.cn/"
        internal const val CoursePageUrl =
            "https://jw.swu.edu.cn/jwglxt/kbcx/xskbcx_cxXskbcxIndex.html?gnmkdm=N2151"
        private const val CurrentUrlExtra = "swu_unified_auth_current_url"

        internal fun intent(context: Context): Intent =
            Intent(context, SwuUnifiedAuthActivity::class.java)

        internal fun captureCookies(resultData: Intent?): List<AutoRefreshCookie> {
            val cookieManager = CookieManager.getInstance()
            val urls = listOfNotNull(
                PortalUrl,
                TeachingRootUrl,
                CoursePageUrl,
                resultData?.getStringExtra(CurrentUrlExtra)
            ).filter { it.startsWith("https://") }.distinct()
            return urls.mapNotNull { url ->
                cookieManager.getCookie(url)
                    ?.takeIf(String::isNotBlank)
                    ?.let { value -> AutoRefreshCookie(url, value) }
            }
        }

        internal fun isCoursePage(url: String?): Boolean {
            val uri = runCatching { Uri.parse(url.orEmpty()) }.getOrNull() ?: return false
            return uri.host.equals("jw.swu.edu.cn", ignoreCase = true) &&
                uri.path.orEmpty().contains("xskbcx_cxXskbcxIndex.html")
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun SwuUnifiedAuthBrowser(
    topPadding: androidx.compose.ui.unit.Dp,
    onAuthenticated: (String) -> Unit
) {
    val context = LocalContext.current
    var webView by remember { mutableStateOf<WebView?>(null) }
    var currentUrl by remember { mutableStateOf(SwuUnifiedAuthActivity.CoursePageUrl) }
    var loading by remember { mutableStateOf(true) }
    var ready by remember { mutableStateOf(false) }
    var pageError by remember { mutableStateOf<String?>(null) }

    fun openExternal(uri: Uri): Boolean {
        val external = runCatching {
            if (uri.scheme.equals("intent", ignoreCase = true)) {
                Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME)
            } else {
                Intent(Intent.ACTION_VIEW, uri)
            }
        }.getOrElse {
            pageError = "无法识别网页请求的外部链接"
            return true
        }.apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
            component = null
            selector = null
        }
        val fallbackUrl = external.getStringExtra("browser_fallback_url")
        runCatching { context.startActivity(external) }.onFailure {
            if (fallbackUrl?.let(::isHttpUrl) == true) {
                webView?.loadUrl(fallbackUrl)
            } else {
                pageError = "未找到可处理该认证链接的应用"
            }
        }
        return true
    }

    BackHandler(enabled = webView?.canGoBack() == true) {
        webView?.goBack()
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = topPadding)
            .background(MaterialTheme.colorScheme.background)
    ) {
        Box(Modifier.fillMaxWidth().weight(1f)) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { webContext ->
                    WebView(webContext).apply webView@ {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.databaseEnabled = true
                        settings.cacheMode = WebSettings.LOAD_DEFAULT
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        settings.javaScriptCanOpenWindowsAutomatically = true
                        settings.setSupportMultipleWindows(false)
                        configureEduImportSecurity()
                        enableSystemCredentialAutofill()
                        CookieManager.getInstance().apply {
                            setAcceptCookie(true)
                            setAcceptThirdPartyCookies(this@webView, true)
                        }
                        webChromeClient = WebChromeClient()
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest
                            ): Boolean {
                                val scheme = request.url.scheme.orEmpty().lowercase(Locale.ROOT)
                                return if (scheme == "http" || scheme == "https") false
                                else openExternal(request.url)
                            }

                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                super.onPageStarted(view, url, favicon)
                                currentUrl = url.orEmpty()
                                loading = true
                                ready = false
                                pageError = null
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                currentUrl = url.orEmpty()
                                loading = false
                                ready = pageError == null && SwuUnifiedAuthActivity.isCoursePage(url)
                                CookieManager.getInstance().flush()
                            }

                            override fun onReceivedError(
                                view: WebView?,
                                request: WebResourceRequest?,
                                error: WebResourceError?
                            ) {
                                super.onReceivedError(view, request, error)
                                if (request?.isForMainFrame == true) {
                                    loading = false
                                    ready = false
                                    pageError = "页面加载失败：${error?.description ?: "网络连接异常"}"
                                }
                            }

                            override fun onReceivedHttpError(
                                view: WebView?,
                                request: WebResourceRequest?,
                                errorResponse: WebResourceResponse?
                            ) {
                                super.onReceivedHttpError(view, request, errorResponse)
                                if (request?.isForMainFrame == true) {
                                    loading = false
                                    ready = false
                                    pageError = "页面返回 HTTP ${errorResponse?.statusCode ?: "错误"}"
                                }
                            }

                            override fun onReceivedSslError(
                                view: WebView?,
                                handler: SslErrorHandler?,
                                error: SslError?
                            ) {
                                handler?.cancel()
                                loading = false
                                ready = false
                                pageError = "网站证书校验失败，已停止加载"
                            }
                        }
                        webView = this
                        loadUrl(SwuUnifiedAuthActivity.CoursePageUrl)
                    }
                },
                update = {},
                onRelease = { released ->
                    if (webView === released) webView = null
                    released.releaseSleepDownWebView(clearResourceCache = false)
                }
            )
            if (loading) {
                LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceContainer
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = when {
                        pageError != null -> pageError!!
                        ready -> "已进入西南大学教务系统，可以返回并校验课表接口。"
                        else -> "请在上方完成统一认证。如登录后没有自动返回教务系统，点击下方按钮。"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (pageError != null) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                Text(
                    "此页仅为自动刷新保存会话 Cookie，不会触发原教务导入。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { webView?.reload() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("重新加载")
                    }
                    Button(
                        onClick = {
                            if (ready) onAuthenticated(currentUrl)
                            else webView?.loadUrl(SwuUnifiedAuthActivity.CoursePageUrl)
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (ready) "认证完成" else "进入教务系统")
                    }
                }
            }
        }
    }
}

private fun isHttpUrl(value: String): Boolean {
    val scheme = runCatching { Uri.parse(value).scheme }.getOrNull()
    return scheme.equals("http", ignoreCase = true) || scheme.equals("https", ignoreCase = true)
}
