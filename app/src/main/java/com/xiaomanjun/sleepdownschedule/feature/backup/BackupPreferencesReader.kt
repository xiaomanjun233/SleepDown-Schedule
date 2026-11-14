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
            val contextAssetId = historyContextAsset(context, entry, historyIds.getValue(entry.id), historyAssets)
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
                materialLevel = com.xiaomanjun.sleepdownschedule.core.performance.AppMaterialPreferences.level.name,
                appIcon = AppIconManager.backupPreferences(context),
                dayAgent = DayAgentPreferences.backupPreferences(context, scheduleStableIdsByRoomId),
                aiImport = AiImportSettingsStore.exportForBackup(context),
                aiImportHistoryRetentionDays = AiImportHistoryStore.retentionDays(context),
                aiImportHistory = backupHistories,
                courseQuietSettings = com.xiaomanjun.sleepdownschedule.feature.reminder.CourseQuietPreferences.read(context)
            ),
            additionalAssets = historyAssets
        )
    }

    private fun historyContextAsset(
        context: Context,
        entry: AiImportHistoryEntry,
        ownerId: String,
        images: MutableList<BackupAsset>
    ): BackupAsset? {
        val safeId = entry.id.takeIf { safeHistoryId.matches(it) }
        val file = safeId?.let { File(context.filesDir, "$HistoryContextDirectory/$it.json") }
        val exists = file?.isFile == true
        if (!exists && entry.context == null) return null
        var bytes = file?.takeIf(File::isFile)?.let { runCatching { it.readBytes() }.getOrNull() }
        bytes?.let { raw ->
            val root = org.json.JSONObject(raw.toString(Charsets.UTF_8))
            val references = root.optJSONArray("screenshotPreviews") ?: org.json.JSONArray()
            for (index in 0 until references.length()) {
                val image = references.optJSONObject(index) ?: continue
                val assetId = BackupStableId.new(BackupStableId.ASSET_PREFIX)
                val imageBytes = AiImportHistoryImages.file(checkNotNull(file?.parentFile), entry.id, image)?.readBytes()
                    ?: image.optString("base64").takeIf(String::isNotBlank)?.let {
                        android.util.Base64.decode(it, android.util.Base64.DEFAULT)
                    }
                images += BackupAsset(assetId = assetId, category = BackupAssetCategory.SCHEDULES,
                    purpose = BackupAssetPurpose.AI_IMPORT_SCREENSHOT, mediaType = image.optString("mimeType", "image/jpeg"),
                    bytes = imageBytes, optional = true, ownerId = ownerId,
                    missingReason = if (imageBytes == null) "原始课表图片不可读取" else null)
                image.remove("fileName")
                image.remove("base64")
                image.put("assetId", assetId)
            }
            bytes = root.toString().toByteArray(Charsets.UTF_8)
        }
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
