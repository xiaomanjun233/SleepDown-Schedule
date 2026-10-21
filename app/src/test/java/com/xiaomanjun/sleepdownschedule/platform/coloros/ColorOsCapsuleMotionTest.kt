package com.xiaomanjun.sleepdownschedule.platform.coloros

import android.app.Application
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.NativeCapsuleButtonRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ColorOsCapsuleMotionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun replacementButtonIsAttachedAndReceivesClicksInBothDirections() {
        var firstClicks = 0
        var secondClicks = 0
        val first = TextView(compose.activity).apply { setOnClickListener { firstClicks++ } }
        val second = TextView(compose.activity).apply { setOnClickListener { secondClicks++ } }
        val selected = mutableStateOf(false)
        val motion = ColorOsCapsuleMotion()
        compose.setContent {
            NativeCapsuleButtonLayout(
                NativeCapsuleButtonRequest({}, Modifier.width(240.dp), 48.dp,
                    PaddingValues(horizontal = 16.dp), dark = false),
                if (selected.value) second else first, motion, update = {}
            ) { BasicText("Switch mode") }
        }
        compose.runOnIdle {
            assertTrue(first.isAttachedToWindow)
            first.performClick()
            selected.value = true
        }
        compose.runOnIdle {
            assertNull(first.parent)
            assertTrue(second.isAttachedToWindow)
            second.performClick()
            selected.value = false
        }
        compose.runOnIdle {
            assertNull(second.parent)
            assertTrue(first.isAttachedToWindow)
            first.performClick()
            assertEquals(2, firstClicks)
            assertEquals(1, secondClicks)
        }
    }

    @Test fun contentRemainsLiveAndFollowsNativeMotionWithoutRecomposition() {
        val motion = ColorOsCapsuleMotion()
        val label = mutableStateOf("Confirm")
        var compositions = 0
        lateinit var textCoordinates: LayoutCoordinates
        val button = TextView(compose.activity)
        compose.setContent {
            NativeCapsuleButtonLayout(
                NativeCapsuleButtonRequest({}, Modifier.width(240.dp).testTag("capsule"), 48.dp,
                    PaddingValues(horizontal = 16.dp), dark = false),
                button, motion, update = {}
            ) {
                SideEffect { compositions++ }
                BasicText(label.value, style = TextStyle(color = Color.Magenta, fontSize = 18.sp),
                    modifier = Modifier.onGloballyPositioned { textCoordinates = it })
            }
        }
        compose.onNodeWithText("Confirm").assertIsDisplayed()
        compose.waitForIdle()
        val initialCount = compositions
        val initialBounds = textCoordinates.boundsInRoot()
        compose.runOnIdle { motion.update(12f, 5f, 0.9f, 0.9f) }
        compose.waitForIdle()
        assertEquals(initialCount, compositions)
        // A graphics-layer transform does not relayout the text. Query current coordinates,
        // rather than expecting onGloballyPositioned to emit another layout callback.
        val textBounds = textCoordinates.boundsInRoot()
        assertEquals(initialBounds.center.x + 12f, textBounds.center.x, 0.6f)
        assertEquals(initialBounds.center.y + 5f, textBounds.center.y, 0.6f)
        assertEquals(initialBounds.width * 0.9f, textBounds.width, 0.6f)
        compose.runOnIdle { label.value = "Saved" }
        compose.onNodeWithText("Saved").assertIsDisplayed()
        assertTrue(compositions > initialCount)
    }
}
