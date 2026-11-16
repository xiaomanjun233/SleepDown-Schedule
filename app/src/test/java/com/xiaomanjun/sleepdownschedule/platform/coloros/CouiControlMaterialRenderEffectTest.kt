package com.xiaomanjun.sleepdownschedule.platform.coloros

import android.app.Application
import android.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.itos.coui.effect.CouiFloatingMaterial
import com.itos.coui.effect.couiControlMaterialEffect
import com.itos.coui.effect.couiControlMaterialEffectOrNull
import com.xiaomanjun.sleepdownschedule.feature.onboarding.VersionWelcomeScreen
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31, 32, 35], application = Application::class, qualifiers = "w360dp-h800dp-320dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CouiControlMaterialRenderEffectTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun transparentOverLayersAreSkippedSoIlluminatedMaterialBuildsCleanBlurEffect() {
        val illuminated = CouiFloatingMaterial(
            firstColor = Color.Transparent,
            firstBlendMode = BlendMode.SRC_OVER,
            secondColor = Color.Transparent,
            secondBlendMode = BlendMode.SRC_OVER,
            edgeAlpha = 0f,
            innerGlowAlpha = 0f,
            blurRadius = 30f,
        )
        assertNotNull(couiControlMaterialEffectOrNull(illuminated))
        assertNotNull(couiControlMaterialEffect(illuminated))
    }

    @Test
    fun emptyBlurAndNoOpTintReturnsNullInsteadOfDegenerateColorFilterEffect() {
        val empty = CouiFloatingMaterial(
            firstColor = Color.Transparent,
            firstBlendMode = BlendMode.SRC_OVER,
            secondColor = Color.White.copy(alpha = 0f),
            secondBlendMode = BlendMode.LUMINOSITY,
            edgeAlpha = 0f,
            innerGlowAlpha = 0f,
            blurRadius = 0f,
            sampleBrightness = 1f,
        )
        assertNull(couiControlMaterialEffectOrNull(empty))

        val dstOnly = empty.copy(firstColor = Color.Red, firstBlendMode = BlendMode.DST, secondBlendMode = BlendMode.DST)
        assertNull(couiControlMaterialEffectOrNull(dstOnly))
    }

    @Test
    fun nonTransparentTintOrExposureBuildsValidRenderEffectWithoutBlur() {
        val tinted = CouiFloatingMaterial(
            firstColor = Color(0x3F333333),
            firstBlendMode = BlendMode.OVERLAY,
            secondColor = Color.Transparent,
            secondBlendMode = BlendMode.SRC_OVER,
            edgeAlpha = 0f,
            innerGlowAlpha = 0f,
            blurRadius = 0f,
        )
        assertNotNull(couiControlMaterialEffectOrNull(tinted))

        val darkened = tinted.copy(
            firstColor = Color.Transparent,
            firstBlendMode = BlendMode.SRC_OVER,
            sampleBrightness = 0.7f,
        )
        assertNotNull(couiControlMaterialEffectOrNull(darkened))
    }

    @Test
    fun versionWelcomeActionButtonRendersPastRevealWithoutCrashing() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            VersionWelcomeScreen(
                opening = false,
                onLightBackground = {},
                onContinue = {},
            )
        }
        compose.mainClock.advanceTimeBy(4_000)
        compose.onNodeWithText("继续").assertIsDisplayed()
    }
}
