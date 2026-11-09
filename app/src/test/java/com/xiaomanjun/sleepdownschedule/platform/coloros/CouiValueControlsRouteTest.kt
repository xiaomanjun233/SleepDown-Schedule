package com.xiaomanjun.sleepdownschedule.platform.coloros

import android.app.Application
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.test.junit4.createComposeRule
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LocalNativeCapsuleButtonRenderer
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LocalNativeSliderRenderer
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LocalNativeSwitchRenderer
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class CouiValueControlsRouteTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun couiControlsInstallButtonSliderAndSwitchWithoutPrivateColorOs() {
        var button = false
        var slider = false
        var switch = false
        compose.setContent {
            ProvideCouiMaterials(couiControlsEnabled = true) {
                val buttonRenderer = LocalNativeCapsuleButtonRenderer.current
                val sliderRenderer = LocalNativeSliderRenderer.current
                val switchRenderer = LocalNativeSwitchRenderer.current
                SideEffect {
                    button = buttonRenderer != null
                    slider = sliderRenderer != null
                    switch = switchRenderer != null
                }
            }
        }
        compose.waitForIdle()
        assertTrue(button)
        assertTrue(slider)
        assertTrue(switch)
    }
}
