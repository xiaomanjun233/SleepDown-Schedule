package com.xiaomanjun.sleepdownschedule.feature.reminder

import android.content.Context
import java.time.LocalTime

internal data class LiveUpdatePreferencesSnapshot(
    val duringClassEnabled: Boolean,
    val breakStatusEnabled: Boolean,
    val tomorrowReminderEnabled: Boolean,
    val tomorrowReminderTime: LocalTime
)

/** Global realtime-activity preferences that do not belong to one schedule profile. */
internal object LiveUpdatePreferences {
    private const val PreferencesName = "live_update_preferences"
    private const val DuringClassKey = "during_class_enabled"
    private const val BreakStatusKey = "break_status_enabled"
    private const val TomorrowReminderKey = "tomorrow_reminder_enabled"
    private const val TomorrowReminderTimeKey = "tomorrow_reminder_time"
    private val DefaultTomorrowReminderTime = LocalTime.of(22, 0)

    fun read(context: Context): LiveUpdatePreferencesSnapshot {
        val preferences = preferences(context)
        return LiveUpdatePreferencesSnapshot(
            duringClassEnabled = preferences.getBoolean(DuringClassKey, true),
            breakStatusEnabled = preferences.getBoolean(BreakStatusKey, true),
            tomorrowReminderEnabled = preferences.getBoolean(TomorrowReminderKey, true),
            tomorrowReminderTime = preferences.getString(
                TomorrowReminderTimeKey,
                DefaultTomorrowReminderTime.toString()
            )?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
                ?: DefaultTomorrowReminderTime
        )
    }

    fun setDuringClassEnabled(context: Context, enabled: Boolean) {
        preferences(context).edit().putBoolean(DuringClassKey, enabled).apply()
    }

    fun setBreakStatusEnabled(context: Context, enabled: Boolean) {
        preferences(context).edit().putBoolean(BreakStatusKey, enabled).apply()
    }

    fun setTomorrowReminderEnabled(context: Context, enabled: Boolean) {
        preferences(context).edit().putBoolean(TomorrowReminderKey, enabled).apply()
    }

    fun setTomorrowReminderTime(context: Context, time: LocalTime) {
        preferences(context).edit().putString(TomorrowReminderTimeKey, time.toString()).apply()
    }

    private fun preferences(context: Context) = context.applicationContext.getSharedPreferences(
        PreferencesName,
        Context.MODE_PRIVATE
    )
}
