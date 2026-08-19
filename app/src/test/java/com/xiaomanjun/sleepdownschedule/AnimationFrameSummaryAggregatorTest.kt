package com.xiaomanjun.sleepdownschedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnimationFrameSummaryAggregatorTest {
    @Test
    fun emitsOneSummaryWhenTargetAnimationEnds() {
        val summaries = mutableListOf<AnimationFrameSummary>()
        val aggregator = AnimationFrameSummaryAggregator("PersonalizeOpen", summaries::add)

        aggregator.onFrame("Idle", 1_000_000, isJank = false)
        aggregator.onFrame("PersonalizeOpen", 10_000_000, isJank = false)
        aggregator.onFrame("PersonalizeOpen", 20_000_000, isJank = true)
        aggregator.onFrame("PersonalizeOpen", 30_000_000, isJank = false)
        aggregator.onFrame("PersonalizeOpen", 40_000_000, isJank = false)
        aggregator.onFrame("PersonalizeOpen", 50_000_000, isJank = true)
        aggregator.onFrame("Idle", 1_000_000, isJank = false)
        aggregator.onFrame("Idle", 1_000_000, isJank = false)

        assertEquals(1, summaries.size)
        with(summaries.single()) {
            assertEquals("PersonalizeOpen", animation)
            assertEquals(5, frameCount)
            assertEquals(2, jankFrameCount)
            assertEquals(30_000_000, frameDurationUiP50Nanos)
            assertEquals(50_000_000, frameDurationUiP90Nanos)
            assertEquals(50_000_000, frameDurationUiP95Nanos)
            assertEquals(50_000_000, frameDurationUiP99Nanos)
            assertEquals(50_000_000, maxFrameDurationUiNanos)
            assertTrue(toLogMessage().contains("jankRate=40.00%"))
        }
    }

    @Test
    fun ignoresNonTargetAnimations() {
        val summaries = mutableListOf<AnimationFrameSummary>()
        val aggregator = AnimationFrameSummaryAggregator("PersonalizeOpen", summaries::add)

        aggregator.onFrame("PersonalizePrepare", 20_000_000, isJank = true)
        aggregator.onFrame("PersonalizeClose", 30_000_000, isJank = true)
        aggregator.onFrame("Idle", 1_000_000, isJank = false)

        assertTrue(summaries.isEmpty())
    }
}
