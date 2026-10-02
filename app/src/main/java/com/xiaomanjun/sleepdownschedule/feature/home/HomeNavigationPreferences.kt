package com.xiaomanjun.sleepdownschedule.feature.home

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** An app-wide presentation choice, independent of any schedule's data or default start page. */
internal object HomeNavigationPreferences {
    private const val PreferencesName = "home_navigation_preferences"
    private const val ParallelKey = "parallel_navigation"
    private val revision = MutableStateFlow(0L)
    val changes = revision.asStateFlow()

    fun isParallel(context: Context): Boolean =
        context.applicationContext.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
            .getBoolean(ParallelKey, false)

    fun setParallel(context: Context, enabled: Boolean) {
        context.applicationContext.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
            .edit().putBoolean(ParallelKey, enabled).apply()
        revision.value += 1
    }
}
