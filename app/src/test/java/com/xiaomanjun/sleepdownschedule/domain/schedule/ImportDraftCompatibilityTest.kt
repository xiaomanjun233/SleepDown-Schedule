package com.xiaomanjun.sleepdownschedule.domain.schedule

import com.xiaomanjun.sleepdownschedule.*
import org.junit.Assert.*
import org.junit.Test

class ImportDraftCompatibilityTest {
    private fun course(period: Int) = CourseEntity(name = "课程", teacher = null, location = null,
        weekday = 1, periods = listOf(period), weeks = listOf(1, 2), weekParity = WeekParity.ALL, note = null)

    @Test fun overlappingAndSparsePeriodsKeepExactTimesAndSourceNumbers() {
        val source = listOf(PeriodEntity(2, "08:00", "09:30"), PeriodEntity(5, "08:30", "10:00"))
        val draft = ImportDraft(defaultConfig(), source, listOf(course(2), course(5)))
        val result = draft.withCompatiblePeriodGrid()
        assertEquals(listOf(1, 2, 3), result.periods.map { it.periodIndex })
        assertEquals(listOf(listOf(2), listOf(5)), result.courses.map { it.periods })
        assertEquals(listOf("08:00", "08:30"), result.courses.map { it.customStartTime })
        assertEquals(listOf("09:30", "10:00"), result.courses.map { it.customEndTime })
        assertTrue(result.courses.all { it.originalPeriodTimes != null })
        assertTrue(result.notices.isNotEmpty())
        assertEquals(result, result.withCompatiblePeriodGrid())
        val projected = projectCourseArrangements(result.courses, result.config, result.periods)
        assertTrue(projected.all { it.periods.isNotEmpty() })
        assertEquals(listOf("08:00", "08:30"), projected.map { courseTimeSegments(it, result.periods).first().start.toString() })
    }

    @Test fun validSleepDownArrangementRemainsUnchanged() {
        val draft = ImportDraft(defaultConfig(), listOf(PeriodEntity(1, "08:00", "08:45")), listOf(course(1)))
        assertEquals(draft, draft.withCompatiblePeriodGrid())
    }
}
