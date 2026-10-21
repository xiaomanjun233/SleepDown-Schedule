package com.xiaomanjun.sleepdownschedule.domain.schedule

import com.xiaomanjun.sleepdownschedule.model.*
import org.junit.Assert.*
import org.junit.Test

class ScheduleRefreshReviewTest {
    private fun course(id: Long, name: String = "数学", weekday: Int = 1) = RefreshCourse.from(
        CourseEntity(id = id, name = name, teacher = "张老师", location = "101", weekday = weekday,
            periods = listOf(1, 2), weeks = listOf(1, 2), weekParity = WeekParity.ALL, note = null))

    @Test fun unchangedContentIgnoresOrderIdsAndLocalColor() {
        val before = listOf(course(8).copy(color = 123), course(9, "英语"))
        val after = before.reversed().map { it.copy(id = 0, color = null, weeks = it.weeks.reversed()) }
        assertTrue(refreshDifferences(before, after).isEmpty())
    }

    @Test fun uniqueLessonCanMoveAndRetainItsIdentityAndColor() {
        val before = listOf(course(8).copy(color = 123))
        val after = listOf(course(0, weekday = 3).copy(location = "202"))
        val changes = refreshDifferences(before, after)
        assertEquals(1, changes.size)
        assertEquals(before, mergeReviewedRefresh(before, changes, emptySet()))
        val chosen = mergeReviewedRefresh(before, changes, setOf(0)).single()
        assertEquals(8L, chosen.id)
        assertEquals(123L, chosen.color)
        assertEquals(3, chosen.weekday)
        assertEquals("202", chosen.location)
    }

    @Test fun ambiguousRepeatedLessonsAreListedSeparatelyInsteadOfGuessing() {
        val before = listOf(course(1, weekday = 1), course(2, weekday = 2))
        val after = listOf(course(0, weekday = 3), course(0, weekday = 4))
        val changes = refreshDifferences(before, after)
        assertEquals(4, changes.size)
        assertTrue(changes.none { it.before != null && it.after != null })
        assertEquals(before, mergeReviewedRefresh(before, changes, emptySet()))
        assertEquals(after, mergeReviewedRefresh(before, changes, changes.indices.toSet()))
    }

    @Test fun individualSelectionKeepsUnchangedAndUnselectedCourses() {
        val before = listOf(course(1), course(2, "英语"), course(3, "物理"))
        val after = listOf(course(0).copy(location = "202"), course(0, "物理"), course(0, "化学"))
        val changes = refreshDifferences(before, after)
        val selected = changes.indices.filter { changes[it].after?.name == "化学" }.toSet()
        val merged = mergeReviewedRefresh(before, changes, selected)
        assertEquals(4, merged.size)
        assertTrue(merged.containsAll(before))
        assertEquals(0L, merged.single { it.name == "化学" }.id)
    }

    @Test fun exactDuplicateMatchesConsumeOneOccurrenceAtATime() {
        val changes = refreshDifferences(listOf(course(1), course(2)), listOf(course(0)))
        assertEquals(1, changes.size)
        assertNull(changes.single().after)
    }

    @Test fun timingGuardIgnoresAppearanceButDetectsPeriodAndTermChanges() {
        val config = defaultConfig()
        val periods = listOf(PeriodEntity(1, "08:00", "08:45"))
        val key = refreshTimingKey(config, periods)
        assertEquals(key, refreshTimingKey(config.copy(cardAlpha = 0.5f), periods))
        assertNotEquals(key, refreshTimingKey(config.copy(totalWeeks = 16), periods))
        assertNotEquals(key, refreshTimingKey(config, listOf(periods.single().copy(startTime = "08:10"))))
    }
}
