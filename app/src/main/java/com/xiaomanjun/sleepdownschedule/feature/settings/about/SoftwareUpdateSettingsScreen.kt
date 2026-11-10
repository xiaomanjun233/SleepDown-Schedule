package com.xiaomanjun.sleepdownschedule.feature.settings.about

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.itos.coui.effect.couiButtonFeedback
import com.itos.coui.icons.CouiIcons
import com.itos.coui.menu.CouiMenuItem
import com.itos.coui.menu.Menu
import com.itos.coui.shape.CouiCapsuleShape
import com.itos.coui.shape.CouiShape
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.rememberCouiTextStyle
import com.itos.coui.theme.CouiTheme
import com.kyant.backdrop.Backdrop
import com.xiaomanjun.sleepdownschedule.AppState
import com.xiaomanjun.sleepdownschedule.R
import com.xiaomanjun.sleepdownschedule.core.identity.AppDistribution
import com.xiaomanjun.sleepdownschedule.core.ui.scaffold.detailContentBottomPadding
import com.xiaomanjun.sleepdownschedule.core.ui.scaffold.detailContentTopPadding
import com.xiaomanjun.sleepdownschedule.core.ui.settings.CouiSettingsToolbarButton
import com.xiaomanjun.sleepdownschedule.core.web.openSleepDownCustomTab
import com.xiaomanjun.sleepdownschedule.core.web.resolveSleepDownCustomTabsPackage
import com.xiaomanjun.sleepdownschedule.feature.settings.SettingsPage
import com.xiaomanjun.sleepdownschedule.feature.update.*

/** 独立全屏软件更新页，沿用既有检查、下载、安装及底部权限窗口。 */
@Composable
internal fun SoftwareUpdateSettingsScreen(state: AppState, backdrop: Backdrop?) {
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
    )
    if (updates.dialog == AppUpdateDialog.InstallPermissionRequired) {
        AppUpdateDialogHost(updates, backdrop, state.config,
            onRetry = { updates.checkForUpdate() }, onOpenRelease = { open(it.releaseUrl) },
            onOpenBackup = { open(SleepDownReleasesUrl); updates.dismiss() })
    }
}

@Composable
internal fun SoftwareUpdateMenu(backdrop: Backdrop?, onOpenPage: (SettingsPage) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var destination by remember { mutableStateOf<SettingsPage?>(null) }
    val items = remember {
        buildList {
            if (AppDistribution.supportsSelfUpdate) add(CouiMenuItem(SettingsPage.AutoUpdate, "自动更新设置"))
            add(CouiMenuItem(SettingsPage.Changelog, "历史日志"))
        }
    }
    Box {
        CouiSettingsToolbarButton(backdrop, onClick = { expanded = true }) {
            Icon(CouiIcons.More, "更多选项", Modifier.size(24.dp), tint = CouiTheme.colorScheme.onSurface)
        }
        Menu(
            expanded = expanded,
            items = items,
            onDismissRequest = { expanded = false },
            onItemClick = {
                destination = it.value
                expanded = false
            },
            onDismissFinished = {
                val page = destination
                destination = null
                page?.let(onOpenPage)
            },
        )
    }
}

/** 对应系统更新的沉浸式窄屏和宽屏头卡，底部操作与正文滚动分开。 */
@Composable
internal fun SoftwareUpdatePageContent(
    version: String, dialog: AppUpdateDialog?, onRetry: () -> Unit,
    onDownload: (GiteeReleaseInfo) -> Unit, onOpenRelease: (GiteeReleaseInfo) -> Unit,
    onOpenBackup: () -> Unit, downloadPercent: Int? = null,
) {
    val screenWidth = LocalConfiguration.current.screenWidthDp
    val immersive = screenWidth < 600
    val colors = CouiTheme.colorScheme
    val foreground = if (immersive) Color.White else colors.onSurface
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
    val top = detailContentTopPadding()
    val bottom = detailContentBottomPadding() + 8.dp
    Box(Modifier.fillMaxSize()) {
        if (immersive) {
            Box(Modifier.matchParentSize().background(Color(0xFF111329))) {
                Image(painterResource(R.drawable.about_sleepdown_hero), null, Modifier.matchParentSize(),
                    alignment = Alignment.CenterEnd, contentScale = ContentScale.Crop)
                Box(Modifier.matchParentSize().background(Brush.verticalGradient(
                    0f to Color(0xFF111329),
                    .32f to Color(0xFF111329).copy(alpha = .78f),
                    .68f to Color(0xFF111329).copy(alpha = .46f),
                    1f to Color(0xFF111329),
                )))
            }
        }
        Column(Modifier.fillMaxSize().padding(bottom = bottom), horizontalAlignment = Alignment.CenterHorizontally) {
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val viewport = maxHeight
                val bottomAligned = immersive && release == null
                Column(
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                        .heightIn(min = if (bottomAligned) viewport else 0.dp)
                        .padding(top = top, bottom = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = if (bottomAligned) Arrangement.SpaceBetween else Arrangement.Top,
                ) {
                    val heroTop = if (immersive) {
                        if (release != null) 44.dp else (viewport - 320.dp).coerceIn(20.dp, 163.dp)
                    } else 12.dp
                    val displayVersion = release?.tagName?.removePrefix("v") ?: version
                    SoftwareUpdateHero(displayVersion, heroTop, immersive)
                    SoftwareUpdateDetails(dialog, release, displayVersion, immersive, onRetry)
                }
            }
            if (action != null || downloading) {
                // OTA 在绑定进度按钮时按窗口类别覆盖 InstallDownloadLarge 的资源宽度。
                val buttonWidth = if (screenWidth in 600..839) 280.dp else 296.dp
                val label = action?.first ?: downloadPercent?.let { "正在下载 · $it%" } ?: "正在下载"
                Box(Modifier.padding(horizontal = 24.dp)) {
                    SoftwareUpdateAction(label, buttonWidth, action?.second, downloadPercent)
                }
                if (hasBackup) Text("备用下载", Modifier.clickable(role = Role.Button, onClick = onOpenBackup)
                    .padding(12.dp), color = foreground, style = rememberCouiTextStyle(CouiTextKind.PreferenceValue))
            }
        }
    }
}

