package com.xiaomanjun.sleepdownschedule.feature.course.editor

import com.xiaomanjun.sleepdownschedule.*
import com.xiaomanjun.sleepdownschedule.domain.course.*

import org.junit.Assert.assertEquals
import org.junit.Test

class CourseEditorGroupingTest {
    private fun course(
        id: Long,
        weekday: Int,
        weeks: List<Int>,
        teacher: String = "林老师",
        periods: List<Int> = listOf(3, 4)
    ) = CourseEntity(
        id = id,
        name = "高等数学",
        teacher = teacher,
        location = "A101",
        weekday = weekday,
        periods = periods,
        weeks = weeks,
        weekParity = WeekParity.ALL,
        note = null,
        scheduleId = 1
    )

    @Test
    fun equivalentFragmentsOnSameWeekdayShareOneEditorPage() {
        val earlyWeeks = course(1, weekday = 1, weeks = listOf(1, 2, 3))
        val lateWeeks = course(2, weekday = 1, weeks = listOf(6, 7, 8))

        val groups = buildCourseEditorGroups(earlyWeeks, listOf(earlyWeeks, lateWeeks))

        assertEquals(1, groups.size)
        assertEquals(setOf(1, 2, 3, 6, 7, 8), groups.single().courses.flatMap(CourseEntity::weeks).toSet())
    }

    @Test
    fun meaningfulCourseDetailsStillCreateSeparatePages() {
        val base = course(1, weekday = 1, weeks = listOf(1, 2))
        val otherTeacher = course(2, weekday = 2, weeks = listOf(1, 2), teacher = "周老师")
        val otherPeriods = course(3, weekday = 3, weeks = listOf(1, 2), periods = listOf(5, 6))

        assertEquals(3, buildCourseEditorGroups(base, listOf(base, otherTeacher, otherPeriods)).size)
    }

    @Test
    fun groupConflictDetectionIgnoresConflictsThatAlreadyExisted() {
        val original = course(1, weekday = 2, weeks = listOf(1))
        val other = course(9, weekday = 2, weeks = listOf(1), teacher = "另一位老师")
        val unchanged = original.copy(name = "线性代数")

        assertEquals(
            emptyList<Int>(),
            conflictWeeksForEditedCourseGroup(listOf(original), listOf(unchanged), listOf(original, other))
        )
    }

    @Test
    fun groupConflictDetectionFindsNewWeekdayCollisions() {
        val original = course(1, weekday = 1, weeks = listOf(1, 2))
        val other = course(9, weekday = 4, weeks = listOf(2), teacher = "另一位老师")
        val moved = original.copy(weekday = 4)

        assertEquals(
            listOf(2),
            conflictWeeksForEditedCourseGroup(listOf(original), listOf(moved), listOf(original, other))
        )
    }

    @Test
    fun singleCourseMovedToAnotherWeekdayKeepsItsDatabaseId() {
        val original = course(41, weekday = 1, weeks = listOf(1, 2, 3))

        assertEquals(
            41L,
            courseEditorOriginalForWeekday(
                originals = listOf(original),
                weekday = 5,
                selectedWeekdayCount = 1
            )?.id
        )
    }

    @Test
    fun applyAllIncludesWeeksWithDifferentExactTimesButKeepsOtherCoursesSeparate() {
        val original = course(41, weekday = 1, weeks = listOf(1, 2))
            .copy(customStartTime = "10:10", customEndTime = "11:45")
        val anotherTime = course(42, weekday = 1, weeks = listOf(3, 4))
            .copy(customStartTime = "10:20", customEndTime = "11:55")
        val otherTeacher = course(43, weekday = 1, weeks = listOf(5, 6), teacher = "周老师")

        val scope = courseApplyAllScope(
            original,
            original.copy(customStartTime = "10:30", customEndTime = "12:00"),
            listOf(original, anotherTime, otherTeacher)
        )

        assertEquals(setOf(41L, 42L), scope.originals.map(CourseEntity::id).toSet())
        assertEquals(listOf(1, 2, 3, 4), scope.edited.weeks)
        assertEquals("10:30", scope.edited.customStartTime)
        assertEquals("12:00", scope.edited.customEndTime)
    }

    @Test
    fun applyAllDoesNotCollapseConcurrentOrUnchangedTimeVariants() {
        val original = course(41, weekday = 1, weeks = listOf(1, 2))
            .copy(customStartTime = "10:10", customEndTime = "11:45")
        val laterWeeks = course(42, weekday = 1, weeks = listOf(3, 4))
            .copy(customStartTime = "10:20", customEndTime = "11:55")
        val concurrent = course(43, weekday = 1, weeks = listOf(2, 5))
            .copy(customStartTime = "10:30", customEndTime = "12:00")

        val timeEdit = courseApplyAllScope(original, original.copy(customStartTime = "10:40"),
            listOf(original, laterWeeks, concurrent))
        val noteEdit = courseApplyAllScope(original, original.copy(note = "更新备注"),
            listOf(original, laterWeeks, concurrent))

        assertEquals(setOf(41L, 42L), timeEdit.originals.map(CourseEntity::id).toSet())
        assertEquals(listOf(41L), noteEdit.originals.map(CourseEntity::id))
    }
}
