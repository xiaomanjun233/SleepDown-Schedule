package com.xiaomanjun.sleepdownschedule.feature.coloros

import android.net.Uri

internal object ColorOSCourseContract {
    const val SOURCE_AUTHORITY = "com.xiaomanjun.sleepdownschedule.coloros.course"
    const val PROXY_AUTHORITY = "com.suda.yzune.wakeupschedule.provider"
    const val PROXY_PACKAGE = "com.suda.yzune.wakeupschedule"
    const val PROXY_ACTIVITY = "$PROXY_PACKAGE.MainActivity"
    const val PROXY_WARM_UP_ACTION = "$PROXY_PACKAGE.action.WARM_UP"
    const val BRIDGE_PERMISSION = "com.xiaomanjun.sleepdownschedule.permission.COLOROS_COURSE_BRIDGE"
    const val PROXY_METADATA_KEY = "com.xiaomanjun.sleepdownschedule.COLOROS_PROXY"
    const val PROXY_METADATA_VERSION = "sleepdown-coloros-proxy-v1"
    const val PROXY_REFRESH_METHOD = "refresh"
    const val PROXY_REFRESH_ACCEPTED = "refresh_accepted"

    val refreshUri: Uri = Uri.parse("content://$PROXY_AUTHORITY/refresh")
    fun sourceUri(path: String): Uri = Uri.parse("content://$SOURCE_AUTHORITY/$path")
    fun proxyUri(path: String): Uri = Uri.parse("content://$PROXY_AUTHORITY/$path")
}
