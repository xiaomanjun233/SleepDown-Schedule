package com.xiaomanjun.sleepdownschedule.feature.importing

import com.xiaomanjun.sleepdownschedule.feature.importing.shiguang.ShiguangWarehouseUpdater
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class ShiguangWarehouseUpdaterTest {
    @Test
    fun validSuccessfulRefreshIsReusedForSevenDays() {
        val lastSuccess = 1_000_000L

        assertFalse(
            ShiguangWarehouseUpdater.shouldRefresh(
                lastSuccessfulRefreshMillis = lastSuccess,
                nowMillis = lastSuccess + TimeUnit.DAYS.toMillis(7) - 1,
                hasValidRemoteIndex = true
            )
        )
        assertTrue(
            ShiguangWarehouseUpdater.shouldRefresh(
                lastSuccessfulRefreshMillis = lastSuccess,
                nowMillis = lastSuccess + TimeUnit.DAYS.toMillis(7),
                hasValidRemoteIndex = true
            )
        )
    }

    @Test
    fun missingCacheRefreshesEvenWhenTimestampExists() {
        assertTrue(
            ShiguangWarehouseUpdater.shouldRefresh(
                lastSuccessfulRefreshMillis = 1_000_000L,
                nowMillis = 1_000_001L,
                hasValidRemoteIndex = false
            )
        )
    }
}
