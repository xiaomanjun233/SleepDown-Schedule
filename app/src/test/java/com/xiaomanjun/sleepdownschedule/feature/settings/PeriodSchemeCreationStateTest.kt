package com.xiaomanjun.sleepdownschedule.feature.settings

import com.xiaomanjun.sleepdownschedule.domain.schedule.PeriodDayPart
import org.junit.Assert.*
import org.junit.Test

class PeriodSchemeCreationStateTest {
    private val morning = PeriodDayPart.MORNING
    private val noon = PeriodDayPart.NOON
    private val afternoon = PeriodDayPart.AFTERNOON
    private val evening = PeriodDayPart.EVENING

    @Test fun eveningOffThenOnRestoresTheExactAllocation() {
        val original = PeriodSchemeCreationState(mapOf(morning to 4, afternoon to 4, evening to 2))
        val disabled = requireNotNull(original.withEnabledParts(original.enabledParts - evening))
        assertEquals(mapOf(morning to 4, afternoon to 6), disabled.counts)

        val restored = requireNotNull(disabled.withEnabledParts(disabled.enabledParts + evening))
        assertEquals(original.counts, restored.counts)
        assertEquals(original.enabledParts, restored.enabledParts)
        assertEquals(10, restored.total)
    }

    @Test fun noonOffThenOnRestoresTheExactAllocation() {
        val original = PeriodSchemeCreationState(mapOf(morning to 3, noon to 2, afternoon to 4, evening to 1))
        val disabled = requireNotNull(original.withEnabledParts(original.enabledParts - noon))
        val restored = requireNotNull(disabled.withEnabledParts(disabled.enabledParts + noon))

        assertEquals(original.counts, restored.counts)
        assertEquals(original.enabledParts, restored.enabledParts)
    }

    @Test fun splitOffThenOnRestoresOptionalSectionsAndTheirCounts() {
        val original = PeriodSchemeCreationState(mapOf(morning to 3, noon to 2, afternoon to 4, evening to 1))
        val disabled = requireNotNull(original.withSplitEnabled(false))
        assertEquals(setOf(morning), disabled.enabledParts)
        assertEquals(mapOf(morning to 10), disabled.counts)

        val restored = requireNotNull(disabled.withSplitEnabled(true))
        assertEquals(original.counts, restored.counts)
        assertEquals(original.enabledParts, restored.enabledParts)
    }

    @Test fun splitRoundTripDoesNotReenableAnOptionalSectionAlreadyDisabled() {
        val original = PeriodSchemeCreationState(mapOf(morning to 4, afternoon to 4, evening to 2))
        val noEvening = requireNotNull(original.withEnabledParts(original.enabledParts - evening))
        val restored = requireNotNull(requireNotNull(noEvening.withSplitEnabled(false)).withSplitEnabled(true))

        assertEquals(noEvening.counts, restored.counts)
        assertEquals(noEvening.enabledParts, restored.enabledParts)
    }

    @Test fun enablingMoreSectionsThanPeriodsIsRejectedWithoutIncreasingTheTotal() {
        val single = PeriodSchemeCreationState(mapOf(morning to 1))
        assertNull(single.withSplitEnabled(true))
        assertEquals(1, single.total)
        assertEquals(setOf(morning), single.enabledParts)

        val split = PeriodSchemeCreationState(mapOf(morning to 1, afternoon to 1))
        assertNull(split.withEnabledParts(split.enabledParts + noon))
        assertEquals(2, split.total)
    }

    @Test fun explicitReallocationSupersedesAnOlderToggleSnapshotAtTheSameTotal() {
        val original = PeriodSchemeCreationState(mapOf(morning to 4, afternoon to 4, evening to 2))
        val disabled = requireNotNull(original.withEnabledParts(original.enabledParts - evening))
            .withCounts(mapOf(morning to 2, afternoon to 8))
        val enabled = requireNotNull(disabled.withEnabledParts(disabled.enabledParts + evening))

        assertEquals(mapOf(morning to 2, afternoon to 7, evening to 1), enabled.counts)
        assertEquals(10, enabled.total)
    }

    @Test fun confirmingUnchangedCountsKeepsTheToggleSnapshot() {
        val original = PeriodSchemeCreationState(mapOf(morning to 4, afternoon to 4, evening to 2))
        val disabled = requireNotNull(original.withEnabledParts(original.enabledParts - evening))
        val confirmed = disabled.withCounts(disabled.counts)
        val restored = requireNotNull(confirmed.withEnabledParts(confirmed.enabledParts + evening))

        assertEquals(original.counts, restored.counts)
    }

    @Test fun explicitTotalChangePreservesThatTotalWhenSectionsAreReenabled() {
        val original = PeriodSchemeCreationState(mapOf(morning to 4, afternoon to 4, evening to 2))
        val disabled = requireNotNull(original.withSplitEnabled(false)).withCounts(mapOf(morning to 6))
        val restored = requireNotNull(disabled.withSplitEnabled(true))

        assertEquals(original.enabledParts, restored.enabledParts)
        assertEquals(6, restored.total)
        assertEquals(mapOf(morning to 4, afternoon to 1, evening to 1), restored.counts)
    }

    @Test fun splitRoundTripPreservesASavedEmptyMorningSection() {
        val original = PeriodSchemeCreationState(mapOf(morning to 0, afternoon to 3, evening to 1))
        val restored = requireNotNull(requireNotNull(original.withSplitEnabled(false)).withSplitEnabled(true))

        assertEquals(original.counts, restored.counts)
        assertEquals(setOf(afternoon, evening), restored.enabledParts)
    }
}
