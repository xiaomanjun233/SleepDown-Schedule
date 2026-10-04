package com.xiaomanjun.sleepdownschedule.feature.backup

import com.xiaomanjun.sleepdownschedule.feature.importing.*

import com.xiaomanjun.sleepdownschedule.*
import com.xiaomanjun.sleepdownschedule.feature.agent.*

import com.xiaomanjun.sleepdownschedule.core.identity.AppIconManager

import android.content.Context
import java.io.File

/** Applies only the protocol's non-secret preference whitelist after the Room commit. */
object BackupPreferencesApplier {
    fun apply(
        context: Context,
        preferences: BackupPreferences,
        scheduleRoomIdsByStableId: Map<String, Int>,
        finalAssetFilesById: Map<String, File>
    ) {
        preferences.appIcon?.let { AppIconManager.applyBackupPreferences(context, it) }
        preferences.dayAgent?.let {
            DayAgentPreferences.applyBackupPreferences(context, it, scheduleRoomIdsByStableId)
        }
        preferences.aiImport?.let { AiImportSettingsStore.applyBackupPreferences(context, it) }
        if (preferences.savedPeriodSchemes != null) {
            com.xiaomanjun.sleepdownschedule.data.repository.PeriodSchemeLibraryStore.restore(context, preferences.savedPeriodSchemes)
        } else {
            // Legacy backups also replace Room IDs, but must retain the existing global library.
            com.xiaomanjun.sleepdownschedule.data.repository.PeriodSchemeLibraryStore.resetScheduleImports(context)
        }
        preferences.courseQuietSettings?.let {
            com.xiaomanjun.sleepdownschedule.feature.reminder.CourseQuietPreferences.write(context, it)
        }
        AiImportHistoryStore.applyBackup(
            context = context,
            entries = preferences.aiImportHistory,
            contextFilesByAssetId = finalAssetFilesById,
            retentionDays = preferences.aiImportHistoryRetentionDays
        )
    }
}
