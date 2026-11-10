package com.xiaomanjun.sleepdownschedule.platform.coloros

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.itos.coui.menu.Menu
import com.itos.coui.menu.CouiMenuItem
import com.itos.coui.menu.CouiMenuDefaults
import com.itos.coui.menu.CouiMenuPlacement
import com.itos.coui.menu.CouiMenuContourStyle
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
private fun actionMenuStyle(dark: Boolean, placement: CouiMenuPlacement) = CouiMenuDefaults.style(dark).copy(
    // 动作菜单只显示当前按压的共享遮罩，不保留设置菜单的持久选中项。
    itemShape = CouiCapsuleShape,
    pressMaskOnForeground = true,
    // DefaultAdapter 普通动作取 couiTextBodyL；不继承 Kit 设置项的 14sp medium。
    itemTextStyle = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 15.sp,
        fontWeight = FontWeight.Normal, letterSpacing = 0.sp,
        platformStyle = PlatformTextStyle(includeFontPadding = true)),
    themeTint = Color.Transparent,
    contourStyle = CouiMenuContourStyle.White,
    // OVERLAY on a dark material has almost no headroom at the stock .1 setting.
    spotlightIntensity = if (dark) 0.45f else 0.85f,
    spotlightBlendMode = if (dark) android.graphics.BlendMode.PLUS else android.graphics.BlendMode.OVERLAY,
    placement = placement,
)

/** 首页动作可以即时提交，需要切页的宿主可以等待窗口退场。 */
@Composable
internal fun GlowOverflowMenuButton(dark: Boolean, interactive: Boolean, actions: List<AddMenuAction>,
    modifier: Modifier, deferSelectionUntilClosed: Boolean = false,
    content: @Composable (onClick: () -> Unit) -> Unit) {
    var mounted by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    var submitted by remember { mutableStateOf<AddMenuAction?>(null) }
    var shownActions by remember { mutableStateOf(emptyList<AddMenuAction>()) }
    val latestActions by rememberUpdatedState(actions)
    val items = shownActions.couiItems()
    val onBounds = com.xiaomanjun.sleepdownschedule.core.ui.material.LocalHomeOverflowMenuBounds.current
    Box(modifier) {
        content {
            if (interactive && latestActions.isNotEmpty() && submitted == null) {
                if (!mounted) shownActions = latestActions.toList()
                mounted = true
                expanded = true
            }
        }
        if (mounted) {
            Menu(modifier = Modifier.width(196.dp), expanded = expanded, items = items,
                style = actionMenuStyle(dark, CouiMenuPlacement.AnchorCenter), offset = DpOffset(0.dp, 8.dp),
                onDismissRequest = { expanded = false },
                onItemClick = { item ->
                    val action = shownActions.getOrNull(item.value)
                    if (expanded && submitted == null && action != null) {
                        submitted = action
                        expanded = false
                        if (!deferSelectionUntilClosed) action.onClick()
                    }
                },
                onDismissFinished = {
                    val action = submitted
                    submitted = null
                    mounted = false
                    if (deferSelectionUntilClosed) action?.onClick?.invoke()
                }, onMenuPositioned = { onBounds?.invoke(it) })
        }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) { submitted = null; expanded = false; mounted = false }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
}

@Composable
internal fun GlowCourseShortcutMenu(session: Any, bounds: Rect, dark: Boolean,
    actions: List<AddMenuAction>, closing: Boolean, onDismiss: () -> Unit,
    bindDismiss: (((() -> Unit) -> Unit)?) -> Unit, anchorFraction: Float) {
    val density = LocalDensity.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val placement = when {
        (anchorFraction > 0.5f) != rtl -> CouiMenuPlacement.AnchorEnd
        else -> CouiMenuPlacement.AnchorStart
    }
    var expanded by remember(session) { mutableStateOf(true) }
    var finished by remember(session) { mutableStateOf(false) }
    var waiter by remember(session) { mutableStateOf<(() -> Unit)?>(null) }
    var submitted by remember(session) { mutableStateOf(false) }
    val latestBind by rememberUpdatedState(bindDismiss)
    val latestActions by rememberUpdatedState(actions)
    val items = actions.couiItems()
    // 真实布局把课程的 root 坐标换成窗口坐标，适用于横屏、分屏和系统安全区。
    Box(Modifier.absoluteOffset { IntOffset(bounds.left.roundToInt(), bounds.top.roundToInt()) }
        .size(with(density) { bounds.width.toDp() }, with(density) { bounds.height.toDp() })) {
        Menu(modifier = Modifier.width(196.dp), expanded = expanded && !closing, items = items,
            style = actionMenuStyle(dark, placement), offset = DpOffset(0.dp, 8.dp),
            onDismissRequest = onDismiss,
            onItemClick = { item ->
                if (!closing && expanded && !submitted) {
                    submitted = true
                    expanded = false
                    latestActions.getOrNull(item.value)?.onClick?.invoke()
                }
            },
            onDismissFinished = {
                finished = true
                val done = waiter
                waiter = null
                done?.invoke()
            })
    }
    DisposableEffect(session) {
        latestBind { after ->
            if (finished) after() else { waiter = after; expanded = false }
        }
        onDispose {
            latestBind(null)
            val done = waiter
            waiter = null
            done?.invoke()
        }
    }
}
