package com.xiaomanjun.sleepdownschedule.domain.schedule

import com.xiaomanjun.sleepdownschedule.model.*
import com.xiaomanjun.sleepdownschedule.glass.ui.courseCardColorOverrideForMode

import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomCourseTimeTest {
    private val periods = listOf(
        PeriodEntity(1, "08:00", "08:45"),
        PeriodEntity(2, "08:55", "09:40"),
        PeriodEntity(3, "10:00", "10:45")
    )

    private fun course(
        customStart: String? = null,
        customEnd: String? = null,
        customColor: Long? = null
    ) = CourseEntity(
        name = "高等数学",
        teacher = null,
        location = null,
        weekday = 1,
        periods = listOf(1, 2),
        weeks = listOf(1),
        weekParity = WeekParity.ALL,
        note = null,
        customStartTime = customStart,
        customEndTime = customEnd,
        customColorArgb = customColor
    )

    @Test
    fun exactTimeOverridesPeriodTimesAndProducesFractionalWeekPlacement() {
        val course = course("08:27", "09:22")

        assertTrue(course.hasCustomTime())
        assertFalse(courseAllowsWeekPeriodDrag(course))
        assertEquals(LocalTime.of(8, 27), courseStartTime(course, periods))
        assertEquals(LocalTime.of(9, 22), courseEndTime(course, periods))
        assertEquals("08:27 - 09:22", courseTimeLabel(course, periods))

        val placement = exactTimeWeekPlacement(course, periods)!!
        assertEquals(27f / 45f, placement.topRows, 0.001f)
        assertEquals(1f, placement.heightRows, 0.001f)
    }

    @Test
    fun exactTimeAnchorsEveryTouchedPeriodAndUsesNearestPeriodInsideBreak() {
        assertEquals(
            listOf(1, 2),
            courseAnchorPeriodsForTimeRange(LocalTime.of(8, 30), LocalTime.of(9, 10), periods)
        )
        assertEquals(
            listOf(3),
            courseAnchorPeriodsForTimeRange(LocalTime.of(9, 48), LocalTime.of(9, 52), periods)
        )
    }

    @Test
    fun veryShortExactTimeKeepsItsTrueContinuousHeight() {
        val placement = exactTimeWeekPlacement(course("08:27", "08:28"), periods)!!

        assertEquals(1f / 45f, placement.heightRows, 0.001f)
    }

    @Test
    fun invalidOrIncompleteExactTimeFallsBackToPeriods() {
        val course = course("09:00", "08:30")

        assertFalse(course.hasCustomTime())
        assertNull(course.customTimeRangeOrNull())
        assertEquals(LocalTime.of(8, 0), courseStartTime(course, periods))
        assertEquals(LocalTime.of(9, 40), courseEndTime(course, periods))
    }

    @Test
    fun courseColorOverrideIsAvailableOnlyInColorfulMode() {
        val custom = 0xFF6688AAL
        val course = course(customColor = custom)

        assertEquals(
            custom,
            courseCardColorOverrideForMode(
                defaultConfig().copy(courseCardColorMode = CourseCardColorMode.COLORFUL),
                course
            )
        )
        assertNull(
            courseCardColorOverrideForMode(
                defaultConfig().copy(courseCardColorMode = CourseCardColorMode.GRADIENT),
                course
            )
        )
    }
}
