package com.xiaomanjun.sleepdownschedule.domain.schedule

import com.xiaomanjun.sleepdownschedule.model.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class ImportDraftTimingTest {
    private val base = listOf(PeriodEntity(1, "09:00", "09:45"), PeriodEntity(2, "09:55", "10:40"))
    private val other = listOf(PeriodEntity(1, "08:20", "09:05"), PeriodEntity(2, "09:15", "10:00"))
    private val course = CourseEntity(name = "课程", teacher = null, location = "B楼", weekday = 1,
        periods = listOf(1, 2), weeks = listOf(1, 2), weekParity = WeekParity.ALL, note = null,
        independentPeriodSchemeId = -1)
    private val config = defaultConfig()

    @Test fun importedAlternateBellsBecomeCustomClocksWithoutPublicBindings() {
        val draft = ImportDraft(config, base, listOf(course), timetablePlan = ImportedTimetablePlan(
            independentTimetables = listOf(ImportedTimetable("B楼", other))))
        val converted = draft.withCustomCourseTimetables()
        val result = converted.courses.single()
        assertNull(result.independentPeriodSchemeId)
        assertTrue(converted.timetablePlan.independentTimetables.isEmpty())
        assertEquals(course.periods, result.periods)
        assertEquals(course.weeks, result.weeks)
        assertEquals(courseTimeSegments(course, other), courseTimeSegments(result, base))
        assertNull(draft.timingPreviewState().courses.single().independentPeriodSchemeId)
    }

    @Test fun retiringLegacyReferenceRetainsPartialOverridesAndRemainingBells() {
        val legacy = course.copy(customPeriodTimes = "1,08:25-09:10")
        val result = legacy.withCustomTimetableTimes(other)
        assertEquals("1,08:25-09:10;2,09:15-10:00", result.customPeriodTimes)
        assertNull(result.independentPeriodSchemeId)
        val changedBase = base.map { it.copy(startTime = "11:00", endTime = "11:45") }
        assertEquals(courseTimeSegments(legacy, other), courseTimeSegments(result, changedBase))
    }

    @Test fun customImportedClocksIgnoreSubsequentDateTimetableChanges() {
        val result = course.withCustomTimetableTimes(other)
        val catalog = TimetableCatalog(times = listOf(PeriodSchemeTimeEntity(1, 1, "11:00", "11:45"),
            PeriodSchemeTimeEntity(1, 2, "11:55", "12:40")),
            combos = listOf(TimetableComboEntity(1, "日期作息", 1)))
        assertEquals(courseTimeSegments(course, other), resolveCourseOccurrence(result,
            config.copy(activeTimetableComboId = 1), base, catalog, LocalDate.of(2026, 10, 29)).segments)
    }

    @Test(expected = IllegalArgumentException::class)
    fun incompleteLegacyReferenceIsRejectedBeforeImportWrites() {
        course.withCustomTimetableTimes(other.take(1))
    }

    @Test(expected = IllegalArgumentException::class)
    fun partialLessonClocksCannotHideMissingReferencedLessons() {
        course.copy(customStartTime = "08:20", customEndTime = "10:00", customPeriodTimes = "1,08:25-09:10")
            .withCustomTimetableTimes(emptyList())
    }
}
