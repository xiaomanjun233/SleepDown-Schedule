package com.xiaomanjun.sleepdownschedule

import com.xiaomanjun.sleepdownschedule.feature.reminder.NotificationScheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalTime

class CourseAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == NotificationScheduler.ACTION_REFRESH_COURSE_ALARMS) {
            val pending = goAsync()
            val app = context.applicationContext as CourseScheduleApp
            app.applicationScope.launch(Dispatchers.IO) {
                try {
                    val snapshot = app.repository.activeSnapshot()
                    NotificationScheduler.refreshToday(
                        context = app,
                        courses = snapshot.courses,
                        config = snapshot.config,
                        periods = snapshot.periods,
                        forceReschedule = true
                    )
                } finally {
                    pending.finish()
                }
            }
            return
        }
        NotificationScheduler.withShortWakeLock(context, "course_alarm") {
            NotificationScheduler.createChannel(context)
            val payload = NotificationScheduler.payloadFromIntent(intent)
            val name = payload?.name ?: intent.getStringExtra("courseName") ?: "课程"
            val location = payload?.location ?: intent.getStringExtra("location").orEmpty()
            val timeText = payload?.timeText ?: intent.getStringExtra("timeText").orEmpty()
            val mode = runCatching { NotificationMode.valueOf(intent.getStringExtra("notificationMode") ?: NotificationMode.STANDARD.name) }.getOrDefault(NotificationMode.STANDARD)
            val startTime = runCatching { LocalTime.parse(timeText.substringBefore("-").trim()) }.getOrNull()
            if (
                mode == NotificationMode.LIVE_UPDATE &&
                payload?.segments.isNullOrEmpty() &&
                startTime != null &&
                !LocalTime.now().isBefore(startTime)
            ) {
                Log.d("SleepDownLiveUpdate", "skip alarm live update: course already started name=$name, start=$startTime")
                return@withShortWakeLock
            }
            if (!NotificationScheduler.canPostNotifications(context)) {
                Log.w("SleepDownLiveUpdate", "skip alarm: notification delivery unavailable")
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
                val livePayload = payload ?: return@withShortWakeLock
                if (livePayload.shouldStop()) {
                    Log.d("SleepDownLiveUpdate", "alarm boundary reached payload expiry key=${livePayload.muteKey}")
                    NotificationManagerCompat.from(context).cancel(NotificationScheduler.liveUpdateId())
                    NotificationScheduler.stopLiveUpdateService(context)
                    return@withShortWakeLock
                }
                if (NotificationScheduler.isPayloadMuted(context, livePayload)) {
                    Log.d("SleepDownLiveUpdate", "skip muted alarm payload key=${livePayload.muteKey}")
                    return@withShortWakeLock
                }
                Log.d("SleepDownLiveUpdate", "alarm receiver live update: kind=${livePayload.kind}, name=$name")
                NotificationScheduler.startLiveUpdateService(context, livePayload)
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
