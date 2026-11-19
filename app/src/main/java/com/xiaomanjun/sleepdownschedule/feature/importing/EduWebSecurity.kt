package com.xiaomanjun.sleepdownschedule.feature.importing

import android.annotation.SuppressLint
import android.os.Build
import android.util.Log
import android.view.View
import android.view.autofill.AutofillManager
import android.webkit.CookieManager
import android.webkit.WebView
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature

internal fun normalizeEduUrl(input: String): String {
    val trimmed = input.trim()
    return when {
        trimmed.isBlank() ||
            trimmed.equals("http://", ignoreCase = true) ||
            trimmed.equals("https://", ignoreCase = true) -> ""
        trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true) -> trimmed
        !HostPortPrefix.containsMatchIn(trimmed) && ExplicitUriScheme.containsMatchIn(trimmed) -> ""
        else -> "https://$trimmed"
    }
}

private val HostPortPrefix = Regex(
    "^(?:\\[[^\\]]+]|localhost|[^/:?#]+\\.[^/:?#]+):\\d+(?:[/?#]|$)",
    RegexOption.IGNORE_CASE
)
private val ExplicitUriScheme = Regex("^[A-Za-z][A-Za-z0-9+.-]*:")

/** Call before navigation or configuring the view, including its popups. */
@SuppressLint("RequiresFeature")
internal fun WebView.eduImportCookieManager(standaloneImport: Boolean): CookieManager {
    if (standaloneImport && WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
        // Auto refresh restores saved credentials into the default profile. The interactive
        // browser has its own persistent cookies and site storage managed by Chromium.
        WebViewCompat.setProfile(this, "sleepdown_edu_import")
        return WebViewCompat.getProfile(this).cookieManager
    }
    return CookieManager.getInstance()
}

@SuppressLint("SetJavaScriptEnabled", "RequiresFeature")
internal fun WebView.configureEduImportSecurity(): Boolean {
    settings.allowContentAccess = true
    settings.allowFileAccess = true
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        @Suppress("DEPRECATION")
        settings.allowUniversalAccessFromFileURLs = true
        @Suppress("DEPRECATION")
        settings.allowFileAccessFromFileURLs = true
    }
    if (WebViewFeature.isFeatureSupported(WebViewFeature.REQUESTED_WITH_HEADER_ALLOW_LIST)) {
        return runCatching {
            WebSettingsCompat.setRequestedWithHeaderOriginAllowList(settings, emptySet())
            true
        }.getOrElse { error ->
            Log.w(
                "EduWebView",
                "requestedWith strip failed: ${error.javaClass.simpleName}: ${error.message}"
            )
            false
        }
    }
    Log.i("EduWebView", "requestedWith strip unsupported by current WebView")
    return false
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

/** Complete only a verified login/import, while the original visible WebView still exists. */
internal fun WebView.commitSystemCredentialAutofill() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    // Native WebView virtual fields keep the actual SSO domain, including popup login windows.
    // Neither field values nor password-manager data pass through the JavaScript import bridge.
    try {
        context.getSystemService(AutofillManager::class.java)?.commit()
    } catch (error: RuntimeException) {
        // The optional provider must not turn an already-saved session/draft into a login failure.
        Log.w("EduAutofill", "System autofill commit failed: ${error.javaClass.simpleName}")
    }
}
