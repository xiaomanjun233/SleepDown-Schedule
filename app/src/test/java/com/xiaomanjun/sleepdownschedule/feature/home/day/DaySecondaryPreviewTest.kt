package com.xiaomanjun.sleepdownschedule.feature.home.day

import com.xiaomanjun.sleepdownschedule.CourseEntity
import com.xiaomanjun.sleepdownschedule.PeriodEntity
import com.xiaomanjun.sleepdownschedule.WeekParity
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DaySecondaryPreviewTest {
    private val today = LocalDate.of(2026, 9, 2)
    private val course = CourseEntity(
        id = 1,
        name = "高等数学",
        teacher = null,
        location = null,
        weekday = 3,
        periods = listOf(1, 2),
        weeks = listOf(1),
        weekParity = WeekParity.ALL,
        note = null
    )
    private val periods = listOf(
        PeriodEntity(1, "08:00", "08:45"),
        PeriodEntity(2, "08:55", "09:40")
    )

    @Test
    fun normalDayShowsTomorrowOnlyAfterTodaysLastCourse() {
        assertFalse(
            shouldShowSecondaryDay(
                displayDayCount = 1,
                targetDate = today,
                now = LocalDateTime.of(2026, 9, 2, 9, 39),
                dayCourses = listOf(course),
                periods = periods
            )
        )
        assertTrue(
            shouldShowSecondaryDay(
                displayDayCount = 1,
                targetDate = today,
                now = LocalDateTime.of(2026, 9, 2, 9, 40),
                dayCourses = listOf(course),
                periods = periods
            )
        )
    }

    @Test
    fun normalDayDoesNotAppendTomorrowWhileBrowsingAnotherDate() {
        assertFalse(
            shouldShowSecondaryDay(
                displayDayCount = 1,
                targetDate = today.minusDays(1),
                now = LocalDateTime.of(2026, 9, 2, 23, 0),
                dayCourses = emptyList(),
                periods = periods
            )
        )
    }

    @Test
    fun twoDayModeAlwaysRequestsTheFollowingDate() {
        assertTrue(
            shouldShowSecondaryDay(
                displayDayCount = 2,
                targetDate = today.minusDays(7),
                now = LocalDateTime.of(2026, 9, 2, 8, 0),
                dayCourses = emptyList(),
                periods = periods
            )
        )
    }
}
