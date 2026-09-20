package com.xiaomanjun.sleepdownschedule.feature.importing

import android.annotation.SuppressLint
import android.view.View
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.webkit.ScriptHandler
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import java.util.Locale
import kotlin.math.max

internal const val DesktopWebUserAgent =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

internal fun desktopViewportContent(widthPx: Int, density: Float, screenWidthDp: Int): String {
    val cssWidth = if (widthPx > 0 && density.isFinite() && density > 0f) widthPx / density else screenWidthDp.toFloat()
    val available = cssWidth.coerceAtLeast(1f)
    val layoutWidth = max(1280f, available)
    return String.format(Locale.US, "width=%.4f, initial-scale=%.4f", layoutWidth, available / layoutWidth)
}

/**
 * Desktop layout follows Nexio's document-start viewport approach. Legacy portals often write
 * body/login widths inline in document.ready and never handle resize. onPageFinished is too
 * late for those measurements, so it is only the fallback for old WebView providers.
 */
@SuppressLint("SetJavaScriptEnabled")
internal class WebCompatDelegate(private val webView: WebView, desktop: Boolean) {
    private var desktopMode = desktop
    private val documentStartSupported = WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
    private var documentStartScript: ScriptHandler? = null
    private var viewportContent: String? = null
    private var disposed = false
    private var pendingInitialUrl: String? = null
    private val layoutListener = View.OnLayoutChangeListener { _, left, _, right, _, oldLeft, _, oldRight, _ ->
        if (!disposed && right > left && right - left != oldRight - oldLeft) {
            updateViewportScript()
            if (desktopMode && pendingInitialUrl == null) {
                // Resize the current viewport without reloading a login form or replaying POST.
                webView.evaluateJavascript(desktopViewportScript(checkNotNull(viewportContent)), null)
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
        webView.setInitialScale(0)
        updateViewportScript()
        webView.addOnLayoutChangeListener(layoutListener)
    }

    fun loadInitialUrl(url: String) {
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
        webView.stopLoading()
        desktopMode = enabled
        updateViewportScript()
        applyUserAgent()
        webView.setInitialScale(0)
        // Removing the registration affects future documents. Reload is required in both
        // directions so mobile gets the website's original viewport and no desktop observer.
        if (pendingInitialUrl == null) webView.reload()
    }

    fun onPageFinished() {
        if (!disposed && desktopMode && !documentStartSupported) {
            updateViewportScript()
            webView.evaluateJavascript(desktopViewportScript(checkNotNull(viewportContent)), null)
        }
    }

    fun dispose(rendererGone: Boolean = false) {
        if (disposed) return
        disposed = true
        pendingInitialUrl = null
        webView.removeOnLayoutChangeListener(layoutListener)
        if (!rendererGone) documentStartScript?.remove()
        documentStartScript = null
    }

    private fun applyUserAgent() {
        webView.settings.userAgentString = if (desktopMode) DesktopWebUserAgent else
            WebSettings.getDefaultUserAgent(webView.context)
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
