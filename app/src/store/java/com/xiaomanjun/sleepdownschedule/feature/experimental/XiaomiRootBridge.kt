package com.xiaomanjun.sleepdownschedule.feature.experimental

import android.content.Context

/** Store builds do not request root access or alter system networking. */
internal object XiaomiRootBridge {
    fun isAuthorized(context: Context): Boolean = false
    fun requestAuthorization(context: Context): Boolean = false
    fun restoreIfInterrupted(context: Context): Boolean = true
    fun postWithTemporaryBypass(context: Context, post: () -> Unit) = post()
}
