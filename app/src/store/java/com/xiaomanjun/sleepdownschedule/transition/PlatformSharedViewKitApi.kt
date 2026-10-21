package com.xiaomanjun.sleepdownschedule.transition

import android.app.Activity
import android.app.ActivityOptions
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Bundle

/** Store builds neither depend on nor load the experimental SDK. */
internal object PlatformSharedViewKitApi : SharedViewKitApi {
    override fun supportedProtocol() = 0
    override fun extras() = emptyMap<String, String>()
    override fun options(bitmap: Bitmap, bounds: Rect, radiusPx: Float, extras: Bundle,
        callbacks: SharedViewKitCallbacks): ActivityOptions? = null
    override fun updateReturn(activity: Activity, extras: Bundle) = Unit
    override fun cancel() = Unit
}
