package com.xiaomanjun.sleepdownschedule.glass

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.xiaomanjun.sleepdownschedule.app.ui.LiquidControlToggle
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LiquidToggleInteractionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun loadedAppearanceAndPagerChangesUseCurrentCallback() {
        val loaded = mutableStateOf(false)
        val selectedWidget = mutableStateOf(0)
        val checked = mutableStateOf(false)
        val calls = mutableListOf<Pair<Int, Boolean>>()
        compose.setContent {
            MaterialTheme {
                val source = rememberGlassLayerBackdrop(GlassBackdropDomain.DialogBridge, "toggle-test")
                val widget = selectedWidget.value
                Box(Modifier.size(100.dp)) {
                    LiquidControlToggle(checked.value, { next -> calls += widget to next; checked.value = next },
                        source, enabled = loaded.value, modifier = Modifier.testTag("toggle"), compact = true)
                }
            }
        }
        compose.runOnIdle { loaded.value = true; checked.value = true }
        compose.waitForIdle()
        compose.onNodeWithTag("toggle").performTouchInput { click(center) }
        compose.runOnIdle { assertEquals(listOf(0 to false), calls) }

        compose.runOnIdle { selectedWidget.value = 1; checked.value = true }
        compose.waitForIdle()
        compose.onNodeWithTag("toggle").performTouchInput { click(center) }
        compose.runOnIdle { assertEquals(listOf(0 to false, 1 to false), calls) }

        compose.runOnIdle { loaded.value = false }
        compose.waitForIdle()
        compose.onNodeWithTag("toggle").performTouchInput { click(center) }
        compose.runOnIdle { assertEquals(2, calls.size) }
    }
}
