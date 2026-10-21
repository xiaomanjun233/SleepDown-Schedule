package com.xiaomanjun.sleepdownschedule.platform.coloros

import android.graphics.Rect
import android.view.MotionEvent

/** Both Window.Callback events and getGlobalVisibleRect use the decor's coordinate space. */
internal class NativeDialogTouchGate(private val onDismissRequest: () -> Unit) {
    private var startedOutside = false

    fun consume(event: MotionEvent, panelInWindow: Rect): Boolean {
        // rawX/rawY include the dialog window's screen offset and cannot be compared to this rect.
        val outside = !panelInWindow.contains(event.x.toInt(), event.y.toInt())
        if (event.actionMasked == MotionEvent.ACTION_DOWN) startedOutside = outside
        val consume = startedOutside || event.actionMasked == MotionEvent.ACTION_OUTSIDE
        if (consume && outside &&
            (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_OUTSIDE)) {
            onDismissRequest()
        }
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            startedOutside = false
        }
        return consume
    }
}
