package com.xiaomanjun.sleepdownschedule.core.web

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri

internal fun Context.resolveSleepDownCustomTabsPackage(): String? {
    return CustomTabsClient.getPackageName(
        this,
        listOf("com.microsoft.emmx"),
        true
    ) ?: CustomTabsClient.getPackageName(this, emptyList())
}

internal fun Context.openSleepDownCustomTab(
    url: String,
    providerPackage: String?,
    initialWidthPx: Int,
    darkTheme: Boolean
) {
    val chromeColor = if (darkTheme) android.graphics.Color.BLACK else android.graphics.Color.WHITE
    runCatching {
        val customTab = CustomTabsIntent.Builder()
            .setShowTitle(true)
            .setShareState(CustomTabsIntent.SHARE_STATE_OFF)
            .setColorScheme(
                if (darkTheme) CustomTabsIntent.COLOR_SCHEME_DARK else CustomTabsIntent.COLOR_SCHEME_LIGHT
            )
            .setToolbarColor(chromeColor)
            .setNavigationBarColor(chromeColor)
            .setInitialActivityWidthPx(initialWidthPx)
            .setActivitySideSheetBreakpointDp(600)
            .setActivitySideSheetPosition(CustomTabsIntent.ACTIVITY_SIDE_SHEET_POSITION_END)
            .setActivitySideSheetDecorationType(CustomTabsIntent.ACTIVITY_SIDE_SHEET_DECORATION_TYPE_DIVIDER)
            .setActivitySideSheetRoundedCornersPosition(CustomTabsIntent.ACTIVITY_SIDE_SHEET_ROUNDED_CORNERS_POSITION_TOP)
            .setActivitySideSheetMaximizationEnabled(false)
            // AndroidX Browser accepts toolbar radii in the inclusive 0..16 dp range.
            .setToolbarCornerRadiusDp(16)
            .setCloseButtonPosition(CustomTabsIntent.CLOSE_BUTTON_POSITION_START)
            .setBackgroundInteractionEnabled(false)
            .build()
            .also { intent -> providerPackage?.let(intent.intent::setPackage) }
        customTab.launchUrl(this, url.toUri())
    }.onFailure {
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
        }.onFailure {
            Toast.makeText(this, "未找到可打开此链接的浏览器", Toast.LENGTH_SHORT).show()
        }
    }
}
