package com.itos.coui.menu

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.itos.coui.effect.CouiStateProgress
import com.itos.coui.input.isCouiTouchCancelled
import com.itos.coui.motion.couiTouchMotion
import com.itos.coui.motion.rememberCouiTouchMotion
import com.itos.coui.shape.CouiShape
import com.itos.coui.theme.CouiIcon
import com.itos.coui.theme.CouiText
import kotlin.math.roundToInt

/** SleepDown 系统菜单装配；尺寸、定位与列表取自 Settings 17 poplist，不使用 Menu 的 Demo 布局。 */
data class SystemPopupMenuItem(
    val text: String,
    val iconRes: Int?,
    val enabled: Boolean = true,
    val textColor: Color = Color.Unspecified,
    val iconColor: Color = Color.Unspecified,
)

/** anchor/available 均为宿主窗口坐标；课程 shortcut 对齐逻辑起始边，工具栏菜单沿锚点居中避让。 */
internal fun systemPopupMenuBounds(anchor: Rect, available: Rect, size: IntSize,
    gap: Float, alignStart: Boolean, rtl: Boolean): Rect {
    val width = size.width.toFloat().coerceAtMost(available.width).coerceAtLeast(0f)
    val height = size.height.toFloat().coerceAtMost(available.height).coerceAtLeast(0f)
    val preferredX = if (alignStart) (if (rtl) anchor.right - width else anchor.left)
        else anchor.center.x - width / 2f
    val below = anchor.bottom + gap
    val above = anchor.top - gap - height
    val y = when {
        below + height <= available.bottom -> below
        above >= available.top -> above
        available.bottom - anchor.bottom >= anchor.top - available.top -> available.bottom - height
        else -> available.top
    }.coerceIn(available.top, (available.bottom - height).coerceAtLeast(available.top))
    val x = preferredX.coerceIn(available.left, (available.right - width).coerceAtLeast(available.left))
    return Rect(x, y, x + width, y + height)
}

@Composable
fun SystemPopupMenu(
    expanded: Boolean,
    anchorBounds: Rect,
    items: List<SystemPopupMenuItem>,
    dark: Boolean,
    onDismissRequest: () -> Unit,
    onItemClick: (Int) -> Unit,
    onDismissFinished: () -> Unit,
    alignStart: Boolean = false,
    selectionHaptics: Boolean = true,
    onPositioned: (Rect) -> Unit = {},
) {
    val motion = rememberCouiMenuMotion(expanded)
    val finished by rememberUpdatedState(onDismissFinished)
    var mounted by remember { mutableStateOf(expanded) }
    LaunchedEffect(expanded, motion.visible) {
        if (expanded) mounted = true
        else if (mounted && !motion.visible) { mounted = false; finished() }
    }
    if (!expanded && !motion.visible) return
    val shape = remember { CouiShape(28.dp, 3f) }
    val foreground = Color(if (dark) 0xE6FFFFFF else 0xE6000000)
    // 这里只借用材质/阴影协议，所有可见几何、字号和条目排版在本入口独立装配。
    val foundation = CouiMenuDefaults.style(dark).copy(shape = shape, itemShape = shape,
        themeTint = Color.Transparent, itemTextStyle = TextStyle(fontFamily = FontFamily.SansSerif,
            fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Normal))
    CompositionLocalProvider(LocalCouiMenuStyle provides foundation) {
        SystemPopupWindow(expanded, anchorBounds, items, dark, foreground, shape, motion,
            onDismissRequest, onItemClick, alignStart, selectionHaptics, onPositioned)
    }
}

