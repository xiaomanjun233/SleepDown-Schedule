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
import java.time.LocalTime

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
                val start = payload.startTime()
                val reachedStart = start == null ||
                    !LocalTime.now().isBefore(start)
                if (reachedStart && !payload.isPreview()) {
                    clearStoredPayload()
                    NotificationManagerCompat.from(this@LiveUpdateForegroundService)
                        .cancel(NotificationScheduler.liveUpdateId())
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    break
                }
                if (!NotificationScheduler.canPostNotifications(this@LiveUpdateForegroundService)) {
                    Log.w("SleepDownLiveUpdate", "stop live update: POST_NOTIFICATIONS denied")
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
                // Align updates with the wall-clock minute boundary. The native
                // chronometer keeps ticking in SystemUI between these refreshes.
                val now = System.currentTimeMillis()
                delay((60_000L - now % 60_000L + 150L).coerceAtLeast(1_000L))
            }
        }
    }

    private fun Intent.toLiveUpdatePayload(): LiveUpdatePayload? {
        val name = getStringExtra(NotificationScheduler.EXTRA_LIVE_UPDATE_NAME)
            ?.takeIf { it.isNotBlank() } ?: return null
        val timeText = getStringExtra(NotificationScheduler.EXTRA_LIVE_UPDATE_TIME)
            ?.takeIf { it.isNotBlank() } ?: return null
        return LiveUpdatePayload(
            name = name,
            timeText = timeText,
            location = getStringExtra(NotificationScheduler.EXTRA_LIVE_UPDATE_LOCATION).orEmpty(),
            showActions = getBooleanExtra(NotificationScheduler.EXTRA_LIVE_UPDATE_ACTIONS, true),
            muteKey = getStringExtra(NotificationScheduler.EXTRA_LIVE_UPDATE_MUTE_KEY).orEmpty(),
            muteUntil = getStringExtra(NotificationScheduler.EXTRA_LIVE_UPDATE_MUTE_UNTIL).orEmpty(),
            chipTextMode = runCatching {
                LiveUpdateChipTextMode.valueOf(
                    getStringExtra(NotificationScheduler.EXTRA_LIVE_UPDATE_CHIP_MODE)
                        ?: LiveUpdateChipTextMode.LOCATION.name
                )
            }.getOrDefault(LiveUpdateChipTextMode.LOCATION)
        )
    }

    private fun storePayload(payload: LiveUpdatePayload) {
        getSharedPreferences(LiveUpdatePayload.PREFS, MODE_PRIVATE).edit {
                putString("name", payload.name)
                .putString("time", payload.timeText)
                .putString("location", payload.location)
                .putBoolean("actions", payload.showActions)
                .putString("mute_key", payload.muteKey)
                .putString("mute_until", payload.muteUntil)
                .putString("chip_mode", payload.chipTextMode.name)
            }
    }

    private fun restorePayload(): LiveUpdatePayload? {
        val prefs = getSharedPreferences(LiveUpdatePayload.PREFS, MODE_PRIVATE)
        val name = prefs.getString("name", null)?.takeIf { it.isNotBlank() } ?: return null
        val time = prefs.getString("time", null)?.takeIf { it.isNotBlank() } ?: return null
        return LiveUpdatePayload(
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
            }.getOrDefault(LiveUpdateChipTextMode.LOCATION)
        )
    }

    private fun clearStoredPayload() {
        activePayload = null
        getSharedPreferences(LiveUpdatePayload.PREFS, MODE_PRIVATE).edit {clear()}
    }
}
