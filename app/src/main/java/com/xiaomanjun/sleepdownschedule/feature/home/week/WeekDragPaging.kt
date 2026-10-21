package com.xiaomanjun.sleepdownschedule.feature.home.week

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.Velocity

internal fun weekDragEdgeDirection(pointerX: Float, left: Float, right: Float, edge: Float,
    week: Int, totalWeeks: Int): Int = when {
    right <= left || edge <= 0 -> 0
    pointerX <= left + edge && week > 1 -> -1
    pointerX >= right - edge && week < totalWeeks -> 1
    else -> 0
}

/** The root stays in the pointer hit path even when the source pager page is disposed. */
@Composable
internal fun Modifier.weekMoveGestureHost(controller: WeekEditOverlayController,
    onDrag: (Offset) -> Unit, onFinish: (Velocity) -> Unit, onCancel: () -> Unit): Modifier {
    val drag by rememberUpdatedState(onDrag)
    val finish by rememberUpdatedState(onFinish)
    val cancel by rememberUpdatedState(onCancel)
    return pointerInput(controller) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val tracker = VelocityTracker()
            var owned = false
            var released = false
            try {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (event.changes.any { it.id != down.id && it.pressed }) break
                    val request = controller.request
                    if (!owned && request?.mode == WeekEditOverlayMode.Move && controller.gestureActive) {
                        owned = true
                        controller.rootOwnsMoveGesture = true
                    }
                    if (owned) {
                        tracker.addPosition(change.uptimeMillis, change.position)
                        if (!change.pressed) {
                            released = change.changedToUpIgnoreConsumed()
                            change.consume()
                            if (released) finish(tracker.calculateVelocity())
                            break
                        }
                        drag(change.positionChange())
                        change.consume()
                    } else if (!change.pressed) break
                }
            } finally {
                if (owned && !released) cancel()
            }
        }
    }
}
