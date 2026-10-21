package com.xiaomanjun.sleepdownschedule.transition

import org.junit.Assert.*
import org.junit.Test

class SharedViewKitCapabilitiesTest {
    @Test fun coordinateTokenOverridesHumanDescription() {
        assertFalse(caps("rect|SCREEN|WINDOW mentioned in description").windowCoordinates)
        assertTrue(caps("rect|WINDOW|bounds").windowCoordinates)
        assertTrue(caps("rect|old WINDOW description").windowCoordinates)
        assertFalse(caps(null).windowCoordinates)
    }

    @Test fun backAndFallbackHaveIndependentSupportSets() {
        val result = SharedViewKitCapabilities.from(mapOf(
            SharedViewKitContract.BackType to "int|VIEW_ANIM_FOLLOW_SYSTEM|back",
            SharedViewKitContract.FallbackType to "int|VIEW_ANIM_DEFAULT|fallback"))
        assertEquals(2, result.missingTargetBackType)
        assertNull(result.fallbackType)
    }

    @Test fun undeclaredParametersStayUnsetWhileBareIntAllowsLegalPublicValues() {
        assertNull(caps(null).missingTargetBackType)
        assertEquals(2, SharedViewKitCapabilities.from(mapOf(SharedViewKitContract.BackType to "int")).missingTargetBackType)
        assertNull(SharedViewKitCapabilities.from(mapOf(SharedViewKitContract.BackType to "boolean")).missingTargetBackType)
    }

    private fun caps(bounds: String?) = SharedViewKitCapabilities.from(
        bounds?.let { mapOf(SharedViewKitContract.Bounds to it) }.orEmpty())
}
