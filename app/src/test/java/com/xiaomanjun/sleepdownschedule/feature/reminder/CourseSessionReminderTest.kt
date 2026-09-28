package com.xiaomanjun.sleepdownschedule.feature.reminder

import com.xiaomanjun.sleepdownschedule.domain.schedule.courseReminderSessions
import com.xiaomanjun.sleepdownschedule.domain.schedule.courseTimeLabel
import com.xiaomanjun.sleepdownschedule.model.*
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class CourseSessionReminderTest {
    private val date = LocalDate.of(2026, 9, 10)
    private val zone = ZoneId.of("Asia/Shanghai")
    private val periods = listOf(
        PeriodEntity(1, "08:00", "08:45"), PeriodEntity(2, "08:55", "09:40"),
        PeriodEntity(3, "10:00", "10:45"), PeriodEntity(4, "10:55", "11:40"),
        PeriodEntity(5, "14:00", "14:45"), PeriodEntity(6, "14:55", "15:40")
    )
    private val splitCourse = CourseEntity(
        id = 1, name = "A", teacher = null, location = null, weekday = 4,
        periods = listOf(1, 2, 5, 6), weeks = listOf(1), weekParity = WeekParity.ALL, note = null
    )
    private fun epoch(hour: Int, minute: Int) = date.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()
    private fun payload(course: CourseEntity): LiveUpdatePayload {
        val timeline = NotificationScheduler.courseTimeline(date, course, periods, zone)
        return LiveUpdatePayload(
            name = course.name, timeText = courseTimeLabel(course, periods), location = "",
            showActions = false, muteKey = "test", muteUntil = timeline.last().endAtMillis.toString(),
            chipTextMode = LiveUpdateChipTextMode.COUNTDOWN, segments = timeline,
            duringClassEnabled = true, breakStatusEnabled = false,
            expiresAtMillis = timeline.last().endAtMillis
        )
    }

    @Test fun discontinuousCourseRemindsAndCountsEachSessionSeparately() {
        val sessions = courseReminderSessions(splitCourse, periods)
        assertEquals(listOf(listOf(1, 2), listOf(5, 6)), sessions.map { it.periods })
        assertEquals(listOf("08:00 - 09:40", "14:00 - 15:40"), sessions.map { courseTimeLabel(it, periods) })
        assertTrue(sessions.all { it.id == splitCourse.id })
        assertEquals(listOf(1, 2, 5, 6), splitCourse.periods)
        val morning = payload(sessions.first())
        assertEquals(40, morning.statusAt(epoch(9, 0)).minutesToTransition)
        assertTrue(morning.shouldStop(epoch(9, 40)))
    }

    @Test fun middleCourseWinsAndLaterSessionResumesWithoutOldBoundaryCancellation() {
        val middle = splitCourse.copy(id = 2, name = "B", periods = listOf(3, 4))
        val payloads = (courseReminderSessions(splitCourse, periods) + middle).map(::payload)
        assertEquals("B", NotificationScheduler.selectImmediateCoursePayload(payloads, epoch(10, 20), 10)?.name)
        assertNull(NotificationScheduler.selectImmediateCoursePayload(payloads, epoch(12, 0), 10))
        val afternoon = NotificationScheduler.selectImmediateCoursePayload(payloads, epoch(14, 20), 10)!!
        assertEquals("A", afternoon.name)
        assertEquals(epoch(14, 0), afternoon.startAtMillis())
        assertEquals(80, afternoon.statusAt(epoch(14, 20)).minutesToTransition)
    }

    @Test fun ongoingClassHasPriorityOverAnotherCoursesLeadTime() {
        val active = payload(splitCourse.copy(periods = listOf(1, 2)))
        val upcoming = payload(splitCourse.copy(name = "B", periods = listOf(3, 4)))
        assertSame(active, NotificationScheduler.selectImmediateCoursePayload(listOf(upcoming, active), epoch(9, 30), 45))
    }

    @Test fun classStartBoundaryCanCreateReminderWithoutPreClassPost() {
        val active = payload(splitCourse.copy(periods = listOf(1, 2)))
        val start = epoch(8, 0)
        assertTrue(start in active.refreshBoundaries())
        assertSame(active, NotificationScheduler.selectImmediateCoursePayload(listOf(active), start, 10))
        assertEquals(LiveUpdatePhase.IN_CLASS, active.statusAt(start).phase)
    }

    @Test fun customTimeRemainsOneExactSessionAndAffectsAlarmRescheduling() {
        val original = splitCourse.copy(customStartTime = "18:00", customEndTime = "19:00")
        val edited = original.copy(customEndTime = "20:00")
        assertEquals(listOf(original), courseReminderSessions(original, periods))
        assertNotEquals(
            NotificationScheduler.scheduleSignature(listOf(original), defaultConfig(), periods, date),
            NotificationScheduler.scheduleSignature(listOf(edited), defaultConfig(), periods, date)
        )
    }

    @Test fun customBellTimesKeepTheLiveActivityInBreakWithTheAssistant() {
        val course = splitCourse.copy(periods = listOf(1, 2), customStartTime = "10:10",
            customEndTime = "11:45", customPeriodTimes = "1,10:10-10:50;2,11:05-11:45")
        val timeline = NotificationScheduler.courseTimeline(date, course, periods, zone)
        assertEquals(2, timeline.size)
        assertEquals(epoch(10, 50), timeline.first().endAtMillis)
        assertEquals(epoch(11, 5), timeline.last().startAtMillis)
    }
}
