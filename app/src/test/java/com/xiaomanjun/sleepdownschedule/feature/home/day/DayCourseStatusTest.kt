package com.xiaomanjun.sleepdownschedule.feature.home.day

import com.xiaomanjun.sleepdownschedule.model.CourseEntity
import com.xiaomanjun.sleepdownschedule.model.PeriodEntity
import com.xiaomanjun.sleepdownschedule.model.WeekParity
import java.time.LocalDate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DayCourseStatusTest {
    private val today = LocalDate.of(2026, 9, 21)
    private val course = CourseEntity(id = 1, name = "课程", teacher = null, location = null,
        weekday = 1, periods = listOf(1, 2), weeks = listOf(4), weekParity = WeekParity.ALL, note = null)
    private val periods = listOf(PeriodEntity(1, "08:00", "08:45"), PeriodEntity(2, "08:55", "09:40"))

    @Test fun staysColoredBeforeAndDuringClassAndTurnsGrayAtItsFinalEnd() {
        listOf(7 to 59, 8 to 0, 8 to 45, 9 to 39).forEach { (hour, minute) ->
            assertFalse(hasDayCourseEnded(course, periods, today, today.atTime(hour, minute)))
        }
        assertTrue(hasDayCourseEnded(course, periods, today, today.atTime(9, 40)))
        assertTrue(hasDayCourseEnded(course, periods, today, today.atTime(23, 59)))
    }

    @Test fun otherDatesStayColoredAndMidnightResetsThePreviousDay() {
        val now = today.atTime(23, 59)
        assertFalse(hasDayCourseEnded(course, periods, today.minusDays(1), now))
        assertFalse(hasDayCourseEnded(course, periods, today.plusDays(1), now))
        assertFalse(hasDayCourseEnded(course, periods, today, today.plusDays(1).atStartOfDay()))
    }

    @Test fun customTimeTakesPrecedenceOverPeriodTime() {
        val custom = course.copy(customStartTime = "14:00", customEndTime = "15:35")
        assertFalse(hasDayCourseEnded(custom, periods, today, today.atTime(10, 0)))
        assertFalse(hasDayCourseEnded(custom, periods, today, today.atTime(15, 34)))
        assertTrue(hasDayCourseEnded(custom, periods, today, today.atTime(15, 35)))
    }

    @Test fun missingOrInvalidEndTimeDoesNotPretendTheCourseHasEnded() {
        val now = today.atTime(23, 59)
        assertFalse(hasDayCourseEnded(course, periods.take(1), today, now))
        assertFalse(hasDayCourseEnded(course, listOf(PeriodEntity(2, "08:55", "invalid")), today, now))
        assertFalse(hasDayCourseEnded(course.copy(periods = emptyList()), periods, today, now))
    }

    @Test fun makeUpClassUsesItsDisplayedDateRegardlessOfTheOriginalWeekday() {
        val moved = course.copy(weekday = 5)
        assertTrue(hasDayCourseEnded(moved, periods, today, today.atTime(9, 40)))
        assertFalse(hasDayCourseEnded(moved, periods, today.plusDays(4), today.atTime(9, 40)))
    }
}
