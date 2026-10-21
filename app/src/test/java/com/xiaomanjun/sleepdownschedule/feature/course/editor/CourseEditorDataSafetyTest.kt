package com.xiaomanjun.sleepdownschedule.feature.course.editor

import com.xiaomanjun.sleepdownschedule.CourseEntity
import com.xiaomanjun.sleepdownschedule.WeekParity
import com.xiaomanjun.sleepdownschedule.defaultConfig
import org.junit.Assert.*
import org.junit.Test

class CourseEditorDataSafetyTest {
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
