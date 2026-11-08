package com.xiaomanjun.sleepdownschedule.platform.coloros

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.itos.coui.menu.ProvideCouiMenuBackdrop
import com.itos.coui.menu.Menu
import com.itos.coui.menu.CouiMenuItem
import com.itos.coui.menu.CouiMenuDefaults
import com.itos.coui.effect.SpotlightDiagnostic
import com.itos.coui.diagnostic.CouiRenderDiagnostics
import com.xiaomanjun.sleepdownschedule.glass.*
import com.xiaomanjun.sleepdownschedule.R
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.AddMenuAction
import com.xiaomanjun.sleepdownschedule.core.ui.material.LocalHomeMaterialControls
import com.xiaomanjun.sleepdownschedule.core.ui.material.LocalHomeOverflowMenu
import com.xiaomanjun.sleepdownschedule.core.ui.chrome.HomeIconButtonVisual
import com.xiaomanjun.sleepdownschedule.defaultConfig
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
class GlowSystemMenusTest {
    @get:Rule val compose = createComposeRule()

    @Test fun kitMenuSpotlightReceivesPressAndReleasesOnCancelAndUpInBothThemes() {
        val dark = mutableStateOf(false)
        val events = mutableListOf<SpotlightDiagnostic>()
        var commits = 0
        compose.setContent {
            MaterialTheme {
                ProvideCouiMenuBackdrop(isolateContent = false, recordWhenIdle = false) { source ->
                    Box(Modifier.fillMaxSize().then(source).background(Color(0xFF85B6D0))) {
                        Menu(expanded = true, onDismissRequest = {},
                            modifier = Modifier.width(196.dp),
                            style = CouiMenuDefaults.style(dark.value),
                            items = listOf(CouiMenuItem(0, "第一项"), CouiMenuItem(1, "第二项")),
                            onItemClick = { commits++ }, onSpotlightDiagnostic = events::add)
                    }
                }
            }
        }
        for (theme in listOf(false, true)) {
            compose.runOnIdle { dark.value = theme; events.clear() }
            val first = compose.onNodeWithText("第一项")
            val delta = compose.onNodeWithText("第二项").fetchSemanticsNode().boundsInRoot.center -
                first.fetchSemanticsNode().boundsInRoot.center
            first.performTouchInput { down(center); moveTo(center + delta) }
            compose.mainClock.advanceTimeBy(300)
            compose.onNode(isPopup()).captureToImage()
            compose.runOnIdle {
                assertEquals(1, events.count { it == SpotlightDiagnostic.Pressed })
                assertTrue(events.any { it is SpotlightDiagnostic.FirstDraw && it.progress > 0f })
                assertFalse(events.any { it is SpotlightDiagnostic.Released })
            }
            first.performTouchInput { cancel() }
            compose.runOnIdle {
                assertEquals(1, events.count { it is SpotlightDiagnostic.Released })
                assertEquals(0, commits)
                events.clear()
            }
            first.performTouchInput { click() }
            compose.runOnIdle {
                assertEquals(1, events.count { it == SpotlightDiagnostic.Pressed })
                assertEquals(1, events.count { it is SpotlightDiagnostic.Released })
                assertEquals(1, commits)
                commits = 0
            }
        }
    }

