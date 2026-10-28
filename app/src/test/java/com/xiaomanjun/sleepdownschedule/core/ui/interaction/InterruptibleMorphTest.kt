package com.xiaomanjun.sleepdownschedule.core.ui.interaction

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.ui.MotionDurationScale
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test

class InterruptibleMorphTest {
    private fun replayCurve() = MorphCurveContinuity().apply {
        bind({ p, _ -> floatArrayOf(100f * p, 0f, 100f * p + 20f, 20f) }) { current, closing ->
            val frames: (Float) -> FloatArray = { p ->
                val x = if (closing) current[0] * p else current[0] + (100f - current[0]) * p
                floatArrayOf(x, 0f, x + 20f, 20f)
            }
            frames
        }
    }
    private class Frames(private val stepNanos: Long = 16_666_667L) {
        val clock = BroadcastFrameClock()
        private var time = 0L
        suspend fun next() {
            clock.sendFrame(time)
            time += stepNanos
            repeat(4) { yield() }
        }
    }

    @Test fun redirectedCardHasOneElapsedClockWithoutAnExtraBrakeOrEasing() = runBlocking {
        val frames = Frames(10_000_000L)
        val clock = Animatable(0f)
        val motion = MorphRetargetState()
        val curve = replayCurve()
        val opening = launch(frames.clock, start = CoroutineStart.UNDISPATCHED) { motion.animate(clock, 1f, 380, continuation = curve) }
        repeat(13) { frames.next() }
        val before = clock.value
        opening.cancelAndJoin()
        curve.redirect(before, true, 440)
        assertEquals(before * 100f, curve.value(0, before, Float.NaN), 0.001f)
        val duration = curve.durationMillis!!
        val returning = launch(frames.clock, start = CoroutineStart.UNDISPATCHED) { motion.animate(clock, 0f, 440, continuation = curve) }
        repeat(duration / 10 + 2) { frame ->
            frames.next()
            val expected = before * (1f - frame * 10f / duration).coerceAtLeast(0f)
            assertEquals(expected, clock.value, 0.00001f)
        }
        assertTrue(returning.isCompleted)
        assertEquals(0f, curve.value(0, clock.value, Float.NaN), 0f)
    }

    @Test fun redirectedCardStillHonorsSystemAnimationScaleAndDisable() = runBlocking {
        for (factor in listOf(2f, 0f)) {
            val frames = Frames(10_000_000L)
            val clock = Animatable(0.5f)
            val motion = MorphRetargetState()
            val curve = replayCurve().apply {
                redirect(0.5f, true, 440)
            }
            val scale = object : MotionDurationScale { override val scaleFactor = factor }
            val returning = launch(frames.clock + scale, start = CoroutineStart.UNDISPATCHED) { motion.animate(clock, 0f, 440, continuation = curve) }
            repeat(12) { frame ->
                frames.next()
                val expected = if (factor == 0f) 0f else 0.5f * (1f - frame * 10f / factor / curve.durationMillis!!).coerceAtLeast(0f)
                assertEquals(expected, clock.value, 0.00001f)
            }
            repeat(100) { frames.next() }
            assertTrue(returning.isCompleted)
            assertEquals(0f, clock.value, 0f)
        }
    }

    @Test fun interruptedContinuationHonorsTheSystemAnimationMultiplier() = runBlocking {
        val frames = Frames()
        val clock = Animatable(0f)
        val motion = MorphRetargetState()
        val opening = launch(frames.clock, start = CoroutineStart.UNDISPATCHED) { motion.animate(clock, 1f, 380) }
        repeat(7) { frames.next() }
        val start = clock.value
        val speed = motion.velocity
        opening.cancelAndJoin()
        val scale = object : MotionDurationScale { override val scaleFactor = 2f }
        val returning = launch(frames.clock + scale, start = CoroutineStart.UNDISPATCHED) { motion.animate(clock, 0f, 440) }
        repeat(5) { frame ->
            frames.next()
            val elapsed = frame * 16_666_667f / 2_000_000_000f
            assertEquals(start + speed * elapsed - speed / 0.060f * elapsed * elapsed * 0.5f, clock.value, 0.00001f)
        }
        repeat(120) { frames.next() }
        assertTrue(returning.isCompleted)
        assertEquals(0f, clock.value, 0f)
    }

    @Test fun disabledSystemAnimationsAlsoFinishAnInterruptedContinuation() = runBlocking {
        val frames = Frames()
        val clock = Animatable(0f)
        val motion = MorphRetargetState()
        val opening = launch(frames.clock, start = CoroutineStart.UNDISPATCHED) { motion.animate(clock, 1f, 380) }
        repeat(7) { frames.next() }
        opening.cancelAndJoin()
        val scale = object : MotionDurationScale { override val scaleFactor = 0f }
        val returning = launch(frames.clock + scale, start = CoroutineStart.UNDISPATCHED) { motion.animate(clock, 0f, 440) }
        frames.next()
        assertTrue(returning.isCompleted)
        assertEquals(0f, clock.value, 0f)
        assertEquals(0f, motion.velocity, 0f)
    }

