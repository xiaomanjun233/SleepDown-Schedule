package com.xiaomanjun.sleepdownschedule

import android.content.Context
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Android-facing read-only export orchestration; it never mutates Room, prefs or source files. */
class BackupExportService(
    context: Context,
    private val database: AppDatabase
) {
    private val appContext = context.applicationContext
    private val sourceReader = BackupAssetSourceReader(appContext)

    suspend fun export(metadata: BackupSourceMetadata = defaultMetadata()): BackupArchive =
        withContext(Dispatchers.IO) {
            val snapshot = BackupRoomSnapshotReader(database).read()
            val idMapping = BackupExportMapper.createIdMapping(snapshot)
            val preferences = BackupPreferencesReader.read(
                context = appContext,
                scheduleStableIdsByRoomId = idMapping.scheduleIds
            )
            val assets = buildAssetInputs(snapshot)
            BackupExportMapper.toArchive(
                metadata = metadata,
                snapshot = snapshot,
                preferences = preferences.preferences,
                assetInputs = assets,
                additionalAssets = preferences.additionalAssets,
                idMapping = idMapping
            )
        }

    private fun buildAssetInputs(snapshot: BackupRoomSnapshot): List<BackupExportAssetInput> {
        val inputs = mutableListOf<BackupExportAssetInput>()
        snapshot.configs.forEach { config ->
            val uri = config.wallpaperUri?.takeIf(String::isNotBlank) ?: return@forEach
            inputs += sourceReader.readUri(
                sourceKey = "schedule-wallpaper:${config.id}:$uri",
                uriString = uri,
                mediaType = mediaTypeForUri(uri)
            )
        }
        snapshot.widgetAppearances.forEach { appearance ->
            val uri = appearance.wallpaperUri?.takeIf(String::isNotBlank) ?: return@forEach
            inputs += sourceReader.readUri(
                sourceKey = "widget-wallpaper:${appearance.variant}:${appearance.appWidgetId}:$uri",
                uriString = uri,
                mediaType = mediaTypeForUri(uri)
            )
        }
        snapshot.agentMessages.forEach { message ->
            val fileName = parseAgentMessageContent(message.content).attachmentFileName ?: return@forEach
            inputs += sourceReader.readAgentAttachment(
                sourceKey = "agent-attachment:${message.scheduleId}:${message.id}:$fileName",
                fileName = fileName,
                mediaType = mediaTypeForFileName(fileName)
            )
        }
        return inputs
    }

    private fun mediaTypeForUri(uriString: String): String? {
        val uri = Uri.parse(uriString)
        return if (uri.scheme == "content") {
            runCatching { appContext.contentResolver.getType(uri) }.getOrNull()
        } else {
            mediaTypeForFileName(uri.path.orEmpty())
        }
    }

    private fun mediaTypeForFileName(name: String): String? = when (name.substringAfterLast('.', "").lowercase()) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "webp" -> "image/webp"
        "json" -> "application/json"
        else -> null
    }

    private fun defaultMetadata(): BackupSourceMetadata {
        val info = appContext.packageManager.getPackageInfo(appContext.packageName, 0)
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        } else {
            @Suppress("DEPRECATION")
            info.versionCode
        }
        return BackupSourceMetadata(
            createdAt = java.time.Instant.now().toString(),
            sourceAppVersionName = info.versionName.orEmpty().ifBlank { "unknown" },
            sourceVersionCode = versionCode,
            sourcePackageName = appContext.packageName,
            sourceDatabaseVersion = APP_DATABASE_VERSION,
            devicePlatform = "Android"
        )
    }
}
