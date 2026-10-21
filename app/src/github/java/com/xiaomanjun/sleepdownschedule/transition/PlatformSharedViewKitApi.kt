package com.xiaomanjun.sleepdownschedule.transition

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Bundle
import com.itgsa.animation.sharedview.ViewAnimationListener
import com.itgsa.animation.sharedview.ViewAnimationManager

internal object PlatformSharedViewKitApi : SharedViewKitApi {
    override fun supportedProtocol() = ViewAnimationManager.isViewAnimationSupported()
    override fun extras(): Map<String, String> = ViewAnimationManager.getViewAnimationExtras().orEmpty()
    override fun options(bitmap: Bitmap, bounds: Rect, radiusPx: Float, extras: Bundle,
        callbacks: SharedViewKitCallbacks) = ViewAnimationManager.makeViewAnimationOptions(
        bitmap, bounds, radiusPx, extras, object : ViewAnimationListener {
            override fun onAnimationStart(mode: Int) = callbacks.onStart(mode)
            override fun onAnimationEnd(mode: Int) = callbacks.onEnd(mode)
            override fun onAnimationCancel(mode: Int) = callbacks.onCancel(mode)
        }
    )
    override fun updateReturn(activity: Activity, extras: Bundle) =
        ViewAnimationManager.overrideViewAnimationTransition(activity, extras)
    override fun cancel() { ViewAnimationManager.cancelCurrentViewAnimation() }
}
