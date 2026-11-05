package com.xiaomanjun.sleepdownschedule.core.ui.settings

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.itos.coui.layout.Group
import com.itos.coui.layout.Item
import com.itos.coui.layout.Scaffold
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text
import com.itos.coui.theme.LocalCouiMaterialEnabled
import com.itos.coui.topappbar.TopAppBar
import com.xiaomanjun.sleepdownschedule.defaultConfig
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
        var clicks = 0
        compose.setContent {
            CouiSettingsTheme(defaultConfig()) {
                material = LocalCouiMaterialEnabled.current
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
        compose.onNodeWithText("设置").assertIsDisplayed()
        compose.onNodeWithText("通用设置").assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertFalse(material)
            assertEquals(1, clicks)
        }
    }
}
