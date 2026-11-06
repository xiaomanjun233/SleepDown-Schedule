package com.xiaomanjun.sleepdownschedule.platform.coloros

import com.xiaomanjun.sleepdownschedule.core.ui.material.VisualEffectsState
import com.xiaomanjun.sleepdownschedule.model.VisualEffectMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualEffectsCapabilityTest {
    @Test
    fun glowCanUseCouiDockWithoutPrivateColorOsControls() {
        val state = VisualEffectsState(
            effectiveMode = VisualEffectMode.ColorOsGlow,
            colorOsAvailable = false,
            couiKitAvailable = true,
        )
        assertTrue(state.glowEnabled)
        assertTrue(state.usesCouiControls)
        assertTrue(state.usesCouiDock)
        assertFalse(state.usesNativeControls)
    }

    @Test
    fun oldSystemKeepsPreferenceAndUsesExistingNativePresentation() {
        val state = VisualEffectsState(
            effectiveMode = VisualEffectMode.ColorOsGlow,
            colorOsAvailable = true,
            couiKitAvailable = false,
        )
        assertTrue(state.glowEnabled)
        assertFalse(state.usesCouiDock)
        assertTrue(state.usesNativeControls)
    }

    @Test
    fun liquidModeDoesNotUseEitherGlowRenderer() {
        val state = VisualEffectsState(
            effectiveMode = VisualEffectMode.LiquidGlass,
            colorOsAvailable = true,
            couiKitAvailable = true,
        )
        assertFalse(state.glowEnabled)
        assertFalse(state.usesCouiDock)
        assertFalse(state.usesNativeControls)
    }
}
