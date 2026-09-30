package com.xiaomanjun.sleepdownschedule.domain.schedule

import com.xiaomanjun.sleepdownschedule.model.*
import org.junit.Assert.*
import org.junit.Test

class PeriodTopologyMappingTest {
    private val original = listOf(
        PeriodEntity(1, "08:00", "08:45"),
        PeriodEntity(2, "08:55", "09:40"),
        PeriodEntity(3, "09:50", "10:35")
    )
    private fun times(vararg ranges: Pair<String, String>) = ranges.mapIndexed { index, (start, end) ->
        PeriodSchemeTimeEntity(1, index + 1, start, end)
    }
    private fun course(indices: List<Int>, name: String = "课程") = CourseEntity(
        id = indices.first().toLong(), name = name, teacher = null, location = null, weekday = 1, periods = indices,
        weeks = listOf(1), weekParity = WeekParity.ALL, note = null
    )
    private fun preview(courses: List<CourseEntity>, target: List<PeriodSchemeTimeEntity>, vararg operations: PeriodTopologyOperation) =
        previewPeriodCourseMapping(courses, original, target, operations.toList())

    @Test fun appendThenDeleteDoesNotRemapAnOtherwiseTimeOnlyEdit() {
        val shifted = times("08:55" to "09:40", "09:50" to "10:35", "10:45" to "11:30")
        val value = course(listOf(2))
        assertEquals(preview(listOf(value), shifted), preview(listOf(value), shifted,
            PeriodTopologyOperation.AddAfter(3), PeriodTopologyOperation.Delete(4)))
        assertEquals(listOf(2), preview(listOf(value), shifted).courses.single().periods)
    }

    @Test fun automaticResizeKeepsLaterSectionIdentities() {
        val originalConfig = defaultConfig().copy(morningPeriodCount = 4, noonPeriodCount = 0, afternoonPeriodCount = 4, eveningPeriodCount = 0)
        val target = originalConfig.copy(morningPeriodCount = 3)
        val operations = periodTopologyResizeOperations(originalConfig, target, automaticSections = true)
        assertEquals(listOf(PeriodTopologyOperation.Delete(4)), operations)
        assertEquals(listOf(1, 2, 3, 5, 6, 7, 8), netPeriodIdentities(8, operations))
        assertEquals(listOf(PeriodTopologyOperation.Delete(8)),
            periodTopologyResizeOperations(originalConfig, target, automaticSections = false))
    }

    @Test fun sameTotalRepartitionDoesNotReplaceLessons() {
        val config = defaultConfig().copy(morningPeriodCount = 4, afternoonPeriodCount = 4)
        assertTrue(periodTopologyResizeOperations(config, config.copy(morningPeriodCount = 5, afternoonPeriodCount = 3), true).isEmpty())
    }

    @Test fun deleteAndRestoreRetainsSavedLessonIdentity() {
        val operations = listOf(PeriodTopologyOperation.Delete(2), PeriodTopologyOperation.AddAfter(1, 0))
        assertEquals(listOf(1, 2, 3), netPeriodIdentities(3, operations))
        assertFalse(hasNetPeriodTopologyChange(3, operations))
    }

    @Test fun consecutiveDeletesCanRestoreInEitherOrder() {
        val deleted = listOf(PeriodTopologyOperation.Delete(2), PeriodTopologyOperation.Delete(2))
        assertEquals(listOf(1, 2, 3), netPeriodIdentities(3, deleted + listOf(
            PeriodTopologyOperation.AddAfter(1, 0), PeriodTopologyOperation.AddAfter(2, 1))))
        assertEquals(listOf(1, 2, 3), netPeriodIdentities(3, deleted + listOf(
            PeriodTopologyOperation.AddAfter(1, 1), PeriodTopologyOperation.AddAfter(1, 0))))
    }

    @Test fun deletingAnUnoccupiedEarlierLessonRenumbersByClock() {
        val value = preview(listOf(course(listOf(3))), times("08:55" to "09:40", "09:50" to "10:35"),
            PeriodTopologyOperation.Delete(1))
        assertEquals(listOf(2), value.courses.single().periods)
        assertEquals(1, value.changedCount)
    }

