package com.xiaomanjun.sleepdownschedule.feature.home.week

import com.xiaomanjun.sleepdownschedule.model.*
import com.xiaomanjun.sleepdownschedule.domain.schedule.*
import com.xiaomanjun.sleepdownschedule.feature.home.day.weekCourseBuckets
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class WeekPresentationTest {
    private val periods = listOf(PeriodEntity(1, "08:00", "08:45"), PeriodEntity(2, "08:55", "09:40"),
        PeriodEntity(3, "09:50", "10:35"), PeriodEntity(4, "10:45", "11:30"))
    private fun course(id: Long, slots: List<Int>, weeks: List<Int> = listOf(1)) =
        CourseEntity(id, "课程$id", null, null, 1, slots, weeks, WeekParity.ALL, null)

    @Test fun stableReferencesNeverOverlapActualOrEachOther() {
        val actual = course(10, listOf(2, 3), listOf(2))
        val inputs = listOf(actual, course(4, listOf(3, 4)), course(3, listOf(1)), course(2, listOf(1)))
        val cards = nonCurrentWeekCards(inputs, listOf(actual), 2, 1, periods, 60f)
        assertEquals(listOf(2L), cards.map { it.course.id })
        assertEquals(cards, nonCurrentWeekCards(inputs.reversed(), listOf(actual), 2, 1, periods, 60f))
        assertTrue(nonCurrentWeekCards(inputs, listOf(actual), 1, 1, periods, 60f).isEmpty())
    }

    @Test fun usesFinalVisibleBoundsAndCustomTimeRatherThanOnlyPeriodIndices() {
        val reference = course(1, listOf(1)).copy(customStartTime = "08:30", customEndTime = "09:10")
        assertTrue(nonCurrentWeekCards(listOf(reference), emptyList(), 2, 1, periods, 60f,
            listOf(55f to 85f)).isEmpty())
        assertEquals(1, nonCurrentWeekCards(listOf(reference), emptyList(), 2, 1, periods, 60f,
            listOf(150f to 170f)).size)
    }

    @Test fun parityAndBrowsedWeekDetermineReferenceVisibility() {
        val odd = course(1, listOf(1), (1..8).toList()).copy(weekParity = WeekParity.ODD)
        assertEquals(1, nonCurrentWeekCards(listOf(odd), emptyList(), 4, 1, periods, 60f).size)
        assertTrue(nonCurrentWeekCards(listOf(odd), emptyList(), 3, 1, periods, 60f).isEmpty())
    }

    @Test fun supplementaryTimesKeepAllCurrentCoursesAndPickStableNonOverlappingReferences() {
        val first = course(1, emptyList()).copy(customStartTime = "19:00", customEndTime = "20:00")
        val overlap = first.copy(id = 2, customStartTime = "19:30", customEndTime = "20:30")
        val actual = first.copy(id = 3, weeks = listOf(2), customStartTime = "20:15", customEndTime = "21:00")
        assertEquals(listOf(first), nonCurrentSupplementaryCourses(listOf(overlap, first), listOf(actual), 2, 1, periods))
    }

    @Test fun emptyWeekendHolidayIsVisibleWithoutBecomingACourseOrReminder() {
        val date = LocalDate.parse("2026-09-19")
        val plain = defaultConfig().copy(termStartDate = "2026-08-31", autoCurrentWeek = true, hideEmptyWeekends = true,
            scheduleAdjustmentsJson = encodeScheduleAdjustments(listOf(ScheduleAdjustment(date.toString()))))
        val decorated = plain.copy(showNonCurrentWeekCourses = true,
            scheduleAdjustmentsJson = encodeScheduleAdjustments(listOf(ScheduleAdjustment(date.toString(), allDayPlaceholder = true))))
        val actual = course(10, listOf(1), listOf(3))
        val plainBuckets = weekCourseBuckets(listOf(actual), 3, plain, date)
        val decoratedBuckets = weekCourseBuckets(listOf(actual), 3, decorated, date)
        assertEquals(plainBuckets, decoratedBuckets)
        assertEquals((1..7).toList(), weekPresentationWeekdays(decoratedBuckets, listOf(actual), decorated, 3))
        assertTrue(coursesForDate(AppState(config = decorated, courses = listOf(actual)), date).isEmpty())
        assertEquals(listOf(actual), coursesForDate(AppState(config = decorated, courses = listOf(actual)), date.minusDays(5)))
    }

    @Test fun legacyLayoutRetainsAlignmentWhileNewAxesAreIndependent() {
        val original = defaultConfig().copy(weekCardTextAlignment = WeekCardTextAlignment.END)
        assertEquals(WeekCardTextAlignment.CENTER, original.copy(weekCardContentLayout = WeekCardContentLayout.CENTERED).effectiveWeekCardTextAlignment())
        assertEquals(WeekCardTextAlignment.START, original.copy(weekCardContentLayout = WeekCardContentLayout.TOP_DOWN).effectiveWeekCardTextAlignment())
        for (vertical in listOf(WeekCardContentLayout.TOP, WeekCardContentLayout.MIDDLE)) {
            for (horizontal in WeekCardTextAlignment.entries) {
                assertEquals(horizontal, original.copy(weekCardContentLayout = vertical, weekCardTextAlignment = horizontal).effectiveWeekCardTextAlignment())
            }
        }
    }
}
