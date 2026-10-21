package com.xiaomanjun.sleepdownschedule.feature.importing

import org.junit.Assert.*
import org.junit.Test

class AiImportConversationTest {
    @Test fun storedDraftRetainsTimeAlignment() {
        val draft = com.xiaomanjun.sleepdownschedule.model.ImportDraft(
            com.xiaomanjun.sleepdownschedule.defaultConfig().copy(
                periodAlignmentMode = com.xiaomanjun.sleepdownschedule.model.PeriodAlignmentMode.TIME),
            listOf(com.xiaomanjun.sleepdownschedule.model.PeriodEntity(1, "08:00", "08:45")), emptyList())
        val restored = ScheduleImportParser.parseStoredDraft(draftToPayload(draft).toString(),
            com.xiaomanjun.sleepdownschedule.defaultConfig()).getOrThrow()
        assertEquals(draft.config.periodAlignmentMode, restored.config.periodAlignmentMode)
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
