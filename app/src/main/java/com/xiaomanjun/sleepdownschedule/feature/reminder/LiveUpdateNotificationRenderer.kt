package com.xiaomanjun.sleepdownschedule.feature.reminder

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import com.xiaomanjun.sleepdownschedule.MainActivity
import com.xiaomanjun.sleepdownschedule.LiveUpdateActionReceiver
import com.xiaomanjun.sleepdownschedule.R
import com.xiaomanjun.sleepdownschedule.model.LiveUpdateChipTextMode
import com.xiaomanjun.sleepdownschedule.core.identity.applyAppNotificationIcon
import com.xiaomanjun.sleepdownschedule.feature.experimental.XiaomiSuperIsland
import com.xiaomanjun.sleepdownschedule.feature.reminder.NotificationScheduler.ACTION_CANCEL_LIVE_UPDATE
import com.xiaomanjun.sleepdownschedule.feature.reminder.NotificationScheduler.ACTION_TOGGLE_DND

/** Stateless notification presentation; alarms, services, muting and DND ownership stay outside. */
internal object LiveUpdateNotificationRenderer {
    private const val TAG = "SleepDownLiveUpdate"
    const val CHANNEL_ID = "course_reminders"
    const val EXTRA_LIVE_UPDATE_IDENTITY = "sleepdown.live_update_identity"
    const val EXTRA_ISLAND_PREVIEW = "sleepdown.island_preview"

