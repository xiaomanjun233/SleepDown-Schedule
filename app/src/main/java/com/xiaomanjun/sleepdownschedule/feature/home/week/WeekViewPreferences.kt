package com.xiaomanjun.sleepdownschedule.feature.home.week

import android.content.Context
import com.xiaomanjun.sleepdownschedule.BuildConfig

internal enum class WeekViewStyle {
    NORMAL,
    BOUNDLESS
}

internal object WeekViewPreferences {
    private const val PreferencesName = "week_view_preferences"
    private const val StyleKey = "week_view_style"
    private const val IntroShownVersionKey = "week_view_boundless_intro_shown_version"

    fun style(context: Context): WeekViewStyle {
        val preferences = preferences(context)
        return preferences.getString(StyleKey, WeekViewStyle.NORMAL.name)
            ?.let { stored -> runCatching { WeekViewStyle.valueOf(stored) }.getOrNull() }
            ?: WeekViewStyle.NORMAL
    }

    fun setStyle(context: Context, style: WeekViewStyle) {
        preferences(context).edit().putString(StyleKey, style.name).apply()
    }

    /**
     * 首次更新到当前版本时返回 true，用于弹出无界模式切换引导。
     * 以记录过的 versionCode 判断：只对第一次到达的版本提示一次。
     */
    fun shouldShowIntro(context: Context): Boolean {
        return preferences(context)
            .getInt(IntroShownVersionKey, 0) < BuildConfig.VERSION_CODE
    }

    fun markIntroShown(context: Context) {
        preferences(context).edit().putInt(IntroShownVersionKey, BuildConfig.VERSION_CODE).apply()
    }

    private fun preferences(context: Context) =
        context.applicationContext.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
}