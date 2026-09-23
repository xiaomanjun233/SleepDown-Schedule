package com.xiaomanjun.sleepdownschedule.feature.home.week

import com.xiaomanjun.sleepdownschedule.CourseEntity
import com.xiaomanjun.sleepdownschedule.WeekParity
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeekCourseBellTest {
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

    @Test
    fun railUsesImportedBellOnlyDuringItsActualSection() {
        assertEquals(3, activeWeekCourseBell(listOf(imported), 3, LocalTime.of(10, 30))?.index)
        assertNull(activeWeekCourseBell(listOf(imported), 3, LocalTime.of(10, 55)))
        assertTrue(specialCourseCoversTime(listOf(imported), 3, LocalTime.of(10, 55)))
        assertEquals(4, activeWeekCourseBell(listOf(imported), 3, LocalTime.of(11, 30))?.index)
        assertNull(activeWeekCourseBell(listOf(imported), 2, LocalTime.of(11, 30)))
        assertFalse(specialCourseCoversTime(listOf(imported), 3, LocalTime.of(11, 50)))
    }
}
