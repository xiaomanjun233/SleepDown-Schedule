package com.xiaomanjun.sleepdownschedule.core.ui.controls

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ConicGlowProgressRingTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun lightFollowsTheArcAndFadesOutsideItsStroke() {
        val glow = mutableStateOf(false)
        compose.setContent {
            Box(
                Modifier.size(112.dp).background(Color(0xFF191919)).testTag("quota-ring"),
                contentAlignment = Alignment.Center
            ) {
                ConicGlowProgressRing(0.6f, Color(0xFFDED324), "", darkSurface = true,
                    ringSize = 84.dp, strokeWidth = 8.dp, glowEnabled = glow.value)
            }
        }

        val ring = compose.onNodeWithTag("quota-ring")
        val plain = ring.captureToImage().toPixelMap()
        compose.runOnIdle { glow.value = true }
        compose.mainClock.advanceTimeBy(400)
        val lit = ring.captureToImage().toPixelMap()

        // At 60% the head is at 54 degrees. Its trail bends clockwise, away from the gap.
        assertTrue(lit.atAngle(74, 5.5f).red > plain.atAngle(74, 5.5f).red + 0.005f)
        assertEquals(plain.atAngle(84, 11f), lit.atAngle(84, 11f))
        assertEquals(plain.atAngle(34, 5.5f), lit.atAngle(34, 5.5f))
    }

    private fun PixelMap.atAngle(degrees: Int, outsideStrokeCenter: Float): Color {
        val angle = Math.toRadians(degrees.toDouble())
        val radius = (38f + outsideStrokeCenter) * width / 112f
        val x = (width / 2f + cos(angle) * radius).roundToInt()
        val y = (height / 2f + sin(angle) * radius).roundToInt()
        return this[x, y]
    }
}
