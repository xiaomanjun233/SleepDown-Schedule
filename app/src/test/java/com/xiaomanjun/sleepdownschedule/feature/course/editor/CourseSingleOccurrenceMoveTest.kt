package com.xiaomanjun.sleepdownschedule.feature.course.editor

import com.xiaomanjun.sleepdownschedule.*
import com.xiaomanjun.sleepdownschedule.domain.course.conflictWeeksForSingleWeekEdit
import org.junit.Assert.*
import org.junit.Test

class CourseSingleOccurrenceMoveTest {
    private val original = CourseEntity(id = 1, name = "数学", teacher = null, location = null,
        weekday = 1, periods = listOf(1), weeks = listOf(3, 4, 5), weekParity = WeekParity.ALL, note = null)

    @Test fun unchangedWeekSelectionUsesSourceOccurrence() {
        assertEquals(3, courseEditorSingleDestinationWeek(original, original.copy(weekday = 5), 3))
    }

    @Test fun selectingOneDifferentWeekMovesOccurrenceThere() {
        val edited = original.copy(weeks = listOf(4), weekday = 5)
        assertEquals(4, courseEditorSingleDestinationWeek(original, edited, 3))
        assertTrue(conflictWeeksForSingleWeekEdit(original, edited, 3, listOf(original)).isEmpty())
    }

    @Test fun multipleDestinationWeeksCannotMeanOnlyOnce() {
        assertNull(courseEditorSingleDestinationWeek(original, original.copy(weeks = listOf(4, 5)), 3))
    }

    @Test fun destinationConflictIncludesUnmovedOccurrenceOfSameCourse() {
        val edited = original.copy(weeks = listOf(4))
        assertEquals(listOf(4), conflictWeeksForSingleWeekEdit(original, edited, 3, listOf(original)))
    }

    @Test fun conflictCheckUsesDestinationInsteadOfSourceWeek() {
        val edited = original.copy(weeks = listOf(4), weekday = 5)
        val other = original.copy(id = 2, name = "英语", weeks = listOf(3), weekday = 5)
        assertTrue(conflictWeeksForSingleWeekEdit(original, edited, 3, listOf(original, other)).isEmpty())
        assertEquals(listOf(4), conflictWeeksForSingleWeekEdit(original, edited, 3, listOf(original, other.copy(weeks = listOf(4)))))
    }
}
