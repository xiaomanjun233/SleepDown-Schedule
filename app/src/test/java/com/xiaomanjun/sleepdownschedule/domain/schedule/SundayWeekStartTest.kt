package com.xiaomanjun.sleepdownschedule.domain.schedule

import com.xiaomanjun.sleepdownschedule.model.*
import com.xiaomanjun.sleepdownschedule.feature.home.day.visibleWeekdaysForBuckets
import com.xiaomanjun.sleepdownschedule.feature.home.day.weekCourseBuckets
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class SundayWeekStartTest {
    private val firstMonday = LocalDate.of(2026, 10, 5)
    private val config = defaultConfig().copy(termStartDate = firstMonday.toString(), totalWeeks = 3,
        weekStartsOnSunday = true)

    @Test fun sundaySwitchesTheTeachingWeekWhileMondayDefaultKeepsTheOldWeek() {
        val sunday = firstMonday.plusDays(6)
        assertEquals(1, effectiveCurrentWeek(config, sunday.minusDays(1)))
        assertEquals(2, effectiveCurrentWeek(config, sunday))
        assertEquals(2, effectiveCurrentWeek(config, sunday.plusDays(1)))
        assertEquals(1, effectiveCurrentWeek(config.copy(weekStartsOnSunday = false), sunday))
        assertEquals(sunday, scheduleWeekStartDate(config, 2))
    }

    @Test fun isoWeekdayNumbersMapToTheCorrectDatesInBothOrders() {
        for (sundayFirst in listOf(false, true)) {
            val setting = config.copy(weekStartsOnSunday = sundayFirst)
            val start = scheduleWeekStartDate(setting, 2)
            for (weekday in 1..7) {
                val date = start.plusDays(scheduleWeekdayOffset(setting, weekday))
                assertEquals(weekday, date.dayOfWeek.value)
                assertEquals(2, scheduleWeekForDateOrNull(setting, date))
            }
        }
    }

    @Test fun actualTermOpeningStillGatesCoursesAndTheLastWeekEndsOnSaturday() {
        assertNull(scheduleWeekForDateOrNull(config, firstMonday.minusDays(1)))
        val lastSaturday = firstMonday.plusDays(19)
        assertEquals(lastSaturday, scheduleTermEndDate(config))
        assertEquals(3, scheduleWeekForDateOrNull(config, lastSaturday))
        assertNull(scheduleWeekForDateOrNull(config, lastSaturday.plusDays(1)))
    }

    @Test fun dailyQueriesUseSundayInTheNewWeekWithoutRewritingTheCourse() {
        val course = CourseEntity(id = 1, name = "周日课", teacher = null, location = null, weekday = 7,
            periods = listOf(1), weeks = listOf(2), weekParity = WeekParity.ALL, note = null)
        val state = AppState(config = config, courses = listOf(course))
        assertEquals(listOf(course), coursesForDate(state, firstMonday.plusDays(6)))
        assertTrue(coursesForDate(state, firstMonday.plusDays(13)).isEmpty())
        val buckets = weekCourseBuckets(listOf(course), 2, config)
        assertEquals(listOf(7, 1, 2, 3, 4, 5, 6), visibleWeekdaysForBuckets(buckets, false, true))
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7), visibleWeekdaysForBuckets(buckets, false, false))
    }
}
