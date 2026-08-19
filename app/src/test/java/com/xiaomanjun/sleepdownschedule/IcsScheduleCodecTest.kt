package com.xiaomanjun.sleepdownschedule

import java.time.LocalDate
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IcsScheduleCodecTest {
    @Test
    fun parseWeeklyEventCreatesWeeksAndPeriods() {
        val ics = """
            BEGIN:VCALENDAR
            VERSION:2.0
            BEGIN:VEVENT
            DTSTART;TZID=Asia/Shanghai:20260907T080000
            DTEND;TZID=Asia/Shanghai:20260907T094000
            RRULE:FREQ=WEEKLY;COUNT=4;BYDAY=MO
            SUMMARY:高等数学
            LOCATION:A101
            DESCRIPTION:教师：张老师
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()

        val draft = IcsScheduleCodec.parse(ics.toByteArray(), defaultConfig()).getOrThrow()

        assertEquals("2026-09-07", draft.config.termStartDate)
        assertEquals(4, draft.config.totalWeeks)
        assertEquals(listOf(1, 2, 3, 4), draft.courses.single().weeks)
        assertEquals(1, draft.courses.single().weekday)
        assertEquals("张老师", draft.courses.single().teacher)
        assertEquals("08:00", draft.periods.single().startTime)
        assertEquals("09:40", draft.periods.single().endTime)
    }

    @Test
    fun exportedCalendarCanBeImportedAgain() {
        val config = defaultConfig().copy(
            totalWeeks = 4,
            currentWeek = 1,
            termStartDate = "2026-09-07",
            autoCurrentWeek = true,
            morningPeriodCount = 2,
            noonPeriodCount = 0,
            afternoonPeriodCount = 0,
            eveningPeriodCount = 0
        )
        val periods = listOf(
            PeriodEntity(1, "08:00", "08:45"),
            PeriodEntity(2, "08:55", "09:40")
        )
        val courses = listOf(
            CourseEntity(
                id = 10,
                name = "大学英语",
                teacher = "李老师",
                location = "B202",
                weekday = 2,
                periods = listOf(1, 2),
                weeks = listOf(1, 2, 3, 4),
                weekParity = WeekParity.ALL,
                note = "带教材"
            )
        )

        val exported = IcsScheduleCodec.export("测试课表", config, periods, courses, LocalDate.of(2026, 9, 7))
        val imported = IcsScheduleCodec.parse(exported.toByteArray(), defaultConfig()).getOrThrow()

        assertTrue(exported.contains("BEGIN:VCALENDAR"))
        assertEquals("大学英语", imported.courses.single().name)
        assertEquals("B202", imported.courses.single().location)
        assertEquals(listOf(1, 2, 3, 4), imported.courses.single().weeks)
        assertEquals(listOf(1, 2), imported.courses.single().periods)
        assertEquals(listOf("08:00", "08:55"), imported.periods.map { it.startTime })
        assertEquals(listOf("08:45", "09:40"), imported.periods.map { it.endTime })
        assertEquals(2, imported.config.morningPeriodCount)
        assertEquals(0, imported.config.noonPeriodCount)
    }

    @Test
    fun exportUsesCurrentSystemTimeZone() {
        val previous = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Europe/London"))

            val exported = IcsScheduleCodec.export(
                calendarName = "系统时区",
                config = defaultConfig().copy(termStartDate = "2026-09-07"),
                periods = listOf(PeriodEntity(1, "08:00", "08:45")),
                courses = listOf(
                    CourseEntity(
                        name = "测试课程",
                        teacher = null,
                        location = null,
                        weekday = 1,
                        periods = listOf(1),
                        weeks = listOf(1),
                        weekParity = WeekParity.ALL,
                        note = null
                    )
                ),
                today = LocalDate.of(2026, 9, 7)
            )

            assertTrue(exported.contains("DTSTART;TZID=Europe/London:"))
            assertTrue(exported.contains("DTEND;TZID=Europe/London:"))
        } finally {
            TimeZone.setDefault(previous)
        }
    }
}
