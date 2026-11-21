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
        assertNull(AutoRefreshAnswers.resolve(saved, first.copy(options = listOf("2026 春", "2026 春"))))
    }

    @Test fun promptsReuseConfirmedValuesAndRequireConfirmationForNewQuestions() {
        val answers = mutableMapOf<String, String>()
        val request = EduBridgeInteractionRequest.Prompt("one", "学年", "请输入学年", "2026", null)
        AutoRefreshAnswers.record(answers, request, "\"2025\"")
        assertEquals("\"2025\"", AutoRefreshAnswers.resolve(answers, request.copy(requestId = "two", defaultValue = "2027")))
        assertNull(AutoRefreshAnswers.resolve(answers, request.copy(title = "校区")))
    }

    @Test fun reconnectDoesNotReplaceSavedTermWithTheNewPageDefault() {
        val answers = mutableMapOf<String, String>()
        val request = EduBridgeInteractionRequest.SingleSelection(
            "one", "选择学期", listOf("2026 春", "2026 秋"), 1
        )
        AutoRefreshAnswers.record(answers, request, "0")
        assertEquals("0", AutoRefreshAnswers.resolve(answers, request))
        assertNull(AutoRefreshAnswers.resolve(answers, request.copy(options = listOf("2026 秋"), defaultIndex = 0)))
    }

    @Test fun backgroundProtocolAcknowledgesIntroductoryAlertsWithoutOpeningImportUi() {
        val request = EduBridgeInteractionRequest.Alert("one", "西南大学课表导入", "请先完成学校登录", "好的，开始导入")
        assertEquals("true", AutoRefreshAnswers.resolve(emptyMap(), request))
    }

    @Test fun informationalWarningsAreNotMistakenForFailedSessions() {
        val request = EduBridgeInteractionRequest.Alert(
            "one", "作息时间提示", "请在课表页面核对课程时间，如有错误请手动修改课程所在位置或节次信息。", "我知道了"
        )
        assertEquals("true", AutoRefreshAnswers.resolve(emptyMap(), request))
        assertEquals("true", AutoRefreshAnswers.resolve(emptyMap(), request.copy(
            title = "集中实践课需手动添加", message = "教务系统未给出星期和节次，无法自动导入"
        )))
        assertNull(AutoRefreshAnswers.resolve(emptyMap(), request.copy(title = "拉取失败")))
        assertNull(AutoRefreshAnswers.resolve(emptyMap(), request.copy(title = "未找到课程")))
    }
}
