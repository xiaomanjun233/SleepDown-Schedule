package com.xiaomanjun.sleepdownschedule

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DayAgentWeatherCacheTest {
    @Test
    fun acceptsOnlyRecentPastTimestamps() {
        val now = 2_000_000L

        assertTrue(isDayAgentWeatherCacheFresh(now - 60_000L, now))
        assertTrue(isDayAgentWeatherCacheFresh(now - 30 * 60 * 1_000L, now))
        assertFalse(isDayAgentWeatherCacheFresh(now - 30 * 60 * 1_000L - 1L, now))
    }

    @Test
    fun rejectsMissingAndFutureTimestampsAfterClockRollback() {
        val now = 2_000_000L

        assertFalse(isDayAgentWeatherCacheFresh(0L, now))
        assertFalse(isDayAgentWeatherCacheFresh(now + 1L, now))
    }
}
