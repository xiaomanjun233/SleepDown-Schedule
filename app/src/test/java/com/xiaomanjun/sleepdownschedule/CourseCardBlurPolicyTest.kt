package com.xiaomanjun.sleepdownschedule

import org.junit.Assert.assertEquals
import org.junit.Test

class CourseCardBlurPolicyTest {
    @Test
    fun liquidAndSimpleCardsKeepIndependentBlurLimits() {
        assertEquals(10f, courseCardBlurMaximum(glassEnabled = true), 0f)
        assertEquals(24f, courseCardBlurMaximum(glassEnabled = false), 0f)
    }

    @Test
    fun agentPercentUsesTheActiveCardModeRange() {
        val simple = defaultConfig().copy(courseCardGlassEnabled = false)
        val updated = AgentSettingRegistry.apply(simple, "COURSE_CARD_BLUR_PERCENT", "100")
        assertEquals(24f, updated?.courseCardBlur ?: -1f, 0f)
    }
}
