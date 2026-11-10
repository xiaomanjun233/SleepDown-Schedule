package com.xiaomanjun.sleepdownschedule.platform.coloros

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.node.RootForTest
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.xiaomanjun.sleepdownschedule.core.ui.interaction.AnimateScrollToTopAction
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

private const val StatusBarClicked = "com.oplus.clicktop"
private const val StatusBarServicePermission = "android.permission.STATUS_BAR_SERVICE"

/** ColorOS scans native scrollable Views. Compose lists expose their scroll actions as semantics. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun ColorOsStatusBarScrollToTop() {
    if (Build.MANUFACTURER.lowercase() !in setOf("oppo", "oneplus", "realme")) return
    val view = LocalView.current
    val composeRoot = view as? RootForTest ?: return
    val scope = rememberCoroutineScope()
    LifecycleResumeEffect(view) {
        var returnToTop: Job? = null
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == StatusBarClicked && view.hasWindowFocus() && view.windowVisibility == View.VISIBLE) {
                    returnToTop?.cancel()
                    returnToTop = scope.launch { composeRoot.animateVisibleListsToTop() }
                }
            }
        }
        ContextCompat.registerReceiver(view.context, receiver, IntentFilter(StatusBarClicked),
            StatusBarServicePermission, null, ContextCompat.RECEIVER_EXPORTED)
        onPauseOrDispose {
            returnToTop?.cancel()
            view.context.unregisterReceiver(receiver)
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
private suspend fun RootForTest.animateVisibleListsToTop() = coroutineScope {
    semanticsOwner.getAllSemanticsNodes(mergingEnabled = false).forEach { node ->
        val range = node.config.getOrNull(SemanticsProperties.VerticalScrollAxisRange) ?: return@forEach
        if (range.reverseScrolling || node.boundsInWindow.isEmpty ||
            SemanticsProperties.EditableText in node.config ||
            SemanticsProperties.HideFromAccessibility in node.config) return@forEach
        if (generateSequence(node.parent) { it.parent }.any {
            SemanticsProperties.VerticalScrollAxisRange in it.config || SemanticsProperties.HideFromAccessibility in it.config
        }) return@forEach
        val animate = node.config.getOrNull(AnimateScrollToTopAction)
        if (animate != null) {
            launch { animate() }
        } else if (SemanticsActions.ScrollToIndex !in node.config) {
            // Non-lazy scroll ranges are measured in pixels.
            val offset = range.value()
            if (offset <= 0f || !offset.isFinite()) return@forEach
            val scrollBy = node.config.getOrNull(SemanticsActions.ScrollByOffset)
            if (scrollBy != null) launch { scrollBy(Offset(0f, -offset)) }
            else node.config.getOrNull(SemanticsActions.ScrollBy)?.action?.invoke(0f, -offset)
        }
    }
}
