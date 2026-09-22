package com.xiaomanjun.sleepdownschedule.feature.schedule.autorefresh

import com.xiaomanjun.sleepdownschedule.feature.importing.EduBridgeInteractionRequest
import org.junit.Assert.*
import org.junit.Test

class AutoRefreshPreferencesTest {
    @Test fun oldShortIntervalsMigrateWithoutEnablingDisabledRefresh() {
        listOf(0L, 15L, 30L, 60L, 360L, 720L, 1440L).forEach {
            assertEquals(1440L, AutoRefreshFrequency.normalize(it))
            assertEquals(0, AutoRefreshFrequency.selectedIndex(false, it))
            assertEquals(1, AutoRefreshFrequency.selectedIndex(true, it))
        }
        assertEquals(10080L, AutoRefreshFrequency.normalize(10080))
        assertEquals(2, AutoRefreshFrequency.selectedIndex(true, 10080))
    }

    @Test fun choicesSurviveReorderingAndNeverSilentlySwitchSemester() {
        val saved = mutableMapOf<String, String>()
        val first = EduBridgeInteractionRequest.SingleSelection("one", "选择学期", listOf("2025 秋", "2026 春"), 0)
        AutoRefreshAnswers.record(saved, first, "1")
        val reordered = first.copy(requestId = "two", options = listOf("2026 春", "2025 秋"))
        assertEquals("0", AutoRefreshAnswers.resolve(saved, reordered))
        assertNull(AutoRefreshAnswers.resolve(saved, first.copy(options = listOf("2026 秋"))))
        assertNull(AutoRefreshAnswers.resolve(emptyMap(), first))
    }

    @Test fun promptsReuseConfirmedValuesAndRequireConfirmationForNewQuestions() {
        val answers = mutableMapOf<String, String>()
        val request = EduBridgeInteractionRequest.Prompt("one", "学年", "请输入学年", "2026", null)
        AutoRefreshAnswers.record(answers, request, "\"2025\"")
        assertEquals("\"2025\"", AutoRefreshAnswers.resolve(answers, request.copy(requestId = "two", defaultValue = "2027")))
        assertNull(AutoRefreshAnswers.resolve(answers, request.copy(title = "校区")))
    }
}
