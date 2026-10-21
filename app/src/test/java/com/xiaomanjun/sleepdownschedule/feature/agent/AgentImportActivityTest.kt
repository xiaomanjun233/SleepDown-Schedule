package com.xiaomanjun.sleepdownschedule.feature.agent

import com.xiaomanjun.sleepdownschedule.feature.importing.*
import java.util.concurrent.CancellationException
import org.junit.Assert.*
import org.junit.Test

class AgentImportActivityTest {
    @Test fun rapidSemanticEventsAreRetainedWhileTheirIndependentTickerCoalesces() {
        var now = 0L
        val steps = mutableListOf<String>()
        val updates = mutableListOf<AiImportActivity>()
        val scheduled = mutableListOf<() -> Unit>()
        val publisher = AiImportActivityPublisher(nanoTime = { now },
            schedule = { _, action -> scheduled += action }, onUpdate = updates::add)
        val interaction = AiImportInteraction("")
        interaction.onExecutionStep = { step ->
            steps += step
            publisher.publish(AiImportActivity(step, AiImportActivitySource.STATUS))
        }
        interaction.reportExecutionStep("读取导入 JSON 草稿")
        now = 1_000_000L
        interaction.reportExecutionStep("编辑并校验导入草稿")
        assertEquals(listOf("读取导入 JSON 草稿", "编辑并校验导入草稿"), steps)
        assertEquals(listOf("读取导入 JSON 草稿"), updates.map { it.text })
        assertEquals(1, scheduled.size)
        now = 100_000_000L
        scheduled.single().invoke()
        assertEquals(steps, updates.map { it.text })
        interaction.markCancelled()
    }

    @Test fun actualProviderNarrativeReachesTickerAndToolBurstCannotSwallowIt() {
        val interaction = AiImportInteraction("")
        val steps = mutableListOf<String>()
        val updates = mutableListOf<AiImportActivity>()
        interaction.onExecutionStep = steps::add
        interaction.onActivity = updates::add
        reportAgentImportStatus(interaction, AgentRunStatus(AgentRunStatusIcon.THINKING,
            "准备下一步", "已读取 JSON，接下来核对本周课程地点。"))
        reportAgentImportStatus(interaction, AgentRunStatus(AgentRunStatusIcon.SCHEDULE, "读取导入 JSON 草稿"))
        reportAgentImportStatus(interaction, AgentRunStatus(AgentRunStatusIcon.SCHEDULE, "编辑并校验导入草稿"))
        reportAgentImportStatus(interaction, AgentRunStatus(AgentRunStatusIcon.THINKING, "正在思考"))
        assertEquals(listOf("读取导入 JSON 草稿", "编辑并校验导入草稿"), steps)
        assertEquals(listOf(AiImportActivity("已读取 JSON，接下来核对本周课程地点。", AiImportActivitySource.MODEL_PROGRESS)), updates)
        interaction.markCancelled()
    }

    @Test fun localStatusMetadataDoesNotBecomeProviderNarration() {
        val interaction = AiImportInteraction("")
        val steps = mutableListOf<String>()
        val reports = mutableListOf<String>()
        interaction.onExecutionStep = steps::add
        interaction.onProgressReport = reports::add
        reportAgentImportStatus(interaction, AgentRunStatus(AgentRunStatusIcon.THINKING, "正在切换备用配置", "model-name"))
        assertEquals(listOf("正在切换备用配置"), steps)
        assertTrue(reports.isEmpty())
        interaction.markCancelled()
    }

    @Test fun codeToolJsonPrivateThinkingAndOpaquePayloadsAreNotPublicNarrative() {
        for (raw in listOf(
            "<think>隐藏内容</think><import_progress>不能显示</import_progress>",
            "{\"operationsJson\":\"秘密\"}", "```kotlin\nval secret = 1\n```",
            "val secret = 1", "x".repeat(100), "<agent_actions>[]</agent_actions>",
            "encrypted_content=秘密", "<analysis>隐藏内容</analysis>", "<import_progress>未完成")) {
            assertTrue(raw, importAgentProgressDetails(raw).isEmpty())
        }
        assertEquals(listOf("已核对两门课程。"), importAgentProgressDetails("<import_progress>已核对两门课程。</import_progress>"))
        assertEquals(listOf("已读取草稿。 正在核对时间。"), importAgentProgressDetails("已读取草稿。\n正在核对时间。"))
    }

