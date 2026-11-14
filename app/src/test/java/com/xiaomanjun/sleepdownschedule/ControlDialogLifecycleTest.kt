package com.xiaomanjun.sleepdownschedule

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.CenteredDialogContentTransition
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LocalControlDialogSurface
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.SleepDownPickerDialog
import com.xiaomanjun.sleepdownschedule.core.ui.material.LocalVisualEffects
import com.xiaomanjun.sleepdownschedule.core.ui.material.VisualEffectsState
import com.xiaomanjun.sleepdownschedule.model.VisualEffectMode
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import com.itos.coui.menu.ProvideCouiMenuBackdrop

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ControlDialogLifecycleTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun nativePickerResizeCanReverseWithoutOvershootingEitherLayout() {
        val expanded = mutableStateOf(false)
        val mountedModes = mutableSetOf<Boolean>()
        compose.setContent {
            CompositionLocalProvider(LocalControlDialogSurface provides true) {
                CenteredDialogContentTransition(expanded.value, Modifier.testTag("picker-size")) { large ->
                    DisposableEffect(large) {
                        mountedModes.add(large)
                        onDispose { mountedModes.remove(large) }
                    }
                    Box(Modifier.width(240.dp).height(if (large) 320.dp else 120.dp))
                }
            }
        }
        val collapsedHeight = compose.onNodeWithTag("picker-size").fetchSemanticsNode().boundsInRoot.height
        compose.runOnIdle { expanded.value = true }
        val expandedHeight = compose.onNodeWithTag("picker-size").fetchSemanticsNode().boundsInRoot.height
        assertTrue(expandedHeight > collapsedHeight)
        compose.mainClock.autoAdvance = false
        repeat(4) { cycle ->
            compose.runOnIdle { expanded.value = cycle % 2 != 0 }
            repeat(5) {
                compose.mainClock.advanceTimeBy(16)
                val height = compose.onNodeWithTag("picker-size").fetchSemanticsNode().boundsInRoot.height
                assertTrue("Native resize must stay inside both layouts: $height",
                    height in collapsedHeight..expandedHeight)
                compose.runOnIdle { assertEquals("Outgoing picker must not overlap the new mode",
                    1, mountedModes.size) }
            }
        }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(setOf(expanded.value), mountedModes) }
        assertEquals(expandedHeight,
            compose.onNodeWithTag("picker-size").fetchSemanticsNode().boundsInRoot.height, 1f)
    }

    @Test @Config(sdk = [26])
    fun sharedAlertRemainsUsableWithoutGpuEffects() {
        var actions = 0
        compose.setContent {
            MaterialTheme {
                com.xiaomanjun.sleepdownschedule.platform.coloros.ProvideCouiMaterials(false) {
                    com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LiquidAlertDialog(
                        title = "兼容窗口", message = "内容",
                        actions = listOf(com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LiquidAlertAction(
                            "完成", com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LiquidAlertActionStyle.Primary
                        ) { actions++ }),
                        config = defaultConfig(), onDismissRequest = {})
                }
            }
        }
        compose.onNodeWithText("完成").performClick()
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(1, actions) }
    }

    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun materialSwitchKeepsTheSameWindowAndDraftUntilExitCompletes() = verifyMaterialSwitch(withSource = true)

    @Test fun missingSourceKeepsTheSameWindowAndDraft() = verifyMaterialSwitch(withSource = false)

    private fun verifyMaterialSwitch(withSource: Boolean) {
        val show = mutableStateOf(false)
        val glow = mutableStateOf(false)
        var windows = 0
        var disposed = 0
        var completions = 0
        var currentView: android.view.View? = null
        compose.setContent {
            val mode = if (glow.value) VisualEffectMode.ColorOsGlow else VisualEffectMode.LiquidGlass
            CompositionLocalProvider(LocalVisualEffects provides VisualEffectsState(mode, mode, true)) {
                MaterialTheme {
                    com.xiaomanjun.sleepdownschedule.platform.coloros.ProvideCouiMaterials(glow.value) {
                        val dialog: @androidx.compose.runtime.Composable () -> Unit = {
                            SleepDownPickerDialog(show.value, "选择日期", { show.value = false }, defaultConfig(),
                                onDismissFinished = { completions++ },
                                titleAction = { Text("标题操作") }, bottomActions = { Text("完成操作") }) {
                                val draft = androidx.compose.runtime.remember { mutableStateOf("原稿") }
                                val view = androidx.compose.ui.platform.LocalView.current
                                SideEffect { currentView = view }
                                DisposableEffect(Unit) {
                                    windows++
                                    onDispose { disposed++ }
                                }
                                androidx.compose.foundation.text.BasicTextField(draft.value, { draft.value = it },
                                    Modifier.testTag("draft"))
                            }
                        }
                        if (withSource) {
                            ProvideCouiMenuBackdrop(isolateContent = false, recordWhenIdle = false) { source ->
                                Box(Modifier.fillMaxSize().then(source))
                                dialog()
                            }
                        } else dialog()
                    }
                }
            }
        }
        compose.runOnIdle { assertEquals(0, windows); show.value = true }
        compose.onNodeWithText("选择日期").assertExists()
        val window = currentView
        compose.onNodeWithTag("draft").performTextReplacement("未保存草稿")
        compose.runOnIdle { glow.value = true }
        compose.onNodeWithTag("draft").assertTextEquals("未保存草稿")
        compose.runOnIdle { assertSame(window, currentView); assertEquals(1, windows); assertEquals(0, disposed) }
        compose.runOnIdle { glow.value = false }
        compose.onNodeWithTag("draft").assertTextEquals("未保存草稿")
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { show.value = false }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle { assertEquals(0, completions); assertEquals(0, disposed) }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.onNodeWithTag("draft").assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, completions); assertEquals(1, disposed); show.value = true }
        compose.onNodeWithTag("draft").assertTextEquals("原稿")
        compose.runOnIdle { assertEquals(2, windows) }
    }
}
