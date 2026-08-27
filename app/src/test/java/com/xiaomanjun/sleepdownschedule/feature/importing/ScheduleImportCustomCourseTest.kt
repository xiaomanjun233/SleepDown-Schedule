package com.xiaomanjun.sleepdownschedule.feature.importing

import com.xiaomanjun.sleepdownschedule.*

import org.junit.Assert.assertEquals
import org.junit.Test

class ScheduleImportCustomCourseTest {
    @Test
    fun sleepDownTokenRoundTripPreservesExactTimeAndCourseColor() {
        val config = defaultConfig().copy(totalWeeks = 16)
        val periods = listOf(
            PeriodEntity(1, "08:00", "08:45"),
            PeriodEntity(2, "08:55", "09:40")
        )
        val source = CourseEntity(
            name = "高等数学",
            teacher = "林老师",
            location = "A101",
            weekday = 1,
            periods = listOf(1, 2),
            weeks = (1..16).toList(),
            weekParity = WeekParity.ALL,
            note = null,
            customStartTime = "08:12",
            customEndTime = "09:26",
            customColorArgb = 0xFF6688AAL
        )

        val token = buildSleepDownScheduleToken(config, periods, listOf(source))
        val imported = ScheduleImportParser.parse(token, defaultConfig()).getOrThrow().courses.single()

        assertEquals("08:12", imported.customStartTime)
        assertEquals("09:26", imported.customEndTime)
        assertEquals(0xFF6688AAL, imported.customColorArgb)
    }
}
