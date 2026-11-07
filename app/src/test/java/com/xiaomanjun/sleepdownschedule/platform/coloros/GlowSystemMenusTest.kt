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

    @Test fun overflowUsesSystemRowsAndCommitsOnlyAfterExit() {
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
        assertEquals(196 * pixelsPerDp, row.width, 2f)
        assertTrue(row.height >= 40 * pixelsPerDp - 1f)
        com.xiaomanjun.sleepdownschedule.feature.settings.writeSettingsVisualArtifact(
            "system-glow-overflow.png", compose.onNode(isPopup()).captureToImage().asAndroidBitmap())
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("课程管理").performTouchInput { click() }
        compose.runOnIdle { assertEquals(0, commits) }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(1, commits) }
        compose.onNodeWithText("课程管理").assertDoesNotExist()
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
