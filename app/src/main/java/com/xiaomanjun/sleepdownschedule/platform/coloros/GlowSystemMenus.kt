package com.xiaomanjun.sleepdownschedule.platform.coloros

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.itos.coui.menu.SystemPopupMenu
import com.itos.coui.menu.SystemPopupMenuItem
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.AddMenuAction
import kotlin.math.roundToInt

private fun List<AddMenuAction>.systemItems() = map {
    SystemPopupMenuItem(it.label, it.iconRes,
        textColor = it.textTint ?: Color.Unspecified, iconColor = it.iconTint ?: Color.Unspecified)
}

/** 独立系统菜单先完成退场，再交给原业务入口；不把液滴 Morph 套在凝光菜单上。 */
@Composable
internal fun GlowOverflowMenuButton(dark: Boolean, interactive: Boolean, actions: List<AddMenuAction>,
    modifier: Modifier, content: @Composable (onClick: () -> Unit) -> Unit) {
    var anchor by remember { mutableStateOf<Rect?>(null) }
    var mounted by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    var shownActions by remember { mutableStateOf(emptyList<AddMenuAction>()) }
    val latestActions by rememberUpdatedState(actions)
    val items = remember(shownActions) { shownActions.systemItems() }
    val onBounds = com.xiaomanjun.sleepdownschedule.core.ui.material.LocalHomeOverflowMenuBounds.current
    Box(modifier.onGloballyPositioned { anchor = it.boundsInWindow() }) {
        content {
            if (interactive && latestActions.isNotEmpty() && pending == null) {
                if (!mounted) shownActions = latestActions.toList()
                mounted = true
                expanded = true
            }
        }
        if (mounted) anchor?.let { bounds ->
            SystemPopupMenu(expanded, bounds, items, dark,
                onDismissRequest = { expanded = false },
                onItemClick = { index ->
                    if (expanded && pending == null) {
                        pending = shownActions.getOrNull(index)?.onClick
                        expanded = false
                    }
                },
                onDismissFinished = {
                    val action = pending
                    pending = null
                    mounted = false
                    action?.invoke()
                }, onPositioned = { onBounds?.invoke(it) })
        }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) { pending = null; expanded = false; mounted = false }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); pending = null }
    }
}

@Composable
internal fun GlowCourseShortcutMenu(session: Any, bounds: Rect, dark: Boolean,
    actions: List<AddMenuAction>, closing: Boolean, onDismiss: () -> Unit,
    bindDismiss: (((() -> Unit) -> Unit)?) -> Unit) {
    val density = LocalDensity.current
    var anchor by remember(session) { mutableStateOf<Rect?>(null) }
    var expanded by remember(session) { mutableStateOf(true) }
    var finished by remember(session) { mutableStateOf(false) }
    var waiter by remember(session) { mutableStateOf<(() -> Unit)?>(null) }
    var pendingAction by remember(session) { mutableStateOf<(() -> Unit)?>(null) }
    val latestBind by rememberUpdatedState(bindDismiss)
    val latestActions by rememberUpdatedState(actions)
    val items = remember(actions) { actions.systemItems() }
    // 真实布局把课程的 root 坐标换成窗口坐标，适用于横屏、分屏和系统安全区。
    Box(Modifier.absoluteOffset { IntOffset(bounds.left.roundToInt(), bounds.top.roundToInt()) }
        .size(with(density) { bounds.width.toDp() }, with(density) { bounds.height.toDp() })
        .onGloballyPositioned { anchor = it.boundsInWindow() })
    anchor?.let {
        SystemPopupMenu(expanded && !closing, it, items, dark, onDismiss,
            onItemClick = { index ->
                if (!closing && expanded && pendingAction == null) {
                    pendingAction = latestActions.getOrNull(index)?.onClick
                    expanded = false
                }
            },
            onDismissFinished = {
                finished = true
                val action = pendingAction
                pendingAction = null
                val done = waiter
                waiter = null
                done?.invoke()
                // COUIPopupListWindow 在菜单真正退出后才交接触发的动作。
                action?.invoke()
            }, alignStart = true, selectionHaptics = false)
    }
    DisposableEffect(session) {
        latestBind { after ->
            if (finished) after() else { waiter = after; expanded = false }
        }
        onDispose {
            latestBind(null)
            pendingAction = null
            val done = waiter
            waiter = null
            done?.invoke()
        }
    }
}
