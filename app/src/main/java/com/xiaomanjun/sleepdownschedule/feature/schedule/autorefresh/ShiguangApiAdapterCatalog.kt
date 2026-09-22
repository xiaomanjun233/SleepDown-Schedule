package com.xiaomanjun.sleepdownschedule.feature.schedule.autorefresh

import android.content.Context
import com.xiaomanjun.sleepdownschedule.feature.importing.EduAdapter
import com.xiaomanjun.sleepdownschedule.feature.importing.EduSchool
import com.xiaomanjun.sleepdownschedule.feature.importing.ShiguangWarehouse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest

/**
 * Reviewed official adapters whose course data comes from an API. HTML metadata is allowed.
 * Pin the reviewed source: the upstream schema has no course-data-source flag, and a DOM/text
 * keyword filter cannot distinguish metadata from HTML timetable parsing.
 */
internal object ShiguangApiAdapterCatalog {
    suspend fun loadSupported(context: Context): List<EduAdapter> = withContext(Dispatchers.IO) {
        parseCatalog(context.assets.open("auto_refresh/catalog.tsv").bufferedReader().use { it.readText() })
    }

    internal fun supportsAutomaticRefresh(adapter: EduAdapter, reviewed: List<EduAdapter>): Boolean =
        reviewed.any { it.school.id == adapter.school.id && it.adapterId == adapter.adapterId }

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
        val source = ShiguangWarehouse.resolveBundledScript(context, adapter)
        require(matchesReviewedSource(adapter, source)) { "教务适配器校验失败，请更新应用后重试" }
        return source
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
