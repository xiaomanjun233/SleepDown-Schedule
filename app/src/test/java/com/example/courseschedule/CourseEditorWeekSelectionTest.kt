package com.example.courseschedule

import org.junit.Assert.assertEquals
import org.junit.Test

class CourseEditorWeekSelectionTest {
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