    @Test fun overflowUsesCouiRowsAndCommitsImmediatelyExactlyOnce() {
        var commits = 0
        var pixelsPerDp = 1f
        compose.setContent {
            pixelsPerDp = LocalDensity.current.density
            MaterialTheme {
                ProvideColorOsMaterials(enabled = false, couiControlsEnabled = true) {
                    ProvideCouiMenuBackdrop(isolateContent = false, recordWhenIdle = false) { source ->
                        Box(Modifier.fillMaxSize().then(source).background(Color(0xFF85B6D0)), contentAlignment = Alignment.TopEnd) {
                            val actions = remember {
                                listOf(AddMenuAction(R.drawable.ic_courses, "课程管理") { commits++ },
                                    AddMenuAction(R.drawable.ic_settings, "课表设置") { commits += 10 })
                            }
                            CompositionLocalProvider(LocalHomeOverflowMenu provides rememberUpdatedState(actions)) {
                                HomeIconButtonVisual(null, defaultConfig(), R.drawable.ic_more_horizontal,
                                    "打开", Modifier.padding(16.dp).size(42.dp))
                            }
                        }
                    }
                }
            }
        }
        compose.onNodeWithContentDescription("打开").performClick()
        val row = compose.onNodeWithText("课程管理").fetchSemanticsNode().boundsInRoot
        assertEquals(196 * pixelsPerDp, row.width + 16 * pixelsPerDp, 2f)
        assertTrue(row.height >= 37 * pixelsPerDp - 1f)
        com.xiaomanjun.sleepdownschedule.feature.settings.writeSettingsVisualArtifact(
            "system-glow-overflow.png", compose.onNode(isPopup()).captureToImage().asAndroidBitmap())
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("课程管理").performTouchInput { click() }
        compose.runOnIdle { assertEquals(1, commits) }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(1, commits) }
        compose.onNodeWithText("课程管理").assertDoesNotExist()
    }

    @Test fun overflowDragSelectsTheReleaseItemAndMovingOutsideDoesNotSubmit() {
        var commits = 0
        compose.setContent {
            MaterialTheme {
                ProvideColorOsMaterials(enabled = false, couiControlsEnabled = true) {
                    ProvideCouiMenuBackdrop(isolateContent = false, recordWhenIdle = false) { source ->
                        Box(Modifier.fillMaxSize().then(source).background(Color(0xFF85B6D0)),
                            contentAlignment = Alignment.TopEnd) {
                            val actions = listOf(AddMenuAction(R.drawable.ic_courses, "课程管理") { commits++ },
                                AddMenuAction(R.drawable.ic_settings, "课表设置") { commits += 10 })
                            CompositionLocalProvider(LocalHomeOverflowMenu provides rememberUpdatedState(actions)) {
                                HomeIconButtonVisual(null, defaultConfig(), R.drawable.ic_more_horizontal,
                                    "打开", Modifier.padding(16.dp).size(42.dp))
                            }
                        }
                    }
                }
            }
        }
        compose.onNodeWithContentDescription("打开").performClick()
        val first = compose.onNodeWithText("课程管理")
        val firstBounds = first.fetchSemanticsNode().boundsInRoot
        val secondBounds = compose.onNodeWithText("课表设置").fetchSemanticsNode().boundsInRoot
        val delta = secondBounds.center - firstBounds.center
        first.performTouchInput { down(center); moveTo(center + delta); moveTo(androidx.compose.ui.geometry.Offset(-100f, center.y)); up() }
        compose.runOnIdle { assertEquals(0, commits) }
        first.performTouchInput { down(center); moveTo(center + delta) }
        compose.mainClock.advanceTimeBy(300)
        com.xiaomanjun.sleepdownschedule.feature.settings.writeSettingsVisualArtifact(
            "coui-action-menu-native-mask.png", compose.onNode(isPopup()).captureToImage().asAndroidBitmap())
        compose.runOnIdle { assertEquals(0, commits) }
        first.performTouchInput { up() }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(10, commits) }
    }

    @Test fun homeTopBarUsesCouiButtonInsideToolbarSurface() {
        var clicks = 0
        CouiRenderDiagnostics.enable { }
        try {
        compose.setContent {
            MaterialTheme {
                ProvideColorOsMaterials(enabled = false, couiControlsEnabled = true) {
                    val background = rememberGlassLayerBackdrop(GlassBackdropDomain.Background, "toolbar-test-background")
                    val foreground = rememberGlassLayerBackdrop(GlassBackdropDomain.Content, "toolbar-test-content")
                    val toolbar = rememberHomeToolbarBackdrop()
                    Box(Modifier.fillMaxSize()) {
                        Box(Modifier.matchParentSize().glassBackdropProducer(background).background(Color(0xFF85B6D0)))
                        Box(Modifier.matchParentSize().glassBackdropProducer(foreground))
                        HomeToolbarBackdrop(toolbar, background, foreground, Modifier.fillMaxWidth().height(120.dp)) { }
                        CompositionLocalProvider(LocalCouiHomeTopBarActive provides true,
                            LocalHomeToolbarBackdrop provides toolbar) {
                            assertTrue(LocalHomeMaterialControls.current.IconButton(
                                iconRes = R.drawable.ic_more_horizontal,
                                contentDescription = "更多",
                                dark = false,
                                interactive = true,
                                selected = false,
                                modifier = Modifier.size(42.dp),
                                onClick = { clicks++ },
                                toolbarNavigation = false,
                                enabled = true,
                            ))
                        }
                    }
                }
            }
        }
        compose.onNodeWithContentDescription("更多", useUnmergedTree = true).assertExists().performClick()
        compose.runOnIdle { assertEquals(1, clicks) }
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        val toolbarImage = compose.onRoot().captureToImage().asAndroidBitmap()
        compose.runOnIdle {
            val sources = CouiRenderDiagnostics.snapshot().getJSONArray("sources")
            val toolbarSource = (0 until sources.length()).map { sources.getJSONObject(it) }
                .single { it.optString("name") == "home-toolbar-background" }
            assertTrue(toolbarSource.getBoolean("recorded"))
            assertEquals(1, toolbarSource.getInt("activeConsumers"))
            assertTrue(toolbarSource.getLong("sampleCount") > 0)
        }
        com.xiaomanjun.sleepdownschedule.feature.settings.writeSettingsVisualArtifact(
            "coui-home-top-bar-button.png",
            toolbarImage,
        )
        } finally { CouiRenderDiagnostics.disable() }
    }

    @Test fun liquidModeKeepsItsExistingMenuHost() {
        var handled = true
        var contentInvoked = false
        compose.setContent {
            ProvideColorOsMaterials(enabled = false, couiControlsEnabled = false) {
                handled = LocalHomeMaterialControls.current.OverflowMenuButton(false, true,
                    listOf(AddMenuAction(R.drawable.ic_courses, "课程管理") {}), Modifier) { contentInvoked = true }
            }
        }
        compose.runOnIdle { assertFalse(handled); assertFalse(contentInvoked) }
    }

    @Test fun shortcutCancelDoesNotSelectAndTheExitBarrierFinishesExactlyOnce() {
        var commits = 0
        val closing = mutableStateOf(false)
        var dismiss: (((() -> Unit) -> Unit))? = null
        compose.setContent {
            MaterialTheme {
                ProvideColorOsMaterials(enabled = false, couiControlsEnabled = true) {
                    ProvideCouiMenuBackdrop(isolateContent = false, recordWhenIdle = false) { source ->
                        Box(Modifier.fillMaxSize().then(source).background(Color(0xFF85B6D0))) {
                            LocalHomeMaterialControls.current.ShortcutMenu("course", Rect(50f, 200f, 150f, 350f), false,
                                listOf(AddMenuAction(R.drawable.ic_edit, "编辑单节课") {
                                    closing.value = true; dismiss?.invoke { commits++ }
                                }, AddMenuAction(R.drawable.ic_trash, "移除课程") { commits += 10 }),
                                closing.value, { closing.value = true; dismiss?.invoke { } }, { dismiss = it })
                        }
                    }
                }
            }
        }
        compose.onNodeWithText("编辑单节课").performTouchInput { down(center); moveBy(androidx.compose.ui.geometry.Offset(8f, 4f)); cancel() }
        compose.runOnIdle { assertEquals(0, commits) }
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("编辑单节课").performClick()
        compose.runOnIdle { assertEquals(0, commits) }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(1, commits) }
    }
}
