package com.xiaomanjun.sleepdownschedule.feature.importing.chatgpt

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsIntent
import androidx.browser.customtabs.CustomTabsService

/** Browser-owned authentication: shares the user's browser session, never a WebView. */
internal object ChatGptBrowser {
    fun open(context: Context, url: String) {
        val activity = generateSequence(context) { (it as? ContextWrapper)?.baseContext }
            .filterIsInstance<Activity>().firstOrNull()
        val launchContext = activity ?: context
        val candidates = launchContext.packageManager.queryIntentServices(
            Intent(CustomTabsService.ACTION_CUSTOM_TABS_CONNECTION), 0
        ).map { it.serviceInfo.packageName }.distinct()
        val provider = CustomTabsClient.getPackageName(launchContext, candidates)
            ?: throw ChatGptAuthException("请先安装或启用支持 Custom Tab 的浏览器（如 Chrome、Edge），再重试。")
        try {
            createIntent(provider, activity == null).launchUrl(launchContext, Uri.parse(url))
        } catch (_: ActivityNotFoundException) {
            throw ChatGptAuthException("无法打开浏览器，请检查默认浏览器设置后重试。")
        }
    }

    internal fun createIntent(provider: String, newTask: Boolean): CustomTabsIntent =
        CustomTabsIntent.Builder()
            .setShowTitle(true)
            .setShareState(CustomTabsIntent.SHARE_STATE_OFF)
            .setColorScheme(CustomTabsIntent.COLOR_SCHEME_SYSTEM)
            .build().also {
                it.intent.setPackage(provider)
                if (newTask) it.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
}