@Composable
private fun SystemPopupWindow(expanded: Boolean, anchor: Rect, items: List<SystemPopupMenuItem>,
    dark: Boolean, foreground: Color, shape: CouiShape, motion: CouiMenuMotion,
    onDismiss: () -> Unit, onItemClick: (Int) -> Unit, alignStart: Boolean,
    selectionHaptics: Boolean, onPositioned: (Rect) -> Unit) {
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val host = LocalView.current
    val haptic = LocalHapticFeedback.current
    val focus = LocalFocusManager.current
    val touch = rememberCouiTouchMotion(expanded)
    val backdrop = LocalCouiMenuBackdrop.current
    val material = if (backdrop != null) couiMenuMaterial(backdrop, shape, expanded, motion, touch, false)
        else CouiMenuMaterial()
    val shadow = rememberCouiMenuShadow(shape, motion, touch)
    var viewport by remember { mutableStateOf(Rect.Zero) }
    var menuSize by remember { mutableStateOf(IntSize.Zero) }
    val margin = with(density) { 16.dp.toPx() }
    val bottomMargin = with(density) { 32.dp.toPx() }
    val available = Rect(viewport.left + margin, viewport.top + margin,
        (viewport.right - margin).coerceAtLeast(viewport.left + margin),
        (viewport.bottom - bottomMargin).coerceAtLeast(viewport.top + margin))
    val bounds = systemPopupMenuBounds(anchor, available, menuSize, with(density) { 8.dp.toPx() },
        alignStart, direction == LayoutDirection.Rtl)
    SideEffect {
        if (menuSize.width > 1 && menuSize.height > 1 && viewport != Rect.Zero) {
            val anchorY = if (bounds.center.y >= anchor.center.y) anchor.bottom else anchor.top
            motion.path = CouiMenuMotionPath.from(Offset(anchor.center.x, anchorY) - bounds.center)
            onPositioned(bounds)
        }
    }
    val position = remember { object : PopupPositionProvider {
        override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize,
            layoutDirection: LayoutDirection, popupContentSize: IntSize) = IntOffset.Zero
    } }
    val scroll = rememberScrollState()
    val scrollBar = remember(scroll) { CouiMenuScrollBar(scroll) }
    val currentClick by rememberUpdatedState(onItemClick)
    val rowBounds = remember { mutableStateMapOf<Int, Rect>() }
    var contentCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var pressed by remember { mutableStateOf<Int?>(null) }
    var indicator by remember { mutableStateOf<Rect?>(null) }
    val mask = remember { CouiStateProgress() }
    DisposableEffect(mask) { onDispose { mask.dispose() } }
    LaunchedEffect(pressed) { mask.setActive(pressed != null, minimumPress = true) }
    val indicatorTop by animateFloatAsState(indicator?.top ?: 0f,
        spring(dampingRatio = 1f, stiffness = 1754.5962f), label = "systemPopupMaskY")
    fun select(index: Int) {
        if (expanded && items[index].enabled) {
            if (selectionHaptics) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            currentClick(index)
        }
    }
    Popup(popupPositionProvider = position, onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true).withMenuInput(expanded)) {
        Box(Modifier.fillMaxSize().onGloballyPositioned { coordinates ->
            val screen = IntArray(2); val window = IntArray(2)
            host.getLocationOnScreen(screen); host.getLocationInWindow(window)
            val origin = coordinates.positionOnScreen() - Offset((screen[0] - window[0]).toFloat(),
                (screen[1] - window[1]).toFloat())
            viewport = Rect(origin, coordinates.size.toSize())
        }, contentAlignment = AbsoluteAlignment.TopLeft) {
            Spacer(Modifier.matchParentSize().pointerInput(expanded) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    if (expanded) onDismiss()
                }
            })
            Box(Modifier.absoluteOffset { IntOffset((bounds.left - viewport.left).roundToInt(),
                (bounds.top - viewport.top).roundToInt()) }.onSizeChanged { menuSize = it }.then(shadow.drawing)) {
                Box(Modifier.graphicsLayer {
                    val frame = motion.frame
                    scaleX = frame.scale * touch.scaleX.value; scaleY = frame.scale * touch.scaleY.value
                    translationX = frame.translation.x + touch.translationX.value
                    translationY = frame.translation.y + touch.translationY.value
                    alpha = frame.alpha; transformOrigin = TransformOrigin.Center
                    compositingStrategy = CompositingStrategy.ModulateAlpha
                }.then(shadow.surface).couiTouchMotion(touch, expanded)
                    .width(with(density) { minOf(196.dp, available.width.coerceAtLeast(1f).toDp()) })
                    .heightIn(max = with(density) { available.height.coerceAtLeast(1f).toDp() })
                    .clip(shape).background(if (backdrop == null) foundationColor(dark) else Color.Transparent)
                    .then(material.drawing).then(material.input).drawBehind {
                        val rect = indicator ?: return@drawBehind
                        val inset = 8.dp.toPx()
                        val path = androidx.compose.ui.graphics.Path().apply {
                            addOutline(shape.createOutline(androidx.compose.ui.geometry.Size(
                                (size.width - inset * 2).coerceAtLeast(0f), rect.height), layoutDirection, this@drawBehind))
                        }
                        withTransform({ translate(inset, indicatorTop - scroll.value) }) {
                            drawPath(path, Color(if (dark) 0x26FFFFFF else 0x14000000)
                                .copy(alpha = (if (dark) .15f else .08f) * mask.value.coerceIn(0f, 1f)))
                        }
                    }
                    .then(couiMenuScrollBar(scrollBar, expanded, true, dark, 8.dp))
                    .semantics { isTraversalGroup = true; paneTitle = "操作菜单" }
                    .onPreviewKeyEvent {
                        if (it.type != KeyEventType.KeyDown) false else when (it.key) {
                            Key.DirectionDown -> focus.moveFocus(FocusDirection.Down)
                            Key.DirectionUp -> focus.moveFocus(FocusDirection.Up)
                            else -> false
                        }
                    }) {
                    Column(Modifier.fillMaxWidth().verticalScroll(scroll)
                        .onGloballyPositioned { contentCoordinates = it }
                        .pointerInput(expanded, items) {
                            if (!expanded) return@pointerInput
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                fun hit(point: Offset) = rowBounds.entries.firstOrNull {
                                    it.value.contains(point) && items[it.key].enabled
                                }?.key
                                pressed = hit(down.position)
                                pressed?.let { indicator = rowBounds[it] }
                                var accepted: Int? = null
                                try {
                                    while (true) {
                                        val event = awaitPointerEvent(PointerEventPass.Final)
                                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                        if (event.isCouiTouchCancelled() || change.isConsumed) break
                                        val index = hit(change.position)
                                        if (index != null && index != pressed && change.pressed)
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        pressed = index
                                        index?.let { indicator = rowBounds[it] }
                                        if (!change.pressed) {
                                            if (change.previousPressed) { accepted = index; change.consume() }
                                            break
                                        }
                                    }
                                } finally { pressed = null }
                                accepted?.let(::select)
                            }
                        }.padding(vertical = 8.dp)) {
                        items.forEachIndexed { index, item ->
                            if (index > 0) Box(Modifier.fillMaxWidth().padding(start = 52.dp, end = 20.dp)
                                .height(.5.dp).background(Color(if (dark) 0x26FFFFFF else 0x1F000000)))
                            val interaction = remember { MutableInteractionSource() }
                            Row(Modifier.fillMaxWidth().heightIn(min = 40.dp)
                                .onGloballyPositioned { row ->
                                    contentCoordinates?.takeIf { it.isAttached }?.let { root ->
                                        val topLeft = root.localPositionOf(row, Offset.Zero)
                                        rowBounds[index] = Rect(topLeft, row.size.toSize())
                                    }
                                }.semantics(mergeDescendants = true) {
                                    role = Role.Button
                                    if (!item.enabled) disabled()
                                    onClick { if (expanded && item.enabled) { select(index); true } else false }
                                }.focusable(expanded && item.enabled, interaction)
                                .onKeyEvent {
                                    if (it.type == KeyEventType.KeyUp && (it.key == Key.Enter || it.key == Key.DirectionCenter)) {
                                        select(index); true
                                    } else false
                                }.padding(horizontal = 20.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                val text = item.textColor.takeOrElse { foreground }
                                    .let { if (item.enabled) it else it.copy(alpha = .3f) }
                                item.iconRes?.let { icon ->
                                    CouiIcon(Modifier.size(20.dp), painterResource(icon), null,
                                        tint = item.iconColor.takeOrElse { text })
                                    Spacer(Modifier.width(12.dp))
                                }
                                CouiText(Modifier.weight(1f), item.text, color = text,
                                    style = LocalCouiMenuStyle.current.itemTextStyle)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun foundationColor(dark: Boolean) = Color(if (dark) 0xFF262626 else 0xFFEEEEEE)
