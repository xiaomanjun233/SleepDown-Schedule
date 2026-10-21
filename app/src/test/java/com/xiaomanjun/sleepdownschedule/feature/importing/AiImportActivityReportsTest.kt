package com.xiaomanjun.sleepdownschedule.feature.importing

import org.junit.Assert.*
import org.junit.Test

class AiImportActivityReportsTest {
    @Test fun slowFirstTokenAndLongOperationsShowElapsedTimeWithoutInventingProgress() {
        assertNull(aiImportWaitLabel(2_000_000_000, 0, AiImportWaitPhase.LOCAL_OPERATION))
        assertEquals("当前步骤已持续 15 秒", aiImportWaitLabel(15_000_000_000, 0, AiImportWaitPhase.LOCAL_OPERATION))
        assertEquals("等待 AI 服务响应 · 15 秒", aiImportWaitLabel(15_000_000_000, 0, AiImportWaitPhase.SERVICE_RESPONSE))
        assertEquals("等待新的模型输出 · 1 分 5 秒", aiImportWaitLabel(65_000_000_000, 0, AiImportWaitPhase.MODEL_OUTPUT))
        assertNull(aiImportWaitLabel(1, 100, AiImportWaitPhase.MODEL_OUTPUT))
    }

    @Test fun realStreamAndToolClocksCannotBeRewoundByDelayedOrUnchangedTickerText() {
        val session = AiEduImportProgressSession
        try {
            session.update(AiEduImportProgress(taskId = "timing"))
            session.updateWaitPhase("timing", AiImportWaitPhase.SERVICE_RESPONSE, 10)
            session.updateStream("timing", false, "", 20)
            session.updateStream("timing", false, "", 30)
            assertEquals(30, session.liveReasoning.value.activityAtNanos)
            session.updateWaitPhase("timing", AiImportWaitPhase.LOCAL_OPERATION, 40)
            session.updateActivity("timing", AiImportActivity("模型之前的报告", AiImportActivitySource.MODEL_PROGRESS))
            assertEquals(AiImportWaitPhase.LOCAL_OPERATION, session.liveReasoning.value.waitPhase)
            assertEquals(40, session.liveReasoning.value.activityAtNanos)
            session.updateWaitPhase("stale-task", AiImportWaitPhase.MODEL_OUTPUT, 50)
            assertEquals(40, session.liveReasoning.value.activityAtNanos)
        } finally {
            session.update(null)
        }
    }

    @Test fun modelReportsAndRealOperationsRemainDistinctOrderedAndBounded() {
        var reports = appendAiImportReport(emptyList(), AiImportReport("先核对周次", AiImportReportKind.MODEL))
        reports = appendAiImportReport(reports, AiImportReport("读取导入 JSON", AiImportReportKind.EXECUTION))
        assertEquals(listOf(AiImportReportKind.MODEL, AiImportReportKind.EXECUTION), reports.map { it.kind })
        assertEquals(reports, appendAiImportReport(reports, reports.last()))
        repeat(30) { reports = appendAiImportReport(reports, AiImportReport("已处理阶段 $it", AiImportReportKind.EXECUTION)) }
        assertEquals(12, reports.size)
        assertEquals("已处理阶段 29", reports.last().text)
    }

    @Test fun reportsCannotGrowOrAddBlankPlaceholders() {
        assertTrue(appendAiImportReport(emptyList(), AiImportReport(" \n ", AiImportReportKind.MODEL)).isEmpty())
        val report = appendAiImportReport(emptyList(), AiImportReport("一\n".repeat(1_000), AiImportReportKind.MODEL)).single()
        assertTrue(report.text.length <= 180)
        assertFalse(report.text.contains('\n'))
    }
}
