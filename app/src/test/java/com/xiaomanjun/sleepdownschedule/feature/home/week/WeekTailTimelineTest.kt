package com.xiaomanjun.sleepdownschedule.feature.home.week

import org.junit.Assert.*
import org.junit.Test

class WeekTailTimelineTest {
    @Test fun outgoingPageSurvivesUntilTheLastTailGroupLeaves() {
        val timeline = WeekTailTimeline(0f)
        timeline.advance(0L, 0f, 0, 1f)
        val trailing = timeline.advance(16_000_000L, 1f, 0, 1f)
        assertTrue(weekPageIntersectsTail(0, 1f, trailing))
        assertTrue(weekPageIntersectsTail(1, 1f, trailing))
        assertFalse(weekPageIntersectsTail(2, 1f, trailing))
        val settled = timeline.advance(160_000_000L, 1f, 0, 1f)
        assertFalse(weekPageIntersectsTail(0, 1f, settled))
        assertTrue(weekPageIntersectsTail(1, 1f, settled))
    }

    @Test fun reversedJumpReleasesOnlyTheFullyHiddenPage() {
        val timeline = WeekTailTimeline(19f)
        timeline.advance(0L, 19f, 0, 1f)
        val trailing = timeline.advance(16_000_000L, 18f, 0, 1f)
        assertTrue(weekPageIntersectsTail(19, 18f, trailing))
        assertTrue(weekPageIntersectsTail(18, 18f, trailing))
        assertFalse(weekPageIntersectsTail(17, 18f, trailing))
        val settled = timeline.advance(160_000_000L, 18f, 0, 1f)
        assertFalse(weekPageIntersectsTail(19, 18f, settled))
        assertTrue(weekPageIntersectsTail(18, 18f, settled))
    }

    @Test fun sameVerticalPositionAlwaysUsesTheSameRow() {
        val screenOnly = weekTailGroupForCard(0.5f, 0.5f, null, null)
        assertEquals(weekTailGroup(3, 3), screenOnly)
        val firstCard = weekTailGroupForCard(0.5f, 0.5f, 0f, 0f)
        val lastCard = weekTailGroupForCard(0.5f, 0.5f, 1f, 1f)
        assertEquals(weekTailGroup(3, 1), firstCard)
        assertEquals(weekTailGroup(3, 4), lastCard)
        assertNotEquals(firstCard, lastCard)
    }

    @Test fun rebasingClearsEveryFollowerBeforeTheNextOnePageAnimation() {
        val timeline = WeekTailTimeline(0f)
        timeline.advance(0L, 0f, 0, 1f)
        timeline.advance(16_000_000L, 0.5f, 0, 1f)
        timeline.snapTo(18f).forEach { assertEquals(18f, it, 0f) }
        val started = timeline.advance(32_000_000L, 18.2f, 0, 1f)
        assertEquals(18.2f, started.first(), 0f)
        assertEquals(18f, started.last(), 0f)
    }

    @Test fun followersCatchUpWhileFingerHoldsHalfway() {
        val timeline = WeekTailTimeline(0f)
        val anchor = weekTailGroup(2, 2)
        timeline.advance(0L, 0f, anchor, 1f)
        val started = timeline.advance(16_000_000L, 0.5f, anchor, 1f)
        assertEquals(0.5f, started[anchor], 0f)
        assertTrue(started[weekTailGroup(0, 0)] < started[anchor])
        var held = started
        for (ms in 32..224 step 16) held = timeline.advance(ms * 1_000_000L, 0.5f, anchor, 1f)
        held.forEach { assertEquals(0.5f, it, 0.00001f) }
    }

    @Test fun rowsTrailTogetherWithoutChangingSameRowCardSpacing() {
        val timeline = WeekTailTimeline(0f)
        val anchor = weekTailGroup(2, 2)
        var positions = emptyList<Float>()
        for (ms in 0..96 step 8) positions = timeline.advance(ms * 1_000_000L, ms / 200f, anchor, 1f)
        assertEquals(positions[weekTailGroup(1, 2)], positions[weekTailGroup(3, 2)], 0.00001f)
        assertEquals(positions[weekTailGroup(2, 1)], positions[weekTailGroup(2, 3)], 0.00001f)
        assertTrue(positions[anchor] > positions[weekTailGroup(1, 2)])
        assertTrue(positions[anchor] - positions[weekTailGroup(1, 2)] > 0.10f)
        for (row in 0 until WeekTailRows) {
            for (column in 1 until WeekTailColumns) {
                assertEquals(positions[weekTailGroup(row, 0)], positions[weekTailGroup(row, column)], 0f)
            }
        }
        assertTrue(positions[weekTailGroup(1, 2)] > positions[weekTailGroup(0, 2)])
    }

    @Test fun reversalKeepsHistoryAndEventuallySettlesAtNewTarget() {
        val timeline = WeekTailTimeline(0f)
        for (ms in 0..96 step 8) timeline.advance(ms * 1_000_000L, ms / 200f, 0, 1f)
        val reversed = timeline.advance(104_000_000L, 0.4f, 0, 1f)
        assertEquals(0.4f, reversed[0], 0f)
        assertTrue(reversed[weekTailGroup(1, 0)] < reversed[0])
        var positions = timeline.advance(112_000_000L, 0f, 0, 1f)
        assertTrue(positions[weekTailGroup(1, 0)] > positions[0])
        for (ms in 120..288 step 8) positions = timeline.advance(ms * 1_000_000L, 0f, 0, 1f)
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
