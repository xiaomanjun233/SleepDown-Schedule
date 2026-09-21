package com.xiaomanjun.sleepdownschedule.feature.schedule.autorefresh

import android.content.Context
import com.xiaomanjun.sleepdownschedule.feature.importing.EduAdapter
import com.xiaomanjun.sleepdownschedule.feature.importing.EduSchool
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
    private const val SwuKey = "SWU/SWU_01"
    private const val SwuDirectAsset = "auto_refresh/swu_direct_api.js"

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
        SwuKey,
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
        val warehouseAdapters = ShiguangWarehouse.loadAdapters(context)
        val upstreamSwu = warehouseAdapters.firstOrNull(::isSwuDirectAdapter)
        val candidates = (warehouseAdapters.filterNot(::isSwuDirectAdapter) +
            swuDirectVariant(upstreamSwu ?: fallbackSwuAdapter()))
            .filter { adapter -> "${adapter.school.id}/${adapter.adapterId}" in auditedKeys }
            .filter { it.importUrl.startsWith("http://") || it.importUrl.startsWith("https://") }
        buildList {
            for (adapter in candidates) {
                val script = try {
                    resolveScript(context, adapter)
                } catch (_: Exception) {
                    continue
                }
                if (isApiOnlyScript(adapter.school.id, adapter.adapterId, script)) add(adapter)
            }
        }.sortedWith(compareBy<EduAdapter> { it.school.initial }.thenBy { it.school.name }.thenBy { it.adapterName })
    }

    fun find(adapters: List<EduAdapter>, schoolId: String, adapterId: String): EduAdapter? =
        adapters.firstOrNull { it.school.id == schoolId && it.adapterId == adapterId }

    suspend fun resolveScript(context: Context, adapter: EduAdapter): String {
        if (!isSwuDirectAdapter(adapter)) return ShiguangWarehouse.resolveScript(context, adapter)
        return withContext(Dispatchers.IO) {
            context.assets.open(SwuDirectAsset).bufferedReader().use { it.readText() }
        }
    }

    fun isSwuDirectAdapter(adapter: EduAdapter): Boolean =
        isSwuDirectAdapter(adapter.school.id, adapter.adapterId)

    fun isSwuDirectAdapter(schoolId: String, adapterId: String): Boolean =
        "$schoolId/$adapterId" == SwuKey

    internal fun isApiOnlyScript(schoolId: String, adapterId: String, script: String): Boolean {
        if ("$schoolId/$adapterId" !in auditedKeys || script.isBlank()) return false
        val makesStructuredRequest = Regex(
            "\\b(fetch|XMLHttpRequest|axios)\\b|\\$\\s*\\.\\s*ajax\\s*\\(",
            RegexOption.IGNORE_CASE
        ).containsMatchIn(script)
        if (!makesStructuredRequest) return false
        return unsafeDomOrHtmlPatterns.none { it.containsMatchIn(script) }
    }

    private fun swuDirectVariant(adapter: EduAdapter): EduAdapter = adapter.copy(
        adapterName = "西南大学教务（纯接口试验）",
        importUrl = "https://i.swu.edu.cn/",
        description = "登录办事大厅后自动进入教务系统，按当前学期直接请求课表与周历 JSON；校外通常需要校园网或 aTrust。"
    )

    private fun fallbackSwuAdapter(): EduAdapter = EduAdapter(
        school = EduSchool(id = "SWU", name = "西南大学", folder = "SWU", initial = "X"),
        adapterId = "SWU_01",
        adapterName = "西南大学教务",
        category = "BACHELOR_AND_ASSOCIATE",
        assetJsPath = "swu.js",
        importUrl = "https://i.swu.edu.cn/",
        maintainer = "小漫君",
        description = "基于正方新一代教务接口取数。"
    )
}
