package com.xiaomanjun.sleepdownschedule.platform.coloros

import android.app.Application
import com.itos.coui.effect.SpotlightPreset
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class CouiSpotlightCompatibilityTest {
    @Test fun loadsPresetsWithoutPlatformBlendModes() {
        assertEquals(SpotlightPreset.OpaqueSmall1, SpotlightPreset.valueOf("OpaqueSmall1"))
    }
}
