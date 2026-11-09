package com.xiaomanjun.sleepdownschedule.feature.course.editor

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.itos.coui.menu.ProvideCouiMenuBackdrop
import com.xiaomanjun.sleepdownschedule.AppState
import com.xiaomanjun.sleepdownschedule.CourseEntity
import com.xiaomanjun.sleepdownschedule.CourseCardColorMode
import com.xiaomanjun.sleepdownschedule.WeekParity
import com.xiaomanjun.sleepdownschedule.defaultConfig
import com.xiaomanjun.sleepdownschedule.defaultPeriods
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LocalNativePagerIndicatorRenderer
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.NativePagerIndicatorRequest
import com.xiaomanjun.sleepdownschedule.core.ui.material.LocalVisualEffects
import com.xiaomanjun.sleepdownschedule.core.ui.material.VisualEffectsState
import com.xiaomanjun.sleepdownschedule.feature.home.personalization.CourseColorDialogStage
import com.xiaomanjun.sleepdownschedule.feature.home.personalization.CourseColorEditorDialog
import com.xiaomanjun.sleepdownschedule.feature.home.rememberHomeAdaptiveMetrics
import com.xiaomanjun.sleepdownschedule.model.VisualEffectMode
import com.xiaomanjun.sleepdownschedule.platform.coloros.ProvideCouiMaterials
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
class CoursePickerLayoutTest {
    @get:Rule val compose = createComposeRule()
    private var pixelsPerDp = 1f

    private fun show(content: @Composable () -> Unit) {
        compose.setContent {
            pixelsPerDp = LocalDensity.current.density
            MaterialTheme {
                CompositionLocalProvider(LocalVisualEffects provides VisualEffectsState(
                    effectiveMode = VisualEffectMode.ColorOsGlow, couiKitAvailable = true)) {
                    ProvideCouiMaterials(couiControlsEnabled = true) {
                        ProvideCouiMenuBackdrop(isolateContent = false, recordWhenIdle = false) { source ->
                            Box(Modifier.fillMaxSize().then(source).background(Color(0xFF85B6D0))) { content() }
                        }
                    }
                }
            }
        }
    }

