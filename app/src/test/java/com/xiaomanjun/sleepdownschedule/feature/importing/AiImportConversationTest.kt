package com.xiaomanjun.sleepdownschedule.feature.importing

import org.junit.Assert.*
import org.junit.Test

class AiImportConversationTest {
    @Test fun continuingFromAnOlderPreviewDoesNotReplaceTheLatestReplyArtifact() {
        val draft = com.xiaomanjun.sleepdownschedule.model.ImportDraft(
            com.xiaomanjun.sleepdownschedule.defaultConfig(), com.xiaomanjun.sleepdownschedule.defaultPeriods(),
            listOf(com.xiaomanjun.sleepdownschedule.model.CourseEntity(name = "原始课程", teacher = null, location = null,
                weekday = 1, periods = listOf(1), weeks = listOf(1),
                weekParity = com.xiaomanjun.sleepdownschedule.model.WeekParity.ALL, note = null)))
        val first = ensureImportCheckpoint(AiEduImportProgress(taskId = "test"), draft)
        val revised = draft.copy(courses = listOf(draft.courses.single().copy(name = "本轮新课程")))
        val latest = appendImportCheckpoint(first, revised, "修改完成", AiImportCheckpointKind.AI).getOrThrow().first
        val selectedOld = selectImportCheckpoint(latest, first.selectedCheckpointId!!, draft.config).getOrThrow().first
        val archived = archiveImportTurn(selectedOld, draft).last()
        assertEquals(latest.checkpoints.last().payload, archived.artifactPayload)
    }

    @Test fun intermediateDialogueSurvivesANewTurnAndHistoryRoundTrip() {
        val reports = List(30) { index -> AiImportReport("第 $index 条中间消息",
            if (index % 2 == 0) AiImportReportKind.MODEL else AiImportReportKind.EXECUTION) }
        val first = AiEduImportProgress(userPrompt = "导入课表", activityReports = reports, finished = true)
        val next = AiEduImportProgress(userPrompt = "只修改周次", conversationTurns = archiveImportTurn(first),
            activityReports = listOf(AiImportReport("正在核对周次", AiImportReportKind.MODEL)))
        val restored = progressFromJson(progressToJson(next))
        assertEquals(reports, restored.conversationTurns.single().activityReports)
        assertEquals(next.activityReports, restored.activityReports)
        val legacy = progressToJson(next)
        legacy.getJSONArray("conversationTurns").getJSONObject(0).remove("activityReports")
        assertTrue(progressFromJson(legacy).conversationTurns.single().activityReports.isEmpty())
    }

    @Test fun storedDraftRetainsExplicitCourseClock() {
        val draft = com.xiaomanjun.sleepdownschedule.model.ImportDraft(
            com.xiaomanjun.sleepdownschedule.defaultConfig().copy(
                periodAlignmentMode = com.xiaomanjun.sleepdownschedule.model.PeriodAlignmentMode.TIME),
            listOf(com.xiaomanjun.sleepdownschedule.model.PeriodEntity(1, "08:00", "08:45")),
            listOf(com.xiaomanjun.sleepdownschedule.model.CourseEntity(name = "课程", teacher = null, location = null,
                weekday = 1, periods = listOf(1), weeks = listOf(1),
                weekParity = com.xiaomanjun.sleepdownschedule.model.WeekParity.ALL, note = null,
                customStartTime = "08:10", customEndTime = "08:55")))
        val restored = ScheduleImportParser.parseStoredDraft(draftToPayload(draft).toString(),
            com.xiaomanjun.sleepdownschedule.defaultConfig()).getOrThrow()
        assertEquals("08:10", restored.courses.single().customStartTime)
        assertEquals("08:55", restored.courses.single().customEndTime)
    }

    @Test fun cancelledConfirmationCannotSendAndCanBePreparedAgain() {
        for (title in listOf("课表.pdf", "课表口令文本")) {
            var sent = 0
            var cancelled = 0
            AiEduImportProgressSession.update(AiEduImportProgress(attachmentTitle = title, awaitingConfirmation = true))
            AiEduImportProgressSession.setActions(onConfirm = { sent++ }, onCancel = { cancelled++ })
            AiEduImportProgressSession.cancel()
            AiEduImportProgressSession.confirm()
            AiEduImportProgressSession.cancel()
            val cancelledState = checkNotNull(AiEduImportProgressSession.progress.value)
            assertFalse(cancelledState.awaitingConfirmation)
            assertTrue(cancelledState.returnToBrowser)
            assertEquals(0, sent)
            assertEquals(1, cancelled)
            AiEduImportProgressSession.update(AiEduImportProgress(attachmentTitle = title, awaitingConfirmation = true))
            AiEduImportProgressSession.setActions(onConfirm = { sent++ })
            AiEduImportProgressSession.confirm()
            assertEquals(1, sent)
        }
        AiEduImportProgressSession.update(null)
    }

    @Test fun historyRetainsStoppedTurnAndArtifactWithoutDuplicatingTheCurrentReply() {
        val previous = AiEduImportConversationTurn("初始导入", artifactPayload = "{\"courses\":[]}")
        val current = AiEduImportProgress(userPrompt = "仅改本周", requestInstructions = "完整上下文", finished = true,
            awaitingUserInput = true, conversationTurns = listOf(previous), clarificationQuestions = listOf("哪门课？"))
        val restored = progressFromJson(progressToJson(current))
        assertEquals(current, restored)
        assertEquals(2, archiveImportTurn(restored).size)
        assertEquals("已停止，等待补充", archiveImportTurn(restored).last().status)
        val legacy = progressToJson(current).put("schemaVersion", 1).put("conversationTurns", org.json.JSONArray()
            .put(org.json.JSONObject().put("userPrompt", current.userPrompt).put("aiOutput", current.aiOutput)))
        assertTrue(progressFromJson(legacy).conversationTurns.isEmpty())
    }

    @Test fun onlyCompleteCoursesBecomeProvisionalCards() {
        val prefix = """{"courses":[{"name":"数学 } 课","weekday":1,"periods":[1],"weeks":[1],"weekParity":"ODD"},"""
        val courses = completeStreamingCourses(prefix + """{"name":"还未输出完"""")
        assertEquals(1, courses.size)
        assertEquals("数学 } 课", courses.single().name)
        assertEquals(com.xiaomanjun.sleepdownschedule.model.WeekParity.ODD, courses.single().weekParity)
        assertTrue(completeStreamingCourses("""{"courses":[{"name":"未完成"""").isEmpty())
    }
}
