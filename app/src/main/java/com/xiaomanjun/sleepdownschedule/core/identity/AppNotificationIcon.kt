package com.xiaomanjun.sleepdownschedule.core.identity

import android.app.Notification
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle

private const val AppIconExtra = "com.xiaomanjun.sleepdownschedule.notification.APP_ICON"
private const val PromotedOngoingExtra = "android.requestPromotedOngoing"

/** One style-aware app icon for both the notification header and promoted SystemUI surface. */
internal fun Notification.Builder.applyAppNotificationIcon(
    context: Context,
    iconResId: Int = currentLiveUpdateIconResId(context)
): Notification.Builder = apply {
    setSmallIcon(iconResId)
    setLargeIcon(null as Icon?)
    extras.remove(Notification.EXTRA_LARGE_ICON)
    extras.remove(Notification.EXTRA_LARGE_ICON_BIG)
    extras.putBoolean(AppIconExtra, true)
    setColor(Notification.COLOR_DEFAULT)
}

internal fun Notification.usesAppNotificationIcon(): Boolean =
    extras.getBoolean(AppIconExtra) || extras.getBoolean(PromotedOngoingExtra)

internal fun Icon?.matchesAppNotificationIcon(context: Context, iconResId: Int): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && this != null &&
        type == Icon.TYPE_RESOURCE && resId == iconResId && resPackage == context.packageName

internal fun Notification.withUpdatedAppNotificationIcon(
    context: Context,
    iconResId: Int,
    iconExtras: Bundle?
): Notification? {
    if (iconExtras == null && smallIcon.matchesAppNotificationIcon(context, iconResId) && getLargeIcon() == null) {
        return null
    }
    return Notification.Builder.recoverBuilder(context, clone())
        .applyAppNotificationIcon(context, iconResId)
        .apply { if (iconExtras != null) addExtras(iconExtras) }
        .setOnlyAlertOnce(true)
        .build()
}
