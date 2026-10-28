package com.xiaomanjun.sleepdownschedule.core.ui.interaction

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w411dp-h891dp-port")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RetainedMorphLayerTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun originalStationReopensTheSameContentWhileTheReturningVisualIsFarAway() {
        val returning = mutableStateOf(true)
        var mounts = 0
        var reopenCount = 0
        var movingCardTaps = 0
        compose.setContent {
            Box(Modifier.fillMaxSize().testTag("root")) {
                RetainedMorphLayer(returning.value, zIndex = 100f,
                    returnAnchor = androidx.compose.ui.geometry.Rect(10f, 10f, 110f, 110f),
                    onReturnAnchorTap = { reopenCount++; returning.value = false }
                ) { modifier ->
                    DisposableEffect(Unit) { mounts++; onDispose {} }
                    Box(modifier.fillMaxSize()) {
                        Box(Modifier.offset(180.dp, 250.dp).size(80.dp).clickable { movingCardTaps++ })
                    }
                }
            }
        }
        repeat(8) {
            compose.runOnIdle { returning.value = true }
            // Touch the fixed, empty station, well away from the moving card.
            compose.onNodeWithTag("root").performTouchInput { click(androidx.compose.ui.geometry.Offset(30f, 30f)) }
            compose.runOnIdle { assertFalse(returning.value) }
        }
        compose.runOnIdle {
            assertEquals(8, reopenCount)
            assertEquals(0, movingCardTaps)
            assertEquals(1, mounts)
        }
    }

    @Test fun returnReopenReturnRetainsOneContentTreeAndLetsRealTouchesReachOtherCards() {
        val returning = mutableStateOf(false)
        var mounts = 0
        var disposals = 0
        var cardTaps = 0
        var formTaps = 0
        compose.setContent {
            Box(Modifier.fillMaxSize().testTag("root")) {
                Box(Modifier.size(120.dp).clickable { cardTaps++ })
                RetainedMorphLayer(returning.value, zIndex = 100f) { modifier ->
                    DisposableEffect(Unit) { mounts++; onDispose { disposals++ } }
                    Box(modifier.fillMaxSize().clickable { formTaps++ })
                }
            }
        }
        compose.onNodeWithTag("root").performTouchInput { click(androidx.compose.ui.geometry.Offset(30f, 30f)) }
        compose.runOnIdle { assertEquals(1, formTaps) }
        repeat(4) {
            compose.runOnIdle { returning.value = true }
            compose.onNodeWithTag("root").performTouchInput { click(androidx.compose.ui.geometry.Offset(30f, 30f)) }
            compose.runOnIdle { returning.value = false }
        }
        compose.runOnIdle {
            assertEquals(4, cardTaps)
            assertEquals(1, formTaps)
            assertEquals(1, mounts)
            assertEquals(0, disposals)
        }
    }
}
