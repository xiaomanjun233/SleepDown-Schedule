package com.xiaomanjun.sleepdownschedule

import com.xiaomanjun.sleepdownschedule.feature.reminder.LiveUpdatePayload
import com.xiaomanjun.sleepdownschedule.feature.reminder.LiveUpdatePayloadCodec
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
        LiveUpdatePayloadCodec.payloadFromIntent(this)

    private fun storePayload(payload: LiveUpdatePayload) =
        LiveUpdatePayloadCodec.storePayload(
            getSharedPreferences(LiveUpdatePayload.PREFS, MODE_PRIVATE), payload
        )

    private fun restorePayload(): LiveUpdatePayload? =
        LiveUpdatePayloadCodec.restorePayload(getSharedPreferences(LiveUpdatePayload.PREFS, MODE_PRIVATE))

    private fun clearStoredPayload() {
        activePayload = null
        getSharedPreferences(LiveUpdatePayload.PREFS, MODE_PRIVATE).edit {clear()}
    }
}
