package com.xiaomanjun.sleepdownschedule.core.performance

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit

enum class AppMaterialLevel { QUALITY, PERFORMANCE }

/** Device-wide rendering preference; schedule appearance values remain intact. */
object AppMaterialPreferences {
    private const val Prefs = "app_material_preferences"
    var level by mutableStateOf(AppMaterialLevel.QUALITY)
        private set
    val isPerformance: Boolean get() = level == AppMaterialLevel.PERFORMANCE

    fun load(context: Context) {
        level = if (context.getSharedPreferences(Prefs, Context.MODE_PRIVATE)
                .getString("level", null) == AppMaterialLevel.PERFORMANCE.name)
            AppMaterialLevel.PERFORMANCE else AppMaterialLevel.QUALITY
    }

    fun setLevel(context: Context, value: AppMaterialLevel) {
        context.getSharedPreferences(Prefs, Context.MODE_PRIVATE).edit { putString("level", value.name) }
        level = value
    }
}
