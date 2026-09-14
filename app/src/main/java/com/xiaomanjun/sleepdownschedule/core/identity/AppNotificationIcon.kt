package com.xiaomanjun.sleepdownschedule.core.identity

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Log

private const val AppIconExtra = "com.xiaomanjun.sleepdownschedule.notification.APP_ICON"
private const val PromotedOngoingExtra = "android.requestPromotedOngoing"

/** One style-aware app icon for both the notification header and promoted SystemUI surface. */
internal fun Notification.Builder.applyAppNotificationIcon(context: Context): Notification.Builder = apply {
    setSmallIcon(currentLiveUpdateIconResId(context))
    setLargeIcon(null as Icon?)
    extras.remove(Notification.EXTRA_LARGE_ICON)
    extras.remove(Notification.EXTRA_LARGE_ICON_BIG)
    extras.putBoolean(AppIconExtra, true)
    setColor(Notification.COLOR_DEFAULT)
}

/** Update posted tasks in place, including promoted notifications created before this helper. */
internal fun refreshAppNotificationIcons(context: Context) {
    val manager = context.getSystemService(NotificationManager::class.java)
    if (!manager.areNotificationsEnabled()) return
    val iconResId = currentLiveUpdateIconResId(context)
    for (active in manager.activeNotifications) {
        val notification = active.notification
        if (!notification.extras.getBoolean(AppIconExtra) &&
            !notification.extras.getBoolean(PromotedOngoingExtra)
        ) continue
        val icon = notification.smallIcon
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
            icon?.type == Icon.TYPE_RESOURCE && icon.resId == iconResId &&
            icon.resPackage == context.packageName && notification.getLargeIcon() == null
        ) continue
        val updated = Notification.Builder.recoverBuilder(context, notification)
            .applyAppNotificationIcon(context)
            .setOnlyAlertOnce(true)
            .build()
        try {
            manager.notify(active.tag, active.id, updated)
        } catch (error: SecurityException) {
            Log.w("SleepDownAppIcon", "Notification permission changed during icon refresh", error)
            return
        }
    }
}