    @Test fun deletingAnOccupiedLessonIsBlockedInsteadOfAssigningNearest() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            preview(listOf(course(listOf(2))), times("08:00" to "08:45", "09:50" to "10:35"),
                PeriodTopologyOperation.Delete(2))
        }
        assertTrue(error.message!!.contains("已删除"))
    }

    @Test fun replacingDeletedOccupiedLessonWithFreshLessonStillNeedsResolution() {
        assertThrows(IllegalArgumentException::class.java) {
            preview(listOf(course(listOf(2))), original.map { PeriodSchemeTimeEntity(1, it.periodIndex, it.startTime, it.endTime) },
                PeriodTopologyOperation.Delete(2), PeriodTopologyOperation.AddAfter(1))
        }
    }

    @Test fun ambiguousOverlapCannotExpandCourse() {
        assertThrows(IllegalArgumentException::class.java) {
            preview(listOf(course(listOf(2))), times("08:00" to "08:45", "08:55" to "09:15", "09:15" to "09:40", "09:50" to "10:35"),
                PeriodTopologyOperation.AddAfter(2))
        }
    }

    @Test fun mergingTwoSourceLessonsCannotCollapseCourse() {
        assertThrows(IllegalArgumentException::class.java) {
            preview(listOf(course(listOf(1, 2))), times("08:00" to "09:40", "09:50" to "10:35", "10:45" to "11:30", "11:40" to "12:25"),
                PeriodTopologyOperation.AddAfter(3))
        }
    }

    @Test fun separateCoursesCannotGainANewConflict() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            preview(listOf(course(listOf(1), "甲"), course(listOf(2), "乙")),
                times("08:00" to "09:40", "09:50" to "10:35", "10:45" to "11:30", "11:40" to "12:25"),
                PeriodTopologyOperation.AddAfter(3))
        }
        assertTrue(error.message!!.contains("同一节次"))
    }

    @Test fun customIntervalUsesItsActualClockInsteadOfDefaultBells() {
        val value = course(listOf(3)).copy(customStartTime = "09:45", customEndTime = "09:50")
        val result = preview(listOf(value), times("08:00" to "08:45", "08:55" to "09:40", "09:45" to "09:50", "09:50" to "10:35"),
            PeriodTopologyOperation.AddAfter(2))
        assertEquals(value, result.courses.single())
    }

    @Test fun oppositeParityCoursesDoNotGainAConflict() {
        val odd = course(listOf(1), "单周").copy(weeks = listOf(1, 2), weekParity = WeekParity.ODD)
        val even = course(listOf(2), "双周").copy(weeks = listOf(1, 2), weekParity = WeekParity.EVEN)
        val result = preview(listOf(odd, even),
            times("08:00" to "09:40", "09:50" to "10:35", "10:45" to "11:30", "11:40" to "12:25"),
            PeriodTopologyOperation.AddAfter(3))
        assertEquals(listOf(listOf(1), listOf(1)), result.courses.map { it.periods })
    }

    @Test fun separateCustomClocksCanShareABroadAnchorWithoutConflict() {
        val first = course(listOf(1)).copy(customStartTime = "08:00", customEndTime = "08:45")
        val second = course(listOf(2)).copy(customStartTime = "08:55", customEndTime = "09:40")
        val result = preview(listOf(first, second),
            times("08:00" to "09:40", "09:50" to "10:35", "10:45" to "11:30", "11:40" to "12:25"),
            PeriodTopologyOperation.AddAfter(3))
        assertEquals(listOf(listOf(1), listOf(1)), result.courses.map { it.periods })
    }

    @Test fun customBellsCannotSilentlyChangeTheirIndices() {
        val value = course(listOf(3)).copy(customPeriodTimes = "3,09:50-10:35")
        assertThrows(IllegalArgumentException::class.java) {
            preview(listOf(value), times("08:55" to "09:40", "09:50" to "10:35"), PeriodTopologyOperation.Delete(1))
        }
    }

    @Test fun noOverlapDoesNotGuessNearestLesson() {
        assertThrows(IllegalArgumentException::class.java) {
            preview(listOf(course(listOf(2))), times("12:00" to "12:45", "12:55" to "13:40", "13:50" to "14:35", "14:45" to "15:30"),
                PeriodTopologyOperation.AddAfter(3))
        }
    }
}
