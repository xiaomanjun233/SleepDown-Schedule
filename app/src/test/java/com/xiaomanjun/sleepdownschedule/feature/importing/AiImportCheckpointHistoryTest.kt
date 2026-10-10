package com.xiaomanjun.sleepdownschedule.feature.importing

import android.app.Application
import com.xiaomanjun.sleepdownschedule.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class, sdk = [28])
class AiImportCheckpointHistoryTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val session = AiEduImportProgressSession

    private fun draft(name: String) = ImportDraft(defaultConfig(), listOf(PeriodEntity(1, "08:00", "08:45")),
        listOf(CourseEntity(name = name, teacher = null, location = null, weekday = 1, periods = listOf(1),
            weeks = listOf(1, 2), weekParity = WeekParity.ALL, note = null)), ImportDraftSource.AI_EDU)

    @Before fun prepare() {
        AiImportHistoryStore.clear(context)
        session.beginTask(AiEduImportProgress(taskId = "persisted-task"))
    }

    @After fun cleanup() {
        session.update(null)
        AiImportHistoryStore.clear(context)
    }

    @Test fun successiveMilestonesUpdateOneHistoryRowAndReopenAllValidatedVersions() {
        val firstDraft = draft("第一阶段")
        val first = session.publishCheckpoint("persisted-task", firstDraft).getOrThrow()
        AiImportHistoryStore.record(context, firstDraft, session.progress.value)
        val id = AiImportHistoryStore.load(context).single().id
        val secondDraft = draft("第二阶段")
        session.publishCheckpoint("persisted-task", secondDraft).getOrThrow()
        AiImportHistoryStore.updateMatching(context, firstDraft, secondDraft, session.progress.value!!)
        session.selectCheckpoint("persisted-task", first.id).getOrThrow()
        AiImportHistoryStore.updateMatching(context, secondDraft, firstDraft, session.progress.value!!)
        val restored = AiImportHistoryStore.load(context).single()
        assertEquals(id, restored.id)
        assertEquals(2, restored.context!!.checkpoints.size)
        assertEquals(first.id, restored.context!!.selectedCheckpointId)
        assertEquals("第一阶段", AiImportHistoryStore.restore(restored, defaultConfig()).getOrThrow().courses.single().name)
        assertFalse(restored.context!!.finished)
    }

    @Test fun staleBackgroundWriteCannotUndoPersistedManualEditOrCreateAnotherRow() {
        val original = draft("原始")
        val first = session.publishCheckpoint("persisted-task", original).getOrThrow()
        val stale = session.progress.value!!
        AiImportHistoryStore.record(context, original, stale)
        val edited = session.editCheckpointJson("persisted-task", first.id,
            draftToPayload(draft("用户修订")).toString()).getOrThrow()
        AiImportHistoryStore.updateMatching(context, original, session.previewDraft.value!!, session.progress.value!!)
        AiImportHistoryStore.updateMatching(context, original, original, stale)
        val entry = AiImportHistoryStore.load(context).single()
        assertEquals(edited.id, entry.context!!.selectedCheckpointId)
        assertTrue(entry.context!!.checkpointSelectionLocked)
        assertEquals("用户修订", AiImportHistoryStore.restore(entry, defaultConfig()).getOrThrow().courses.single().name)
        assertEquals(edited.payload, entry.payload)
    }

    @Test fun persistedInterruptedMilestoneRemainsRecoverableWithoutTheLiveSession() {
        val original = draft("停止前已完成的课表")
        val first = session.publishCheckpoint("persisted-task", original).getOrThrow()
        session.updateActiveTask("persisted-task") { it.copy(finished = true, awaitingUserInput = true) }
        AiImportHistoryStore.record(context, original, session.progress.value)
        session.update(null)
        val entry = AiImportHistoryStore.load(context).single()
        val restored = AiImportHistoryStore.restore(entry, defaultConfig()).getOrThrow()
        assertEquals(original.courses, restored.courses)
        assertEquals(first.id, entry.context!!.checkpoints.single().id)
        assertTrue(entry.context!!.awaitingUserInput)
        session.beginTask(entry.context!!.copy(taskId = "continued", finished = false, awaitingUserInput = false),
            restored, preserveCheckpointsFromTaskId = entry.context!!.taskId)
        assertEquals(first.id, session.progress.value!!.selectedCheckpointId)
        assertEquals(original.courses, session.previewDraft.value!!.courses)
    }

    @Test fun delayedPredecessorSaveCannotOverwriteSuccessorOrCreateADuplicate() {
        val original = draft("旧任务")
        session.publishCheckpoint("persisted-task", original).getOrThrow()
        val predecessor = session.progress.value!!
        AiImportHistoryStore.record(context, original, predecessor)
        val id = AiImportHistoryStore.load(context).single().id
        session.beginTask(predecessor.copy(taskId = "revision-task"), original, "persisted-task")
        val revised = draft("新任务")
        session.publishCheckpoint("revision-task", revised).getOrThrow()
        val successor = session.progress.value!!
        AiImportHistoryStore.update(context, id, revised, successor)
        AiImportHistoryStore.update(context, id, original, predecessor)
        AiImportHistoryStore.updateMatching(context, original, original, predecessor)
        AiImportHistoryStore.record(context, original, predecessor)
        val entry = AiImportHistoryStore.load(context).single()
        assertEquals(id, entry.id)
        assertEquals("revision-task", entry.context!!.taskId)
        assertEquals("新任务", AiImportHistoryStore.restore(entry, defaultConfig()).getOrThrow().courses.single().name)
    }
}
