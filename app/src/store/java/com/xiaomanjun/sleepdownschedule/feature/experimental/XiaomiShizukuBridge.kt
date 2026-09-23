package com.xiaomanjun.sleepdownschedule.feature.experimental

import android.content.Context

/** Store builds contain no Shizuku provider, permission or client dependency. */
internal object XiaomiShizukuBridge {
    fun isRunning(): Boolean = false
    fun isAuthorized(): Boolean = false
    fun requestPermission(onResult: (Boolean) -> Unit) = onResult(false)
    fun restoreIfInterrupted(context: Context): Boolean = true
    fun postWithTemporaryBypass(context: Context, post: () -> Unit) = post()
}
