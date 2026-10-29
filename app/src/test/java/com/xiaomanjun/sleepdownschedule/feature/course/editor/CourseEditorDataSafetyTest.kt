package com.xiaomanjun.sleepdownschedule.feature.course.editor

import com.xiaomanjun.sleepdownschedule.CourseEntity
import com.xiaomanjun.sleepdownschedule.WeekParity
import com.xiaomanjun.sleepdownschedule.defaultConfig
import com.xiaomanjun.sleepdownschedule.PeriodEntity
import com.xiaomanjun.sleepdownschedule.domain.schedule.courseTimeSegments
import com.xiaomanjun.sleepdownschedule.domain.schedule.withCustomTimetableTimes
import org.junit.Assert.*
import org.junit.Test

class CourseEditorDataSafetyTest {
    @Test fun manuallySelectedCustomClocksCannotStartBeforeTheFirstBell() {
        val bells = listOf(PeriodEntity(1, "09:00", "09:45"), PeriodEntity(2, "09:55", "10:40"))
        val early = course.copy(customStartTime = "08:20", customEndTime = "09:05")
        assertNotNull(manualCourseClockError(early, null, bells))
        assertNull(manualCourseClockError(early.copy(customStartTime = "09:00", customEndTime = "09:45"), null, bells))
        assertNotNull(customCourseStartError("09:00", "1,08:20-09:05", courseClockOrigin(bells)))
    }

    @Test fun renamingAnExistingEarlyCourseKeepsItsRealClockWhileManualTimeChangesAreChecked() {
        val bells = listOf(PeriodEntity(1, "09:00", "09:45"))
        val imported = course.copy(customStartTime = "08:20", customEndTime = "09:05")
        assertNull(manualCourseClockError(imported.copy(name = "改名", location = "B201"), imported, bells))
        assertNotNull(manualCourseClockError(imported.copy(customStartTime = "08:30"), imported, bells))
    }
    @Test fun editingLegacyReferenceKeepsEveryLessonClockAfterItBecomesCustom() {
        val legacy = course.copy(independentPeriodSchemeId = 9)
        val reference = listOf(PeriodEntity(3, "08:20", "09:05"), PeriodEntity(4, "09:15", "10:00"))
        val converted = legacy.withCustomTimetableTimes(reference)
        val draft = courseEditorDraft(listOf(converted), periods, 16).copy(name = "线性代数")
        val saved = draft.toCourses(listOf(legacy), periods, true).single()
        assertNull(saved.independentPeriodSchemeId)
        assertEquals(converted.customPeriodTimes, saved.customPeriodTimes)
        assertEquals(courseTimeSegments(legacy, reference), courseTimeSegments(saved, emptyList()))
        assertEquals(legacy.weeks, saved.weeks)
    }

    @Test fun explicitPeriodRangeReplacesTheGapsAndSurvivesReopening() {
        val source = course.copy(periods = listOf(1, 3), customStartTime = "08:00", customEndTime = "10:45")
        val draft = courseEditorDraft(listOf(source), listOf(1, 2, 3), 20).copy(
            customStartTime = null, customEndTime = null, customPeriodTimes = null,
            periodRangeEdited = true)
        val saved = draft.toCourses(listOf(source), listOf(1, 2, 3), true).single()
        assertEquals(listOf(1, 2, 3), saved.periods)
        val reopened = courseEditorDraft(listOf(saved), listOf(1, 2, 3), 20)
        assertEquals(1, reopened.periodStart)
        assertEquals(3, reopened.periodEnd)
        assertNull(reopened.customStartTime)
        assertNull(reopened.customPeriodTimes)
    }

    private val periods = listOf(3, 4)
    private val course = CourseEntity(
        id = 12, name = "高等数学", teacher = "林老师", location = "A101", weekday = 1,
        periods = periods, weeks = listOf(1, 2, 18), weekParity = WeekParity.ALL,
        note = null, scheduleId = 1
    )

    @Test fun identicalMondayAndWednesdayCoursesRemainSeparate() {
        val wednesday = course.copy(id = 13, weekday = 3)
        val groups = buildCourseEditorGroups(wednesday, listOf(course, wednesday))

        assertEquals(2, groups.size)
        assertEquals(listOf(course), groups.single { it.representative?.weekday == 1 }.courses)
        assertEquals(listOf(wednesday), groups.single { it.representative?.weekday == 3 }.courses)
    }

