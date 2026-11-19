package com.xiaomanjun.sleepdownschedule.feature.importing.shiguang

import android.content.Context
import com.xiaomanjun.sleepdownschedule.feature.importing.EduAdapter
import com.xiaomanjun.sleepdownschedule.feature.importing.ShiguangWarehouse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.TimeUnit

internal data class ShiguangRefreshResult(
    val changed: Boolean,
    val adapterCount: Int
)

internal fun describeAdapterRefresh(
    before: List<EduAdapter>,
    after: List<EduAdapter>,
    changed: Boolean
): String {
    val oldKeys = before.map { Triple(it.school.id, it.source, it.adapterId) }.toSet()
    val added = after.filter { Triple(it.school.id, it.source, it.adapterId) !in oldKeys }
    if (added.isEmpty()) return if (changed) {
        "适配列表已更新，本次没有新增学校或教务入口。"
    } else {
        "适配列表已是最新，本次没有新增学校或教务入口。"
    }
    val names = added.map { "${it.school.name} · ${it.adapterName}" }.distinct()
    return "新增 ${added.size} 个教务入口：${names.take(6).joinToString("、")}" +
        if (names.size > 6) "等" else ""
}

internal object ShiguangWarehouseUpdater {
    const val GITEE_REPO_URL = "https://gitee.com/XingHeYuZhuan-gh/shiguang_warehouse"
    const val GITHUB_REPO_URL = "https://github.com/XingHeYuZhuan/shiguang_warehouse"
    private const val CacheDirectoryName = "shiguang_warehouse"
    private const val IndexFileName = "school_index.pb"
    private const val PreferencesName = "shiguang_warehouse_metadata"
    private const val LastSuccessfulRefreshKey = "last_successful_refresh"
    private const val IndexShaKey = "index_sha256"
    private const val VersionIdKey = "version_id"
    private const val RepoUrlKey = "repo_url"
    private const val IndexBranch = "index-pb-release"
    private const val ResourcesBranch = "main"
    private const val MaxDownloadBytes = 8L * 1024L * 1024L
    private val RefreshIntervalMillis = TimeUnit.DAYS.toMillis(7)
    private val refreshMutex = Mutex()

    fun getRepoUrl(context: Context): String {
        val stored = preferences(context).getString(RepoUrlKey, GITEE_REPO_URL).orEmpty().trim()
        if (stored.isBlank() || stored.contains("com_haooz_account/shiguang_warehouse")) {
            return GITEE_REPO_URL
        }
        return stored.removeSuffix(".git").trimEnd('/')
    }

    fun setRepoUrl(context: Context, url: String) {
        val normalized = url.trim().removeSuffix(".git").trimEnd('/')
            .takeIf { it == GITEE_REPO_URL || it == GITHUB_REPO_URL }
            ?: GITEE_REPO_URL
        preferences(context).edit().putString(RepoUrlKey, normalized).apply()
    }

    fun isGithubSource(context: Context): Boolean =
        getRepoUrl(context).contains("github.com", ignoreCase = true)

    fun sourceLabel(context: Context): String =
        if (isGithubSource(context)) "GitHub" else "Gitee"

    private fun indexUrl(context: Context): String =
        "${getRepoUrl(context)}/raw/$IndexBranch/$IndexFileName"

    private fun resourceBaseUrl(context: Context): String =
        "${getRepoUrl(context)}/raw/$ResourcesBranch/resources/"

    fun cachedIndexFile(context: Context): File = File(cacheRoot(context), IndexFileName)

    fun cachedScriptFile(context: Context, adapter: EduAdapter): File =
        safeResourceFile(context, currentRemoteSnapshot(context).indexSha, resourceRelativePath(adapter))

    fun hasValidRemoteIndex(context: Context): Boolean = runCatching {
        currentRemoteSnapshot(context).adapters.isNotEmpty()
    }.getOrDefault(false)

    fun isRefreshStale(context: Context, nowMillis: Long = System.currentTimeMillis()): Boolean {
        val lastSuccess = preferences(context).getLong(LastSuccessfulRefreshKey, 0L)
        return shouldRefresh(
            lastSuccessfulRefreshMillis = lastSuccess,
            nowMillis = nowMillis,
            hasValidRemoteIndex = hasValidRemoteIndex(context)
        ) || AiDockWarehouseUpdater.isRefreshStale(context, nowMillis)
    }

