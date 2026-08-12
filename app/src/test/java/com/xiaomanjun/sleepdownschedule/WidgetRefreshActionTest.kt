package com.xiaomanjun.sleepdownschedule

import android.content.Intent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetRefreshActionTest {
    @Test
    fun refreshesAfterClockOrTimezoneChanges() {
        assertTrue(MiuixTodayWidgetRenderer.isRefreshAction(Intent.ACTION_DATE_CHANGED))
        assertTrue(MiuixTodayWidgetRenderer.isRefreshAction(Intent.ACTION_TIME_CHANGED))
        assertTrue(MiuixTodayWidgetRenderer.isRefreshAction(Intent.ACTION_TIMEZONE_CHANGED))
    }

    @Test
    fun ignoresUnrelatedBroadcasts() {
        assertFalse(MiuixTodayWidgetRenderer.isRefreshAction(Intent.ACTION_AIRPLANE_MODE_CHANGED))
    }
}