    @Test fun animatedEditorSuppliesItsShellBackdropToThePagerIndicator() {
        val first = CourseEntity(id = 1, name = "分页课程", teacher = null, location = "A", weekday = 1,
            periods = listOf(1), weeks = listOf(1), weekParity = WeekParity.ALL, note = null)
        val second = first.copy(id = 2, location = "B", weekday = 2)
        val state = AppState(courses = listOf(first, second))
        val request = CourseEditorOverlayRequest(first, 1, Rect(30f, 70f, 150f, 210f))
        var indicator: NativePagerIndicatorRequest? = null
        var renderedByKit = false
        show {
            val renderPager = checkNotNull(LocalNativePagerIndicatorRenderer.current)
            CompositionLocalProvider(LocalNativePagerIndicatorRenderer provides { pager ->
                val rendered = renderPager(pager)
                SideEffect {
                    indicator = pager
                    renderedByKit = rendered
                }
                rendered
            }) {
                CourseEditorContainerOverlayHost(request, state, null, state.config, rememberHomeAdaptiveMetrics(),
                    onDismissRequest = {}, onSave = { _, _, _ -> }, onCopy = { _, _ -> },
                    onDelete = { _, _ -> }, motionState = rememberCourseEditorMotionState())
            }
        }
        compose.runOnIdle {
            assertEquals(2, indicator?.pageCount)
            assertNotNull(indicator?.backdrop)
            assertTrue(renderedByKit)
            assertEquals(0, indicator?.selectedPage?.invoke())
            indicator?.onSelect?.invoke(1)
        }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(1, indicator?.selectedPage?.invoke()) }
    }

    @Test fun weekdayGridUsesTheContentInsetWithoutWrappingTheLastDay() {
        val labels = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
        show {
            CourseEditorPickerOverlay(true, CourseEditorPickerRequest.Grid("选择星期", labels, setOf(0, 2), 7) {},
                null, defaultConfig(), false, {}, {})
        }
        val first = compose.onNode(hasText("周一") and hasClickAction()).fetchSemanticsNode().boundsInRoot
        val last = compose.onNode(hasText("周日") and hasClickAction()).fetchSemanticsNode().boundsInRoot
        val cancel = compose.onNode(hasText("取消") and hasClickAction()).fetchSemanticsNode().boundsInRoot
        assertEquals(first.top, last.top, 1f)
        assertEquals(8 * pixelsPerDp, first.left - cancel.left, 2f)
        assertEquals(44 * pixelsPerDp, first.height, 2f)
        com.xiaomanjun.sleepdownschedule.feature.settings.writeSettingsVisualArtifact(
            "coui-course-weekday-picker.png", compose.onNode(isDialog()).captureToImage().asAndroidBitmap())
    }

    @Test fun paletteKeepsTheCompactAutomaticActionAndTheStandardFooter() {
        show { CourseColorPicker(true, null, 0xFF6699FFL, null, defaultConfig(), false, {}, {}, {}) }
        val auto = compose.onNode(hasText("自动") and hasClickAction()).fetchSemanticsNode().boundsInRoot
        val grid = compose.onNodeWithContentDescription("调色盘").fetchSemanticsNode().boundsInRoot
        val cancel = compose.onNode(hasText("取消") and hasClickAction()).fetchSemanticsNode().boundsInRoot
        val confirm = compose.onNode(hasText("确定") and hasClickAction()).fetchSemanticsNode().boundsInRoot
        assertEquals(grid.right, auto.right, 2f)
        assertEquals(8 * pixelsPerDp, grid.left - cancel.left, 2f)
        assertEquals(8 * pixelsPerDp, confirm.right - grid.right, 2f)
        assertEquals(44 * pixelsPerDp, confirm.height, 2f)
        assertTrue(auto.height < confirm.height)
        com.xiaomanjun.sleepdownschedule.feature.settings.writeSettingsVisualArtifact(
            "coui-course-color-picker.png", compose.onNode(isDialog()).captureToImage().asAndroidBitmap())
    }

    @Test fun wallpaperSamplingHasAFixedFooterAndReturnsToThePalette() {
        show {
            var stage by remember { mutableStateOf(CourseColorDialogStage.Picker) }
            CourseColorEditorDialog(show = true, stage = stage, mode = CourseCardColorMode.SOLID,
                colors = listOf(0xFF6699FF), selectedIndex = 0, backdrop = null, config = defaultConfig(),
                onDismiss = {}, onStageChange = { stage = it }, onSelectedIndexChange = {},
                onColorChange = { _, _ -> }, onConfirm = {})
        }
        compose.onNodeWithContentDescription("从壁纸吸色").performClick()
        compose.onNodeWithText("从壁纸取色").assertIsDisplayed()
        compose.onNodeWithText("返回").assertIsDisplayed()
        compose.onNodeWithText("确认").assertIsNotEnabled()
        com.xiaomanjun.sleepdownschedule.feature.settings.writeSettingsVisualArtifact(
            "coui-wallpaper-color-picker.png", compose.onNode(isDialog()).captureToImage().asAndroidBitmap())
        compose.onNodeWithText("返回").performClick()
        compose.onNodeWithContentDescription("调色盘").assertIsDisplayed()
    }

    @Test fun editorFormKeepsItsFixedHeaderAndOpensTheWeekdayPicker() {
        val periodValues = (1..12).toList()
        val draft = courseEditorDraft(emptyList(), periodValues, 20)
        show {
            var picker by remember { mutableStateOf<CourseEditorPickerRequest?>(null) }
            Box(Modifier.size(328.dp, 600.dp).background(Color(0xFFF0F1F2))) {
                CourseEditorFormPage(null, "添加课程", emptyList(), null, draft, {},
                    periodValues, defaultPeriods(1), 20, defaultConfig(), null, null,
                    onCancel = {}, onSave = {}, onDelete = null, onOpenPicker = { picker = it },
                    onOpenColorPicker = {}, pageCount = 1)
            }
            picker?.let {
                CourseEditorPickerOverlay(true, it, null, defaultConfig(), false, { picker = null }, {})
            }
        }
        compose.onNodeWithText("添加课程").assertExists()
        val fields = compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes()
        assertTrue(fields.size >= 3)
        assertTrue(fields.zipWithNext().all { (first, next) -> first.boundsInRoot.bottom <= next.boundsInRoot.top })
        com.xiaomanjun.sleepdownschedule.feature.settings.writeSettingsVisualArtifact(
            "coui-course-editor-form.png", compose.onRoot().captureToImage().asAndroidBitmap())
        compose.onNodeWithText("星期").performClick()
        compose.onNodeWithText("选择星期").assertExists()
    }
}
