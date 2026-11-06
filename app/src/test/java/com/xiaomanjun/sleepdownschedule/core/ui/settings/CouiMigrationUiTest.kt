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
import com.itos.coui.backdrop.LocalCouiSurfaceBackdrop
import com.itos.coui.segmentbutton.CouiSegmentedControl
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
                    Box(Modifier.fillMaxSize().testTag("page")) {
                        SettingsActionButton("测试实时活动", backdrop, {}, badgeText = "实验功能",
                            modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 16.dp, vertical = 18.dp).fillMaxWidth())
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
                            val surface = LocalCouiSurfaceBackdrop.current
                            val window = rememberCouiWindowBackdrop()
                            SideEffect { assertNotNull(surface); assertNotSame(window, surface) }
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
