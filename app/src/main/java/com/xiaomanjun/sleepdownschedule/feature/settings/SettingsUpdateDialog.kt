package com.xiaomanjun.sleepdownschedule.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.kyant.backdrop.Backdrop
import com.xiaomanjun.sleepdownschedule.ScheduleConfigEntity
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LiquidAlertAction
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LiquidAlertActionStyle
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LiquidAlertDialog
import com.xiaomanjun.sleepdownschedule.feature.update.GiteeReleaseInfo

internal sealed interface SettingsUpdateDialog {
    data object Checking : SettingsUpdateDialog
    data class Available(val release: GiteeReleaseInfo) : SettingsUpdateDialog
    data class UpToDate(val latestTag: String) : SettingsUpdateDialog
    data class Downloading(val release: GiteeReleaseInfo) : SettingsUpdateDialog
    data class NoApk(val release: GiteeReleaseInfo) : SettingsUpdateDialog
    data class Error(val message: String) : SettingsUpdateDialog
    data object InstallPermissionRequired : SettingsUpdateDialog
}

@Composable
internal fun SettingsUpdateDialogHost(
    dialog: SettingsUpdateDialog?,
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onDownload: (GiteeReleaseInfo) -> Unit,
    onOpenRelease: (GiteeReleaseInfo) -> Unit,
    onOpenBackup: () -> Unit,
    onRequestInstallPermission: () -> Unit
) {
    when (dialog) {
        null -> Unit
        SettingsUpdateDialog.Checking -> LiquidAlertDialog(
            title = "正在检查更新",
            message = "正在读取 Gitee 上最新的 SleepDown-Schedule Release。",
            actions = listOf(LiquidAlertAction("取消", LiquidAlertActionStyle.Cancel, onClick = onDismiss)),
            backdrop = backdrop,
            config = config,
            onDismissRequest = onDismiss
        )
        is SettingsUpdateDialog.Available -> {
            val release = dialog.release
            val notes = release.notes.trim().ifBlank { "该版本没有填写更新说明。" }
            LiquidAlertDialog(
                title = "发现新版本 ${release.name}",
                message = "$notes\n\n当前将从 Gitee 下载 APK，安装前仍会由系统向你确认。",
                actions = listOf(
                    LiquidAlertAction("稍后", LiquidAlertActionStyle.Cancel, onClick = onDismiss),
                    LiquidAlertAction("下载并安装", LiquidAlertActionStyle.Primary) { onDownload(release) }
                ),
                backdrop = backdrop,
                config = config,
                onDismissRequest = onDismiss
            )
        }
        is SettingsUpdateDialog.UpToDate -> LiquidAlertDialog(
            title = "已是最新版本",
            message = "当前安装版本已不低于 Gitee 最新 Release（${dialog.latestTag}）。",
            actions = listOf(LiquidAlertAction("知道了", LiquidAlertActionStyle.Primary, onClick = onDismiss)),
            backdrop = backdrop,
            config = config,
            onDismissRequest = onDismiss
        )
        is SettingsUpdateDialog.Downloading -> LiquidAlertDialog(
            title = "正在下载 ${dialog.release.name}",
            message = "正在从 Gitee 下载 APK。现在可以返回或退到桌面，下载会在后台继续，并通过实时活动显示进度。",
            actions = listOf(
                LiquidAlertAction(
                    "后台下载",
                    LiquidAlertActionStyle.Secondary,
                    onClick = onDismiss
                )
            ),
            backdrop = backdrop,
            config = config,
            onDismissRequest = onDismiss
        )
        is SettingsUpdateDialog.NoApk -> LiquidAlertDialog(
            title = "Release 中没有 APK",
            message = "已找到 ${dialog.release.name}，但该 Release 没有附带 APK 安装包。可以查看发行版，或使用备用下载页。",
            actions = listOf(
                LiquidAlertAction("备用下载", LiquidAlertActionStyle.Secondary, onClick = onOpenBackup),
                LiquidAlertAction("查看发行版", LiquidAlertActionStyle.Primary) { onOpenRelease(dialog.release) }
            ),
            backdrop = backdrop,
            config = config,
            onDismissRequest = onDismiss
        )
        is SettingsUpdateDialog.Error -> LiquidAlertDialog(
            title = "检查更新失败",
            message = dialog.message,
            actions = listOf(
                LiquidAlertAction("备用下载", LiquidAlertActionStyle.Secondary, onClick = onOpenBackup),
                LiquidAlertAction("重试", LiquidAlertActionStyle.Primary, onClick = onRetry)
            ),
            backdrop = backdrop,
            config = config,
            onDismissRequest = onDismiss
        )
        SettingsUpdateDialog.InstallPermissionRequired -> LiquidAlertDialog(
            title = "允许安装更新",
            message = "Android 需要你先允许 SleepDown 安装来自 Gitee 的更新。授权返回后会继续打开系统安装确认页面。",
            actions = listOf(
                LiquidAlertAction("取消", LiquidAlertActionStyle.Cancel, onClick = onDismiss),
                LiquidAlertAction("去授权", LiquidAlertActionStyle.Primary, onClick = onRequestInstallPermission)
            ),
            backdrop = backdrop,
            config = config,
            onDismissRequest = onDismiss
        )
    }
}

internal fun Throwable.readableUpdateMessage(): String =
    message?.takeIf { it.isNotBlank() } ?: "网络请求或安装包处理失败，请稍后重试。"
