package com.xiaomanjun.sleepdownschedule.feature.settings

import org.junit.Assert.*
import org.junit.Test

class PeriodEditorExitCoordinatorTest {
    @Test fun toolbarExitWaitsForTheNestedDraftAndDoesNotCompleteTwice() {
        val coordinator = PeriodEditorExitCoordinator()
        coordinator.opened()
        assertTrue(coordinator.requestPageExit())
        assertTrue(coordinator.isOpen)
        assertTrue(coordinator.pending)
        assertNull(coordinator.result)
        assertEquals(0, coordinator.resultVersion)
        assertTrue(coordinator.requestPageExit())
        assertEquals(1, coordinator.requestVersion)
        coordinator.closedAfterDraftHandoff()
        assertFalse(coordinator.isOpen)
        assertFalse(coordinator.pending)
        assertEquals(true, coordinator.result)
        assertEquals(1, coordinator.resultVersion)
        coordinator.closedAfterDraftHandoff()
        assertEquals(1, coordinator.resultVersion)
    }

    @Test fun continueEditingAndSaveFailureKeepThePageOpenAndAllowAnotherAttempt() {
        val coordinator = PeriodEditorExitCoordinator()
        coordinator.opened()
        coordinator.requestPageExit()
        coordinator.cancelRequest()
        assertTrue(coordinator.isOpen)
        assertFalse(coordinator.pending)
        assertEquals(false, coordinator.result)
        assertTrue(coordinator.requestPageExit())
        assertEquals(2, coordinator.requestVersion)
        coordinator.closedAfterDraftHandoff()
        assertEquals(true, coordinator.result)
        assertEquals(2, coordinator.resultVersion)
    }

    @Test fun ordinaryEditingDoesNotNavigateAndDisposalCancelsPendingExit() {
        val coordinator = PeriodEditorExitCoordinator()
        assertFalse(coordinator.requestPageExit())
        coordinator.opened()
        coordinator.closedAfterDraftHandoff()
        assertEquals(0, coordinator.resultVersion)
        coordinator.opened()
        coordinator.requestPageExit()
        coordinator.abandon()
        assertFalse(coordinator.isOpen)
        assertEquals(false, coordinator.result)
    }
}
