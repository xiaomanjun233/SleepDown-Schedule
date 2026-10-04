package com.xiaomanjun.sleepdownschedule.domain.schedule

import com.xiaomanjun.sleepdownschedule.model.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class PeriodSchemeLibraryTest {
    private val config = defaultConfig(7).copy(morningPeriodCount = 2, noonPeriodCount = 0,
        afternoonPeriodCount = 0, eveningPeriodCount = 0)
    private val source = PeriodSchemeDraft(PeriodSchemeEntity(42, 7, "夏令时", isActive = true), listOf(
        PeriodSchemeTimeEntity(42, 1, "09:00", "09:50"), PeriodSchemeTimeEntity(42, 2, "10:20", "11:00")))

    @Test fun snapshotHasNoScheduleIdentityAndSurvivesSerialization() {
        val saved = savePeriodSchemeSnapshot("independent", "夏令时", config, source)
        val serialized = Json.encodeToString(saved)
        assertFalse(serialized.contains("scheduleId"))
        assertFalse(serialized.contains("schemeId"))
        assertEquals(saved, Json.decodeFromString<SavedPeriodScheme>(serialized))
    }

    @Test fun applyingToDifferentSchedulesCopiesBellsAndPreservesExistingSchemes() {
        val saved = savePeriodSchemeSnapshot("independent", "夏令时", config, source)
        for (scheduleId in listOf(8, 19)) {
            val targetConfig = config.copy(id = scheduleId, morningPeriodCount = 1, afternoonPeriodCount = 1)
            val existing = source.copy(scheme = source.scheme.copy(scheduleId = scheduleId, name = "原作息"))
            val draft = SchedulePeriodSchemesDraft(listOf(existing), 42)
            val applied = applySavedPeriodScheme(saved, targetConfig, draft)
            assertEquals(scheduleId, applied.config.id)
            assertEquals(2, applied.config.morningPeriodCount)
            assertEquals(0, applied.config.afternoonPeriodCount)
            assertEquals(2, applied.draft.schemes.size)
            assertEquals(existing.times, applied.draft.schemes.first().times)
            val added = applied.draft.schemes.last()
            assertTrue(added.scheme.id < 0)
            assertEquals(scheduleId, added.scheme.scheduleId)
            assertEquals(added.scheme.id, applied.draft.activeSchemeId)
            assertEquals(listOf("09:00", "10:20"), added.times.map { it.startTime })
            assertTrue(applied.draft.topologyOperations.isEmpty())
            assertEquals(1, draft.schemes.size)
        }
    }

    @Test fun storedAutomaticBellsAreNeverRegeneratedBySavingOrApplyingLibrary() {
        val auto = source.copy(scheme = source.scheme.copy(mode = PeriodSchemeMode.AUTO_MATCH))
        val saved = savePeriodSchemeSnapshot("independent", "自定义铃声", config, auto)
        assertEquals("10:20", saved.times.last().startTime)
        val applied = applySavedPeriodScheme(saved, config, SchedulePeriodSchemesDraft(listOf(auto), 42))
        assertEquals(auto.times, applied.draft.schemes.first().times)
        assertEquals(PeriodSchemeMode.MANUAL, applied.draft.schemes.first().scheme.mode)
    }

    @Test fun repeatedApplicationUsesDistinctDraftIdsAndKeepsPendingTopologyOperations() {
        val saved = savePeriodSchemeSnapshot("independent", "夏令时", config, source)
        val draft = SchedulePeriodSchemesDraft(listOf(source), 42, listOf(PeriodTopologyOperation.AddAfter(1)))
        val first = applySavedPeriodScheme(saved, config, draft)
        val second = applySavedPeriodScheme(saved, first.config, first.draft)
        assertNotEquals(first.draft.activeSchemeId, second.draft.activeSchemeId)
        assertEquals(draft.topologyOperations, second.draft.topologyOperations)
    }

    @Test fun mismatchedPeriodCountCannotRewriteTargetCourseNumbering() {
        val saved = savePeriodSchemeSnapshot("independent", "夏令时", config, source)
        val target = config.copy(morningPeriodCount = 3)
        val draft = SchedulePeriodSchemesDraft(listOf(source), 42)
        try { applySavedPeriodScheme(saved, target, draft); fail("Must reject different period counts") }
        catch (expected: IllegalArgumentException) { assertTrue(expected.message!!.contains("先在详细节次编辑中调整")) }
        assertEquals(42L, draft.activeSchemeId)
        assertEquals(3, target.morningPeriodCount)
    }
}
