package com.xiaomanjun.sleepdownschedule.core.ui.interaction

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.withFrameNanos
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.coroutines.coroutineContext

internal data class MorphClockSample(val value: Float, val velocity: Float, val finished: Boolean)

/** Scalar continuation for the independent background depth clock. Visual card interruptions
 * replay their original authored route through MorphCurveContinuity below. */
internal class MorphRetargetPlan(
    private val start: Float,
    private val initialVelocity: Float,
    private val target: Float,
    durationMillis: Int,
    valueRange: ClosedFloatingPointRange<Float>
) {
    private val opposite = initialVelocity * (target - start) < 0f
    private val clearance = if (initialVelocity > 0f) valueRange.endInclusive - start else start - valueRange.start
    private val brakeSeconds = if (opposite && abs(initialVelocity) > 0.000001f) {
        min(0.060f, 2f * clearance.coerceAtLeast(0f) / abs(initialVelocity))
    } else 0f
    private val departure = start + initialVelocity * brakeSeconds * 0.5f
    private val departureVelocity = if (opposite) 0f else initialVelocity
    private val speedLimit = maxOf(
        (valueRange.endInclusive - valueRange.start) * 1500f / durationMillis,
        abs(departureVelocity)
    )
    private val travelSeconds = if (abs(target - departure) > 0.000001f) {
        1.5f * abs(target - departure) / speedLimit
    } else 0f
    val durationSeconds: Float = brakeSeconds + travelSeconds

    fun sample(elapsedSeconds: Float): MorphClockSample {
        val elapsed = elapsedSeconds.coerceAtLeast(0f)
        if (elapsed >= durationSeconds) return MorphClockSample(target, 0f, true)
        if (elapsed < brakeSeconds) {
            val acceleration = -initialVelocity / brakeSeconds
            return MorphClockSample(start + initialVelocity * elapsed + acceleration * elapsed * elapsed * 0.5f,
                initialVelocity + acceleration * elapsed, false)
        }
        val travel = elapsed - brakeSeconds
        if (travelSeconds == 0f || travel >= travelSeconds) return MorphClockSample(target, 0f, true)
        val u = travel / travelSeconds
        val u2 = u * u
        val u3 = u2 * u
        val distance = target - departure
        return MorphClockSample(
            departure + distance * (3f * u2 - 2f * u3) + departureVelocity * travelSeconds * (u3 - 2f * u2 + u),
            distance * (6f * u - 6f * u2) / travelSeconds + departureVelocity * (3f * u2 - 4f * u + 1f),
            false
        )
    }
}

/** Normal flights retain their route's clock. Only interrupted flights use a finite continuation. */
internal class MorphRetargetState(private val valueRange: ClosedFloatingPointRange<Float> = 0f..1f) {
    var velocity: Float = 0f
        private set
    private var moving = false

    fun reset() { moving = false; velocity = 0f }

    suspend fun animate(
        clock: Animatable<Float, AnimationVector1D>, target: Float, durationMillis: Int,
        easing: Easing = LinearEasing, delayMillis: Int = 0,
        continuation: MorphCurveContinuity? = null
    ) {
        if (abs(target - clock.value) < 0.000001f && abs(velocity) < 0.000001f) {
            reset()
            clock.snapTo(target)
            return
        }
        val interrupted = moving
        moving = true
        val flightDuration = continuation?.durationMillis
        if (flightDuration != null) {
            // Only elapsed time: the original route owns position, shape and content handoff.
            // Another easing or braking stage here would distort that authored replay.
            clock.animateTo(target, tween(flightDuration, easing = LinearEasing)) {
                this@MorphRetargetState.velocity = this.velocity
            }
        } else if (!interrupted) {
            val duration = (durationMillis * abs(target - clock.value) /
                (valueRange.endInclusive - valueRange.start)).roundToInt().coerceAtLeast(1)
            clock.animateTo(target, tween(duration, delayMillis, easing)) {
                this@MorphRetargetState.velocity = this.velocity
            }
        } else {
            val plan = MorphRetargetPlan(clock.value, velocity, target, durationMillis, valueRange)
            var previousFrame: Long? = null
            var elapsedSeconds = 0f
            while (true) {
                val frame = withFrameNanos { it }
                val scale = coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f
                if (scale <= 0f) {
                    clock.snapTo(target)
                    break
                }
                previousFrame?.let { elapsedSeconds += (frame - it) / (1_000_000_000f * scale) }
                previousFrame = frame
                val sample = plan.sample(elapsedSeconds)
                clock.snapTo(sample.value)
                velocity = sample.velocity
                if (sample.finished) break
            }
        }
        reset()
    }
}

/** Only the moving, input-blocked form owns this gesture; settled forms keep normal scrolling. */
internal fun Modifier.morphDismissSwipe(
    enabled: Boolean, onTapTarget: ((Offset) -> Boolean)? = null, onDismiss: () -> Unit
): Modifier =
    if (!enabled) this else pointerInput(onDismiss, onTapTarget) {
        val distance = 48.dp.toPx()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                val delta = change.position - down.position
                if (-delta.y >= distance && -delta.y > abs(delta.x) * 1.5f) {
                    change.consume()
                    onDismiss()
                    break
                }
                if (!change.pressed) {
                    if (delta.getDistance() <= viewConfiguration.touchSlop && onTapTarget?.invoke(change.position) == true)
                        change.consume()
                    break
                }
            }
        }
    }
