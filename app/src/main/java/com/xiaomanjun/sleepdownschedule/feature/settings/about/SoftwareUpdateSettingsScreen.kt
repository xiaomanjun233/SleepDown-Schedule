package com.xiaomanjun.sleepdownschedule.feature.settings.about

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.itos.coui.button.CouiButtonDefaults
import com.itos.coui.button.CouiCapsuleButton
import com.itos.coui.button.CouiProgressButton
import com.itos.coui.icons.CouiIcons
import com.itos.coui.shape.CouiCapsuleShape
import com.itos.coui.shape.CouiShape
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.rememberCouiTextStyle
import com.itos.coui.theme.CouiTheme
import com.kyant.backdrop.Backdrop
import com.xiaomanjun.sleepdownschedule.AppState
import com.xiaomanjun.sleepdownschedule.R
import com.xiaomanjun.sleepdownschedule.core.identity.AppDistribution
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.AddMenuAction
import com.xiaomanjun.sleepdownschedule.core.ui.scaffold.detailContentBottomPadding
import com.xiaomanjun.sleepdownschedule.core.ui.scaffold.detailContentTopPadding
import com.xiaomanjun.sleepdownschedule.core.ui.settings.CouiSettingsToolbarButton
import com.xiaomanjun.sleepdownschedule.core.web.openSleepDownCustomTab
import com.xiaomanjun.sleepdownschedule.core.web.resolveSleepDownCustomTabsPackage
import com.xiaomanjun.sleepdownschedule.feature.settings.SettingsPage
import com.xiaomanjun.sleepdownschedule.feature.update.*
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.GlowOverflowMenuButton
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** 独立全屏软件更新页，沿用既有检查、下载、安装及底部权限窗口。 */
@Composable
internal fun SoftwareUpdateSettingsScreen(
    state: AppState, backdrop: Backdrop?, showVersionNotes: Boolean, onOpenVersionNotes: () -> Unit,
) {
    val context = LocalContext.current
    val updates = rememberAppUpdateSession()
    val dark = CouiTheme.isDark
    val browser = remember(context) { context.resolveSleepDownCustomTabsPackage() }
    val width = LocalConfiguration.current.screenWidthDp
    val density = LocalDensity.current
    fun open(url: String) = context.openSleepDownCustomTab(url, providerPackage = browser,
        initialWidthPx = with(density) { (width.dp * .68f).roundToPx() }, darkTheme = dark)
    var lastBetaPreference by remember(updates) { mutableStateOf<Boolean?>(null) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (AppDistribution.supportsSelfUpdate) {
            val includeBeta = GiteeAppUpdater.includesBeta(context)
            if (includeBeta != lastBetaPreference) {
                lastBetaPreference = includeBeta
                updates.checkForUpdate()
            }
        }
    }
    val dialog = updates.dialog
    val release = updates.availableRelease
    var openedReleaseTag by rememberSaveable { mutableStateOf<String?>(null) }
    val openNotes by rememberUpdatedState(onOpenVersionNotes)
    LaunchedEffect(release?.tagName) {
        val tag = release?.tagName ?: return@LaunchedEffect
        if (openedReleaseTag != tag) {
            openedReleaseTag = tag
            openNotes()
        }
    }
    val downloadPercent = if (dialog is AppUpdateDialog.Downloading) {
        val tag = dialog.release.tagName
        val progress = remember(tag) {
            GiteeAppUpdater.downloadState.map { state ->
                when (state) {
                    is UpdateDownloadState.Downloading -> state.progressPercent.takeIf { state.releaseTag == tag }
                    is UpdateDownloadState.Completed -> 100.takeIf { state.releaseTag == tag }
                    else -> null
                }
            }.distinctUntilChanged()
        }
        progress.collectAsStateWithLifecycle(initialValue = null).value
    } else null
    SoftwareUpdatePageContent(
        version = updates.versionName, dialog = dialog,
        onRetry = { if (AppDistribution.supportsSelfUpdate) updates.checkForUpdate() else open(SleepDownReleasesUrl) },
        onDownload = updates::downloadAndInstall, onOpenRelease = { open(it.releaseUrl) },
        onOpenBackup = { open(SleepDownReleasesUrl) },
        downloadPercent = downloadPercent,
        onOpenVersionNotes = onOpenVersionNotes, showVersionNotes = showVersionNotes,
        release = release,
        downloaded = release != null && updates.downloadedReleaseTag == release.tagName,
    )
    if (updates.dialog == AppUpdateDialog.InstallPermissionRequired) {
        AppUpdateDialogHost(updates, backdrop, state.config,
            onRetry = { updates.checkForUpdate() }, onOpenRelease = { open(it.releaseUrl) },
            onOpenBackup = { open(SleepDownReleasesUrl); updates.dismiss() })
    }
}

