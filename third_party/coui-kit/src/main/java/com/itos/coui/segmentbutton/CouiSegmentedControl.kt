package com.itos.coui.segmentbutton

import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.itos.coui.effect.CouiContour
import com.itos.coui.effect.CouiContourStyle
import com.itos.coui.effect.LocalCouiContourGlowEnabled
import com.itos.coui.effect.SpotlightPreset
import com.itos.coui.effect.rememberCouiSpotlight
import com.itos.coui.motion.*
import com.itos.coui.shape.CouiCapsuleShape
import com.itos.coui.shape.CouiShape
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text
import com.itos.coui.text.rememberCouiTextStyle
import kotlin.math.*

/** COUISegmentButtonLayout 的宽度分配；短标签先保留自然宽度，余宽按原厂规则分配。 */
internal fun segmentWidths(natural: List<Float>, available: Float): List<Float> {
    if (natural.isEmpty()) return emptyList()
    val order = natural.indices.sortedBy { natural[it] }
    val result = FloatArray(natural.size)
    val mean = available / natural.size
    var remaining = available
    var index = 0
    var limit = mean
    var last = -1
    while (index < order.size) {
        val item = order[index]
        if (natural[item] < limit) {
            result[item] = natural[item]; remaining -= natural[item]; index++
        } else if (last == index) {
            while (index < order.size) result[order[index++]] = limit
            remaining = 0f
            break
        } else { limit = remaining / (order.size - index); last = index }
    }
    if (last == -1) return natural.map { mean.roundToInt().toFloat() }
    if (remaining > 0f && order.size > 1)
        order.dropLast(1).forEach { result[it] += remaining / (order.size - 1) }
    return result.map { it.roundToInt().toFloat().coerceAtLeast(0f) }
}

internal fun segmentDynamic(from: Float, to: Float, normal: Float, minimum: Float, exponent: Float): Float =
    if (from <= 0f || to <= 0f) normal else
        (normal - (normal - minimum) * (abs(from - to) / max(from, to)).pow(exponent)).coerceIn(0f, 1f)

internal fun segmentRubber(delta: Float, distance: Float): Float =
    if (distance == 0f) 0f else (1f - 1f / (abs(delta) * .12f / distance + 1f)) * distance * sign(delta)

internal fun segmentRubberDistance(width: Float, scale: Float, edge: Boolean, switchDistance: Float): Float =
    ((1f - scale) * width / 2f).coerceAtLeast(0f) + if (edge) 0f else switchDistance

/** 原生选择的提交门；即时选择和抬手提交共用预览，但系统 CANCEL 不产生新提交。 */
internal class SegmentSelectionSession {
    var touching = false; private set
    var preview = 0; private set
    private var emitted = 0
    fun begin(selected: Int) { touching = true; preview = selected; emitted = selected }
    fun move(index: Int, onRelease: Boolean, submit: (Int) -> Unit) {
        preview = index
        if (!onRelease && index != emitted) { emitted = index; submit(index) }
    }
    fun finish(cancelled: Boolean, selected: Int, onRelease: Boolean, submit: (Int) -> Unit) {
        if (!touching) return
        touching = false
        if (!cancelled && onRelease && preview != selected) submit(preview)
        if (cancelled) preview = selected
    }
}

/**
 * 受控胶囊分段选择器。surfaceModifier 仅提供所属背景域的材质，不创建采样源。
 * 独立位置/宽度/高度/橡皮筋弹簧对应 COUISegmentButtonLayout；宿主决定何时保存。
 */
