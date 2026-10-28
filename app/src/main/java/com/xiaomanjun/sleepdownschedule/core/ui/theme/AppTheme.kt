package com.xiaomanjun.sleepdownschedule.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import com.xiaomanjun.sleepdownschedule.model.ScheduleConfigEntity

@Composable
fun appUsesDarkTheme(config: ScheduleConfigEntity): Boolean {
    val systemDark = isSystemInDarkTheme()
    return if (config.followSystemDarkMode) systemDark else config.darkMode
}
