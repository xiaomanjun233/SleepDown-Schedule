package com.xiaomanjun.sleepdownschedule.feature.home.week

import android.content.Context

internal enum class WeekViewStyle {
    NORMAL,
    BOUNDLESS
}

internal object WeekViewPreferences {
    private const val PreferencesName = "week_view_preferences"
    private const val StyleKey = "week_view_style"

    fun style(context: Context): WeekViewStyle {
        val preferences = preferences(context)
        return preferences.getString(StyleKey, WeekViewStyle.BOUNDLESS.name)
            ?.let { stored -> runCatching { WeekViewStyle.valueOf(stored) }.getOrNull() }
            ?: WeekViewStyle.BOUNDLESS
    }

    fun setStyle(context: Context, style: WeekViewStyle) {
        preferences(context).edit().putString(StyleKey, style.name).apply()
    }

    private fun preferences(context: Context) =
        context.applicationContext.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
}
