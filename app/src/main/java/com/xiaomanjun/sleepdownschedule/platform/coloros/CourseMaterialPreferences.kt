package com.xiaomanjun.sleepdownschedule.platform.coloros

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.NativeCourseCardMode

// Persisted IDs are retained for upgrades from the retired native material implementation.
private const val CourseMaterialPreference = "coloros_course_material"
private const val CourseTintStrength = "tint_strength"
private const val CourseBlurScale = "blur_radius_scale"

@Composable
internal fun rememberCourseCardMaterialPreferences(blurRadius: Float): NativeCourseCardMode {
    val host = LocalContext.current.applicationContext
    val preferences = remember(host) { host.getSharedPreferences(CourseMaterialPreference, Context.MODE_PRIVATE) }
    // Keep the persisted tint and blur scale shared with the old renderer.
    val enabled = remember { mutableStateOf(true) }
    val tint = remember(preferences) { mutableFloatStateOf(preferences.getFloat(CourseTintStrength, 0.5f).coerceIn(0f, 1f)) }
    val blurScale = remember(preferences) { mutableFloatStateOf(preferences.getFloat(CourseBlurScale, 1f).coerceIn(0f, 2f)) }
    DisposableEffect(preferences) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
            if (key == CourseTintStrength) tint.floatValue = prefs.getFloat(key, 0.5f).coerceIn(0f, 1f)
            if (key == CourseBlurScale) blurScale.floatValue = prefs.getFloat(key, 1f).coerceIn(0f, 2f)
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return remember(preferences, blurRadius) { NativeCourseCardMode(enabled,
        onEnabledChange = {},
        tintState = tint,
        blurScaleState = blurScale,
        defaultBlurRadiusPx = blurRadius,
        onTintStrengthChange = { value, commit ->
            tint.floatValue = value.coerceIn(0f, 1f)
            if (commit) preferences.edit().putFloat(CourseTintStrength, tint.floatValue).apply()
        },
        onBlurRadiusScaleChange = { value, commit ->
            blurScale.floatValue = value.coerceIn(0f, 2f)
            if (commit) preferences.edit().putFloat(CourseBlurScale, blurScale.floatValue).apply()
        },
        onPreviewFinished = {
            tint.floatValue = preferences.getFloat(CourseTintStrength, 0.5f).coerceIn(0f, 1f)
            blurScale.floatValue = preferences.getFloat(CourseBlurScale, 1f).coerceIn(0f, 2f)
        }) }
}
