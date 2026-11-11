package com.xiaomanjun.sleepdownschedule.platform.coloros

import com.xiaomanjun.sleepdownschedule.model.VisualEffectMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualEffectModeTest {
    @Test
    fun missingPreferenceDefaultsToGlowWhileSavedValuesStay() {
        assertEquals(VisualEffectMode.ColorOsGlow, VisualEffectMode.fromId(null))
        assertEquals(VisualEffectMode.ColorOsGlow, VisualEffectMode.fromId("unknown"))
        assertEquals(VisualEffectMode.LiquidGlass, VisualEffectMode.fromId("liquid_glass"))
        assertEquals(VisualEffectMode.ColorOsGlow, VisualEffectMode.fromId("coloros_glow"))
        VisualEffectMode.entries.forEach { assertEquals(it, VisualEffectMode.fromId(it.id)) }
    }

}
