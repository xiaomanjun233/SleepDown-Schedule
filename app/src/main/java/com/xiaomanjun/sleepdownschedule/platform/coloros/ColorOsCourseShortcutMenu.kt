package com.xiaomanjun.sleepdownschedule.platform.coloros

import android.view.View
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.viewinterop.AndroidView
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.AddMenuAction
import com.xiaomanjun.sleepdownschedule.core.ui.material.LocalHomeMaterialStyle
import kotlin.math.roundToInt

/** Reuse the home overflow window; this transparent View only supplies the course anchor. */
@Composable
internal fun ColorOsCourseShortcutMenu(
    session: Any, bounds: Rect, dark: Boolean, actions: List<AddMenuAction>, closing: Boolean,
    onDismiss: () -> Unit,
    bindDismiss: (((() -> Unit) -> Unit)?) -> Unit
): Boolean {
    val native = rememberNativeContext(dark) ?: return false
    val host = LocalContext.current
    val density = LocalDensity.current
    val style = LocalHomeMaterialStyle.current
    val latestDismiss = rememberUpdatedState(onDismiss)
    val latestActions = rememberUpdatedState(actions)
    val latestClosing = rememberUpdatedState(closing)
    val menuContext = remember(native, host, style?.dark, dark) {
        native.first.themedContext(host, style?.dark ?: dark)
    }
    val menu = remember(native, session, style, menuContext) {
        ColorOsNativeUi.prepare("create native course shortcut") {
            NativeOverflowMenu(native.first, menuContext, host,
                neutral = style?.weatherMaterial == true, foreground = style?.foreground,
                onDismiss = { latestDismiss.value() }, alignToAnchorEdge = true)
        }
    } ?: return false
    var failed by remember(menu) { mutableStateOf(false) }
    if (failed) return false
    // PopupMenuLocateHelper constructs ResponsiveUIModel from anchor.context. That context
    // must resolve the same vendor grid resources as the menu, not SleepDown's resource IDs.
    val anchor = remember(menu) { View(menuContext).apply {
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        isClickable = false
        isFocusable = false
    } }
    val shown = remember(menu) { booleanArrayOf(false) }
    val active = remember(menu) { booleanArrayOf(true) }
    AndroidView(factory = { anchor }, modifier = Modifier
        .offset { IntOffset(bounds.left.roundToInt(), bounds.top.roundToInt()) }
        .size(with(density) { bounds.width.toDp() }, with(density) { bounds.height.toDp() })
        .onGloballyPositioned {
            if (!shown[0] && anchor.isAttachedToWindow && anchor.width > 0 && anchor.height > 0) {
                shown[0] = true
                anchor.post {
                    if (active[0] && !latestClosing.value && anchor.isAttachedToWindow) {
                        if (ColorOsNativeUi.prepare("show native course shortcut") {
                                menu.show(anchor, latestActions.value)
                                true
                            } != true) failed = true
                    }
                }
            }
        })
    DisposableEffect(menu) {
        bindDismiss(menu::dismiss)
        onDispose {
            active[0] = false
            bindDismiss(null)
            ColorOsNativeUi.prepare("dispose native course shortcut") { menu.dispose() }
        }
    }
    return true
}
