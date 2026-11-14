package com.xiaomanjun.sleepdownschedule.core.ai

import java.io.Closeable
import java.io.File
import java.security.MessageDigest

/** Task-owned scratch files. Paths never grant access to app databases, credentials or source files. */
internal class AgentTaskFiles(private val directory: File) : Closeable {
    data class Snapshot(val path: String, val text: String, val hash: String)
    private val root = directory.canonicalFile.apply { check(mkdirs() || isDirectory) }
    private fun target(path: String): File {
        require(path.isNotBlank() && !File(path).isAbsolute) { "需要任务目录内的相对路径" }
        val file = File(root, path).canonicalFile
        require(file.path.startsWith(root.path + File.separator) && file != root) { "文件路径超出本次任务目录" }
        return file
    }
    @Synchronized fun list(): List<String> = root.walkTopDown().filter(File::isFile)
        .map { it.relativeTo(root).invariantSeparatorsPath }.take(33).toList()

    @Synchronized fun read(path: String): Snapshot {
        val file = target(path)
        require(file.isFile && file.length() <= 256 * 1024) { "任务文件不存在或超过 256 KiB" }
        val bytes = file.readBytes()
        return Snapshot(path, bytes.toString(Charsets.UTF_8), hash(bytes))
    }

    @Synchronized fun write(path: String, text: String, expectedHash: String): Snapshot {
        val file = target(path)
        if (file.exists()) require(read(path).hash == expectedHash) { "文件已变化，请重新读取后编辑；未覆盖旧内容" }
        else require(expectedHash.isEmpty()) { "文件不存在，请以空 expectedHash 创建新文件" }
        val bytes = text.toByteArray(Charsets.UTF_8)
        require(bytes.size <= 256 * 1024) { "单个任务文件不能超过 256 KiB" }
        val existing = root.walkTopDown().filter(File::isFile).toList()
        require(existing.size < 32 || file.exists()) { "任务文件最多 32 个" }
        require(existing.sumOf { if (it.canonicalFile == file) 0L else it.length() } + bytes.size <= 2 * 1024 * 1024) { "任务文件总量超过 2 MiB" }
        check(file.parentFile?.mkdirs() == true || file.parentFile?.isDirectory == true)
        val temporary = File.createTempFile(".write-", ".tmp", file.parentFile)
        try {
            temporary.writeBytes(bytes)
            java.nio.file.Files.move(temporary.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        } finally { temporary.delete() }
        return read(path)
    }

    @Synchronized fun edit(path: String, oldText: String, newText: String, expectedHash: String): Snapshot {
        val current = read(path)
        require(current.hash == expectedHash) { "文件已变化，请重新读取后编辑" }
        require(oldText.isNotEmpty()) { "需要明确的原文片段" }
        val index = current.text.indexOf(oldText)
        require(index >= 0 && current.text.indexOf(oldText, index + oldText.length) < 0) { "原文片段需要精确且只匹配一次" }
        return write(path, current.text.replaceRange(index, index + oldText.length, newText), expectedHash)
    }

    override fun close() {
        // Only the directory created for this task is eligible for cleanup.
        check(directory.canonicalFile == root)
        root.walkBottomUp().forEach { file -> if (file.canonicalPath == root.path || file.canonicalPath.startsWith(root.path + File.separator)) file.delete() }
    }
    companion object {
        fun hash(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }
}
