package com.xiaomanjun.sleepdownschedule

import com.xiaomanjun.sleepdownschedule.feature.reminder.LiveUpdatePayload
import com.xiaomanjun.sleepdownschedule.feature.reminder.NotificationScheduler
import com.xiaomanjun.sleepdownschedule.feature.experimental.XiaomiSuperIsland

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
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
    // Serialize the optional refresh with service start/stop and event-driven notification updates.
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var refreshJob: Job? = null
    private var activePayload: LiveUpdatePayload? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            NotificationScheduler.ACTION_STOP_LIVE_UPDATE_SERVICE -> {
                clearStoredPayload()
                refreshJob?.cancel()
                stopForeground(STOP_FOREGROUND_REMOVE)
                NotificationScheduler.cancelLiveUpdateNotifications(this)
                stopSelf()
            }
            else -> {
                if (XiaomiSuperIsland.isEnabled(this)) {
                    // Focus notifications are posted directly. A queued service start must not
                    // replace the island with a foreground, ongoing notification.
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return START_NOT_STICKY
                }
                NotificationScheduler.createChannel(this)
                val payload = intent?.toLiveUpdatePayload() ?: restorePayload()
                val renderedAtMillis = System.currentTimeMillis()
                val notification = payload?.buildNotification(this)
                    ?: NotificationScheduler.notificationFromIntent(intent ?: Intent())
                if (notification != null && payload != null) {
                    activePayload = payload
                    storePayload(payload)
                    NotificationScheduler.postLiveUpdateNotification(this, notification) { id, value -> startForeground(id, value) }
                    startRefreshLoop(renderedAtMillis)
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

    private fun startRefreshLoop(initialRenderedAtMillis: Long) {
        refreshJob?.cancel()
        refreshJob = serviceScope.launch {
            // onStartCommand already posted this payload with startForeground.
            var firstFrame = true
            var renderedAtMillis = initialRenderedAtMillis
            while (isActive) {
                if (XiaomiSuperIsland.isEnabled(this@LiveUpdateForegroundService)) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    break
                }
                val payload = activePayload ?: break
                if (payload.shouldStop()) {
                    clearStoredPayload()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    NotificationScheduler.cancelLiveUpdateNotifications(this@LiveUpdateForegroundService)
                    stopSelf()
                    NotificationScheduler.requestRefresh(this@LiveUpdateForegroundService)
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
                    if (!firstFrame) {
                        renderedAtMillis = System.currentTimeMillis()
                        val notification = payload.buildNotification(this@LiveUpdateForegroundService)
                        NotificationScheduler.postLiveUpdateNotification(this@LiveUpdateForegroundService, notification) { id, value ->
                            startForeground(id, value)
                        }
                    }
                } catch (securityException: SecurityException) {
                    Log.w("SleepDownLiveUpdate", "stop live update: notification permission revoked", securityException)
                    clearStoredPayload()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    break
                }
                firstFrame = false
                val now = System.currentTimeMillis()
                // Schedule from the frame we just built. If notification posting crosses a
                // phase boundary, the delay below becomes 1ms instead of skipping that phase.
                val nextRefresh = payload.nextRefreshAtMillis(renderedAtMillis)
                if (nextRefresh == null) {
                    // Building/posting may itself cross expiry. Run the cleanup above rather
                    // than leave an expired foreground notification without another callback.
                    if (payload.shouldStop(now)) continue
                    break
                }
                // Honor second-precision custom times and expiry instead of waiting for the
                // next wall-clock minute. Text and progress share this update; the matching
                // boundary alarm still owns CPU wakeups when the device is asleep.
                delay((nextRefresh - now).coerceAtLeast(1L))
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
