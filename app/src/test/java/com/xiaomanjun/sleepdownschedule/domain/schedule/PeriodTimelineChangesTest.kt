package com.xiaomanjun.sleepdownschedule.domain.schedule

import com.xiaomanjun.sleepdownschedule.model.*
import org.junit.Assert.*
import org.junit.Test

class PeriodTimelineChangesTest {
    private val config = defaultConfig().copy(
        morningPeriodCount = 2, noonPeriodCount = 0, afternoonPeriodCount = 1, eveningPeriodCount = 0
    )
    private val scheme = PeriodSchemeDraft(
        PeriodSchemeEntity(id = 1, scheduleId = 1, name = "原作息", mode = PeriodSchemeMode.MANUAL),
        listOf(
            PeriodSchemeTimeEntity(1, 1, "08:00", "08:45"),
            PeriodSchemeTimeEntity(1, 2, "08:55", "09:40"),
            PeriodSchemeTimeEntity(1, 3, "10:00", "10:45")
        )
    )
    private val initial = PeriodTimelineSession(config, SchedulePeriodSchemesDraft(listOf(scheme), 1))

    @Test fun openingAutomaticSchemeDoesNotCreateAnUnsavedChange() {
        val automatic = scheme.copy(
            scheme = scheme.scheme.copy(mode = PeriodSchemeMode.AUTO_MATCH),
            specialBreaks = mapOf(1 to 15), overriddenPeriods = setOf(3)
        )
        val before = initial.updateActive(automatic)
        val opened = before.updateActive(automatic.materializeForTimeline(config))
        assertNotEquals(before.draft, opened.draft)
        assertFalse(opened.hasChangesFrom(before))
    }

    @Test fun confirmingTheSameTimeDoesNotCreateAnUnsavedChange() {
        val unchanged = resizeTimelineBlock(initial, 1, false, 45)
        assertFalse(unchanged.hasChangesFrom(initial))
    }

    @Test fun restoringATimeAfterCompressionClearsTheUnsavedChange() {
        val changed = resizeTimelineBlock(initial, 1, false, 75)
        assertEquals("10:00", changed.active.times[1].endTime)
        assertTrue(changed.hasChangesFrom(initial))
        val restored = resizeTimelineBlock(changed, 1, false, 45)
        assertEquals(initial.active.times, restored.active.times)
        assertFalse(restored.hasChangesFrom(initial))
    }

    @Test fun changingTheSectionAnchorRequiresSaving() {
        val shifted = shiftTimelinePart(initial, PeriodDayPart.MORNING, 490)
        assertTrue(shifted.hasChangesFrom(initial))
        assertFalse(shiftTimelinePart(shifted, PeriodDayPart.MORNING, 480).hasChangesFrom(initial))
    }

    @Test fun creatingASchemeRequiresSavingBeforeAnyFurtherEdit() {
        val added = scheme.copy(
            scheme = scheme.scheme.copy(id = -1, name = "新作息"),
            times = scheme.times.map { it.copy(schemeId = -1) }
        )
        val created = initial.copy(draft = initial.draft.copy(schemes = listOf(scheme, added), activeSchemeId = -1))
        assertTrue(created.hasChangesFrom(initial))
    }

    @Test fun changingAnInactiveSchemeIsStillAnUnsavedChange() {
        val other = scheme.copy(scheme = scheme.scheme.copy(id = 2), times = scheme.times.map { it.copy(schemeId = 2) })
        val before = initial.copy(draft = initial.draft.copy(schemes = listOf(scheme, other)))
        val after = before.updateActive(other.copy(scheme = other.scheme.copy(name = "夏令时")))
        assertEquals(before.draft.activeSchemeId, after.draft.activeSchemeId)
        assertTrue(after.hasChangesFrom(before))
    }

    @Test fun changingSectionCountsRequiresSaving() {
        val repartitioned = initial.copy(config = config.copy(morningPeriodCount = 3, afternoonPeriodCount = 0))
        assertTrue(repartitioned.hasChangesFrom(initial))
    }

    @Test fun replacingALessonStillRequiresSavingItsCourseMapping() {
        val removed = requireNotNull(deleteTimelinePeriod(initial, 2))
        val replaced = requireNotNull(insertTimelinePeriod(removed, removed.vacancies.single().id))
        assertEquals(initial.config, replaced.config)
        assertEquals(initial.active.times, replaced.active.times)
        assertTrue(replaced.hasChangesFrom(initial))
    }
}
