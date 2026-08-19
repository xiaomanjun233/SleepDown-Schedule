package com.xiaomanjun.sleepdownschedule

import android.annotation.SuppressLint
import android.webkit.WebView

internal const val EDU_BRIDGE_TEST_PAGE_URL =
    "file:///android_asset/shiguang_warehouse-main/resources/GLOBAL_TOOLS/test_page.html"

internal fun normalizeEduUrl(input: String): String {
    val trimmed = input.trim()
    return when {
        trimmed.isBlank() ||
            trimmed.equals("http://", ignoreCase = true) ||
            trimmed.equals("https://", ignoreCase = true) -> ""
        trimmed.equals(EDU_BRIDGE_TEST_PAGE_URL, ignoreCase = true) -> EDU_BRIDGE_TEST_PAGE_URL
        trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true) -> trimmed
        ExplicitUriScheme.containsMatchIn(trimmed) -> ""
        else -> "https://$trimmed"
    }
}

private val ExplicitUriScheme = Regex("^[A-Za-z][A-Za-z0-9+.-]*:")

@SuppressLint("SetJavaScriptEnabled")
internal fun WebView.configureEduImportSecurity(adapter: EduAdapter) {
    settings.allowContentAccess = false
    settings.allowFileAccess = adapter.isEduTestTool()
}

@SuppressLint("JavascriptInterface")
internal fun WebView.attachEduImportBridge(bridge: EduImportBridge) {
    detachEduImportBridge()
    addJavascriptInterface(bridge, "AndroidBridgePromise")
    addJavascriptInterface(bridge, "AndroidBridge")
}

internal fun WebView.detachEduImportBridge() {
    removeJavascriptInterface("AndroidBridgePromise")
    removeJavascriptInterface("AndroidBridge")
}
