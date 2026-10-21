package com.xiaomanjun.sleepdownschedule.platform.coloros

import android.app.Application
import android.graphics.Rect
import android.view.MotionEvent
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class NativeDialogTouchGateTest {
    private val panel = Rect(20, 30, 320, 330)

    // Raw coordinates remain on screen when Android offsets delivery into the dialog window.
    private fun event(action: Int, x: Float, y: Float): MotionEvent =
        MotionEvent.obtain(0, 10, action, x + 80f, y + 900f, 0).apply {
            offsetLocation(-80f, -900f)
        }

    @Test fun offsetDialogPassesPickerDragsAndButtonTapsToContent() {
        var dismissals = 0
        val gate = NativeDialogTouchGate { dismissals++ }
        listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP).forEach { action ->
            val e = event(action, 100f, 100f)
            assertFalse(panel.contains(e.rawX.toInt(), e.rawY.toInt()))
            assertFalse("Inside event $action must reach picker/button", gate.consume(e, panel))
            e.recycle()
        }
        assertEquals(0, dismissals)
    }

    @Test fun gestureStartingInsideCanLeaveThePanelWithoutDismissing() {
        var dismissals = 0
        val gate = NativeDialogTouchGate { dismissals++ }
        val down = event(MotionEvent.ACTION_DOWN, 100f, 100f)
        val move = event(MotionEvent.ACTION_MOVE, 10f, 10f)
        val up = event(MotionEvent.ACTION_UP, 10f, 10f)
        listOf(down, move, up).forEach { assertFalse(gate.consume(it, panel)); it.recycle() }
        assertEquals(0, dismissals)
    }

    @Test fun outsideTapRequestsDismissOnceAndCancelDoesNotLeakIntoNextGesture() {
        var dismissals = 0
        val gate = NativeDialogTouchGate { dismissals++ }
        listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP).forEach { action ->
            event(action, 0f, 0f).let { assertTrue(gate.consume(it, panel)); it.recycle() }
        }
        assertEquals(1, dismissals)
        listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_CANCEL).forEach { action ->
            event(action, 0f, 0f).let { assertTrue(gate.consume(it, panel)); it.recycle() }
        }
        event(MotionEvent.ACTION_DOWN, 100f, 100f).let {
            assertFalse(gate.consume(it, panel)); it.recycle()
        }
        assertEquals(1, dismissals)
    }
}
