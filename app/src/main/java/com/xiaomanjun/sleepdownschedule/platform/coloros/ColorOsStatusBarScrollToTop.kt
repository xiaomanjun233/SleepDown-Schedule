package com.xiaomanjun.sleepdownschedule.platform.coloros

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.node.RootForTest
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect

private const val StatusBarClicked = "com.oplus.clicktop"
private const val StatusBarServicePermission = "android.permission.STATUS_BAR_SERVICE"

/** ColorOS scans native scrollable Views. Compose lists expose their scroll actions as semantics. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun ColorOsStatusBarScrollToTop() {
    if (Build.MANUFACTURER.lowercase() !in setOf("oppo", "oneplus", "realme")) return
    val view = LocalView.current
    val composeRoot = view as? RootForTest ?: return
    LifecycleResumeEffect(view) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == StatusBarClicked && view.hasWindowFocus() && view.windowVisibility == View.VISIBLE) {
                    composeRoot.scrollVisibleListsToTop()
                }
            }
        }
        ContextCompat.registerReceiver(view.context, receiver, IntentFilter(StatusBarClicked),
            StatusBarServicePermission, null, ContextCompat.RECEIVER_EXPORTED)
        onPauseOrDispose { view.context.unregisterReceiver(receiver) }
    }
}

private fun RootForTest.scrollVisibleListsToTop() {
    semanticsOwner.getAllSemanticsNodes(mergingEnabled = false).forEach { node ->
        val range = node.config.getOrNull(SemanticsProperties.VerticalScrollAxisRange) ?: return@forEach
        val offset = range.value()
        if (range.reverseScrolling || offset <= 0f || !offset.isFinite() || node.boundsInWindow.isEmpty ||
            SemanticsProperties.EditableText in node.config ||
            SemanticsProperties.HideFromAccessibility in node.config) return@forEach
        if (generateSequence(node.parent) { it.parent }.any {
            SemanticsProperties.VerticalScrollAxisRange in it.config || SemanticsProperties.HideFromAccessibility in it.config
        }) return@forEach
        val scrollToIndex = node.config.getOrNull(SemanticsActions.ScrollToIndex)?.action
        if (scrollToIndex != null) scrollToIndex(0)
        else node.config.getOrNull(SemanticsActions.ScrollBy)?.action?.invoke(0f, -offset)
    }
}
