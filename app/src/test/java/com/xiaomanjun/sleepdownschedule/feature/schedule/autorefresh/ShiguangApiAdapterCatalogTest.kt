package com.xiaomanjun.sleepdownschedule.feature.schedule.autorefresh

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import com.xiaomanjun.sleepdownschedule.feature.importing.EduAdapter
import com.xiaomanjun.sleepdownschedule.feature.importing.EduSchool

class ShiguangApiAdapterCatalogTest {
    private val assets = File("src/main/assets")
    private fun catalog() = ShiguangApiAdapterCatalog.parseCatalog(File(assets, "auto_refresh/catalog.tsv").readText())
    private fun source(adapter: com.xiaomanjun.sleepdownschedule.feature.importing.EduAdapter) =
        File(assets, "shiguang_warehouse-main/resources/${adapter.school.folder}/${adapter.assetJsPath}").readText()

    @Test fun reviewedSourcesMatchCatalogIncludingNewSchools() {
        val adapters = catalog()
        assertEquals(142, adapters.size)
        assertEquals(130, adapters.map { it.school.id }.distinct().size)
        adapters.forEach { assertTrue(it.displayName, ShiguangApiAdapterCatalog.matchesReviewedSource(it, source(it))) }
    }

    @Test fun htmlMetadataAndCampusNetworkEntriesAreIncluded() {
        val adapters = catalog()
        listOf("SWU", "UZZ", "CUG", "AUFE", "GUIT", "CDUTCM", "WBU", "SICNU").forEach { id ->
            assertTrue(id, adapters.any { it.school.id == id })
        }
        val swu = adapters.first { it.school.id == "SWU" }
        assertTrue(source(swu).contains("DOMParser") || source(swu).contains("querySelector"))
        assertTrue(adapters.any { it.school.id == "AUFE" && it.importUrl.contains("vpn") })
        assertFalse(adapters.any { it.school.id in setOf("BUPT", "FAFU", "CQUST", "HIIT") })
        // Monthly results must not replace a full semester; offer the school's semester adapter.
        assertFalse(adapters.any { it.school.id == "HNSF" && it.adapterId == "HNSF_02" })
        assertTrue(adapters.any { it.school.id == "HNSF" && it.adapterId == "HNSF_01" })
    }

    @Test fun unreviewedSourceChangesAreRejectedButLineEndingsArePortable() {
        val adapter = catalog().first()
        val original = source(adapter)
        val crlfSource = original.replace("\r\n", "\n").replace("\n", "\r\n")
        assertTrue(ShiguangApiAdapterCatalog.matchesReviewedSource(adapter, crlfSource))
        assertFalse(ShiguangApiAdapterCatalog.matchesReviewedSource(adapter, original + "\nfetch('/changed');"))
        assertFalse(ShiguangApiAdapterCatalog.matchesReviewedSource(adapter, ""))
    }

    @Test fun newWarehouseScriptsNeedStructuredApiCourseData() {
        val candidate = EduAdapter(
            EduSchool("NEW", "新学校", "NEW"), "NEW_01", "新教务", "BACHELOR_AND_ASSOCIATE",
            "new.js", "https://school.example.edu", "", ""
        )
        assertTrue(ShiguangApiAdapterCatalog.isLikelyApiAdapter(candidate,
            "const response = await fetch('/api/timetable'); const data = await response.json(); window.shiguangBridge.addCourse(data);"))
        assertFalse(ShiguangApiAdapterCatalog.isLikelyApiAdapter(candidate,
            "const rows = document.querySelectorAll('table tr'); window.shiguangBridge.addCourse(rows);"))
        assertFalse(ShiguangApiAdapterCatalog.isLikelyApiAdapter(candidate,
            "const response = await fetch('/semester'); const html = await response.text(); document.querySelector('table'); window.shiguangBridge.addCourse(html);"))
        assertFalse(ShiguangApiAdapterCatalog.isLikelyApiAdapter(candidate,
            "async function selectMonth() {} const response = await fetch('/api/timetable'); const data = await response.json(); window.shiguangBridge.addCourse(data);"))
        val reviewed = catalog().first { it.school.id == "SWU" }
        assertTrue(ShiguangApiAdapterCatalog.isLikelyApiAdapter(reviewed, source(reviewed)))
    }

    @Test fun changedExistingAdapterMetadataTriggersReinspection() {
        val bundled = catalog().first()
        assertFalse(ShiguangApiAdapterCatalog.metadataChanged(
            bundled, bundled.copy(warehouseGeneration = "new-index")
        ))
        assertTrue(ShiguangApiAdapterCatalog.metadataChanged(
            bundled, bundled.copy(assetJsPath = "updated.js")
        ))
    }

    @Test fun sharedWarehouseMetadataDoesNotLoseReviewedApiCapability() {
        val reviewed = catalog()
        val swu = reviewed.first { it.school.id == "SWU" }
        assertTrue(ShiguangApiAdapterCatalog.supportsAutomaticRefresh(
            swu.copy(adapterName = "上游新名称", warehouseGeneration = "current-index-generation"), reviewed
        ))
        val semester = reviewed.first { it.school.id == "HNSF" && it.adapterId == "HNSF_01" }
        assertFalse(ShiguangApiAdapterCatalog.supportsAutomaticRefresh(
            semester.copy(adapterId = "HNSF_02"), reviewed
        ))
        assertFalse(ShiguangApiAdapterCatalog.supportsAutomaticRefresh(
            swu.copy(adapterId = "unreviewed-entry"), reviewed
        ))
    }
}
