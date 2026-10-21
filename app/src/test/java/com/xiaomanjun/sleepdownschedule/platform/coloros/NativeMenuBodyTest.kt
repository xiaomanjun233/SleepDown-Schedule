package com.xiaomanjun.sleepdownschedule.platform.coloros

import android.app.Application
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.xiaomanjun.sleepdownschedule.core.ui.settings.GlassMiuixSettingsTheme
import com.xiaomanjun.sleepdownschedule.defaultConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.ListPopupVisualStyle
import top.yukonga.miuix.kmp.basic.LocalListPopupVisualStyle

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class NativeMenuBodyTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun embeddedAndroidRowsSupportIntrinsicMenuWidthAndHostSelection() {
        val clicked = mutableListOf<Int>()
        compose.setContent {
            MaterialTheme {
                GlassMiuixSettingsTheme(defaultConfig()) {
                    val style = ListPopupVisualStyle(itemContent = { row ->
                        val context = LocalContext.current
                        val view = remember(row.item.text) {
                            FrameLayout(context).apply {
                                addView(TextView(context).apply { text = row.item.text; textSize = 17f },
                                    FrameLayout.LayoutParams(-1, -1))
                            }
                        }
                        val policy = remember(view) { NativeMenuMeasurePolicy(view) }
                        Layout(content = { AndroidView(factory = { view }) },
                            modifier = row.modifier.height(40.dp), measurePolicy = policy)
                        true
                    })
                    CompositionLocalProvider(LocalListPopupVisualStyle provides style) {
                        Column(Modifier.width(IntrinsicSize.Max).testTag("native-menu")) {
                            listOf(DropdownItem("可操作菜单"), DropdownItem("禁止操作", enabled = false))
                                .forEachIndexed { index, item ->
                                    DropdownImpl(item, 2, false, index, onSelectedIndexChange = { clicked += it })
                                }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        val bounds = compose.onNodeWithTag("native-menu").fetchSemanticsNode().boundsInRoot
        assertTrue("Native intrinsic width must not collapse", bounds.width > 0f)
        assertTrue("Native rows must retain their host height", bounds.height > 0f)
        compose.onNodeWithContentDescription("可操作菜单").performClick()
        compose.onNodeWithContentDescription("禁止操作").performClick()
        assertEquals(listOf(0), clicked)
    }
}
