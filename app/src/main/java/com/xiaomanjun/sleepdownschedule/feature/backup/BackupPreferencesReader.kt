package com.xiaomanjun.sleepdownschedule.feature.backup

import com.xiaomanjun.sleepdownschedule.feature.importing.*

import com.xiaomanjun.sleepdownschedule.*
import com.xiaomanjun.sleepdownschedule.feature.agent.*

import com.xiaomanjun.sleepdownschedule.core.identity.AppIconManager

import android.content.Context
import java.io.File

data class BackupPreferencesExport(
    val preferences: BackupPreferences,
    val additionalAssets: List<BackupAsset> = emptyList()
)

/**
 * Read-only adapter for non-Room user preferences. It exports only protocol-safe fields; API keys
 * and other credential material are never loaded into the result.
 */
object BackupPreferencesReader {
    private const val HistoryContextDirectory = "ai_import_history"
    private val safeHistoryId = Regex("[A-Za-z0-9_-]+")

    fun read(
        context: Context,
        scheduleStableIdsByRoomId: Map<Int, String>
    ): BackupPreferencesExport {
        val histories = AiImportHistoryStore.loadForBackup(context)
        val historyIds = histories.associate { entry ->
            entry.id to BackupStableId.new(BackupStableId.HISTORY_PREFIX)
        }
        require(historyIds.size == histories.size) { "AI import history ID 重复" }

        val historyAssets = mutableListOf<BackupAsset>()
        val backupHistories = histories.map { entry ->
            val contextAssetId = historyContextAsset(context, entry, historyIds.getValue(entry.id))
                ?.also { historyAssets += it }
                ?.assetId
            BackupAiImportHistoryEntry(
                id = historyIds.getValue(entry.id),
                createdAt = entry.createdAt,
                title = entry.title,
                prompt = entry.prompt,
                sourceSummary = entry.sourceSummary,
                payload = entry.payload,
                contextAssetId = contextAssetId
            )
        }

        return BackupPreferencesExport(
            preferences = BackupPreferences(
                preferencesVersion = BackupFormatV1.PREFERENCES_VERSION,
                appIcon = AppIconManager.backupPreferences(context),
                dayAgent = DayAgentPreferences.backupPreferences(context, scheduleStableIdsByRoomId),
                aiImport = AiImportSettingsStore.exportForBackup(context),
                aiImportHistoryRetentionDays = AiImportHistoryStore.retentionDays(context),
                aiImportHistory = backupHistories
            ),
            additionalAssets = historyAssets
        )
    }

    private fun historyContextAsset(
        context: Context,
        entry: AiImportHistoryEntry,
        ownerId: String
    ): BackupAsset? {
        val safeId = entry.id.takeIf { safeHistoryId.matches(it) }
        val file = safeId?.let { File(context.filesDir, "$HistoryContextDirectory/$it.json") }
        val exists = file?.isFile == true
        if (!exists && entry.context == null) return null
        val bytes = file?.takeIf(File::isFile)?.let { runCatching { it.readBytes() }.getOrNull() }
        return BackupAsset(
            assetId = BackupStableId.new(BackupStableId.ASSET_PREFIX),
            category = BackupAssetCategory.OTHER,
            purpose = BackupAssetPurpose.AI_IMPORT_HISTORY_CONTEXT,
            mediaType = "application/json",
            bytes = bytes,
            optional = true,
            ownerId = ownerId,
            missingReason = if (bytes == null) "AI 导入历史上下文文件不可读取" else null
        )
    }
}
