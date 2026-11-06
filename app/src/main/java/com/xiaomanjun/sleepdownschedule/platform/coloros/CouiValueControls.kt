package com.xiaomanjun.sleepdownschedule.platform.coloros

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.itos.coui.slider.CouiSliderDefaults
import com.itos.coui.slider.Slider as CouiSliderComponent
import com.itos.coui.switch.CouiSwitchDefaults
import com.itos.coui.switch.Switch as CouiSwitchComponent
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.NativeSliderRequest
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.NativeSliderSession
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.NativeSwitchRequest
import kotlin.math.abs

/**
 * COUI-Kit 滑条的兼容入口。保留宿主的 preview/commit/cancel 事务、snapValue 和
 * 触摸拖动语义；Kit 滑条只负责轨道、thumb、跟手光和键盘/无障碍步进。
 */
@Composable
internal fun CouiValueSlider(request: NativeSliderRequest): Boolean {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val latest = rememberUpdatedState(request)
    val session = remember { NativeSliderSession { latest.value } }
    var internalValue by remember(request.range) {
        mutableFloatStateOf(request.value().coerceIn(request.range))
    }
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val span = request.range.endInclusive - request.range.start

    SideEffect {
        if (!session.tracking) internalValue = latest.value.value().coerceIn(latest.value.range)
    }
    DisposableEffect(session) { onDispose { session.finish(true) } }

    fun preview(raw: Float): Float {
        val current = latest.value
        val snap = current.snapValue?.takeIf { it in current.range }
        return if (snap != null && span > 0f && abs(raw - snap) < span * 0.015f) snap else raw
    }

    Box(request.modifier.fillMaxWidth().height(48.dp)) {
        CouiSliderComponent(
            modifier = Modifier.fillMaxWidth().height(48.dp)
                .pointerInput(session) {
                    awaitPointerEventScope {
                        var downId: PointerId? = null
                        var downPosition: Offset? = null
                        var horizontalDrag = false
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.isTouchCancelled()) {
                                session.finish(true)
                                downId = null
                                downPosition = null
                                horizontalDrag = false
                                continue
                            }
                            val tracked = event.changes.firstOrNull { it.id == downId }
                                ?: event.changes.firstOrNull { it.pressed }
                            if (tracked == null) {
                                downId = null
                                downPosition = null
                                horizontalDrag = false
                                continue
                            }
                            if (downId == null) {
                                downId = tracked.id
                                downPosition = tracked.position
                                horizontalDrag = false
                            }
                            val delta = tracked.position - (downPosition ?: tracked.position)
                            if (!horizontalDrag && abs(delta.x) > viewConfiguration.touchSlop) {
                                horizontalDrag = true
                            }
                            // The kit slider yields to a vertical parent scroll before dragging.
                            // Keep this observer on the same rule, or preview mode would stay on.
                            if (!horizontalDrag && abs(delta.y) > viewConfiguration.touchSlop &&
                                abs(delta.y) > abs(delta.x)
                            ) {
                                session.finish(true)
                                downId = null
                                downPosition = null
                            }
                            if (!tracked.pressed) {
                                downId = null
                                downPosition = null
                                horizontalDrag = false
                            }
                        }
                    }
                },
            value = internalValue,
            onValueChange = { raw ->
                if (!session.tracking) session.start()
                internalValue = raw.coerceIn(request.range)
                session.change(preview(internalValue))
            },
            onValueChangeFinished = { session.finish(false) },
            colors = CouiSliderDefaults.colors(MaterialTheme.colorScheme.primary, dark, request.enabled),
            dark = dark,
            valueRange = request.range,
            enabled = request.enabled,
        )
        val snap = request.snapValue?.takeIf { it in request.range }
        if (snap != null && span > 0f) {
            val fraction = ((snap - request.range.start) / span).coerceIn(0f, 1f)
            Canvas(Modifier.matchParentSize()) {
                val inset = 14.dp.toPx()
                val visual = if (isLtr) fraction else 1f - fraction
                drawCircle(
                    color = Color.White.copy(alpha = 0.78f),
                    radius = 2.5.dp.toPx(),
                    center = Offset(inset + (size.width - inset * 2f) * visual, size.height / 2f),
                )
            }
        }
    }
    return true
}

/** COUI-Kit 开关；compact 保持原 fallback 的 0.8 视觉缩放，不改变宿主行高。 */
@Composable
internal fun CouiValueSwitch(request: NativeSwitchRequest): Boolean {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val compact = if (request.compact) Modifier.graphicsLayer {
        scaleX = 0.8f
        scaleY = 0.8f
    } else Modifier
    CouiSwitchComponent(
        modifier = request.modifier.then(compact),
        checked = request.checked,
        onCheckedChange = request.onCheckedChange,
        dark = dark,
        colors = CouiSwitchDefaults.colors(MaterialTheme.colorScheme.primary, dark),
        enabled = request.enabled,
    )
    return true
}

/** Mirrors Coui input's cancel predicate; the kit helper is module-internal. */
private fun PointerEvent.isTouchCancelled(): Boolean =
    motionEvent == null && changes.any { it.previousPressed } && changes.all {
        !it.pressed && (!it.previousPressed ||
            (it.isConsumed && it.uptimeMillis == it.previousUptimeMillis &&
                it.position == it.previousPosition))
    }