    @Test fun completedAgentDecisionReportsEveryPublicBlockInOrderBeforeToolsRun() {
        val interaction = AiImportInteraction("")
        val reports = mutableListOf<String>()
        val steps = mutableListOf<String>()
        interaction.onProgressReport = reports::add
        interaction.onExecutionStep = steps::add
        reportAgentImportStatus(interaction, AgentRunStatus(AgentRunStatusIcon.THINKING, "准备下一步", """
            <import_progress>已定位待修改课程。</import_progress>
            <import_progress>已核对原始上课时间。</import_progress>
        """.trimIndent()))
        reportAgentImportStatus(interaction, AgentRunStatus(AgentRunStatusIcon.SCHEDULE, "编辑并校验导入草稿"))
        interaction.publishActivity("继续检查", AiImportActivitySource.PROVIDER_SUMMARY)
        assertEquals(listOf("已定位待修改课程。", "已核对原始上课时间。"), reports)
        assertEquals(listOf("编辑并校验导入草稿"), steps)
        interaction.markCancelled()
    }

    @Test fun repeatedOrUnsafeBlocksDoNotHideLaterCompletedPublicProse() {
        val interaction = AiImportInteraction("")
        val reports = mutableListOf<String>()
        interaction.onProgressReport = reports::add
        reportAgentImportStatus(interaction, AgentRunStatus(AgentRunStatusIcon.THINKING, "准备下一步", """
            <import_progress>已核对课程。</import_progress>
            <import_progress>已核对课程。</import_progress>
            <import_progress>{"secret":1}</import_progress>
            <import_progress>已完成时间校验。</import_progress>
            <import_progress>尚未完整
        """.trimIndent()))
        assertEquals(listOf("已核对课程。", "已完成时间校验。"), reports)
        interaction.markCancelled()
    }

    @Test fun streamedReportsAreNotReplayedByTheCompletedAgentDecision() {
        val interaction = AiImportInteraction("")
        val reports = mutableListOf<String>()
        interaction.onProgressReport = reports::add
        interaction.publishActivity("已定位课程。", AiImportActivitySource.MODEL_PROGRESS)
        interaction.publishActivity("已校验时间。", AiImportActivitySource.MODEL_PROGRESS)
        reportAgentImportStatus(interaction, AgentRunStatus(AgentRunStatusIcon.THINKING, "准备下一步", """
            <import_progress>已定位课程。</import_progress>
            <import_progress>已校验时间。</import_progress>
        """.trimIndent()))
        assertEquals(listOf("已定位课程。", "已校验时间。"), reports)
        interaction.markCancelled()
    }

    @Test fun executionChannelIsBoundedAndCancellationRejectsLateEvents() {
        val interaction = AiImportInteraction("")
        val steps = mutableListOf<String>()
        interaction.onExecutionStep = steps::add
        interaction.reportExecutionStep("读取\n草稿".repeat(100))
        assertEquals(1, steps.size)
        assertTrue(steps.single().length <= AiImportActivityMaxChars)
        assertFalse(steps.single().contains('\n'))
        interaction.markCancelled()
        assertThrows(CancellationException::class.java) { interaction.reportExecutionStep("迟到事件") }
        assertEquals(1, steps.size)
    }

    @Test fun completedReportsSurviveFollowingReasoningAndDeduplicateAcrossBothEntryPoints() {
        val interaction = AiImportInteraction("")
        val reports = mutableListOf<String>()
        interaction.onProgressReport = reports::add
        interaction.publishActivity("已完成课程定位", AiImportActivitySource.MODEL_PROGRESS)
        interaction.publishActivity("继续核对", AiImportActivitySource.PROVIDER_SUMMARY)
        interaction.publishActivity(AiImportActivity("已完成课程定位", AiImportActivitySource.MODEL_PROGRESS))
        interaction.publishActivity("已核对时间", AiImportActivitySource.MODEL_PROGRESS)
        interaction.publishActivity("继续整理", AiImportActivitySource.PROVIDER_SUMMARY)
        assertEquals(listOf("已完成课程定位", "已核对时间"), reports)
        interaction.publishActivity("{\"secret\":1}", AiImportActivitySource.MODEL_PROGRESS)
        assertEquals(2, reports.size)
        interaction.markCancelled()
        assertThrows(CancellationException::class.java) {
            interaction.publishActivity("迟到的报告", AiImportActivitySource.MODEL_PROGRESS)
        }
        assertEquals(2, reports.size)
    }

    @Test fun nativeReasoningKeepsItsChannelWithoutExposingCodeOrOpaquePayloads() {
        val interaction = AiImportInteraction("")
        val updates = mutableListOf<AiImportActivity>()
        val reports = mutableListOf<String>()
        interaction.onActivity = updates::add
        interaction.onProgressReport = reports::add
        interaction.publishActivity("{\"encrypted_content\":\"secret\"}", AiImportActivitySource.PROVIDER_SUMMARY)
        interaction.publishActivity("x".repeat(100), AiImportActivitySource.PROVIDER_SUMMARY)
        interaction.publishActivity("正在核对课程时间", AiImportActivitySource.PROVIDER_SUMMARY)
        assertEquals(listOf(AiImportActivity("正在核对课程时间", AiImportActivitySource.PROVIDER_SUMMARY)), updates)
        assertTrue(reports.isEmpty())
        interaction.markCancelled()
    }
}
