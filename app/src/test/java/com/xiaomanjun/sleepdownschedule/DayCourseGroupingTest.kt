package com.xiaomanjun.sleepdownschedule

import com.xiaomanjun.sleepdownschedule.feature.home.day.courseDayPart
import com.xiaomanjun.sleepdownschedule.feature.home.day.groupDayCourses
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DayCourseGroupingTest {
    private val config = defaultConfig().copy(
        morningPeriodCount = 4,
        noonPeriodCount = 2,
        afternoonPeriodCount = 4,
        eveningPeriodCount = 2
    )

    private fun course(periods: List<Int>) = CourseEntity(
        id = periods.firstOrNull()?.toLong() ?: 0L,
        name = "测试课程",
        teacher = null,
        location = null,
        weekday = 1,
        periods = periods,
        weeks = listOf(1),
        weekParity = WeekParity.ALL,
        note = null
    )

    @Test
    fun usesFirstCoursePeriodToChooseConfiguredDayPart() {
        assertEquals(PeriodDayPart.MORNING, courseDayPart(config, course(listOf(1, 2))))
        assertEquals(PeriodDayPart.NOON, courseDayPart(config, course(listOf(5, 6))))
        assertEquals(PeriodDayPart.AFTERNOON, courseDayPart(config, course(listOf(7, 8))))
        assertEquals(PeriodDayPart.EVENING, courseDayPart(config, course(listOf(11, 12))))
    }

    @Test
    fun emptyCourseHasNoDayPart() {
        assertNull(courseDayPart(config, course(emptyList())))
    }

    @Test
    fun previewRetainsAllFourDayPartsAndMultipleAfternoonCourses() {
        val courses = listOf(1, 5, 7, 9, 11).map { course(listOf(it, it + 1)) }

        val groups = groupDayCourses(config, courses)

        assertEquals(PeriodDayPart.entries.toList(), groups.map { it.first })
        assertEquals(courses, groups.flatMap { it.second })
        assertEquals(2, groups.single { it.first == PeriodDayPart.AFTERNOON }.second.size)
    }

    @Test
    fun previewKeepsCustomTimeCoursesWithoutPeriodAnchors() {
        val custom = course(emptyList()).copy(
            customStartTime = "14:00",
            customEndTime = "15:40"
        )
        val courses = listOf(course(listOf(1, 2)), custom, course(listOf(11, 12)))

        val groups = groupDayCourses(config, courses)

        assertEquals(listOf(custom), groups.single { it.first == null }.second)
        assertEquals(courses.toSet(), groups.flatMap { it.second }.toSet())
        assertEquals(courses.size, groups.sumOf { it.second.size })
    }

    @Test
    fun previewKeepsOrdinaryPeriodsOutsideConfiguredDayParts() {
        val courseBeyondConfig = course(listOf(13, 14))
        val courses = listOf(course(listOf(1, 2)), courseBeyondConfig, course(listOf(11, 12)))

        val groups = groupDayCourses(config, courses)

        assertEquals(listOf(courseBeyondConfig), groups.single { it.first == null }.second)
        assertEquals(courses.size, groups.sumOf { it.second.size })
    }

    @Test
    fun previewKeepsCoursesWhenNoDayPartIsConfigured() {
        val noParts = config.copy(
            morningPeriodCount = 0,
            noonPeriodCount = 0,
            afternoonPeriodCount = 0,
            eveningPeriodCount = 0
        )
        val courses = listOf(course(listOf(1, 2)), course(listOf(7, 8)))

        assertEquals(listOf(null to courses), groupDayCourses(noParts, courses))
    }

    @Test
    fun emptyDayDoesNotCreateSectionHeaders() {
        assertEquals(emptyList<Pair<PeriodDayPart?, List<CourseEntity>>>(), groupDayCourses(config, emptyList()))
    }
}
