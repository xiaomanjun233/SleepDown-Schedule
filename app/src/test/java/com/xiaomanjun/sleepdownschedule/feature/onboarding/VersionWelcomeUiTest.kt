package com.xiaomanjun.sleepdownschedule.feature.onboarding

import android.app.Application
import android.content.Intent
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w360dp-h800dp-320dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class VersionWelcomeUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun welcomeUsesTheProductNameInTheDebugBuild() {
        var continued = false
        compose.mainClock.autoAdvance = false
        compose.setContent {
            VersionWelcomeScreen(opening = false, onLightBackground = {}, onContinue = { continued = true })
        }
        compose.mainClock.advanceTimeBy(500)
        val opening = compose.onRoot().captureToImage().toPixelMap()
        val centerX = opening.width / 2
        val centerY = opening.height / 2
        assertTrue(opening[8, centerY].red < 0.08f)
        assertTrue((centerX - 180..centerX + 180 step 4).any { x ->
            (centerY - 150..centerY + 150 step 4).any { y -> opening[x, y].red > 0.95f }
        })

        compose.mainClock.advanceTimeBy(1_374)
        val whiteFrame = compose.onRoot().captureToImage().toPixelMap()
        assertTrue(whiteFrame[4, 4].red > 0.95f)
        assertTrue(whiteFrame[whiteFrame.width - 5, whiteFrame.height - 5].red > 0.95f)

        compose.mainClock.advanceTimeBy(750)
        val curtain = compose.onRoot().captureToImage().toPixelMap()
        assertTrue(curtain[centerX, 4].red < 0.08f)
        assertTrue(curtain[centerX, centerY].red > 0.95f)

        compose.mainClock.advanceTimeBy(5_300)
        compose.onNodeWithText("SleepDown课程表").assertIsDisplayed()
        compose.onNodeWithText("SleepDown 测试版").assertDoesNotExist()
        compose.onNodeWithText("已焕然一新").assertIsDisplayed()
        compose.onNodeWithText("继续").assertIsDisplayed().performClick()
        compose.runOnIdle { assertTrue(continued) }
    }

    @Test fun firstLaunchGateUsesLauncherAndRemembersAcceptance() {
        val app = RuntimeEnvironment.getApplication()
        val launch = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        assertFalse(VersionWelcomeEntry.shouldShow(app, Intent(Intent.ACTION_VIEW)))
        assertTrue(VersionWelcomeEntry.shouldShow(app, launch))
        VersionWelcomeEntry.markSeen(app)
        assertFalse(VersionWelcomeEntry.shouldShow(app, launch))
    }
}
