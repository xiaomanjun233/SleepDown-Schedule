package com.xiaomanjun.sleepdownschedule.feature.home

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.xiaomanjun.sleepdownschedule.app.ui.HomeMode
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
class HomeTopBarScrollStateTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun retainedPagesRestoreTheirOwnOverlapAndScheduleSwitchClearsIt() {
        val mode = mutableStateOf(HomeMode.Week)
        val schedule = mutableIntStateOf(1)
        val changes = mutableListOf<Boolean>()
        lateinit var report: (HomeMode, Boolean) -> Unit
        compose.setContent {
            report = rememberHomeTopBarScrollReporter(mode.value, schedule.intValue) { changes += it }
        }
        compose.waitForIdle()
        assertEquals(false, changes.last())
        compose.runOnIdle { report(HomeMode.Week, true) }
        compose.waitForIdle()
        assertEquals(true, changes.last())
        compose.runOnIdle { mode.value = HomeMode.Day }
        compose.waitForIdle()
        assertEquals(false, changes.last())
        val dayChanges = changes.toList()
        compose.runOnIdle { report(HomeMode.Week, false); report(HomeMode.Week, true) }
        compose.waitForIdle()
        assertEquals("A retained background page cannot change the selected page's top bar", dayChanges, changes)
        compose.runOnIdle { mode.value = HomeMode.Week }
        compose.waitForIdle()
        assertEquals(true, changes.last())
        compose.runOnIdle { schedule.intValue = 2 }
        compose.waitForIdle()
        assertEquals(false, changes.last())
    }
}
