package com.xiaomanjun.sleepdownschedule.feature.home

import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test

class HomeSwitchMotionTest {
    @Test fun densePagesSelectPlainSlideBeforeTheFirstTransitionFrame() {
        val count = mutableIntStateOf(0)
        val motion = HomeSwitchMotion(false, mutableStateOf(false), renderedCardCount = { count.intValue })
        assertFalse(motion.plainSlide)
        count.intValue = 20
        assertTrue(motion.plainSlide)
        assertFalse(motion.moving)
    }

    private class Frames {
        val clock = BroadcastFrameClock()
        private var time = 0L

        suspend fun next() {
            clock.sendFrame(time)
            time += 16_666_667L
            repeat(3) { yield() }
        }
    }

    @Test fun tenCardsKeepStaggerWhileElevenMoveTogetherWithoutOvershoot() = runBlocking {
        for (count in listOf(10, 11)) {
            val frames = Frames()
            val target = mutableStateOf(true)
            val motion = HomeSwitchMotion(false, target, renderedCardCount = { count })
            val job = launch(frames.clock, start = CoroutineStart.UNDISPATCHED) { motion.animateTo(true) }
            var sawStagger = false
            var previous = 0f
            repeat(90) {
                frames.next()
                val page = motion.progress.value
                sawStagger = sawStagger || (0 until 6).any { motion.groupProgress(it) != page }
                if (count == 11) {
                    assertTrue(page >= previous && page <= 1f)
                    for (group in 0 until 6) assertEquals(page, motion.groupProgress(group), 0f)
                }
                previous = page
            }
            assertEquals(count == 10, sawStagger)
            if (count == 11) {
                assertTrue(job.isCompleted)
                assertFalse(motion.moving)
                assertFalse(motion.retains(false))
            }
            job.cancelAndJoin()
        }
    }

    @Test fun reversalKeepsItsStyleAndPositionThenNextSwitchRechecksCount() = runBlocking {
        val frames = Frames()
        val target = mutableStateOf(true)
        var count = 11
        val motion = HomeSwitchMotion(false, target, renderedCardCount = { count })
        val outgoing = launch(frames.clock, start = CoroutineStart.UNDISPATCHED) { motion.animateTo(true) }
        repeat(7) { frames.next() }
        assertTrue(motion.progress.value in 0.01f..0.99f)
        val interruptedPosition = motion.progress.value
        outgoing.cancelAndJoin()
        count = 10
        target.value = false
        val returning = launch(frames.clock, start = CoroutineStart.UNDISPATCHED) { motion.animateTo(false) }
        assertEquals(interruptedPosition, motion.progress.value, 0f)
        assertTrue(motion.plainSlide)
        repeat(90) {
            frames.next()
            for (group in 0 until 6) assertEquals(motion.progress.value, motion.groupProgress(group), 0f)
        }
        assertTrue(returning.isCompleted)
        assertFalse(motion.moving)
        assertFalse(motion.retains(true))

        target.value = true
        val next = launch(frames.clock, start = CoroutineStart.UNDISPATCHED) { motion.animateTo(true) }
        assertFalse(motion.plainSlide)
        next.cancelAndJoin()
        motion.settleAt(true)
        assertFalse(motion.moving)
        for (group in 0 until 6) assertEquals(1f, motion.groupProgress(group), 0f)
    }

    @Test fun denseLandscapeAlsoUsesPlainSlideAndSettleClearsInterruptedMotion() = runBlocking {
        val frames = Frames()
        val target = mutableStateOf(true)
        val motion = HomeSwitchMotion(false, target, landscape = true, renderedCardCount = { 11 })
        val job = launch(frames.clock, start = CoroutineStart.UNDISPATCHED) { motion.animateTo(true) }
        repeat(7) { frames.next() }
        assertTrue(motion.plainSlide)
        assertTrue(motion.moving)
        job.cancelAndJoin()
        target.value = false
        motion.settleAt(false)
        assertFalse(motion.moving)
        assertFalse(motion.retains(true))
        assertEquals(0f, motion.progress.value, 0f)
        for (group in 0 until 6) assertEquals(0f, motion.groupProgress(group), 0f)
    }
}
