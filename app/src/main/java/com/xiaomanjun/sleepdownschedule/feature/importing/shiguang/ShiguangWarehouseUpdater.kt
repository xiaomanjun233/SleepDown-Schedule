package com.xiaomanjun.sleepdownschedule.feature.importing.shiguang

import android.content.Context
import com.xiaomanjun.sleepdownschedule.feature.importing.EduAdapter
import com.xiaomanjun.sleepdownschedule.feature.importing.ShiguangWarehouse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

internal data class ShiguangRefreshResult(
    val changed: Boolean,
    val adapterCount: Int
)

internal object ShiguangWarehouseUpdater {
    private const val CacheDirectoryName = "shiguang_warehouse"
    private const val IndexFileName = "school_index.pb"
    private const val PreferencesName = "shiguang_warehouse_metadata"
    private const val LastSuccessfulRefreshKey = "last_successful_refresh"
    private const val IndexShaKey = "index_sha256"
    private const val IndexUrl =
        "https://raw.githubusercontent.com/XingHeYuZhuan/shiguang_warehouse/index-pb-release/school_index.pb"
    private const val ResourceBaseUrl =
        "https://raw.githubusercontent.com/XingHeYuZhuan/shiguang_warehouse/main/resources/"
    private val RefreshIntervalMillis = TimeUnit.DAYS.toMillis(7)
    private val refreshMutex = Mutex()

    fun cachedIndexFile(context: Context): File = File(cacheRoot(context), IndexFileName)

    fun cachedScriptFile(context: Context, adapter: EduAdapter): File =
        safeResourceFile(context, resourceRelativePath(adapter))

    fun hasValidRemoteIndex(context: Context): Boolean = runCatching {
        ShiguangWarehouse.parseProtocolV2Index(cachedIndexFile(context).readBytes()).isNotEmpty()
    }.getOrDefault(false)

    fun isRefreshStale(context: Context, nowMillis: Long = System.currentTimeMillis()): Boolean {
        val lastSuccess = preferences(context).getLong(LastSuccessfulRefreshKey, 0L)
        return lastSuccess <= 0L || nowMillis - lastSuccess >= RefreshIntervalMillis
    }

    suspend fun refresh(context: Context): ShiguangRefreshResult = withContext(Dispatchers.IO) {
        refreshMutex.withLock {
            val bytes = download(IndexUrl)
            val adapters = ShiguangWarehouse.parseProtocolV2Index(bytes)
            require(adapters.isNotEmpty()) { "远端拾光索引为空" }
            val sha = bytes.sha256()
            val previousSha = preferences(context).getString(IndexShaKey, null)
            atomicWrite(cachedIndexFile(context), bytes)
            val committed = preferences(context).edit()
                .putLong(LastSuccessfulRefreshKey, System.currentTimeMillis())
                .putString(IndexShaKey, sha)
                .commit()
            check(committed) { "无法保存拾光仓库刷新时间" }
            ShiguangRefreshResult(
                changed = previousSha != sha,
                adapterCount = adapters.size
            )
        }
    }

    suspend fun resolveRemoteScript(context: Context, adapter: EduAdapter): String =
        withContext(Dispatchers.IO) {
            refreshMutex.withLock {
                val relativePath = resourceRelativePath(adapter)
                val target = safeResourceFile(context, relativePath)
                target.takeIf { it.isFile }?.readText()?.takeIf { it.isNotBlank() }?.let { return@withLock it }

                val encodedPath = relativePath.split('/').joinToString("/") { segment ->
                    URLEncoder.encode(segment, Charsets.UTF_8.name()).replace("+", "%20")
                }
                val bytes = download(ResourceBaseUrl + encodedPath)
                require(bytes.isNotEmpty()) { "远端拾光脚本为空：$relativePath" }
                atomicWrite(target, bytes)
                bytes.toString(Charsets.UTF_8)
            }
        }

    internal fun resourceRelativePath(adapter: EduAdapter): String {
        val path = if ('/' in adapter.assetJsPath) {
            adapter.assetJsPath
        } else {
            "${adapter.school.folder}/${adapter.assetJsPath}"
        }
        require(path.isNotBlank()) { "拾光脚本路径为空" }
        require('\\' !in path && !path.startsWith('/')) { "非法拾光脚本路径：$path" }
        require(!Regex("^[A-Za-z][A-Za-z0-9+.-]*:").containsMatchIn(path)) {
            "拾光脚本路径不能包含 scheme：$path"
        }
        val segments = path.split('/')
        require(segments.none { it.isBlank() || it == "." || it == ".." }) { "非法拾光脚本路径：$path" }
        require(path.endsWith(".js", ignoreCase = true)) { "拾光资源不是 JS：$path" }
        return segments.joinToString("/")
    }

    private fun cacheRoot(context: Context): File =
        File(context.filesDir, CacheDirectoryName).apply { mkdirs() }

    private fun safeResourceFile(context: Context, relativePath: String): File {
        val resources = File(cacheRoot(context), "resources").apply { mkdirs() }
        val target = File(resources, relativePath)
        val rootPath = resources.canonicalFile.toPath()
        val targetPath = target.canonicalFile.toPath()
        require(targetPath.startsWith(rootPath)) { "拾光脚本路径越界：$relativePath" }
        return target
    }

    private fun preferences(context: Context) =
        context.applicationContext.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)

    private fun download(url: String): ByteArray {
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = 12_000
            connection.readTimeout = 20_000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Accept", "application/octet-stream, text/javascript, */*")
            connection.setRequestProperty("User-Agent", "SleepDown-Schedule/ShiguangWarehouse")
            val status = connection.responseCode
            if (status !in 200..299) throw IOException("拾光仓库请求失败：HTTP $status")
            connection.inputStream.use { it.readBytes() }
        } finally {
            connection.disconnect()
        }
    }

    private fun atomicWrite(target: File, bytes: ByteArray) {
        target.parentFile?.mkdirs()
        val temporary = File(target.parentFile, ".${target.name}.tmp")
        try {
            FileOutputStream(temporary).use { output ->
                output.write(bytes)
                output.fd.sync()
            }
            Files.move(
                temporary.toPath(),
                target.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
        } finally {
            temporary.delete()
        }
    }

    private fun ByteArray.sha256(): String = MessageDigest.getInstance("SHA-256")
        .digest(this)
        .joinToString("") { byte -> "%02x".format(byte) }
}
