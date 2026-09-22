package com.xiaomanjun.sleepdownschedule.feature.schedule.autorefresh

import org.junit.Assert.*
import org.junit.Test
import java.io.File

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
        assertTrue(ShiguangApiAdapterCatalog.matchesReviewedSource(adapter, original.replace("\n", "\r\n")))
        assertFalse(ShiguangApiAdapterCatalog.matchesReviewedSource(adapter, original + "\nfetch('/changed');"))
        assertFalse(ShiguangApiAdapterCatalog.matchesReviewedSource(adapter, ""))
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
