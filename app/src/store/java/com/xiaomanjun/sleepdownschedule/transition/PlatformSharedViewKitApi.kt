package com.xiaomanjun.sleepdownschedule.transition

import android.app.Activity
import android.app.ActivityOptions
import android.os.Bundle
import android.view.View

/** Store builds neither depend on nor load the experimental SDK. */
internal object PlatformSharedViewKitApi : SharedViewKitApi {
    override fun supportedProtocol() = 0
    override fun extras() = emptyMap<String, String>()
    override fun options(source: View, extras: Bundle,
        callbacks: SharedViewKitCallbacks): ActivityOptions? = null
    override fun updateReturn(activity: Activity, extras: Bundle) = Unit
    override fun cancel() = Unit
}
