package com.xiaomanjun.sleepdownschedule.feature.importing

import android.annotation.SuppressLint
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.webkit.ScriptHandler
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

internal fun desktopWebUserAgent(defaultUserAgent: String): String {
    val chromeVersion = Regex("Chrome/[0-9.]+").find(defaultUserAgent)?.value ?: return defaultUserAgent
    return "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) $chromeVersion Safari/537.36"
}

internal fun desktopViewportContent(widthPx: Int, density: Float, screenWidthDp: Int): String {
    val cssWidth = if (widthPx > 0 && density.isFinite() && density > 0f) widthPx / density else screenWidthDp.toFloat()
    val available = cssWidth.coerceAtLeast(1f)
    val layoutWidth = max(1280f, available)
    return String.format(Locale.US, "width=%.4f, initial-scale=%.4f", layoutWidth, available / layoutWidth)
}

internal fun desktopViewportScale(widthPx: Int, density: Float, screenWidthDp: Int): Float {
    val cssWidth = if (widthPx > 0 && density.isFinite() && density > 0f) widthPx / density else screenWidthDp.toFloat()
    val available = cssWidth.coerceAtLeast(1f)
    return available / max(1280f, available)
}

internal fun desktopZoomFactor(current: Float, expected: Float): Float? {
    if (!current.isFinite() || !expected.isFinite() || current <= 0f || expected <= 0f) return null
    if (abs(current - expected) <= 0.03f) return null
    return (expected / current).coerceIn(0.01f, 100f)
}

internal fun isMissingEduScript(message: String): Boolean =
    MissingEduScript.containsMatchIn(message)

private val MissingEduScript = Regex(
    """(\$|jQuery|beangle|bg) is not defined|requires jQuery""", RegexOption.IGNORE_CASE
)

/**
 * Desktop layout follows Nexio's document-start viewport approach. Legacy portals often write
 * body/login widths inline in document.ready and never handle resize. onPageFinished is too
 * late for those measurements, so it is only the fallback for old WebView providers.
 */
