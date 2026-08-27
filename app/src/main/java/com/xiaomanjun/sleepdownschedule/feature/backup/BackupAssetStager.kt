package com.xiaomanjun.sleepdownschedule.feature.backup

import com.xiaomanjun.sleepdownschedule.*

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest

class BackupAssetStageException(message: String, cause: Throwable? = null) : IllegalArgumentException(message, cause)

data class BackupAssetStage(
    val operationId: String,
    val directory: File,
    val presentFilesByAssetId: Map<String, File>,
    val missingAssets: List<BackupAssetManifestEntry>
)

/**
 * Materializes decoded asset bytes below the operation-specific restore staging directory.
 *
 * This class never writes to the live wallpaper, widget or Agent directories. The Repository
 * phase may consume the returned files only after its Room transaction and preference commit
 * policy has accepted the archive.
 */
object BackupAssetStager {
    private val operationIdPattern = Regex("[A-Za-z0-9][A-Za-z0-9_-]{0,63}")

    fun stage(
        rootDirectory: File,
        archive: DecodedBackupArchive,
        operationId: String
    ): BackupAssetStage {
        if (!operationIdPattern.matches(operationId)) {
            fail("restore operationId 非法")
        }
        val root = canonicalRoot(rootDirectory)
        val operationDirectory = File(File(root, ".sleepdown_restore"), operationId)
        val assetsDirectory = File(operationDirectory, "assets")
        ensureWithin(root, operationDirectory)
        ensureDirectory(assetsDirectory)

        val descriptors = archive.manifest.assets.associateBy { it.assetId }
        if (descriptors.size != archive.manifest.assets.size || descriptors.size != archive.assets.size) {
            fail("asset manifest 与 decoded asset 数量不一致")
        }

        val presentFiles = LinkedHashMap<String, File>()
        val missingAssets = ArrayList<BackupAssetManifestEntry>()
        val seenAssetIds = HashSet<String>()
        archive.assets.forEach { asset ->
            val descriptor = descriptors[asset.assetId] ?: fail("asset 不在 manifest 中: ${asset.assetId}")
            if (!seenAssetIds.add(asset.assetId)) fail("decoded asset 包含重复 ID: ${asset.assetId}")
            BackupStableId.requireValid(asset.assetId, BackupStableId.ASSET_PREFIX)
            if (descriptor.category !in BackupAssetCategory.all) fail("未知 asset category: ${descriptor.category}")
            if (descriptor.purpose !in BackupAssetPurpose.all) fail("未知 asset purpose: ${descriptor.purpose}")
            if (descriptor.relativePath != "assets/${descriptor.category}/${descriptor.assetId}") {
                fail("asset path 不符合 manifest 规则: ${asset.assetId}")
            }
            if (asset.category != descriptor.category || asset.purpose != descriptor.purpose ||
                asset.mediaType != descriptor.mediaType || asset.optional != descriptor.optional ||
                asset.ownerId != descriptor.ownerId
            ) {
                fail("decoded asset 元数据与 manifest 不一致: ${asset.assetId}")
            }
            if (asset.bytes == null) {
                if (descriptor.present) fail("manifest 标记 present 但没有 asset bytes: ${asset.assetId}")
                if (asset.missingReason != descriptor.missingReason) {
                    fail("decoded missingReason 与 manifest 不一致: ${asset.assetId}")
                }
                missingAssets += descriptor
                return@forEach
            }
            if (!descriptor.present) fail("manifest 标记 missing 但仍有 asset bytes: ${asset.assetId}")
            val bytes = asset.bytes
            if (bytes.isEmpty() || bytes.size.toLong() != descriptor.byteLength) {
                fail("asset 大小与 manifest 不一致: ${asset.assetId}")
            }
            if (sha256(bytes) != descriptor.sha256) {
                fail("asset checksum mismatch: ${asset.assetId}")
            }
            val target = File(assetsDirectory, "${descriptor.category}/${descriptor.assetId}")
            ensureWithin(assetsDirectory, target)
            writeIdempotently(target, bytes)
            presentFiles[asset.assetId] = target.canonicalFile
        }

        return BackupAssetStage(
            operationId = operationId,
            directory = operationDirectory.canonicalFile,
            presentFilesByAssetId = presentFiles.toMap(),
            missingAssets = missingAssets.toList()
        )
    }

    private fun canonicalRoot(rootDirectory: File): File {
        return try {
            if (rootDirectory.exists() && !rootDirectory.isDirectory) {
                fail("restore staging root 不是目录")
            }
            if (!rootDirectory.exists() && !rootDirectory.mkdirs()) {
                fail("无法创建 restore staging root")
            }
            rootDirectory.canonicalFile
        } catch (error: BackupAssetStageException) {
            throw error
        } catch (error: IOException) {
            throw BackupAssetStageException("无法解析 restore staging root", error)
        }
    }

    private fun ensureDirectory(directory: File) {
        try {
            if (directory.exists()) {
                if (!directory.isDirectory) fail("restore staging 路径不是目录: ${directory.path}")
            } else if (!directory.mkdirs() && !directory.isDirectory) {
                fail("无法创建 restore staging 目录: ${directory.path}")
            }
            ensureWithin(directory.parentFile?.parentFile ?: directory, directory)
        } catch (error: BackupAssetStageException) {
            throw error
        } catch (error: SecurityException) {
            throw BackupAssetStageException("无法创建 restore staging 目录", error)
        }
    }

    private fun writeIdempotently(target: File, bytes: ByteArray) {
        try {
            if (target.exists()) {
                if (target.isDirectory) fail("asset staging target 是目录: ${target.path}")
                if (target.length() != bytes.size.toLong() || !target.readBytes().contentEquals(bytes)) {
                    fail("同一 operationId 下 asset bytes 不一致: ${target.path}")
                }
                return
            }
            val parent = target.parentFile ?: fail("asset staging target 没有父目录")
            if (!parent.exists() && !parent.mkdirs()) fail("无法创建 asset staging 目录")
            ensureWithin(parent.parentFile ?: parent, target)
            val temporary = File(parent, ".${target.name}.tmp")
            if (temporary.exists() && !temporary.delete()) {
                fail("无法清理 asset staging 临时文件: ${temporary.path}")
            }
            FileOutputStream(temporary).use { stream ->
                stream.write(bytes)
                stream.flush()
                try {
                    stream.fd.sync()
                } catch (error: IOException) {
                    throw BackupAssetStageException("无法持久化 asset staging 文件", error)
                }
            }
            if (!temporary.renameTo(target)) {
                if (!target.exists() || target.length() != bytes.size.toLong() ||
                    !target.readBytes().contentEquals(bytes)
                ) {
                    fail("无法原子落地 asset staging 文件: ${target.path}")
                }
                if (!temporary.delete()) fail("无法清理 asset staging 临时文件")
            }
        } catch (error: BackupAssetStageException) {
            throw error
        } catch (error: IOException) {
            throw BackupAssetStageException("写入 asset staging 文件失败: ${target.path}", error)
        } catch (error: SecurityException) {
            throw BackupAssetStageException("写入 asset staging 文件失败: ${target.path}", error)
        }
    }

    private fun ensureWithin(root: File, candidate: File) {
        val rootPath = root.canonicalFile.path
        val candidatePath = candidate.canonicalFile.path
        if (candidatePath != rootPath && !candidatePath.startsWith(rootPath + File.separator)) {
            fail("restore staging 路径越界: ${candidate.path}")
        }
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { byte -> "%02x".format(byte) }

    private fun fail(message: String): Nothing = throw BackupAssetStageException(message)
}
