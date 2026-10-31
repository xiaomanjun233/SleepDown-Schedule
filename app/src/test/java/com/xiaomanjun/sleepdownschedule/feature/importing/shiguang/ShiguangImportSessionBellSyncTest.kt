package com.xiaomanjun.sleepdownschedule.feature.importing.shiguang

import com.xiaomanjun.sleepdownschedule.PeriodEntity
import com.xiaomanjun.sleepdownschedule.defaultConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Upstream shapes that used to abort the whole import or lose the campus clock. */
class ShiguangImportSessionBellSyncTest {
    private val basePeriods = listOf(
        PeriodEntity(1, "08:20", "09:00"),
        PeriodEntity(2, "09:15", "09:55"),
        PeriodEntity(3, "10:20", "11:00"),
        PeriodEntity(4, "11:15", "11:55")
    )

    @Test
    fun campusClockBecomesPerLessonSoTheRailCanFollowIt() {
        val session = ShiguangImportSession()
        session.begin(defaultConfig(), basePeriods, allowImportedBellTimes = true)
        session.stageCourses(
            """[{"name":"A楼课程","teacher":"","position":"","day":2,"startSection":3,"endSection":4,
                "weeks":[1,2],"isCustomTime":true,"customStartTime":"10:10","customEndTime":"11:45"}]"""
        )

        val course = session.complete().courses.single()

        assertEquals(listOf(3, 4), course.periods)
        // Only the reported edges are exact; the interior boundary keeps the target scheme's bell.
        assertEquals("3,10:10-11:00;4,11:15-11:45", course.customPeriodTimes)
        assertEquals("10:10", course.customStartTime)
        assertEquals("11:45", course.customEndTime)
        assertNull(course.timingReviewReason)
    }

    @Test
    fun singleSectionCampusClockStaysExact() {
        val session = ShiguangImportSession()
        session.begin(defaultConfig(), basePeriods, allowImportedBellTimes = true)
        session.stageCourses(
            """[{"name":"单节","teacher":"","position":"","day":2,"startSection":3,"endSection":3,
                "weeks":[1],"isCustomTime":true,"customStartTime":"10:10","customEndTime":"10:50"}]"""
        )

        val course = session.complete().courses.single()

        assertEquals("3,10:10-10:50", course.customPeriodTimes)
    }

    @Test
    fun unscheduledCourseIsKeptForReviewInsteadOfFailingTheImport() {
        val session = ShiguangImportSession()
        session.begin(defaultConfig(), basePeriods, allowImportedBellTimes = true)
        session.stageCourses(
            """[{"name":"未安排课程","teacher":"张老师","position":"待定","day":0,
                "startSection":0,"endSection":0,"weeks":[1,2,3]}]"""
        )

        val draft = session.complete()
        val course = draft.courses.single()

        assertEquals(listOf(1, 2, 3), course.weeks)
        assertEquals("张老师", course.teacher)
        assertTrue(course.periods.isEmpty())
        assertNotNull(course.timingReviewReason)
        assertTrue(draft.notices.any { it.contains("待安排") })
    }

    @Test
    fun slotsNumberedZeroDoNotEnterTheGrid() {
        val session = ShiguangImportSession()
        session.begin(defaultConfig(), basePeriods, allowImportedBellTimes = true)
        session.stageTimeSlots(
            """[{"number":0,"startTime":"08:00","endTime":"08:20"},
                {"number":1,"startTime":"08:20","endTime":"09:00"},
                {"number":2,"startTime":"09:15","endTime":"09:55"}]"""
        )
        session.stageCourses(
            """[{"name":"第一节","teacher":"","position":"","day":1,"startSection":1,"endSection":2,"weeks":[1]}]"""
        )

        val draft = session.complete()

        assertEquals(listOf(1, 2), draft.periods.map { it.periodIndex })
        assertEquals(listOf(1, 2), draft.courses.single().periods)
    }

    @Test
    fun weeksBeyondSemesterExpandTheTermInsteadOfRejectingTheImport() {
        val session = ShiguangImportSession()
        session.begin(defaultConfig(), basePeriods, allowImportedBellTimes = true)
        session.stageCourseConfig("""{"semesterTotalWeeks":16}""")
        session.stageCourses(
            """[{"name":"实验课","teacher":"","position":"","day":4,"startSection":3,"endSection":4,"weeks":[1,18]}]"""
        )

        val draft = session.complete()

        assertEquals(18, draft.config.totalWeeks)
        assertTrue(draft.notices.any { it.contains("总周数已扩展") })
    }

    @Test
    fun customLessonTimesFromTheAdapterAreNotOverwrittenByTheDerivation() {
        val session = ShiguangImportSession()
        session.begin(defaultConfig(), basePeriods, allowImportedBellTimes = true)
        session.stageCourses(
            """[{"name":"逐节","teacher":"","position":"","day":2,"startSection":3,"endSection":4,
                "weeks":[1],"customStartTime":"10:10","customEndTime":"11:45",
                "customPeriodTimes":"3,10:10-10:50;4,11:05-11:45"}]"""
        )

        val course = session.complete().courses.single()

        assertEquals("3,10:10-10:50;4,11:05-11:45", course.customPeriodTimes)
    }
}