@SuppressLint("SetJavaScriptEnabled")
internal class WebCompatDelegate(
    private val webView: WebView,
    desktop: Boolean,
    private val onMessage: (String) -> Unit = {}
) {
    private var desktopMode = desktop
    private val documentStartSupported = WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
    private var documentStartScript: ScriptHandler? = null
    private var viewportContent: String? = null
    private var disposed = false
    private var pendingInitialUrl: String? = null
    private val resources = EduWebResourceInterceptor.create(webView.context)
    @Volatile private var userAgent = ""
    private var documentVersion = 0
    private var interacted = false
    private var zoomChecked = false
    private var pendingZoom: Runnable? = null
    private var pendingRecovery: Runnable? = null
    private val layoutListener = View.OnLayoutChangeListener { _, left, _, right, _, oldLeft, _, oldRight, _ ->
        if (!disposed && right > left && right - left != oldRight - oldLeft) {
            updateViewportScript()
            if (desktopMode && pendingInitialUrl == null) {
                // Resize the current viewport without reloading a login form or replaying POST.
                webView.evaluateJavascript(desktopViewportScript(checkNotNull(viewportContent)), null)
                if (!interacted) correctDesktopZoom(documentVersion, webView.url, 0)
            }
            loadPendingUrl()
        }
    }

    init {
        with(webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            layoutAlgorithm = WebSettings.LayoutAlgorithm.NORMAL
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            textZoom = 100
        }
        applyUserAgent()
        updateViewportScript()
        webView.addOnLayoutChangeListener(layoutListener)
    }

    fun loadInitialUrl(url: String) {
        if (disposed) return
        pendingInitialUrl = url
        if (webView.width > 0) loadPendingUrl()
    }

    private fun loadPendingUrl() {
        val url = pendingInitialUrl ?: return
        pendingInitialUrl = null
        updateViewportScript()
        webView.loadUrl(url)
    }

    fun setDesktopMode(enabled: Boolean) {
        if (disposed || enabled == desktopMode) return
        cancelPageCallbacks()
        documentVersion++
        webView.stopLoading()
        desktopMode = enabled
        updateViewportScript()
        applyUserAgent()
        // Removing the registration affects future documents. Reload is required in both
        // directions so mobile gets the website's original viewport and no desktop observer.
        if (pendingInitialUrl == null) webView.reload()
    }

    fun onPageStarted() {
        cancelPageCallbacks()
        documentVersion++
        interacted = false
        zoomChecked = false
    }

    fun onUserInteraction() {
        interacted = true
        cancelPageCallbacks()
    }

    fun intercept(request: WebResourceRequest): WebResourceResponse? = resources.intercept(request, userAgent)

    fun recordRequest(request: WebResourceRequest) {
        if (request.isForMainFrame) resources.policy.recordNavigation(request.url.toString(), request.method)
    }

    fun onHttpError(request: WebResourceRequest, status: Int) {
        if (!request.isForMainFrame && status >= 400 &&
            eduWebOrigin(request.url.toString()) == eduWebOrigin(webView.url) &&
            isEduStyleOrScript(request.url.toString(), request.requestHeaders)) {
            recoverResources()
        }
    }

    fun onConsoleMessage(message: ConsoleMessage) {
        if (message.messageLevel() == ConsoleMessage.MessageLevel.ERROR &&
            isMissingEduScript(message.message().orEmpty()) &&
            (message.sourceId().isNullOrBlank() || eduWebOrigin(message.sourceId()) == eduWebOrigin(webView.url))) {
            recoverResources()
        }
    }

    private fun recoverResources() {
        val url = webView.url ?: return
        if (disposed || interacted || pendingRecovery != null || !resources.policy.enable(url)) return
        val version = documentVersion
        pendingRecovery = Runnable {
            pendingRecovery = null
            if (!disposed && !interacted && version == documentVersion && webView.url == url &&
                webView.isAttachedToWindow && resources.policy.canReload(url)) {
                onMessage("正在兼容加载此教务网站…")
                webView.reload()
            }
        }.also { webView.postDelayed(it, 1200) }
    }

    fun onPageFinished(url: String?) {
        if (disposed || url != webView.url) return
        if (desktopMode && !documentStartSupported) {
            updateViewportScript()
            webView.evaluateJavascript(desktopViewportScript(checkNotNull(viewportContent)), null)
        }
        if (desktopMode && !zoomChecked && !interacted) {
            zoomChecked = true
            correctDesktopZoom(documentVersion, url, 0)
        }
    }

    private fun expectedScale(): Float = desktopViewportScale(
        webView.width, webView.resources.displayMetrics.density, webView.resources.configuration.screenWidthDp
    )

    private fun correctDesktopZoom(version: Int, url: String?, attempt: Int) {
        fun isCurrent() = !disposed && desktopMode && !interacted && version == documentVersion &&
            url == webView.url && webView.isAttachedToWindow
        if (!isCurrent()) return
        webView.evaluateJavascript("window.visualViewport ? window.visualViewport.scale : -1") { raw ->
            if (!isCurrent()) return@evaluateJavascript
            val scale = raw?.trim('"')?.toFloatOrNull() ?: return@evaluateJavascript
            val factor = desktopZoomFactor(scale, expectedScale()) ?: return@evaluateJavascript
            webView.zoomBy(factor)
            // Chromium animates page scale; check once after it settles, never reload a form.
            if (attempt == 0) {
                pendingZoom?.let(webView::removeCallbacks)
                pendingZoom = Runnable {
                    pendingZoom = null
                    correctDesktopZoom(version, url, 1)
                }.also { webView.postDelayed(it, 400) }
            }
        }
    }

    private fun cancelPageCallbacks() {
        pendingZoom?.let(webView::removeCallbacks)
        pendingRecovery?.let(webView::removeCallbacks)
        pendingZoom = null
        pendingRecovery = null
    }

    fun dispose(rendererGone: Boolean = false) {
        if (disposed) return
        disposed = true
        documentVersion++
        cancelPageCallbacks()
        resources.policy.close()
        pendingInitialUrl = null
        webView.removeOnLayoutChangeListener(layoutListener)
        if (!rendererGone) documentStartScript?.remove()
        documentStartScript = null
    }

    private fun applyUserAgent() {
        val default = WebSettings.getDefaultUserAgent(webView.context)
        userAgent = if (desktopMode) desktopWebUserAgent(default) else default
        webView.settings.userAgentString = userAgent
    }

    private fun updateViewportScript() {
        val next = if (desktopMode) desktopViewportContent(
            webView.width,
            webView.resources.displayMetrics.density,
            webView.resources.configuration.screenWidthDp
        ) else null
        if (next == viewportContent) return
        documentStartScript?.remove()
        documentStartScript = null
        viewportContent = next
        webView.setInitialScale(if (desktopMode) (expectedScale() * 100).roundToInt().coerceIn(1, 100) else 0)
        if (documentStartSupported && next != null) {
            documentStartScript = WebViewCompat.addDocumentStartJavaScript(
                webView, desktopViewportScript(next), setOf("*")
            )
        }
    }
}

internal fun desktopViewportScript(content: String): String = """
    (function () {
        if (window.top !== window) return;
        var key = '__sleepdownDesktopViewport';
        var content = '$content';
        var existing = window[key];
        if (existing) {
            existing.content = content;
            existing.apply();
            return;
        }
        var state = { content: content, observer: null, apply: null };
        function apply() {
            var head = document.head;
            if (!head) return;
            // Disconnect around our own mutations to avoid an observer/attribute-write loop.
            state.observer.disconnect();
            var own = head.querySelector('meta[data-nexio-desktop-viewport]');
            var metas = head.querySelectorAll('meta');
            for (var i = 0; i < metas.length; i++) {
                var meta = metas[i];
                if ((meta.getAttribute('name') || '').toLowerCase() === 'viewport' && meta !== own) {
                    meta.parentNode.removeChild(meta);
                }
            }
            if (!own) {
                own = document.createElement('meta');
                own.setAttribute('data-nexio-desktop-viewport', 'true');
                head.appendChild(own);
            }
            if (own.getAttribute('name') !== 'viewport') own.setAttribute('name', 'viewport');
            if (own.getAttribute('content') !== state.content) own.setAttribute('content', state.content);
            state.observer.observe(head, {
                childList: true, subtree: true, attributes: true,
                attributeFilter: ['name', 'content', 'data-nexio-desktop-viewport']
            });
        }
        state.apply = apply;
        state.observer = new MutationObserver(apply);
        window[key] = state;
        // document-start can run before <head> exists. Stop watching the whole document as
        // soon as head is available; normal operation observes only head, not the page body.
        state.observer.observe(document, { childList: true, subtree: true });
        apply();
        document.addEventListener('DOMContentLoaded', apply, { once: true });
        window.addEventListener('load', apply, { once: true });
    })();
""".trimIndent()
