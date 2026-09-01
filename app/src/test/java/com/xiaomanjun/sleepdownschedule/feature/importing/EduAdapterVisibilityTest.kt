package com.xiaomanjun.sleepdownschedule.feature.importing

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EduAdapterVisibilityTest {
    @Test
    fun `manual share code and development tools are hidden from general tools`() {
        assertTrue(adapter("WakeUp").isManualShareCodeTool())
        assertTrue(adapter("StarLink").isManualShareCodeTool())
        assertFalse(adapter("GENERAL_TOOL_01").isManualShareCodeTool())
        assertFalse(adapter("WakeUp", schoolId = "OTHER_SCHOOL").isManualShareCodeTool())

        assertTrue(adapter("GENERAL_TOOL_01").isDevelopmentOnlyGeneralTool())
        assertTrue(adapter("GENERAL_TOOL_02").isDevelopmentOnlyGeneralTool())
        assertFalse(adapter("GENERAL_TOOL_03").isDevelopmentOnlyGeneralTool())
        assertFalse(adapter("GENERAL_TOOL_01", schoolId = "OTHER_SCHOOL").isDevelopmentOnlyGeneralTool())
    }

    private fun adapter(
        adapterId: String,
        schoolId: String = "GLOBAL_TOOLS"
    ) = EduAdapter(
        school = EduSchool(
            id = schoolId,
            name = "通用工具",
            folder = "GLOBAL_TOOLS"
        ),
        adapterId = adapterId,
        adapterName = adapterId,
        category = "GENERAL_TOOL",
        assetJsPath = "tool.js",
        importUrl = "",
        maintainer = "test",
        description = "test"
    )
}
