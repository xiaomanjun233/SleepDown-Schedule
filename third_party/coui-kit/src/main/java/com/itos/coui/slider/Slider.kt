package com.itos.coui.slider

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.progressSemantics
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.itos.coui.effect.SpotlightPreset
import com.itos.coui.effect.rememberCouiSpotlight
import com.itos.coui.input.isCouiTouchCancelled
import com.itos.coui.motion.CouiTouchMotion
import com.itos.coui.shape.CouiCapsuleShape
import com.itos.coui.theme.CouiTheme
import kotlin.math.abs

/**
 * steps 是内部等分点数, showTickMarks 独立决定是否使用 SectionSeekBar 的点位与吸附。
 * 不采样背景, 不依赖 Oplus SDK; 宿主提供配色、深浅色和轨道 Shape。
 */
@Composable
fun Slider(
    modifier: Modifier = Modifier,
    value: Float,
    onValueChange: (Float) -> Unit,
    colors: CouiSliderColors? = null,
    dark: Boolean = CouiTheme.isDark,
    shape: Shape = CouiCapsuleShape,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    showTickMarks: Boolean = false,
    enabled: Boolean = true,
    onValueChangeFinished: () -> Unit = {},
) {
    val resolvedColors = colors ?: CouiSliderDefaults.colors(CouiTheme.colorSchemeFor(dark).accent, dark, enabled)
    require(steps >= 0 && steps < Int.MAX_VALUE)
    require(value.isFinite() && valueRange.start.isFinite() && valueRange.endInclusive.isFinite())
    require(valueRange.endInclusive >= valueRange.start && (valueRange.endInclusive - valueRange.start).isFinite())
    val interactive = enabled && valueRange.endInclusive > valueRange.start
    val density = LocalDensity.current.density
    val layoutDirection = LocalLayoutDirection.current
    val rtl = layoutDirection == LayoutDirection.Rtl
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val onChange by rememberUpdatedState(onValueChange)
    val onFinished by rememberUpdatedState(onValueChangeFinished)
    val motion = remember(valueRange, steps, showTickMarks, density, view) {
        CouiSliderMotion(valueRange, steps, showTickMarks, density, value, { onChange(it) }, { onFinished() }) { code, section ->
            performSliderHaptic(view, code, section)
        }
    }
    val touch = remember(scope, density) { CouiTouchMotion(scope, density, deformationEnabled = false) }
    val renderer = remember(density, shape, layoutDirection) { CouiSliderRenderer(density, shape, layoutDirection) }
    val (spotlightInput, drawSpotlight) = rememberCouiSpotlight(
        interactive = interactive,
        preset = SpotlightPreset.OpaqueMedium1,
        dark = dark,
    )
    SideEffect {
        motion.syncValue(value)
        if (!interactive) {
            motion.cancelGesture()
            touch.release(Offset.Zero)
        }
    }
    DisposableEffect(motion) {
        onDispose { motion.dispose() }
    }
    DisposableEffect(touch, renderer) {
        onDispose {
            touch.dispose()
            renderer.dispose()
        }
    }
    val span = valueRange.endInclusive - valueRange.start
    val keyIncrement = span / if (steps > 0 || showTickMarks) steps + 1 else 100
    Box(
        modifier
            .defaultMinSize(minWidth = 144.dp, minHeight = 48.dp)
            .progressSemantics(value.coerceIn(valueRange), valueRange, steps)
            .semantics {
                if (!interactive) disabled()
                setProgress { next ->
                    if (!interactive || !next.isFinite()) false
                    else {
                        motion.setFromAction(next)
                        true
                    }
                }
            }
            .onKeyEvent { event ->
                if (!interactive || event.type != KeyEventType.KeyDown) return@onKeyEvent false
                val next = when (event.key) {
                    Key.DirectionLeft -> motion.value + keyIncrement * if (rtl) 1f else -1f
                    Key.DirectionRight -> motion.value + keyIncrement * if (rtl) -1f else 1f
                    Key.DirectionUp -> motion.value + keyIncrement
                    Key.DirectionDown -> motion.value - keyIncrement
                    Key.MoveHome -> valueRange.start
                    Key.MoveEnd -> valueRange.endInclusive
                    else -> return@onKeyEvent false
                }
                motion.setFromAction(next)
                true
            }
            .focusable(interactive)
            .onSizeChanged { motion.resize(it.width - 28f * density) }
            .pointerInput(motion, touch, interactive, rtl) {
                if (!interactive) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown()
                    if (motion.length <= 0f) return@awaitEachGesture
                    val tracker = VelocityTracker.obtain()
                    val downTime = down.uptimeMillis
                    var active = down.id
                    var pointerOffset = 0f
                    var origin = down.position
                    var dragging = false
                    var completed = false
                    fun fingerX(x: Float): Float {
                        val local = x - pointerOffset - touch.translationX.value - 14f * density
                        return if (rtl) motion.length - local else local
                    }
                    try {
                        tracker.addChange(downTime, down)
                        motion.press(fingerX(down.position.x))
                        touch.begin(down.position, size)
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.isCouiTouchCancelled()) break
                            val tracked = event.changes.firstOrNull { it.id == active } ?: break
                            val next = if (!tracked.pressed) event.changes.firstOrNull { it.pressed } else null
                            if (next != null) {
                                pointerOffset += next.position.x - tracked.position.x
                                origin += next.position - tracked.position
                                touch.switchPointer(tracked.position, next.position)
                                active = next.id
                                tracker.clear()
                                tracker.addChange(downTime, next)
                                continue
                            }
                            if (tracked.isConsumed) break
                            if (!tracked.pressed) {
                                val local = fingerX(tracked.position.x)
                                val inside = tracked.position.x in 0f..size.width.toFloat() &&
                                    tracked.position.y in 0f..size.height.toFloat()
                                if (dragging || inside) {
                                    val refreshRate = view.display?.refreshRate?.takeIf { it > 0f } ?: 60f
                                    if (dragging) motion.drag(local, tracker.xVelocity * if (rtl) -1f else 1f)
                                    motion.release(local, tracker.xVelocity * if (rtl) -1f else 1f, 1f / refreshRate)
                                    tracked.consume()
                                } else motion.cancelGesture()
                                touch.release(Offset(tracker.xVelocity * 0.0003f, 0f))
                                completed = true
                                break
                            }
                            tracker.addChange(downTime, tracked)
                            tracker.computeCurrentVelocity(1000, 8000f)
                            val delta = tracked.position - origin
                            if (!dragging && abs(delta.y) > viewConfiguration.touchSlop && abs(delta.y) > abs(delta.x)) break
                            if (abs(delta.x) >= 5f) touch.move(Offset(tracked.position.x, down.position.y))
                            if (dragging || abs(delta.x) > viewConfiguration.touchSlop) {
                                dragging = true
                                motion.drag(fingerX(tracked.position.x), tracker.xVelocity * if (rtl) -1f else 1f)
                                tracked.consume()
                            }
                        }
                    } finally {
                        tracker.recycle()
                        if (!completed) {
                            motion.cancelGesture()
                            touch.release(Offset.Zero)
                        }
                    }
                }
            },
    ) {
        Canvas(
            Modifier.matchParentSize()
                .graphicsLayer {
                    translationX = touch.translationX.value
                    compositingStrategy = if (drawSpotlight == null) CompositingStrategy.Auto else CompositingStrategy.Offscreen
                }
                .then(spotlightInput),
        ) {
            val canvas = drawContext.canvas.nativeCanvas
            renderer.draw(canvas, size.height, motion, resolvedColors, interactive, rtl, if (showTickMarks) steps + 1 else 0)
            if (drawSpotlight != null) {
                val checkpoint = canvas.save()
                renderer.clipTrack(canvas)
                drawSpotlight()
                canvas.restoreToCount(checkpoint)
            }
        }
    }
}

private fun VelocityTracker.addChange(downTime: Long, change: PointerInputChange) {
    for (sample in change.historical) {
        val event = MotionEvent.obtain(downTime, sample.uptimeMillis, MotionEvent.ACTION_MOVE, sample.position.x, sample.position.y, 0)
        addMovement(event)
        event.recycle()
    }
    val action = when {
        !change.previousPressed -> MotionEvent.ACTION_DOWN
        else -> MotionEvent.ACTION_MOVE
    }
    val event = MotionEvent.obtain(downTime, change.uptimeMillis, action, change.position.x, change.position.y, 0)
    addMovement(event)
    event.recycle()
}

private fun performSliderHaptic(view: View, code: Int, section: Boolean) {
    if (view.performHapticFeedback(code)) return
    if (section && code == 308 && view.performHapticFeedback(302)) return
    val standard = when {
        code == 306 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> HapticFeedbackConstants.GESTURE_END
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE ->
            if (section) HapticFeedbackConstants.SEGMENT_TICK else HapticFeedbackConstants.SEGMENT_FREQUENT_TICK
        else -> HapticFeedbackConstants.CLOCK_TICK
    }
    view.performHapticFeedback(standard)
}
