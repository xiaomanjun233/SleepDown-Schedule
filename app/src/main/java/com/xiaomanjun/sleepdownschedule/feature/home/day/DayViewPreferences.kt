package com.xiaomanjun.sleepdownschedule.feature.home.day

import android.content.Context

internal enum class DayViewMode {
    STANDARD,
    TWO_DAY
}

internal object DayViewPreferences {
    private const val PreferencesName = "day_view_preferences"
    private const val ModeKey = "day_view_mode"

    fun mode(context: Context, legacyTwoDay: Boolean = false): DayViewMode {
        val preferences = preferences(context)
        if (!preferences.contains(ModeKey) && legacyTwoDay) {
            preferences.edit().putString(ModeKey, DayViewMode.TWO_DAY.name).apply()
            return DayViewMode.TWO_DAY
        }
        return preferences.getString(ModeKey, DayViewMode.STANDARD.name)
            ?.let { stored -> runCatching { DayViewMode.valueOf(stored) }.getOrNull() }
            ?: DayViewMode.STANDARD
    }

    fun setMode(context: Context, mode: DayViewMode) {
        preferences(context).edit().putString(ModeKey, mode.name).apply()
    }

    private fun preferences(context: Context) =
        context.applicationContext.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
}
