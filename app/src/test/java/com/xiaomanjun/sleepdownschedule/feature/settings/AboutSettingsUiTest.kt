package com.xiaomanjun.sleepdownschedule.feature.settings

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.xiaomanjun.sleepdownschedule.AppState
import com.xiaomanjun.sleepdownschedule.defaultConfig
import com.xiaomanjun.sleepdownschedule.core.ui.material.LocalVisualEffects
import com.xiaomanjun.sleepdownschedule.core.ui.material.VisualEffectsState
import com.xiaomanjun.sleepdownschedule.core.ui.settings.CouiSettingsPageScaffold
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsGroup
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsNavigationRow
import com.xiaomanjun.sleepdownschedule.feature.settings.about.ColorOsAboutSettingsScreen
import com.xiaomanjun.sleepdownschedule.feature.settings.about.SoftwareUpdatePageContent
import com.xiaomanjun.sleepdownschedule.feature.settings.about.ChangelogSettingsScreen
import com.xiaomanjun.sleepdownschedule.feature.update.AppUpdateDialog
import com.xiaomanjun.sleepdownschedule.feature.update.GiteeReleaseInfo
import com.xiaomanjun.sleepdownschedule.model.VisualEffectMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w360dp-h800dp-560dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AboutSettingsUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun aboutUsesFullNameAndOpensTheHistoryAsASeparatePage() {
        val config = defaultConfig().copy(darkMode = true, followSystemDarkMode = false)
        RuntimeEnvironment.getApplication().applicationInfo.nonLocalizedLabel = "SleepDown课程表"
        compose.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                CompositionLocalProvider(LocalVisualEffects provides VisualEffectsState(effectiveMode = VisualEffectMode.ColorOsGlow)) {
                    var page by remember { mutableStateOf(SettingsPage.About) }
                    CouiSettingsPageScaffold(page.title(), config, showBack = true,
                        onBack = { page = SettingsPage.About }) { _, source ->
                        when (page) {
                            SettingsPage.About -> ColorOsAboutSettingsScreen(AppState(config = config), source, {}, {},
                                onSoftwareUpdate = { page = SettingsPage.SoftwareUpdate },
                                onHistory = { page = SettingsPage.Changelog })
                            SettingsPage.Changelog -> ChangelogSettingsScreen(AppState(config = config), source)
                            else -> SoftwareUpdatePageContent("1.2.8_beta2", AppUpdateDialog.UpToDate("1.2.8_beta2"), {}, {}, {}, {})
                        }
                    }
                }
            }
        }
        compose.onNodeWithText("SleepDown课程表").assertIsDisplayed()
        compose.onNodeWithText("检查更新").assertIsDisplayed()
        compose.onNodeWithText("github.com/xiaomanjun233").assertIsDisplayed()
        compose.onNodeWithText("应用名称").assertDoesNotExist()
        writeSettingsVisualArtifact("about-c17-native.png", compose.onRoot().captureToImage().asAndroidBitmap())
        compose.onNodeWithText("更新日志").performClick()
        compose.onNodeWithText("1.2.8_beta1").assertExists()
        compose.onNodeWithText("github.com/xiaomanjun233").assertDoesNotExist()
        writeSettingsVisualArtifact("changelog-c17-native.png", compose.onRoot().captureToImage().asAndroidBitmap())
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithText("检查更新").performClick()
        compose.onNodeWithText("已是最新版本").assertIsDisplayed()
        compose.onNodeWithText("github.com/xiaomanjun233").assertDoesNotExist()
        writeSettingsVisualArtifact("software-current-c17-native.png", compose.onRoot().captureToImage().asAndroidBitmap())
    }

    @Test fun softwareUpdateUsesTheOriginalButtonSizeAndDownloadsExactlyOnce() {
        val config = defaultConfig().copy(darkMode = true, followSystemDarkMode = false)
        val release = GiteeReleaseInfo(name = "SleepDown 1.2.8", tagName = "v1.2.8", notes = "版本说明测试",
            apkName = "sleepdown.apk", apkUrl = "https://example.com/sleepdown.apk",
            releaseUrl = "https://example.com/release", prerelease = false)
        var downloads = 0
        compose.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                CouiSettingsPageScaffold("软件更新", config, showBack = true) { _, source ->
                    SoftwareUpdatePageContent("1.2.8_beta2", AppUpdateDialog.Available(release), {},
                        { assertEquals(release, it); downloads++ }, {}, {}, history = {
                            SettingsGroup(source, config) {
                                SettingsNavigationRow("历史更新日志", onClick = {})
                            }
                        })
                }
            }
        }
        val button = compose.onNodeWithText("下载并安装")
        val bounds = button.fetchSemanticsNode().boundsInRoot
        val density = RuntimeEnvironment.getApplication().resources.displayMetrics.density
        assertEquals(220f * density, bounds.width, 1f)
        assertEquals(44f * density, bounds.height, 1f)
        writeSettingsVisualArtifact("software-update-c17-native.png", compose.onRoot().captureToImage().asAndroidBitmap())
        button.performClick()
        compose.runOnIdle { assertEquals(1, downloads) }
    }
}
