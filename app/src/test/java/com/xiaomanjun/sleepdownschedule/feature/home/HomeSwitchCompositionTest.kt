package com.xiaomanjun.sleepdownschedule.feature.home

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
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
class HomeSwitchCompositionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun denseCardsDoNotRecomposeWhenThePageStartsOrFinishesMoving() {
        val secondary = mutableStateOf(false)
        val compositions = IntArray(20)
        compose.setContent {
            MaterialTheme {
                val motion = rememberHomeSwitchMotion(secondary.value, "dense-test", renderedCardCount = { 20 })
                HomeSwitchPane(motion, false, Modifier.size(320.dp, 480.dp), retainContent = true) {
                    repeat(compositions.size) { DenseCard(it, compositions) }
                }
            }
        }
        // The retained pane intentionally warms after 200 ms. Measure the same warmed path
        // used on-device, rather than counting a cold pane's first legitimate remount.
        compose.mainClock.advanceTimeBy(300)
        compose.waitForIdle()
        val initial = compositions.toList()
        compose.runOnIdle { secondary.value = true }
        compose.waitForIdle()
        compose.runOnIdle { secondary.value = false }
        compose.waitForIdle()
        assertEquals("A page transform must not invalidate every unchanged card", initial, compositions.toList())
    }

    @Composable private fun DenseCard(index: Int, compositions: IntArray) {
        Box(Modifier.size(40.dp).homeSwitchGroup())
        SideEffect { compositions[index]++ }
    }
}
