package com.xiaomanjun.sleepdownschedule.feature.settings.about

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itos.coui.effect.couiButtonFeedback
import com.itos.coui.shape.CouiShape
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.rememberCouiTextStyle
import com.itos.coui.theme.CouiTheme
import com.kyant.backdrop.Backdrop
import com.xiaomanjun.sleepdownschedule.AppState
import com.xiaomanjun.sleepdownschedule.core.identity.AppDistribution
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.SleepDownSecondaryPageList
import com.xiaomanjun.sleepdownschedule.core.ui.scaffold.detailContentBottomPadding
import com.xiaomanjun.sleepdownschedule.core.ui.scaffold.detailContentTopPadding
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsGroup
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsNavigationRow
import com.xiaomanjun.sleepdownschedule.core.web.openSleepDownCustomTab
import com.xiaomanjun.sleepdownschedule.core.web.resolveSleepDownCustomTabsPackage
import com.xiaomanjun.sleepdownschedule.feature.update.*

/** 独立全屏软件更新页，沿用既有检查、下载、安装及底部权限窗口。 */
@Composable
internal fun SoftwareUpdateSettingsScreen(state: AppState, backdrop: Backdrop?, onHistory: () -> Unit) {
    val context = LocalContext.current
    val updates = rememberAppUpdateSession()
    val dark = CouiTheme.isDark
    val browser = remember(context) { context.resolveSleepDownCustomTabsPackage() }
    val width = LocalConfiguration.current.screenWidthDp
    val density = LocalDensity.current
    fun open(url: String) = context.openSleepDownCustomTab(url, providerPackage = browser,
        initialWidthPx = with(density) { (width.dp * .68f).roundToPx() }, darkTheme = dark)
    LaunchedEffect(updates) {
        if (AppDistribution.supportsSelfUpdate) updates.checkForUpdate()
    }
    SoftwareUpdatePageContent(
        version = updates.versionName, dialog = updates.dialog,
        onRetry = { if (AppDistribution.supportsSelfUpdate) updates.checkForUpdate() else open(SleepDownReleasesUrl) },
        onDownload = updates::downloadAndInstall, onOpenRelease = { open(it.releaseUrl) },
        onOpenBackup = { open(SleepDownReleasesUrl) },
        history = {
            SettingsGroup(backdrop, state.config) {
                SettingsNavigationRow("历史更新日志", onClick = onHistory)
            }
        },
    )
    if (updates.dialog == AppUpdateDialog.InstallPermissionRequired) {
        AppUpdateDialogHost(updates, backdrop, state.config,
            onRetry = { updates.checkForUpdate() }, onOpenRelease = { open(it.releaseUrl) },
            onOpenBackup = { open(SleepDownReleasesUrl); updates.dismiss() })
    }
}

/** 版本与说明独立滚动，220×44dp 主按钮固定在安全区上方。 */
@Composable
internal fun SoftwareUpdatePageContent(
    version: String, dialog: AppUpdateDialog?, onRetry: () -> Unit,
    onDownload: (GiteeReleaseInfo) -> Unit, onOpenRelease: (GiteeReleaseInfo) -> Unit,
    onOpenBackup: () -> Unit, history: @Composable () -> Unit = {},
) {
    val colors = CouiTheme.colorScheme
    val title: String
    val message: String
    var label: String? = null
    var action: (() -> Unit)? = null
    when (dialog) {
        AppUpdateDialog.Checking -> { title = "正在检查更新"; message = "正在读取最新发布版本。" }
        is AppUpdateDialog.UpToDate -> {
            title = "已是最新版本"; message = "最新版本 ${dialog.latestTag}"
            label = "重新检查"; action = onRetry
        }
        is AppUpdateDialog.Available -> {
            title = dialog.release.name
            message = dialog.release.notes.trim().ifEmpty { "此版本未提供更新说明。" }
            label = "下载并安装"; action = { onDownload(dialog.release) }
        }
        is AppUpdateDialog.Downloading -> {
            title = "正在下载 ${dialog.release.name}"
            message = "可通过通知查看下载进度。"
        }
        is AppUpdateDialog.NoApk -> {
            title = "本次发布未附带安装包"; message = dialog.release.name
            label = "查看发布页"; action = { onOpenRelease(dialog.release) }
        }
        is AppUpdateDialog.Error -> {
            title = "检查更新失败"; message = dialog.message; label = "重试"; action = onRetry
        }
        AppUpdateDialog.InstallPermissionRequired -> {
            title = "安装包已下载"; message = "允许安装此来源的应用后继续。"
        }
        null -> { title = "当前版本 $version"; message = ""; label = "检查更新"; action = onRetry }
    }
    val hasBackup = dialog is AppUpdateDialog.Error || dialog is AppUpdateDialog.NoApk
    val bottomPadding = detailContentBottomPadding()
    Column(Modifier.fillMaxSize()) {
        SleepDownSecondaryPageList(
            contentTopPadding = detailContentTopPadding(), contentBottomPadding = 16.dp,
            modifier = Modifier.weight(1f),
        ) {
            item(key = "software-version") {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 56.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(SleepDownFullName, style = rememberCouiTextStyle(CouiTextKind.PageTitle).copy(
                        fontSize = 32.sp, fontWeight = FontWeight.SemiBold), color = colors.onSurface,
                        textAlign = TextAlign.Center)
                    Spacer(Modifier.height(12.dp))
                    Text(version, style = rememberCouiTextStyle(CouiTextKind.PreferenceValue), color = colors.onSurfaceVariant)
                }
            }
            item(key = "software-notes") {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(title, style = rememberCouiTextStyle(CouiTextKind.SectionTitle), color = colors.onSurface)
                    if (message.isNotEmpty()) Text(message,
                        style = rememberCouiTextStyle(CouiTextKind.PreferenceSummary), color = colors.onSurfaceVariant)
                }
            }
            item(key = "software-history") { history() }
        }
        if (label != null && action != null) {
            Column(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = bottomPadding + 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                val interaction = remember { MutableInteractionSource() }
                Box(Modifier.widthIn(max = 220.dp).fillMaxWidth().heightIn(min = 44.dp)
                    .clip(CouiShape(22.dp, 2.5f)).background(colors.accent)
                    .couiButtonFeedback(CouiTheme.isDark, interaction)
                    .clickable(interaction, null, role = Role.Button, onClick = action)
                    .padding(horizontal = 12.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
                    Text(label, style = rememberCouiTextStyle(CouiTextKind.PreferenceValue).copy(fontWeight = FontWeight.Medium),
                        color = colors.onAccent)
                }
                if (hasBackup) Text("备用下载", Modifier.clickable(onClick = onOpenBackup).padding(12.dp),
                    style = rememberCouiTextStyle(CouiTextKind.PreferenceValue), color = colors.accent)
            }
        } else Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }
}
