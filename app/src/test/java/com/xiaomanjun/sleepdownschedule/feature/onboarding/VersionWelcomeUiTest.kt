package com.xiaomanjun.sleepdownschedule.feature.onboarding

import android.app.Application
import android.content.Intent
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
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
        compose.mainClock.autoAdvance = false
        compose.setContent {
            MaterialTheme {
                VersionWelcomeScreen(remember { Animatable(0f) }, true, {}, {}, {})
            }
        }
        compose.mainClock.advanceTimeBy(1_300)
        val opening = compose.onRoot().captureToImage().toPixelMap()
        val calendarX = opening.width / 2
        val calendarY = (opening.height * 0.42f).toInt()
        assertTrue(opening[calendarX, calendarY].red > 0.75f)
        assertTrue(opening[0, calendarY].red < 0.08f)

        compose.mainClock.advanceTimeBy(7_000)
        compose.onNodeWithText("SleepDown课程表").assertIsDisplayed()
        compose.onNodeWithText("SleepDown 测试版").assertDoesNotExist()
        compose.onNodeWithText("已焕然一新").assertIsDisplayed()
        compose.onNodeWithText("继续").assertIsDisplayed()
    }

    @Test fun buttonMaskRevealsTheLiveSceneBeforeReleasingTheCover() {
        val progress = mutableFloatStateOf(0f)
        val home = Color(0xFF173566)
        val cover = Color(0xFFEEF2F7)
        val card = Color(0xFF35AD58)
        lateinit var button: Rect
        compose.setContent {
            button = with(LocalDensity.current) {
                Rect(100.dp.toPx(), 700.dp.toPx(), 260.dp.toPx(), 756.dp.toPx())
            }
            Box(Modifier.fillMaxSize().background(home)) {
                CompositionLocalProvider(LocalWelcomeCourseFlight provides progress) {
                    Box(Modifier.align(Alignment.Center).size(52.dp).welcomeCourseFlight(0.25f).background(card))
                }
                Box(Modifier.fillMaxSize().versionWelcomeAperture(progress) { button }.background(cover))
            }
        }
        val before = compose.onRoot().captureToImage().toPixelMap()
        assertEquals(cover, before[button.center.x.toInt(), button.center.y.toInt()])
        compose.runOnIdle { progress.floatValue = 0.42f }
        val middle = compose.onRoot().captureToImage().toPixelMap()
        assertEquals(home, middle[button.center.x.toInt(), button.center.y.toInt()])
        assertEquals(cover, middle[12, 12])
        compose.runOnIdle { progress.floatValue = 1f }
        val finalFrame = compose.onRoot().captureToImage().toPixelMap()
        assertEquals(home, finalFrame[12, 12])
        assertEquals(card, finalFrame[finalFrame.width / 2, finalFrame.height / 2])
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
