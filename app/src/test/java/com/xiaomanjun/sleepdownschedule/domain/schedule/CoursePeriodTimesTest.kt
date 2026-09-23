package com.xiaomanjun.sleepdownschedule.domain.schedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CoursePeriodTimesTest {
    @Test
    fun twoCoursesCanSharePeriodNumbersWithDifferentBellsAndBreaks() {
        val first = normalizeCourseClock(
            null, null, "3,10:10-10:50;4,11:05-11:45", listOf(3, 4)
        )
        val second = normalizeCourseClock(
            null, null, "3,10:20-11:00;4,11:15-11:55", listOf(3, 4)
        )

        assertEquals("10:10", first.start)
        assertEquals("11:45", first.end)
        assertEquals("10:20", second.start)
        assertEquals("11:55", second.end)
        assertEquals("3,10:10-10:50;4,11:05-11:45", first.periodTimes)
        assertEquals("3,10:20-11:00;4,11:15-11:55", second.periodTimes)
    }

    @Test
    fun refusesMissingOrConflictingPeriodBells() {
        assertThrows(IllegalArgumentException::class.java) {
            normalizeCourseClock(null, null, "3,10:10-10:50", listOf(3, 4))
        }
        assertThrows(IllegalArgumentException::class.java) {
            normalizeCourseClock(null, null, "3,10:10-11:10;4,11:05-11:45", listOf(3, 4))
        }
        assertThrows(IllegalArgumentException::class.java) {
            normalizeCourseClock("10:20", "11:45", "3,10:10-10:50;4,11:05-11:45", listOf(3, 4))
        }
    }
}
