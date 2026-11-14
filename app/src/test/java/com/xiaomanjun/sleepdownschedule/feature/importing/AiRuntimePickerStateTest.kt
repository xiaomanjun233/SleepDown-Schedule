package com.xiaomanjun.sleepdownschedule.feature.importing

import org.junit.Assert.*
import org.junit.Test

class AiRuntimePickerStateTest {
    private fun picker() = AiRuntimePickerState(AiImportSettings(AiProviderPresets.none.copy(defaultModel = "model-A")))

    @Test fun triggerTransfersOnlyAfterPopupMeasurementAndReturnsAfterActualExit() {
        val state = picker()
        state.open()
        val session = state.popupSession
        assertFalse(state.triggerTransferred)
        state.onPopupMeasured()
        assertTrue(state.triggerTransferred)
        state.dismiss()
        assertTrue(state.triggerTransferred)
        state.onDismissFinished()
        assertFalse(state.triggerTransferred)
        assertNull(state.retainedAnchorLabel)
        state.open()
        assertEquals(session + 1, state.popupSession)
    }

    @Test fun selectionCannotResizeTheSourceInTheMiddleOfReturnMotion() {
        val state = picker()
        state.open()
        state.onPopupMeasured()
        state.commit(state.settings.copy(profile = state.settings.profile.copy(defaultModel = "a-much-longer-model-B")))
        state.dismiss()
        assertEquals("model-A", state.label)
        state.onDismissFinished()
        assertEquals("a-much-longer-model-B", state.label)
    }

    @Test fun reopeningKeepsTheAnchorAndRejectsLateExitCompletion() {
        val state = picker()
        state.open()
        state.onPopupMeasured()
        val session = state.popupSession
        state.dismiss()
        state.commit(state.settings.copy(profile = state.settings.profile.copy(defaultModel = "model-B")))
        state.open()
        state.onDismissFinished()
        assertTrue(state.expanded)
        assertEquals(session, state.popupSession)
        assertTrue(state.triggerTransferred)
        assertEquals("model-A", state.label)
        state.dismiss()
        state.onDismissFinished()
        assertEquals("model-B", state.label)
    }

    @Test fun losingTheHostRestoresTheTriggerEvenWithoutAnExitCallback() {
        val state = picker()
        state.open()
        state.onPopupMeasured()
        state.releaseHost()
        state.onPopupMeasured()
        assertFalse(state.expanded)
        assertFalse(state.triggerTransferred)
        assertNull(state.retainedAnchorLabel)
        state.open()
        assertTrue(state.expanded)
        assertFalse(state.triggerTransferred)
    }
}
