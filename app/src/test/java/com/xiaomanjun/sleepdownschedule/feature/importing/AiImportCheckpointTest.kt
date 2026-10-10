package com.xiaomanjun.sleepdownschedule.feature.importing

import com.xiaomanjun.sleepdownschedule.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class AiImportCheckpointTest {
    private val session = AiEduImportProgressSession

    @After fun resetSession() { session.update(null) }

    private fun draft(name: String = "数学", note: String? = null) = ImportDraft(
        config = defaultConfig(), periods = listOf(PeriodEntity(1, "08:00", "08:45")),
        courses = listOf(CourseEntity(name = name, teacher = null, location = null, weekday = 1,
            periods = listOf(1), weeks = listOf(1, 2), weekParity = WeekParity.ALL, note = note)),
        source = ImportDraftSource.AI_EDU)

    private fun start(taskId: String = "task") { session.beginTask(AiEduImportProgress(taskId = taskId)) }

    @Test fun validatedMilestoneIsSelectableBeforeTaskFinishes() {
        start()
        val first = session.publishCheckpoint("task", draft(), "第一阶段").getOrThrow()
        assertFalse(session.progress.value!!.finished)
        assertEquals(first.id, session.progress.value!!.selectedCheckpointId)
        assertEquals("数学", session.previewDraft.value!!.courses.single().name)
        assertTrue(session.selectCheckpoint("task", first.id).isSuccess)
        assertTrue(session.progress.value!!.checkpointSelectionLocked)
    }

    @Test fun explicitSelectionSurvivesLateStreamAndFinalResult() {
        start()
        val first = session.publishCheckpoint("task", draft("早期结果")).getOrThrow()
        session.publishCheckpoint("task", draft("稍后结果")).getOrThrow()
        session.selectCheckpoint("task", first.id).getOrThrow()
        session.updateStream("task", true, "{}")
        session.updateActiveTask("task", draft("最终结果")) { it.copy(finished = true) }
        assertEquals(first.id, session.progress.value!!.selectedCheckpointId)
        assertEquals("早期结果", session.previewDraft.value!!.courses.single().name)
        assertEquals(3, session.progress.value!!.checkpoints.size)
        assertTrue(session.progress.value!!.finished)
    }

    @Test fun manualJsonEditIsImmutableAndCannotBeClobberedByACapturedProgress() {
        start()
        val first = session.publishCheckpoint("task", draft()).getOrThrow()
        val captured = session.progress.value!!
        val edited = session.editCheckpointJson("task", first.id, draftToPayload(draft("用户修改")).toString()).getOrThrow()
        assertNotEquals(first.id, edited.id)
        assertEquals("数学", first.restore().getOrThrow().courses.single().name)
        session.updateActiveTask("task", draft("晚到结果")) { captured.copy(finished = true) }
        assertEquals(edited.id, session.progress.value!!.selectedCheckpointId)
        assertEquals("用户修改", session.previewDraft.value!!.courses.single().name)
        assertEquals(3, session.progress.value!!.checkpoints.size)
    }

    @Test fun editedNoOpStillCreatesAnIndependentManualVersion() {
        start()
        val first = session.publishCheckpoint("task", draft()).getOrThrow()
        val edited = session.editCheckpointJson("task", first.id, first.payload).getOrThrow()
        assertNotEquals(first.id, edited.id)
        assertEquals(first.payload, edited.payload)
        assertEquals(AiImportCheckpointKind.MANUAL_EDIT, edited.kind)
        assertEquals(2, session.progress.value!!.checkpoints.size)
    }

    @Test fun invalidIncompleteOrNonJsonEditsLeaveSelectionAndPayloadIntact() {
        start()
        val first = session.publishCheckpoint("task", draft()).getOrThrow()
        val malformed = listOf("{", first.payload.dropLast(1), "说明：${first.payload}",
            first.payload.replace("\"weekday\":1", "\"weekday\":9"),
            first.payload.replace("\"weeks\":[1,2]", "\"weeks\":[]"),
            first.payload.replace("\"courses\":[", "\"courses\":[] ,\"ignored\":["),
            """{schemaVersion:1,scheduleConfig:{totalWeeks:20,periods:[]},courses:[]}""")
        malformed.forEach { json ->
            assertTrue("Unexpected acceptance: $json", session.editCheckpointJson("task", first.id, json).isFailure)
            assertEquals(first.id, session.progress.value!!.selectedCheckpointId)
            assertEquals(listOf(first), session.progress.value!!.checkpoints)
            assertEquals("数学", session.previewDraft.value!!.courses.single().name)
        }
    }

    @Test fun mutableCallerCollectionsCannotChangeAnAcceptedCheckpoint() {
        val weeks = mutableListOf(1, 2)
        val courses = mutableListOf(draft().courses.single().copy(weeks = weeks))
        val source = draft().copy(courses = courses)
        start()
        val checkpoint = session.publishCheckpoint("task", source).getOrThrow()
        weeks.clear()
        courses.clear()
        assertEquals(listOf(1, 2), checkpoint.restore().getOrThrow().courses.single().weeks)
        val firstRestore = checkpoint.restore().getOrThrow()
        (firstRestore.courses as MutableList).clear()
        assertEquals(1, checkpoint.restore().getOrThrow().courses.size)
    }

    @Test fun newTaskRejectsStaleEditsMilestonesAndFinals() {
        start("first")
        val first = session.publishCheckpoint("first", draft()).getOrThrow()
        start("second")
        assertTrue(session.progress.value!!.checkpoints.isEmpty())
        assertNull(session.previewDraft.value)
        assertTrue(session.publishCheckpoint("first", draft()).isFailure)
        assertTrue(session.selectCheckpoint("first", first.id).isFailure)
        assertTrue(session.editCheckpointJson("first", first.id, first.payload).isFailure)
        assertNull(session.updateActiveTask("first", draft()) { it.copy(finished = true) })
        assertEquals("second", session.progress.value!!.taskId)
    }

    @Test fun stoppedTaskKeepsUsableStagesButRejectsMoreAutomaticOutput() {
        start()
        val first = session.publishCheckpoint("task", draft()).getOrThrow()
        session.updateActiveTask("task") { it.copy(finished = true, awaitingUserInput = true) }
        assertTrue(session.publishCheckpoint("task", draft("迟到")).isFailure)
        assertTrue(session.selectCheckpoint("task", first.id).isSuccess)
        assertTrue(session.editCheckpointJson("task", first.id, draftToPayload(draft("停下后编辑")).toString()).isSuccess)
    }

    @Test fun revisionExplicitlyCarriesStagesFromHistoryWithoutUsingUnrelatedLiveTask() {
        start("history-task")
        val checkpoint = session.publishCheckpoint("history-task", draft("历史课表")).getOrThrow()
        session.selectCheckpoint("history-task", checkpoint.id).getOrThrow()
        val history = progressFromJson(progressToJson(session.progress.value!!))
        start("unrelated")
        session.publishCheckpoint("unrelated", draft("其他任务")).getOrThrow()
        session.beginTask(history.copy(taskId = "revision"), draft("历史课表"), "history-task")
        val restored = session.progress.value!!
        assertEquals(listOf(checkpoint.id), restored.checkpoints.map { it.id })
        assertTrue(restored.checkpoints.all { it.taskId == "revision" })
        assertEquals(checkpoint.id, restored.selectedCheckpointId)
        assertTrue(restored.checkpointSelectionLocked)
        assertEquals("历史课表", session.previewDraft.value!!.courses.single().name)
        assertTrue(session.selectCheckpoint("history-task", checkpoint.id).isFailure)
    }

    @Test fun pureHistorySelectionAndEditingCannotMutateTheActiveSession() {
        val history = ensureImportCheckpoint(AiEduImportProgress(taskId = "history"), draft("历史"))
        start("live")
        session.publishCheckpoint("live", draft("正在运行")).getOrThrow()
        val before = session.progress.value
        val selected = selectImportCheckpoint(history, history.checkpoints.single().id, defaultConfig()).getOrThrow()
        val edited = editImportCheckpoint(selected.first, history.checkpoints.single().id,
            draftToPayload(draft("历史修改")).toString(), defaultConfig()).getOrThrow()
        assertEquals("历史修改", edited.second.restore().getOrThrow().courses.single().name)
        assertSame(before, session.progress.value)
        assertEquals("正在运行", session.previewDraft.value!!.courses.single().name)
    }

    @Test fun countCapReportsFailureWithoutEvictingChosenStageOrAcceptingAnEdit() {
        start()
        val first = session.publishCheckpoint("task", draft("选择保留")).getOrThrow()
        session.selectCheckpoint("task", first.id).getOrThrow()
        repeat(AiImportCheckpoint.MaxRetainedCount - 1) { session.publishCheckpoint("task", draft("课程 $it")).getOrThrow() }
        assertTrue(session.publishCheckpoint("task", draft("超过上限")).isFailure)
        assertTrue(session.editCheckpointJson("task", first.id, first.payload).isFailure)
        assertEquals(AiImportCheckpoint.MaxRetainedCount, session.progress.value!!.checkpoints.size)
        assertEquals(first.id, session.progress.value!!.selectedCheckpointId)
        assertEquals("选择保留", session.previewDraft.value!!.courses.single().name)
        assertTrue(session.progress.value!!.checkpointNotice!!.contains("上限"))
        assertTrue(session.publishCheckpoint("task", draft("选择保留")).isSuccess)
    }

    @Test fun byteCapAndOversizedInputNeverReplaceTheSelection() {
        start()
        val first = session.publishCheckpoint("task", draft()).getOrThrow()
        session.selectCheckpoint("task", first.id).getOrThrow()
        val large = "x".repeat(200_000)
        var rejected = false
        repeat(10) { if (session.publishCheckpoint("task", draft("大课表 $it", large)).isFailure) rejected = true }
        assertTrue(rejected)
        assertTrue(session.progress.value!!.checkpoints.sumOf { it.retainedBytes } <= AiImportCheckpoint.MaxRetainedBytes)
        assertTrue(session.editCheckpointJson("task", first.id, " ".repeat(AiImportCheckpoint.MaxPayloadBytes + 1)).isFailure)
        assertEquals(first.id, session.progress.value!!.selectedCheckpointId)
    }

    @Test fun historyRoundTripPreservesSelectionLockIdsAndIgnoresUnknownFields() {
        start()
        val first = session.publishCheckpoint("task", draft()).getOrThrow()
        val edited = session.editCheckpointJson("task", first.id, draftToPayload(draft("编辑")).toString()).getOrThrow()
        session.updateActiveTask("task", draft("最终")) { it.copy(finished = true) }
        val original = session.progress.value!!
        val json = progressToJson(original).put("futureOption", "ignored")
        json.getJSONArray("checkpoints").getJSONObject(0).put("futureStageField", JSONObject().put("version", 4))
        val restored = progressFromJson(json)
        assertEquals(original, restored)
        assertEquals(edited.id, restored.selectedCheckpointId)
        val entry = AiImportHistoryEntry("entry", 0, "", "", "", draftToPayload(draft("旧索引")).toString(), restored)
        assertEquals("编辑", AiImportHistoryStore.restore(entry, defaultConfig()).getOrThrow().courses.single().name)
    }

    @Test fun legacyHistoryAndInvalidFutureCheckpointsRemainReadable() {
        val legacy = JSONObject().put("schemaVersion", 1).put("userPrompt", "旧任务")
        assertTrue(progressFromJson(legacy).checkpoints.isEmpty())
        val seeded = ensureImportCheckpoint(progressFromJson(legacy), draft())
        val json = progressToJson(seeded)
        json.getJSONArray("checkpoints").put(JSONObject().put("id", "invalid").put("payload", "{}"))
        val restored = progressFromJson(json)
        assertEquals(1, restored.checkpoints.size)
        assertEquals(seeded.selectedCheckpointId, restored.selectedCheckpointId)
    }

    @Test fun historySerializationIsBoundedAndDoesNotWriteRawScreenshots() {
        var progress = ensureImportCheckpoint(AiEduImportProgress(taskId = "persist"), draft())
        val image = RenderedPageImage(0, "image/png", "raw-image-secret")
        progress = progress.copy(screenshotPreviews = listOf(image), aiOutput = "大".repeat(900_000),
            conversationTurns = List(20) { AiEduImportConversationTurn("历史 $it", artifactPayload = "z".repeat(200_000)) })
        val json = progressToJson(progress)
        assertTrue(json.toString().toByteArray(Charsets.UTF_8).size <= MaxAiImportContextBytes)
        assertFalse(json.toString().contains("raw-image-secret"))
        assertEquals(progress.selectedCheckpointId, progressFromJson(json).selectedCheckpointId)
        val legacy = JSONObject().put("screenshotPreviews", JSONArray().put(JSONObject()
            .put("pageIndex", 0).put("mimeType", "image/png").put("base64", "legacy-image")))
        assertEquals("legacy-image", progressFromJson(legacy).screenshotPreviews.single().base64)
    }

    @Test fun staleHistorySnapshotsCannotUndoManualSelection() {
        start()
        val first = session.publishCheckpoint("task", draft()).getOrThrow()
        val before = session.progress.value!!
        session.selectCheckpoint("task", first.id).getOrThrow()
        val selected = session.progress.value!!
        assertSame(selected, newerCheckpointProgress(selected, before))
        assertSame(selected, newerCheckpointProgress(before, selected))
    }

    @Test fun predecessorHistoryWriteCannotRollBackANewerTaskInTheSameLineage() {
        start("old-task")
        session.publishCheckpoint("old-task", draft()).getOrThrow()
        val old = session.progress.value!!
        session.beginTask(old.copy(taskId = "new-task"), draft(), "old-task")
        val successor = session.progress.value!!
        assertNotEquals(old.taskId, successor.taskId)
        assertEquals(old.checkpointLineageId, successor.checkpointLineageId)
        assertTrue(successor.checkpointGeneration > old.checkpointGeneration)
        assertSame(successor, newerCheckpointProgress(successor, old.copy(checkpointRevision = 900)))
        val restored = progressFromJson(progressToJson(successor))
        assertEquals(successor.checkpointGeneration, restored.checkpointGeneration)
        assertEquals(successor.checkpointLineageId, restored.checkpointLineageId)
    }

    @Test fun historicalImportConsumptionPreservesAnotherLiveWorkspace() {
        start("live")
        val checkpoint = session.publishCheckpoint("live", draft("保留的工作区")).getOrThrow()
        session.requestFinalImport(draft("历史课表"), false)
        session.consumeFinalImportRequest()
        assertNull(session.finalImportRequest.value)
        assertEquals(checkpoint.id, session.progress.value!!.selectedCheckpointId)
        assertEquals("保留的工作区", session.previewDraft.value!!.courses.single().name)
    }

    @Test fun taskOwnedSideEffectsAreAtomicallyRejectedAfterReplacement() {
        start("old")
        var calls = 0
        assertTrue(session.withCurrentTask("old") { calls++ })
        start("new")
        assertFalse(session.withCurrentTask("old") { calls++ })
        assertTrue(session.withCurrentTask("new") { calls++ })
        assertEquals(2, calls)
    }

    @Test fun repeatedNoOpRevisionsCannotGrowLiveConversationMemoryWithoutBound() {
        val original = draft()
        var progress = AiEduImportProgress(taskId = "bounded-conversation", reasoningOutput = "r".repeat(100_000),
            aiOutput = "o".repeat(100_000))
        repeat(100) { turn -> progress = progress.copy(userPrompt = "第 $turn 轮", conversationTurns = archiveImportTurn(progress, original)) }
        assertEquals(8, progress.conversationTurns.size)
        assertTrue(progress.conversationTurns.all { it.reasoningOutput.length <= 8_192 && it.aiOutput.length <= 16_384 })
        assertTrue(progress.conversationTurns.all { it.artifactPayload.toByteArray().size <= AiImportCheckpoint.MaxPayloadBytes })
    }

    @Test fun explicitHistoryEditBranchesAboveLiveRevisionWithoutChangingIt() {
        start("original")
        session.publishCheckpoint("original", draft("原课表")).getOrThrow()
        val historical = session.progress.value!!
        session.beginTask(historical.copy(taskId = "live-revision"), draft("原课表"), "original")
        session.publishCheckpoint("live-revision", draft("后台修订")).getOrThrow()
        val liveBefore = session.progress.value!!
        val (editedProgress, edited) = editImportCheckpoint(historical, historical.selectedCheckpointId!!,
            draftToPayload(draft("用户编辑历史")).toString(), defaultConfig()).getOrThrow()
        val branch = forkImportCheckpointHistory(editedProgress)
        assertTrue(branch.checkpointGeneration > liveBefore.checkpointGeneration)
        assertEquals(liveBefore.taskId, session.progress.value!!.taskId)
        assertEquals(liveBefore.selectedCheckpointId, session.progress.value!!.selectedCheckpointId)
        assertEquals(liveBefore.checkpointGeneration, session.progress.value!!.checkpointGeneration)
        assertFalse(session.progress.value!!.finished)
        assertEquals("后台修订", session.previewDraft.value!!.courses.single().name)
        assertTrue(session.progress.value!!.checkpoints.any { it.id == edited.id })
        assertEquals(3, branch.checkpoints.size)
        assertEquals(edited.id, branch.selectedCheckpointId)
        session.updateActiveTask("live-revision", draft("更晚的后台结果")) { it.copy(finished = true) }
        assertSame(branch, newerCheckpointProgress(branch, session.progress.value!!))
        assertEquals("用户编辑历史", branch.checkpoints.first { it.id == branch.selectedCheckpointId }
            .restore().getOrThrow().courses.single().name)
    }

    @Test fun oversizedHistoryReservesTheSelectedStageBeforeOtherVersions() {
        val stages = (0..AiImportCheckpoint.MaxRetainedCount).map { index ->
            AiImportCheckpoint.create("oversized-history", draft("阶段 $index"), "阶段 $index")
        }
        val selected = stages.last()
        val progress = AiEduImportProgress(taskId = "oversized-history", checkpoints = stages,
            selectedCheckpointId = selected.id, checkpointSelectionLocked = true)
        val restored = progressFromJson(progressToJson(progress))
        assertEquals(AiImportCheckpoint.MaxRetainedCount, restored.checkpoints.size)
        assertEquals(selected.id, restored.selectedCheckpointId)
        assertTrue(restored.checkpointSelectionLocked)
        assertTrue(restored.checkpoints.any { it.id == selected.id })
        assertNotNull(restored.checkpointNotice)
    }

    @Test fun unknownJsonFieldsDoNotBecomePartOfASavedCheckpoint() {
        start()
        val first = session.publishCheckpoint("task", draft()).getOrThrow()
        val editedJson = JSONObject(first.payload).put("futureMetadata", "not-part-of-course-data").toString()
        val edited = session.editCheckpointJson("task", first.id, editedJson).getOrThrow()
        assertFalse(edited.payload.contains("futureMetadata"))
        assertFalse(edited.payload.contains("not-part-of-course-data"))
        assertEquals(first.payload, edited.payload)
    }

    @Test fun laterExplicitLiveSelectionOrEditTakesPrecedenceOverAHistoryBranch() {
        start("live")
        val first = session.publishCheckpoint("live", draft("第一阶段")).getOrThrow()
        val historical = session.progress.value!!
        session.publishCheckpoint("live", draft("第二阶段")).getOrThrow()
        val historySelection = selectImportCheckpoint(historical, first.id, defaultConfig()).getOrThrow().first
        val historyBranch = forkImportCheckpointHistory(historySelection)
        session.selectCheckpoint("live", first.id).getOrThrow()
        val liveSelection = session.progress.value!!
        assertTrue(liveSelection.checkpointGeneration > historyBranch.checkpointGeneration)
        assertSame(liveSelection, newerCheckpointProgress(historyBranch, liveSelection))
        val laterHistoryBranch = forkImportCheckpointHistory(historySelection)
        session.editCheckpointJson("live", first.id, draftToPayload(draft("最新用户修改")).toString()).getOrThrow()
        val liveEdit = session.progress.value!!
        assertTrue(liveEdit.checkpointGeneration > laterHistoryBranch.checkpointGeneration)
        assertSame(liveEdit, newerCheckpointProgress(laterHistoryBranch, liveEdit))
    }

    @Test fun laterLiveSelectionRetainsAnAcceptedHistoryManualStage() {
        start("live")
        val first = session.publishCheckpoint("live", draft()).getOrThrow()
        val historical = session.progress.value!!
        session.publishCheckpoint("live", draft("后台阶段")).getOrThrow()
        val (editedProgress, manual) = editImportCheckpoint(historical, first.id,
            draftToPayload(draft("保留的手动阶段")).toString(), defaultConfig()).getOrThrow()
        val branch = forkImportCheckpointHistory(editedProgress)
        session.selectCheckpoint("live", first.id).getOrThrow()
        val latest = newerCheckpointProgress(branch, session.progress.value!!)
        assertEquals(first.id, latest.selectedCheckpointId)
        assertTrue(latest.checkpoints.any { it.id == manual.id })
        assertEquals("保留的手动阶段", latest.checkpoints.first { it.id == manual.id }.restore().getOrThrow().courses.single().name)
    }
}
