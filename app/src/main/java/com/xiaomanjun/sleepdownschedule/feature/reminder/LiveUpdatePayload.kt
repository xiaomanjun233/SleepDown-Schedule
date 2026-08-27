package com.xiaomanjun.sleepdownschedule.feature.reminder

import com.xiaomanjun.sleepdownschedule.*

import android.app.Notification
import android.content.Context
import java.time.LocalTime

internal data class LiveUpdatePayload(
    val name: String,
    val timeText: String,
    val location: String,
    val showActions: Boolean,
    val muteKey: String,
    val muteUntil: String,
    val chipTextMode: LiveUpdateChipTextMode
) {
    companion object {
        const val PREFS = "live_update_service_state"
    }

    fun startTime(): LocalTime? = runCatching {
        LocalTime.parse(timeText.substringBefore("-").trim())
    }.getOrNull()

    fun isPreview(): Boolean = muteKey.startsWith("preview:")

    fun buildNotification(context: Context): Notification =
        NotificationScheduler.liveUpdateNotification(
            context,
            name,
            timeText,
            location,
            showActions,
            muteKey,
            muteUntil,
            chipTextMode
        )
}
