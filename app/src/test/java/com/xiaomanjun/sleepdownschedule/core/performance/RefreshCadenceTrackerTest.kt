package com.xiaomanjun.sleepdownschedule.core.performance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RefreshCadenceTrackerTest {
    @Test
    fun medianIntervalsMapToStableRefreshBuckets() {
        assertEquals(
            RefreshRateBucket.Hz60,
            refreshRateBucketForMedianInterval(16_666_667L)
        )
        assertEquals(
            RefreshRateBucket.Hz90,
            refreshRateBucketForMedianInterval(11_111_111L)
        )
        assertEquals(
            RefreshRateBucket.Hz120,
            refreshRateBucketForMedianInterval(8_333_333L)
        )
    }

    @Test
    fun trackerEmitsOnlyConfirmedBucketChanges() {
        val tracker = RefreshCadenceTracker(sampleSize = 5, confirmationsRequired = 2)
        var timestamp = 1_000_000_000L
        assertNull(tracker.recordFrameTimestampNanos(timestamp))

        val firstChanges = buildList {
            repeat(8) {
                timestamp += if (it % 2 == 0) 16_500_000L else 16_800_000L
                tracker.recordFrameTimestampNanos(timestamp)?.let(::add)
            }
        }
        assertEquals(listOf(RefreshRateBucket.Hz60), firstChanges)

        val highRefreshChanges = buildList {
            repeat(10) {
                timestamp += if (it % 2 == 0) 8_200_000L else 8_450_000L
                tracker.recordFrameTimestampNanos(timestamp)?.let(::add)
            }
        }
        assertEquals(listOf(RefreshRateBucket.Hz120), highRefreshChanges)
    }
}
