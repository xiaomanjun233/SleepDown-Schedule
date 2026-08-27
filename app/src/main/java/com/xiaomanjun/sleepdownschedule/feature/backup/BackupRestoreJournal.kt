package com.xiaomanjun.sleepdownschedule.feature.backup

import com.xiaomanjun.sleepdownschedule.*

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
enum class BackupRestoreState {
    READ_ONLY,
    VALIDATED,
    STAGED,
    DB_COMMITTED,
    PREFS_COMMITTED,
    FINALIZED
}

@Serializable
data class BackupRestoreMarker(
    val operationId: String,
    val archiveFingerprint: String,
    val state: BackupRestoreState,
    val plan: BackupImportPlan,
    val finalAssetPathsById: Map<String, String> = emptyMap(),
    val newlyCreatedAssetPaths: Set<String> = emptySet(),
    val dbCommitStarted: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)

@Serializable
data class BackupRestoreAssetMetadata(
    val assetId: String,
    val category: String,
    val purpose: String,
    val mediaType: String,
    val optional: Boolean,
    val ownerId: String? = null,
    val missingReason: String? = null
)

@Serializable
data class BackupRestorePayload(
    val manifest: BackupManifest,
    val data: BackupData,
    val preferences: BackupPreferences,
    val checksums: BackupChecksums,
    val assets: List<BackupRestoreAssetMetadata>
) {
    companion object {
        fun fromArchive(archive: DecodedBackupArchive): BackupRestorePayload = BackupRestorePayload(
            manifest = archive.manifest,
            data = archive.data,
            preferences = archive.preferences,
            checksums = archive.checksums,
            assets = archive.assets.map { asset ->
                BackupRestoreAssetMetadata(
                    assetId = asset.assetId,
                    category = asset.category,
                    purpose = asset.purpose,
                    mediaType = asset.mediaType,
                    optional = asset.optional,
                    ownerId = asset.ownerId,
                    missingReason = asset.missingReason
                )
            }
        )
    }
}

internal val BackupRestoreJson = Json {
    encodeDefaults = true
    ignoreUnknownKeys = false
}

class BackupRestoreJournalException(message: String, cause: Throwable? = null) : IllegalArgumentException(message, cause)

/** Small write-ahead journal kept next to one operation's staging directory. */
class BackupRestoreJournal(
    filesDirectory: File,
    val operationId: String
) {
    private val root = filesDirectory.canonicalFile
    val directory: File = File(root, ".sleepdown_restore/$operationId").canonicalFile
    private val markerFile: File get() = File(directory, "marker.json")
    val preferencesFile: File get() = File(directory, "preferences.json")
    private val payloadFile: File get() = File(directory, "payload.json")

    init {
        require(operationId.matches(Regex("[A-Za-z0-9][A-Za-z0-9_-]{0,63}"))) {
            "restore operationId 非法"
        }
        ensureWithin(root, directory)
    }

    fun writeMarker(marker: BackupRestoreMarker) {
        require(marker.operationId == operationId) { "marker operationId 不一致" }
        ensureDirectory(directory)
        atomicWrite(markerFile, BackupRestoreJson.encodeToString(marker).toByteArray(Charsets.UTF_8))
    }

    fun readMarker(): BackupRestoreMarker? {
        if (!markerFile.isFile) return null
        return try {
            BackupRestoreJson.decodeFromString<BackupRestoreMarker>(markerFile.readText(Charsets.UTF_8))
        } catch (error: Exception) {
            throw BackupRestoreJournalException("restore marker 损坏: ${markerFile.path}", error)
        }.also { marker ->
            require(marker.operationId == operationId) { "restore marker operationId 不一致" }
        }
    }

    fun writePreferences(preferences: BackupPreferences) {
        ensureDirectory(directory)
        atomicWrite(
            preferencesFile,
            BackupRestoreJson.encodeToString(preferences).toByteArray(Charsets.UTF_8)
        )
    }

    fun writePayload(payload: BackupRestorePayload) {
        ensureDirectory(directory)
        atomicWrite(
            payloadFile,
            BackupRestoreJson.encodeToString(payload).toByteArray(Charsets.UTF_8)
        )
    }

    fun readPayload(): BackupRestorePayload {
        if (!payloadFile.isFile) throw BackupRestoreJournalException("restore payload journal 缺失")
        return try {
            BackupRestoreJson.decodeFromString<BackupRestorePayload>(payloadFile.readText(Charsets.UTF_8))
        } catch (error: Exception) {
            throw BackupRestoreJournalException("restore payload journal 损坏", error)
        }
    }

    fun readPreferences(): BackupPreferences {
        if (!preferencesFile.isFile) throw BackupRestoreJournalException("restore preferences journal 缺失")
        return try {
            BackupRestoreJson.decodeFromString<BackupPreferences>(preferencesFile.readText(Charsets.UTF_8))
        } catch (error: Exception) {
            throw BackupRestoreJournalException("restore preferences journal 损坏", error)
        }
    }

    fun deleteOperation() {
        ensureWithin(root, directory)
        if (directory.exists() && !directory.deleteRecursively()) {
            throw BackupRestoreJournalException("无法清理 restore operation: ${directory.path}")
        }
    }

    private fun ensureDirectory(directory: File) {
        if (directory.exists()) {
            if (!directory.isDirectory) throw BackupRestoreJournalException("restore journal 路径不是目录")
        } else if (!directory.mkdirs() && !directory.isDirectory) {
            throw BackupRestoreJournalException("无法创建 restore journal 目录")
        }
        ensureWithin(root, directory)
    }

    private fun atomicWrite(target: File, bytes: ByteArray) {
        val parent = target.parentFile ?: throw BackupRestoreJournalException("journal target 没有父目录")
        ensureDirectory(parent)
        val temporary = File(parent, ".${target.name}.tmp")
        try {
            FileOutputStream(temporary).use { output ->
                output.write(bytes)
                output.flush()
                output.fd.sync()
            }
            if (!temporary.renameTo(target)) {
                if (!target.exists() || !target.readBytes().contentEquals(bytes)) {
                    throw BackupRestoreJournalException("无法原子写入 restore journal: ${target.path}")
                }
                if (!temporary.delete()) throw BackupRestoreJournalException("无法清理 restore journal 临时文件")
            }
        } catch (error: BackupRestoreJournalException) {
            throw error
        } catch (error: IOException) {
            throw BackupRestoreJournalException("写入 restore journal 失败: ${target.path}", error)
        } catch (error: SecurityException) {
            throw BackupRestoreJournalException("写入 restore journal 失败: ${target.path}", error)
        } finally {
            if (temporary.exists()) temporary.delete()
        }
    }

    private fun ensureWithin(root: File, candidate: File) {
        val rootPath = root.canonicalFile.path
        val candidatePath = candidate.canonicalFile.path
        require(candidatePath == rootPath || candidatePath.startsWith(rootPath + File.separator)) {
            "restore journal 路径越界"
        }
    }
}
