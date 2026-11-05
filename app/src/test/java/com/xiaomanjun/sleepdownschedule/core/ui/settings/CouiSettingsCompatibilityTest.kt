package com.xiaomanjun.sleepdownschedule.core.ui.settings

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.geometry.Offset
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.itos.coui.layout.Group
import com.itos.coui.layout.Item
import com.itos.coui.layout.Scaffold
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text
import com.itos.coui.theme.LocalCouiMaterialEnabled
import com.itos.coui.theme.CouiPresets
import com.itos.coui.topappbar.TopAppBar
import com.xiaomanjun.sleepdownschedule.defaultConfig
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.SleepDownSecondaryPageList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26], application = Application::class)
class CouiSettingsCompatibilityTest {
    @get:Rule val compose = createComposeRule()

    @Test fun android8DisplaysAndClicksCouiLayoutWithoutMaterialSampling() {
        var material = true
        var nativeColors = false
        var clicks = 0
        val accent = Color(0xFF268563)
        compose.setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = accent)) {
            CouiSettingsTheme(defaultConfig()) {
                material = LocalCouiMaterialEnabled.current
                nativeColors = MaterialTheme.colorScheme.background == CouiPresets.Light.background &&
                    MaterialTheme.colorScheme.surfaceBright == CouiPresets.Light.container &&
                    MaterialTheme.colorScheme.primary == accent &&
                    com.itos.coui.theme.CouiTheme.colorScheme.let {
                        it.accent == accent && it.accentOnSurface == accent &&
                            it.checkboxAccent == accent && it.menuCheckboxAccent == accent
                    }
                Scaffold(topBar = {
                    TopAppBar(title = { Text(text = "设置", kind = CouiTextKind.Toolbar) })
                }) { padding ->
                    Column(Modifier.padding(padding)) {
                        Group {
                            Item(onClick = { clicks++ }) {
                                Text(text = "通用设置", kind = CouiTextKind.PreferenceTitle)
                            }
                        }
                    }
                }
            }
            }
        }
        compose.onNodeWithText("设置").assertIsDisplayed()
        compose.onNodeWithText("通用设置").assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertFalse(material)
            org.junit.Assert.assertTrue(nativeColors)
            assertEquals(1, clicks)
        }
    }

    @Test fun couiPageHostsExistingDropdownAndCommitsItsSelection() {
        var selected by mutableStateOf(0)
        val config = defaultConfig()
        compose.setContent {
            CouiSettingsPageScaffold(title = "通用设置", config = config) { padding, _ ->
                Column(Modifier.padding(padding)) {
                    SettingsGroup(backdrop = null, config = config) {
                        SleepDownLiquidDropdownPreference(
                            title = "导航模式", items = listOf("普通导航", "并列导航"),
                            selectedIndex = selected, backdrop = null, config = config,
                            onSelectedIndexChange = { selected = it },
                        )
                    }
                }
            }
        }
        compose.onNodeWithText("导航模式").performClick()
        compose.onNodeWithText("并列导航").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, selected) }
        compose.onNodeWithText("导航模式").performClick()
        compose.onNodeWithText("普通导航").assertIsDisplayed()
        compose.onRoot().performTouchInput { click(Offset(1f, 1f)) }
        compose.waitForIdle()
        compose.onNodeWithText("普通导航").assertDoesNotExist()
        compose.onNodeWithText("导航模式").performClick()
        compose.onNodeWithText("普通导航").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(0, selected) }
    }

    @Test fun adaptedGroupsMatchUpstreamGeometryAndRowSizing() {
        compose.setContent {
            CouiSettingsTheme(defaultConfig()) {
                Row(Modifier.fillMaxSize()) {
                    Column(Modifier.weight(1f).testTag("upstream-pane")) {
                        Group(title = "参考分组") {
                            Item { Text(text = "参考选项", kind = CouiTextKind.PreferenceTitle) }
                        }
                        Group {
                            Item { Text(text = "参考第二项", kind = CouiTextKind.PreferenceTitle) }
                        }
                    }
                    Box(Modifier.weight(1f).testTag("adapter-pane")) {
                        SleepDownSecondaryPageList(contentTopPadding = 0.dp, contentBottomPadding = 0.dp) {
                            item {
                                GlassPreferenceSection("适配分组") {
                                    SettingsGroup(backdrop = null, config = defaultConfig()) {
                                        SettingsBasicComponent(title = "适配选项")
                                    }
                                }
                            }
                            item {
                                SettingsGroup(backdrop = null, config = defaultConfig()) {
                                    SettingsBasicComponent(title = "适配第二项")
                                }
                            }
                        }
                    }
                }
            }
        }
        val sourceOrigin = compose.onNodeWithTag("upstream-pane").fetchSemanticsNode().boundsInRoot.left
        val targetOrigin = compose.onNodeWithTag("adapter-pane").fetchSemanticsNode().boundsInRoot.left
        for ((source, target) in listOf("参考分组" to "适配分组", "参考选项" to "适配选项", "参考第二项" to "适配第二项")) {
            val reference = compose.onNodeWithText(source).fetchSemanticsNode().boundsInRoot
            val adapted = compose.onNodeWithText(target).fetchSemanticsNode().boundsInRoot
            assertEquals(reference.top, adapted.top, .5f)
            assertEquals(reference.left - sourceOrigin, adapted.left - targetOrigin, .5f)
            assertEquals(reference.height, adapted.height, .5f)
        }
    }
}
