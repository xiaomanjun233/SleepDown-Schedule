package com.xiaomanjun.sleepdownschedule.feature.importing

import com.xiaomanjun.sleepdownschedule.domain.schedule.hasCustomTime
import com.xiaomanjun.sleepdownschedule.feature.agent.AgentImportWorkspace
import com.xiaomanjun.sleepdownschedule.feature.agent.decodeAgentToolCall
import com.xiaomanjun.sleepdownschedule.model.CourseEntity
import com.xiaomanjun.sleepdownschedule.model.ImportDraft
import com.xiaomanjun.sleepdownschedule.model.WeekParity
import com.xiaomanjun.sleepdownschedule.model.defaultConfig
import com.xiaomanjun.sleepdownschedule.model.defaultPeriods
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiImportTimeNormalizationTest {
    private fun draft(start: String = "08:00", end: String = "09:40", periods: List<Int> = listOf(1, 2)) =
        ImportDraft(defaultConfig(), defaultPeriods(), listOf(
            CourseEntity(1, "数学", null, null, 1, periods, listOf(1), WeekParity.ALL, null,
                customStartTime = start, customEndTime = end)
        ))

    @Test fun exactBellRangeSurvivesAiParsingAndStageRestoreAsOrdinaryPeriods() = runBlocking {
        for (source in listOf(draft(), draft(end = "08:45", periods = listOf(1)))) {
            val result = AiImportRepairManager.parseWithRepair(
                AiScheduleImportResult(draftToPayload(source).toString(), "test"), source.config,
                requestRepair = { _, _, _ -> error("Valid time range must not request repair") }
            ).getOrThrow()
            val restored = AiImportCheckpoint.create("matching-bells", result.draft, "识别结果").restore().getOrThrow()
            assertEquals(source.courses.single().periods, restored.courses.single().periods)
            assertNull(restored.courses.single().customStartTime)
            assertNull(restored.courses.single().customEndTime)
            assertFalse(restored.courses.single().hasCustomTime())
        }
    }

    @Test fun mismatchedTimesGapsAndWrongPeriodNumbersRemainExact() {
        for (source in listOf(
            draft(start = "08:01"), draft(end = "09:39"),
            draft(end = "10:45", periods = listOf(1, 3)),
            draft(periods = listOf(2, 3)), draft(periods = listOf(99))
        )) {
            assertEquals(source, normalizeAiImportTimes(source))
        }
    }

    @Test fun trustedBellsAndSharedOrStoredClockPoliciesArePreserved() {
        val source = draft()
        for (course in listOf(
            source.courses.single().copy(customPeriodTimes = "1,08:00-08:45;2,08:55-09:40"),
            source.courses.single().copy(originalPeriodTimes = "1,08:00-08:45;2,08:55-09:40")
        )) {
            val imported = source.copy(courses = listOf(course))
            assertEquals(imported, normalizeAiImportTimes(imported))
        }
        val json = draftToPayload(source).toString()
        assertEquals("08:00", ScheduleImportParser.parseStoredDraft(json, source.config).getOrThrow().courses.single().customStartTime)
        assertEquals("08:00", ScheduleImportParser.parse(json, source.config).getOrThrow().courses.single().customStartTime)
    }

    @Test fun editingMetadataDoesNotChangeAnExistingClockPolicy() {
        val source = draft()
        val renamed = source.copy(courses = source.courses.map { it.copy(name = "改名") })
        assertEquals(renamed, normalizeAiImportTimes(renamed, source))
    }

    @Test fun aiCorrectionToMatchingTimePublishesAnOrdinaryPeriodStage() {
        val stages = mutableListOf<ImportDraft>()
        val workspace = AgentImportWorkspace(draft(start = "08:01")) { stage, _ -> stages += stage }
        val result = workspace.execute(decodeAgentToolCall("fix-clock", "PATCH_IMPORT_JSON", buildJsonObject {
            put("revision", workspace.revision)
            put("operationsJson", """[{"op":"replace","path":"/courses/0/customStartTime","value":"08:00"}]""")
            put("summary", "按节次显示")
        }))
        assertTrue(result.content, result.success)
        assertEquals(1, stages.size)
        assertFalse(stages.single().courses.single().hasCustomTime())
        assertEquals(listOf(1, 2), stages.single().courses.single().periods)
    }

    @Test fun invalidBellSchemeFailsAtAiParsingBeforePreviewPublication() {
        val source = draft()
        for (periods in listOf(
            source.periods.map { if (it.periodIndex == 2) it.copy(startTime = "08:40") else it },
            source.periods.filterNot { it.periodIndex == 2 }
        )) {
            assertTrue(ScheduleImportParser.parseAiResponse(
                draftToPayload(source.copy(periods = periods)).toString(), source.config
            ).isFailure)
        }
    }
}
