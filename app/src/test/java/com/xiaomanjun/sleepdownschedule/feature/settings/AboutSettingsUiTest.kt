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
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.itos.coui.icons.CouiIcons
import com.xiaomanjun.sleepdownschedule.AppState
import com.xiaomanjun.sleepdownschedule.defaultConfig
import com.xiaomanjun.sleepdownschedule.core.ui.material.LocalVisualEffects
import com.xiaomanjun.sleepdownschedule.core.ui.material.VisualEffectsState
import com.xiaomanjun.sleepdownschedule.core.ui.settings.CouiSettingsPageScaffold
import com.xiaomanjun.sleepdownschedule.feature.settings.about.AutoUpdateSettingsScreen
import com.xiaomanjun.sleepdownschedule.feature.settings.about.ColorOsAboutSettingsScreen
import com.xiaomanjun.sleepdownschedule.feature.settings.about.SoftwareUpdateMenu
import com.xiaomanjun.sleepdownschedule.feature.settings.about.SoftwareUpdateToolbarButton
import com.xiaomanjun.sleepdownschedule.feature.settings.about.SoftwareUpdatePageContent
import com.xiaomanjun.sleepdownschedule.feature.settings.about.ChangelogSettingsScreen
import com.xiaomanjun.sleepdownschedule.feature.update.AppUpdateDialog
import com.xiaomanjun.sleepdownschedule.feature.update.GiteeReleaseInfo
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w360dp-h800dp-560dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AboutSettingsUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun aboutShowsGitHubRowsAndSoftwareUpdateShowsBundledNotesAndImmediateMenu() {
        val config = defaultConfig().copy(darkMode = true, followSystemDarkMode = false)
        var notesOpens = 0
        var welcomeReplays = 0
        var pickedPage: SettingsPage? = null
        RuntimeEnvironment.getApplication().applicationInfo.nonLocalizedLabel = "SleepDown课程表"
        compose.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                CompositionLocalProvider(LocalVisualEffects provides VisualEffectsState(
                    effectiveMode = VisualEffectMode.ColorOsGlow)) {
                    var page by remember { mutableStateOf(SettingsPage.About) }
                    var showingNotes by remember { mutableStateOf(false) }
                    val back: () -> Unit = {
                        if (page == SettingsPage.SoftwareUpdate && showingNotes) showingNotes = false
                        else page = if (page == SettingsPage.SoftwareUpdate) SettingsPage.About else SettingsPage.SoftwareUpdate
                    }
                    CouiSettingsPageScaffold(
                        title = if (page == SettingsPage.SoftwareUpdate && showingNotes) "更新说明" else page.title(),
                        config = config,
                        showBack = true,
                        onBack = back,
                        topBarNavigation = if (page == SettingsPage.SoftwareUpdate) {
                            { SoftwareUpdateToolbarButton(CouiIcons.Back, "返回", back) }
                        } else null,
                        topBarActions = { source ->
                            if (page == SettingsPage.SoftwareUpdate) SoftwareUpdateMenu(source, onReplayWelcome = { welcomeReplays++ }) {
                                pickedPage = it
                                page = it
                            }
                        },
                    ) { _, source ->
                        when (page) {
                            SettingsPage.About -> ColorOsAboutSettingsScreen(AppState(config = config), source, {}, {},
                                onSoftwareUpdate = { page = SettingsPage.SoftwareUpdate })
                            SettingsPage.Changelog -> ChangelogSettingsScreen(AppState(config = config), source)
                            SettingsPage.AutoUpdate -> AutoUpdateSettingsScreen(config, source, onUpdateConfig = {})
                            else -> SoftwareUpdatePageContent("2.0", AppUpdateDialog.UpToDate("2.0"),
                                {}, {}, {}, {}, onOpenVersionNotes = { notesOpens++; showingNotes = true },
                                showVersionNotes = showingNotes)
                        }
                    }
                }
            }
        }
        compose.onNodeWithText("SleepDown课程表").assertIsDisplayed()
        val update = compose.onNode(hasText("检查更新") or hasText("正在检查更新…") or
            hasText("发现新版本") or hasText("已是最新版本")).assertIsDisplayed()
        val density = RuntimeEnvironment.getApplication().resources.displayMetrics.density
        assertEquals(40f * density, update.fetchSemanticsNode().boundsInRoot.height, 1f)
        val profile = compose.onNodeWithText("关于开发者").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val repository = compose.onNodeWithText("项目仓库").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertEquals(profile.left, repository.left, 1f)
        assertTrue(profile.bottom < repository.top)
        compose.onNodeWithText("xiaomanjun233").assertIsDisplayed()
        compose.onNodeWithText("SleepDown-Schedule").assertIsDisplayed()
        compose.onNodeWithText("更新日志").assertDoesNotExist()
        compose.onNodeWithText("源代码").assertDoesNotExist()
        writeSettingsVisualArtifact("about-c17-native.png", compose.onRoot().captureToImage().asAndroidBitmap())
        update.performClick()
        compose.onNodeWithText("已是最新版本").assertIsDisplayed()
        compose.onNodeWithText("关于开发者").assertDoesNotExist()
        val retryBounds = compose.onNodeWithText("重新检查").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val notes = compose.onNodeWithText("查看更新说明").assertIsDisplayed()
        val notesBounds = notes.fetchSemanticsNode().boundsInRoot
        assertEquals(44f * density, retryBounds.height, 1f)
        assertEquals(retryBounds.width, notesBounds.width, 1f)
        assertEquals(8f * density, notesBounds.top - retryBounds.bottom, 1f)
        writeSettingsVisualArtifact("software-current-c17-native.png", compose.onRoot().captureToImage().asAndroidBitmap())
        notes.performClick()
        compose.runOnIdle { assertEquals(1, notesOpens) }
        compose.onNodeWithText("软件版本").assertIsDisplayed()
        compose.onNodeWithText("课表与作息").assertIsDisplayed()
        writeSettingsVisualArtifact("software-version-notes-c17-native.png", compose.onRoot().captureToImage().asAndroidBitmap())
        compose.onNodeWithContentDescription("收起更新说明").performClick()
        compose.onNodeWithText("课表与作息").assertDoesNotExist()
        compose.onNodeWithContentDescription("展开更新说明").performClick()
        compose.onNodeWithText("课表与作息").assertIsDisplayed()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithText("已是最新版本").assertIsDisplayed()
        compose.onNodeWithText("历史日志").assertDoesNotExist()
        val more = compose.onNodeWithContentDescription("更多选项")
        more.performClick()
        val highlightsMenuItem = compose.onNodeWithText("查看亮点功能").assertIsDisplayed()
        val historyMenuItem = compose.onNodeWithText("历史日志").assertIsDisplayed()
        assertTrue(highlightsMenuItem.fetchSemanticsNode().boundsInRoot.top < historyMenuItem.fetchSemanticsNode().boundsInRoot.top)
        assertTrue(historyMenuItem.fetchSemanticsNode().boundsInRoot.width < 196f * density)
        highlightsMenuItem.performClick()
        compose.runOnIdle { assertEquals(1, welcomeReplays) }
        more.performClick()
        compose.mainClock.autoAdvance = false
        try {
            compose.onNodeWithText("自动更新设置").performClick()
            compose.runOnIdle { assertEquals(SettingsPage.AutoUpdate, pickedPage) }
        } finally {
            compose.mainClock.autoAdvance = true
        }
        compose.onNodeWithText("自动检查更新").assertIsDisplayed()
        compose.onNodeWithText("接收 Beta 版更新").assertIsDisplayed()
        compose.onNodeWithContentDescription("返回").performClick()
        more.performClick()
        compose.onNodeWithText("历史日志").performClick()
        compose.onNodeWithText("2.0").assertExists()
        compose.onNodeWithText("1.2.8_beta1").assertDoesNotExist()
        compose.onNodeWithText("1.2.8_beta2").assertDoesNotExist()
        writeSettingsVisualArtifact("changelog-c17-native.png", compose.onRoot().captureToImage().asAndroidBitmap())
    }

    @Test fun softwareUpdateUsesThePhoneOtaButtonSizeAndDownloadsExactlyOnce() {
        val config = defaultConfig().copy(darkMode = true, followSystemDarkMode = false)
        val release = GiteeReleaseInfo(name = "SleepDown 1.2.8", tagName = "v1.2.8", notes = "版本说明测试",
            apkName = "sleepdown.apk", apkUrl = "https://example.com/sleepdown.apk",
            releaseUrl = "https://example.com/release", prerelease = false)
        var downloads = 0
        compose.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                CouiSettingsPageScaffold("软件更新", config, showBack = true,
                    topBarNavigation = { SoftwareUpdateToolbarButton(CouiIcons.Back, "返回") {} }) { _, _ ->
                    SoftwareUpdatePageContent("2.0", AppUpdateDialog.Available(release), {},
                        { assertEquals(release, it); downloads++ }, {}, {})
                }
            }
        }
        val button = compose.onNodeWithText("下载并安装")
        val bounds = button.fetchSemanticsNode().boundsInRoot
        val density = RuntimeEnvironment.getApplication().resources.displayMetrics.density
        assertEquals(296f * density, bounds.width, 1f)
        assertEquals(44f * density, bounds.height, 1f)
        writeSettingsVisualArtifact("software-update-c17-native.png", compose.onRoot().captureToImage().asAndroidBitmap())
        button.performClick()
        compose.runOnIdle { assertEquals(1, downloads) }
    }
}
