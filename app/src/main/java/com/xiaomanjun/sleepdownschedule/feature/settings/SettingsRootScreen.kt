package com.xiaomanjun.sleepdownschedule.feature.settings

import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SettingsBackupRestore
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kyant.backdrop.Backdrop
import com.kyant.shapes.RoundedRectangle
import com.xiaomanjun.sleepdownschedule.AppState
import com.xiaomanjun.sleepdownschedule.core.identity.AppDistribution
import com.xiaomanjun.sleepdownschedule.core.identity.currentIconResId
import com.xiaomanjun.sleepdownschedule.core.ui.chrome.DockScrollPadding
import com.xiaomanjun.sleepdownschedule.core.ui.settings.CouiSettingsRootScaffold
import com.xiaomanjun.sleepdownschedule.core.ui.settings.LocalSettingsSharedGradient
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsGroup
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsDivider
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsNavigationRow
import com.xiaomanjun.sleepdownschedule.core.web.openSleepDownCustomTab
import com.xiaomanjun.sleepdownschedule.core.web.resolveSleepDownCustomTabsPackage
import com.xiaomanjun.sleepdownschedule.feature.home.homeSwitchGroup
import com.xiaomanjun.sleepdownschedule.feature.settings.about.SleepDownReleasesUrl
import com.xiaomanjun.sleepdownschedule.feature.update.GiteeAppUpdater
import com.xiaomanjun.sleepdownschedule.feature.update.AppUpdateDialog
import com.xiaomanjun.sleepdownschedule.feature.update.AppUpdateDialogHost
import com.xiaomanjun.sleepdownschedule.feature.update.rememberAppUpdateSession
import com.xiaomanjun.sleepdownschedule.core.ui.theme.appUsesDarkTheme
import java.io.File
import top.yukonga.miuix.kmp.utils.overScrollVertical
import com.itos.coui.scroll.rememberCouiOverscrollEffect
import com.xiaomanjun.sleepdownschedule.BuildConfig
import com.xiaomanjun.sleepdownschedule.core.ui.settings.LocalCouiSettingsListState
import com.xiaomanjun.sleepdownschedule.core.ui.settings.CouiSettingsSearch
import com.xiaomanjun.sleepdownschedule.core.ui.settings.LocalCouiSettingsSearch
import com.xiaomanjun.sleepdownschedule.core.ui.settings.LocalCouiSettingsListLayout
import com.xiaomanjun.sleepdownschedule.core.ui.settings.LocalCouiSettingsContentPadding
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsArrowPreference
import androidx.compose.foundation.lazy.rememberLazyListState

