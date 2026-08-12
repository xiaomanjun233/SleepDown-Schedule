package com.xiaomanjun.sleepdownschedule

import android.content.Context
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream

/** Reads managed/SAF resources without writing, persisting URI strings, or following Zip paths. */
class BackupAssetSourceReader(private val context: Context) {
    fun readUri(
        sourceKey: String,
        uriString: String,
        mediaType: String? = null
    ): BackupExportAssetInput = runCatching {
        val uri = Uri.parse(uriString)
        val bytes = if (uri.scheme == "file") {
            File(uri.path.orEmpty()).inputStream().use(::readBounded)
        } else {
            context.contentResolver.openInputStream(uri)?.use(::readBounded)
                ?: error("content URI 不可读取")
        }
        BackupExportAssetInput(sourceKey, bytes, mediaType)
    }.getOrElse {
        BackupExportAssetInput(
            sourceKey = sourceKey,
            bytes = null,
            mediaType = mediaType,
            missingReason = "资源 URI 不可读取或超过备份大小限制"
        )
    }

    fun readAgentAttachment(
        sourceKey: String,
        fileName: String,
        mediaType: String? = null
    ): BackupExportAssetInput {
        if (!ManagedBackupAgentAttachmentName.matches(fileName)) {
            return BackupExportAssetInput(
                sourceKey = sourceKey,
                bytes = null,
                mediaType = mediaType,
                missingReason = "Agent 附件文件名不符合私有文件规则"
            )
        }
        return runCatching {
            val directory = File(context.filesDir, "agent_attachments").canonicalFile
            val target = File(directory, fileName).canonicalFile
            if (!target.path.startsWith(directory.path + File.separator) || !target.isFile) {
                return@runCatching BackupExportAssetInput(
                    sourceKey = sourceKey,
                    bytes = null,
                    mediaType = mediaType,
                    missingReason = "Agent 附件文件不存在"
                )
            }
            readFile(sourceKey, target, mediaType)
        }.getOrElse {
            BackupExportAssetInput(
                sourceKey = sourceKey,
                bytes = null,
                mediaType = mediaType,
                missingReason = "Agent 附件文件不可读取"
            )
        }
    }

    fun readFile(
        sourceKey: String,
        file: File,
        mediaType: String? = null
    ): BackupExportAssetInput = runCatching {
        require(file.isFile) { "资源文件不存在" }
        BackupExportAssetInput(sourceKey, file.inputStream().use(::readBounded), mediaType)
    }.getOrElse {
        BackupExportAssetInput(
            sourceKey = sourceKey,
            bytes = null,
            mediaType = mediaType,
            missingReason = "资源文件不可读取或超过备份大小限制"
        )
    }

    private fun readBounded(input: InputStream): ByteArray {
        val output = ByteArrayOutputStream(minOf(64 * 1024L, BackupCodecLimits.MAX_ASSET_BYTES).toInt())
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (count == 0) continue
            if (output.size().toLong() + count > BackupCodecLimits.MAX_ASSET_BYTES) {
                error("资源超过单 asset 大小限制")
            }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private companion object {
        val ManagedBackupAgentAttachmentName =
            Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\.(?:jpg|png|webp)$")
    }
}
