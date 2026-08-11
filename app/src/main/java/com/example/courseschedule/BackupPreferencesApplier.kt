package com.example.courseschedule

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
        AiImportHistoryStore.applyBackup(
            context = context,
            entries = preferences.aiImportHistory,
            contextFilesByAssetId = finalAssetFilesById,
            retentionDays = preferences.aiImportHistoryRetentionDays
        )
    }
}
