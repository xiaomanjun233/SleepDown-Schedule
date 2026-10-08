package com.xiaomanjun.sleepdownschedule.feature.reminder

import android.content.Context
import com.xiaomanjun.sleepdownschedule.domain.schedule.CourseQuietSettings
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** One app-wide setting, shared across schedule changes; never keyed by a schedule ID. */
internal object CourseQuietPreferences {
    private val json = Json { ignoreUnknownKeys = true }
    private fun preferences(context: Context) = context.applicationContext
        .getSharedPreferences("course_quiet_settings", Context.MODE_PRIVATE)

    fun read(context: Context): CourseQuietSettings = preferences(context).getString("settings", null)
        ?.let { json.decodeFromString<CourseQuietSettings>(it) }?.also { it.validate() }
        ?: CourseQuietSettings()

    fun write(context: Context, settings: CourseQuietSettings) {
        settings.validate()
        check(preferences(context).edit().putString("settings", json.encodeToString(settings)).commit()) {
            "课程勿扰设置保存失败"
        }
    }
}
