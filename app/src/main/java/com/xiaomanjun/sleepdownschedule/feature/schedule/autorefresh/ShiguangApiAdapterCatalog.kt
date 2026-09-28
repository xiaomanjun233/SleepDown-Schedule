package com.xiaomanjun.sleepdownschedule.feature.schedule.autorefresh

import android.content.Context
import com.xiaomanjun.sleepdownschedule.BuildConfig
import com.xiaomanjun.sleepdownschedule.feature.importing.EduAdapter
import com.xiaomanjun.sleepdownschedule.feature.importing.EduSchool
import com.xiaomanjun.sleepdownschedule.feature.importing.ShiguangWarehouse
import com.xiaomanjun.sleepdownschedule.feature.importing.shiguang.ShiguangWarehouseUpdater
import com.xiaomanjun.sleepdownschedule.feature.importing.isAiEduImportTool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest

/** Detect unattended course fetches from the active Shiguang scripts, including HTML responses. */
internal object ShiguangApiAdapterCatalog {
    private fun loadLocalReviewed(context: Context): List<EduAdapter> {
        if (!BuildConfig.SLEEPDOWN_LOCAL_EDU_TEST) return emptyList()
        // A developer-provided Debug script still needs its own exact source fingerprint.
        val testRoot = "edu_adapter_test"
        if (context.assets.list(testRoot)?.contains("auto_refresh.tsv") != true) return emptyList()
        return parseCatalog(
            context.assets.open("$testRoot/auto_refresh.tsv").bufferedReader().use { it.readText() }
        )
    }

    suspend fun loadLoginAdapters(context: Context): List<EduAdapter> = withContext(Dispatchers.IO) {
        ShiguangWarehouse.loadVisibleAdapters(context).filterNot(EduAdapter::isAiEduImportTool)
    }

    suspend fun loadSupported(context: Context): List<EduAdapter> = withContext(Dispatchers.IO) {
        runCatching { ShiguangWarehouseUpdater.refreshIfStale(context) }
        val localReviewed = loadLocalReviewed(context).associateBy(::key)
        val bundledByKey = ShiguangWarehouse.loadBundledAdapters(context).associateBy(::key)
        val hasRemoteIndex = ShiguangWarehouseUpdater.hasValidRemoteIndex(context)
        ShiguangWarehouse.loadVisibleAdapters(context)
            .filterNot(EduAdapter::isAiEduImportTool)
            .filter { it.importUrl.startsWith("https://") || it.importUrl.startsWith("http://") }
            .mapNotNull { adapter ->
                val source = runCatching {
                    if (ShiguangWarehouse.isLocalTestAdapter(adapter)) {
                        ShiguangWarehouse.resolveScript(context, adapter)
                    } else {
                        val cached = if (hasRemoteIndex) runCatching {
                            ShiguangWarehouseUpdater.cachedScriptFile(context, adapter)
                        }.getOrNull() else null
                        val bundled = bundledByKey[key(adapter)]
                        when {
                            cached?.isFile == true -> cached.readText()
                            bundled != null && bundled.school.folder == adapter.school.folder &&
                                bundled.assetJsPath == adapter.assetJsPath ->
                                ShiguangWarehouse.resolveBundledScript(context, bundled)
                            else -> ShiguangWarehouseUpdater.resolveRemoteScript(context, adapter)
                        }
                    }
                }.getOrNull() ?: return@mapNotNull null
                val localSourceValid = !ShiguangWarehouse.isLocalTestAdapter(adapter) ||
                    localReviewed[key(adapter)]?.let { matchesReviewedSource(it, source) } == true
                adapter.takeIf { localSourceValid && isLikelyApiAdapter(adapter, source) }
            }
            .distinctBy(::key)
    }

    private fun key(adapter: EduAdapter) = adapter.school.id to adapter.adapterId

    internal fun supportsAutomaticRefresh(adapter: EduAdapter, supported: List<EduAdapter>): Boolean =
        supported.any { it.school.id == adapter.school.id && it.adapterId == adapter.adapterId }

    internal fun parseCatalog(text: String): List<EduAdapter> = text.lineSequence()
        .filter { it.isNotBlank() && !it.startsWith("#") }
        .map { line ->
            val fields = line.split('\t')
            require(fields.size == 12) { "自动刷新学校索引格式错误" }
            require(fields[8].startsWith("https://") || fields[8].startsWith("http://"))
            require(fields[11].matches(Regex("[0-9a-f]{64}")))
            EduAdapter(
                school = EduSchool(fields[0], fields[1], fields[2], fields[3]),
                adapterId = fields[4],
                adapterName = fields[5],
                category = fields[6],
                assetJsPath = fields[7],
                importUrl = fields[8],
                maintainer = fields[9],
                description = fields[10],
                warehouseGeneration = fields[11]
            )
        }.toList().also { adapters ->
            require(adapters.map { "${it.school.id}/${it.adapterId}" }.distinct().size == adapters.size)
        }

    fun find(adapters: List<EduAdapter>, schoolId: String, adapterId: String): EduAdapter? =
        adapters.firstOrNull { it.school.id == schoolId && it.adapterId == adapterId }

    suspend fun resolveScript(context: Context, adapter: EduAdapter): String {
        val source = ShiguangWarehouse.resolveScript(context, adapter)
        if (ShiguangWarehouse.isLocalTestAdapter(adapter)) {
            val reviewed = loadLocalReviewed(context).firstOrNull { key(it) == key(adapter) }
            require(reviewed != null && matchesReviewedSource(reviewed, source)) {
                "本地测试适配器尚未通过自动刷新校验"
            }
        }
        require(isLikelyApiAdapter(adapter, source)) { "该教务入口需要打开网页手动刷新课表" }
        return source
    }

    /** A course request may return JSON or HTML; DOM-only page scraping stays in the visible flow. */
    internal fun isLikelyApiAdapter(adapter: EduAdapter, source: String): Boolean {
        if (source.isBlank() || !source.contains("shiguangBridge") ||
            !source.contains("saveImportedCourses")) return false
        // Month-only imports cannot replace a whole semester during unattended refresh.
        if (Regex("选择.{0,8}月份|仅.{0,8}本月|selectMonth\\s*\\(", RegexOption.IGNORE_CASE)
                .containsMatchIn(source + adapter.description)) return false
        val network = Regex("\\bfetch\\s*\\(|\\baxios\\s*\\.|\\bXMLHttpRequest\\b|\\$\\.(?:ajax|get|post|getJSON)\\s*\\(")
            .containsMatchIn(source)
        return network
    }

    internal fun matchesReviewedSource(adapter: EduAdapter, source: String): Boolean {
        if (source.isBlank()) return false
        val canonical = source.replace("\r\n", "\n").trimEnd() + "\n"
        val digest = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return digest == adapter.warehouseGeneration
    }

    fun isSwuAdapter(adapter: EduAdapter): Boolean = adapter.school.id == "SWU"
}
