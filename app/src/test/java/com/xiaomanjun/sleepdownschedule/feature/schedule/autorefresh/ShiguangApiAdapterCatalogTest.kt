package com.xiaomanjun.sleepdownschedule.feature.schedule.autorefresh

import com.xiaomanjun.sleepdownschedule.feature.importing.EduAdapter
import com.xiaomanjun.sleepdownschedule.feature.importing.EduSchool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest

class ShiguangApiAdapterCatalogTest {
    private val resources = File("src/main/assets/shiguang_warehouse-main/resources")

    private fun adapter(schoolId: String, assetJsPath: String) = EduAdapter(
        EduSchool(schoolId, schoolId, schoolId), "${schoolId}_01", schoolId,
        "BACHELOR_AND_ASSOCIATE", assetJsPath, "https://school.example.edu", "", ""
    )

    private fun source(adapter: EduAdapter) =
        File(resources, "${adapter.school.folder}/${adapter.assetJsPath}").readText()

    @Test fun htmlCourseRequestsAreEligibleWithoutAReviewedCatalogEntry() {
        listOf(
            adapter("SICNU", "school.js"),
            adapter("CUP", "cup_02.js"),
            adapter("HIIT", "hiit_01.js")
        ).forEach { candidate ->
            assertTrue(candidate.school.id, ShiguangApiAdapterCatalog.isLikelyApiAdapter(
                candidate, source(candidate)
            ))
        }
    }

    @Test fun domOnlyPagesKeepTheManualWebViewRoute() {
        val candidate = adapter("BUPT", "bupt_01.js")
        assertFalse(ShiguangApiAdapterCatalog.isLikelyApiAdapter(candidate, source(candidate)))
        val monthly = adapter("HNSF", "hnsf_02.js")
        assertFalse(ShiguangApiAdapterCatalog.isLikelyApiAdapter(monthly, source(monthly)))
    }

    @Test fun monthlyAndMissingCourseRequestsAreNotUnattendedImports() {
        val candidate = adapter("NEW", "new.js")
        assertTrue(ShiguangApiAdapterCatalog.isLikelyApiAdapter(candidate,
            "const html = await fetch('/semester').then(r => r.text()); window.shiguangBridge.saveImportedCourses(html);"))
        assertFalse(ShiguangApiAdapterCatalog.isLikelyApiAdapter(candidate,
            "const rows = document.querySelectorAll('table tr'); window.shiguangBridge.saveImportedCourses(rows);"))
        assertFalse(ShiguangApiAdapterCatalog.isLikelyApiAdapter(candidate,
            "async function selectMonth() {} const html = await fetch('/course').then(r => r.text()); window.shiguangBridge.saveImportedCourses(html);"))
        assertFalse(ShiguangApiAdapterCatalog.isLikelyApiAdapter(candidate,
            "const html = await fetch('/course').then(r => r.text());"))
    }

    @Test fun debugScriptFingerprintStillRejectsChangedSource() {
        val source = "window.shiguangBridge.showToast('test');\n"
        val digest = MessageDigest.getInstance("SHA-256").digest(source.toByteArray())
            .joinToString("") { "%02x".format(it) }
        val reviewed = adapter("TEST", "test.js").copy(warehouseGeneration = digest)
        assertTrue(ShiguangApiAdapterCatalog.matchesReviewedSource(reviewed, source.replace("\n", "\r\n")))
        assertFalse(ShiguangApiAdapterCatalog.matchesReviewedSource(reviewed, source + "fetch('/changed');"))
        assertEquals(1, ShiguangApiAdapterCatalog.parseCatalog(
            listOf("TEST", "Test", "TEST", "T", "TEST_01", "Test", "BACHELOR_AND_ASSOCIATE",
                "test.js", "https://school.example.edu", "", "", digest).joinToString("\t")
        ).size)
    }

    @Test fun loginSelectionMatchesTheSchoolAndEntranceWithoutPreclassifyingEveryScript() {
        val supported = adapter("CUP", "cup_02.js")
        assertEquals(supported, ShiguangApiAdapterCatalog.find(listOf(supported), "CUP", "CUP_01"))
        assertEquals(null, ShiguangApiAdapterCatalog.find(listOf(supported), "CUP", "CUP_02"))
        assertEquals(null, ShiguangApiAdapterCatalog.find(listOf(supported), "OTHER", "CUP_01"))
    }
}
