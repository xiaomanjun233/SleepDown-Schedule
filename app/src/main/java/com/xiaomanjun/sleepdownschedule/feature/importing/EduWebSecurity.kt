package com.xiaomanjun.sleepdownschedule.feature.importing

import android.annotation.SuppressLint
import android.os.Build
import android.view.View
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

/**
 * Keeps credential handling inside Android's Autofill framework. WebView supplies the current web
 * domain and its virtual HTML input nodes, so the user's selected service (for example Edge) can
 * offer credentials for that site without SleepDown reading or storing the password.
 */
internal fun WebView.enableSystemCredentialAutofill() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_YES
    isSaveEnabled = true
}
