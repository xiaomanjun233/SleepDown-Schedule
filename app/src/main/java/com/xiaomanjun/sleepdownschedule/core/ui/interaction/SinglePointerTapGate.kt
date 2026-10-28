package com.xiaomanjun.sleepdownschedule.core.ui.interaction

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** Observes multi-touch without consuming it, so week editing/zoom gestures keep their inputs. */
internal class SinglePointerTapGate {
    private var generation = 0L
    private var multiple = false
    val allowed: Boolean get() = !multiple
    fun begin(): Long { multiple = false; return ++generation }
    fun observe(token: Long, count: Int) { if (token == generation && count > 1) multiple = true }
    fun finish(token: Long) { if (token == generation) multiple = false }
}

internal fun Modifier.observeCoursePointers(gate: SinglePointerTapGate): Modifier = pointerInput(gate) {
    coroutineScope {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val token = gate.begin()
            var event = currentEvent
            while (true) {
                gate.observe(token, event.changes.count { it.pressed || it.previousPressed })
                if (event.changes.none { it.pressed }) break
                event = awaitPointerEvent(PointerEventPass.Initial)
            }
            // Child click callbacks run later in this pointer event. Reset afterwards, without
            // blocking observation of the next gesture or retaining a stale accessibility lock.
            launch { withFrameNanos { }; gate.finish(token) }
        }
    }
}
