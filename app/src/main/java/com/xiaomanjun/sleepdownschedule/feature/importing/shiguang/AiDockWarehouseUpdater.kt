package com.xiaomanjun.sleepdownschedule.feature.importing.shiguang

import android.content.Context
import com.xiaomanjun.sleepdownschedule.feature.importing.EduAdapter
import com.xiaomanjun.sleepdownschedule.feature.importing.EduAdapterSource
import com.xiaomanjun.sleepdownschedule.feature.importing.EduSchool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * edu-ai-dock dual-repository updater:
 * - GitHub: https://raw.githubusercontent.com/HaoZai000/edu-ai-dock/main
 * - Gitee:  https://gitee.com/com_haooz_account/edu-ai-dock/raw/main
 *
 * Follows the platform priority chosen in [ShiguangWarehouseUpdater], falls back to the mirror
 * on failure, remembers [lastGoodBase], and safely isolates cached index/scripts.
 */
internal object AiDockWarehouseUpdater {
    const val GITHUB_BASE = "https://raw.githubusercontent.com/HaoZai000/edu-ai-dock/main"
    const val GITEE_BASE = "https://gitee.com/com_haooz_account/edu-ai-dock/raw/main"

    private const val CacheDirectoryName = "aidock_warehouse"
    private const val IndexFileName = "school_index.json"
    private const val IndexRemotePath = "index/school_index.json"
    private const val BundledAssetPath = "eduloader/school_index_ai.json"
    private const val PreferencesName = "aidock_warehouse_metadata"
    private const val LastSuccessfulRefreshKey = "last_successful_refresh"
    private const val ClientIndexVersion = 1
    private const val MaxIndexBytes = 4L * 1024L * 1024L
    private const val MaxScriptBytes = 1L * 1024L * 1024L
    private val RefreshIntervalMillis = TimeUnit.DAYS.toMillis(7)
    private val refreshMutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }

    @Volatile
    private var lastGoodBase: String? = null

    fun cachedIndexFile(context: Context): File = File(cacheRoot(context), IndexFileName)

    fun isRefreshStale(context: Context, nowMillis: Long = System.currentTimeMillis()): Boolean {
        val lastSuccess = preferences(context).getLong(LastSuccessfulRefreshKey, 0L)
        val hasValid = loadCachedAdapters(context).isNotEmpty()
        return ShiguangWarehouseUpdater.shouldRefresh(
            lastSuccessfulRefreshMillis = lastSuccess,
            nowMillis = nowMillis,
            hasValidRemoteIndex = hasValid
        )
    }

    fun loadCachedAdapters(context: Context): List<EduAdapter> {
        val file = cachedIndexFile(context)
        if (file.isFile) {
            runCatching { parseIndex(file.readBytes()).adapters }
                .getOrNull()
                ?.takeIf { it.isNotEmpty() }
                ?.let { return it }
        }
        return runCatching {
            context.assets.open(BundledAssetPath).use { input ->
                parseIndex(input.readBytes()).adapters
            }
        }.getOrDefault(emptyList())
    }

    suspend fun refresh(context: Context): ShiguangRefreshResult = withContext(Dispatchers.IO) {
        refreshMutex.withLock {
            refreshLocked(context.applicationContext)
        }
    }

    suspend fun refreshIfStale(context: Context): ShiguangRefreshResult? = withContext(Dispatchers.IO) {
        refreshMutex.withLock {
            val app = context.applicationContext
            if (!isRefreshStale(app)) return@withLock null
            refreshLocked(app)
        }
    }

    suspend fun resolveScript(context: Context, adapter: EduAdapter): String = withContext(Dispatchers.IO) {
        refreshMutex.withLock {
            val app = context.applicationContext
            val relativePath = normalizeSafeScriptPath(adapter.assetJsPath)
            val target = safeScriptFile(app, relativePath)
            val encodedPath = relativePath.split('/').joinToString("/") { segment ->
                URLEncoder.encode(segment, Charsets.UTF_8.name()).replace("+", "%20")
            }
            var lastError: Throwable? = null
            for (base in orderedRemotes(app)) {
                val attempt = runCatching {
                    val bytes = ShiguangWarehouseUpdater.downloadWithAntiHotlink("$base/$encodedPath", MaxScriptBytes)
                    require(bytes.isNotEmpty()) { "AI 仓库脚本为空：$relativePath" }
                    ShiguangWarehouseUpdater.atomicWrite(target, bytes)
                    lastGoodBase = base
                    bytes.toString(Charsets.UTF_8)
                }
                attempt.getOrNull()?.let { return@withLock it }
                lastError = attempt.exceptionOrNull()
            }
            if (target.isFile) {
                val cachedBytes = target.readBytes()
                if (cachedBytes.isNotEmpty() && !ShiguangWarehouseUpdater.isGiteeAntiHotlinkPage(cachedBytes)) {
                    return@withLock cachedBytes.toString(Charsets.UTF_8)
                }
            }
            throw (lastError ?: IOException("无法获取 AI 仓库脚本 $relativePath"))
        }
    }

    internal fun orderedRemotes(context: Context): List<String> {
        val preferred = if (ShiguangWarehouseUpdater.isGithubSource(context)) {
            listOf(GITHUB_BASE, GITEE_BASE)
        } else {
            listOf(GITEE_BASE, GITHUB_BASE)
        }
        val good = lastGoodBase ?: return preferred
        return listOf(good) + preferred.filter { it != good }
    }

    internal fun parseIndex(bytes: ByteArray): AiDockIndexSnapshot {
        val root = json.parseToJsonElement(bytes.toString(Charsets.UTF_8)) as? JsonObject
            ?: throw IOException("AI 仓库索引格式无效")
        val protocolVersion = root["protocol_version"]?.jsonPrimitive?.intOrNull ?: ClientIndexVersion
        val versionId = root["version_id"]?.jsonPrimitive?.contentOrNull.orEmpty()
        if (protocolVersion > ClientIndexVersion) {
            return AiDockIndexSnapshot(protocolVersion, versionId, emptyList())
        }
        val schools = (root["schools"] as? JsonArray).orEmpty()
        val adapters = buildList {
            schools.forEach { element ->
                val schoolObj = element as? JsonObject ?: return@forEach
                val schoolId = schoolObj["id"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                val schoolName = schoolObj["name"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                if (schoolId.isBlank() || schoolName.isBlank()) return@forEach
                val initial = schoolObj["initial"]?.jsonPrimitive?.contentOrNull?.trim()
                    .orEmpty()
                    .ifBlank { schoolName.take(1).uppercase() }
                val school = EduSchool(
                    id = schoolId,
                    name = schoolName,
                    folder = schoolId,
                    initial = initial
                )
                val schoolAdapters = (schoolObj["adapters"] as? JsonArray).orEmpty()
                schoolAdapters.forEach { adapterElement ->
                    adapterElement.toAiDockAdapter(school)?.let(::add)
                }
            }
        }
        return AiDockIndexSnapshot(protocolVersion, versionId, adapters)
    }

    internal fun isSafeScriptPath(path: String): Boolean {
        if (path.isBlank()) return false
        if (!path.startsWith("resources/")) return false
        if (!path.endsWith(".js", ignoreCase = true)) return false
        if ('\\' in path || ".." in path) return false
        if (Regex("^[A-Za-z][A-Za-z0-9+.-]*:").containsMatchIn(path)) return false
        val segments = path.split('/')
        return segments.none { it.isBlank() || it == "." || it == ".." }
    }

    private fun normalizeSafeScriptPath(path: String): String {
        val trimmed = path.trim()
        require(isSafeScriptPath(trimmed)) { "非法 AI 仓库脚本路径：$path" }
        return trimmed
    }

    private fun JsonElement.toAiDockAdapter(school: EduSchool): EduAdapter? {
        val obj = this as? JsonObject ?: return null
        val adapterId = obj["adapter_id"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        val scriptPath = obj["asset_js_path"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        if (adapterId.isBlank() || !isSafeScriptPath(scriptPath)) return null
        val adapterName = obj["adapter_name"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            .ifBlank { adapterId }
        val category = parseCategory(obj["category"])
        return EduAdapter(
            school = school,
            adapterId = adapterId,
            adapterName = adapterName,
            category = category,
            assetJsPath = scriptPath,
            importUrl = obj["import_url"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty(),
            maintainer = obj["maintainer"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty(),
            description = obj["description"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty(),
            source = EduAdapterSource.AI_DOCK
        )
    }

    private fun parseCategory(raw: JsonElement?): String {
        val primitive = raw as? JsonPrimitive ?: return "ADAPTER_CATEGORY_UNKNOWN"
        if (primitive.isString) {
            return when (primitive.content.trim().uppercase()) {
                "GENERAL_TOOL" -> "GENERAL_TOOL"
                "BACHELOR_AND_ASSOCIATE" -> "BACHELOR_AND_ASSOCIATE"
                "POSTGRADUATE" -> "POSTGRADUATE"
                else -> "ADAPTER_CATEGORY_UNKNOWN"
            }
        }
        return when (primitive.intOrNull) {
            1 -> "GENERAL_TOOL"
            2 -> "BACHELOR_AND_ASSOCIATE"
            3 -> "POSTGRADUATE"
            else -> "ADAPTER_CATEGORY_UNKNOWN"
        }
    }

    private fun refreshLocked(context: Context): ShiguangRefreshResult {
        val cached = runCatching {
            cachedIndexFile(context).takeIf { it.isFile }?.readBytes()?.let(::parseIndex)?.adapters
        }.getOrNull()
        var lastError: Throwable? = null
        for (base in orderedRemotes(context)) {
            val attempt = runCatching {
                val bytes = ShiguangWarehouseUpdater.downloadWithAntiHotlink("$base/$IndexRemotePath", MaxIndexBytes)
                val remote = parseIndex(bytes)
                if (remote.protocolVersion > ClientIndexVersion) {
                    throw IOException("AI 脚本索引协议版本过高：v${remote.protocolVersion}")
                }
                if (remote.adapters.isEmpty() && remote.versionId.isBlank()) {
                    throw IOException("AI 脚本索引为空，跳过覆盖")
                }
                lastGoodBase = base
                val changed = cached != remote.adapters
                if (changed) {
                    ShiguangWarehouseUpdater.atomicWrite(cachedIndexFile(context), bytes)
                }
                val committed = preferences(context).edit()
                    .putLong(LastSuccessfulRefreshKey, System.currentTimeMillis())
                    .commit()
                check(committed) { "无法保存 AI 脚本仓库刷新时间" }
                ShiguangRefreshResult(changed = changed, adapterCount = remote.adapters.size)
            }
            attempt.getOrNull()?.let { return it }
            lastError = attempt.exceptionOrNull()
        }
        throw (lastError ?: IOException("AI 脚本索引下载失败"))
    }

    private fun cacheRoot(context: Context): File =
        File(context.filesDir, CacheDirectoryName).apply { mkdirs() }

    private fun safeScriptFile(context: Context, relativePath: String): File {
        val scriptsRoot = File(cacheRoot(context), "scripts").apply { mkdirs() }
        val target = File(scriptsRoot, relativePath)
        val rootPath = scriptsRoot.canonicalFile.toPath()
        val targetPath = target.canonicalFile.toPath()
        require(targetPath.startsWith(rootPath)) { "AI 仓库脚本路径越界：$relativePath" }
        return target
    }

    private fun preferences(context: Context) =
        context.applicationContext.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
}

internal data class AiDockIndexSnapshot(
    val protocolVersion: Int,
    val versionId: String,
    val adapters: List<EduAdapter>
)
