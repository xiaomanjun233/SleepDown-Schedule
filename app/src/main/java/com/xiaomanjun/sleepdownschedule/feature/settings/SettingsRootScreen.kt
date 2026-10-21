package com.xiaomanjun.sleepdownschedule.feature.settings

import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import com.xiaomanjun.sleepdownschedule.core.ui.settings.GlassMiuixRootSettingsScaffold
import com.xiaomanjun.sleepdownschedule.core.ui.settings.GlassPreferenceSection
import com.xiaomanjun.sleepdownschedule.core.ui.settings.LocalSettingsSharedGradient
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsGroup
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsNavigationRow
import com.xiaomanjun.sleepdownschedule.core.web.openSleepDownCustomTab
import com.xiaomanjun.sleepdownschedule.core.web.resolveSleepDownCustomTabsPackage
import com.xiaomanjun.sleepdownschedule.feature.home.homeSwitchGroup
import com.xiaomanjun.sleepdownschedule.feature.settings.about.SleepDownReleasesUrl
import com.xiaomanjun.sleepdownschedule.feature.update.GiteeAppUpdater
import com.xiaomanjun.sleepdownschedule.feature.update.GiteeReleaseInfo
import com.xiaomanjun.sleepdownschedule.feature.update.GiteeUpdateCheckResult
import com.xiaomanjun.sleepdownschedule.glass.ui.appUsesDarkTheme
import java.io.File
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.utils.overScrollVertical