    @Test fun openingLaterFragmentKeepsTheClickedRecordForSingleOperations() {
        val early = course.copy(id = 1, weeks = listOf(1, 2))
        val late = course.copy(id = 20, weeks = listOf(18))
        val group = buildCourseEditorGroups(late, listOf(early, late)).single()

        assertEquals(late, group.representative)
        assertEquals(late, group.courses.first())
        assertTrue(courseEditorHasOccurrence(group.representative!!, 18))
        assertFalse(courseEditorHasOccurrence(group.representative!!, 2))
    }

    @Test fun renamingOutOfTermCoursePreservesWeekEighteen() {
        val original = course.copy(weeks = listOf(18))
        val draft = courseEditorDraft(listOf(original), periods, totalWeeks = 16)
        val edited = draft.copy(name = "线性代数").toCourses(listOf(original), periods, true).single()

        assertEquals(setOf(18), draft.weeks)
        assertEquals(original.copy(name = "线性代数"), edited)
    }

    @Test fun renamingPartlyOutOfTermCourseKeepsEveryOriginalWeek() {
        val draft = courseEditorDraft(listOf(course), periods, totalWeeks = 16)
        val edited = draft.copy(name = "线性代数").toCourses(listOf(course), periods, true).single()

        assertEquals(course.weeks, edited.weeks)
        assertEquals(course.weekParity, edited.weekParity)
    }

    @Test fun renamingParityCoursePreservesStoredRangeAndParity() {
        val original = course.copy(weeks = (1..20).toList(), weekParity = WeekParity.ODD)
        val draft = courseEditorDraft(listOf(original), periods, totalWeeks = 16)
        val edited = draft.copy(name = "线性代数").toCourses(listOf(original), periods, true).single()

        assertEquals((1..20).filter { it % 2 == 1 }.toSet(), draft.weeks)
        assertEquals(original.copy(name = "线性代数"), edited)
    }

    @Test fun emptyOrInactiveExistingWeeksNeverGetAWholeTermDefault() {
        val empty = courseEditorDraft(listOf(course.copy(weeks = emptyList())), periods, 16)
        val inactive = courseEditorDraft(listOf(course.copy(weeks = listOf(18), weekParity = WeekParity.ODD)), periods, 16)

        assertTrue(empty.weeks.isEmpty())
        assertTrue(inactive.weeks.isEmpty())
        assertTrue(empty.toCourses(listOf(course.copy(weeks = emptyList())), periods, true).isEmpty())
    }

    @Test fun newCourseAloneDefaultsToTheCurrentTerm() {
        assertEquals((1..16).toSet(), courseEditorDraft(emptyList(), periods, 16).weeks)
    }

    @Test fun explicitWeekChangesAreNotReplacedByApplyAllExpansion() {
        val edited = course.copy(weeks = listOf(2))
        val scope = courseEditorApplyAllScope(listOf(course), listOf(edited), listOf(course))

        assertEquals(listOf(course), scope.originals)
        assertEquals(listOf(edited), scope.edited)
    }

    @Test fun groupedSaveKeepsAnExplicitScopeWithoutTouchingOtherWeekdays() {
        val early = course.copy(id = 1, weeks = listOf(1, 2))
        val late = course.copy(id = 20, weeks = listOf(18))
        val wednesday = course.copy(id = 30, weekday = 3)
        val originals = buildCourseEditorGroups(late, listOf(early, late, wednesday))
            .single { it.courses.any { entry -> entry.id == late.id } }.courses
        val replacements = courseEditorDraft(originals, periods, 16)
            .copy(name = "线性代数").toCourses(originals, periods, true)
        val scope = courseEditorApplyAllScope(originals, replacements, listOf(early, late, wednesday))

        assertEquals(setOf(1L, 20L), scope.originals.map(CourseEntity::id).toSet())
        assertEquals(listOf(1, 2, 18), scope.edited.single().weeks)
        assertEquals(1, scope.edited.single().weekday)
    }

    @Test fun confirmationDatesMatchTheSelectedWeekdayAndActiveWeeks() {
        val config = defaultConfig().copy(autoCurrentWeek = true, termStartDate = "2026-08-31", totalWeeks = 20)
        val original = course.copy(weeks = listOf(1, 2, 3), weekParity = WeekParity.ODD)
        val summary = courseEditorScopeDescription(listOf(original), config)

        assertTrue(summary.contains("周一"))
        assertTrue(summary.contains("2026-08-31"))
        assertTrue(summary.contains("2026-09-14"))
        assertFalse(summary.contains("2026-09-07"))
        assertEquals("第3周 · 周一 · 2026-09-14", courseEditorOccurrenceLabel(original, 3, config))
    }
}