    @Test fun uninterruptedRoutesKeepTheirOwnLinearClockAndFinishWithoutASpringTail() = runBlocking {
        val routes = listOf(380 to 1f, 440 to 0f, 340 to 1f, 220 to 0f)
        for ((duration, target) in routes) {
            val frames = Frames(10_000_000L)
            val clock = Animatable(1f - target)
            val motion = MorphRetargetState()
            val job = launch(frames.clock, start = CoroutineStart.UNDISPATCHED) { motion.animate(clock, target, duration) }
            for (frame in 0..duration / 10) {
                frames.next()
                val fraction = frame * 10f / duration
                assertEquals("duration=$duration, frame=$frame", (1f - target) + (2f * target - 1f) * fraction, clock.value, 0.0001f)
                if (frame * 10 < duration) assertFalse(job.isCompleted)
            }
            assertTrue(job.isCompleted)
            assertEquals(target, clock.value, 0f)
            assertEquals(0f, motion.velocity, 0f)
        }
    }

    @Test fun backgroundKeepsItsExistingDelayAndEasingInsteadOfAddingAnotherTimingCurve() = runBlocking {
        val frames = Frames(10_000_000L)
        val clock = Animatable(1f)
        val reference = Animatable(1f)
        val easing = com.xiaomanjun.sleepdownschedule.feature.course.editor.CourseEditorMotion.BackgroundZoomInertialEasing
        val motion = MorphRetargetState(1f..1.08f)
        val job = launch(frames.clock, start = CoroutineStart.UNDISPATCHED) { motion.animate(clock, 1.08f, 420, easing, 20) }
        val referenceJob = launch(frames.clock, start = CoroutineStart.UNDISPATCHED) { reference.animateTo(1.08f, tween(420, 20, easing)) }
        repeat(45) {
            frames.next()
            assertEquals(reference.value, clock.value, 0.000001f)
        }
        assertTrue(job.isCompleted && referenceJob.isCompleted)
    }

    @Test fun finiteContinuationBrakesInsideBoundsAndCannotAccumulateUnboundedSpeed() {
        for ((start, speed, target) in listOf(Triple(0.30f, 2.63f, 0f), Triple(0.99f, 2.63f, 0f),
            Triple(0.02f, -2.27f, 1f), Triple(0.65f, 3.40f, 1f))) {
            val duration = if (target == 0f) 440 else 380
            val plan = MorphRetargetPlan(start, speed, target, duration, 0f..1f)
            assertEquals(start, plan.sample(0f).value, 0f)
            assertEquals(speed, plan.sample(0f).velocity, 0.00001f)
            assertTrue(plan.durationSeconds <= duration / 1000f + 0.061f)
            repeat((plan.durationSeconds * 1000).toInt() + 2) { millis ->
                val sample = plan.sample(millis / 1000f)
                assertTrue(sample.value in -0.00001f..1.00001f)
                assertTrue(kotlin.math.abs(sample.velocity) <= maxOf(1500f / duration, kotlin.math.abs(speed)) + 0.001f)
            }
            assertEquals(MorphClockSample(target, 0f, true), plan.sample(plan.durationSeconds + 0.001f))
        }
    }

    @Test fun rapidSameCardRetargetKeepsPositionAndBrakesBeforeReversing() = runBlocking {
        val frames = Frames()
        val clock = Animatable(0f)
        val motion = MorphRetargetState()
        var job = launch(frames.clock, start = CoroutineStart.UNDISPATCHED) { motion.animate(clock, 1f, 380) }
        repeat(7) { frames.next() }
        assertTrue(clock.value in 0.01f..0.9f)
        repeat(6) { turn ->
            val before = clock.value
            val velocity = motion.velocity
            job.cancelAndJoin()
            val target = if (turn % 2 == 0) 0f else 1f
            job = launch(frames.clock, start = CoroutineStart.UNDISPATCHED) { motion.animate(clock, target, if (target == 1f) 380 else 440) }
            assertEquals(before, clock.value, 0f)
            frames.next() // the initial frame of a new target must also retain the old velocity
            assertEquals(velocity, motion.velocity, 0.001f)
            if (turn == 0) {
                frames.next()
                assertTrue("A close request must brake the outward motion, not jump or flip its velocity", clock.value > before)
            }
            repeat(4) { frames.next() }
        }
        repeat(120) { frames.next() }
        assertTrue(job.isCompleted)
        assertEquals(1f, clock.value, 0f)
    }

    @Test fun retargetingARepeatedlyNeverChangesTheIndependentClosingClockOfB() = runBlocking {
        val frames = Frames()
        val a = Animatable(0f)
        val b = Animatable(1f)
        val referenceB = Animatable(1f)
        val aMotion = MorphRetargetState()
        val bMotion = MorphRetargetState()
        val referenceMotion = MorphRetargetState()
        var aJob = launch(frames.clock, start = CoroutineStart.UNDISPATCHED) { aMotion.animate(a, 1f, 380) }
        val bJob = launch(frames.clock, start = CoroutineStart.UNDISPATCHED) { bMotion.animate(b, 0f, 440) }
        val referenceJob = launch(frames.clock, start = CoroutineStart.UNDISPATCHED) { referenceMotion.animate(referenceB, 0f, 440) }
        repeat(120) { frame ->
            if (frame in listOf(6, 10, 14, 18, 22, 26)) {
                aJob.cancelAndJoin()
                val target = if ((frame / 4) % 2 == 1) 0f else 1f
                aJob = launch(frames.clock, start = CoroutineStart.UNDISPATCHED) { aMotion.animate(a, target, if (target == 1f) 380 else 440) }
            }
            frames.next()
            assertEquals(referenceB.value, b.value, 0.00001f)
        }
        assertTrue(aJob.isCompleted && bJob.isCompleted && referenceJob.isCompleted)
        assertEquals(0f, b.value, 0f)
    }
}
