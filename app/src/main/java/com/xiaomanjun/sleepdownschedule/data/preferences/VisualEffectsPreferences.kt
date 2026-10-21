package com.xiaomanjun.sleepdownschedule.data.preferences

import android.content.Context
import android.content.SharedPreferences
import com.xiaomanjun.sleepdownschedule.model.VisualEffectMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal class VisualEffectsPreferences private constructor(private val preferences: SharedPreferences) {
    private val selected = MutableStateFlow(readMode())
    val mode = selected.asStateFlow()

    // SharedPreferences retains listeners weakly; the process-wide owner keeps this one alive.
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == ModeKey || key == null) selected.value = readMode()
    }

    init {
        preferences.registerOnSharedPreferenceChangeListener(listener)
    }

    fun select(mode: VisualEffectMode) {
        preferences.edit().putString(ModeKey, mode.id).apply()
        selected.value = mode
    }

    private fun readMode() = VisualEffectMode.fromId(preferences.getString(ModeKey, null))

    companion object {
        private const val ModeKey = "mode"
        @Volatile private var instance: VisualEffectsPreferences? = null

        fun get(context: Context): VisualEffectsPreferences = instance ?: synchronized(this) {
            instance ?: VisualEffectsPreferences(
                context.applicationContext.getSharedPreferences("visual_effects", Context.MODE_PRIVATE)
            ).also { instance = it }
        }
    }
}
