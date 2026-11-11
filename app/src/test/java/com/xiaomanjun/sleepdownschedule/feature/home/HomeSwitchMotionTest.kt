package com.xiaomanjun.sleepdownschedule.feature.home

import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test

class HomeSwitchMotionTest {
    private class Frames {
        val clock = BroadcastFrameClock()
        private var time = 0L
        suspend fun next() {
            clock.sendFrame(time)
            time += 16_666_667L
            repeat(3) { yield() }
        }
    }

    @Test fun oneClockDrivesFollowersAndReleasesTheOutgoingPage() = runBlocking {
        val frames = Frames()
        val target = mutableStateOf(true)
        val motion = HomeSwitchMotion(false, target)
        val job = launch(frames.clock, start = CoroutineStart.UNDISPATCHED) { motion.animateTo(true) }
        var sawStagger = false
        repeat(120) {
            frames.next()
            sawStagger = sawStagger || (1 until 6).any { motion.groupProgress(it) != motion.progress.value }
        }
        assertTrue(sawStagger)
        assertTrue(job.isCompleted)
        assertFalse(motion.moving)
        assertFalse(motion.retains(false))
        for (group in 0 until 6) assertEquals(1f, motion.groupProgress(group), 0f)
    }

    @Test fun reversingInEitherOrientationKeepsPositionAndBothPagesUntilSettled() = runBlocking {
        for (landscape in listOf(false, true)) {
            val frames = Frames()
            val target = mutableStateOf(true)
            val motion = HomeSwitchMotion(false, target, landscape)
            val outgoing = launch(frames.clock, start = CoroutineStart.UNDISPATCHED) { motion.animateTo(true) }
            repeat(7) { frames.next() }
            val interrupted = motion.progress.value
            val followers = (0 until 6).map(motion::groupProgress)
            assertTrue(interrupted in 0.01f..0.99f)
            outgoing.cancelAndJoin()
            target.value = false
            val returning = launch(frames.clock, start = CoroutineStart.UNDISPATCHED) { motion.animateTo(false) }
            assertEquals(interrupted, motion.progress.value, 0f)
            assertEquals(followers, (0 until 6).map(motion::groupProgress))
            assertTrue(motion.retains(false) && motion.retains(true))
            repeat(120) { frames.next() }
            assertTrue(returning.isCompleted)
            assertFalse(motion.moving)
            assertFalse(motion.retains(true))
            for (group in 0 until 6) assertEquals(0f, motion.groupProgress(group), 0f)
        }
    }

    @Test fun disablingAnimationSettlesAnInterruptedTransition() = runBlocking {
        val frames = Frames()
        val target = mutableStateOf(true)
        val motion = HomeSwitchMotion(false, target)
        val job = launch(frames.clock, start = CoroutineStart.UNDISPATCHED) { motion.animateTo(true) }
        repeat(7) { frames.next() }
        job.cancelAndJoin()
        target.value = false
        motion.settleAt(false)
        assertFalse(motion.moving)
        assertFalse(motion.retains(true))
        assertEquals(0f, motion.progress.value, 0f)
        assertEquals(0f, motion.cornerFraction, 0f)
    }
}