@Composable
internal fun SoftwareUpdateMenu(
    backdrop: Backdrop?,
    onReplayWelcome: () -> Unit,
    onOpenPage: (SettingsPage) -> Unit,
) {
    val highlights = stringResource(R.string.software_update_view_highlights)
    val compact = LocalConfiguration.current.screenWidthDp < 600
    val dark = compact || CouiTheme.isDark
    val openPage by rememberUpdatedState(onOpenPage)
    val replay by rememberUpdatedState(onReplayWelcome)
    val actions = remember(highlights) {
        buildList {
            add(AddMenuAction(label = highlights) { replay() })
            if (AppDistribution.supportsSelfUpdate) {
                add(AddMenuAction(label = "自动更新设置") { openPage(SettingsPage.AutoUpdate) })
            }
            add(AddMenuAction(label = "历史日志") { openPage(SettingsPage.Changelog) })
        }
    }
    val panelColor = if (compact) SoftwareUpdateToolbarColors.surface.compositeOver(SoftwareUpdateBackground)
        else Color.Unspecified
    GlowOverflowMenuButton(dark, interactive = true, actions, Modifier, containerColor = panelColor,
        menuWidth = 164.dp) { open ->
        if (compact) {
            SoftwareUpdateToolbarButton(CouiIcons.More, "更多选项", open)
        } else WideSoftwareUpdateToolbarButton(CouiIcons.More, "更多选项", backdrop, onClick = open)
    }
}

internal val SoftwareUpdateBackground = Color(0xFF12152D)
internal val SoftwareUpdateToolbarColors = CouiButtonDefaults.secondaryColors(dark = true, material = true).copy(
    surface = Color.White.copy(alpha = .15f),
)

@Composable
internal fun SoftwareUpdateToolbarButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
        CouiCapsuleButton(
            modifier = Modifier.width(40.dp),
            onClick = onClick, dark = true, height = 40.dp,
            colors = SoftwareUpdateToolbarColors,
            contentPadding = PaddingValues(0.dp),
        ) {
            Icon(icon, label, Modifier.size(24.dp), tint = Color.White)
        }
    }
}

@Composable
internal fun WideSoftwareUpdateToolbarButton(
    icon: ImageVector, label: String, backdrop: Backdrop? = null, onClick: () -> Unit,
) {
    CouiSettingsToolbarButton(backdrop, onClick = onClick, emphasizeLightShadow = true) {
        Icon(icon, label, Modifier.size(24.dp), tint = CouiTheme.colorScheme.onSurface)
    }
}

