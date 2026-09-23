package com.xiaomanjun.sleepdownschedule.feature.schedule.autorefresh

import android.content.Context
import com.xiaomanjun.sleepdownschedule.feature.importing.EduAdapter
import com.xiaomanjun.sleepdownschedule.feature.importing.EduSchool
import com.xiaomanjun.sleepdownschedule.feature.importing.ShiguangWarehouse
import com.xiaomanjun.sleepdownschedule.feature.importing.shiguang.ShiguangWarehouseUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest

/** Reviewed bundled adapters plus new API adapters verified from the synchronized warehouse. */
internal object ShiguangApiAdapterCatalog {
    suspend fun loadSupported(context: Context): List<EduAdapter> = withContext(Dispatchers.IO) {
        // The school picker updates this cache too. Check its TTL here so this page can discover
        // newly published schools without requiring a visit to the regular import screen first.
        runCatching { ShiguangWarehouseUpdater.refreshIfStale(context) }
        val reviewed = parseCatalog(
            context.assets.open("auto_refresh/catalog.tsv").bufferedReader().use { it.readText() }
        )
        val official = ShiguangWarehouse.loadAdapters(context)
        val currentByKey = official.associateBy(::key)
        val bundledByKey = ShiguangWarehouse.loadBundledAdapters(context).associateBy(::key)
        val hasRemoteIndex = ShiguangWarehouseUpdater.hasValidRemoteIndex(context)
        val reviewedByKey = reviewed.associateBy(::key)
        val supported = reviewed.mapNotNull { approved ->
            // The catalog SHA is a review of the shipped script. An updated remote script is
            // checked again by resolveScript before any login or background refresh runs.
            currentByKey[key(approved)]?.takeIf { it.importUrl.startsWith("http") }
        }.toMutableList()
        official.asSequence()
            .filter { key(it) !in reviewedByKey }
            .filter { it.importUrl.startsWith("https://") || it.importUrl.startsWith("http://") }
            .filter { candidate ->
                val bundled = bundledByKey[key(candidate)]
                bundled == null || metadataChanged(bundled, candidate) ||
                    (hasRemoteIndex && runCatching {
                        ShiguangWarehouseUpdater.cachedScriptFile(context, candidate).isFile
                    }.getOrDefault(false))
            }
            .forEach { candidate ->
                // A failed download leaves this one school unavailable; it must not hide the
                // already verified schools or turn every new entry into a manual adapter.
                val source = runCatching {
                    ShiguangWarehouseUpdater.resolveRemoteScript(context, candidate)
                }.getOrNull()
                if (source != null && isLikelyApiAdapter(candidate, source)) supported += candidate
            }
        supported.distinctBy(::key)
    }

    private fun key(adapter: EduAdapter) = adapter.school.id to adapter.adapterId

    internal fun metadataChanged(bundled: EduAdapter, current: EduAdapter): Boolean =
        bundled.assetJsPath != current.assetJsPath ||
            bundled.importUrl != current.importUrl ||
            bundled.adapterName != current.adapterName ||
            bundled.description != current.description ||
            bundled.category != current.category

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
        val reviewed = parseCatalog(
            context.assets.open("auto_refresh/catalog.tsv").bufferedReader().use { it.readText() }
        ).firstOrNull { key(it) == key(adapter) }
        val source = if (ShiguangWarehouseUpdater.hasValidRemoteIndex(context)) {
            runCatching { ShiguangWarehouseUpdater.resolveRemoteScript(context, adapter) }
                .getOrElse { error ->
                    if (reviewed == null) throw error
                    ShiguangWarehouse.resolveBundledScript(context, reviewed)
                }
        } else {
            requireNotNull(reviewed) { "新适配器的云端脚本暂不可用，请联网后重试" }
            ShiguangWarehouse.resolveBundledScript(context, reviewed)
        }
        require(
            (reviewed != null && matchesReviewedSource(reviewed, source)) ||
                isLikelyApiAdapter(adapter, source)
        ) { "教务适配器已更改，当前脚本无法确认可安全自动刷新" }
        return source
    }

    /** Positive API evidence is required; ordinary DOM metadata lookup alone is not disqualifying. */
    internal fun isLikelyApiAdapter(adapter: EduAdapter, source: String): Boolean {
        if (source.isBlank() || !source.contains("shiguangBridge")) return false
        // Month-only imports cannot replace a whole semester during unattended refresh.
        if (Regex("选择.{0,8}月份|仅.{0,8}本月|selectMonth\\s*\\(", RegexOption.IGNORE_CASE)
                .containsMatchIn(source + adapter.description)) return false
        val network = Regex("\\bfetch\\s*\\(|\\baxios\\s*\\.|\\bXMLHttpRequest\\b|\\$\\.ajax\\s*\\(|\\$\\.getJSON\\s*\\(")
            .containsMatchIn(source)
        val structuredResponse = Regex("\\.json\\s*\\(|JSON\\.parse\\s*\\(|\\$\\.getJSON\\s*\\(|responseType\\s*=\\s*['\"]json['\"]")
            .containsMatchIn(source)
        return network && structuredResponse
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
