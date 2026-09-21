package com.xiaomanjun.sleepdownschedule.feature.schedule.autorefresh

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ShiguangApiAdapterCatalogTest {
    @Test
    fun bundledAuditedAdaptersRemainApiOnly() {
        val scripts = mapOf(
            "CJLU/CJLU" to "CJLU/cjlu.js",
            "CQUT/CQUT" to "CQUT/cqut_01.js",
            "CUST/CUST_01" to "CUST/cust.js",
            "FJCPC/FJCPC" to "FJCPC/fjcpc.js",
            "GLMU/GLMU_01" to "GLMU/glmu.js",
            "GZTRC/GZTRC" to "GZTRC/gztrc.js",
            "HBMU/HBMU_01" to "HBMU/hbmu.js",
            "HLJU/HLJU" to "HLJU/hlju.js",
            "HNSF/HNSF_02" to "HNSF/hnsf_02.js",
            "HNZY/HNZY_01" to "HNZY/hnzy.js",
            "HQU/HQU" to "HQU/hquadap.js",
            "HUAT/HUAT" to "HUAT/HUAT.js",
            "IMU/IMU_01" to "IMU/imu_01.js",
            "MMPT/MMPT" to "MMPT/mmpt.js",
            "NEAU/NEAU_01" to "NEAU/NEAU_01.js",
            "SDDFVC/SDDFVC" to "SDDFVC/sddfvc.js",
            "SDIPCT/SDIPCT" to "SDIPCT/sdipct.js",
            "SHZQ/SHZQ_01" to "SHZQ/shzq.js",
            "SYIST/SYIST_01" to "SYIST/syist_01.js",
            "TCU/TCU_01" to "TCU/tcu_01.js",
            "TONGJI/TONGJI_01" to "TONGJI/tongji_01.js",
            "UZZ/UZZ_01" to "UZZ/uzz_01.js",
            "XJZFU/XJZFU_01" to "XJZFU/xjzfu.js"
        )
        val resources = File("src/main/assets/shiguang_warehouse-main/resources")
        assertTrue(resources.isDirectory)
        scripts.forEach { (key, relativePath) ->
            val (schoolId, adapterId) = key.split('/')
            val source = File(resources, relativePath).readText()
            assertTrue(
                "$key must remain an API-only adapter",
                ShiguangApiAdapterCatalog.isApiOnlyScript(schoolId, adapterId, source)
            )
        }
    }

    @Test
    fun swuExperimentalVariantUsesOnlyStructuredApis() {
        val source = File("src/main/assets/auto_refresh/swu_direct_api.js").readText()

        assertTrue(ShiguangApiAdapterCatalog.isApiOnlyScript("SWU", "SWU_01", source))
        assertFalse(source.contains("DOMParser"))
        assertFalse(source.contains("querySelector"))
        assertFalse(source.contains("response.text"))
    }

    @Test
    fun auditedJsonApiScriptIsAccepted() {
        val script = """
            fetch('/api/schedule').then(function (response) {
                return response.json();
            }).then(function (payload) {
                window.shiguangBridge.showToast(JSON.stringify(payload));
            });
        """.trimIndent()

        assertTrue(ShiguangApiAdapterCatalog.isApiOnlyScript("CJLU", "CJLU", script))
    }

    @Test
    fun domAndHtmlResponseReadersAreRejected() {
        assertFalse(
            ShiguangApiAdapterCatalog.isApiOnlyScript(
                "CJLU",
                "CJLU",
                "fetch('/schedule').then(r => r.json()); document.querySelector('table');"
            )
        )
        assertFalse(
            ShiguangApiAdapterCatalog.isApiOnlyScript(
                "CJLU",
                "CJLU",
                "fetch('/schedule').then(response => response.text());"
            )
        )
    }

    @Test
    fun unreviewedAdapterIsRejectedEvenWhenItUsesJson() {
        assertFalse(
            ShiguangApiAdapterCatalog.isApiOnlyScript(
                "UNKNOWN",
                "UNKNOWN_01",
                "fetch('/api/schedule').then(response => response.json());"
            )
        )
    }
}
