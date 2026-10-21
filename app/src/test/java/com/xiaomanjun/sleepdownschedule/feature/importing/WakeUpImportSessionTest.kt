package com.xiaomanjun.sleepdownschedule.feature.importing

import com.xiaomanjun.sleepdownschedule.defaultConfig
import com.xiaomanjun.sleepdownschedule.defaultPeriods
import com.xiaomanjun.sleepdownschedule.feature.importing.shiguang.ShiguangImportSession
import com.xiaomanjun.sleepdownschedule.domain.schedule.projectCourseArrangements
import com.xiaomanjun.sleepdownschedule.domain.schedule.courseTimeLabel
import com.xiaomanjun.sleepdownschedule.domain.schedule.validateResolvedPeriodTimes
import com.xiaomanjun.sleepdownschedule.model.PeriodSchemeTimeEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class WakeUpImportSessionTest {
    @Test fun visibleNodesMayStartAfterOneAndHaveGapsWithoutBlockingPreview() {
        val session = wakeUpSession()
        session.stageTimeSlots("""[
            {"number":3,"startTime":"10:00","endTime":"10:45"},
            {"number":4,"startTime":"10:55","endTime":"11:40"},
            {"number":7,"startTime":"14:00","endTime":"14:45"}
        ]""")
        session.stageCourses(course(3, 4))
        val draft = session.complete()
        assertEquals(listOf(1, 2, 3), draft.periods.map { it.periodIndex })
        org.junit.Assert.assertNull(validateResolvedPeriodTimes(draft.periods.map {
            PeriodSchemeTimeEntity(0, it.periodIndex, it.startTime, it.endTime)
        }))
        assertEquals(listOf(3, 4), draft.courses.single().periods)
        assertEquals("3,10:00-10:45;4,10:55-11:40", draft.courses.single().originalPeriodTimes)
        val displayed = projectCourseArrangements(draft.courses, draft.config, draft.periods).single()
        assertEquals(listOf(1, 2), displayed.periods)
        assertEquals("10:00 - 11:40", courseTimeLabel(displayed, draft.periods))
        val restored = ScheduleImportParser.parseStoredDraft(draftToPayload(draft).toString(), draft.config).getOrThrow()
        assertEquals(draft.courses, restored.courses)
    }

    @Test fun missingIntermediateNodeDoesNotInventItsBellOrLoseTheWholeCourse() {
        val session = wakeUpSession()
        session.stageTimeSlots("""[
            {"number":1,"startTime":"08:00","endTime":"08:45"},
            {"number":3,"startTime":"09:55","endTime":"10:40"}
        ]""")
        session.stageCourses(course(1, 3))
        val draft = session.complete()
        val imported = draft.courses.single()
        assertEquals(listOf(1, 2, 3), imported.periods)
        assertEquals("08:00", imported.customStartTime)
        assertEquals("10:40", imported.customEndTime)
        org.junit.Assert.assertNull(imported.customPeriodTimes)
        assertEquals("1,08:00-08:45;3,09:55-10:40", imported.originalPeriodTimes)
        assertEquals("08:00 - 10:40", courseTimeLabel(
            projectCourseArrangements(draft.courses, draft.config, draft.periods).single(), draft.periods))
    }

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
        assertEquals(listOf(listOf(1, 2), listOf(3, 4), listOf(5)), draft.courses.map { it.periods })
        val displayed = projectCourseArrangements(draft.courses, draft.config, draft.periods)
        assertEquals(listOf(listOf(1), listOf(1, 2), listOf(3)), displayed.map { it.periods })
        assertEquals("08:00 - 09:30", courseTimeLabel(displayed[0], draft.periods))
        assertEquals("09:20 - 10:45", courseTimeLabel(displayed[1], draft.periods))
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
        assertEquals(listOf(1, 2, 3), draft.courses.single().periods)
        assertEquals(listOf(1), projectCourseArrangements(draft.courses, draft.config, draft.periods).single().periods)
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
