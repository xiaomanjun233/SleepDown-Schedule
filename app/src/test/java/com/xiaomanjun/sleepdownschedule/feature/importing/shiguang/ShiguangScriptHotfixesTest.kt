package com.xiaomanjun.sleepdownschedule.feature.importing.shiguang

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ShiguangScriptHotfixesTest {
    private val broken = "fixture broken parser\n"
    private val fixes = listOf(ShiguangScriptHotfix("SWU/swu.js", shiguangScriptSha256(broken)))

    @Test fun onlyTheKnownBrokenSchoolRevisionUsesTheBundledFix() {
        assertEquals("fixed", resolveShiguangScriptHotfix("SWU/swu.js", broken, fixes) { "fixed" })
        assertEquals("fixed", resolveShiguangScriptHotfix("SWU/swu.js", broken.replace("\n", "\r\n"), fixes) { "fixed" })
        assertEquals("upstream newer", resolveShiguangScriptHotfix("SWU/swu.js", "upstream newer", fixes) {
            error("New upstream code must remain selected")
        })
        assertEquals(broken, resolveShiguangScriptHotfix("OTHER/school.js", broken, fixes) {
            error("Other schools must remain selected")
        })
    }

    @Test fun missingOrUnchangedRepairCannotSilentlyPretendToFixTheImport() {
        assertThrows(IllegalArgumentException::class.java) {
            resolveShiguangScriptHotfix("SWU/swu.js", broken, fixes) { broken }
        }
        assertThrows(IllegalStateException::class.java) {
            resolveShiguangScriptHotfix("SWU/swu.js", broken, fixes) { error("Missing bundled script") }
        }
    }
}
