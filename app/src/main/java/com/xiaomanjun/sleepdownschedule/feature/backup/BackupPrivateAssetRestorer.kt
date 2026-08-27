package com.xiaomanjun.sleepdownschedule.feature.backup

import com.xiaomanjun.sleepdownschedule.*

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

data class BackupRestoredAssets(
    val pathsByAssetId: Map<String, String>,
    val urisByAssetId: Map<String, String>,
    val filesByAssetId: Map<String, File>,
    val attachmentFileNamesByAssetId: Map<String, String>,
    val newlyCreatedPaths: Set<String>
) {
    val historyContextFilesByAssetId: Map<String, File>
        get() = filesByAssetId
}

/**
 * Copies validated staged bytes into the app's managed private directories. Target names are
 * derived only from the archive stable ID/owner, so old content URIs never cross into Room.
 */
object BackupPrivateAssetRestorer {
    fun prepare(
        context: Context,
        operationId: String,
        archive: DecodedBackupArchive,
        stage: BackupAssetStage
    ): BackupRestoredAssets {
        val root = context.filesDir.canonicalFile
        val operationPrefix = "sleepdown_restore_${operationId}_"
        val paths = linkedMapOf<String, String>()
        val uris = linkedMapOf<String, String>()
        val files = linkedMapOf<String, File>()
        val attachmentNames = linkedMapOf<String, String>()
        val created = linkedSetOf<String>()

        archive.assets.filter { it.bytes != null }.forEach { asset ->
            val staged = stage.presentFilesByAssetId[asset.assetId]
                ?: error("present asset 未完成 staging: ${asset.assetId}")
            val targetSpec = targetFile(root, operationPrefix, asset)
            ensureWithin(root, targetSpec.file)
            val wasCreated = writeIdempotently(targetSpec.file, staged)
            if (wasCreated) created += targetSpec.file.canonicalPath
            val canonical = targetSpec.file.canonicalFile
            paths[asset.assetId] = canonical.path
            uris[asset.assetId] = Uri.fromFile(canonical).toString()
            files[asset.assetId] = canonical
            targetSpec.attachmentFileName?.let { attachmentNames[asset.assetId] = it }
        }

        return BackupRestoredAssets(
            pathsByAssetId = paths.toMap(),
            urisByAssetId = uris.toMap(),
            filesByAssetId = files.toMap(),
            attachmentFileNamesByAssetId = attachmentNames.toMap(),
            newlyCreatedPaths = created.toSet()
        )
    }

    fun deleteNewlyCreatedFiles(files: Collection<String>, context: Context) {
        val root = context.filesDir.canonicalFile
        files.forEach { path ->
            val file = File(path).canonicalFile
            ensureWithin(root, file)
            if (file.isFile && !file.delete()) {
                throw IllegalStateException("无法删除未提交 restore 资源: ${file.path}")
            }
        }
    }

    private data class TargetFile(val file: File, val attachmentFileName: String? = null)

    private fun targetFile(root: File, operationPrefix: String, asset: BackupAsset): TargetFile {
        val extension = extensionFor(asset.mediaType)
        return when (asset.purpose) {
            BackupAssetPurpose.SCHEDULE_WALLPAPER -> TargetFile(
                File(root, "wallpaper/${operationPrefix}${asset.assetId}.$extension")
            )
            BackupAssetPurpose.WIDGET_WALLPAPER -> TargetFile(
                File(root, "widget_wallpaper/${operationPrefix}${asset.assetId}.$extension")
            )
            BackupAssetPurpose.AGENT_ATTACHMENT -> {
                val uuid = asset.assetId.substringAfter('_')
                val fileName = "$uuid.$extension"
                require(ManagedAgentAttachmentName.matches(fileName)) {
                    "Agent attachment 目标文件名非法: ${asset.assetId}"
                }
                TargetFile(File(root, "agent_attachments/$fileName"), fileName)
            }
            BackupAssetPurpose.AI_IMPORT_HISTORY_CONTEXT -> {
                val owner = asset.ownerId ?: error("AI history context 缺少 ownerId")
                BackupStableId.requireValid(owner, BackupStableId.HISTORY_PREFIX)
                TargetFile(File(root, "ai_import_history/$owner.json"))
            }
            BackupAssetPurpose.AI_IMPORT_SCREENSHOT,
            BackupAssetPurpose.OTHER -> TargetFile(
                File(root, "sleepdown_restore_assets/${operationPrefix}${asset.assetId}.$extension")
            )
            else -> error("未知 restore asset purpose: ${asset.purpose}")
        }
    }

    private fun writeIdempotently(target: File, source: File): Boolean {
        try {
            if (target.exists()) {
                if (!target.isFile) error("restore asset target 不是文件: ${target.path}")
                if (!sameBytes(target, source)) error("restore asset target 内容冲突: ${target.path}")
                return false
            }
            val parent = target.parentFile ?: error("restore asset target 没有父目录")
            if (!parent.exists() && !parent.mkdirs() && !parent.isDirectory) {
                error("无法创建 restore asset 目录: ${parent.path}")
            }
            val temporary = File(parent, ".${target.name}.tmp")
            if (temporary.exists() && !temporary.delete()) error("无法清理 restore asset 临时文件")
            FileInputStream(source).use { input ->
                FileOutputStream(temporary).use { output ->
                    input.copyTo(output, DEFAULT_BUFFER_SIZE)
                    output.flush()
                    output.fd.sync()
                }
            }
            if (!temporary.renameTo(target)) {
                if (!target.exists() || !sameBytes(target, source)) {
                    error("无法原子落地 restore asset: ${target.path}")
                }
                if (!temporary.delete()) error("无法清理 restore asset 临时文件")
                return false
            }
            return true
        } catch (error: IOException) {
            throw IllegalStateException("写入 restore asset 失败: ${target.path}", error)
        } catch (error: SecurityException) {
            throw IllegalStateException("写入 restore asset 失败: ${target.path}", error)
        }
    }

    private fun sameBytes(first: File, second: File): Boolean {
        if (first.length() != second.length()) return false
        FileInputStream(first).use { left ->
            FileInputStream(second).use { right ->
                val leftBuffer = ByteArray(DEFAULT_BUFFER_SIZE)
                val rightBuffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val leftCount = left.read(leftBuffer)
                    val rightCount = right.read(rightBuffer)
                    if (leftCount != rightCount) return false
                    if (leftCount < 0) return true
                    if (!leftBuffer.copyOf(leftCount).contentEquals(rightBuffer.copyOf(rightCount))) return false
                }
            }
        }
    }

    private fun extensionFor(mediaType: String): String = when (mediaType.lowercase()) {
        "image/jpeg" -> "jpg"
        "image/png" -> "png"
        "image/webp" -> "webp"
        "application/json" -> "json"
        "text/plain" -> "txt"
        else -> "bin"
    }

    private fun ensureWithin(root: File, candidate: File) {
        val rootPath = root.canonicalPath
        val candidatePath = candidate.canonicalPath
        require(candidatePath == rootPath || candidatePath.startsWith(rootPath + File.separator)) {
            "restore asset 路径越界: ${candidate.path}"
        }
    }

    private val ManagedAgentAttachmentName =
        Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\.(?:jpg|png|webp)$")
}
