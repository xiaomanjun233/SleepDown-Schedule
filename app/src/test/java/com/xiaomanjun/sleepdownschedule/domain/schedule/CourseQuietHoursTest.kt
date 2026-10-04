package com.xiaomanjun.sleepdownschedule.domain.schedule

import com.xiaomanjun.sleepdownschedule.model.*
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class CourseQuietHoursTest {
    private val monday = LocalDate.of(2026, 10, 5)
    private val zone = ZoneId.of("Asia/Shanghai")
    private val periods = listOf(PeriodEntity(1, "08:00", "08:45"), PeriodEntity(2, "08:55", "09:40"),
        PeriodEntity(3, "14:00", "14:45"))
    private val config = defaultConfig().copy(termStartDate = monday.toString(), autoCurrentWeek = true,
        totalWeeks = 2, notificationsEnabled = false)
    private fun course(id: Long, indices: List<Int>) = CourseEntity(id, "课程", null, null, 1, indices,
        listOf(1, 2), WeekParity.ALL, null)
    private fun at(time: String, date: LocalDate = monday) = date.atTime(LocalTime.parse(time)).atZone(zone).toInstant().toEpochMilli()
    private fun windows(courses: List<CourseEntity>, settings: CourseQuietSettings = CourseQuietSettings(doNotDisturbEnabled = true)) =
        courseQuietWindows(AppState(courses = courses, config = config, periods = periods), settings, monday, zone)
            .filter { it.start < monday.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() }

    @Test fun adjacentCoursesStayQuietAcrossBreakWhileLunchSeparatesSessions() {
        val result = windows(listOf(course(1, listOf(1)), course(2, listOf(2)), course(3, listOf(3))))
        assertEquals(listOf(CourseQuietWindow(at("08:00"), at("09:40")), CourseQuietWindow(at("14:00"), at("14:45"))), result)
        assertTrue(result.first().contains(at("08:50")))
        assertFalse(result.first().contains(at("09:40")))
    }

    @Test fun advanceAndDelayApplyOnceToMergedCourseWindow() {
        val result = windows(listOf(course(1, listOf(1, 2))), CourseQuietSettings(soundEnabled = true,
            advanceMinutes = 1, delayMinutes = 1)).single()
        assertEquals(CourseQuietWindow(at("07:59"), at("09:41")), result)
    }

    @Test fun customBellSegmentsDriveQuietTimesInsteadOfScheduleDefaults() {
        val custom = course(1, listOf(1, 2)).copy(customStartTime = "10:00", customEndTime = "11:45",
            customPeriodTimes = "1,10:00-10:45;2,11:00-11:45")
        assertEquals(CourseQuietWindow(at("10:00"), at("11:45")), windows(listOf(custom)).single())
    }

    @Test fun separatedSegmentsDoNotMuteLongEmptyGapWithinOneCourse() {
        val custom = course(1, listOf(1, 2)).copy(customPeriodTimes = "1,10:00-10:45;2,14:00-14:45")
        assertEquals(2, windows(listOf(custom)).size)
    }

    @Test fun overlappingOffsetsMergeEvenWithNoBreakAllowance() {
        val custom = course(1, listOf(1, 2)).copy(customPeriodTimes = "1,10:00-10:45;2,11:15-12:00")
        val result = windows(listOf(custom), CourseQuietSettings(soundEnabled = true, advanceMinutes = 20,
            delayMinutes = 20, keepDuringBreakMinutes = 0))
        assertEquals(listOf(CourseQuietWindow(at("09:40"), at("12:20"))), result)
    }

    @Test fun offsetsDoNotInflateTheConfiguredBreakAllowance() {
        val custom = course(1, listOf(1, 2)).copy(customPeriodTimes = "1,10:00-10:45;2,11:07-12:00")
        assertEquals(2, windows(listOf(custom), CourseQuietSettings(soundEnabled = true,
            advanceMinutes = 1, delayMinutes = 1, keepDuringBreakMinutes = 20)).size)
    }

    @Test fun adjustedTeachingDateIsRespectedAndEndedTermHasNoWindows() {
        val adjusted = config.copy(scheduleAdjustmentsJson = encodeScheduleAdjustments(listOf(
            ScheduleAdjustment("2026-10-10", monday.toString(), "补课"))))
        val state = AppState(courses = listOf(course(1, listOf(1))), config = adjusted, periods = periods)
        val saturday = LocalDate.of(2026, 10, 10)
        val result = courseQuietWindows(state, CourseQuietSettings(doNotDisturbEnabled = true), saturday, zone)
        assertTrue(result.any { it.contains(at("08:30", saturday)) })
        assertTrue(courseQuietWindows(state, CourseQuietSettings(soundEnabled = true), LocalDate.of(2026, 11, 1), zone).isEmpty())
    }

    @Test fun disabledQuietSettingsProduceNoWindowsEvenWhenCoursesExist() {
        assertTrue(windows(listOf(course(1, listOf(1))), CourseQuietSettings()).isEmpty())
    }
}
