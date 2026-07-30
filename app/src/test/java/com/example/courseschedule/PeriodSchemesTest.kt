package com.example.courseschedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PeriodSchemesTest {
    private val config = defaultConfig().copy(
        morningPeriodCount = 2,
        noonPeriodCount = 1,
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
            noonStartTime = "12:20",
            afternoonStartTime = "14:00",
            eveningStartTime = "19:00"
        )

        val result = resolveSchemeTimes(config, PeriodSchemeDraft(scheme, emptyList()))

        assertEquals(listOf("08:00", "08:55", "12:20", "14:00", "14:55", "19:00"), result.map { it.startTime })
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

    @Test
    fun laterDayPartIsPushedAfterPreviousPartInsteadOfOverlapping() {
        val scheme = PeriodSchemeEntity(
            id = 11,
            scheduleId = 1,
            name = "overlap",
            mode = PeriodSchemeMode.AUTO_MATCH,
            classDurationMinutes = 45,
            breakDurationMinutes = 10,
            morningStartTime = "08:00",
            noonStartTime = "12:10",
            afternoonStartTime = "12:10",
            eveningStartTime = "19:20"
        )

        val result = resolveSchemeTimes(config, PeriodSchemeDraft(scheme, emptyList()))

        assertEquals("12:55", result.first { it.periodIndex == 4 }.startTime)
        assertNull(validateResolvedPeriodTimes(result))
    }

    @Test
    fun normalizingAutoStartsPersistsTheEffectiveStart() {
        val scheme = PeriodSchemeEntity(
            id = 12,
            scheduleId = 1,
            name = "normalize",
            mode = PeriodSchemeMode.AUTO_MATCH,
            classDurationMinutes = 45,
            breakDurationMinutes = 10,
            morningStartTime = "08:00",
            noonStartTime = "12:10",
            afternoonStartTime = "12:10",
            eveningStartTime = "19:20"
        )

        val normalized = normalizeAutoSchemeStarts(config, PeriodSchemeDraft(scheme, emptyList()))

        assertEquals("12:55", normalized.scheme.afternoonStartTime)
        assertNull(validateResolvedPeriodTimes(normalized.times))
    }

    @Test
    fun periodPickerBoundsKeepSelectionBetweenNeighboringLessons() {
        val bounds = periodTimePickerBounds(previousEnd = "08:45", nextStart = "10:00")

        assertEquals(8 * 60 + 45, bounds.minimumStartMinute)
        assertEquals(10 * 60, bounds.maximumEndMinute)
        assertEquals(
            PeriodTimeSelection(8 * 60 + 45, 10 * 60),
            constrainPeriodTimeSelection(8 * 60, 11 * 60, bounds)
        )
        assertEquals(
            PeriodTimeSelection(9 * 60 + 9, 9 * 60 + 10),
            constrainPeriodTimeSelection(
                startMinute = 9 * 60 + 40,
                endMinute = 9 * 60 + 10,
                bounds = bounds,
                anchor = PeriodTimeSelectionAnchor.START
            )
        )
    }

    @Test
    fun automaticPartStartConstraintsReserveRoomAndPushLaterPartsForward() {
        val scheme = PeriodSchemeEntity(
            id = 13,
            scheduleId = 1,
            name = "constrained",
            mode = PeriodSchemeMode.AUTO_MATCH,
            classDurationMinutes = 45,
            breakDurationMinutes = 10
        )
        val draft = PeriodSchemeDraft(scheme, emptyList())

        val constrained = constrainAutomaticPartStarts(
            config,
            draft,
            mapOf(
                PeriodDayPart.MORNING to 8 * 60,
                PeriodDayPart.NOON to 8 * 60 + 20,
                PeriodDayPart.AFTERNOON to 8 * 60 + 30,
                PeriodDayPart.EVENING to 8 * 60 + 40
            )
        )

        assertEquals(8 * 60, constrained[PeriodDayPart.MORNING])
        assertEquals(9 * 60 + 40, constrained[PeriodDayPart.NOON])
        assertEquals(10 * 60 + 25, constrained[PeriodDayPart.AFTERNOON])
        assertEquals(12 * 60 + 5, constrained[PeriodDayPart.EVENING])
    }
}
