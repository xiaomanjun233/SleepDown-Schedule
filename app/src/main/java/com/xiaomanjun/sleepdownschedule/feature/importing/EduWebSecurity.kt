package com.xiaomanjun.sleepdownschedule.feature.importing

import android.annotation.SuppressLint
import android.webkit.WebView

internal fun normalizeEduUrl(input: String): String {
    val trimmed = input.trim()
    return when {
        trimmed.isBlank() ||
            trimmed.equals("http://", ignoreCase = true) ||
            trimmed.equals("https://", ignoreCase = true) -> ""
        trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true) -> trimmed
        ExplicitUriScheme.containsMatchIn(trimmed) -> ""
        else -> "https://$trimmed"
    }
}

private val ExplicitUriScheme = Regex("^[A-Za-z][A-Za-z0-9+.-]*:")

@SuppressLint("SetJavaScriptEnabled")
internal fun WebView.configureEduImportSecurity() {
    settings.allowContentAccess = false
    settings.allowFileAccess = false
}
