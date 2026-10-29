package com.xiaomanjun.sleepdownschedule.feature.home.day

import com.xiaomanjun.sleepdownschedule.model.CourseEntity
import com.xiaomanjun.sleepdownschedule.model.PeriodEntity
import com.xiaomanjun.sleepdownschedule.model.WeekParity
import java.time.LocalDate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import com.xiaomanjun.sleepdownschedule.model.*
import com.xiaomanjun.sleepdownschedule.domain.schedule.*
import org.junit.Assert.assertTrue
import org.junit.Test

class DayCourseStatusTest {
    @Test fun dayAndWeekCardsResolveTheActualOccurrenceDateInsteadOfReusingTodaysProjection() {
        val date = LocalDate.of(2026, 10, 29)
        val config = defaultConfig().copy(termStartDate = "2026-10-26", totalWeeks = 2,
            activeTimetableComboId = 1)
        val base = listOf(PeriodEntity(1, "09:00", "09:45"))
        val catalog = TimetableCatalog(
            schemes = listOf(PeriodSchemeEntity(11, 0, "平时"), PeriodSchemeEntity(12, 0, "当天")),
            times = listOf(PeriodSchemeTimeEntity(11, 1, "09:00", "09:45"),
                PeriodSchemeTimeEntity(12, 1, "08:20", "09:05")),
            combos = listOf(TimetableComboEntity(id = 1, name = "日期安排", baseSchemeId = 11)),
            rules = listOf(TimetableDateRuleEntity(comboId = 1, schemeId = 12,
                startDate = date.toString(), endDate = date.toString(), position = 0)))
        val source = course.copy(weekday = 4, periods = listOf(1), weeks = listOf(1, 2))
        val state = AppState(config = config, periods = base, timetableCatalog = catalog,
            courses = listOf(projectCourseArrangement(source, config, base, catalog, date)))
        for ((day, expected) in listOf(date to "08:20", date.plusWeeks(1) to "09:00")) {
            val card = dayDisplayCourses(state, day).single()
            val week = weekCourseBuckets(state.courses, if (day == date) 1 else 2, config, day, catalog, base)
                .byWeekday.getValue(4).single()
            assertEquals(expected, courseStartTime(card, base).toString())
            assertEquals(courseTimeSegments(week, base), courseTimeSegments(card, base))
        }
    }
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
