package com.xiaomanjun.sleepdownschedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CourseCardColorAssignmentTest {
    private fun course(id: Long, name: String) = CourseEntity(
        id = id,
        name = name,
        teacher = null,
        location = null,
        weekday = 1,
        periods = listOf(1),
        weeks = listOf(1),
        weekParity = WeekParity.ALL,
        note = null
    )

    @Test
    fun similarWallpaperColorsAreExpandedIntoSeparatedCourseColors() {
        val courses = (1L..10L).map { course(it, "课程$it") }
        val nearlyIdenticalWallpaperColors = listOf(
            0xFF7EA5C8L,
            0xFF80A7CAL,
            0xFF82A9CCL
        )

        val assignments = buildCourseCardColorAssignments(courses, nearlyIdenticalWallpaperColors)
        val colors = assignments.values.toList()

        assertEquals(courses.size, assignments.size)
        assertEquals(colors.size, colors.distinct().size)
        colors.forEachIndexed { index, color ->
            colors.drop(index + 1).forEach { other ->
                assertTrue(
                    "colors ${color.toString(16)} and ${other.toString(16)} are too similar",
                    courseCardPerceptualDistance(color, other) >= 0.055
                )
            }
        }
    }

    @Test
    fun assignmentIsStableForTheSameCourseSet() {
        val courses = listOf(course(1, "高等数学"), course(2, "大学英语"), course(3, "体育"))
        val palette = listOf(0xFF6688AAL, 0xFF7799BBL)

        assertEquals(
            buildCourseCardColorAssignments(courses, palette),
            buildCourseCardColorAssignments(courses.reversed(), palette)
        )
    }

    @Test
    fun spatiallyAdjacentCardsDoNotKeepAnIndistinguishableColorFamily() {
        val courses = listOf(
            course(1, "并排课程甲"),
            course(2, "并排课程乙"),
            course(3, "相邻课程丙").copy(periods = listOf(2))
        )
        val greenPalette = listOf(0xFF59775CL, 0xFF637B5CL, 0xFF71825FL)

        val assignments = buildCourseCardColorAssignments(courses, greenPalette)
        val first = assignments.getValue(courseCardColorKey(courses[0]))
        val second = assignments.getValue(courseCardColorKey(courses[1]))

        assertTrue(
            "adjacent cards need visible hue, saturation, or brightness contrast",
            courseCardAppearanceDistance(first, second) >= 0.20
        )
    }

}
