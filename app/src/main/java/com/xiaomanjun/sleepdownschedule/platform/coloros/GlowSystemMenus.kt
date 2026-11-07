package com.xiaomanjun.sleepdownschedule.platform.coloros

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.itos.coui.menu.Menu
import com.itos.coui.menu.CouiMenuItem
import com.itos.coui.menu.CouiMenuDefaults
import com.itos.coui.shape.CouiCapsuleShape
import com.xiaomanjun.sleepdownschedule.core.ui.chrome.homeActionIconPainter
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.AddMenuAction
import kotlin.math.roundToInt

@Composable
private fun List<AddMenuAction>.couiItems() = mapIndexed { index, action ->
    CouiMenuItem(index, action.label, icon = action.imageVector,
        iconPainter = if (action.imageVector == null) action.iconRes?.let { homeActionIconPainter(it) } else null,
        iconTint = action.iconTint, textColor = action.textTint)
}

@Composable
private fun actionMenuStyle(dark: Boolean) = CouiMenuDefaults.style(dark).copy(
    // 动作菜单只显示当前按压的共享遮罩，不保留设置菜单的持久选中项。
    itemShape = CouiCapsuleShape,
    pressMaskOnForeground = true,
)

/** 独立系统菜单先完成退场，再交给原业务入口；不把液滴 Morph 套在凝光菜单上。 */
@Composable
internal fun GlowOverflowMenuButton(dark: Boolean, interactive: Boolean, actions: List<AddMenuAction>,
    modifier: Modifier, content: @Composable (onClick: () -> Unit) -> Unit) {
    var mounted by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    var shownActions by remember { mutableStateOf(emptyList<AddMenuAction>()) }
    val latestActions by rememberUpdatedState(actions)
    val items = shownActions.couiItems()
    val onBounds = com.xiaomanjun.sleepdownschedule.core.ui.material.LocalHomeOverflowMenuBounds.current
    Box(modifier) {
        content {
            if (interactive && latestActions.isNotEmpty() && pending == null) {
                if (!mounted) shownActions = latestActions.toList()
                mounted = true
                expanded = true
            }
        }
        if (mounted) {
            Menu(modifier = Modifier.width(196.dp), expanded = expanded, items = items,
                style = actionMenuStyle(dark), offset = DpOffset(0.dp, 8.dp),
                onDismissRequest = { expanded = false },
                onItemClick = { item ->
                    if (expanded && pending == null) {
                        pending = shownActions.getOrNull(item.value)?.onClick
                        expanded = false
                    }
                },
                onDismissFinished = {
                    val action = pending
                    pending = null
                    mounted = false
                    action?.invoke()
                }, onMenuPositioned = { onBounds?.invoke(it) })
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
    var expanded by remember(session) { mutableStateOf(true) }
    var finished by remember(session) { mutableStateOf(false) }
    var waiter by remember(session) { mutableStateOf<(() -> Unit)?>(null) }
    var pendingAction by remember(session) { mutableStateOf<(() -> Unit)?>(null) }
    val latestBind by rememberUpdatedState(bindDismiss)
    val latestActions by rememberUpdatedState(actions)
    val items = actions.couiItems()
    // 真实布局把课程的 root 坐标换成窗口坐标，适用于横屏、分屏和系统安全区。
    Box(Modifier.absoluteOffset { IntOffset(bounds.left.roundToInt(), bounds.top.roundToInt()) }
        .size(with(density) { bounds.width.toDp() }, with(density) { bounds.height.toDp() })) {
        Menu(modifier = Modifier.width(196.dp), expanded = expanded && !closing, items = items,
            style = actionMenuStyle(dark), offset = DpOffset(0.dp, 8.dp),
            onDismissRequest = onDismiss,
            onItemClick = { item ->
                if (!closing && expanded && pendingAction == null) {
                    pendingAction = latestActions.getOrNull(item.value)?.onClick
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
            })
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
