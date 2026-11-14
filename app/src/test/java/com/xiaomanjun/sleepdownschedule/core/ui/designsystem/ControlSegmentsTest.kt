package com.xiaomanjun.sleepdownschedule.core.ui.designsystem

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.xiaomanjun.sleepdownschedule.core.ui.material.LocalVisualEffects
import com.xiaomanjun.sleepdownschedule.core.ui.material.VisualEffectsState
import com.xiaomanjun.sleepdownschedule.glass.GlassBackdropDomain
import com.xiaomanjun.sleepdownschedule.glass.glassBackdropProducer
import com.xiaomanjun.sleepdownschedule.glass.rememberGlassLayerBackdrop
import com.xiaomanjun.sleepdownschedule.model.VisualEffectMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ControlSegmentsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun switchingMaterialKeepsLabelGeometryAndSelection() {
        val glow = mutableStateOf(false)
        val selected = mutableIntStateOf(0)
        var selections = 0
        compose.setContent {
            val mode = if (glow.value) VisualEffectMode.ColorOsGlow else VisualEffectMode.LiquidGlass
            CompositionLocalProvider(LocalVisualEffects provides VisualEffectsState(mode, mode, true)) {
                MaterialTheme {
                    val source = rememberGlassLayerBackdrop(GlassBackdropDomain.Content, "segments-test")
                    Box(Modifier.size(300.dp, 100.dp)) {
                        Box(Modifier.size(300.dp, 100.dp).glassBackdropProducer(source).background(Color.Blue))
                        ControlSegments(SegmentControlRequest(listOf("课程", "待办"), selected.intValue,
                            { selected.intValue = it; selections++ }, Modifier.width(280.dp), dark = false,
                            height = 40.dp, backdrop = source))
                    }
                }
            }
        }
        val bounds = compose.onNodeWithText("课程").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithText("待办").performClick().assertIsSelected()
        compose.runOnIdle { glow.value = true }
        compose.onNodeWithText("待办").assertIsSelected()
        assertEquals(bounds, compose.onNodeWithText("课程").fetchSemanticsNode().boundsInRoot)
        compose.runOnIdle { glow.value = false }
        compose.onNodeWithText("待办").assertIsSelected()
        compose.runOnIdle { assertEquals(1, selections) }
    }
}
