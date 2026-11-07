package com.xiaomanjun.sleepdownschedule.core.ui.settings

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.*
import com.itos.coui.menu.ProvideCouiMenuBackdrop
import com.itos.coui.menu.rememberCouiWindowBackdrop
import com.itos.coui.segmentbutton.CouiSegmentedControl
import com.itos.coui.diagnostic.CouiRenderDiagnostics
import com.kyant.backdrop.Backdrop
import com.xiaomanjun.sleepdownschedule.defaultConfig
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.*
import com.xiaomanjun.sleepdownschedule.platform.coloros.CouiCapsuleButtonRenderer
import com.xiaomanjun.sleepdownschedule.platform.coloros.CouiPickerDialog
import com.xiaomanjun.sleepdownschedule.platform.coloros.ProvideColorOsMaterials
import com.xiaomanjun.sleepdownschedule.feature.schedule.manager.ScheduleNameDialog
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-420dpi", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CouiMigrationUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun badgeActionWrapsContentStaysAtTheBottomAndConsumesItsSuppliedBackdrop() {
        var samples = 0
        val dialogSurface = mutableStateOf(false)
        var primary = Color.Unspecified
        val accent = Color(0xFF777777)
        val backdrop = object : Backdrop {
            override val isCoordinatesDependent = false
            override fun DrawScope.drawBackdrop(density: Density, coordinates: LayoutCoordinates?,
                layerBlock: (GraphicsLayerScope.() -> Unit)?) { samples++; drawRect(Color(0xFF104050)) }
        }
        var pixelsPerDp = 1f
        compose.setContent {
            pixelsPerDp = LocalDensity.current.density
            MaterialTheme(colorScheme = darkColorScheme(primary = accent)) {
                ProvideControlMaterials(ControlMaterialRenderers(button = { request, content ->
                    primary = request.primaryColor
                    CouiCapsuleButtonRenderer(request, content)
                })) {
                    CompositionLocalProvider(LocalNativeDialogSurface provides dialogSurface.value) {
                    Box(Modifier.fillMaxSize().testTag("page")) {
                        SettingsActionButton("测试实时活动", backdrop, {}, badgeText = "实验功能",
                            modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 16.dp, vertical = 18.dp).fillMaxWidth())
                    }
                    }
                }
            }
        }
        val button = compose.onNode(hasText("测试实时活动") and hasClickAction()).fetchSemanticsNode().boundsInRoot
        val page = compose.onNodeWithTag("page").fetchSemanticsNode().boundsInRoot
        assertTrue("Badge content must not expand to the window height", button.height < 100 * pixelsPerDp)
        assertTrue("Floating action must stay near the page bottom", button.bottom > page.bottom - 70 * pixelsPerDp)
        val pixels = compose.onRoot().captureToImage().asAndroidBitmap()
        compose.runOnIdle { assertEquals(accent, primary); assertTrue("Caller backdrop must be sampled", samples > 0) }
        com.xiaomanjun.sleepdownschedule.feature.settings.writeSettingsVisualArtifact(
            "coui-notification-floating-button.png", pixels)
        compose.runOnIdle { dialogSurface.value = true; samples = 0 }
        compose.onRoot().captureToImage()
        compose.runOnIdle { assertEquals("Dialog buttons must not sample a stale page backdrop", 0, samples) }
    }

    @Test fun darkCardRolesAreOpaqueBeforeConsumersAdjustTheirAlpha() {
        compose.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                CouiSettingsTheme(defaultConfig().copy(darkMode = true, followSystemDarkMode = false)) {
                    val surface = MaterialTheme.colorScheme.surfaceContainerHigh
                    SideEffect {
                        assertEquals(1f, surface.alpha, .001f)
                        assertTrue("Dark cards must not become translucent white", surface.copy(alpha = .86f).luminance() < .15f)
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    @Test fun nestedScaffoldsInTheSameWindowReuseTheCompleteWindowSource() {
        compose.setContent {
            ProvideCouiMenuBackdrop { outerModifier ->
                val outer = rememberCouiWindowBackdrop()
                Box(Modifier.fillMaxSize().then(outerModifier)) {
                    ProvideCouiMenuBackdrop { innerModifier ->
                        val inner = rememberCouiWindowBackdrop()
                        SideEffect { assertSame(outer, inner); assertEquals(Modifier, innerModifier) }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    @Test fun idleWindowSourceDoesNotRecordThePageForUnrelatedControlAnimations() {
        val show = mutableStateOf(false)
        CouiRenderDiagnostics.enable { }
        try {
            compose.setContent {
                MaterialTheme {
                    ProvideCouiMenuBackdrop(isolateContent = false, recordWhenIdle = false) { source ->
                        Box(Modifier.fillMaxSize().testTag("idle-page").then(source)) {
                            CouiPickerDialog(PickerDialogRequest(show.value, "选择", false,
                                { show.value = false }, null, null, null, null, false) {
                                androidx.compose.material3.Text("内容")
                            })
                        }
                    }
                }
            }
            compose.onNodeWithTag("idle-page").captureToImage()
            var sourceId = ""
            compose.runOnIdle {
                val source = CouiRenderDiagnostics.snapshot().getJSONArray("sources").getJSONObject(0)
                sourceId = source.getString("id")
                assertEquals(0, source.getInt("recordCallbackCount"))
            }
            compose.runOnIdle { show.value = true }
            compose.onNodeWithText("内容").assertExists()
            compose.onNodeWithTag("idle-page").captureToImage()
            compose.runOnIdle {
                val sources = CouiRenderDiagnostics.snapshot().getJSONArray("sources")
                val source = (0 until sources.length()).map { sources.getJSONObject(it) }.first { it.getString("id") == sourceId }
                assertTrue(source.getInt("recordCallbackCount") > 0)
                assertTrue(source.getInt("activeConsumers") > 0)
                assertEquals(androidx.compose.ui.graphics.layer.CompositingStrategy.Auto.toString(),
                    source.getString("compositingStrategy"))
            }
            compose.runOnIdle { show.value = false }
            compose.waitForIdle()
            compose.runOnIdle {
                val source = CouiRenderDiagnostics.snapshot().getJSONArray("sources").getJSONObject(0)
                assertEquals(0, source.getInt("activeConsumers"))
            }
        } finally { CouiRenderDiagnostics.disable() }
    }

    @Test fun dialogMotionDoesNotRewriteWindowAttributesOnEveryAnimationFrame() {
        var windowUpdates = 0
        val show = mutableStateOf(true)
        CouiRenderDiagnostics.enable { if (it.endsWith("dialog.window.configure")) windowUpdates++ }
        try {
            compose.setContent {
                MaterialTheme {
                    ProvideCouiMenuBackdrop(isolateContent = false, recordWhenIdle = false) { source ->
                        Box(Modifier.fillMaxSize().then(source)) {
                            CouiPickerDialog(PickerDialogRequest(show.value, "选择", false,
                                { show.value = false }, null, null, null, null, false) {
                                androidx.compose.material3.Text("动画中的内容")
                            })
                        }
                    }
                }
            }
            compose.onNodeWithText("动画中的内容").assertExists()
            compose.mainClock.autoAdvance = false
            compose.runOnIdle { show.value = false }
            // 先让可见性切换的 owner / Dialog 子组合提交一次窗口策略，再观察运动帧。
            repeat(3) { compose.mainClock.advanceTimeByFrame() }
            compose.waitForIdle()
            compose.runOnIdle { assertTrue(windowUpdates > 0) }
            fun windowUpdateFrames(): Int {
                var frames = 0
                repeat(6) {
                    var before = 0
                    compose.runOnIdle { before = windowUpdates }
                    compose.mainClock.advanceTimeByFrame()
                    compose.runOnIdle { if (windowUpdates != before) frames++ }
                }
                return frames
            }
            // owner与Dialog子组合可能再交接一次策略，不能随每个动画帧重复写窗口。
            assertTrue("Closing must not update Window on every frame", windowUpdateFrames() <= 1)
            compose.runOnIdle { show.value = true }
            repeat(3) { compose.mainClock.advanceTimeByFrame() }
            compose.waitForIdle()
            assertTrue("Reopening must not update Window on every frame", windowUpdateFrames() <= 1)
            compose.mainClock.autoAdvance = true
            compose.waitForIdle()
        } finally { CouiRenderDiagnostics.disable() }
    }

    @Test fun standardTimeDialogUsesDemoColumnWidthsAndActionHeight() {
        var pixelsPerDp = 1f
        compose.setContent {
            pixelsPerDp = LocalDensity.current.density
            MaterialTheme {
                CompositionLocalProvider(LocalVisualEffects provides VisualEffectsState(
                    effectiveMode = VisualEffectMode.ColorOsGlow, couiKitAvailable = true)) {
                    ProvideCouiMenuBackdrop { source ->
                        Box(Modifier.fillMaxSize().then(source)) {
                            CouiPickerDialog(PickerDialogRequest(true, "选择时间", false, {}, null, null,
                                listOf(LiquidAlertAction("取消", LiquidAlertActionStyle.Cancel) {},
                                    LiquidAlertAction("确定", LiquidAlertActionStyle.Primary) {}), null, false) {
                                SettingsTimePickerContent(450, 0..1439) { }
                            })
                        }
                    }
                }
            }
        }
        val hour = compose.onNodeWithContentDescription("小时").fetchSemanticsNode().boundsInRoot
        val minute = compose.onNodeWithContentDescription("分钟").fetchSemanticsNode().boundsInRoot
        val confirm = compose.onNode(hasText("确定") and hasClickAction()).fetchSemanticsNode().boundsInRoot
        assertEquals(62 * pixelsPerDp, hour.width, 2f)
        assertEquals(62 * pixelsPerDp, minute.center.x - hour.center.x, 2f)
        assertEquals(135 * pixelsPerDp, hour.height, 2f)
        assertEquals(44 * pixelsPerDp, confirm.height, 2f)
        com.xiaomanjun.sleepdownschedule.feature.settings.writeSettingsVisualArtifact(
            "coui-demo-time-dialog.png", compose.onNode(isDialog()).captureToImage().asAndroidBitmap())
    }

    @Test fun couiQuickSettingsUsesTheStandardWindowAndCanOpenANestedCalendar() {
        val backdrop = object : Backdrop {
            override val isCoordinatesDependent = false
            override fun DrawScope.drawBackdrop(density: Density, coordinates: LayoutCoordinates?,
                layerBlock: (GraphicsLayerScope.() -> Unit)?) { drawRect(Color(0xFF104050)) }
        }
        val draft = mutableStateOf<com.xiaomanjun.sleepdownschedule.feature.schedule.picker.QuickScheduleDraft?>(
            com.xiaomanjun.sleepdownschedule.feature.schedule.picker.QuickScheduleDraft(1, 20, 1, true, false, "2026-09-01"))
        var saves = 0
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalVisualEffects provides VisualEffectsState(
                    effectiveMode = VisualEffectMode.ColorOsGlow, couiKitAvailable = true)) {
                    ProvideColorOsMaterials(enabled = false, couiControlsEnabled = true) {
                        ProvideCouiMenuBackdrop(isolateContent = false, recordWhenIdle = false) { source ->
                            Box(Modifier.fillMaxSize().then(source)) {
                                com.xiaomanjun.sleepdownschedule.feature.schedule.picker.QuickScheduleSettingsSheets(
                                    draft.value, defaultConfig(), backdrop, { draft.value = it }, { draft.value = null }, {},
                                    { _, done -> saves++; done() }, suppressDetailedButton = false,
                                    onDetailedSettings = { _, _, _, _ -> })
                            }
                        }
                    }
                }
            }
        }
        compose.onNodeWithText("课表设置").assertExists()
        val detail = compose.onNode(hasText("详细设置") and hasClickAction()).fetchSemanticsNode().boundsInRoot
        val cancel = compose.onNode(hasText("取消") and hasClickAction()).fetchSemanticsNode().boundsInRoot
        val done = compose.onNode(hasText("完成") and hasClickAction()).fetchSemanticsNode().boundsInRoot
        assertEquals(cancel.left, detail.left, 2f)
        assertEquals(done.right, detail.right, 2f)
        assertTrue(detail.top > done.bottom)
        com.xiaomanjun.sleepdownschedule.feature.settings.writeSettingsVisualArtifact(
            "coui-demo-quick-settings.png", compose.onNode(isDialog()).captureToImage().asAndroidBitmap())
        compose.onNodeWithText("学期开始日期").performClick()
        compose.onNodeWithText("选择日期").assertDoesNotExist()
        compose.onAllNodes(isDialog()).assertCountEquals(2)
        compose.onAllNodesWithText("取消").onLast().performClick()
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(0, saves) }
        compose.onNodeWithText("完成").performClick()
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(1, saves) }
    }

    @Test fun rtlSegmentIndicatorFollowsSelectionAndCancelledDragDoesNotCommit() {
        val selected = mutableIntStateOf(0)
        val commits = mutableListOf<Int>()
        var pixelsPerDp = 1f
        compose.setContent {
            pixelsPerDp = LocalDensity.current.density
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                CouiSegmentedControl(
                    modifier = Modifier.width(300.dp).background(Color.Black).testTag("segment"),
                    labels = listOf("日", "周"), selectedIndex = selected.intValue,
                    onSelected = { commits += it; selected.intValue = it },
                    dark = false, containerSurfaceEnabled = false, commitOnRelease = true)
            }
        }
        val segment = compose.onNodeWithTag("segment")
        val bounds = segment.fetchSemanticsNode().boundsInRoot
        val first = compose.onNodeWithText("日").fetchSemanticsNode().boundsInRoot.center - bounds.topLeft
        val second = compose.onNodeWithText("周").fetchSemanticsNode().boundsInRoot.center - bounds.topLeft
        assertTrue("RTL first tab must be on the right", first.x > second.x)
        val pixels = segment.captureToImage().toPixelMap()
        val sampleY = (6 * pixelsPerDp).toInt()
        assertTrue("Indicator must stay on the selected RTL tab",
            pixels[first.x.toInt(), sampleY].luminance() > pixels[second.x.toInt(), sampleY].luminance() + .2f)
        segment.performTouchInput { down(first); moveTo(second) }
        compose.runOnIdle { assertTrue(commits.isEmpty()) }
        segment.performTouchInput { cancel() }
        compose.runOnIdle { assertTrue(commits.isEmpty()) }
        segment.performTouchInput {
            down(first)
            moveTo(first + androidx.compose.ui.geometry.Offset(0f, 80 * pixelsPerDp))
            moveTo(second)
            up()
        }
        compose.runOnIdle { assertTrue("Vertical handoff must not commit a later horizontal preview", commits.isEmpty()) }
        segment.performTouchInput { down(first); moveTo(second); up() }
        compose.runOnIdle { assertEquals(listOf(1), commits) }
    }

    @Test fun segmentDragCanStartFromTheMovingIndicatorBeforeItReachesTheSelectedTab() {
        val selected = mutableIntStateOf(0)
        compose.setContent {
            MaterialTheme {
                CouiSegmentedControl(Modifier.width(300.dp).testTag("moving-segment"),
                    listOf("短", "中间", "较长标签"), selected.intValue, { selected.intValue = it },
                    dark = false, containerSurfaceEnabled = false)
            }
        }
        val segment = compose.onNodeWithTag("moving-segment")
        val origin = segment.fetchSemanticsNode().boundsInRoot.topLeft
        val first = compose.onNodeWithText("短").fetchSemanticsNode().boundsInRoot.center - origin
        val middle = compose.onNodeWithText("中间").fetchSemanticsNode().boundsInRoot.center - origin
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { selected.intValue = 2 }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        segment.performTouchInput { down(first); moveTo(middle) }
        compose.runOnIdle { assertEquals("Moving indicator remains a valid drag origin", 1, selected.intValue) }
        segment.performTouchInput { up() }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
    }

    @Test fun dateCalendarClampsLeapDayAndBrowsingDoesNotCommitADate() {
        var changes = 0
        val year = mutableIntStateOf(2024)
        compose.setContent {
            CompositionLocalProvider(LocalVisualEffects provides VisualEffectsState(
                effectiveMode = VisualEffectMode.ColorOsGlow, couiKitAvailable = true)) {
                MaterialTheme {
                    SettingsDatePickerContent(year.intValue, 2, 31,
                        { changes++ }, { changes++ }, { changes++ })
                }
            }
        }
        compose.onNode(hasText("29") and isSelected()).assertExists()
        compose.runOnIdle { year.intValue = 2023 }
        compose.onNode(hasText("28") and isSelected()).assertExists()
        val next = org.robolectric.RuntimeEnvironment.getApplication().getString(
            com.itos.coui.R.string.coui_picker_calendar_next_month)
        compose.onNodeWithContentDescription(next).performClick()
        compose.runOnIdle { assertEquals(0, changes) }
    }

    @Test fun nameDraftSurvivesBothModeChangesWithoutSubmitting() {
        val glow = mutableStateOf(true)
        val submissions = mutableListOf<String>()
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalVisualEffects provides VisualEffectsState(
                    effectiveMode = if (glow.value) VisualEffectMode.ColorOsGlow else VisualEffectMode.LiquidGlass,
                    couiKitAvailable = true)) {
                    ProvideColorOsMaterials(enabled = false, couiControlsEnabled = glow.value) {
                        ProvideCouiMenuBackdrop { source ->
                            Box(Modifier.fillMaxSize().then(source)) {
                                ScheduleNameDialog("命名", "原名", null, defaultConfig(), submissions::add, {})
                            }
                        }
                    }
                }
            }
        }
        compose.onNode(hasSetTextAction()).performTextReplacement("保留的草稿")
        compose.waitForIdle()
        compose.runOnIdle { glow.value = false }
        compose.onNode(hasSetTextAction()).assertTextContains("保留的草稿")
        compose.onNode(hasSetTextAction()).performTextReplacement("液态编辑")
        compose.runOnIdle { glow.value = true }
        compose.onNode(hasSetTextAction()).assertTextContains("液态编辑")
        compose.runOnIdle { assertTrue(submissions.isEmpty()) }
        compose.onNodeWithText("完成").performClick()
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(listOf("液态编辑"), submissions) }
    }

    @Test fun pickerReversesItsExitWithoutLosingContentOrCompletingTheCancelledClose() {
        val show = mutableStateOf(false)
        var completions = 0
        compose.setContent {
            MaterialTheme {
                ProvideCouiMenuBackdrop { source ->
                    Box(Modifier.fillMaxSize().then(source)) {
                        CouiPickerDialog(PickerDialogRequest(show.value, "时间", false,
                            { show.value = false }, { completions++ }, null, null, null, false) {
                            val window = rememberCouiWindowBackdrop()
                            val nativeSurface = LocalNativeDialogSurface.current
                            SideEffect { assertNotNull(window); assertTrue(nativeSurface) }
                            androidx.compose.material3.Text("选择草稿")
                        })
                    }
                }
            }
        }
        compose.runOnIdle { show.value = true }
        compose.onNodeWithText("选择草稿").assertExists()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { show.value = false }
        compose.mainClock.advanceTimeBy(40)
        compose.onNodeWithText("选择草稿").assertExists()
        compose.runOnIdle { show.value = true }
        compose.mainClock.advanceTimeBy(400)
        compose.runOnIdle { assertEquals(0, completions) }
        compose.onNodeWithText("选择草稿").assertExists()
        compose.runOnIdle { show.value = false }
        compose.mainClock.advanceTimeBy(400)
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(1, completions) }
        compose.onNodeWithText("选择草稿").assertDoesNotExist()
    }
}
