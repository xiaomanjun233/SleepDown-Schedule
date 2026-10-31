package com.xiaomanjun.sleepdownschedule.feature.home.week

import com.xiaomanjun.sleepdownschedule.model.*
import com.xiaomanjun.sleepdownschedule.domain.schedule.timingPreviewState
import com.xiaomanjun.sleepdownschedule.feature.importing.CanonicalScheduleJson
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeekCourseBellTest {
    private val today = LocalDate.of(2026, 10, 29)
    private val periods = listOf(PeriodEntity(1, "09:00", "09:45"), PeriodEntity(2, "09:55", "10:40"), PeriodEntity(3, "10:50", "11:35"))
    private val config = defaultConfig().copy(termStartDate = "2026-10-26", totalWeeks = 2)
    private val imported = CourseEntity(
        id = 1,
        name = "实训",
        teacher = null,
        location = null,
        weekday = 3,
        periods = listOf(3, 4),
        weeks = listOf(1),
        weekParity = WeekParity.ALL,
        note = null,
        customStartTime = "10:10",
        customEndTime = "11:45",
        customPeriodTimes = "3,10:10-10:50;4,11:05-11:45"
    )

    private val exception = imported.copy(weekday = 4, periods = listOf(1), customStartTime = "08:20", customEndTime = "09:05", customPeriodTimes = null)
    private fun state(courses: List<CourseEntity> = listOf(exception)) = AppState(config = config, periods = periods, courses = courses)

    @Test fun reviewedJsonUsesTodaysCustomClockAndTheOriginalLessonNumber() {
        val draft = CanonicalScheduleJson.decode("""{
          "config":{"semesterStartDate":"2026-10-26","semesterTotalWeeks":2},
          "timeSlots":[{"number":1,"startTime":"09:00","endTime":"09:45"},{"number":2,"startTime":"09:55","endTime":"10:40"}],
          "courses":[{"name":"高等数学","teacher":"","position":"B201","day":4,"weeks":[1],
            "isCustomTime":true,"customStartTime":"08:20","customEndTime":"09:05",
            "sleepdown":{"groupId":"math-20261029-exception","mode":"CUSTOM","periods":[1],"startTime":"08:20","endTime":"09:05"}}]
        }""", config)
        val result = weekDayTimeline(draft.timingPreviewState(), today, LocalTime.of(8, 30))
        assertEquals("08:20", result.periods.first().startTime)
        assertEquals("09:05", result.periods.first().endTime)
        assertEquals(1, result.activePeriodIndex)
        assertTrue(result.exceptionalActivePeriod)
        assertEquals(2, result.periods.size)
    }

    @Test fun onlyTheExceptionalLessonChangesAndTheStoredBaselineIsUntouched() {
        val source = state()
        val result = weekDayTimeline(source, today, LocalTime.of(8, 30))
        assertEquals(periods.drop(1), result.periods.drop(1))
        assertEquals("09:00", source.periods.first().startTime)
        assertNull(weekDayTimeline(source, today, LocalTime.of(9, 10)).activePeriodIndex)
    }

    @Test fun tomorrowAndNextWeeksOrdinaryOccurrenceRestoreTheBaseline() {
        val source = state(listOf(exception, exception.copy(weeks = listOf(2), customStartTime = null, customEndTime = null)))
        for (date in listOf(today.plusDays(1), today.plusWeeks(1))) {
            val result = weekDayTimeline(source, date, LocalTime.of(9, 10))
            assertEquals(periods, result.periods)
            assertEquals(1, result.activePeriodIndex)
            assertFalse(result.exceptionalActivePeriod)
        }
    }

    @Test fun theNextOrdinaryLessonUsesTheBlueMarkerPolicy() {
        val english = exception.copy(id = 2, periods = listOf(2), customStartTime = null, customEndTime = null)
        val result = weekDayTimeline(state(listOf(exception, english)), today, LocalTime.of(10, 0))
        assertEquals(2, result.activePeriodIndex)
        assertFalse(result.exceptionalActivePeriod)
    }

    @Test fun importedLessonBreakDoesNotHighlightAnotherBell() {
        val split = exception.copy(periods = listOf(1, 2), customStartTime = "08:20", customEndTime = "10:00",
            customPeriodTimes = "1,08:20-09:05;2,09:15-10:00")
        assertNull(weekDayTimeline(state(listOf(split)), today, LocalTime.of(9, 10)).activePeriodIndex)
        val result = weekDayTimeline(state(listOf(split)), today, LocalTime.of(9, 30))
        assertEquals(2, result.activePeriodIndex)
        assertTrue(result.exceptionalActivePeriod)
    }

    @Test fun wholeRangesDoNotInventUnknownInternalLessonBoundaries() {
        val whole = exception.copy(periods = listOf(1, 2), customStartTime = "08:20", customEndTime = "10:30")
        val result = weekDayTimeline(state(listOf(whole)), today, LocalTime.of(8, 30))
        assertEquals("08:20", result.periods[0].startTime)
        assertEquals("09:45", result.periods[0].endTime)
        assertEquals("09:55", result.periods[1].startTime)
        assertEquals("10:30", result.periods[1].endTime)
    }

    @Test fun customIntentMatchingTheBaselineDoesNotTurnTheNumberYellow() {
        val matching = exception.copy(customStartTime = "09:00", customEndTime = "09:45")
        val result = weekDayTimeline(state(listOf(matching)), today, LocalTime.of(9, 10))
        assertEquals(periods, result.periods)
        assertFalse(result.exceptionalActivePeriod)
    }

    @Test fun dateRulesRemainDayScopedAndMarkOnlyAnExceptionalActiveNumber() {
        val catalog = TimetableCatalog(times = periods.map { PeriodSchemeTimeEntity(10, it.periodIndex, it.startTime, it.endTime) } +
            PeriodSchemeTimeEntity(11, 1, "08:20", "09:05"),
            combos = listOf(TimetableComboEntity(20, "日期安排", 10)),
            rules = listOf(TimetableDateRuleEntity(20, 0, 11, today.toString(), today.toString())))
        val source = state(emptyList()).copy(config = config.copy(activeTimetableComboId = 20), timetableCatalog = catalog)
        assertTrue(weekDayTimeline(source, today, LocalTime.of(8, 30)).exceptionalActivePeriod)
        assertEquals(periods, weekDayTimeline(source, today.plusDays(1), LocalTime.of(9, 10)).periods)
    }

    @Test fun competingArrangementsPreferTheCourseActuallyInProgress() {
        val later = exception.copy(id = 2, customStartTime = "11:00", customEndTime = "11:45")
        val source = state(listOf(exception, later))
        assertEquals("08:20", weekDayTimeline(source, today, LocalTime.of(8, 30)).periods.first().startTime)
        val result = weekDayTimeline(source, today, LocalTime.of(11, 10))
        assertEquals("11:00", result.periods.first().startTime)
        assertEquals(1, result.activePeriodIndex)
        assertTrue(result.exceptionalActivePeriod)
    }
}
