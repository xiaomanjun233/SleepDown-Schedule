package com.xiaomanjun.sleepdownschedule.feature.importing.shiguang

import com.xiaomanjun.sleepdownschedule.PeriodEntity
import com.xiaomanjun.sleepdownschedule.defaultConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ShiguangImportSessionCustomTimeTest {
    private val basePeriods = listOf(
        PeriodEntity(1, "08:20", "09:00"),
        PeriodEntity(2, "09:15", "09:55"),
        PeriodEntity(3, "10:20", "11:00"),
        PeriodEntity(4, "11:15", "11:55")
    )

    @Test
    fun explicitCourseTimeSurvivesWithoutCustomFlagAndKeepsSectionAnchors() {
        val session = ShiguangImportSession()
        session.begin(defaultConfig(), basePeriods, allowImportedBellTimes = true)
        session.stageTimeSlots("""[
          {"number":1,"startTime":"08:20","endTime":"09:00"},
          {"number":2,"startTime":"09:15","endTime":"09:55"},
          {"number":3,"startTime":"10:20","endTime":"11:00"},
          {"number":4,"startTime":"11:15","endTime":"11:55"}
        ]""")
        session.stageCourses("""[{"name":"数控机床加工技术与实践","teacher":"","position":"实训中心","day":3,"startSection":3,"endSection":4,"weeks":[1,2,3],"customStartTime":"10:10","customEndTime":"11:45","customPeriodTimes":"3,10:10-10:50;4,11:05-11:45"}]""")

        val draft = session.complete()
        val course = draft.courses.single()

        assertEquals("10:20", draft.periods.first { it.periodIndex == 3 }.startTime)
        assertEquals(listOf(3, 4), course.periods)
        assertEquals(listOf(1, 2, 3), course.weeks)
        assertEquals("10:10", course.customStartTime)
        assertEquals("11:45", course.customEndTime)
        assertEquals("3,10:10-10:50;4,11:05-11:45", course.customPeriodTimes)
    }

    @Test
    fun incompleteExactRangeIsRejectedInsteadOfFallingBackToGlobalPeriods() {
        val session = ShiguangImportSession()
        session.begin(defaultConfig(), basePeriods)

        assertThrows(IllegalArgumentException::class.java) {
            session.stageCourses("""[{"name":"实验课","teacher":"","position":"","day":3,"startSection":3,"endSection":4,"weeks":[1],"customStartTime":"10:10"}]""")
        }
    }
}
