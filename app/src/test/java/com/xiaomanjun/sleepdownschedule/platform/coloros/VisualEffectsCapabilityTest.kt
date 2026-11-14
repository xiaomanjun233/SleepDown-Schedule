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
            couiKitAvailable = true,
        )
        assertTrue(state.glowEnabled)
        assertTrue(state.usesCouiControls)
        assertTrue(state.usesCouiDock)
    }

    @Test
    fun unavailableKitDoesNotEnableGlowMaterials() {
        val state = VisualEffectsState(
            effectiveMode = VisualEffectMode.ColorOsGlow,

            couiKitAvailable = false,
        )
        assertTrue(state.glowEnabled)
        assertFalse(state.usesCouiDock)
        assertFalse(state.usesCouiControls)
    }

    @Test
    fun liquidModeDoesNotUseEitherGlowRenderer() {
        val state = VisualEffectsState(
            effectiveMode = VisualEffectMode.LiquidGlass,
            couiKitAvailable = true,
        )
        assertFalse(state.glowEnabled)
        assertFalse(state.usesCouiDock)
        assertFalse(state.usesCouiControls)
    }
}
