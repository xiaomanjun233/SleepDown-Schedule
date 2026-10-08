package com.xiaomanjun.sleepdownschedule.data.repository

import com.xiaomanjun.sleepdownschedule.CourseEntity
import com.xiaomanjun.sleepdownschedule.ImportDraft
import com.xiaomanjun.sleepdownschedule.PeriodEntity
import com.xiaomanjun.sleepdownschedule.WeekParity
import com.xiaomanjun.sleepdownschedule.defaultConfig
import com.xiaomanjun.sleepdownschedule.defaultPeriods
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AutoRefreshTimingPreservationTest {
    private val currentPeriods = listOf(
        PeriodEntity(1, "08:30", "09:15", 1),
        PeriodEntity(2, "09:25", "10:10", 1)
    )

    private fun course(period: Int) = CourseEntity(
        name = "课程", teacher = null, location = null, weekday = 1,
        periods = listOf(period), weeks = listOf(1), weekParity = WeekParity.ALL, note = null
    )

    @Test
    fun refreshKeepsUserTimesAndSchemesWhileUpdatingTerm() {
        val current = defaultConfig().copy(
            totalWeeks = 16, currentWeek = 6, termStartDate = "2025-09-08",
            autoCurrentWeek = false, classDurationMinutes = 50, breakDurationMinutes = 5
        )
        val fetched = ImportDraft(
            config = current.copy(totalWeeks = 20, termStartDate = null,
                autoCurrentWeek = true, classDurationMinutes = 45, breakDurationMinutes = 10),
            periods = defaultPeriods(),
            courses = listOf(course(1))
        )

        val result = preserveTimingForAutoRefresh(current, currentPeriods, fetched)

        assertEquals(currentPeriods, result.periods)
        assertEquals(20, result.config.totalWeeks)
        assertEquals(6, result.config.currentWeek)
        assertEquals("2025-09-08", result.config.termStartDate)
        assertEquals(false, result.config.autoCurrentWeek)
        assertEquals(50, result.config.classDurationMinutes)
        assertEquals(5, result.config.breakDurationMinutes)
    }

    @Test
    fun newSectionRetainsImportedSourceWithoutReplacingTheSelectedScheme() {
        val current = defaultConfig()
        val fetched = ImportDraft(current, defaultPeriods(), listOf(course(3)))

        val result = preserveTimingForAutoRefresh(current, currentPeriods, fetched)
        assertEquals(currentPeriods, result.periods)
        assertEquals(listOf(3), result.courses.single().periods)
        assertEquals("3,${fetched.periods[2].startTime}-${fetched.periods[2].endTime}", result.courses.single().originalPeriodTimes)
    }
}
