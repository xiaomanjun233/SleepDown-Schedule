package com.xiaomanjun.sleepdownschedule

import org.junit.Assert.assertEquals
import org.junit.Test

class CourseEditorWeekSelectionTest {
    @Test
    fun `infers every odd even and custom week selections`() {
        assertEquals(CourseWeekSelectionMode.EVERY, inferCourseWeekSelectionMode((3..8).toList()))
        assertEquals(CourseWeekSelectionMode.ODD, inferCourseWeekSelectionMode(listOf(1, 3, 5, 7)))
        assertEquals(CourseWeekSelectionMode.EVEN, inferCourseWeekSelectionMode(listOf(2, 4, 6, 8)))
        assertEquals(CourseWeekSelectionMode.CUSTOM, inferCourseWeekSelectionMode(listOf(1, 3, 7)))
    }

    @Test
    fun `switching parity rebuilds within the current selected bounds`() {
        assertEquals(
            setOf(3, 5, 7),
            weeksForCourseWeekSelectionMode(CourseWeekSelectionMode.ODD, setOf(3, 4, 8), 20)
        )
        assertEquals(
            setOf(4, 6, 8),
            weeksForCourseWeekSelectionMode(CourseWeekSelectionMode.EVEN, setOf(3, 4, 8), 20)
        )
        assertEquals(
            (3..8).toSet(),
            weeksForCourseWeekSelectionMode(CourseWeekSelectionMode.EVERY, setOf(3, 4, 8), 20)
        )
    }

    @Test
    fun `preserves a week split out as an independent occurrence`() {
        val course = CourseEntity(
            id = 1,
            name = "材料分析测试技术",
            teacher = null,
            location = "31-0203",
            weekday = 1,
            periods = listOf(7, 8, 9),
            weeks = listOf(1) + (3..16),
            weekParity = WeekParity.ALL,
            note = null,
            scheduleId = 1
        )

        val excluded = excludedWeeksInsideCourseRange(course)

        assertEquals(setOf(2), excluded)
        assertEquals(listOf(1) + (3..16), weeksInEditorRange(1, 16, excluded))
        assertEquals("第1、3–16周", compactWeekSelectionLabel(weeksInEditorRange(1, 16, excluded)))
    }
}
