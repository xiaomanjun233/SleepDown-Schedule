package com.xiaomanjun.sleepdownschedule

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.xiaomanjun.sleepdownschedule.core.ui.settings.GlassMiuixSettingsTheme
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LocalNativeAlertRenderer
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LocalNativeDialogSurface
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.NativeAlertRequest
import com.xiaomanjun.sleepdownschedule.feature.schedule.picker.QuickScheduleDraft
import com.xiaomanjun.sleepdownschedule.feature.schedule.picker.QuickScheduleSettingsSheets
import com.xiaomanjun.sleepdownschedule.glass.GlassBackdropDomain
import com.xiaomanjun.sleepdownschedule.glass.glassBackdropProducer
import com.xiaomanjun.sleepdownschedule.glass.rememberGlassLayerBackdrop
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
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
class QuickScheduleSheetLifecycleTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun nativeSheetRetainsDraftUntilSaveAndWindowExitFinish() {
        val draft = mutableStateOf<QuickScheduleDraft?>(null)
        var window: NativeAlertRequest? = null
        var completeSave: (() -> Unit)? = null
        var saves = 0
        var dismissals = 0
        compose.setContent {
            MaterialTheme {
                GlassMiuixSettingsTheme(defaultConfig()) {
                    CompositionLocalProvider(LocalNativeAlertRenderer provides { request ->
                        SideEffect { window = request }
                        CompositionLocalProvider(LocalNativeDialogSurface provides true) {
                            request.messageContent?.invoke()
                        }
                        true
                    }) {
                        QuickScheduleSettingsSheets(draft.value, defaultConfig(), null,
                            onDraftChange = { draft.value = it }, onDismiss = { draft.value = null },
                            onDismissFinished = { dismissals++ },
                            onSave = { value, done ->
                                assertEquals(20, value.totalWeeks)
                                saves++
                                completeSave = done
                            }, onDetailedSettings = { _, _, _, _ -> })
                    }
                }
            }
        }
        repeat(2) { cycle ->
            compose.runOnIdle { draft.value = QuickScheduleDraft(1, 20, 1, true, false, "2026-09-01") }
            compose.onNodeWithText("课表设置").assertExists()
            compose.onNodeWithText("完成").performClick()
            compose.runOnIdle {
                assertTrue(window!!.visible)
                assertEquals(cycle + 1, saves)
                window!!.onDismissRequest()
                assertEquals("Outside/back while saving cannot save twice", cycle + 1, saves)
                completeSave!!()
            }
            compose.waitForIdle()
            compose.onNodeWithText("课表设置").assertExists()
            compose.runOnIdle {
                assertFalse(window!!.visible)
                assertEquals(cycle, dismissals)
                window!!.onDismissFinished!!()
            }
            compose.onNodeWithText("课表设置").assertDoesNotExist()
            assertEquals(cycle + 1, dismissals)
        }
    }

    @Test fun reopeningSameScheduleRestoresContentAndGeometry() = reopen(withGlass = false)

    @Test fun reopeningGlassSheetRestoresContentAndGeometry() = reopen(withGlass = true)

    private fun reopen(withGlass: Boolean) {
        val draft = mutableStateOf<QuickScheduleDraft?>(null)
        var dismissals = 0
        var saves = 0
        val pageScale = mutableStateOf(1f)
        val unrelatedDialog = mutableStateOf(false)
        compose.setContent {
            MaterialTheme {
                GlassMiuixSettingsTheme(defaultConfig()) {
                    val backdrop = rememberGlassLayerBackdrop(GlassBackdropDomain.Content, "sheet-test")
                    Scaffold(Modifier.fillMaxSize().testTag("sheet-root"),
                        underlayModifier = Modifier.fillMaxSize().graphicsLayer {
                            scaleX = pageScale.value
                            scaleY = pageScale.value
                        }.glassBackdropProducer(backdrop).background(Color.DarkGray),
                        contentWindowInsets = WindowInsets(0, 0, 0, 0), popupHost = {
                        top.yukonga.miuix.kmp.utils.MiuixPopupUtils.MiuixPopupHost()
                        QuickScheduleSettingsSheets(draft.value, defaultConfig(), backdrop.takeIf { withGlass },
                            onDraftChange = { draft.value = it },
                            onDismiss = { draft.value = null },
                            onDismissFinished = { dismissals++ },
                            onSave = { _, done -> saves++; done() },
                            onDetailedSettings = { _, _, _, _ -> })
                        top.yukonga.miuix.kmp.utils.MiuixPopupUtils.DialogLayout(visible = unrelatedDialog) {
                            androidx.compose.material3.Text("另一个弹窗")
                        }
                    }) {}
                }
            }
        }
        var originalTitle: Rect? = null
        var originalDetail: Rect? = null
        repeat(3) { cycle ->
            compose.runOnIdle {
                pageScale.value = if (cycle == 1) 0.8f else 1f
                draft.value = QuickScheduleDraft(1, 20, 1, true, false, "2026-09-01")
            }
            compose.waitForIdle()
            compose.onNodeWithText("总周数").assertExists()
            val title = compose.onNodeWithText("课表设置").fetchSemanticsNode().boundsInRoot
            val detail = compose.onNodeWithText("详细设置").fetchSemanticsNode().boundsInRoot
            val panel = compose.onNodeWithTag("quick_schedule_sheet").fetchSemanticsNode().boundsInRoot
            val root = compose.onNodeWithTag("sheet-root").fetchSemanticsNode().boundsInRoot
            assertTrue("Sheet stays within the host", panel.left >= root.left && panel.right <= root.right)
            assertTrue("Sheet stays above the host bottom", panel.bottom <= root.bottom)
            assertEquals("Balanced horizontal gaps", panel.left - root.left, root.right - panel.right, 1f)
            if (cycle == 0) { originalTitle = title; originalDetail = detail }
            assertEquals("Reopen $cycle title", originalTitle, title)
            assertEquals("Reopen $cycle bottom action", originalDetail, detail)
            compose.mainClock.autoAdvance = false
            compose.onNodeWithText("完成").performClick()
            compose.mainClock.advanceTimeByFrame()
            compose.mainClock.advanceTimeByFrame()
            // Closing must retain the same form until its exit animation has completed.
            compose.onNodeWithText("20").assertExists()
            compose.mainClock.autoAdvance = true
            compose.waitForIdle()
            compose.onNodeWithText("课表设置").assertDoesNotExist()
            assertEquals(cycle + 1, saves)
            assertEquals(cycle + 1, dismissals)
            // A separate dialog may use the shared registry before this sheet is reopened.
            compose.runOnIdle { unrelatedDialog.value = true }
            compose.waitForIdle()
            compose.onNodeWithText("另一个弹窗").assertExists()
            compose.runOnIdle { unrelatedDialog.value = false }
            compose.waitForIdle()
        }
    }
}
