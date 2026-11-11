package com.xiaomanjun.sleepdownschedule.feature.home

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.xiaomanjun.sleepdownschedule.feature.home.week.*
import com.xiaomanjun.sleepdownschedule.model.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WeekEditCompositionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun draggingAndLiftSpringsDoNotRecomposeTheTimetableOwner() {
        var ownerCompositions = 0
        lateinit var motion: WeekEditOverlayController
        val config = defaultConfig()
        val course = CourseEntity(1, "课程", null, null, 2, listOf(2, 3), listOf(1), WeekParity.ALL, null)
        val bounds = mutableStateOf<Rect?>(Rect(0f, 0f, 320f, 640f))
        compose.setContent {
            val scope = rememberCoroutineScope()
            motion = remember { WeekEditOverlayController(scope, ScrollState(0)) }
            MaterialTheme {
                Box(Modifier.size(320.dp, 640.dp)) {
                    SideEffect { ownerCompositions++ }
                    WeekEditOverlayHost(motion, bounds, null, config)
                }
            }
        }
        compose.waitForIdle()
        val initial = ownerCompositions
        compose.runOnIdle {
            motion.start(WeekEditOverlayRequest(WeekEditOverlayMode.Move, course,
                Rect(65f, 150f, 115f, 250f), 2, 2, 2, 10, 52f, 50f,
                (1..12).toList(), listOf(course), 1, 7, Offset(85f, 190f)))
        }
        compose.mainClock.advanceTimeBy(500)
        repeat(12) {
            compose.runOnIdle { motion.drag(Offset(3f, 2f), 640f, 48f, 4f) }
            compose.mainClock.advanceTimeBy(16)
        }
        assertEquals(initial, ownerCompositions)
        assertEquals(36f, motion.offsetX, .001f)
        assertEquals(24f, motion.offsetY, .001f)
        compose.runOnIdle { motion.cancelGesture() }
        compose.mainClock.advanceTimeBy(2000)
        assertNull(motion.request)
        assertEquals(initial, ownerCompositions)
    }
}
