package com.xiaomanjun.sleepdownschedule.model

import com.xiaomanjun.sleepdownschedule.*
import kotlinx.serialization.Serializable

@Serializable
data class AppIconPreferencesSnapshot(
    val mode: String,
    val style: String = "KANBAN",
    val followsSystemDarkMode: Boolean,
    val darkTheme: Boolean
)
