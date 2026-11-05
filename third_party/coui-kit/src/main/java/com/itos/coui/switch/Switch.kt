package com.itos.coui.switch

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import android.view.MotionEvent
import android.view.VelocityTracker
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import com.itos.coui.effect.couiSwitchFeedback
import com.itos.coui.effect.couiSwitchFocusOutline
import com.itos.coui.shape.CouiCapsuleShape
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import com.itos.coui.input.isCouiTouchCancelled
import com.itos.coui.input.CouiHapticFeedback
import com.itos.coui.input.performCouiHapticFeedback
import com.itos.coui.theme.CouiTheme

@Immutable
data class CouiSwitchColors(val checkedTrack: Color, val uncheckedTrack: Color, val thumb: Color,
    val disabledCheckedTrack: Color, val disabledUncheckedTrack: Color, val disabledThumb: Color,
    val focusOutline: Color = checkedTrack.copy(alpha = .4f))

object CouiSwitchDefaults {
    val width = 44.dp
    val height = 24.dp
    val thumbSize = 18.dp
    val thumbPadding = 3.dp
    val shape = CouiCapsuleShape
    internal const val transformVelocityPx = 1000f
    internal const val transformDistancePx = 4f
    // SpringData 的构造顺序是 bounce、response、velocity；COUISwitch 读取 getter 后交给 Force。
    internal const val toggleResponse = .4f
    internal const val toggleBounce = .3f
    internal const val dragResponse = .3f
    internal const val dragBounce = 0f
    internal const val attachResponse = .4f
    internal const val attachBounce = .2f
    fun colors(accent: Color, dark: Boolean) = CouiSwitchColors(accent,
        Color(if (dark) 0x40FFFFFF else 0x29000000), Color.White,
        Color(accent.toArgb() and 0x4DFFFFFF), Color(if (dark) 0x26FFFFFF else 0x14000000),
        Color(if (dark) 0x29FFFFFF else 0x8AFFFFFF), Color(if (dark) 0x665C9DFF else 0x660080FF))
}

/**
 * Settings 17 普通开关；宿主持有 checked。onCheckedChange 为 null 时由整行拥有点击与语义。
 * 整行可单独传入 onDragCheckedChange，将滑块区域的拖动提交给同一个业务 owner。
 * 正常点击、拖动吸附只提交一次；取消/父级接管/禁用回到宿主状态。当前原厂分支关闭跟手光。
 */
