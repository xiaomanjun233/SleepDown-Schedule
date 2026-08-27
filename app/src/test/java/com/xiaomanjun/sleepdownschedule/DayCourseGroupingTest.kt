package com.xiaomanjun.sleepdownschedule


import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DayCourseGroupingTest {
    private val config = defaultConfig().copy(
        morningPeriodCount = 4,
        noonPeriodCount = 2,
        afternoonPeriodCount = 4,
        eveningPeriodCount = 2
    )

    private fun course(periods: List<Int>) = CourseEntity(
        id = periods.firstOrNull()?.toLong() ?: 0L,
        name = "测试课程",
        teacher = null,
        location = null,
        weekday = 1,
        periods = periods,
        weeks = listOf(1),
        weekParity = WeekParity.ALL,
        note = null
    )

    @Test
    fun usesFirstCoursePeriodToChooseConfiguredDayPart() {
        assertEquals(PeriodDayPart.MORNING, courseDayPart(config, course(listOf(1, 2))))
        assertEquals(PeriodDayPart.NOON, courseDayPart(config, course(listOf(5, 6))))
        assertEquals(PeriodDayPart.AFTERNOON, courseDayPart(config, course(listOf(7, 8))))
        assertEquals(PeriodDayPart.EVENING, courseDayPart(config, course(listOf(11, 12))))
    }

    @Test
    fun emptyCourseHasNoDayPart() {
        assertNull(courseDayPart(config, course(emptyList())))
    }
}
