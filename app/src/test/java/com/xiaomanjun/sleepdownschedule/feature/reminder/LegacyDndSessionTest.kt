package com.xiaomanjun.sleepdownschedule.feature.reminder

import android.app.NotificationManager.*
import org.junit.Assert.*
import org.junit.Test

class LegacyDndSessionTest {
    @Test fun normalModeReturnsToItsOriginalFilter() {
        val session = LegacyDndSession.start(INTERRUPTION_FILTER_ALL, 7)

        assertEquals(INTERRUPTION_FILTER_PRIORITY, session.appliedFilter)
        assertEquals(INTERRUPTION_FILTER_ALL, session.filterToRestore(INTERRUPTION_FILTER_PRIORITY, 7))
        assertFalse(session.observe(INTERRUPTION_FILTER_PRIORITY, 7).overridden)
    }

    @Test fun existingStricterDndModesStayInPlace() {
        listOf(INTERRUPTION_FILTER_PRIORITY, INTERRUPTION_FILTER_ALARMS, INTERRUPTION_FILTER_NONE).forEach { filter ->
            val session = LegacyDndSession.start(filter, 7)
            assertEquals(filter, session.appliedFilter)
            assertEquals(filter, session.filterToRestore(filter, 7))
        }
    }

    @Test fun aManualModeChangePreventsRestoration() {
        val session = LegacyDndSession.start(INTERRUPTION_FILTER_ALL, 7)

        assertNull(session.filterToRestore(INTERRUPTION_FILTER_ALARMS, 7))
        assertNull(session.filterToRestore(INTERRUPTION_FILTER_ALL, 7))
    }

    @Test fun changingAwayAndBackDoesNotRegainOwnership() {
        val session = LegacyDndSession.start(INTERRUPTION_FILTER_ALL, 7)
            .observe(INTERRUPTION_FILTER_NONE, 7)
            .observe(INTERRUPTION_FILTER_PRIORITY, 7)

        assertTrue(session.overridden)
        assertNull(session.filterToRestore(INTERRUPTION_FILTER_PRIORITY, 7))
    }

    @Test fun rebootInvalidatesThePreviousSession() {
        val session = LegacyDndSession.start(INTERRUPTION_FILTER_ALL, 7)
        assertNull(session.filterToRestore(INTERRUPTION_FILTER_PRIORITY, 8))
        assertTrue(session.observe(INTERRUPTION_FILTER_PRIORITY, 8).overridden)
    }

    @Test(expected = IllegalArgumentException::class)
    fun unavailableModeIsNotGuessed() {
        LegacyDndSession.start(INTERRUPTION_FILTER_UNKNOWN, 7)
    }
}
