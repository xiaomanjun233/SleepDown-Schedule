package com.xiaomanjun.sleepdownschedule.feature.agent

import com.xiaomanjun.sleepdownschedule.model.CourseEntity
import com.xiaomanjun.sleepdownschedule.model.PeriodEntity
import com.xiaomanjun.sleepdownschedule.model.WeekParity
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DayAgentCourseClockTest {
    private val date = LocalDate.of(2026, 9, 16)
    private val periods = listOf(PeriodEntity(1, "08:00", "08:45"), PeriodEntity(2, "08:55", "09:40"))
    private val course = CourseEntity(1, "课程", null, null, 3, listOf(1, 2), listOf(1),
        WeekParity.ALL, null)
    private val slot = AgentCourseSlot(course, date, LocalTime.of(8, 0), LocalTime.of(9, 40))

    @Test fun assistantMovesThroughEachLessonAndBreak() {
        val beforeBreak = agentLessonFocus(listOf(slot), periods, date.atTime(8, 40))!!
        assertEquals(AgentLessonPhase.IN_CLASS, beforeBreak.phase)
        assertEquals(date.atTime(8, 45), beforeBreak.transitionAt)
        assertEquals(300L, beforeBreak.secondsRemainingAt(date.atTime(8, 40)))
        assertFalse(beforeBreak.finalLesson)

        val breakFocus = agentLessonFocus(listOf(slot), periods, date.atTime(8, 50))!!
        assertEquals(AgentLessonPhase.BREAK, breakFocus.phase)
        assertEquals(date.atTime(8, 55), breakFocus.transitionAt)

        val lastLesson = agentLessonFocus(listOf(slot), periods, date.atTime(8, 55))!!
        assertEquals(AgentLessonPhase.IN_CLASS, lastLesson.phase)
        assertEquals(date.atTime(9, 40), lastLesson.transitionAt)
        assertTrue(lastLesson.finalLesson)
        assertEquals("09:59", agentCountdownText(599))
        assertEquals("00:01", agentCountdownText(1))
    }

    @Test fun courseBellTimesOverrideTheDefaultSchedule() {
        val adjusted = course.copy(customStartTime = "10:10", customEndTime = "11:45",
            customPeriodTimes = "1,10:10-10:50;2,11:05-11:45")
        val customSlot = slot.copy(course = adjusted, start = LocalTime.of(10, 10), end = LocalTime.of(11, 45))
        val focus = agentLessonFocus(listOf(customSlot), periods, date.atTime(10, 55))!!
        assertEquals(AgentLessonPhase.BREAK, focus.phase)
        assertEquals(date.atTime(11, 5), focus.transitionAt)
        assertTrue(agentNextBoundaryWithinTenMinutes(listOf(customSlot), periods, date.atTime(10, 55)))
        assertFalse(agentNextBoundaryWithinTenMinutes(listOf(customSlot), periods, date.atTime(9, 0)))
    }

    @Test fun aMissingTeachingPeriodStartsANewSessionInsteadOfAnAllDayBreak() {
        val fullPeriods = periods + PeriodEntity(3, "10:00", "10:45")
        val split = course.copy(periods = listOf(1, 3))
        val splitSlot = slot.copy(course = split, end = LocalTime.of(10, 45))
        val focus = agentLessonFocus(listOf(splitSlot), fullPeriods, date.atTime(8, 50))!!
        assertEquals(AgentLessonPhase.BEFORE, focus.phase)
        assertEquals(date.atTime(10, 0), focus.transitionAt)
    }

    @Test fun anEarlierCourseWinsOverALaterBreakTransition() {
        val longBreak = course.copy(customStartTime = "08:00", customEndTime = "10:15",
            customPeriodTimes = "1,08:00-08:45;2,09:30-10:15")
        val upcoming = course.copy(id = 2, periods = listOf(2), customStartTime = "09:00",
            customEndTime = "09:45")
        val focus = agentLessonFocus(listOf(
            slot.copy(course = longBreak, end = LocalTime.of(10, 15)),
            slot.copy(course = upcoming, start = LocalTime.of(9, 0), end = LocalTime.of(9, 45))
        ), periods, date.atTime(8, 50))!!
        assertEquals(2L, focus.slot.course.id)
        assertEquals(AgentLessonPhase.BEFORE, focus.phase)
    }
}
