package com.xiaomanjun.sleepdownschedule.data.preferences

import android.app.Application
import android.content.Context
import com.xiaomanjun.sleepdownschedule.model.VisualEffectMode
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class VisualEffectsPreferencesTest {
    @Test
    fun unsetModeDefaultsToGlowAndSavedValuesStayUnchanged() {
        val context = RuntimeEnvironment.getApplication()
        val raw = context.getSharedPreferences("visual_effects", Context.MODE_PRIVATE)
        raw.edit().clear().commit()

        val preferences = VisualEffectsPreferences.get(context)
        assertEquals(VisualEffectMode.ColorOsGlow, preferences.mode.value)

        preferences.select(VisualEffectMode.LiquidGlass)
        assertEquals(VisualEffectMode.LiquidGlass, preferences.mode.value)
        assertEquals("liquid_glass", raw.getString("mode", null))

        raw.edit().putString("mode", "coloros_glow").commit()
        assertEquals(VisualEffectMode.ColorOsGlow, preferences.mode.value)
    }
}
