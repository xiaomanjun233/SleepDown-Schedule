package com.xiaomanjun.sleepdownschedule

import org.junit.Assert.assertEquals
import org.junit.Test

class CourseManagementTest {
    private fun course(id: Long, name: String, weekday: Int, start: String? = null) = CourseEntity(
        id = id,
        name = name,
        teacher = null,
        location = null,
        weekday = weekday,
        periods = listOf(if (weekday == 1) 2 else 1),
        weeks = listOf(1),
        weekParity = WeekParity.ALL,
        note = null,
        customStartTime = start,
        customEndTime = start?.let { "10:30" }
    )

    @Test
    fun groupsEveryArrangementOfTheSameCourseAndKeepsStableOrder() {
        val groups = buildManagedCourseGroups(
            listOf(
                course(3, "大学英语", 2),
                course(2, "高等数学", 3),
                course(1, " 高等数学 ", 1, "09:50")
            )
        )

        assertEquals(2, groups.size)
        val math = groups.single { it.key.contains("高等数学") }
        assertEquals(listOf(1L, 2L), math.courses.map(CourseEntity::id))
    }

    @Test
    fun detailActivityResolvesTheWholeGroupFromOneCourseId() {
        val courses = listOf(
            course(3, "大学英语", 2),
            course(2, "高等数学", 3),
            course(1, " 高等数学 ", 1, "09:50")
        )

        assertEquals(
            listOf(1L, 2L),
            managedCourseGroupForCourseId(courses, 2L)?.courses?.map(CourseEntity::id)
        )
    }
}
