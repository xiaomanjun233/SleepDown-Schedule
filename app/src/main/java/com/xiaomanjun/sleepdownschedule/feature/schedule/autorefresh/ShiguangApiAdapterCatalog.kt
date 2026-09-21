package com.xiaomanjun.sleepdownschedule.feature.schedule.autorefresh

import android.content.Context
import com.xiaomanjun.sleepdownschedule.feature.importing.EduAdapter
import com.xiaomanjun.sleepdownschedule.feature.importing.ShiguangWarehouse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * API-only adapters audited against the official Shiguang warehouse.
 *
 * The warehouse schema does not expose an API/HTML flag, so the audited identity list is checked
 * again against the exact cached/bundled script before it is offered. If an upstream update starts
 * reading page DOM or HTML responses, that adapter disappears from this experimental surface.
 */
internal object ShiguangApiAdapterCatalog {
    private val auditedKeys = setOf(
        "CJLU/CJLU",
        "CQUT/CQUT",
        "CUST/CUST_01",
        "FJCPC/FJCPC",
        "GLMU/GLMU_01",
        "GZTRC/GZTRC",
        "HBMU/HBMU_01",
        "HLJU/HLJU",
        "HNSF/HNSF_02",
        "HNZY/HNZY_01",
        "HQU/HQU",
        "HUAT/HUAT",
        "IMU/IMU_01",
        "MMPT/MMPT",
        "NEAU/NEAU_01",
        "SDDFVC/SDDFVC",
        "SDIPCT/SDIPCT",
        "SHZQ/SHZQ_01",
        "SYIST/SYIST_01",
        "TCU/TCU_01",
        "TONGJI/TONGJI_01",
        "UZZ/UZZ_01",
        "XJZFU/XJZFU_01"
    )

    private val unsafeDomOrHtmlPatterns = listOf(
        Regex("\\bDOMParser\\b", RegexOption.IGNORE_CASE),
        Regex("\\.\\s*(querySelector|querySelectorAll|getElementById|getElementsByClassName|getElementsByTagName)\\s*\\(", RegexOption.IGNORE_CASE),
        Regex("\\.\\s*(innerHTML|outerHTML|innerText|textContent)\\b", RegexOption.IGNORE_CASE),
        Regex("\\bdocument\\s*\\.\\s*(body|documentElement)\\b", RegexOption.IGNORE_CASE),
        Regex("\\bresponse\\s*\\.\\s*text\\s*\\(", RegexOption.IGNORE_CASE),
        Regex("text\\s*/\\s*html", RegexOption.IGNORE_CASE),
        Regex("<(html|body|table|thead|tbody|tr|td|th|option)\\b", RegexOption.IGNORE_CASE)
    )

    suspend fun loadSupported(context: Context): List<EduAdapter> = withContext(Dispatchers.IO) {
        val candidates = ShiguangWarehouse.loadAdapters(context)
            .filter { adapter -> "${adapter.school.id}/${adapter.adapterId}" in auditedKeys }
            .filter { it.importUrl.startsWith("http://") || it.importUrl.startsWith("https://") }
        buildList {
            for (adapter in candidates) {
                val script = try {
                    ShiguangWarehouse.resolveScript(context, adapter)
                } catch (_: Exception) {
                    continue
                }
                if (isApiOnlyScript(adapter.school.id, adapter.adapterId, script)) add(adapter)
            }
        }.sortedWith(compareBy<EduAdapter> { it.school.initial }.thenBy { it.school.name }.thenBy { it.adapterName })
    }

    fun find(adapters: List<EduAdapter>, schoolId: String, adapterId: String): EduAdapter? =
        adapters.firstOrNull { it.school.id == schoolId && it.adapterId == adapterId }

    internal fun isApiOnlyScript(schoolId: String, adapterId: String, script: String): Boolean {
        if ("$schoolId/$adapterId" !in auditedKeys || script.isBlank()) return false
        val makesStructuredRequest = Regex(
            "\\b(fetch|XMLHttpRequest|axios)\\b|\\$\\s*\\.\\s*ajax\\s*\\(",
            RegexOption.IGNORE_CASE
        ).containsMatchIn(script)
        if (!makesStructuredRequest) return false
        return unsafeDomOrHtmlPatterns.none { it.containsMatchIn(script) }
    }
}
