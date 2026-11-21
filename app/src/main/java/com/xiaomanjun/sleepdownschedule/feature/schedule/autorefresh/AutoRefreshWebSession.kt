package com.xiaomanjun.sleepdownschedule.feature.schedule.autorefresh

import android.annotation.SuppressLint
import android.webkit.WebView
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import java.util.UUID

/** Chromium owns the session. SleepDown persists only the binding and its verified page. */
@SuppressLint("RequiresFeature")
internal object AutoRefreshWebSession {
    const val RestoreSessionExtra = "edu_restore_auto_refresh_session"
    const val BindingExtra = "edu_refresh_binding"
    private const val ProfilePrefix = "sleepdown_refresh_"
    private val bindingPattern = Regex("[a-f0-9]{32}")
    private var pruned = false

    fun newBindingId(): String = UUID.randomUUID().toString().replace("-", "")
    fun isBindingId(id: String): Boolean = bindingPattern.matches(id)
    fun profileName(id: String): String = ProfilePrefix + id
    fun isSupported(): Boolean = WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)

    fun flush(webView: WebView) {
        if (isSupported()) WebViewCompat.getProfile(webView).cookieManager.flush()
    }

    /** Run on application startup, before any WebView can load a refresh profile. */
    fun pruneRetiredProfiles(activeName: String?) {
        if (pruned || !isSupported()) return
        pruned = true
        val store = ProfileStore.getInstance()
        store.allProfileNames.filter { it.startsWith(ProfilePrefix) && it != activeName }
            .forEach { runCatching { store.deleteProfile(it) } }
    }

    fun retire(name: String?) {
        if (name == null || !name.startsWith(ProfilePrefix) || !isSupported()) return
        val store = ProfileStore.getInstance()
        // A loaded profile cannot be deleted in this process. Clear its session now and
        // remove the isolated profile directory when the application next starts.
        if (runCatching { store.deleteProfile(name) }.isSuccess) return
        store.getProfile(name)?.let { profile ->
            val cookies = profile.cookieManager
            cookies.removeAllCookies { cookies.flush() }
            profile.webStorage.deleteAllData()
        }
    }
}
