package com.xiaomanjun.sleepdownschedule.feature.home

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Constraints
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class HomeSidebarLayoutTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun resizingTheMorphShellDoesNotRemeasureTheSidebarContents() {
        val frame = mutableIntStateOf(0)
        var measurements = 0
        compose.setContent {
            Box(Modifier.size(300.dp, 340.dp).layout { measurable, constraints ->
                // Match HomeSidebar: the spring is read by measurement, not composition.
                val progress = frame.intValue
                val child = measurable.measure(Constraints.fixed((300 - progress * 5).dp.roundToPx(),
                    (48 + progress * 20).dp.roundToPx()))
                layout(constraints.maxWidth, constraints.maxHeight) { child.placeRelative(0, 0) }
            }
                .fixedFloatingChromeContent(220.dp, 340.dp)) {
                Layout(content = {}) { _, constraints ->
                    measurements++
                    layout(constraints.maxWidth, constraints.maxHeight) {}
                }
            }
        }
        compose.waitForIdle()
        val initial = measurements
        repeat(12) { index ->
            compose.runOnIdle { frame.intValue = index + 1 }
            compose.waitForIdle()
        }
        assertEquals("Changing shell size must retain the list's measured layout", initial, measurements)
    }
}
