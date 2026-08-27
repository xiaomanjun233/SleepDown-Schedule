package com.xiaomanjun.sleepdownschedule

import com.xiaomanjun.sleepdownschedule.glass.ui.*
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

    @Test
    fun fourSampledWallpaperColorsStillFlowThroughTheStableAssignmentAlgorithm() {
        val courses = (1L..12L).map { course(it, "课程$it") }
        val sampled = listOf(0xFF77BDF2L, 0xFFF09AB6L, 0xFF8DD3A8L, 0xFFFFD166L)

        val assignments = buildCourseCardColorAssignments(courses, sampled)

        assertEquals(courses.size, assignments.size)
        assertEquals(assignments, buildCourseCardColorAssignments(courses.reversed(), sampled))
        assertTrue(assignments.values.distinct().size > sampled.size)
    }

    @Test
    fun tonalFamilyUsesAnObviousLightToDarkRange() {
        val courses = (1L..10L).map { course(it, "课程$it") }
        val assignments = buildCourseCardColorAssignments(
            courses = courses,
            representativeColors = listOf(0xFF86B7E8L),
            tonalFamily = true
        )
        val values = assignments.values.map { color ->
            maxOf(
                (color shr 16) and 0xFF,
                (color shr 8) and 0xFF,
                color and 0xFF
            ) / 255f
        }

        assertTrue(
            "tonal cards should include visibly light and dark members",
            values.maxOrNull()!! - values.minOrNull()!! >= 0.28f
        )
    }

    @Test
    fun gradientFamilyAlwaysAssignsEveryCourseInATwelveCardSchedule() {
        val courses = listOf(
            "时间管理与拖延症", "高等数学", "大学英语", "大学物理", "数据结构", "线性代数",
            "马克思主义原理", "体育", "软件工程", "数据库", "计算机网络", "操作系统"
        ).mapIndexed { index, name -> course(index.toLong() + 1L, name) }

        val assignments = buildCourseCardColorAssignments(
            courses = courses,
            representativeColors = listOf(0xFF86B7E8L),
            tonalFamily = true
        )

        assertEquals(courses.map(::courseCardColorKey).toSet(), assignments.keys)
        assertEquals(assignments, buildCourseCardColorAssignments(courses.reversed(), listOf(0xFF86B7E8L), true))
    }

    @Test
    fun encodedCustomPaletteIsBoundedAndRoundTrips() {
        val colors = listOf(0xFF112233L, 0xFF445566L, 0xFF112233L, 0xFF778899L)
        assertEquals(colors.distinct(), decodeCourseCardPalette(encodeCourseCardPalette(colors)))
    }

}