/** 手机用沉浸式状态卡，宽屏沿用 OTA 的独立头卡布局。 */
@Composable
internal fun SoftwareUpdatePageContent(
    version: String, dialog: AppUpdateDialog?, onRetry: () -> Unit,
    onDownload: (GiteeReleaseInfo) -> Unit, onOpenRelease: (GiteeReleaseInfo) -> Unit,
    onOpenBackup: () -> Unit, downloadPercent: Int? = null, onOpenVersionNotes: () -> Unit = onOpenBackup,
    showVersionNotes: Boolean = false,
    release: GiteeReleaseInfo? = when (dialog) {
        is AppUpdateDialog.Available -> dialog.release
        is AppUpdateDialog.Downloading -> dialog.release
        is AppUpdateDialog.NoApk -> dialog.release
        else -> null
    },
    downloaded: Boolean = false,
) {
    val screenWidth = LocalConfiguration.current.screenWidthDp
    val compact = screenWidth < 600
    val statusAction: Pair<String, () -> Unit>? = when {
        release != null -> null
        dialog == null -> "检查更新" to onRetry
        dialog is AppUpdateDialog.UpToDate && compact -> "重新检查" to onRetry
        dialog is AppUpdateDialog.Error -> "重试" to onRetry
        else -> null
    }
    val hasBackup = release == null && dialog is AppUpdateDialog.Error
    val displayVersion = release?.tagName?.removePrefix("v") ?: version
    val actionWidth = if (screenWidth in 600..839) 280.dp else 296.dp
    val footerClearance = if (release == null) 0.dp else 64.dp
    val bottom = detailContentBottomPadding() + 8.dp
    val downloadError = (dialog as? AppUpdateDialog.Error)?.message.takeIf { release != null }
    Box(Modifier.fillMaxSize()) {
        if (compact) {
            ImmersiveSoftwareUpdateContent(
                version = displayVersion, dialog = dialog, release = release,
                primary = statusAction,
                secondary = when {
                    release != null || dialog is AppUpdateDialog.UpToDate || dialog == null ->
                        "查看更新说明" to onOpenVersionNotes
                    hasBackup -> "备用下载" to onOpenBackup
                    else -> null
                },
                showVersionNotes = showVersionNotes,
                footerClearance = footerClearance,
            )
        } else {
            val top = detailContentTopPadding()
            AnimatedContent(showVersionNotes, Modifier.fillMaxSize(),
                transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "wide-software-update") { showingNotes ->
                Column(Modifier.fillMaxSize().padding(bottom = bottom + footerClearance),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Column(
                        modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                            .padding(start = 24.dp, end = 24.dp, top = top + 12.dp, bottom = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        if (showingNotes) {
                            val lightFrame = !CouiTheme.isDark
                            SoftwareUpdateCard(null, Modifier.widthIn(max = 660.dp), lightFrame = lightFrame) {
                                SoftwareUpdateVersionNotes(displayVersion, release?.notes, lightFrame, downloadError)
                            }
                        } else {
                            Column(Modifier.widthIn(max = 660.dp).fillMaxWidth()) {
                                SoftwareUpdateHero(displayVersion, updateAvailable = release != null)
                                SoftwareUpdateDetails(dialog, release, displayVersion, onRetry, onOpenVersionNotes)
                            }
                        }
                    }
                    if (!showingNotes && statusAction != null) {
                        SoftwareUpdateAction(statusAction.first, statusAction.second,
                            Modifier.padding(horizontal = 24.dp).widthIn(max = actionWidth).fillMaxWidth())
                        if (hasBackup) SoftwareUpdateTextButton("备用下载", onOpenBackup)
                    }
                }
            }
        }
        if (release != null) {
            val downloading = dialog is AppUpdateDialog.Downloading
            val label = when {
                downloading -> downloadPercent?.let { "正在下载 · $it%" } ?: "正在下载"
                release.apkUrl == null -> "查看发布页"
                downloaded -> "安装更新"
                downloadError != null -> "重试下载"
                else -> "下载并安装"
            }
            val action: (() -> Unit)? = when {
                downloading -> null
                release.apkUrl == null -> { { onOpenRelease(release) } }
                else -> { { onDownload(release) } }
            }
            SoftwareUpdateAction(label, action,
                Modifier.align(Alignment.BottomCenter)
                    .padding(start = 24.dp, end = 24.dp, bottom = bottom)
                    .widthIn(max = actionWidth).fillMaxWidth(),
                percent = downloadPercent, lightSurface = compact, raised = true,
            )
        }
    }
}

@Composable
private fun SoftwareUpdateHero(version: String, updateAvailable: Boolean) {
    val foreground = Color.White
    val density = LocalDensity.current
    // logo_area 的常规卡高 240dp，V1/Q 绑定后的升级卡使用 343dp。
    Box(
        modifier = Modifier.fillMaxWidth().heightIn(min = if (updateAvailable) 343.dp else 240.dp)
            .clip(CouiShape(16.dp, 2.5f)),
        contentAlignment = Alignment.Center,
    ) {
        VersionCardArtwork(Modifier.matchParentSize())
        if (updateAvailable) {
            Text("发现新版本", Modifier.align(Alignment.TopCenter).padding(top = 32.dp, start = 16.dp, end = 16.dp),
                color = foreground, textAlign = TextAlign.Center,
                style = rememberCouiTextStyle(CouiTextKind.PreferenceTitle).copy(fontSize = with(density) { 18.dp.toSp() }))
        }
        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(SleepDownFullName, color = foreground, textAlign = TextAlign.Center,
                style = rememberCouiTextStyle(CouiTextKind.PageTitle).copy(fontSize = 26.sp, lineHeight = 32.sp))
            Text(version, color = foreground.copy(alpha = .85f), textAlign = TextAlign.Center,
                style = rememberCouiTextStyle(CouiTextKind.PreferenceValue))
        }
    }
}

