package com.xiaomanjun.sleepdownschedule.core.ui.designsystem

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.catalog.components.LiquidButton
import com.xiaomanjun.sleepdownschedule.platform.coloros.ProvideCouiMaterials
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
class ControlButtonTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun changingMaterialPreservesContentGeometryAndOneClickOwner() {
        val glow = mutableStateOf(false)
        val enabled = mutableStateOf(true)
        var mounted = 0
        var disposed = 0
        var clicks = 0
        compose.setContent {
            MaterialTheme {
                ProvideCouiMaterials(glow.value) {
                    LiquidButton({ clicks++ }, null, Modifier.width(180.dp).testTag("button"),
                        height = 44.dp, enabled = enabled.value) {
                        DisposableEffect(Unit) { mounted++; onDispose { disposed++ } }
                        Text("保存")
                    }
                }
            }
        }
        val initial = compose.onNodeWithTag("button").fetchSemanticsNode().boundsInRoot.size
        for (value in listOf(true, false, true)) {
            compose.runOnIdle { glow.value = value }
            assertEquals(initial, compose.onNodeWithTag("button").fetchSemanticsNode().boundsInRoot.size)
            compose.onNodeWithTag("button").performClick()
        }
        compose.runOnIdle {
            assertEquals(3, clicks)
            assertEquals(1, mounted)
            assertEquals(0, disposed)
            enabled.value = false
        }
        compose.onNodeWithTag("button").assertIsNotEnabled()
    }
}
