package com.xiaomanjun.sleepdownschedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CourseConflictResolutionTest {
    @Test
    fun `splits discontinuous periods into separate visual segments`() {
        val groups = buildWeekConflictGroups(
            courses = listOf(course(1, periods = listOf(1, 2, 4))),
            periodIndexes = (1..5).toList()
        )

        assertEquals(2, groups.size)
        assertEquals(listOf(1, 2), groups[0].segments.single().periods)
        assertEquals(listOf(4), groups[1].segments.single().periods)
    }

    @Test
    fun `groups partial and transitive overlaps without merging gaps`() {
        val groups = buildWeekConflictGroups(
            courses = listOf(
                course(1, periods = listOf(1, 2)),
                course(2, periods = listOf(2, 3)),
                course(3, periods = listOf(3, 4)),
                course(4, periods = listOf(6))
            ),
            periodIndexes = (1..6).toList()
        )

        assertEquals(2, groups.size)
        assertEquals(setOf(1L, 2L, 3L), groups[0].courses.map { it.id }.toSet())
        assertTrue(groups[0].hasConflict)
        assertFalse(groups[1].hasConflict)
    }

    @Test
    fun `reports only edited weeks that actually conflict`() {
        val original = course(1, periods = listOf(1), weeks = listOf(1, 2, 3))
        val edited = original.copy(periods = listOf(2))
        val conflicts = listOf(
            course(2, periods = listOf(2), weeks = listOf(2)),
            course(3, periods = listOf(3), weeks = listOf(3))
        )

        assertEquals(listOf(2), conflictWeeksForEditedCourse(original, edited, conflicts))
    }

    @Test
    fun `does not report a conflict that already existed before this edit`() {
        val original = course(1, periods = listOf(2), weeks = listOf(1, 2))
        val edited = original.copy(weeks = listOf(1, 2, 3))
        val existingConflict = course(2, periods = listOf(2), weeks = listOf(2))

        assertEquals(
            emptyList<Int>(),
            conflictWeeksForEditedCourse(original, edited, listOf(existingConflict))
        )
    }

    @Test
    fun `changing only periods reports a new conflict for the selected week`() {
        val original = course(1, periods = listOf(1), weeks = (1..16).toList())
        val edited = original.copy(periods = listOf(2))
        val blocker = course(2, periods = listOf(2), weeks = listOf(7))

        assertEquals(
            listOf(7),
            conflictWeeksForSingleWeekEdit(original, edited, 7, listOf(blocker))
        )
    }

    @Test
    fun `reports a newly added conflicting course even when the week already had another conflict`() {
        val original = course(1, periods = listOf(2), weeks = listOf(1, 2))
        val edited = original.copy(periods = listOf(2, 3))
        val conflicts = listOf(
            course(2, periods = listOf(2), weeks = listOf(2)),
            course(3, periods = listOf(3), weeks = listOf(2))
        )

        assertEquals(listOf(2), conflictWeeksForEditedCourse(original, edited, conflicts))
    }

    @Test
    fun `nearest move prefers same day adjacent free periods`() {
        val selected = course(1, periods = listOf(2, 3), weeks = listOf(1))
        val blockers = listOf(
            course(2, periods = listOf(2, 3), weeks = listOf(1))
        )

        val moved = nearestAvailableCourseMove(
            course = selected,
            week = 1,
            courses = blockers,
            periodIndexes = (1..6).toList(),
            weekdayCount = 5
        )

        assertEquals(1, moved?.weekday)
        assertEquals(listOf(4, 5), moved?.periods)
    }

    @Test
    fun `conflict predicate requires same schedule day week and period`() {
        val first = course(1, periods = listOf(2, 3), weeks = listOf(1))
        assertTrue(first.conflictsWith(course(2, periods = listOf(3, 4), weeks = listOf(1)), 1))
        assertFalse(first.conflictsWith(course(3, periods = listOf(3, 4), weeks = listOf(2)), 1))
        assertFalse(first.conflictsWith(course(4, periods = listOf(4), weeks = listOf(1)), 1))
    }

    @Test
    fun `new course conflict check reports overlapping weeks before insertion`() {
        val existing = course(8, periods = listOf(2), weeks = listOf(1, 3))
        val added = course(0, periods = listOf(2), weeks = listOf(1, 2, 3))

        assertEquals(
            listOf(1, 3),
            conflictWeeksForAddedCourses(listOf(added), listOf(existing))
        )
    }

    @Test
    fun `custom time conflicts use exact clock overlap instead of anchor periods`() {
        val definitions = listOf(
            PeriodEntity(1, "08:00", "08:45"),
            PeriodEntity(2, "08:55", "09:40")
        )
        val existing = course(8, periods = listOf(1), weeks = listOf(1))
        val inBreak = course(0, periods = listOf(1), weeks = listOf(1)).copy(
            customStartTime = "08:46",
            customEndTime = "08:54"
        )
        val overlapping = inBreak.copy(customStartTime = "08:30", customEndTime = "09:00")

        assertEquals(
            emptyList<Int>(),
            conflictWeeksForAddedCourses(listOf(inBreak), listOf(existing), definitions)
        )
        assertEquals(
            listOf(1),
            conflictWeeksForAddedCourses(listOf(overlapping), listOf(existing), definitions)
        )

        val groups = buildWeekConflictGroups(
            courses = listOf(existing, inBreak),
            periodIndexes = definitions.map(PeriodEntity::periodIndex),
            periodDefinitions = definitions
        )
        assertEquals(2, groups.size)
        assertFalse(groups.any(WeekConflictGroup::hasConflict))
    }

    private fun course(
        id: Long,
        periods: List<Int>,
        weeks: List<Int> = listOf(1)
    ) = CourseEntity(
        id = id,
        name = "C$id",
        teacher = null,
        location = null,
        weekday = 1,
        periods = periods,
        weeks = weeks,
        weekParity = WeekParity.ALL,
        note = null,
        scheduleId = 1
    )
}
