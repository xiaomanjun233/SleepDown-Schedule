package com.xiaomanjun.sleepdownschedule.platform.coloros

import android.app.Application
import android.view.MotionEvent
import android.view.View
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.core.app.ApplicationProvider
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ColorOsPagerIndicatorTest {
    private fun host() = LauncherPagerIndicatorHost(ApplicationProvider.getApplicationContext()).apply {
        setPages(5, 0f)
        measure(exactly(280), exactly(48))
        layout(0, 0, 280, 48)
    }

    @Test fun tapsSelectTheTouchedPageInBothDirections() {
        val host = host()
        val selected = mutableListOf<Int>()
        host.onSelect = { selected += it }
        host.touch(MotionEvent.ACTION_DOWN, 279f)
        host.touch(MotionEvent.ACTION_UP, 279f)
        host.layoutDirection = View.LAYOUT_DIRECTION_RTL
        host.touch(MotionEvent.ACTION_DOWN, 279f)
        host.touch(MotionEvent.ACTION_UP, 279f)
        assertEquals(listOf(4, 0), selected)
    }

    @Test fun draggingReportsContinuousProgressAndReleaseWithoutAnExtraTap() {
        val host = host()
        var feedbacks = 0
        host.pageFeedback = { feedbacks++ }
        val values = mutableListOf<Triple<Float, Boolean, Boolean>>()
        host.onScrub = { page, active, cancel -> values += Triple(page, active, cancel) }
        host.onSelect = { throw AssertionError("A scrub must not also click") }
        host.touch(MotionEvent.ACTION_DOWN, 0f)
        host.touch(MotionEvent.ACTION_MOVE, 140f)
        host.touch(MotionEvent.ACTION_MOVE, 141f)
        host.touch(MotionEvent.ACTION_UP, 279f)
        assertEquals(2f, values.first().first, 0.01f)
        assertEquals(Triple(4f, false, false), values.last())
        assertEquals(2, feedbacks)
    }

    @Test fun disablingDuringDragCancelsThePageAndNativePress() {
        val host = host()
        var cancelled = false
        val nativeActions = mutableListOf<Int>()
        host.onScrub = { _, active, cancel -> if (!active) cancelled = cancel }
        host.nativeTouch = { nativeActions += it.actionMasked }
        host.touch(MotionEvent.ACTION_DOWN, 0f)
        host.touch(MotionEvent.ACTION_MOVE, 140f)
        host.setInputEnabled(false)
        assertTrue(cancelled)
        assertEquals(MotionEvent.ACTION_CANCEL, nativeActions.last())
        assertFalse(host.touch(MotionEvent.ACTION_DOWN, 140f))
    }

    @Test fun cancelledGestureDoesNotSelectAPageAndReleasedHostRejectsInput() {
        val host = host()
        host.onSelect = { throw AssertionError("Cancelled gesture selected a page") }
        host.touch(MotionEvent.ACTION_DOWN, 0f)
        host.touch(MotionEvent.ACTION_CANCEL, 140f)
        host.release()
        assertFalse(host.touch(MotionEvent.ACTION_DOWN, 140f))
    }

    private fun LauncherPagerIndicatorHost.touch(action: Int, x: Float): Boolean {
        val event = MotionEvent.obtain(0, 10, action, x, 24f, 0)
        return try { onTouchEvent(event) } finally { event.recycle() }
    }

    private fun exactly(value: Int) = View.MeasureSpec.makeMeasureSpec(value, View.MeasureSpec.EXACTLY)
}
