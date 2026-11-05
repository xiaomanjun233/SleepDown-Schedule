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
    // Keep the published protocol names while opening the corresponding full-screen page.
    "ABOUT" -> SettingsPage.About
    "CHANGELOG" -> SettingsPage.Changelog
    "DOWNLOAD" -> SettingsPage.SoftwareUpdate
    "DONATE" -> SettingsPage.Donate
    "PRIVACY_POLICY" -> SettingsPage.PrivacyPolicy
    else -> null
}

internal const val BackupPreviewUriExtra = "backup_preview_uri"
internal const val EduAdapterExtra = "edu_adapter"