@Composable
fun CouiSegmentedControl(
    modifier: Modifier = Modifier,
    labels: List<String>, selectedIndex: Int, onSelected: (Int) -> Unit,
    dark: Boolean, enabled: Boolean = true,
    height: Dp = 42.dp, containerSurfaceEnabled: Boolean = true,
    commitOnRelease: Boolean = false, surfaceModifier: Modifier = Modifier,
) {
    require(labels.isNotEmpty() && selectedIndex in labels.indices)
    val density = LocalDensity.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val haptic = LocalHapticFeedback.current
    val current = rememberUpdatedState(selectedIndex)
    val submit = rememberUpdatedState(onSelected)
    val session = remember(labels) { SegmentSelectionSession() }
    var preview by remember(labels) { mutableIntStateOf(selectedIndex) }
    val position = remember { CouiSpring(0f, .5f) }
    val width = remember { CouiSpring(0f, .5f) }
    val indicatorHeight = remember { CouiSpring(0f, .5f) }
    val rubber = remember { CouiSpring(0f, .5f) }
    var widthLimit by remember { mutableFloatStateOf(Float.MAX_VALUE) }
    val touch = rememberCouiTouchMotion(enabled && containerSurfaceEnabled)
    val press = rememberCouiPressMotion()
    val indicatorPress = rememberCouiPressMotion()
    val labelPresses = labels.mapIndexed { index, _ -> key(index) { rememberCouiPressMotion() } }
    val (lightInput, light) = rememberCouiSpotlight(interactive = enabled && containerSurfaceEnabled,
        dark = dark, preset = SpotlightPreset.OpaqueMedium2, cancelOnConsumedMove = true)
    val textStyle = rememberCouiTextStyle(CouiTextKind.Button)
    val measurer = rememberTextMeasurer()
    SideEffect { if (!session.touching) preview = selectedIndex }
    DisposableEffect(session) { onDispose {
        session.finish(true, current.value, commitOnRelease) { }
        listOf(position, width, indicatorHeight, rubber).forEach { it.cancelComplete() }
    } }
    BoxWithConstraints(modifier.height(height).selectableGroup().graphicsLayer {
        if (containerSurfaceEnabled) {
            scaleX = touch.scaleX.value + press.scale.value - 1f
            scaleY = touch.scaleY.value + press.scale.value - 1f
            translationX = touch.translationX.value; translationY = touch.translationY.value
        }
    }.then(if (containerSurfaceEnabled) Modifier.couiTouchMotion(touch, enabled, cancelOnConsumedMove = true)
        .couiPressMotion(press, enabled, cancelOnConsumedMove = true).then(lightInput).then(surfaceModifier)
        .drawBehind { light?.invoke(this) } else Modifier)) {
        val padding = with(density) { 4.dp.toPx() }
        val available = (constraints.maxWidth.toFloat() - 2 * padding).coerceAtLeast(0f)
        val natural = remember(labels, textStyle, density) { labels.map {
            max(with(density) { 52.dp.toPx() }, measurer.measure(it, textStyle).size.width + with(density) { 24.dp.toPx() })
        } }
        val widths = remember(natural, available) { segmentWidths(natural, available) }
        val starts = widths.indices.map { i -> padding + widths.take(i).sum() }
        fun center(i: Int): Float {
            val logical = starts[i] + widths[i] / 2f
            return if (rtl) constraints.maxWidth - logical else logical
        }
        val targetHeight = with(density) { height.toPx() } - padding * 2
        LaunchedEffect(preview, widths, targetHeight, rtl) {
            val index = preview.coerceIn(labels.indices)
            val targetWidth = widths[index]
            if (width.value <= 0f) {
                position.snapTo(center(index)); width.snapTo(targetWidth); indicatorHeight.snapTo(targetHeight)
            } else {
                widthLimit = if (targetWidth > width.value) targetWidth + with(density) { 7.dp.toPx() } else Float.MAX_VALUE
                rubber.snapTo(0f)
                position.animateTo(center(index), .4f, .1f)
                width.animateTo(targetWidth, segmentDynamic(width.value, targetWidth, .6f, .4f, 2f),
                    if (targetWidth > width.value) .5f else segmentDynamic(width.value, targetWidth, .5f, .1f, 2f), velocity = 1000f)
                indicatorHeight.animateTo(targetHeight, .6f, .5f, velocity = -500f)
            }
        }
        LaunchedEffect(enabled) { if (!enabled) {
            session.finish(true, current.value, commitOnRelease) { }
            preview = current.value; rubber.animateTo(0f, .6f)
        } }
        fun hit(x: Float): Int? {
            val point = if (rtl) constraints.maxWidth - x else x
            return labels.indices.firstOrNull { point >= starts[it] && point <= starts[it] + widths[it] }
        }
        fun select(index: Int) {
            if (preview != index) preview = index
            session.move(index, commitOnRelease) { submit.value(it) }
        }
        val first = center(0); val last = center(labels.lastIndex)
        // 原生仅 invalidate 指示器；弹簧每帧只触发布局/绘制，不重组标签和重新测字。
        Box(Modifier.align(AbsoluteAlignment.TopLeft).layout { measurable, _ ->
            val scale = indicatorPress.scale.value
            val iw = width.value.coerceIn(0f, widthLimit) * scale
            val ih = indicatorHeight.value.coerceAtLeast(targetHeight * .9f) * scale
            val cx = position.value.coerceIn(min(first, last), max(first, last)) + rubber.value
            val left = (cx - iw / 2f).coerceAtLeast(padding)
            val right = (cx + iw / 2f).coerceAtMost(available + padding)
            val child = measurable.measure(Constraints.fixed((right - left).roundToInt().coerceAtLeast(0), ih.roundToInt().coerceAtLeast(0)))
            layout(child.width, child.height) {
                child.place(left.roundToInt(), ((with(density) { height.toPx() } - ih) / 2).roundToInt())
            }
        }
            .segmentIndicator(dark))
        Row(Modifier.fillMaxSize().padding(horizontal = 4.dp)
            .pointerInput(labels, widths, enabled, commitOnRelease) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    session.begin(current.value)
                    var dragging = hit(down.position.x + padding) == current.value
                    if (dragging) indicatorPress.press(IntSize(width.value.roundToInt(), targetHeight.roundToInt()))
                    var pressedLabel = hit(down.position.x + padding)
                    pressedLabel?.let { labelPresses[it].press(IntSize(widths[it].roundToInt(), targetHeight.roundToInt())) }
                    var rubberOrigin = down.position.x
                    var resetRubberOnStable = false
                    var cancelled = false
                    var completed = false
                    try {
                        do {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            val delta = change.position - down.position
                            if (change.isConsumed && delta.getDistance() > 0f) { cancelled = true; break }
                            if (abs(delta.y) > viewConfiguration.touchSlop && abs(delta.y) > abs(delta.x)) {
                                cancelled = true; break
                            }
                            if (dragging && change.pressed) {
                                val target = hit(change.position.x + padding)
                                if (target != null && target != preview) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    select(target)
                                    resetRubberOnStable = true
                                } else if (!position.isRunning && abs(position.value - center(preview)) < .5f) {
                                    if (resetRubberOnStable) { rubberOrigin = change.position.x; resetRubberOnStable = false }
                                    val distance = segmentRubberDistance(width.value, indicatorPress.scale.value,
                                        preview == 0 || preview == labels.lastIndex, with(density) { 5.dp.toPx() })
                                    rubber.animateTo(segmentRubber(change.position.x - rubberOrigin, distance), .6f)
                                }
                                if (abs(delta.x) > viewConfiguration.touchSlop) change.consume()
                            }
                            val touchedLabel = hit(change.position.x + padding)
                            if (touchedLabel != pressedLabel) {
                                pressedLabel?.let { labelPresses[it].release() }
                                touchedLabel?.let { labelPresses[it].press(IntSize(widths[it].roundToInt(), targetHeight.roundToInt())) }
                                pressedLabel = touchedLabel
                            }
                            if (!change.pressed) {
                                if (!change.previousPressed || (change.isConsumed &&
                                    change.uptimeMillis == change.previousUptimeMillis && change.position == change.previousPosition)) cancelled = true
                                if (!cancelled) hit(change.position.x + padding)?.let(::select)
                                completed = !cancelled
                                break
                            }
                        } while (true)
                    } finally {
                        session.finish(cancelled || !completed, current.value, commitOnRelease) { submit.value(it) }
                        preview = if (cancelled || !completed) current.value else session.preview
                        rubber.animateTo(0f, .6f)
                        indicatorPress.release()
                        pressedLabel?.let { labelPresses[it].release() }
                    }
                }
            }) {
            labels.forEachIndexed { index, label ->
                Box(Modifier.width(with(density) { widths[index].toDp() }).fillMaxHeight()
                    .graphicsLayer { scaleX = labelPresses[index].scale.value; scaleY = labelPresses[index].scale.value }
                    .semantics { role = Role.Tab; selected = selectedIndex == index
                        if (!enabled) disabled()
                        onClick { if (enabled && index != current.value) submit.value(index); enabled }
                    }.focusable(enabled).onKeyEvent { key ->
                        if (enabled && key.type == KeyEventType.KeyUp && key.key in listOf(Key.Enter, Key.Spacebar)) {
                            if (index != current.value) submit.value(index); true
                        } else false
                    }, contentAlignment = Alignment.Center) {
                    Text(text = label, kind = CouiTextKind.Button, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = Color(if (dark) 0xE6FFFFFF else 0xE6000000).copy(alpha = if (enabled) .9f else .38f))
                }
            }
        }
    }
}

