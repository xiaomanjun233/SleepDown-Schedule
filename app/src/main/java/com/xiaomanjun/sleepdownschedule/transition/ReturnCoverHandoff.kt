package com.xiaomanjun.sleepdownschedule.transition

import android.view.View
import androidx.core.view.OneShotPreDrawListener

/** Give Compose a frame to apply restored state before retiring the pre-armed landing cover. */
internal class ReturnCoverHandoff(private val root: View, private val releaseCover: () -> Unit) :
    Runnable, View.OnAttachStateChangeListener {
    private var preDraw: OneShotPreDrawListener? = null
    private var released = false
    private val armForNextFrame = Runnable {
        if (!released) {
            preDraw = OneShotPreDrawListener.add(root, this)
            root.invalidate()
        }
    }

    fun start() {
        if (released) return
        if (!root.isAttachedToWindow) {
            run()
            return
        }
        root.addOnAttachStateChangeListener(this)
        root.postOnAnimation(armForNextFrame)
        // An app backgrounded during the return may not draw again. Do not retain its cover.
        root.postDelayed(this, MaximumWaitMillis)
        root.invalidate()
    }

    override fun run() {
        if (released) return
        released = true
        preDraw?.removeListener()
        preDraw = null
        root.removeOnAttachStateChangeListener(this)
        root.removeCallbacks(this)
        root.removeCallbacks(armForNextFrame)
        releaseCover()
    }

    override fun onViewAttachedToWindow(view: View) = Unit
    override fun onViewDetachedFromWindow(view: View) = run()

    companion object {
        internal const val MaximumWaitMillis = 1_000L
    }
}