@Composable
fun Switch(
    modifier: Modifier = Modifier,
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    dark: Boolean = CouiTheme.isDark,
    colors: CouiSwitchColors = CouiSwitchDefaults.colors(CouiTheme.colorSchemeFor(dark).accent, dark),
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
    onDragCheckedChange: ((Boolean) -> Unit)? = onCheckedChange,
    loading: Boolean = false,
    loadingDescription: String? = null,
    hapticFeedbackEnabled: Boolean = true,
) {
    val view = LocalView.current
    val interactive = enabled && !loading
    val loadingMotion = remember { CouiSwitchLoadingMotion() }
    SideEffect { loadingMotion.update(loading) }
    DisposableEffect(loadingMotion) { onDispose { loadingMotion.dispose() } }
    val density = LocalDensity.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val widthPx = with(density) { CouiSwitchDefaults.width.roundToPx() }.toFloat()
    val thumbPx = with(density) { CouiSwitchDefaults.thumbSize.toPx().toInt() }.toFloat()
    val paddingPx = with(density) { CouiSwitchDefaults.thumbPadding.roundToPx() }.toFloat()
    val travel = (widthPx - paddingPx * 2f - thumbPx).coerceAtLeast(1f)
    val state = remember(travel, rtl) { CouiSwitchMotion(checked, travel, rtl) }
    val currentChecked by rememberUpdatedState(checked)
    val callback by rememberUpdatedState(onCheckedChange)
    val dragCallback by rememberUpdatedState(onDragCheckedChange)
    val source = interactionSource ?: remember { MutableInteractionSource() }
    var touchPressed by remember { mutableStateOf(false) }
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    SideEffect {
        state.onCommit = { target -> if (target != currentChecked) dragCallback?.let { change ->
            view.performCouiHapticFeedback(CouiHapticFeedback.Switch, hapticFeedbackEnabled)
            change(target)
        } }
        state.update(checked, interactive)
        if (!interactive) touchPressed = false
    }
    DisposableEffect(state) { onDispose { touchPressed = false; state.dispose(); coordinates = null } }
    LaunchedEffect(state.settled, checked) {
        withFrameNanos { }
        state.reconcile(currentChecked)
    }
    val thumbPaint = remember { Paint(Paint.ANTI_ALIAS_FLAG) }
    val thumbOutline = remember { ThumbOutlineCache() }
    val resources = LocalContext.current.resources
    val configuration = LocalConfiguration.current
    val loadingGlyph = remember(resources, configuration) { CouiSwitchLoadingGlyph(resources) }
    val dragInput = if (onDragCheckedChange != null) Modifier
        .pointerInput(interactive, state) {
            if (!interactive) return@pointerInput
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                val tracker = VelocityTracker.obtain()
                // 消费者使用 getRawX/Y；setLocation 的局部缩放不改变原始位移。
                // 窗口平移在差值中抵消，不把手指位移缩放或提前截断到行程。
                fun windowPosition(local: androidx.compose.ui.geometry.Offset) =
                    coordinates?.takeIf { it.isAttached }?.localToWindow(local)
                fun rawPosition(event: androidx.compose.ui.input.pointer.PointerEvent,
                    local: androidx.compose.ui.geometry.Offset) = event.motionEvent?.let {
                    androidx.compose.ui.geometry.Offset(it.rawX, it.rawY)
                } ?: windowPosition(local)
                // convertToTranslationCoordinates.setLocation 不改变 rawX/Y；VelocityTracker
                // 却读取映射并夹紧的局部 X、固定的 Y=0，不能用原始位移计算甩动速度。
                fun sample(event: androidx.compose.ui.input.pointer.PointerEvent, x: Float) {
                    val original = event.motionEvent ?: return
                    val copy = MotionEvent.obtainNoHistory(original)
                    try {
                        val visualX = x - (size.width - widthPx) / 2f
                        copy.setLocation((visualX / widthPx * travel).coerceIn(0f, travel), 0f)
                        tracker.addMovement(copy)
                    } finally { copy.recycle() }
                }
                val downPosition = rawPosition(currentEvent, down.position)
                if (downPosition == null) { tracker.recycle(); return@awaitEachGesture }
                val accepted = state.begin(downPosition.x, downPosition.y)
                if (accepted) sample(currentEvent, down.position.x)
                var dragging = false
                var complete = false
                // 点击识别在 MOVE 被消费后会 Cancel；原厂按压仍由整次触摸会话持有。
                touchPressed = true
                try {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.isCouiTouchCancelled()) break
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (change.isConsumed) break
                        val point = rawPosition(event, change.position) ?: break
                        if (accepted && (change.position - down.position).getDistance() > viewConfiguration.touchSlop) dragging = true
                        if (dragging) {
                            change.consume()
                            if (change.pressed) {
                                sample(event, change.position.x)
                                state.drag(point.x, point.y)
                            }
                        }
                        if (!change.pressed) {
                            if (dragging) {
                                tracker.computeCurrentVelocity(1000, 12000f)
                                state.attach(tracker.xVelocity, tracker.yVelocity)
                            } else if (accepted) state.cancel()
                            complete = true
                            break
                        }
                        if (!dragging) {
                            val finalEvent = awaitPointerEvent(PointerEventPass.Final)
                            if (finalEvent.changes.any { it.id == down.id && it.isConsumed }) break
                        }
                    }
                } finally {
                    touchPressed = false
                    if (!complete && accepted) state.cancel()
                    tracker.recycle()
                }
            }
        } else Modifier
    val clickInput = if (onCheckedChange != null) Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
        .toggleable(checked, source, indication = null, enabled = interactive, role = Role.Switch,
            onValueChange = { target -> callback?.let { change ->
                view.performCouiHapticFeedback(CouiHapticFeedback.Switch, hapticFeedbackEnabled)
                change(target)
            } }) else Modifier
    Box(modifier.then(clickInput).then(dragInput).semantics {
        if (loading && loadingDescription != null) { stateDescription = loadingDescription; liveRegion = LiveRegionMode.Polite }
    }.onGloballyPositioned { coordinates = it }
        .wrapContentSize(Alignment.Center).requiredSize(CouiSwitchDefaults.width, CouiSwitchDefaults.height)
        // 焦点位于轨道外侧，必须在轨道自身的裁剪层之外绘制。
        .couiSwitchFocusOutline(source, enabled, colors.focusOutline, CouiSwitchDefaults.shape)
        .clip(CouiSwitchDefaults.shape).drawBehind {
            // 与滑块在绘制阶段读取同一帧，避免颜色先经重组、位置直接绘制造成错帧。
            val track = if (enabled) Color(ColorUtils.blendARGB(
                colors.uncheckedTrack.toArgb(), colors.checkedTrack.toArgb(), state.fraction))
                else if (checked) colors.disabledCheckedTrack else colors.disabledUncheckedTrack
            drawRect(track)
        }.couiSwitchFeedback(dark, source, enabled, touchPressed)) {
        Canvas(Modifier.requiredSize(CouiSwitchDefaults.width, CouiSwitchDefaults.height)) {
            thumbPaint.color = (if (enabled) colors.thumb else colors.disabledThumb).toArgb()
            if (enabled) thumbPaint.setShadowLayer(8f, 0f, 4f, android.graphics.Color.argb(25, 0, 0, 0))
            else thumbPaint.clearShadowLayer()
            val stretch = state.stretch
            val x = paddingPx + state.translation
            val y = (size.height - thumbPx) / 2f
            drawIntoCanvas { canvas ->
                val path = thumbOutline.path(
                    androidx.compose.ui.geometry.Size(thumbPx * stretch, thumbPx), layoutDirection, this)
                canvas.nativeCanvas.save()
                canvas.nativeCanvas.translate(x, y)
                canvas.nativeCanvas.scale(loadingMotion.thumbScale, loadingMotion.thumbScale,
                    thumbPx * stretch / 2, thumbPx / 2)
                canvas.nativeCanvas.drawPath(path, thumbPaint)
                canvas.nativeCanvas.restore()
            }
            if (loading) drawIntoCanvas { canvas ->
                loadingGlyph.draw(canvas.nativeCanvas, x, y, x + thumbPx * stretch, y + thumbPx,
                    loadingMotion.scale, loadingMotion.alpha, loadingMotion.rotation)
            }
        }
    }
}

/** 静止时复用同一条 Path；只有滑块尺寸变化才重新生成项目平滑轮廓。 */
private class ThumbOutlineCache {
    private var previous = androidx.compose.ui.geometry.Size.Zero
    private var cached = android.graphics.Path()

    fun path(size: androidx.compose.ui.geometry.Size, direction: androidx.compose.ui.unit.LayoutDirection,
        density: androidx.compose.ui.unit.Density): android.graphics.Path {
        if (size != previous) {
            previous = size
            cached = (CouiCapsuleShape.createOutline(size, direction, density) as androidx.compose.ui.graphics.Outline.Generic)
                .path.asAndroidPath()
        }
        return cached
    }
}
