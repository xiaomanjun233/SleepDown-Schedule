package com.xiaomanjun.sleepdownschedule.feature.importing.progress

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import kotlin.math.ceil
import kotlin.math.exp

/** One bounded body: short spring lag under the finger, damped flight and wall reflection on release. */
internal class EduBrowserWindowMotion {
    var position by mutableStateOf(Offset.Zero)
        private set
    var active by mutableStateOf(false)
        private set
    private var bounds = Offset.Zero
    private var target = Offset.Zero
    private var velocity = Offset.Zero
    private var initialized = false
    private var mode = Mode.IDLE
    private var elapsed = 0f
    private enum class Mode { IDLE, DRAG, SETTLE, FLIGHT }

    fun updateBounds(value: Offset) {
        val next = Offset(value.x.coerceAtLeast(0f), value.y.coerceAtLeast(0f))
        if (!initialized) {
            initialized = true
            bounds = next
            position = Offset(next.x, 0f)
            target = position
        } else if (bounds != next) {
            bounds = next
            position = position.bounded()
            target = target.bounded()
            velocity = Offset.Zero
            if (mode == Mode.FLIGHT) mode = Mode.IDLE
            active = mode != Mode.IDLE && (target - position).getDistance() > 0.4f
        }
    }

    fun beginDrag() { mode = Mode.DRAG; velocity = Offset.Zero; target = position; active = false }
    fun dragBy(delta: Offset) { target = (target + delta).bounded(); active = true }
    fun cancelDrag() { mode = Mode.SETTLE; active = true }
    fun release(value: Offset, maxSpeed: Float) {
        val speed = value.getDistance()
        velocity = if (speed > maxSpeed && speed > 0f) value * (maxSpeed / speed) else value
        mode = if (speed > 24f) Mode.FLIGHT else Mode.SETTLE
        elapsed = 0f
        active = true
    }

    fun moveToFraction(fraction: Offset) {
        stop()
        position = Offset(bounds.x * fraction.x.coerceIn(0f, 1f), bounds.y * fraction.y.coerceIn(0f, 1f))
        target = position
    }

    fun stop() { mode = Mode.IDLE; velocity = Offset.Zero; target = position; active = false }

    fun advance(seconds: Float) {
        if (!active) return
        val duration = seconds.coerceIn(0f, 0.05f)
        val steps = ceil(duration / (1f / 240f)).toInt().coerceAtLeast(1)
        val dt = duration / steps
        var point = position
        var speed = velocity
        repeat(steps) {
            speed = if (mode == Mode.FLIGHT) speed * exp(-4.5f * dt)
                else speed + ((target - point) * 5000f - speed * 120f) * dt
            point += speed * dt
            if (bounds.x <= 0f) { point = point.copy(x = 0f); speed = speed.copy(x = 0f) }
            else if (point.x < 0f || point.x > bounds.x) {
                point = point.copy(x = point.x.coerceIn(0f, bounds.x))
                speed = speed.copy(x = if (mode == Mode.FLIGHT) -speed.x * 0.55f else 0f)
            }
            if (bounds.y <= 0f) { point = point.copy(y = 0f); speed = speed.copy(y = 0f) }
            else if (point.y < 0f || point.y > bounds.y) {
                point = point.copy(y = point.y.coerceIn(0f, bounds.y))
                speed = speed.copy(y = if (mode == Mode.FLIGHT) -speed.y * 0.55f else 0f)
            }
        }
        position = point
        velocity = speed
        elapsed += duration
        if (mode == Mode.FLIGHT) {
            if (speed.getDistance() < 8f || elapsed >= 3f) stop()
        } else if ((target - point).getDistance() < 0.4f && speed.getDistance() < 5f) {
            position = target
            velocity = Offset.Zero
            active = false
            if (mode == Mode.SETTLE) mode = Mode.IDLE
        }
    }

    private fun Offset.bounded() = Offset(x.coerceIn(0f, bounds.x), y.coerceIn(0f, bounds.y))
}
