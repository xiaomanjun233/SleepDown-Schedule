package com.xiaomanjun.sleepdownschedule.core.ui.interaction

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.xiaomanjun.sleepdownschedule.CourseEntity
import com.xiaomanjun.sleepdownschedule.WeekParity
import com.xiaomanjun.sleepdownschedule.feature.course.editor.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w411dp-h891dp-port-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CoursePointerOwnershipTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun simultaneousFingersOpenNeitherCardAndASecondStationRequiresAnExplicitInterruption() {
        val gate = SinglePointerTapGate()
        val registry = CourseEditorSessions()
        val opens = mutableListOf<Long>()
        fun open(id: Long) {
            if (!gate.allowed) return
            val session = registry.select(id, null) ?: return
            val course = CourseEntity(id = id, name = "课程$id", teacher = null, location = null,
                weekday = 1, periods = listOf(1), weeks = listOf(1), weekParity = WeekParity.ALL,
                note = null, scheduleId = 1)
            session.retainedRequest = CourseEditorOverlayRequest(course, 1, null)
            session.request = session.retainedRequest
            session.motion.phase = CourseEditorOverlayPhase.Preparing
            session.motion.phase = CourseEditorOverlayPhase.Opening
            opens += id
        }
        compose.setContent {
            Box(Modifier.fillMaxSize().observeCoursePointers(gate).testTag("root")) {
                Box(Modifier.size(80.dp).clickable { open(1) })
                Box(Modifier.offset(x = 140.dp).size(80.dp).clickable { open(2) })
            }
        }
        val root = compose.onNodeWithTag("root")
        root.performTouchInput {
            down(0, Offset(30f, 30f))
            down(1, Offset(170f, 30f))
            advanceEventTime(40)
            up(0)
            up(1)
        }
        compose.runOnIdle { assertTrue(opens.isEmpty()) }
        root.performTouchInput { click(Offset(30f, 30f)) }
        compose.runOnIdle { assertEquals(listOf(1L), opens) }
        root.performTouchInput { click(Offset(170f, 30f)) }
        compose.runOnIdle { assertEquals(listOf(1L), opens) }
        val a = registry.active
        compose.runOnIdle {
            runBlocking { a.motion.progress.snapTo(0.5f) }
            a.request = null
            a.motion.phase = CourseEditorOverlayPhase.Closing
        }
        root.performTouchInput { click(Offset(170f, 30f)) }
        compose.runOnIdle {
            assertEquals(listOf(1L, 2L), opens)
            assertEquals(2, registry.sessions.size)
            assertEquals(CourseEditorOverlayPhase.Closing, a.motion.phase)
            assertEquals(0.5f, a.motion.progress.value, 0f)
        }
    }
}
