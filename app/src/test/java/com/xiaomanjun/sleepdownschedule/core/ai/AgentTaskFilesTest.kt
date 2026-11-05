package com.xiaomanjun.sleepdownschedule.core.ai

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AgentTaskFilesTest {
    @get:Rule val temporary = TemporaryFolder()
    @Test fun editsUseExactContentAndVersionWithoutGrantingParentAccess() {
        AgentTaskFiles(temporary.newFolder("task")).use { files ->
            val initial = files.write("import.js", "function main() { return 1; }", "")
            val edited = files.edit("import.js", "return 1", "return 2", initial.hash)
            assertTrue(edited.text.contains("return 2"))
            try { files.write("import.js", "replace stale", initial.hash); fail("Expected version conflict") }
            catch (_: IllegalArgumentException) { }
            try { files.write("../escape.js", "bad", ""); fail("Expected boundary rejection") }
            catch (_: IllegalArgumentException) { }
            assertEquals(edited, files.read("import.js"))
            assertEquals(listOf("import.js"), files.list())
        }
    }
}