@Composable
fun SettingsRootScreen(
    state: AppState,
    backdrop: Backdrop?,
    selectedPage: SettingsPage? = null,
    onPageChange: (SettingsPage) -> Unit
) {
    val inheritedBackdrop = backdrop
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    val searchResults = remember(searchQuery) { settingsSearchResults(searchQuery) }
    val context = LocalContext.current
    val darkTheme = appUsesDarkTheme(state.config)
    val versionName = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0"
        }.getOrDefault("1.0")
    }
    val appName = remember {
        runCatching {
            context.packageManager.getApplicationLabel(context.applicationInfo).toString()
        }.getOrDefault("SleepDown课程表")
    }

    val updateAvailable by GiteeAppUpdater.updateAvailable.collectAsStateWithLifecycle()
    LaunchedEffect(versionName) {
        GiteeAppUpdater.restoreCachedStatus(context, versionName)
    }
    CompositionLocalProvider(LocalCouiSettingsSearch provides CouiSettingsSearch(
        searchQuery, searchExpanded, { searchQuery = it }, { expanded ->
            searchExpanded = expanded
            if (!expanded) searchQuery = ""
        },
    )) {
    CouiSettingsRootScaffold(
        title = "设置",
        config = state.config
    ) { innerPadding, pageBackdrop ->
        val backdrop = if (BuildConfig.SLEEPDOWN_COUI_SETTINGS && android.os.Build.VERSION.SDK_INT < 31) null
            else if (BuildConfig.SLEEPDOWN_COUI_SETTINGS || LocalSettingsSharedGradient.current != null) pageBackdrop else inheritedBackdrop
        CompositionLocalProvider(LocalCouiSettingsListLayout provides BuildConfig.SLEEPDOWN_COUI_SETTINGS) {
        LazyColumn(
            state = LocalCouiSettingsListState.current ?: rememberLazyListState(),
            overscrollEffect = if (BuildConfig.SLEEPDOWN_COUI_SETTINGS) rememberCouiOverscrollEffect()
                else androidx.compose.foundation.rememberOverscrollEffect(),
            modifier = Modifier
                .fillMaxSize()
                .consumeWindowInsets(LocalCouiSettingsContentPadding.current ?: innerPadding)
                .then(if (BuildConfig.SLEEPDOWN_COUI_SETTINGS) Modifier else Modifier.overScrollVertical()),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = innerPadding.calculateTopPadding() + if (BuildConfig.SLEEPDOWN_COUI_SETTINGS) 0.dp else 8.dp,
                bottom = DockScrollPadding
            ),
            verticalArrangement = Arrangement.spacedBy(if (BuildConfig.SLEEPDOWN_COUI_SETTINGS) 0.dp else 12.dp)
        ) {
        if (BuildConfig.SLEEPDOWN_COUI_SETTINGS && searchQuery.isNotBlank()) {
            if (searchResults.isEmpty()) item(key = "search-empty") {
                Text("没有找到相关设置", modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (searchResults.isNotEmpty()) item(key = "settings-search-results") {
                SettingsGroup(backdrop = backdrop, config = state.config, topSpacing = 12.dp) {
                    searchResults.forEachIndexed { index, entry ->
                        if (index > 0) SettingsDivider()
                        SettingsNavigationRow(title = entry.title, subtitle = "设置",
                            selected = selectedPage == entry.page, onClick = { onPageChange(entry.page) })
                    }
                }
            }
        } else {
        item {
            SettingsGroup(backdrop = backdrop, config = state.config, modifier = Modifier.fillMaxWidth().homeSwitchGroup(),
                topSpacing = 12.dp) {
                SettingsArrowPreference(
                    title = appName,
                    summary = "版本 $versionName" + if (updateAvailable) " · 发现新版本" else "",
                    iconSize = 56.dp,
                    startAction = {
                        Image(painter = painterResource(currentIconResId(context, darkTheme)),
                            contentDescription = null,
                            modifier = Modifier.size(56.dp).clip(com.itos.coui.shape.CouiShape(16.dp)))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    insideMargin = PaddingValues(16.dp),
                    onClick = { onPageChange(SettingsPage.About) }
                )
            }
        }
        item {
                SettingsGroup(backdrop = backdrop, config = state.config, modifier = Modifier.fillMaxWidth().homeSwitchGroup()) {
                    SettingsNavigationRow(
                        "通用设置",
                        leadingIcon = Icons.Rounded.Settings,
                        leadingIconTint = ComposeColor(0xFF0A84FF),
                        selected = selectedPage == SettingsPage.General,
                        minHeight = 52.dp, onClick = { onPageChange(SettingsPage.General) }
                    )
                    SettingsDivider(startIndent = 56.dp)
                    SettingsNavigationRow(
                        "小组件设置",
                        leadingIcon = Icons.Rounded.Widgets,
                        leadingIconTint = ComposeColor(0xFFFF9500),
                        selected = selectedPage == SettingsPage.Widgets,
                        minHeight = 52.dp, onClick = { onPageChange(SettingsPage.Widgets) }
                    )
                    SettingsDivider(startIndent = 56.dp)
                    SettingsNavigationRow(
                        "当前课表详细设置",
                        leadingIcon = Icons.Rounded.Tune,
                        leadingIconTint = ComposeColor(0xFF5856D6),
                        selected = selectedPage == SettingsPage.Schedule,
                        minHeight = 52.dp, onClick = { onPageChange(SettingsPage.Schedule) }
                    )
                    SettingsDivider(startIndent = 56.dp)
                    SettingsNavigationRow(
                        "自动刷新课表",
                        leadingIcon = Icons.Rounded.Sync,
                        leadingIconTint = ComposeColor(0xFF34C759),
                        badgeText = "实验功能",
                        selected = selectedPage == SettingsPage.AutoRefreshSchedule,
                        minHeight = 52.dp, onClick = { onPageChange(SettingsPage.AutoRefreshSchedule) }
                    )
                    SettingsDivider(startIndent = 56.dp)
                    SettingsNavigationRow(
                        "通知设置",
                        leadingIcon = Icons.Rounded.Notifications,
                        leadingIconTint = ComposeColor(0xFF0A84FF),
                        selected = selectedPage == SettingsPage.Notifications,
                        minHeight = 52.dp, onClick = { onPageChange(SettingsPage.Notifications) }
                    )
                }
        }
        item {
                SettingsGroup(backdrop = backdrop, config = state.config, modifier = Modifier.fillMaxWidth().homeSwitchGroup()) {
                    SettingsNavigationRow(
                        "AI 设置",
                        leadingIcon = Icons.Rounded.AutoAwesome,
                        leadingIconTint = ComposeColor(0xFFAF52DE),
                        selected = selectedPage == SettingsPage.AiImport,
                        minHeight = 52.dp, onClick = { onPageChange(SettingsPage.AiImport) }
                    )
                    SettingsDivider(startIndent = 56.dp)
                    SettingsNavigationRow(
                        "AI 助理",
                        leadingIcon = Icons.Rounded.ChatBubble,
                        leadingIconTint = ComposeColor(0xFF00A9C7),
                        selected = selectedPage == SettingsPage.DayAgent,
                        minHeight = 52.dp, onClick = { onPageChange(SettingsPage.DayAgent) }
                    )
                }
        }
        item {
                SettingsGroup(backdrop = backdrop, config = state.config, modifier = Modifier.fillMaxWidth().homeSwitchGroup()) {
                    SettingsNavigationRow(
                        "备份与恢复",
                        leadingIcon = Icons.Rounded.SettingsBackupRestore,
                        leadingIconTint = ComposeColor(0xFF0A84FF),
                        selected = selectedPage == SettingsPage.BackupRestore,
                        minHeight = 52.dp, onClick = { onPageChange(SettingsPage.BackupRestore) }
                    )
                    SettingsDivider(startIndent = 56.dp)
                    SettingsNavigationRow(
                        "捐赠支持",
                        leadingIcon = Icons.Rounded.Favorite,
                        leadingIconTint = ComposeColor(0xFFFF2D55),
                        selected = selectedPage == SettingsPage.Donate,
                        minHeight = 52.dp, onClick = { onPageChange(SettingsPage.Donate) }
                    )
                }
        }
        }
        }
        }
    }
    }
}