    suspend fun refresh(context: Context): ShiguangRefreshResult = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val (shiguangOutcome, aiDockOutcome) = coroutineScope {
            val shiguang = async {
                runCatching { refreshMutex.withLock { refreshLocked(appContext) } }
            }
            val aiDock = async {
                runCatching { AiDockWarehouseUpdater.refresh(appContext) }
            }
            shiguang.await() to aiDock.await()
        }
        val shiguangResult = shiguangOutcome.getOrNull()
        val aiDockResult = aiDockOutcome.getOrNull()
        if (shiguangResult == null && aiDockResult == null) {
            throw (shiguangOutcome.exceptionOrNull() ?: aiDockOutcome.exceptionOrNull()
                ?: IOException("双仓库刷新均失败"))
        }
        ShiguangRefreshResult(
            changed = (shiguangResult?.changed == true) || (aiDockResult?.changed == true),
            adapterCount = (shiguangResult?.adapterCount ?: 0) + (aiDockResult?.adapterCount ?: 0)
        )
    }

    /** Atomically rechecks the seven-day TTL so concurrent page entries cannot queue downloads. */
    suspend fun refreshIfStale(context: Context): ShiguangRefreshResult? = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val (shiguangResult, aiDockResult) = coroutineScope {
            val shiguang = async {
                runCatching {
                    refreshMutex.withLock {
                        if (!shouldRefresh(
                                lastSuccessfulRefreshMillis = preferences(appContext).getLong(LastSuccessfulRefreshKey, 0L),
                                nowMillis = System.currentTimeMillis(),
                                hasValidRemoteIndex = hasValidRemoteIndex(appContext)
                            )
                        ) null else refreshLocked(appContext)
                    }
                }.getOrNull()
            }
            val aiDock = async {
                runCatching { AiDockWarehouseUpdater.refreshIfStale(appContext) }.getOrNull()
            }
            shiguang.await() to aiDock.await()
        }
        if (shiguangResult == null && aiDockResult == null) return@withContext null
        ShiguangRefreshResult(
            changed = (shiguangResult?.changed == true) || (aiDockResult?.changed == true),
            adapterCount = (shiguangResult?.adapterCount ?: 0) + (aiDockResult?.adapterCount ?: 0)
        )
    }

    internal fun shouldRefresh(
        lastSuccessfulRefreshMillis: Long,
        nowMillis: Long,
        hasValidRemoteIndex: Boolean
    ): Boolean {
        if (!hasValidRemoteIndex || lastSuccessfulRefreshMillis <= 0L) return true
        val ageMillis = nowMillis - lastSuccessfulRefreshMillis
        return ageMillis < 0L || ageMillis >= RefreshIntervalMillis
    }

    suspend fun resolveRemoteScript(context: Context, adapter: EduAdapter): String =
        withContext(Dispatchers.IO) {
            refreshMutex.withLock {
                val relativePath = resourceRelativePath(adapter)
                val snapshot = currentRemoteSnapshot(context)
                val existsInCurrentSnapshot = snapshot.adapters.any { current ->
                    current.school.id == adapter.school.id &&
                        current.adapterId == adapter.adapterId &&
                        resourceRelativePath(current) == relativePath
                }
                val selectedGeneration = if (existsInCurrentSnapshot) {
                    snapshot.indexSha
                } else {
                    adapter.warehouseGeneration.takeIf { it.matches(Regex("[0-9a-f]{64}")) }
                        ?: snapshot.indexSha
                }
                val target = safeResourceFile(context, selectedGeneration, relativePath)
                // JS hotfixes do not necessarily change the protocol index. Revalidate each run.

                val encodedPath = relativePath.split('/').joinToString("/") { segment ->
                    URLEncoder.encode(segment, Charsets.UTF_8.name()).replace("+", "%20")
                }
                val bytes = runCatching {
                    downloadWithAntiHotlink(resourceBaseUrl(context) + encodedPath, MaxDownloadBytes)
                }.getOrElse { error ->
                    if (target.isFile && !isGiteeAntiHotlinkPage(target.readBytes())) {
                        return@withLock target.readText(Charsets.UTF_8)
                    }
                    throw error
                }
                require(bytes.isNotEmpty()) { "远端拾光脚本为空：$relativePath" }
                atomicWrite(target, bytes)
                bytes.toString(Charsets.UTF_8)
            }
        }

    internal fun resourceRelativePath(adapter: EduAdapter): String {
        val assetPath = adapter.assetJsPath.ifBlank { "${adapter.adapterId}.js" }
        val path = if ('/' in assetPath) {
            assetPath
        } else {
            "${adapter.school.folder}/$assetPath"
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

    private fun currentRemoteSnapshot(context: Context) =
        ShiguangWarehouse.parseProtocolV2Snapshot(cachedIndexFile(context).readBytes())

    private fun safeResourceFile(context: Context, generation: String, relativePath: String): File {
        require(generation.matches(Regex("[0-9a-f]{64}"))) { "非法拾光缓存 generation" }
        val resources = File(cacheRoot(context), "resources").apply { mkdirs() }
        val generationRoot = File(resources, generation).apply { mkdirs() }
        val target = File(generationRoot, relativePath)
        val rootPath = generationRoot.canonicalFile.toPath()
        val targetPath = target.canonicalFile.toPath()
        require(targetPath.startsWith(rootPath)) { "拾光脚本路径越界：$relativePath" }
        return target
    }

    private fun preferences(context: Context) =
        context.applicationContext.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)

    private fun refreshLocked(context: Context): ShiguangRefreshResult {
        val bytes = downloadWithAntiHotlink(indexUrl(context), MaxDownloadBytes)
        val snapshot = ShiguangWarehouse.parseProtocolV2Snapshot(bytes)
        val previousSha = preferences(context).getString(IndexShaKey, null)
        atomicWrite(cachedIndexFile(context), bytes)
        val committed = preferences(context).edit()
            .putLong(LastSuccessfulRefreshKey, System.currentTimeMillis())
            .putString(IndexShaKey, snapshot.indexSha)
            .putString(VersionIdKey, snapshot.versionId)
            .commit()
        check(committed) { "无法保存拾光仓库刷新时间" }
        return ShiguangRefreshResult(
            changed = previousSha != snapshot.indexSha,
            adapterCount = snapshot.adapters.size
        )
    }

    internal fun downloadWithAntiHotlink(url: String, maxBytes: Long): ByteArray {
        var currentUrl = url
        repeat(2) { attempt ->
            val bytes = download(currentUrl, maxBytes)
            if (bytes.isEmpty()) throw IOException("仓库响应为空")
            if (attempt == 0 && isGiteeAntiHotlinkPage(bytes)) {
                currentUrl = extractGiteeSignedUrl(bytes)
                    ?: throw IOException("无法解析 Gitee 防盗链签名链接")
                return@repeat
            }
            return bytes
        }
        throw IOException("Gitee 防盗链跳转次数过多")
    }

    internal fun isGiteeAntiHotlinkPage(bytes: ByteArray): Boolean {
        if (bytes.isEmpty() || bytes.size > 1024) return false
        val head = bytes.toString(Charsets.UTF_8).trimStart()
        return head.startsWith("<") && head.contains("raw.giteeusercontent.com")
    }

    internal fun extractGiteeSignedUrl(bytes: ByteArray): String? {
        val html = bytes.toString(Charsets.UTF_8)
        val match = Regex("href=\"([^\"]+)\"").find(html) ?: return null
        return match.groupValues[1].replace("&amp;", "&")
    }

    internal fun atomicWrite(target: File, bytes: ByteArray) {
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

    private fun download(url: String, maxBytes: Long): ByteArray {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.useCaches = false
        connection.setRequestProperty("Cache-Control", "no-cache")
        return try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 25_000
            // Gitee's stable repository URL responds with 302 and a short-lived signed
            // raw.giteeusercontent.com URL. Following that HTTPS redirect is required for both
            // the protocol-v2 index and adapter scripts; the signed destination must not be
            // persisted because Gitee rotates it.
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("Accept", "application/octet-stream, text/javascript, application/json, */*")
            connection.setRequestProperty("User-Agent", "SleepDown-Schedule/ShiguangWarehouse")
            val status = connection.responseCode
            if (status !in 200..299) throw IOException("仓库请求失败：HTTP $status")
            connection.inputStream.use { input ->
                val out = ByteArrayOutputStream()
                val buf = ByteArray(8192)
                var total = 0L
                while (true) {
                    val read = input.read(buf)
                    if (read < 0) break
                    total += read
                    if (total > maxBytes) {
                        throw IOException("仓库响应超过大小限制 (${maxBytes / 1024} KiB)")
                    }
                    out.write(buf, 0, read)
                }
                out.toByteArray()
            }
        } finally {
            connection.disconnect()
        }
    }
}
