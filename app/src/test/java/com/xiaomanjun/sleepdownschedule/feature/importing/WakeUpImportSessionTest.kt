package com.xiaomanjun.sleepdownschedule.feature.importing

import com.xiaomanjun.sleepdownschedule.defaultConfig
import com.xiaomanjun.sleepdownschedule.defaultPeriods
import com.xiaomanjun.sleepdownschedule.feature.importing.shiguang.ShiguangImportSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class WakeUpImportSessionTest {
    @Test
    fun overlappingChainImportsEveryCourseAndKeepsTouchingLessonsSeparate() {
        val session = wakeUpSession()
        session.stageTimeSlots("""[
            {"number":1,"startTime":"08:00","endTime":"08:50"},
            {"number":2,"startTime":"08:40","endTime":"09:30"},
            {"number":3,"startTime":"09:20","endTime":"10:00"},
            {"number":4,"startTime":"10:00","endTime":"10:45"},
            {"number":5,"startTime":"14:00","endTime":"14:45"}
        ]""")
        session.stageCourses("""[
            {"name":"数学","teacher":"张老师","position":"A101","day":1,
             "startSection":1,"endSection":2,"weeks":[1,3,5],"remark":"保留备注"},
            {"name":"英语","teacher":"","position":"","day":2,
             "startSection":3,"endSection":4,"weeks":[2,4]},
            {"name":"实验","teacher":"","position":"","day":3,
             "startSection":5,"endSection":5,"weeks":[1,2]}
        ]""")
        val draft = session.complete()
        assertEquals(listOf("08:00", "10:00", "14:00"), draft.periods.map { it.startTime })
        assertEquals(listOf("10:00", "10:45", "14:45"), draft.periods.map { it.endTime })
        assertEquals(listOf(listOf(1), listOf(1, 2), listOf(3)), draft.courses.map { it.periods })
        assertEquals(listOf(1, 3, 5), draft.courses[0].weeks)
        assertEquals("张老师", draft.courses[0].teacher)
        assertEquals("A101", draft.courses[0].location)
        assertEquals("保留备注", draft.courses[0].note)
    }

    @Test
    fun unorderedNestedAndDuplicateIntervalsUseEarliestClockStart() {
        val session = wakeUpSession()
        session.stageTimeSlots("""[
            {"number":3,"startTime":"08:00","endTime":"09:30"},
            {"number":1,"startTime":"08:20","endTime":"08:40"},
            {"number":2,"startTime":"08:00","endTime":"09:30"}
        ]""")
        session.stageCourses(course(1, 3))
        val draft = session.complete()
        assertEquals("08:00", draft.periods.single().startTime)
        assertEquals("09:30", draft.periods.single().endTime)
        assertEquals(listOf(1), draft.courses.single().periods)
    }

    @Test
    fun customTimesRemainExactAndMissingSectionReferencesStillFail() {
        val session = wakeUpSession()
        session.stageTimeSlots(overlappingSlots)
        session.stageCourses("""[
            {"name":"自定义课","teacher":"","position":"","day":1,"weeks":[1],
             "isCustomTime":true,"customStartTime":"08:25","customEndTime":"08:55"}
        ]""")
        val customCourse = session.complete().courses.single()
        assertEquals("08:25", customCourse.customStartTime)
        assertEquals("08:55", customCourse.customEndTime)
        assertEquals(listOf(1), customCourse.periods)

        session.begin(defaultConfig(), defaultPeriods(), mergeOverlappingTimeSlots = true)
        session.stageTimeSlots(overlappingSlots)
        session.stageCourses(course(3, 3))
        assertThrows(IllegalArgumentException::class.java) { session.complete() }
    }

    @Test
    fun invalidTimesAndNonWakeUpOverlapsRemainValidationErrors() {
        val session = wakeUpSession()
        assertThrows(IllegalArgumentException::class.java) {
            session.stageTimeSlots("""[{"number":1,"startTime":"09:00","endTime":"08:00"}]""")
        }
        session.stageTimeSlots(overlappingSlots)
        // A new task must not inherit WakeUp's normalization policy or section mapping.
        session.begin(defaultConfig(), defaultPeriods())
        assertThrows(IllegalArgumentException::class.java) { session.stageTimeSlots(overlappingSlots) }
    }

    private fun wakeUpSession() = ShiguangImportSession().apply {
        begin(defaultConfig(), defaultPeriods(), mergeOverlappingTimeSlots = true)
    }

    private fun course(start: Int, end: Int) = """[
        {"name":"课程","teacher":"","position":"","day":1,
         "startSection":$start,"endSection":$end,"weeks":[1]}
    ]"""

    private val overlappingSlots = """[
        {"number":1,"startTime":"08:00","endTime":"08:50"},
        {"number":2,"startTime":"08:40","endTime":"09:30"}
    ]"""
}
