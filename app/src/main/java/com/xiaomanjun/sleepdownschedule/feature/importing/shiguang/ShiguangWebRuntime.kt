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

internal fun WebView.injectShiguangRuntime(desktopMode: Boolean, documentUrl: String? = url) {
    postInterceptors[this]?.documentUrl = documentUrl
    evaluateJavascript(ShiguangBridgeInitScript, null)
    if (desktopMode) evaluateJavascript(ShiguangPostInterceptScript, null)
}

internal fun WebView.uninstallShiguangRuntime() {
    postInterceptors.remove(this)?.close()
    removeJavascriptInterface("_shiguangNativeBridge")
    removeJavascriptInterface("WebPostService")
}
