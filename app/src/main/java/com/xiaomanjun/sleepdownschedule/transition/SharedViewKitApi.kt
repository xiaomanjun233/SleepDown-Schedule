package com.xiaomanjun.sleepdownschedule.transition

import android.app.Activity
import android.app.ActivityOptions
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Bundle

/** The only SDK boundary. GitHub uses the official facade; the store variant has no SDK. */
internal interface SharedViewKitApi {
    fun supportedProtocol(): Int
    fun extras(): Map<String, String>
    fun options(bitmap: Bitmap, bounds: Rect, radiusPx: Float, extras: Bundle,
        callbacks: SharedViewKitCallbacks): ActivityOptions?
    fun updateReturn(activity: Activity, extras: Bundle)
    fun cancel()
}

internal data class SharedViewKitCallbacks(
    val onStart: (Int) -> Unit,
    val onEnd: (Int) -> Unit,
    val onCancel: (Int) -> Unit
)