@Composable
private fun SoftwareUpdateDetails(
    dialog: AppUpdateDialog?, release: GiteeReleaseInfo?, version: String,
    onRetry: () -> Unit, onOpenVersionNotes: () -> Unit,
) {
    val colors = CouiTheme.colorScheme
    val foreground = colors.onSurface
    val secondary = colors.onSurfaceVariant
    val titleStyle = rememberCouiTextStyle(CouiTextKind.PreferenceTitle)
    val summaryStyle = rememberCouiTextStyle(CouiTextKind.PreferenceSummary)
    when {
        dialog == AppUpdateDialog.Checking -> {
            SoftwareUpdateChecking(foreground, Modifier.fillMaxWidth().padding(top = 16.dp)
                .clip(CouiShape(20.dp, 2.85f)).background(colors.container))
        }
        release != null -> {
            Column(Modifier.fillMaxWidth().padding(top = 24.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(release.name, color = foreground, style = titleStyle)
                Text(version, color = secondary, style = summaryStyle)
                if (dialog is AppUpdateDialog.Error) Text(dialog.message, color = secondary, style = summaryStyle)
                SoftwareUpdateTextButton("查看更新说明", onOpenVersionNotes)
            }
        }
        else -> {
            val (title, message) = when (dialog) {
                is AppUpdateDialog.UpToDate -> "已是最新版本" to version
                is AppUpdateDialog.NoApk -> "本次发布未附带安装包" to dialog.release.name
                is AppUpdateDialog.Error -> "检查更新失败" to dialog.message
                AppUpdateDialog.InstallPermissionRequired -> "安装包已下载" to "允许安装此来源的应用后继续。"
                else -> "软件版本" to version
            }
            Column(Modifier.fillMaxWidth().padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, color = foreground, style = titleStyle)
                Text(message, color = secondary, style = summaryStyle)
                if (dialog is AppUpdateDialog.UpToDate) {
                    val dark = CouiTheme.isDark
                    val buttonColors = CouiButtonDefaults.secondaryColors(dark)
                        .let { if (dark) it else it.copy(surface = colors.surface) }
                    CouiCapsuleButton(
                        modifier = Modifier.padding(top = 8.dp).wideUpdateButtonShadow(!dark),
                        onClick = onRetry, dark = dark, height = 40.dp, colors = buttonColors,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), pressFeedback = true,
                    ) {
                        Text("重新检查", color = buttonColors.content,
                            style = rememberCouiTextStyle(CouiTextKind.Progress))
                    }
                    SoftwareUpdateTextButton("查看更新说明", onOpenVersionNotes, raised = true)
                }
            }
        }
    }
}

@Composable
private fun SoftwareUpdateTextButton(label: String, onClick: () -> Unit, raised: Boolean = false) {
    val dark = CouiTheme.isDark
    CouiCapsuleButton(
        modifier = Modifier.wideUpdateButtonShadow(raised && !dark),
        onClick = onClick, dark = dark, height = 40.dp,
        colors = CouiButtonDefaults.secondaryColors(dark)
            .copy(surface = if (raised && !dark) CouiTheme.colorScheme.surface else Color.Transparent),
        contentPadding = PaddingValues(horizontal = 12.dp), pressFeedback = true,
    ) {
        Text(label, color = CouiTheme.colorScheme.accent, style = rememberCouiTextStyle(CouiTextKind.PreferenceSummary))
    }
}

@Composable
internal fun SoftwareUpdateChecking(foreground: Color, modifier: Modifier) {
    Row(
        modifier = modifier.heightIn(min = 64.dp).padding(start = 24.dp, end = 27.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("正在检查更新…", Modifier.weight(1f), color = foreground,
            style = rememberCouiTextStyle(CouiTextKind.PreferenceTitle))
        CircularProgressIndicator(Modifier.size(19.5.dp), color = foreground, strokeWidth = 2.dp)
    }
}

@Composable
internal fun SoftwareUpdateAction(
    label: String, onClick: (() -> Unit)?, modifier: Modifier = Modifier, percent: Int? = null,
    lightSurface: Boolean = false, raised: Boolean = false,
) {
    val colors = CouiTheme.colorScheme
    val accent = if (lightSurface) Color(0xFFEFEFF3) else colors.accent
    val onAccent = if (lightSurface) Color(0xFF15151B) else colors.onAccent
    CouiProgressButton(
        label = label, onClick = onClick, dark = CouiTheme.isDark && !lightSurface,
        colors = CouiButtonDefaults.primaryColors(accent).copy(content = onAccent),
        trackContentColor = if (lightSurface) Color.White else colors.onSurface,
        modifier = modifier.wideUpdateButtonShadow(raised && !lightSurface && !CouiTheme.isDark),
        progress = percent?.div(100f),
        textKind = if (lightSurface) CouiTextKind.Button else CouiTextKind.Progress,
    )
}

private fun Modifier.wideUpdateButtonShadow(enabled: Boolean): Modifier =
    if (enabled) shadow(5.dp, CouiCapsuleShape, clip = false,
        ambientColor = Color.Black.copy(alpha = .18f), spotColor = Color.Black.copy(alpha = .26f))
    else this
