package com.xiaomanjun.sleepdownschedule.feature.course.editor

import com.xiaomanjun.sleepdownschedule.CourseEntity
import com.xiaomanjun.sleepdownschedule.WeekParity
import org.junit.Assert.*
import org.junit.Test

class CourseEditorCopyDraftTest {
    private val source = CourseEntity(
        id = 0, name = "物理", teacher = "教师", location = "教室", weekday = 3,
        periods = listOf(2, 3), weeks = listOf(1, 3, 7), weekParity = WeekParity.ODD,
        note = "备注", customStartTime = "08:10", customEndTime = "09:20",
        customColorArgb = 0xff112233, scheduleId = 5
    )

    @Test fun copyKeepsContentButRequiresNewWeekdayAndTime() {
        val draft = courseEditorCopyDraft(source, (1..12).toList(), 20)
        assertEquals(source.name, draft.name)
        assertEquals(source.teacher, draft.teacher)
        assertEquals(source.location, draft.location)
        assertEquals(source.note, draft.note)
        assertEquals(source.customColorArgb, draft.customColorArgb)
        assertEquals(setOf(1, 3, 7), draft.weeks)
        assertTrue(draft.weekdays.isEmpty())
        assertTrue((1..12).none { it in draft.periodStart..draft.periodEnd })
        assertNull(draft.customStartTime)
        assertNull(draft.customEndTime)
    }

    @Test fun singleWeekDraftCannotExpandBackToWholeTerm() {
        val draft = courseEditorCopyDraft(source.copy(weeks = listOf(3), weekParity = WeekParity.ALL), (1..12).toList(), 20)
        assertEquals(setOf(3), draft.weeks)
        assertEquals(WeekParity.ALL, draft.parity)
        assertTrue(buildCourseEditorGroups(null, listOf(source.copy(id = 42))).single().courses.isEmpty())
    }
}
