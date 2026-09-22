package com.xiaomanjun.sleepdownschedule.feature.home.week

import org.junit.Assert.*
import org.junit.Test

class WeekTailTimelineTest {
    @Test fun followersCatchUpWhileFingerHoldsHalfway() {
        val timeline = WeekTailTimeline(0f)
        timeline.advance(0L, 0f, 2, 1f)
        val started = timeline.advance(16_000_000L, 0.5f, 2, 1f)
        assertEquals(0.5f, started[2], 0f)
        assertTrue(started[0] < started[2])
        var held = started
        for (ms in 32..160 step 16) held = timeline.advance(ms * 1_000_000L, 0.5f, 2, 1f)
        held.forEach { assertEquals(0.5f, it, 0.00001f) }
    }

    @Test fun distanceFromFingerDeterminesDelayInBothDirections() {
        val timeline = WeekTailTimeline(0f)
        var positions = emptyList<Float>()
        for (ms in 0..96 step 8) positions = timeline.advance(ms * 1_000_000L, ms / 200f, 2, 1f)
        assertEquals(positions[1], positions[3], 0.00001f)
        assertEquals(positions[0], positions[4], 0.00001f)
        assertTrue(positions[2] > positions[1])
        assertTrue(positions[1] > positions[0])
        assertTrue(positions[0] > positions[5])
    }

    @Test fun reversalKeepsHistoryAndEventuallySettlesAtNewTarget() {
        val timeline = WeekTailTimeline(0f)
        for (ms in 0..96 step 8) timeline.advance(ms * 1_000_000L, ms / 200f, 0, 1f)
        val reversed = timeline.advance(104_000_000L, 0.4f, 0, 1f)
        assertEquals(0.4f, reversed[0], 0f)
        assertTrue(reversed[5] < reversed[0])
        var positions = reversed
        for (ms in 112..288 step 8) positions = timeline.advance(ms * 1_000_000L, 0f, 0, 1f)
        positions.forEach { assertEquals(0f, it, 0.00001f) }
    }

    @Test fun disablingAnimationsAndLongPageJumpsDiscardOldTrails() {
        val timeline = WeekTailTimeline(0f)
        timeline.advance(0L, 0f, 0, 1f)
        timeline.advance(16_000_000L, 0.4f, 0, 1f)
        timeline.advance(32_000_000L, 0.8f, 0, 0f).forEach { assertEquals(0.8f, it, 0f) }
        timeline.advance(48_000_000L, 9f, 0, 1f).forEach { assertEquals(9f, it, 0f) }
    }

    @Test fun idleGapDoesNotStretchTheNextGestureDelay() {
        val timeline = WeekTailTimeline(0f)
        timeline.advance(0L, 0f, 0, 1f)
        timeline.advance(10_000_000_000L, 0.5f, 0, 1f)
        val positions = timeline.advance(10_144_000_000L, 0.5f, 0, 1f)
        positions.forEach { assertEquals(0.5f, it, 0.00001f) }
    }
}
