package com.xiaomanjun.sleepdownschedule.feature.importing.shiguang

import android.webkit.WebView
import java.util.WeakHashMap

// WebView lifecycle calls run on the main thread. Each window owns its pending requests.
private val postInterceptors = WeakHashMap<WebView, ShiguangWebRequestInterceptor>()

internal fun WebView.installShiguangRuntime(
    host: ShiguangBridgeHost,
    interceptor: ShiguangWebRequestInterceptor? = null
) {
    postInterceptors.remove(this)?.close()
    addJavascriptInterface(ShiguangNativeBridge(host), "_shiguangNativeBridge")
    if (interceptor != null) {
        postInterceptors[this] = interceptor
        addJavascriptInterface(interceptor.bridge, "WebPostService")
    }
}

internal fun WebView.injectShiguangRuntime(
    desktopMode: Boolean,
    documentUrl: String? = url,
    postForwardEnabled: Boolean = false
) {
    postInterceptors[this]?.documentUrl = documentUrl
    evaluateJavascript(ShiguangBridgeInitScript, null)
    // Match Nexio: keep native browser POST handling enabled by default so capturing-phase
    // hooks and X-WebView-Post-Id headers do not interfere with SSO/WAF form scripts.
    if (desktopMode && postForwardEnabled) evaluateJavascript(ShiguangPostInterceptScript, null)
}

internal fun WebView.uninstallShiguangRuntime() {
    postInterceptors.remove(this)?.close()
    removeJavascriptInterface("_shiguangNativeBridge")
    removeJavascriptInterface("WebPostService")
}
