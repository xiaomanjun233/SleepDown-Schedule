package com.xiaomanjun.sleepdownschedule.feature.settings

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performClick
import com.xiaomanjun.sleepdownschedule.AppState
import com.xiaomanjun.sleepdownschedule.defaultConfig
import com.xiaomanjun.sleepdownschedule.core.ui.material.LocalVisualEffects
import com.xiaomanjun.sleepdownschedule.core.ui.material.VisualEffectsState
import com.xiaomanjun.sleepdownschedule.model.VisualEffectMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** 对实际首页做栅格核对，保护用户截图指出的卡片色、行高和应用头部排版。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w360dp-h800dp-560dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsHomeVisualTest {
    @get:Rule val compose = createComposeRule()

    @Test fun nativeSettingsHomeMatchesTheSettingsSurfaceAndCatalogGeometry() = checkHome(true)
    @Test fun liquidSettingsHomeKeepsTheSameCardContourAndColor() = checkHome(false)

    private fun checkHome(native: Boolean) {
        val app = RuntimeEnvironment.getApplication()
        app.applicationInfo.nonLocalizedLabel = "SleepDown课程表"
        val config = defaultConfig().copy(darkMode = true, followSystemDarkMode = false)
        compose.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                CompositionLocalProvider(LocalVisualEffects provides VisualEffectsState(
                    effectiveMode = if (native) VisualEffectMode.ColorOsGlow else VisualEffectMode.LiquidGlass)) {
                    SettingsRootScreen(AppState(config = config), backdrop = null, onPageChange = {})
                }
            }
        }
        compose.waitForIdle()
        listOf("应用", "智能助手", "其他").forEach { compose.onNodeWithText(it).assertDoesNotExist() }
        val pixels = compose.onRoot().captureToImage().asAndroidBitmap()
        writeSettingsVisualArtifact(if (native) "settings-home-native.png" else "settings-home-liquid.png", pixels)
        val density = app.resources.displayMetrics.density
        val row = compose.onNodeWithText("通用设置").fetchSemanticsNode().boundsInRoot
        assertEquals(52f * density, row.height, 1f)
        val color = pixels.getPixel((20 * density).toInt(), row.center.y.toInt())
        assertEquals(26, android.graphics.Color.red(color))
        assertEquals(26, android.graphics.Color.green(color))
        assertEquals(26, android.graphics.Color.blue(color))
        // Settings 普通 G2/weight=2.5：前一点在轮廓内，后一点在轮廓外。
        // 普通圆弧会吃掉前一点；直接套 Demo 的 weight=3 则会填上后一点。
        val corner = pixels.getPixel((row.left + 7 * density).toInt(), (row.top + 6 * density).toInt())
        assertEquals(26, android.graphics.Color.red(corner))
        val outside = pixels.getPixel((row.left + 4.5f * density).toInt(), (row.top + 6 * density).toInt())
        assertEquals(0, android.graphics.Color.red(outside))
        val title = compose.onNodeWithText("SleepDown课程表", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val version = compose.onNodeWithText("版本", substring = true, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue(version.top >= title.bottom)
        assertEquals(title.left, version.left, 1f)
        assertTrue(title.left >= 104f * density - 1f)
        compose.onNodeWithText("检查更新").assertDoesNotExist()
        val widgets = compose.onNodeWithText("小组件设置").fetchSemanticsNode().boundsInRoot
        val dividerY = widgets.top.toInt() - 1
        assertEquals(26, android.graphics.Color.red(pixels.getPixel((row.left + 40 * density).toInt(), dividerY)))
        assertTrue(android.graphics.Color.red(pixels.getPixel((row.left + 72 * density).toInt(), dividerY)) in 70..74)
    }

    @Test fun appearanceUsesSettingsPreviewLayoutAndKeepsManualThemeSelection() {
        val config = defaultConfig().copy(darkMode = true, followSystemDarkMode = true)
        var updated = config
        compose.setContent {
            MaterialTheme(colorScheme = androidx.compose.material3.lightColorScheme()) {
                CompositionLocalProvider(LocalVisualEffects provides VisualEffectsState(
                    effectiveMode = VisualEffectMode.ColorOsGlow)) {
                    com.xiaomanjun.sleepdownschedule.core.ui.settings.CouiSettingsPageScaffold(
                        title = "通用设置", config = config, showBack = true) { _, source ->
                        GeneralSettingsScreen(AppState(config = config), source, onUpdateConfig = { updated = it })
                    }
                }
            }
        }
        compose.onNodeWithText("亮色模式").assertIsDisplayed()
        compose.onNodeWithText("暗色模式").assertIsDisplayed()
        writeSettingsVisualArtifact("general-appearance-native.png", compose.onRoot().captureToImage().asAndroidBitmap())
        compose.onNodeWithText("暗色模式").performClick()
        compose.runOnIdle {
            assertTrue(updated.darkMode)
            org.junit.Assert.assertFalse(updated.followSystemDarkMode)
        }
    }
}

internal fun writeSettingsVisualArtifact(name: String, pixels: Bitmap) {
    val module = File(System.getProperty("user.dir")).canonicalFile
    val workspace = if (File(module, "settings.gradle.kts").isFile) module else module.parentFile
    val folder = File(workspace, "tmp/coui-visual-review-20261106")
    folder.mkdirs()
    File(folder, name).outputStream().use { pixels.compress(Bitmap.CompressFormat.PNG, 100, it) }
}
