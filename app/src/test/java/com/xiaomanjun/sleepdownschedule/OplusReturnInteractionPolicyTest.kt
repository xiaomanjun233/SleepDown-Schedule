package com.xiaomanjun.sleepdownschedule

import android.view.MotionEvent
import com.xiaomanjun.sleepdownschedule.transition.ReturnVerticalDragDetector
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OplusReturnInteractionPolicyTest {
    @Test
    fun verticalDragPastTouchSlopRequestsImmediateCleanupOnce() {
        val detector = ReturnVerticalDragDetector(touchSlopPx = 8f)

        assertFalse(detector.onTouch(MotionEvent.ACTION_DOWN, 100f, 200f))
        assertFalse(detector.onTouch(MotionEvent.ACTION_MOVE, 102f, 207f))
        assertTrue(detector.onTouch(MotionEvent.ACTION_MOVE, 103f, 209f))
        assertFalse(detector.onTouch(MotionEvent.ACTION_MOVE, 104f, 240f))
    }

    @Test
    fun horizontalOrCancelledGestureDoesNotRequestCleanup() {
        val detector = ReturnVerticalDragDetector(touchSlopPx = 8f)

        assertFalse(detector.onTouch(MotionEvent.ACTION_DOWN, 100f, 200f))
        assertFalse(detector.onTouch(MotionEvent.ACTION_MOVE, 120f, 205f))
        assertFalse(detector.onTouch(MotionEvent.ACTION_CANCEL, 120f, 205f))
        assertFalse(detector.onTouch(MotionEvent.ACTION_MOVE, 121f, 230f))
    }
}
