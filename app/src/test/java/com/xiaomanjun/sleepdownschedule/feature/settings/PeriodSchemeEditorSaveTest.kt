package com.xiaomanjun.sleepdownschedule.feature.settings

import com.xiaomanjun.sleepdownschedule.domain.schedule.PeriodSchemeDraft
import com.xiaomanjun.sleepdownschedule.domain.schedule.PeriodTimelineSession
import com.xiaomanjun.sleepdownschedule.domain.schedule.SchedulePeriodSchemesDraft
import com.xiaomanjun.sleepdownschedule.domain.schedule.applySavedPeriodScheme
import com.xiaomanjun.sleepdownschedule.domain.schedule.materializeForTimeline
import com.xiaomanjun.sleepdownschedule.domain.schedule.savePeriodSchemeSnapshot
import com.xiaomanjun.sleepdownschedule.model.PeriodSchemeEntity
import com.xiaomanjun.sleepdownschedule.model.PeriodEntity
import com.xiaomanjun.sleepdownschedule.model.PeriodSchemeMode
import com.xiaomanjun.sleepdownschedule.model.PeriodSchemeTimeEntity
import com.xiaomanjun.sleepdownschedule.model.defaultConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PeriodSchemeEditorSaveTest {
    private fun original(): PeriodTimelineSession {
        val config = defaultConfig().copy(activePeriodSchemeId = 7, morningPeriodCount = 2, noonPeriodCount = 0,
            afternoonPeriodCount = 0, eveningPeriodCount = 0)
        val scheme = PeriodSchemeEntity(id = 7, scheduleId = 0, name = "原作息", publicId = "public-7",
            mode = PeriodSchemeMode.AUTO_MATCH, morningPeriodCount = 2,
            specialBreaksJson = "{\"1\":25}", overridesJson = "[2]")
        val item = PeriodSchemeDraft(scheme, listOf(
            PeriodSchemeTimeEntity(7, 1, "08:00", "08:45"),
            PeriodSchemeTimeEntity(7, 2, "09:10", "09:55")
        ), specialBreaks = mapOf(1 to 25), overriddenPeriods = setOf(2))
        return PeriodTimelineSession(config, SchedulePeriodSchemesDraft(listOf(item), 7,
            originalSchemes = listOf(item), originalActiveSchemeId = 7))
    }

    private fun renamedEditorSession(original: PeriodTimelineSession): PeriodTimelineSession {
        val materialized = original.active.materializeForTimeline(original.config)
        return original.updateActive(materialized.copy(scheme = materialized.scheme.copy(name = "新名称")))
    }

    @Test
    fun renameOnlyKeepsAutomaticRulesAndStableIdentity() {
        val original = original()
        val result = preservePeriodSchemeMetadataForRename(original, renamedEditorSession(original))

        assertEquals(original.active.copy(scheme = original.active.scheme.copy(name = "新名称")), result.active)
        assertEquals(PeriodSchemeMode.AUTO_MATCH, result.active.scheme.mode)
        assertEquals(mapOf(1 to 25), result.active.specialBreaks)
        assertEquals(setOf(2), result.active.overriddenPeriods)
        assertEquals(original.draft.originalSchemes, result.draft.originalSchemes)
    }

    @Test
    fun renameWithActualBellEditKeepsTheEditedSession() {
        val original = original()
        val renamed = renamedEditorSession(original)
        val edited = renamed.updateActive(renamed.active.copy(times = renamed.active.times.map {
            if (it.periodIndex == 1) it.copy(endTime = "08:46") else it
        }))

        assertSame(edited, preservePeriodSchemeMetadataForRename(original, edited))
    }

    @Test
    fun renameWithDayPartBoundaryEditKeepsTheEditedSession() {
        val original = original()
        val edited = renamedEditorSession(original).copy(config = original.config.copy(
            morningPeriodCount = 1, noonPeriodCount = 1))

        assertSame(edited, preservePeriodSchemeMetadataForRename(original, edited))
    }

    @Test
    fun loadedAutomaticSchemeUsesAuthoritativeStoredBells() {
        val original = original()
        val stored = original.active.copy(times = listOf(
            PeriodSchemeTimeEntity(7, 2, "09:12", "10:00"),
            PeriodSchemeTimeEntity(7, 1, "08:03", "08:49")
        ))

        assertEquals(listOf(
            PeriodEntity(1, "08:03", "08:49", 23),
            PeriodEntity(2, "09:12", "10:00", 23)
        ), storedPeriodSchemePeriods(23, stored))
        assertEquals(stored.times.sortedBy { it.periodIndex }, periodSchemeTimelineTimes(original.config, stored))
        assertEquals(PeriodSchemeMode.AUTO_MATCH, stored.scheme.mode)
    }

    @Test
    fun emptyInitialTimelineStillGeneratesItsFirstBells() {
        val original = original()
        assertEquals(original.active.times,
            periodSchemeTimelineTimes(original.config, original.active.copy(times = emptyList())))
    }

    @Test
    fun unrelatedTermChangesDoNotPreventDetachedSelection() {
        val original = original()
        assertFalse(hasPendingPeriodSchemeEdits(original.config.copy(totalWeeks = 30), original.draft))
    }

    @Test
    fun pendingBellAndDurationEditsBlockSelectionWithoutChangingTheDraft() {
        val original = original()
        val edited = original.updateActive(original.active.copy(times = original.active.times.map {
            if (it.periodIndex == 1) it.copy(endTime = "08:46") else it
        }))

        assertTrue(hasPendingPeriodSchemeEdits(edited.config, edited.draft))
        assertEquals("08:46", edited.active.times.first().endTime)
        assertEquals("08:45", original.active.times.first().endTime)
        assertTrue(hasPendingPeriodSchemeEdits(original.config.copy(classDurationMinutes = 50), original.draft))
    }

    @Test
    fun selectingAThenBThenARemainsAnUnchangedDetachedReference() {
        val original = original()
        val target = original.active.copy(scheme = original.active.scheme.copy(
            id = 8, publicId = "public-8", name = "另一作息"),
            times = original.active.times.map { it.copy(schemeId = 8) })
        val savedA = savePeriodSchemeSnapshot("public-7", "原作息", original.config, original.active)
            .copy(storedDraft = original.active)
        val savedB = savePeriodSchemeSnapshot("public-8", "另一作息", original.config, target)
            .copy(storedDraft = target)

        val selectedB = applySavedPeriodScheme(savedB, original.config, original.draft)
        assertFalse(hasPendingPeriodSchemeEdits(selectedB.config, selectedB.draft))
        val selectedA = applySavedPeriodScheme(savedA, selectedB.config, selectedB.draft)

        assertFalse(hasPendingPeriodSchemeEdits(selectedA.config, selectedA.draft))
        assertEquals(original.config, selectedA.config)
        assertEquals(original.draft, selectedA.draft)
        assertEquals(7L, original.draft.activeSchemeId)
    }
}
