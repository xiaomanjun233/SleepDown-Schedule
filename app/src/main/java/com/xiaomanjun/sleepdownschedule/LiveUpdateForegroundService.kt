package com.xiaomanjun.sleepdownschedule

import com.xiaomanjun.sleepdownschedule.feature.reminder.LiveUpdatePayload
import com.xiaomanjun.sleepdownschedule.feature.reminder.NotificationScheduler

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class LiveUpdateForegroundService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var refreshJob: Job? = null
    private var activePayload: LiveUpdatePayload? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            NotificationScheduler.ACTION_STOP_LIVE_UPDATE_SERVICE -> {
                clearStoredPayload()
                refreshJob?.cancel()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            else -> {
                NotificationScheduler.createChannel(this)
                val payload = intent?.toLiveUpdatePayload() ?: restorePayload()
                val notification = payload?.buildNotification(this)
                    ?: NotificationScheduler.notificationFromIntent(intent ?: Intent())
                if (notification != null && payload != null) {
                    activePayload = payload
                    storePayload(payload)
                    startForeground(NotificationScheduler.liveUpdateId(), notification)
                    startMinuteRefreshLoop()
                    Log.d("SleepDownLiveUpdate", "foreground service started")
                } else {
                    Log.w("SleepDownLiveUpdate", "foreground service missing notification")
                    clearStoredPayload()
                    stopSelf()
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        refreshJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startMinuteRefreshLoop() {
        refreshJob?.cancel()
        refreshJob = serviceScope.launch {
            while (isActive) {
                val payload = activePayload ?: break
                if (payload.shouldStop()) {
                    clearStoredPayload()
                    NotificationManagerCompat.from(this@LiveUpdateForegroundService)
                        .cancel(NotificationScheduler.liveUpdateId())
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    break
                }
                if (!NotificationScheduler.canPostNotifications(this@LiveUpdateForegroundService)) {
                    Log.w("SleepDownLiveUpdate", "stop live update: notification delivery unavailable")
                    clearStoredPayload()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    break
                }
                try {
                    NotificationManagerCompat.from(this@LiveUpdateForegroundService).notify(
                        NotificationScheduler.liveUpdateId(),
                        payload.buildNotification(this@LiveUpdateForegroundService)
                    )
                } catch (securityException: SecurityException) {
                    Log.w("SleepDownLiveUpdate", "stop live update: notification permission revoked", securityException)
                    clearStoredPayload()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    break
                }
                // Align the compact "X分钟" text with the wall-clock minute boundary. SystemUI's
                // chronometer is intentionally not used because it replaces the promoted chip.
                val now = System.currentTimeMillis()
                delay((60_000L - now % 60_000L + 150L).coerceAtLeast(1_000L))
            }
        }
    }

    private fun Intent.toLiveUpdatePayload(): LiveUpdatePayload? =
        NotificationScheduler.payloadFromIntent(this)

    private fun storePayload(payload: LiveUpdatePayload) {
        getSharedPreferences(LiveUpdatePayload.PREFS, MODE_PRIVATE).edit {
                putString("kind", payload.kind.name)
                .putString("name", payload.name)
                .putString("time", payload.timeText)
                .putString("location", payload.location)
                .putBoolean("actions", payload.showActions)
                .putString("mute_key", payload.muteKey)
                .putString("mute_until", payload.muteUntil)
                .putString("chip_mode", payload.chipTextMode.name)
                .putString("segments", payload.segments.joinToString(";") { "${it.startAtMillis}:${it.endAtMillis}" })
                .putBoolean("during_class", payload.duringClassEnabled)
                .putBoolean("break_status", payload.breakStatusEnabled)
                .putLong("expires_at", payload.expiresAtMillis)
                .putInt("tomorrow_count", payload.tomorrowCourseCount)
            }
    }

    private fun restorePayload(): LiveUpdatePayload? {
        val prefs = getSharedPreferences(LiveUpdatePayload.PREFS, MODE_PRIVATE)
        val name = prefs.getString("name", null)?.takeIf { it.isNotBlank() } ?: return null
        val time = prefs.getString("time", null)?.takeIf { it.isNotBlank() } ?: return null
        return LiveUpdatePayload(
            kind = runCatching {
                com.xiaomanjun.sleepdownschedule.feature.reminder.LiveUpdateKind.valueOf(
                    prefs.getString("kind", com.xiaomanjun.sleepdownschedule.feature.reminder.LiveUpdateKind.COURSE.name)
                        ?: com.xiaomanjun.sleepdownschedule.feature.reminder.LiveUpdateKind.COURSE.name
                )
            }.getOrDefault(com.xiaomanjun.sleepdownschedule.feature.reminder.LiveUpdateKind.COURSE),
            name = name,
            timeText = time,
            location = prefs.getString("location", "").orEmpty(),
            showActions = prefs.getBoolean("actions", true),
            muteKey = prefs.getString("mute_key", "").orEmpty(),
            muteUntil = prefs.getString("mute_until", "").orEmpty(),
            chipTextMode = runCatching {
                LiveUpdateChipTextMode.valueOf(
                    prefs.getString("chip_mode", LiveUpdateChipTextMode.LOCATION.name)
                        ?: LiveUpdateChipTextMode.LOCATION.name
                )
            }.getOrDefault(LiveUpdateChipTextMode.LOCATION),
            segments = prefs.getString("segments", "").orEmpty().split(';').mapNotNull { encoded ->
                val start = encoded.substringBefore(':').toLongOrNull() ?: return@mapNotNull null
                val end = encoded.substringAfter(':', "").toLongOrNull() ?: return@mapNotNull null
                com.xiaomanjun.sleepdownschedule.feature.reminder.LiveUpdateSegment(start, end)
                    .takeIf { end > start }
            },
            duringClassEnabled = prefs.getBoolean("during_class", false),
            breakStatusEnabled = prefs.getBoolean("break_status", true),
            expiresAtMillis = prefs.getLong("expires_at", 0L),
            tomorrowCourseCount = prefs.getInt("tomorrow_count", 0)
        )
    }

    private fun clearStoredPayload() {
        activePayload = null
        getSharedPreferences(LiveUpdatePayload.PREFS, MODE_PRIVATE).edit {clear()}
    }
}
