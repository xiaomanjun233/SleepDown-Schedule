package com.xiaomanjun.sleepdownschedule

import com.xiaomanjun.sleepdownschedule.feature.reminder.NotificationScheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.time.LocalTime

class CourseAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        NotificationScheduler.withShortWakeLock(context, "course_alarm") {
            NotificationScheduler.createChannel(context)
            val name = intent.getStringExtra("courseName") ?: "课程"
            val location = intent.getStringExtra("location").orEmpty()
            val timeText = intent.getStringExtra("timeText").orEmpty()
            val mode = runCatching { NotificationMode.valueOf(intent.getStringExtra("notificationMode") ?: NotificationMode.STANDARD.name) }.getOrDefault(NotificationMode.STANDARD)
            val showActions = intent.getBooleanExtra("liveUpdateActionsEnabled", true)
            val chipTextMode = runCatching { LiveUpdateChipTextMode.valueOf(intent.getStringExtra("liveUpdateChipTextMode") ?: LiveUpdateChipTextMode.LOCATION.name) }.getOrDefault(LiveUpdateChipTextMode.LOCATION)
            val muteKey = intent.getStringExtra("muteKey").orEmpty()
            val muteUntil = intent.getStringExtra("muteUntil").orEmpty()
            val startTime = runCatching { LocalTime.parse(timeText.substringBefore("-").trim()) }.getOrNull()
            if (mode == NotificationMode.LIVE_UPDATE && startTime != null && !LocalTime.now().isBefore(startTime)) {
                Log.d("SleepDownLiveUpdate", "skip alarm live update: course already started name=$name, start=$startTime")
                return@withShortWakeLock
            }
            if (!NotificationScheduler.canPostNotifications(context)) {
                return@withShortWakeLock
            }
            val notification = if (mode == NotificationMode.LIVE_UPDATE) {
                null
            } else {
                NotificationCompat.Builder(context, NotificationScheduler.channelId())
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentTitle("快上课了：$name")
                    .setContentText(if (location.isBlank()) "请准备上课" else "地点：$location")
                    .setAutoCancel(true)
                    .build()
            }
            if (mode == NotificationMode.LIVE_UPDATE) {
                Log.d("SleepDownLiveUpdate", "alarm receiver live update: course=$name, chip=$chipTextMode")
                NotificationScheduler.startLiveUpdateService(
                    context = context,
                    name = name,
                    timeText = timeText,
                    location = location,
                    showActions = showActions,
                    muteKey = muteKey,
                    muteUntil = muteUntil,
                    chipTextMode = chipTextMode
                )
            } else {
                val notificationId = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
                try {
                    NotificationManagerCompat.from(context).notify(notificationId, requireNotNull(notification))
                } catch (securityException: SecurityException) {
                    Log.w("SleepDownLiveUpdate", "skip course notification: permission revoked", securityException)
                }
            }
        }
    }
}
