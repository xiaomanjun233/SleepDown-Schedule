package com.xiaomanjun.sleepdownschedule.domain.schedule

import com.xiaomanjun.sleepdownschedule.model.defaultConfig
import com.xiaomanjun.sleepdownschedule.model.PeriodEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TimeSlotCoursesTest {
    @Test
    fun editingOneSlotPreservesConcurrentAdditionsAndDeletingOnlyRemovesItsTarget() {
        val original = slot()
        val other = slot(id = "b", name = "晚自修", start = "19:00", end = "20:00", afterPeriod = 8)
        val updated = original.copy(name = "晨读")
        assertEquals(listOf(other, updated), editTimeSlotCourse(listOf(original, other), original, updated))
        assertEquals(listOf(other), editTimeSlotCourse(listOf(updated, other), updated, null))
        assertEquals(listOf(other, original), editTimeSlotCourse(listOf(other), null, original))
        val legacyOrder = original.copy(weekdays = listOf(5, 4, 3, 2, 1, 1))
        assertEquals(listOf(updated), editTimeSlotCourse(listOf(legacyOrder), original, updated))
    }

    @Test
    fun staleEditsCannotOverwriteChangedOrDeletedSlots() {
        val original = slot()
        assertThrows(IllegalArgumentException::class.java) {
            editTimeSlotCourse(listOf(original.copy(name = "已修改")), original, original.copy(name = "晨读"))
        }
        assertThrows(IllegalArgumentException::class.java) { editTimeSlotCourse(emptyList(), original, null) }
        assertThrows(IllegalArgumentException::class.java) { editTimeSlotCourse(listOf(original), null, original) }
        assertThrows(IllegalArgumentException::class.java) { editTimeSlotCourse(listOf(original), original, original.copy(id = "b")) }
    }

    @Test
    fun addingCannotExceedLimitOrSaveInvalidSelection() {
        val full = (1..MaxTimeSlotCourses).map { slot(id = it.toString()) }
        assertThrows(IllegalArgumentException::class.java) { editTimeSlotCourse(full, null, slot(id = "new")) }
        assertThrows(IllegalArgumentException::class.java) { editTimeSlotCourse(emptyList(), null, slot(weekdays = emptyList())) }
    }

    @Test
    fun completeRangeOnlyAllowsMatchingGapIncludingExactBellBoundaries() {
        val periods = listOf(PeriodEntity(1, "08:00", "08:45", 1), PeriodEntity(2, "08:55", "09:40", 1),
            PeriodEntity(3, "14:00", "14:45", 1))
        assertEquals(listOf(0), allowedTimeSlotAnchors("07:30", "08:00", periods))
        assertEquals(listOf(1), allowedTimeSlotAnchors("08:45", "08:55", periods.reversed()))
        assertEquals(listOf(2), allowedTimeSlotAnchors("12:00", "14:00", periods))
        assertEquals(listOf(3), allowedTimeSlotAnchors("19:00", "22:00", periods))
        assertEquals(listOf(0), allowedTimeSlotAnchors("19:00", "22:00", emptyList()))
    }

    @Test
    fun overlappingRangesOnlyOfferTheEdgesOfTheCoveredSpan() {
        val periods = listOf(PeriodEntity(1, "08:00", "08:45", 1), PeriodEntity(2, "08:55", "09:40", 1))
        assertEquals(listOf(0, 1), allowedTimeSlotAnchors("07:30", "08:01", periods))
        assertEquals(listOf(0, 2), allowedTimeSlotAnchors("08:30", "09:00", periods))
        assertEquals(listOf(1, 2), allowedTimeSlotAnchors("08:50", "09:41", periods))
        assertThrows(IllegalArgumentException::class.java) { allowedTimeSlotAnchors("09:00", "08:00", periods) }
    }

    @Test
    fun threeClockModesAndLegacyRangesRoundTripWithoutChangingTheirMeaning() {
        val period = slot(start = "08:00", end = "09:40").copy(periodIndices = listOf(1, 2))
        val range = slot(id = "range", start = "08:00", end = "09:40")
        val lessons = slot(id = "lessons", start = "19:00", end = "20:40").copy(periodIndices = listOf(1, 2),
            customPeriodTimes = "1,19:00-19:45;2,19:55-20:40")
        val roundTrip = decodeTimeSlotCourses(encodeTimeSlotCourses(listOf(period, range, lessons))).associateBy { it.id }
        assertEquals(period, roundTrip[period.id])
        assertEquals(range, roundTrip[range.id])
        assertEquals(lessons, roundTrip[lessons.id])
        val legacy = decodeTimeSlotCourses("""[{"id":"legacy","name":"早读","startTime":"07:30","endTime":"07:50"}]""").single()
        assertTrue(legacy.periodIndices.isEmpty())
        assertEquals(null, legacy.customPeriodTimes)
    }

    @Test
    fun periodClockFollowsBellsWhileRangeAndLessonClockRemainExact() {
        val periods = listOf(PeriodEntity(1, "08:10", "08:55", 1), PeriodEntity(2, "09:05", "09:50", 1))
        val selected = slot(start = "08:00", end = "09:40").copy(periodIndices = listOf(1, 2))
        val resolved = resolvedTimeSlotCourse(selected, periods)
        assertEquals("08:10", resolved.startTime)
        assertEquals("09:50", resolved.endTime)
        val range = selected.copy(periodIndices = emptyList())
        assertEquals(range, resolvedTimeSlotCourse(range, periods))
        val lessons = selected.copy(customPeriodTimes = "1,08:00-08:45;2,08:55-09:40")
        assertEquals(lessons, resolvedTimeSlotCourse(lessons, periods))
        assertEquals(selected, resolvedTimeSlotCourse(selected, periods.take(1)))
    }

    @Test
    fun lessonClockCannotLoseItsIndicesOrDisagreeWithItsRange() {
        val lessons = slot(start = "19:00", end = "20:40").copy(periodIndices = listOf(1, 2),
            customPeriodTimes = "1,19:00-19:45;2,19:55-20:40")
        assertThrows(IllegalArgumentException::class.java) { encodeTimeSlotCourses(listOf(lessons.copy(periodIndices = emptyList()))) }
        assertThrows(IllegalArgumentException::class.java) { encodeTimeSlotCourses(listOf(lessons.copy(endTime = "20:00"))) }
        assertThrows(IllegalArgumentException::class.java) { encodeTimeSlotCourses(listOf(lessons.copy(
            customPeriodTimes = "1,19:00-20:00;2,19:55-20:40"))) }
    }

    private fun slot(
        id: String = "a",
        name: String = "早读",
        start: String = "07:30",
        end: String = "07:50",
        afterPeriod: Int = 0,
        weekdays: List<Int> = listOf(1, 2, 3, 4, 5)
    ) = TimeSlotCourse(id, name, start, end, afterPeriod, weekdays)

    @Test
    fun roundTripKeepsStableOrderAndNormalizesWeekdays() {
        val encoded = encodeTimeSlotCourses(
            listOf(
                slot(id = "b", name = "午休", start = "12:00", end = "13:00", afterPeriod = 4, weekdays = listOf(5, 1, 3)),
                slot(id = "a", name = "早读", weekdays = listOf(3, 2, 1, 2))
            )
        )
        val decoded = decodeTimeSlotCourses(encoded)
        assertEquals(listOf("a", "b"), decoded.map { it.id })
        assertEquals(listOf(1, 2, 3), decoded.first().weekdays)
        assertEquals(encoded, encodeTimeSlotCourses(decoded))
        assertEquals(encoded, canonicalTimeSlotCourses(encoded))
    }

    @Test
    fun blankValueMeansNoTimeSlots() {
        assertTrue(decodeTimeSlotCourses("").isEmpty())
        assertEquals("", encodeTimeSlotCourses(emptyList()))
        assertEquals("", canonicalTimeSlotCourses(""))
    }

    @Test
    fun weekdayLookupFiltersAndClampsAnchorToCurrentPeriods() {
        val config = defaultConfig().copy(
            timeSlotCoursesJson = encodeTimeSlotCourses(
                listOf(
                    slot(id = "early", weekdays = listOf(1, 3), afterPeriod = 0),
                    slot(id = "late", start = "20:00", end = "20:40", afterPeriod = 9, weekdays = listOf(1))
                )
            )
        )
        val monday = timeSlotCoursesForWeekday(config, weekday = 1, periodCount = 4)
        assertEquals(listOf("early", "late"), monday.map { it.id })
        assertEquals(listOf(0, 4), monday.map { it.afterPeriod })
        assertTrue(timeSlotCoursesForWeekday(config, weekday = 2, periodCount = 4).isEmpty())
    }

    @Test
    fun validationRejectsBrokenTimeRangesAndEmptyWeekdays() {
        assertThrows(IllegalArgumentException::class.java) {
            encodeTimeSlotCourses(listOf(slot(start = "08:00", end = "07:50")))
        }
        assertThrows(IllegalArgumentException::class.java) {
            encodeTimeSlotCourses(listOf(slot(weekdays = emptyList())))
        }
        assertThrows(IllegalArgumentException::class.java) {
            encodeTimeSlotCourses(listOf(slot(name = " ")))
        }
        assertThrows(IllegalArgumentException::class.java) {
            encodeTimeSlotCourses(listOf(slot(start = "7:30")))
        }
    }

    @Test
    fun malformedStoredValueDoesNotBreakWeekRendering() {
        val config = defaultConfig().copy(timeSlotCoursesJson = "{not-json}")
        assertTrue(timeSlotCoursesForWeekday(config, weekday = 1, periodCount = 4).isEmpty())
        assertEquals("{not-json}", canonicalTimeSlotCourses("{not-json}"))
    }

    @Test
    fun summariesDescribeAnchorTimeAndWeekdays() {
        assertEquals(null, parseTimeSlotTime("24:00"))
        assertEquals(null, parseTimeSlotTime("12:60"))
        assertEquals(null, parseTimeSlotTime("07:30:10"))
        assertEquals("第一节前 · 07:30–07:50 · 周一至周五", timeSlotCourseSummary(slot()))
        assertEquals("第4节后 · 12:00–13:00 · 每天",
            timeSlotCourseSummary(slot(start = "12:00", end = "13:00", afterPeriod = 4, weekdays = (1..7).toList())))
        assertEquals("第一节前", timeSlotAnchorLabel(0))
        assertEquals("第2节后", timeSlotAnchorLabel(2))
    }
}
