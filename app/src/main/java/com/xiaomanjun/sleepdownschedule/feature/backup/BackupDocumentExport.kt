package com.xiaomanjun.sleepdownschedule.feature.backup

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.DigestInputStream
import java.security.MessageDigest

/** Private staging for SAF: validate a complete archive before asking the provider to create a file. */
internal class BackupDocumentExport(private val directory: File) {
    fun prepare(archive: BackupArchive): String {
        check(directory.isDirectory || directory.mkdirs()) { "无法创建备份临时目录" }
        val file = File.createTempFile("backup-", ".sleepdown", directory)
        try {
            file.outputStream().buffered().use { BackupCodec.write(it, archive) }
            // The exact file offered for saving must also pass the existing restore reader.
            file.inputStream().buffered().use { BackupCodec.decode(it) }
            check(file.length() > 0) { "生成的备份为空" }
            return file.name
        } catch (error: Throwable) {
            file.delete()
            throw error
        }
    }

    fun save(
        name: String,
        openDestination: () -> OutputStream?,
        readDestination: () -> InputStream?
    ): Long {
        val file = resolve(name)
        val expected = file.length()
        check(file.isFile && expected > 0) { "待保存的备份已失效，请重新点击保存" }
        val sourceDigest = MessageDigest.getInstance("SHA-256")
        DigestInputStream(file.inputStream().buffered(), sourceDigest).use { input ->
            val output = openDestination() ?: throw IOException("系统未能打开备份保存位置")
            output.use {
                val copied = input.copyTo(it)
                if (copied != expected) throw IOException("备份没有完整写入，请重新保存")
                it.flush()
            }
        }
        // Closing a provider's output without an exception does not prove the document was saved.
        // Reopen the same URI and check the actual bytes, not optional/stale COLUMN_SIZE metadata.
        val savedDigest = MessageDigest.getInstance("SHA-256")
        val savedInput = readDestination() ?: throw IOException("无法读取刚保存的备份进行校验")
        savedInput.buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var actual = 0L
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                actual += count
                if (actual > expected) throw IOException("保存后的备份大小不一致，请重新保存")
                savedDigest.update(buffer, 0, count)
            }
            if (actual != expected) {
                throw IOException("保存后的备份不完整（应为 $expected 字节，实际 $actual 字节）")
            }
        }
        if (!MessageDigest.isEqual(sourceDigest.digest(), savedDigest.digest())) {
            throw IOException("保存后的备份内容校验失败，请重新保存")
        }
        return expected
    }

    fun discard(name: String) {
        resolve(name).delete()
    }

    private fun resolve(name: String): File {
        require(name.matches(Regex("backup-[A-Za-z0-9-]+\\.sleepdown"))) { "备份临时文件标识无效" }
        val file = File(directory, name).canonicalFile
        require(file.parentFile == directory.canonicalFile) { "备份临时文件位置无效" }
        return file
    }
}
