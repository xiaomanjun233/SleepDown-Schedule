package com.xiaomanjun.sleepdownschedule.feature.course.editor

import com.xiaomanjun.sleepdownschedule.WeekParity
import com.xiaomanjun.sleepdownschedule.domain.schedule.courseTimeSegments
import com.xiaomanjun.sleepdownschedule.domain.schedule.encodeCoursePeriodTimes
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CourseLessonClockDraftTest {
    private val draft = CourseLessonClockDraft("08:30", "3", "45", "10", false, emptyList())

    @Test fun uniformLessonsPreserveClassAndBreakBoundaries() {
        val times = draft.times(listOf(3, 4, 5))
        assertEquals(listOf(3, 4, 5), times.map { it.index })
        assertEquals(listOf("08:30", "09:25", "10:20"), times.map { it.start.toString() })
        assertEquals(listOf("09:15", "10:10", "11:05"), times.map { it.end.toString() })
    }

    @Test fun individualDurationsAndExplicitStartsRemainIndependent() {
        val edited = draft.copy(count = "2", individually = true, lessons = listOf(
            CourseLessonClockRow("35", "5"), CourseLessonClockRow("50", "0", "10:00")
        ))
        val times = edited.times(listOf(3, 5))
        assertEquals(listOf(3, 5), times.map { it.index })
        assertEquals("09:05", times.first().end.toString())
        assertEquals("10:00", times.last().start.toString())
        assertEquals("10:50", times.last().end.toString())
    }

    @Test fun invalidCountOverlapsAndMidnightCannotSilentlyChangeTheClock() {
        listOf(
            draft.copy(count = "0"), draft.copy(count = "41"), draft.copy(classMinutes = "-1"),
            draft.copy(start = "23:50"), draft.copy(start = "25:00"),
            draft.copy(count = "2", individually = true, lessons = listOf(
                CourseLessonClockRow("45", "10"), CourseLessonClockRow("45", "0", "08:40")
            ))
        ).forEach { invalid ->
            assertThrows(IllegalArgumentException::class.java) { invalid.times(listOf(3, 4, 5)) }
        }
    }

    @Test fun newCourseSaveAndReopenRetainAllLessonTimesOutsideTheCurrentScheme() {
        val times = draft.times(listOf(3, 4, 5))
        val encoded = encodeCoursePeriodTimes(times)
        val course = CourseEditorDraft("课程", "", "", setOf(1), 3, 5,
            times.first().start.toString(), times.last().end.toString(), null, setOf(1, 2),
            WeekParity.ALL, "", encoded, customLessonTimesEdited = true)
            .toCourses(emptyList(), listOf(1, 2), false).single()
        assertEquals(listOf(3, 4, 5), course.periods)
        assertEquals(encoded, course.customPeriodTimes)
        assertEquals(times, courseTimeSegments(course, emptyList()))
        val reopened = initialCourseLessonClockDraft(LocalTime.NOON, course.periods, course.customPeriodTimes)
        assertEquals(times, reopened.times(course.periods))
    }

    @Test fun reopeningNonconsecutiveLessonsDoesNotInsertAnUnselectedPeriod() {
        val times = draft.copy(count = "2").times(listOf(3, 5))
        val reopened = initialCourseLessonClockDraft(LocalTime.NOON, listOf(3, 5), encodeCoursePeriodTimes(times))
        assertEquals(times, reopened.times(listOf(3, 5)))
        val partial = initialCourseLessonClockDraft(LocalTime.of(8, 30), listOf(3, 4, 5), "4,09:25-10:10")
        assertEquals("3", partial.count)
    }
}