    fun build(context: Context, payload: LiveUpdatePayload, isDndEnabled: () -> Boolean): android.app.Notification {
        val nowMillis = System.currentTimeMillis()
        val status = payload.statusAt(nowMillis)
        val superIslandSelected = XiaomiSuperIsland.isEnabled(context)
        val notificationIdentity = payload.notificationIdentityAt(nowMillis)
        val placeText = payload.location.ifBlank { "未设置地点" }
        val shortText = when {
            payload.kind == LiveUpdateKind.TOMORROW -> "明日${payload.tomorrowCourseCount}门"
            status.phase == LiveUpdatePhase.BEFORE_CLASS -> liveUpdateChipText(
                payload.chipTextMode,
                payload.name,
                placeText,
                status.minutesToTransition
            )
            status.phase == LiveUpdatePhase.FINISHED -> "已下课"
            else -> liveUpdateCountdownChipText(status.minutesToTransition)
        }
        // Chip text is strictly a compact/island presentation choice. The
        // expanded notification always keeps the same complete course content.
        val titleText = payload.name
        val bodyText = if (payload.kind == LiveUpdateKind.TOMORROW) {
            status.detailText
        } else {
            "${courseCardStatusText(status)} · ${payload.timeText}"
        }
        val expandedText = if (payload.kind == LiveUpdateKind.TOMORROW || payload.location.isBlank()) {
            bodyText
        } else {
            "$bodyText\n$placeText"
        }
        val openAppIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val contentIntent = PendingIntent.getActivity(
            context,
            20260522,
            openAppIntent ?: Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        if (superIslandSelected) {
            XiaomiSuperIsland.ensureChannel(context)
            val expiresAt = when {
                payload.expiresAtMillis > nowMillis -> payload.expiresAtMillis
                payload.isPreview() -> payload.startAtMillis()
                payload.duringClassEnabled -> payload.endAtMillis()
                else -> payload.startAtMillis()
            }
            return Notification.Builder(context, XiaomiSuperIsland.ChannelId)
                .applyAppNotificationIcon(context)
                .setContentTitle(titleText)
                .setContentText(bodyText)
                .setContentIntent(contentIntent)
                .setAutoCancel(false)
                .apply {
                    expiresAt?.takeIf { it > nowMillis }?.let { setTimeoutAfter(it - nowMillis) }
                }
                .build().also { notification ->
                    notification.extras.putString(EXTRA_LIVE_UPDATE_IDENTITY, notificationIdentity)
                    notification.extras.putBoolean(EXTRA_ISLAND_PREVIEW, payload.isPreview())
                    val dndTitle = dndActionTitle(context, isDndEnabled)
                    notification.extras.putBundle("miui.focus.actions", Bundle().apply {
                        putParcelable(XiaomiSuperIsland.DndActionKey,
                            Notification.Action.Builder(
                                Icon.createWithResource(context, R.drawable.ic_moon_light),
                                dndTitle,
                                dndActionPendingIntent(context, payload.muteKey, payload.muteUntil)
                            ).build())
                    })
                    XiaomiSuperIsland.decorate(
                        context, notification, payload, status, shortText.toString(), dndTitle
                    )
                }
        }
        val builder = android.app.Notification.Builder(context, CHANNEL_ID)
        builder
            .applyAppNotificationIcon(context)
            .setContentTitle(titleText)
            .setContentText(bodyText)
            .setStyle(android.app.Notification.BigTextStyle().bigText(expandedText))
            .setContentIntent(contentIntent)
            .setDeleteIntent(
                actionPendingIntent(
                    context,
                    ACTION_CANCEL_LIVE_UPDATE,
                    3,
                    payload.muteKey,
                    payload.muteUntil
                )
            )
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            // User-confirmed ColorOS behavior: the native timer is swallowed by the promoted
            // notification renderer. Keep both the chip and card on explicit minute text.
            .setShowWhen(false)
            .setUsesChronometer(false)
            .setChronometerCountDown(false)
            .setCategory(android.app.Notification.CATEGORY_EVENT)
            .setColor(Notification.COLOR_DEFAULT)
        status.progressPercent?.takeUnless { superIslandSelected }?.let { progress ->
            val countdownLine = if (status.phase == LiveUpdatePhase.BREAK) {
                "还有${status.minutesToTransition}分钟上课"
            } else {
                "还有${status.minutesToTransition}分钟下课"
            }
            val infoLine = "${status.detailText.substringBefore(" · 还有")} · ${payload.timeText}"
            builder
                .setCategory(Notification.CATEGORY_PROGRESS)
                .setContentText("$infoLine\n$countdownLine")
            if (Build.VERSION.SDK_INT >= 36) {
                builder.setStyle(
                    Notification.ProgressStyle()
                        // 参照 llpower 的写法：用单个白色小圆点作为 tracker 图标，
                        // 不再叠加 Point，避免进度条上出现两个重叠的进度图示。
                        .setProgressTrackerIcon(
                            Icon.createWithResource(context, R.drawable.ic_live_dot)
                        )
                        .setProgressSegments(listOf(
                            Notification.ProgressStyle.Segment(100)
                        ))
                        .setProgress(progress)
                )
            } else {
                builder.setStyle(null).setProgress(100, progress, false)
            }
        }
        if (payload.kind == LiveUpdateKind.TOMORROW) {
            builder
                .addAction(android.app.Notification.Action.Builder(
                    Icon.createWithResource(context, R.drawable.ic_close_light),
                    "取消提醒",
                    actionPendingIntent(
                        context,
                        ACTION_CANCEL_LIVE_UPDATE,
                        1,
                        payload.muteKey,
                        payload.muteUntil
                    )
                ).build())
        } else if (payload.showActions && (superIslandSelected || status.progressPercent == null)) {
            val dndTitle = dndActionTitle(context, isDndEnabled)
            builder
                .addAction(android.app.Notification.Action.Builder(
                    Icon.createWithResource(context, R.drawable.ic_close_light),
                    "取消本次提醒",
                    actionPendingIntent(context, ACTION_CANCEL_LIVE_UPDATE, 1, payload.muteKey, payload.muteUntil)
                ).build())
                .addAction(android.app.Notification.Action.Builder(
                    Icon.createWithResource(context, R.drawable.ic_moon_light),
                    dndTitle,
                    dndActionPendingIntent(context, payload.muteKey, payload.muteUntil)
                ).build())
        }
        if (!superIslandSelected) {
            runCatching {
                builder.javaClass
                    .getMethod("setRequestPromotedOngoing", java.lang.Boolean.TYPE)
                    .invoke(builder, true)
                Log.d(TAG, "setRequestPromotedOngoing called")
            }.onFailure {
                Log.w(TAG, "setRequestPromotedOngoing unavailable: ${it.javaClass.simpleName}")
            }
            builder.extras.putBoolean("android.requestPromotedOngoing", true)
            // Plain short text remains visible on ColorOS; never delegate the chip to a chronometer.
            runCatching {
                builder.javaClass
                    .getMethod("setShortCriticalText", CharSequence::class.java)
                    .invoke(builder, shortText)
            }.recoverCatching {
                builder.javaClass
                    .getMethod("setShortCriticalText", String::class.java)
                    .invoke(builder, shortText.toString())
            }
            builder.extras.putCharSequence("android.shortCriticalText", shortText)
        }
        builder.extras.putString(EXTRA_LIVE_UPDATE_IDENTITY, notificationIdentity)
        return builder.build().also { notification ->
            XiaomiSuperIsland.decorate(context, notification, payload, status, shortText.toString())
            val promotable = runCatching {
                notification.javaClass
                    .getMethod("hasPromotableCharacteristics")
                    .invoke(notification) as? Boolean
            }.getOrNull()
            val requested = notification.extras.getBoolean("android.requestPromotedOngoing", false)
            val promotionAllowed = canPostPromotedLiveUpdates(context)
            Log.d(
                TAG,
                "live update built: promotable=$promotable, requested=$requested, " +
                    "promotionAllowed=$promotionAllowed, flags=${notification.flags}, " +
                    "style=${notification.extras.getString("android.template")}"
            )
        }
    }

    private fun courseCardStatusText(status: LiveUpdateStatus): String = when (status.phase) {
        LiveUpdatePhase.BEFORE_CLASS -> if (status.minutesToTransition <= 0) {
            "准备上课"
        } else {
            "还剩${status.minutesToTransition}分钟"
        }
        LiveUpdatePhase.IN_CLASS,
        LiveUpdatePhase.BREAK -> status.detailText
        LiveUpdatePhase.FINISHED -> "已下课"
        LiveUpdatePhase.TOMORROW -> status.statusText
    }

    private fun liveUpdateChipText(
        mode: LiveUpdateChipTextMode,
        courseName: String,
        placeText: String,
        minutesLeft: Int
    ): CharSequence = when (mode) {
        LiveUpdateChipTextMode.COUNTDOWN -> liveUpdateCountdownChipText(minutesLeft)
        LiveUpdateChipTextMode.LOCATION -> placeText
        LiveUpdateChipTextMode.SHORT,
        LiveUpdateChipTextMode.NORMAL -> courseName
    }

    private fun liveUpdateCountdownChipText(minutesLeft: Int): String =
        "${minutesLeft.coerceAtLeast(0)}分钟"

    private fun actionPendingIntent(context: Context, action: String, requestCode: Int, muteKey: String, muteUntil: String): PendingIntent {
        val intent = Intent(context, LiveUpdateActionReceiver::class.java)
            .setAction(action)
            .putExtra("muteKey", muteKey)
            .putExtra("muteUntil", muteUntil)
            .addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        return PendingIntent.getBroadcast(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun dndActionPendingIntent(context: Context, muteKey: String, muteUntil: String): PendingIntent {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager?.isNotificationPolicyAccessGranted == true) {
            return actionPendingIntent(context, ACTION_TOGGLE_DND, 2, muteKey, muteUntil)
        }
        // A notification action must open Settings directly. Routing through a BroadcastReceiver
        // is a notification trampoline and is blocked on modern Android releases.
        val settingsIntent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context,
            20260825,
            settingsIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun dndActionTitle(context: Context, isDndEnabled: () -> Boolean): String {
        val manager = context.getSystemService(NotificationManager::class.java)
        return when {
            manager?.isNotificationPolicyAccessGranted != true -> "授权勿扰"
            isDndEnabled() -> "关闭勿扰"
            else -> "开启勿扰"
        }
    }

    fun canPostPromotedLiveUpdates(context: Context): Boolean? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) return null
        return context.getSystemService(NotificationManager::class.java)
            ?.canPostPromotedNotifications()
            ?: false
    }

}