@Composable
private fun SoftwareUpdateHero(version: String, topPadding: Dp, immersive: Boolean) {
    Box(
        modifier = Modifier.padding(horizontal = if (immersive) 16.dp else 24.dp)
            .padding(top = topPadding).widthIn(max = 660.dp).fillMaxWidth()
            .then(if (immersive) Modifier else Modifier.heightIn(min = 240.dp)
                .clip(CouiShape(16.dp, 2.5f))),
        contentAlignment = Alignment.Center,
    ) {
        if (!immersive) {
            Image(painterResource(R.drawable.about_sleepdown_hero), null, Modifier.matchParentSize(),
                contentScale = ContentScale.Crop)
            Box(Modifier.matchParentSize().background(Color(0x99111329)))
        }
        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.heightIn(min = if (immersive) 132.dp else 90.dp), contentAlignment = Alignment.Center) {
                val versionSize = if (immersive) 68.sp else 52.sp
                Text(version.substringBefore('_').substringBefore('-'), color = Color.White,
                    textAlign = TextAlign.Center,
                    style = rememberCouiTextStyle(CouiTextKind.PageTitle).copy(fontSize = versionSize, lineHeight = versionSize))
            }
            Text(SleepDownFullName, color = Color.White, textAlign = TextAlign.Center,
                style = rememberCouiTextStyle(CouiTextKind.PageTitle).copy(fontSize = 26.sp, lineHeight = 32.sp))
        }
    }
}

@Composable
private fun SoftwareUpdateDetails(
    dialog: AppUpdateDialog?, release: GiteeReleaseInfo?, version: String, immersive: Boolean, onRetry: () -> Unit,
) {
    val colors = CouiTheme.colorScheme
    val foreground = if (immersive) Color.White else colors.onSurface
    val secondary = if (immersive) Color.White.copy(alpha = .72f) else colors.onSurfaceVariant
    val contentWidth = Modifier.widthIn(max = 660.dp).fillMaxWidth()
    val titleStyle = rememberCouiTextStyle(CouiTextKind.PreferenceTitle)
    val summaryStyle = rememberCouiTextStyle(CouiTextKind.PreferenceSummary)
    when {
        dialog == AppUpdateDialog.Checking -> {
            Row(
                modifier = contentWidth.padding(start = 16.dp, top = 16.dp, end = 16.dp)
                    .heightIn(min = 64.dp).clip(CouiShape(24.dp, 2.5f))
                    .background(if (immersive) Color.White.copy(alpha = .16f) else colors.container)
                    .padding(start = 24.dp, end = 27.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("正在检查更新…", Modifier.weight(1f), color = foreground, style = titleStyle)
                CircularProgressIndicator(Modifier.size(19.5.dp), color = foreground, strokeWidth = 2.dp)
            }
        }
        release != null -> {
            Column(contentWidth.padding(horizontal = if (immersive) 40.dp else 24.dp).padding(top = 24.dp),
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
            val align = if (immersive) TextAlign.Center else TextAlign.Start
            Column(contentWidth.padding(start = 24.dp, top = 24.dp, end = 24.dp),
                horizontalAlignment = if (immersive) Alignment.CenterHorizontally else Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, color = foreground, textAlign = align, style = titleStyle)
                Text(message, color = secondary, textAlign = align, style = summaryStyle)
                if (dialog is AppUpdateDialog.UpToDate) {
                    Text("重新检查", Modifier.clickable(role = Role.Button, onClick = onRetry)
                        .padding(vertical = 12.dp), color = foreground,
                        style = rememberCouiTextStyle(CouiTextKind.PreferenceValue))
                }
            }
        }
    }
}

@Composable
private fun SoftwareUpdateAction(label: String, width: Dp, onClick: (() -> Unit)?, percent: Int?) {
    val colors = CouiTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val fraction = percent?.div(100f)
    val button = if (onClick != null) Modifier.couiButtonFeedback(interactionSource = interaction)
        .clickable(interaction, null, role = Role.Button, onClick = onClick)
    else if (fraction != null) Modifier.progressSemantics(fraction)
    else Modifier.progressSemantics()
    Box(
        modifier = Modifier.widthIn(max = width).fillMaxWidth().heightIn(min = 44.dp)
            .clip(CouiCapsuleShape)
            .background(if (onClick != null) colors.accent else colors.accent.copy(alpha = .35f))
            .then(button),
        contentAlignment = Alignment.Center,
    ) {
        if (onClick == null && fraction != null) {
            Box(Modifier.matchParentSize(), contentAlignment = Alignment.CenterStart) {
                Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(colors.accent))
            }
        }
        Text(label, Modifier.padding(horizontal = 12.dp, vertical = 8.dp), color = colors.onAccent,
            style = rememberCouiTextStyle(CouiTextKind.Progress))
    }
}
