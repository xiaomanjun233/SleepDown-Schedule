package com.xiaomanjun.sleepdownschedule.feature.settings.about

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.progressSemantics
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.itos.coui.button.CouiButtonDefaults
import com.itos.coui.button.CouiButtonStyle
import com.itos.coui.button.CouiCapsuleButton
import com.itos.coui.icons.CouiIcons
import com.itos.coui.shape.CouiCapsuleShape
import com.itos.coui.shape.CouiShape
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.rememberCouiTextStyle
import com.itos.coui.theme.CouiTheme
import com.kyant.backdrop.Backdrop
import com.xiaomanjun.sleepdownschedule.AppState
import com.xiaomanjun.sleepdownschedule.core.identity.AppDistribution
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.AddMenuAction
import com.xiaomanjun.sleepdownschedule.core.ui.scaffold.detailContentBottomPadding
import com.xiaomanjun.sleepdownschedule.core.ui.scaffold.detailContentTopPadding
import com.xiaomanjun.sleepdownschedule.core.ui.settings.CouiSettingsToolbarButton
import com.xiaomanjun.sleepdownschedule.core.web.openSleepDownCustomTab
import com.xiaomanjun.sleepdownschedule.core.web.resolveSleepDownCustomTabsPackage
import com.xiaomanjun.sleepdownschedule.feature.settings.SettingsPage
import com.xiaomanjun.sleepdownschedule.feature.update.*
import com.xiaomanjun.sleepdownschedule.platform.coloros.GlowOverflowMenuButton

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
    val downloadPercent = if (dialog is AppUpdateDialog.Downloading) {
        val downloadState by GiteeAppUpdater.downloadState.collectAsStateWithLifecycle()
        (downloadState as? UpdateDownloadState.Downloading)
            ?.takeIf { it.releaseTag == dialog.release.tagName }?.progressPercent
    } else null
    SoftwareUpdatePageContent(
        version = updates.versionName, dialog = dialog,
        onRetry = { if (AppDistribution.supportsSelfUpdate) updates.checkForUpdate() else open(SleepDownReleasesUrl) },
        onDownload = updates::downloadAndInstall, onOpenRelease = { open(it.releaseUrl) },
        onOpenBackup = { open(SleepDownReleasesUrl) },
        downloadPercent = downloadPercent,
        onOpenVersionNotes = onOpenVersionNotes, showVersionNotes = showVersionNotes,
    )
    if (updates.dialog == AppUpdateDialog.InstallPermissionRequired) {
        AppUpdateDialogHost(updates, backdrop, state.config,
            onRetry = { updates.checkForUpdate() }, onOpenRelease = { open(it.releaseUrl) },
            onOpenBackup = { open(SleepDownReleasesUrl); updates.dismiss() })
    }
}

