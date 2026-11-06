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

    @Test
    fun privateNativeControlsKeepTheirOwnCapabilityCheck() {
        assertTrue(supportsColorOsGlow(true, "OPPO", 36, "V17.0"))
        assertTrue(supportsColorOsGlow(true, "OnePlus", 36, "17.0.1"))
        assertFalse(supportsColorOsGlow(false, "OPPO", 36, "17.0"))
        assertFalse(supportsColorOsGlow(true, "Google", 36, "17.0"))
        assertFalse(supportsColorOsGlow(true, "OPPO", 30, "17.0"))
        listOf(null, "", "unknown", "16.0", "V15.0").forEach {
            assertFalse(supportsColorOsGlow(true, "OPPO", 36, it))
        }
    }
}
