package com.xiaomanjun.sleepdownschedule.app.ui

import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.xiaomanjun.sleepdownschedule.feature.settings.SettingsPage

sealed interface Screen {
    data object Home : Screen
    data object Config : Screen
}

internal fun agentSettingsPage(value: String?): SettingsPage? = when (value) {
    "GENERAL" -> SettingsPage.General
    "LIQUID_GLASS" -> SettingsPage.LiquidGlass
    "WIDGETS" -> SettingsPage.Widgets
    "AI_IMPORT" -> SettingsPage.AiImport
    "DAY_AGENT" -> SettingsPage.DayAgent
    "SCHEDULE" -> SettingsPage.Schedule
    "AUTO_REFRESH_SCHEDULE" -> SettingsPage.AutoRefreshSchedule
    "NOTIFICATIONS" -> SettingsPage.Notifications
    "SCHEDULE_MANAGER" -> SettingsPage.ScheduleManager
    "BACKUP_RESTORE" -> SettingsPage.BackupRestore
    // ABOUT / CHANGELOG / DOWNLOAD are separate entries in the settings root but all land on the
    // same "关于应用" screen, so the agent protocol keeps three names and maps them to one page.
    "ABOUT" -> SettingsPage.Changelog
    "CHANGELOG" -> SettingsPage.Changelog
    "DOWNLOAD" -> SettingsPage.Changelog
    "DONATE" -> SettingsPage.Donate
    "PRIVACY_POLICY" -> SettingsPage.PrivacyPolicy
    else -> null
}

internal const val SettingsDetailPageExtra = "settings_page"
internal const val BackupPreviewUriExtra = "backup_preview_uri"
internal const val EduAdapterExtra = "edu_adapter"
