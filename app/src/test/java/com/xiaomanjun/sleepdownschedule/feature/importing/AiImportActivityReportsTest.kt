package com.xiaomanjun.sleepdownschedule.feature.importing

import org.junit.Assert.*
import org.junit.Test
import com.xiaomanjun.sleepdownschedule.feature.importing.progress.groupAiImportReports

class AiImportActivityReportsTest {
    @Test fun displayGroupsPreferPublicNarrationWithoutLosingLaterRepeatedOperations() {
        val read = AiImportReport("读取课程", AiImportReportKind.EXECUTION)
        val narratedRead = read.copy(kind = AiImportReportKind.MODEL)
        val patch = AiImportReport("更新周次", AiImportReportKind.EXECUTION)
        val reports = listOf(read, narratedRead, read, patch, patch,
            AiImportReport("继续核对", AiImportReportKind.MODEL), read)
        val groups = groupAiImportReports(reports)
        assertEquals(listOf(listOf(narratedRead), listOf(patch),
            listOf(AiImportReport("继续核对", AiImportReportKind.MODEL)), listOf(read)), groups.map { it.second })
        assertEquals(listOf(1, 3, 5, 6), groups.map { it.first })
        assertEquals(7, reports.size)
        assertEquals(listOf(read, patch), groupAiImportReports(listOf(read, patch)).single().second)
    }

    @Test fun pendingTextRemovesOnlyCommittedParagraphsAndNeverExposesProtocol() {
        val reports = listOf(AiImportReport("**已核对课程**", AiImportReportKind.MODEL),
            AiImportReport("读取导入 JSON", AiImportReportKind.EXECUTION))
        assertEquals("**继续核对周次", remainingAiImportPublicText("**已核对课程**\n\n**继续核对周次", reports))
        assertEquals("", remainingAiImportPublicText("**已核对课程**", reports))
        assertEquals("**已核对课程**中有缺项", remainingAiImportPublicText("**已核对课程**中有缺项", reports))
        assertEquals("", remainingAiImportPublicText("{\"courses\":[]}", reports))
    }

    @Test fun totalImportTimeStartsAtZeroAndContinuesAcrossMinutes() {
        assertEquals("正在导入中 · 0 秒", aiImportElapsedLabel(1, 100))
        assertEquals("正在导入中 · 15 秒", aiImportElapsedLabel(15_000_000_000, 0))
        assertEquals("正在导入中 · 1 分 5 秒", aiImportElapsedLabel(65_000_000_000, 0))
    }

    @Test fun realStreamAndToolClocksCannotBeRewoundByDelayedOrUnchangedTickerText() {
        val session = AiEduImportProgressSession
        try {
            session.update(AiEduImportProgress(taskId = "timing"))
            val started = session.liveReasoning.value.startedAtNanos
            session.beginReasoning("timing")("模型首次响应")
            assertEquals(started, session.liveReasoning.value.startedAtNanos)
            session.updateWaitPhase("timing", AiImportWaitPhase.SERVICE_RESPONSE, 10)
            session.updateStream("timing", false, "", 20)
            session.updateStream("timing", false, "", 30)
            assertEquals(30, session.liveReasoning.value.activityAtNanos)
            session.updateWaitPhase("timing", AiImportWaitPhase.LOCAL_OPERATION, 40)
            session.updateActivity("timing", AiImportActivity("模型之前的报告", AiImportActivitySource.MODEL_PROGRESS))
            assertEquals(AiImportWaitPhase.LOCAL_OPERATION, session.liveReasoning.value.waitPhase)
            assertEquals(40, session.liveReasoning.value.activityAtNanos)
            assertEquals(started, session.liveReasoning.value.startedAtNanos)
            session.updateWaitPhase("stale-task", AiImportWaitPhase.MODEL_OUTPUT, 50)
            assertEquals(40, session.liveReasoning.value.activityAtNanos)
            session.beginReasoning("timing")("下一次工具往返")
            assertEquals(started, session.liveReasoning.value.startedAtNanos)
            session.update(AiEduImportProgress(taskId = "next-timing"))
            assertTrue(session.liveReasoning.value.startedAtNanos > started)
        } finally {
            session.update(null)
        }
    }

    @Test fun modelReportsAndRealOperationsRemainDistinctOrderedAndBounded() {
        var reports = appendAiImportReport(emptyList(), AiImportReport("先核对周次", AiImportReportKind.MODEL))
        reports = appendAiImportReport(reports, AiImportReport("读取导入 JSON", AiImportReportKind.EXECUTION))
        assertEquals(listOf(AiImportReportKind.MODEL, AiImportReportKind.EXECUTION), reports.map { it.kind })
        assertEquals(reports, appendAiImportReport(reports, reports.last()))
        repeat(MaxAiImportReports + 5) { reports = appendAiImportReport(reports, AiImportReport("已处理阶段 $it", AiImportReportKind.EXECUTION)) }
        assertEquals(MaxAiImportReports, reports.size)
        assertEquals("已处理阶段 ${MaxAiImportReports + 4}", reports.last().text)
    }

    @Test fun reportsCannotGrowOrAddBlankPlaceholders() {
        assertTrue(appendAiImportReport(emptyList(), AiImportReport(" \n ", AiImportReportKind.MODEL)).isEmpty())
        val report = appendAiImportReport(emptyList(), AiImportReport("一\n".repeat(1_000), AiImportReportKind.MODEL)).single()
        assertTrue(report.text.length <= MaxAiImportModelReportChars)
        assertTrue(report.text.contains('\n'))
        val markdown = "**正在核对**\n- 保留周次\n- 保留地点"
        assertEquals(markdown, appendAiImportReport(emptyList(), AiImportReport(markdown, AiImportReportKind.MODEL)).single().text)
    }
}
