package com.xiaomanjun.sleepdownschedule.feature.course.management

import com.xiaomanjun.sleepdownschedule.*
import com.xiaomanjun.sleepdownschedule.domain.schedule.captureOriginalPeriodTimes
import com.xiaomanjun.sleepdownschedule.domain.schedule.projectCourseArrangement
import com.xiaomanjun.sleepdownschedule.domain.schedule.originalArrangement

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CourseManagementTest {
    @Test fun addingArrangementFromSavedOrProjectedCourseCapturesItsOwnClock() {
        val config = ScheduleConfigEntity(totalWeeks = 20, currentWeek = 1, notificationLeadMinutes = 10)
        val periods = listOf(PeriodEntity(1, "08:00", "08:45"), PeriodEntity(2, "09:00", "09:45"))
        val source = course(12, "原课程", 1).copy(originalPeriodTimes = "2,09:00-09:45")
        for (template in listOf(source, projectCourseArrangement(source, config, periods))) {
            val added = newManagedCourseArrangement(template, config, periods)
            assertNull(added.originalPeriodTimes)
            assertNull(added.arrangementProjection)
            val captured = captureOriginalPeriodTimes(added, periods)
            val displayed = projectCourseArrangement(captured, config, periods)
            assertEquals(listOf(1), displayed.periods)
            assertEquals("1,08:00-08:45", displayed.originalArrangement().originalPeriodTimes)
            assertEquals((1..20).toList(), added.weeks)
            assertEquals(0L, added.id)
            assertEquals("2,09:00-09:45", source.originalPeriodTimes)
        }
    }

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
