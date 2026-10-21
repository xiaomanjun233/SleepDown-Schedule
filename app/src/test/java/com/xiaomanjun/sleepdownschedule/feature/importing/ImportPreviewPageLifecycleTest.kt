package com.xiaomanjun.sleepdownschedule.feature.importing

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.xiaomanjun.sleepdownschedule.ImportDraft
import com.xiaomanjun.sleepdownschedule.defaultConfig
import com.xiaomanjun.sleepdownschedule.defaultPeriods
import com.xiaomanjun.sleepdownschedule.core.ui.settings.GlassMiuixSettingsTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import top.yukonga.miuix.kmp.basic.Scaffold

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w411dp-h891dp-port")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ImportPreviewPageLifecycleTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val config = defaultConfig()
    private val draft = ImportDraft(config, defaultPeriods(), emptyList())

    @Test fun createAndOverwriteReleaseTheRootPreviewBeforeShowingSetup() {
        val visible = mutableStateOf(false)
        val mounted = mutableStateOf(false)
        val completed = mutableStateOf(false)
        val actions = mutableListOf<Boolean>()
        compose.setContent {
            MaterialTheme {
                GlassMiuixSettingsTheme(config) {
                    Scaffold(Modifier.fillMaxSize()) {
                        if (completed.value && !mounted.value) Text("设置已打开")
                        ImportPreviewPage(draft, config, visible.value, false, null, {}, {},
                            onConfirm = { actions += it; completed.value = true; visible.value = false },
                            onContentMountedChanged = { mounted.value = it })
                    }
                }
            }
        }
        listOf("创建新课表" to true, "覆盖当前课表" to false).forEach { (label, create) ->
            compose.runOnIdle { completed.value = false; visible.value = true }
            compose.waitForIdle()
            compose.runOnIdle { assertTrue(mounted.value) }
            compose.onNodeWithText(label).performScrollTo().performClick()
            compose.waitForIdle()
            compose.runOnIdle { assertFalse(mounted.value); assertEquals(create, actions.last()) }
            compose.onNodeWithText("导入预览").assertDoesNotExist()
            compose.onNodeWithText("设置已打开").assertExists()
        }
        assertEquals(listOf(true, false), actions)
    }

    @Test fun removingTheCallerStillClosesRetainedRootContent() {
        val owner = mutableStateOf(true)
        val mounted = mutableStateOf(false)
        compose.setContent {
            MaterialTheme {
                GlassMiuixSettingsTheme(config) {
                    Scaffold(Modifier.fillMaxSize()) {
                        if (owner.value) ImportPreviewPage(draft, config, true, false, null, {}, {}, {},
                            onContentMountedChanged = { mounted.value = it })
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.runOnIdle { assertTrue(mounted.value); owner.value = false }
        compose.waitForIdle()
        compose.runOnIdle { assertFalse(mounted.value) }
        compose.onNodeWithText("导入预览").assertDoesNotExist()
    }
}
