package com.xiaomanjun.sleepdownschedule.core.ui.designsystem

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ControlMaterialProviderTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun switchingToLiquidReleasesNativeConsumerAndRemovesItsCapabilities() {
        val native = mutableStateOf(true)
        var mounted = 0
        var disposed = 0
        val renderers = ControlMaterialRenderers(alert = {
            DisposableEffect(Unit) {
                mounted++
                onDispose { disposed++ }
            }
            true
        })
        compose.setContent {
            ProvideControlMaterials(if (native.value) renderers else ControlMaterialRenderers.LiquidGlass) {
                val renderer = LocalNativeAlertRenderer.current
                val courseMode = LocalNativeCourseCardMode.current
                renderer?.invoke(NativeAlertRequest("", "", emptyList(), false, {},
                    androidx.compose.ui.unit.Dp(200f), false, null))
                SideEffect {
                    if (!native.value) {
                        assertNull(renderer)
                        assertNull(courseMode)
                    }
                }
            }
        }
        compose.runOnIdle { assertEquals(1, mounted); native.value = false }
        compose.runOnIdle { assertEquals(1, disposed); native.value = true }
        compose.runOnIdle { assertEquals(2, mounted); assertEquals(1, disposed) }
    }
}
