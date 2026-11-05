package com.xiaomanjun.sleepdownschedule.feature.importing

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import com.xiaomanjun.sleepdownschedule.core.ui.settings.GlassMiuixSettingsTheme
import com.xiaomanjun.sleepdownschedule.defaultConfig
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import top.yukonga.miuix.kmp.basic.Scaffold

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w411dp-h891dp-port")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AiRuntimePickerAnimationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun actualPopupRetainsItsSourceThroughExitAndReleasesItWhenPaused() {
        val context = RuntimeEnvironment.getApplication()
        val settings = AiImportSettings(AiProviderProfile("popup-animation-fixture", "动画测试",
            AiProviderType.OpenAIChatCompatible, "http://127.0.0.1", defaultModel = "model-A"))
        AiImportSettingsStore.save(context, settings)
        val state = AiRuntimePickerState(AiImportSettingsStore.loadProvider(context, settings.profile.id))
        val config = defaultConfig()
        val anchorAlignment = mutableStateOf<Alignment>(Alignment.BottomEnd)
        compose.setContent {
            MaterialTheme {
                GlassMiuixSettingsTheme(config) {
                    Scaffold(Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = anchorAlignment.value) {
                            AiRuntimePicker(state, config, null, embedded = true)
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { state.open(); assertFalse(state.triggerTransferred) }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.runOnIdle { assertTrue(state.triggerTransferred) }
        val bottomAnchor = compose.onNodeWithText("model-A").fetchSemanticsNode().boundsInRoot
        assertTrue("A bottom anchor must expand upwards",
            compose.onNodeWithText("供应商").fetchSemanticsNode().boundsInRoot.top < bottomAnchor.top)
        compose.mainClock.autoAdvance = false
        compose.runOnIdle {
            state.commit(state.settings.copy(profile = state.settings.profile.copy(defaultModel = "a-longer-model-B")))
            state.dismiss()
            assertTrue(state.triggerTransferred)
            assertEquals("model-A", state.label)
        }
        compose.mainClock.advanceTimeBy(48)
        compose.runOnIdle { assertTrue(state.triggerTransferred); assertEquals("model-A", state.label) }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.onNodeWithText("a-longer-model-B").assertIsDisplayed()
        val firstSession = state.popupSession
        compose.runOnIdle { assertFalse(state.triggerTransferred); anchorAlignment.value = Alignment.TopStart }
        compose.waitForIdle()
        val topAnchor = compose.onNodeWithText("a-longer-model-B").fetchSemanticsNode().boundsInRoot
        compose.runOnIdle { state.open(); assertEquals(firstSession + 1, state.popupSession) }
        compose.waitForIdle()
        assertTrue("A moved top anchor must expand below its new position",
            compose.onNodeWithText("供应商").fetchSemanticsNode().boundsInRoot.top >= topAnchor.top)
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.runOnIdle { assertFalse(state.expanded); assertFalse(state.triggerTransferred) }
    }
}