@Composable
fun SettingsRootScreen(
    state: AppState,
    backdrop: Backdrop?,
    selectedPage: SettingsPage? = null,
    onPageChange: (SettingsPage) -> Unit
) {
    val inheritedBackdrop = backdrop
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val darkTheme = appUsesDarkTheme(state.config)
    val customTabsPackage = remember(context) { context.resolveSleepDownCustomTabsPackage() }
    val customTabWidthPx = with(density) {
        (configuration.screenWidthDp.dp * 0.68f).roundToPx()
    }
    fun openExternalPage(url: String) {
        context.openSleepDownCustomTab(
            url = url,
            providerPackage = customTabsPackage,
            initialWidthPx = customTabWidthPx,
            darkTheme = darkTheme
        )
    }
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
    var updateDialog by remember { mutableStateOf<SettingsUpdateDialog?>(null) }
    var downloadedUpdate by remember { mutableStateOf<java.io.File?>(null) }
    val installPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        val apk = downloadedUpdate
        if (apk != null && GiteeAppUpdater.canRequestPackageInstalls(context)) {
            runCatching { GiteeAppUpdater.launchInstaller(context, apk) }
                .onFailure { updateDialog = SettingsUpdateDialog.Error(it.readableUpdateMessage()) }
        }
    }

    fun checkForUpdate() {
        if (updateDialog is SettingsUpdateDialog.Checking || updateDialog is SettingsUpdateDialog.Downloading) return
        updateDialog = SettingsUpdateDialog.Checking
        GiteeAppUpdater.markDailyCheckStarted(context)
        scope.launch {
            updateDialog = GiteeAppUpdater.checkForUpdate(context, versionName).fold(
                onSuccess = { result ->
                    GiteeAppUpdater.recordCheckResult(context, result)
                    when (result) {
                        is GiteeUpdateCheckResult.UpdateAvailable -> SettingsUpdateDialog.Available(result.release)
                        is GiteeUpdateCheckResult.UpToDate -> SettingsUpdateDialog.UpToDate(result.release.tagName)
                    }
                },
                onFailure = { SettingsUpdateDialog.Error(it.readableUpdateMessage()) }
            )
        }
    }

    fun downloadAndInstall(release: GiteeReleaseInfo) {
        if (release.apkUrl == null) {
            updateDialog = SettingsUpdateDialog.NoApk(release)
            return
        }
        updateDialog = SettingsUpdateDialog.Downloading(release)
        scope.launch {
            GiteeAppUpdater.downloadApk(context, release).fold(
                onSuccess = { apk ->
                    downloadedUpdate = apk
                    if (GiteeAppUpdater.canRequestPackageInstalls(context)) {
                        runCatching { GiteeAppUpdater.launchInstaller(context, apk) }
                            .onSuccess { updateDialog = null }
                            .onFailure { updateDialog = SettingsUpdateDialog.Error(it.readableUpdateMessage()) }
                    } else {
                        updateDialog = SettingsUpdateDialog.InstallPermissionRequired
                    }
                },
                onFailure = { updateDialog = SettingsUpdateDialog.Error(it.readableUpdateMessage()) }
            )
        }
    }
    GlassMiuixRootSettingsScaffold(
        title = "设置",
        config = state.config
    ) { innerPadding, pageBackdrop ->
        val backdrop = if (LocalSettingsSharedGradient.current != null) pageBackdrop else inheritedBackdrop
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .overScrollVertical(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = innerPadding.calculateTopPadding() + 8.dp,
                bottom = DockScrollPadding
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
        item {
            SettingsGroup(backdrop = backdrop, config = state.config, modifier = Modifier.fillMaxWidth().homeSwitchGroup()) {
                top.yukonga.miuix.kmp.preference.ArrowPreference(
                    title = appName,
                    summary = null,
                    startAction = {
                        Image(
                            painter = painterResource(currentIconResId(context, darkTheme)),
                            contentDescription = null,
                            modifier = Modifier
                                .padding(end = 10.dp)
                                .size(56.dp)
                                .clip(RoundedRectangle(16.dp))
                        )
                    },
                    endActions = {
                        Text(
                            "版本 $versionName",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            softWrap = true,
                            textAlign = TextAlign.End
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.onSurface.copy(
                                alpha = if (selectedPage == SettingsPage.Changelog) 0.10f else 0f
                            )
                        ),
                    insideMargin = PaddingValues(start = 14.dp, end = 22.dp, top = 12.dp, bottom = 12.dp),
                    onClick = { onPageChange(SettingsPage.Changelog) }
                )
                if (AppDistribution.supportsSelfUpdate) {
                    SettingsNavigationRow(
                        "检查更新",
                        leadingIcon = Icons.Rounded.SystemUpdate,
                        badgeText = when (updateDialog) {
                            SettingsUpdateDialog.Checking -> "检查中"
                            is SettingsUpdateDialog.Downloading -> "下载中"
                            else -> if (updateAvailable) "有新版" else null
                        },
                        onClick = ::checkForUpdate
                    )
                }
            }
        }
        item {
            GlassPreferenceSection("应用", modifier = Modifier.homeSwitchGroup()) {
                SettingsGroup(backdrop = backdrop, config = state.config, modifier = Modifier.fillMaxWidth()) {
                    SettingsNavigationRow(
                        "通用设置",
                        leadingIcon = Icons.Rounded.Settings,
                        leadingIconTint = ComposeColor(0xFF0A84FF),
                        selected = selectedPage == SettingsPage.General,
                        onClick = { onPageChange(SettingsPage.General) }
                    )
                    SettingsNavigationRow(
                        "小组件设置",
                        leadingIcon = Icons.Rounded.Widgets,
                        leadingIconTint = ComposeColor(0xFFFF9500),
                        selected = selectedPage == SettingsPage.Widgets,
                        onClick = { onPageChange(SettingsPage.Widgets) }
                    )
                    SettingsNavigationRow(
                        "当前课表详细设置",
                        leadingIcon = Icons.Rounded.Tune,
                        leadingIconTint = ComposeColor(0xFF5856D6),
                        selected = selectedPage == SettingsPage.Schedule,
                        onClick = { onPageChange(SettingsPage.Schedule) }
                    )
                    SettingsNavigationRow(
                        "自动刷新课表",
                        leadingIcon = Icons.Rounded.Sync,
                        leadingIconTint = ComposeColor(0xFF34C759),
                        badgeText = "实验功能",
                        selected = selectedPage == SettingsPage.AutoRefreshSchedule,
                        onClick = { onPageChange(SettingsPage.AutoRefreshSchedule) }
                    )
                    SettingsNavigationRow(
                        "通知设置",
                        leadingIcon = Icons.Rounded.Notifications,
                        leadingIconTint = ComposeColor(0xFF0A84FF),
                        selected = selectedPage == SettingsPage.Notifications,
                        onClick = { onPageChange(SettingsPage.Notifications) }
                    )
                }
            }
        }
        item {
            GlassPreferenceSection("智能助手", modifier = Modifier.homeSwitchGroup()) {
                SettingsGroup(backdrop = backdrop, config = state.config, modifier = Modifier.fillMaxWidth()) {
                    SettingsNavigationRow(
                        "AI 设置",
                        leadingIcon = Icons.Rounded.AutoAwesome,
                        leadingIconTint = ComposeColor(0xFFAF52DE),
                        selected = selectedPage == SettingsPage.AiImport,
                        onClick = { onPageChange(SettingsPage.AiImport) }
                    )
                    SettingsNavigationRow(
                        "AI 助理",
                        leadingIcon = Icons.Rounded.ChatBubble,
                        leadingIconTint = ComposeColor(0xFF00A9C7),
                        selected = selectedPage == SettingsPage.DayAgent,
                        onClick = { onPageChange(SettingsPage.DayAgent) }
                    )
                }
            }
        }
        item {
            GlassPreferenceSection("其他", modifier = Modifier.homeSwitchGroup()) {
                SettingsGroup(backdrop = backdrop, config = state.config, modifier = Modifier.fillMaxWidth()) {
                    SettingsNavigationRow(
                        "备份与恢复",
                        leadingIcon = Icons.Rounded.SettingsBackupRestore,
                        leadingIconTint = ComposeColor(0xFF0A84FF),
                        selected = selectedPage == SettingsPage.BackupRestore,
                        onClick = { onPageChange(SettingsPage.BackupRestore) }
                    )
                    SettingsNavigationRow(
                        "捐赠支持",
                        leadingIcon = Icons.Rounded.Favorite,
                        leadingIconTint = ComposeColor(0xFFFF2D55),
                        selected = selectedPage == SettingsPage.Donate,
                        onClick = { onPageChange(SettingsPage.Donate) }
                    )
                }
            }
        }
        }
    }

    SettingsUpdateDialogHost(
        dialog = updateDialog,
        backdrop = backdrop,
        config = state.config,
        onDismiss = { updateDialog = null },
        onRetry = ::checkForUpdate,
        onDownload = ::downloadAndInstall,
        onOpenRelease = { release ->
            openExternalPage(release.releaseUrl)
        },
        onOpenBackup = {
            openExternalPage(SleepDownReleasesUrl)
            updateDialog = null
        },
        onRequestInstallPermission = {
            installPermissionLauncher.launch(GiteeAppUpdater.unknownSourcesSettingsIntent(context))
        }
    )
}
