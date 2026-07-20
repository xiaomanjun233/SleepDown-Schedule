package com.example.courseschedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PeriodSchemesTest {
    private val config = defaultConfig().copy(
        morningPeriodCount = 2,
        afternoonPeriodCount = 2,
        eveningPeriodCount = 1
    )

    @Test
    fun autoMatchGeneratesEachDayPartIndependently() {
        val scheme = PeriodSchemeEntity(
            id = 9,
            scheduleId = 1,
            name = "test",
            mode = PeriodSchemeMode.AUTO_MATCH,
            classDurationMinutes = 45,
            breakDurationMinutes = 10,
            morningStartTime = "08:00",
            afternoonStartTime = "14:00",
            eveningStartTime = "19:00"
        )

        val result = resolveSchemeTimes(config, PeriodSchemeDraft(scheme, emptyList()))

        assertEquals(listOf("08:00", "08:55", "14:00", "14:55", "19:00"), result.map { it.startTime })
        assertNull(validateResolvedPeriodTimes(result))
    }

    @Test
    fun localOverrideDoesNotPushFollowingAutomaticPeriod() {
        val scheme = PeriodSchemeEntity(
            id = 10,
            scheduleId = 1,
            name = "test",
            mode = PeriodSchemeMode.AUTO_MATCH,
            classDurationMinutes = 45,
            breakDurationMinutes = 10
        )
        val override = PeriodSchemeTimeEntity(10, 1, "08:05", "08:40")

        val result = resolveSchemeTimes(
            config,
            PeriodSchemeDraft(scheme, listOf(override), overriddenPeriods = setOf(1))
        )

        assertEquals("08:05", result[0].startTime)
        assertEquals("08:55", result[1].startTime)
    }

    @Test
    fun overlapIsRejected() {
        val times = listOf(
            PeriodSchemeTimeEntity(1, 1, "08:00", "09:00"),
            PeriodSchemeTimeEntity(1, 2, "08:50", "09:35")
        )
        assertEquals("第 1 节与第 2 节时间重叠", validateResolvedPeriodTimes(times))
    }
}
