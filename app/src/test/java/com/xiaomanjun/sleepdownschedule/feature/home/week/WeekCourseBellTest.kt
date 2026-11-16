package com.xiaomanjun.sleepdownschedule.feature.home.week

import androidx.compose.ui.unit.dp
import com.xiaomanjun.sleepdownschedule.domain.course.buildWeekConflictGroups
import com.xiaomanjun.sleepdownschedule.domain.schedule.projectCourseArrangement
import com.xiaomanjun.sleepdownschedule.model.*
import com.xiaomanjun.sleepdownschedule.domain.schedule.timingPreviewState
import com.xiaomanjun.sleepdownschedule.domain.schedule.hasCustomTime
import com.xiaomanjun.sleepdownschedule.feature.importing.CanonicalScheduleJson
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeekCourseBellTest {
    @Test fun activeCourseBoundsUseTheSameClocksAsTheYellowRail() {
        val later = exception.copy(id = 22, customStartTime = "11:00", customEndTime = "11:45")
        val source = state(listOf(later))
        val rail = weekDayTimeline(source, today, LocalTime.of(11, 10))
        assertTrue(rail.exceptionalActivePeriod)
        assertEquals(1, rail.activePeriodIndex)

        val projected = projectCourseArrangement(later, config, periods, date = today)
        val segment = buildWeekConflictGroups(listOf(projected), periods.map(PeriodEntity::periodIndex), periods)
            .single().segments.single()
        val baselineGrid = weekTimeSlotGrid(emptyList(), periods.size, 100f, periods = periods)
        val todayGrid = baselineGrid.withLessonClocks(rail.periods)
        assertEquals(baselineGrid.rowTops, todayGrid.rowTops)
        assertEquals(12f to 88f, weekCardVerticalBounds(segment, periods, 100.dp, todayGrid, useRailClocks = true))
        assertTrue(weekCardVerticalBounds(segment, periods, 100.dp, baselineGrid).first > 200f)
    }

    @Test fun specialLessonClocksFollowTheirDayAndWeekWithoutReplacingOrdinaryCourses() {
        val slot = com.xiaomanjun.sleepdownschedule.domain.schedule.TimeSlotCourse(
            "special", "自习", "08:20", "09:05", weekdays = listOf(4), periodIndices = listOf(1),
            customPeriodTimes = "1,08:20-09:05", displayMode = com.xiaomanjun.sleepdownschedule.domain.schedule.TimeSlotDisplayMode.SMART,
            startWeek = 1, endWeek = 1)
        val source = state(emptyList()).copy(config = config.copy(timeSlotCoursesJson =
            com.xiaomanjun.sleepdownschedule.domain.schedule.encodeTimeSlotCourses(listOf(slot))))
        val active = weekDayTimeline(source, today, LocalTime.of(8, 30))
        assertEquals("08:20", active.periods.first().startTime)
        assertTrue(active.exceptionalActivePeriod)
        assertEquals(periods, weekDayTimeline(source, today.plusDays(1), LocalTime.of(8, 30)).periods)
        assertEquals(periods, weekDayTimeline(source, today.plusWeeks(1), LocalTime.of(8, 30)).periods)
        val ordinary = exception.copy(customStartTime = null, customEndTime = null)
        assertEquals(periods, weekDayTimeline(source.copy(courses = listOf(ordinary)), today, LocalTime.of(9, 10)).periods)
        val independent = source.copy(config = source.config.copy(timeSlotCoursesJson =
            com.xiaomanjun.sleepdownschedule.domain.schedule.encodeTimeSlotCourses(listOf(slot.copy(
                displayMode = com.xiaomanjun.sleepdownschedule.domain.schedule.TimeSlotDisplayMode.INDEPENDENT)))))
        assertEquals(periods, weekDayTimeline(independent, today, LocalTime.of(8, 30)).periods)
    }

    @Test fun importedCampusClockRemainsCustomThroughPreviewAndReplacesTheActualRail() {
        val session = com.xiaomanjun.sleepdownschedule.feature.importing.shiguang.ShiguangImportSession()
        session.begin(config, periods, allowImportedBellTimes = true)
        session.stageCourses("""[{"name":"异校区","teacher":"","position":"","day":4,"startSection":1,"endSection":2,
            "weeks":[1],"isCustomTime":true,"customStartTime":"08:20","customEndTime":"10:30"}]""")
        val draft = session.complete()
        val course = draft.courses.single()
        assertTrue(course.hasCustomTime())
        assertEquals(listOf(1, 2), course.periods)
        val rail = weekDayTimeline(draft.timingPreviewState(), today, LocalTime.of(8, 30))
        assertEquals("08:20", rail.periods.first().startTime)
        assertEquals("10:30", rail.periods[1].endTime)
        assertTrue(rail.exceptionalActivePeriod)
    }

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
        val beforeWindow = weekDayTimeline(source, today, LocalTime.of(8, 5))
        assertEquals(periods, beforeWindow.periods)
        assertNull(beforeWindow.activePeriodIndex)
        assertFalse(beforeWindow.exceptionalActivePeriod)

        val preClass = weekDayTimeline(source, today, LocalTime.of(8, 12))
        assertEquals("08:20", preClass.periods.first().startTime)
        assertEquals(1, preClass.activePeriodIndex)
        assertTrue(preClass.exceptionalActivePeriod)
        assertEquals(exception.id, preClass.activeCourseId)

        val result = weekDayTimeline(source, today, LocalTime.of(8, 30))
        assertEquals(periods.drop(1), result.periods.drop(1))
        assertEquals("09:00", source.periods.first().startTime)
        assertTrue(result.exceptionalActivePeriod)

        val restored = weekDayTimeline(source, today, LocalTime.of(9, 10))
        assertEquals(periods, restored.periods)
        assertEquals(1, restored.activePeriodIndex)
        assertFalse(restored.exceptionalActivePeriod)
        assertNull(restored.activeCourseId)
    }

    @Test fun preClassLeadWaitsUntilRunningNormalClassEndsThenRestoresAfterCustomClass() {
        val normalSecond = exception.copy(id = 10, periods = listOf(2), customStartTime = null, customEndTime = null)
        val earlyCustomThird = exception.copy(
            id = 11,
            periods = listOf(3),
            customStartTime = "10:45",
            customEndTime = "11:30",
        )
        val source = state(listOf(normalSecond, earlyCustomThird))
        // 10:35 is inside 10-min lead of 10:45, but normal period 2 (09:55-10:40) is still in class.
        val duringSecond = weekDayTimeline(source, today, LocalTime.of(10, 36))
        assertEquals(2, duringSecond.activePeriodIndex)
        assertFalse(duringSecond.exceptionalActivePeriod)
        assertEquals("10:50", duringSecond.periods[2].startTime)

        // At 10:40 when period 2 ends, period 3 immediately switches to custom clock and yellow marker.
        val afterSecond = weekDayTimeline(source, today, LocalTime.of(10, 40))
        assertEquals(3, afterSecond.activePeriodIndex)
        assertTrue(afterSecond.exceptionalActivePeriod)
        assertEquals("10:45", afterSecond.periods[2].startTime)
        assertEquals("11:30", afterSecond.periods[2].endTime)

        // After custom class ends at 11:30, left rail restores to normal period 3 bell 10:50-11:35.
        val ended = weekDayTimeline(source, today, LocalTime.of(11, 30))
        assertEquals(periods, ended.periods)
        assertFalse(ended.exceptionalActivePeriod)
    }

    @Test fun seasonalFullWeekDateRuleActsAsBlueWeekBaselineWhileCustomExceptionTurnsYellow() {
        val summer = listOf(
            PeriodEntity(1, "08:00", "08:45"),
            PeriodEntity(2, "08:55", "09:40"),
            PeriodEntity(3, "14:30", "15:15"),
        )
        val winter = listOf(
            PeriodSchemeTimeEntity(12, 1, "08:00", "08:45"),
            PeriodSchemeTimeEntity(12, 2, "08:55", "09:40"),
            PeriodSchemeTimeEntity(12, 3, "14:00", "14:45"),
        )
        val catalog = TimetableCatalog(
            schemes = listOf(PeriodSchemeEntity(11, 0, "夏令"), PeriodSchemeEntity(12, 0, "冬作息")),
            times = summer.map { PeriodSchemeTimeEntity(11, it.periodIndex, it.startTime, it.endTime) } + winter,
            combos = listOf(TimetableComboEntity(20, "日期作息", 11)),
            rules = listOf(TimetableDateRuleEntity(20, 0, 12, "2026-10-01", "2027-05-01")),
        )
        val ordinaryWinterCourse = CourseEntity(1, "冬令普通课", null, null, 4, listOf(3), listOf(1), WeekParity.ALL, null)
        val customWinterCourse = CourseEntity(
            2, "冬令异校区课", null, null, 4, listOf(2), listOf(1), WeekParity.ALL, null,
            customStartTime = "09:10", customEndTime = "09:55",
        )
        val source = AppState(
            config = config.copy(activeTimetableComboId = 20, notificationLeadMinutes = 10),
            periods = summer,
            courses = listOf(ordinaryWinterCourse, customWinterCourse),
            timetableCatalog = catalog,
        )
        val winterMorningBeforeCustom = weekDayTimeline(source, today, LocalTime.of(8, 10))
        assertEquals("14:00", winterMorningBeforeCustom.periods[2].startTime)
        assertEquals(1, winterMorningBeforeCustom.activePeriodIndex)
        assertFalse(winterMorningBeforeCustom.exceptionalActivePeriod)

        val winterAfternoonOrdinary = weekDayTimeline(source, today, LocalTime.of(14, 15))
        assertEquals(3, winterAfternoonOrdinary.activePeriodIndex)
        assertFalse(winterAfternoonOrdinary.exceptionalActivePeriod)

        val winterPreCustom = weekDayTimeline(source, today, LocalTime.of(9, 2))
        assertEquals(2, winterPreCustom.activePeriodIndex)
        assertTrue(winterPreCustom.exceptionalActivePeriod)
        assertEquals("09:10", winterPreCustom.periods[1].startTime)
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