/** 原厂指示器使用 CAPSULE_10 / EDGE_STYLE_0，复用公共轮廓后端；仅渐变投影属于该控件绘制。 */
@Composable
private fun Modifier.segmentIndicator(dark: Boolean): Modifier {
    val glow = LocalCouiContourGlowEnabled.current
    val contour = remember(glow) { if (glow && android.os.Build.VERSION.SDK_INT >= 33)
        CouiContour(style = CouiContourStyle.Button) else null }
    val paint = remember { android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG) }
    val shape = remember { CouiShape(999.dp, 2f) }
    return drawWithCache {
        val outline = shape.createOutline(size, layoutDirection, this)
        val path = if (outline is Outline.Generic) outline.path else Path().apply { addOutline(outline) }
        val shadow = Brush.radialGradient(listOf(Color(0x0D000000), Color(0x03000000)),
            center = Offset(size.width / 2, size.height / 2), radius = (size.width / 2).coerceAtLeast(1f))
        onDrawBehind {
            clipPath(path, ClipOp.Difference) {
                val top = 2.dp.toPx()
                val shadowSize = androidx.compose.ui.geometry.Size(size.width, size.height + 4.dp.toPx() - top)
                if (dark) drawRoundRect(Color(0x14000000), Offset(0f, top), shadowSize,
                    androidx.compose.ui.geometry.CornerRadius(size.height / 2))
                else drawRoundRect(shadow, Offset(0f, top), shadowSize,
                    androidx.compose.ui.geometry.CornerRadius(size.height / 2))
            }
        }
    }.clip(shape).drawBehind {
        drawRect(Color(if (dark) 0x33FFFFFF else 0xB2FFFFFF))
        contour?.let { edge ->
            paint.shader = edge.shader(size, density, size.height / 2, 2f, Color.Transparent,
                0f, if (dark) .15f else .8f, 1f, 1.8f * density)
            drawIntoCanvas { it.nativeCanvas.drawRect(0f, 0f, size.width, size.height, paint) }
        }
    }
}