@Composable
internal fun SoftwareUpdateMenu(backdrop: Backdrop?, onOpenPage: (SettingsPage) -> Unit) {
    val compact = LocalConfiguration.current.screenWidthDp < 600
    val dark = compact || CouiTheme.isDark
    val openPage by rememberUpdatedState(onOpenPage)
    val actions = remember {
        buildList {
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
        } else CouiSettingsToolbarButton(backdrop, onClick = open) {
            Icon(CouiIcons.More, "更多选项", Modifier.size(24.dp), tint = CouiTheme.colorScheme.onSurface)
        }
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

/** 手机用沉浸式状态卡，宽屏沿用 OTA 的独立头卡布局。 */
@Composable
internal fun SoftwareUpdatePageContent(
    version: String, dialog: AppUpdateDialog?, onRetry: () -> Unit,
    onDownload: (GiteeReleaseInfo) -> Unit, onOpenRelease: (GiteeReleaseInfo) -> Unit,
    onOpenBackup: () -> Unit, downloadPercent: Int? = null, onOpenVersionNotes: () -> Unit = onOpenBackup,
    showVersionNotes: Boolean = false,
) {
    val screenWidth = LocalConfiguration.current.screenWidthDp
    val release = when (dialog) {
        is AppUpdateDialog.Available -> dialog.release
        is AppUpdateDialog.Downloading -> dialog.release
        else -> null
    }
    val downloading = dialog is AppUpdateDialog.Downloading
    val action: Pair<String, () -> Unit>? = when (dialog) {
        null -> "检查更新" to onRetry
        is AppUpdateDialog.Available -> "下载并安装" to { onDownload(dialog.release) }
        is AppUpdateDialog.NoApk -> "查看发布页" to { onOpenRelease(dialog.release) }
        is AppUpdateDialog.Error -> "重试" to onRetry
        else -> null
    }
    val hasBackup = dialog is AppUpdateDialog.Error || dialog is AppUpdateDialog.NoApk
    val displayVersion = release?.tagName?.removePrefix("v") ?: version
    if (screenWidth < 600) {
        val upToDate = dialog is AppUpdateDialog.UpToDate
        ImmersiveSoftwareUpdateContent(
            version = displayVersion,
            installedVersion = version,
            dialog = dialog,
            releaseNotes = release?.notes,
            primary = if (upToDate) "重新检查" to onRetry else action,
            secondary = when {
                upToDate || dialog == null -> "查看更新说明" to onOpenVersionNotes
                hasBackup -> "备用下载" to onOpenBackup
                else -> null
            },
            downloadPercent = downloadPercent,
            showVersionNotes = showVersionNotes,
        )
        return
    }
    val top = detailContentTopPadding()
    val bottom = detailContentBottomPadding() + 8.dp
    AnimatedContent(showVersionNotes, Modifier.fillMaxSize(),
        transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "wide-software-update") { showingNotes ->
        if (showingNotes) {
            Box(Modifier.fillMaxSize().padding(start = 24.dp, end = 24.dp, top = top + 12.dp, bottom = bottom),
                contentAlignment = Alignment.TopCenter) {
                SoftwareUpdateVersionNotes(version, wallpaper = null, modifier = Modifier.widthIn(max = 660.dp))
            }
        } else Column(Modifier.fillMaxSize().padding(bottom = bottom), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(top = top + 12.dp, bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.padding(horizontal = 24.dp).widthIn(max = 660.dp).fillMaxWidth()) {
                SoftwareUpdateHero(displayVersion, updateAvailable = release != null)
                SoftwareUpdateDetails(dialog, release, displayVersion, onRetry, onOpenVersionNotes)
            }
        }
        if (action != null || downloading) {
            // OTA 在绑定进度按钮时按窗口类别覆盖 InstallDownloadLarge 的资源宽度。
            val buttonWidth = if (screenWidth in 600..839) 280.dp else 296.dp
            val label = action?.first ?: downloadPercent?.let { "正在下载 · $it%" } ?: "正在下载"
            Box(Modifier.padding(horizontal = 24.dp)) {
                SoftwareUpdateAction(label, buttonWidth, action?.second, downloadPercent)
            }
            if (hasBackup) SoftwareUpdateTextButton("备用下载", onOpenBackup)
        }
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
                Box(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 20.dp)
                    .height(0.33.dp).background(secondary.copy(alpha = .24f)))
                Text("更新内容", color = foreground, style = titleStyle)
                Text(release.notes.trim().ifEmpty { "此版本未提供更新说明。" }, Modifier.padding(top = 8.dp),
                    color = secondary, style = summaryStyle)
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
                    CouiCapsuleButton(
                        modifier = Modifier.padding(top = 8.dp),
                        onClick = onRetry, dark = dark, height = 40.dp, colors = buttonColors,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), pressFeedback = true,
                    ) {
                        Text("重新检查", color = buttonColors.content,
                            style = rememberCouiTextStyle(CouiTextKind.Progress))
                    }
                    SoftwareUpdateTextButton("查看更新说明", onOpenVersionNotes)
                }
            }
        }
    }
}

@Composable
private fun SoftwareUpdateTextButton(label: String, onClick: () -> Unit) {
    val dark = CouiTheme.isDark
    CouiCapsuleButton(
        onClick = onClick, dark = dark, height = 40.dp,
        colors = CouiButtonDefaults.secondaryColors(dark).copy(surface = Color.Transparent),
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
    label: String, width: Dp, onClick: (() -> Unit)?, percent: Int?, lightSurface: Boolean = false,
) {
    val colors = CouiTheme.colorScheme
    val accent = if (lightSurface) Color(0xFFEFEFF3) else colors.accent
    val onAccent = if (lightSurface) Color(0xFF15151B) else colors.onAccent
    val widthModifier = Modifier.widthIn(max = width).fillMaxWidth()
    val labelStyle = rememberCouiTextStyle(if (lightSurface) CouiTextKind.Button else CouiTextKind.Progress)
    if (onClick != null) {
        CouiCapsuleButton(
            modifier = widthModifier,
            onClick = onClick, dark = CouiTheme.isDark && !lightSurface, height = 44.dp,
            colors = CouiButtonDefaults.primaryColors(accent).copy(content = onAccent),
            style = CouiButtonStyle.Primary,
            shape = CouiCapsuleShape,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            pressFeedback = true,
        ) {
            Text(label, color = onAccent, style = labelStyle)
        }
        return
    }
    val fraction = percent?.div(100f)
    val progress = if (fraction == null) Modifier.progressSemantics() else Modifier.progressSemantics(fraction)
    Box(
        modifier = widthModifier.heightIn(min = 44.dp)
            .clip(CouiCapsuleShape)
            .background(accent.copy(alpha = .35f)).then(progress),
        contentAlignment = Alignment.Center,
    ) {
        val labelPadding = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        Text(label, labelPadding, color = if (lightSurface) Color.White else colors.onSurface, style = labelStyle)
        if (fraction != null) {
            // 原厂下载组件在填充区内单独裁切进度色和文字色。
            Box(
                Modifier.matchParentSize().drawWithContent {
                    val filled = size.width * fraction
                    val rtl = layoutDirection == LayoutDirection.Rtl
                    clipRect(left = if (rtl) size.width - filled else 0f,
                        right = if (rtl) size.width else filled) { this@drawWithContent.drawContent() }
                }.background(accent),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, labelPadding.clearAndSetSemantics {}, color = onAccent, style = labelStyle)
            }
        }
    }
}
