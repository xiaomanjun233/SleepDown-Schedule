package com.xiaomanjun.sleepdownschedule.core.web

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import java.io.File
import java.io.OutputStream
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.StandardOpenOption

internal fun Context.writeDownload(
    fileName: String,
    mimeType: String,
    write: (OutputStream) -> Unit
) {
    val safeName = safeDownloadFileName(fileName)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        writeScopedDownload(safeName, mimeType, write)
    } else {
        writeLegacyDownload(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            safeName,
            write
        )
    }
}

internal fun writeLegacyDownload(directory: File, fileName: String, write: (OutputStream) -> Unit): File {
    val root = directory.canonicalFile
    check(root.isDirectory || root.mkdirs()) { "无法创建下载目录" }
    val safeName = safeDownloadFileName(fileName)
    for (number in 0..999) {
        val target = File(root, numberedDownloadFileName(safeName, number))
        val output = try {
            Files.newOutputStream(target.toPath(), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)
        } catch (_: FileAlreadyExistsException) {
            continue
        }
        try {
            output.use(write)
            return target
        } catch (error: Throwable) {
            runCatching { Files.deleteIfExists(target.toPath()) }.exceptionOrNull()?.let(error::addSuppressed)
            throw error
        }
    }
    error("同名下载文件过多，请整理下载目录")
}

@RequiresApi(Build.VERSION_CODES.Q)
private fun Context.writeScopedDownload(fileName: String, mimeType: String, write: (OutputStream) -> Unit) {
    val values = ContentValues().apply {
        put(MediaStore.Downloads.DISPLAY_NAME, fileName)
        put(MediaStore.Downloads.MIME_TYPE, mimeType)
        put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        put(MediaStore.Downloads.IS_PENDING, 1)
    }
    val target = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        ?: error("无法创建文件")
    try {
        val output = contentResolver.openOutputStream(target) ?: error("无法写入文件")
        output.use(write)
        val published = contentResolver.update(target, ContentValues().apply {
            put(MediaStore.Downloads.IS_PENDING, 0)
        }, null, null)
        check(published == 1) { "无法完成文件保存" }
    } catch (error: Throwable) {
        runCatching { contentResolver.delete(target, null, null) }.exceptionOrNull()?.let(error::addSuppressed)
        throw error
    }
}
