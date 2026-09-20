package com.xiaomanjun.sleepdownschedule.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kyant.backdrop.Backdrop
import com.xiaomanjun.sleepdownschedule.BuildConfig
import com.xiaomanjun.sleepdownschedule.R
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LiquidAlertAction
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LiquidAlertActionStyle
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LiquidAlertDialog
import com.xiaomanjun.sleepdownschedule.feature.coloros.ColorOSCourseBridge
import com.xiaomanjun.sleepdownschedule.feature.coloros.ColorOSCourseComponentInstaller
import com.xiaomanjun.sleepdownschedule.feature.coloros.ColorOSCourseDiagnostics
import com.xiaomanjun.sleepdownschedule.feature.coloros.ColorOSCourseExperiment
import com.xiaomanjun.sleepdownschedule.feature.reminder.NotificationScheduler
import com.xiaomanjun.sleepdownschedule.feature.update.GiteeAppUpdater
import com.xiaomanjun.sleepdownschedule.feature.update.UpdateDownloadState
import com.xiaomanjun.sleepdownschedule.model.AppState
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
internal fun ColorOSCourseSettingsSection(
    state: AppState,
    backdrop: Backdrop?,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit
) {
    if (!BuildConfig.SLEEPDOWN_EXP_BUILD) return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var diagnostics by remember { mutableStateOf<ColorOSCourseDiagnostics?>(null) }
    var showDiagnostics by remember { mutableStateOf(false) }
    var componentError by remember { mutableStateOf<String?>(null) }
    var downloadedComponent by remember { mutableStateOf<File?>(null) }
    val downloadState by GiteeAppUpdater.downloadState.collectAsStateWithLifecycle()
    val componentDownloadState = downloadState.takeIf(ColorOSCourseComponentInstaller::isComponentDownload)

    fun reload(showWhenReady: Boolean = false) {
        scope.launch {
            diagnostics = ColorOSCourseExperiment.diagnose(context)
            if (showWhenReady) showDiagnostics = true
        }
    }

    LaunchedEffect(enabled) {
        diagnostics = ColorOSCourseExperiment.diagnose(context)
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        reload()
    }

    fun installComponent(apk: File) {
        runCatching { GiteeAppUpdater.launchInstaller(context, apk) }
            .onFailure { componentError = it.message ?: "无法打开安装页面" }
    }

    val installPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        val apk = downloadedComponent
        if (apk != null && GiteeAppUpdater.canRequestPackageInstalls(context)) {
            installComponent(apk)
        }
    }

    fun downloadOrInstallComponent() {
        if (componentDownloadState is UpdateDownloadState.Downloading) return
        val readyApk = downloadedComponent
            ?: (componentDownloadState as? UpdateDownloadState.Completed)?.apk?.takeIf(File::exists)
        if (readyApk != null) {
            downloadedComponent = readyApk
            if (GiteeAppUpdater.canRequestPackageInstalls(context)) {
                installComponent(readyApk)
            } else {
                installPermissionLauncher.launch(GiteeAppUpdater.unknownSourcesSettingsIntent(context))
            }
            return
        }
        componentError = null
        scope.launch {
            ColorOSCourseComponentInstaller.download(context).fold(
                onSuccess = { apk ->
                    downloadedComponent = apk
                    if (GiteeAppUpdater.canRequestPackageInstalls(context)) {
                        installComponent(apk)
                    } else {
                        installPermissionLauncher.launch(GiteeAppUpdater.unknownSourcesSettingsIntent(context))
                    }
                },
                onFailure = { componentError = it.message ?: "课程组件下载失败" }
            )
        }
    }

    val current = diagnostics
    val supportedDevice = current?.device?.isColorOSFamily
        ?: ColorOSCourseExperiment.deviceStatus().isColorOSFamily
    val wakeUpConflict = current?.officialWakeUpConflict == true
    val proxyReady = current?.proxyIsSleepDown == true
    val toggleEnabled = supportedDevice && !wakeUpConflict && (proxyReady || enabled)
    val subtitle = when {
        !supportedDevice -> "仅支持 OPPO、一加和 realme 的 ColorOS 设备。"
        wakeUpConflict -> "检测到 WakeUp 课程表，实验兼容组件无法同时安装。"
        enabled && !proxyReady -> "请先安装课程组件。"
        enabled -> "已开启，原来的实时活动已关闭。"
        !proxyReady -> "请先下载并安装课程组件。"
        else -> "开启后，课程将交给系统流体云显示。"
    }
    val componentActionSubtitle = when (val state = componentDownloadState) {
        is UpdateDownloadState.Downloading -> state.progressPercent?.let { "正在下载：$it%" } ?: "正在下载…"
        is UpdateDownloadState.Completed -> "下载完成，点击进入安装。"
        is UpdateDownloadState.Failed -> "下载失败，点击重试。"
        else -> "从 Gitee 下载，完成后会直接进入安装。"
    }
    val componentActionButton = when (val state = componentDownloadState) {
        is UpdateDownloadState.Downloading -> state.progressPercent?.let { "$it%" } ?: "下载中"
        is UpdateDownloadState.Completed -> "安装"
        else -> "下载"
    }

    GlassPreferenceSection("课程流体云") {
        SettingsGroup(
            backdrop = backdrop,
            config = state.config,
            modifier = Modifier.fillMaxWidth()
        ) {
            SettingsInfoRow(
                title = "使用前准备",
                body = "这项功能只适用于 ColorOS。第一次使用请先下载课程组件，再打开下方开关。"
            )
            SettingsDivider()
            SettingsInfoRow(
                title = "如何使用",
                body = "1. 安装课程组件。\n" +
                    "2. 打开“使用系统课程流体云”。\n" +
                    "3. 到系统“设置 → 通知与控制中心 → 流体云”打开总开关。\n" +
                    "4. 回到本页底部点“测试流体云”，测试内容会在 3 分钟后结束。"
            )
            if (!proxyReady && !wakeUpConflict && supportedDevice) {
                SettingsDivider()
                SettingsActionRow(
                    title = "安装课程组件",
                    subtitle = componentActionSubtitle,
                    buttonText = componentActionButton,
                    iconRes = R.drawable.ic_download,
                    backdrop = backdrop,
                    onClick = ::downloadOrInstallComponent
                )
            }
            SettingsDivider()
            SettingsToggleRow(
                title = "使用系统课程流体云",
                subtitle = subtitle,
                checked = enabled,
                backdrop = backdrop,
                enabled = toggleEnabled,
                onCheckedChange = { requested ->
                    val accepted = ColorOSCourseExperiment.setEnabled(context, requested)
                    if (accepted) {
                        NotificationScheduler.cancelCurrentLiveUpdate(context, null, null)
                    }
                    NotificationScheduler.requestReschedule(context)
                    onEnabledChange(accepted)
                    reload()
                }
            )
            SettingsDivider()
            SettingsValueRow("设备支持", current?.device?.let { if (it.isColorOSFamily) "支持" else "不支持" } ?: "检测中…")
            SettingsDivider()
            SettingsValueRow("课程组件", current?.let {
                when {
                    it.proxyIsSleepDown -> "已安装"
                    it.proxyInstalled -> "与已安装的 WakeUp 课程表冲突"
                    else -> "未安装"
                }
            } ?: "检测中…")
            SettingsDivider()
            SettingsValueRow("课程读取", current?.let {
                if (it.exportValid) "正常 · 今天 ${it.todayCourseCount} 门，明天 ${it.tomorrowCourseCount} 门" else "异常"
            } ?: "检测中…")
            SettingsDivider()
            SettingsValueRow("系统读取", current?.let {
                if (it.lastSystemQueryAt > 0) {
                    "最近一次 ${it.lastSystemQueryAt.toDisplayTime()}"
                } else {
                    "等待系统读取"
                }
            } ?: "检测中…")
            SettingsDivider()
            SettingsActionRow(
                title = "重新同步",
                subtitle = "让系统重新读取当前课程。",
                buttonText = "同步",
                iconRes = R.drawable.ic_refresh,
                backdrop = backdrop,
                onClick = {
                    ColorOSCourseBridge.notifyScheduleChanged(context, "manual_settings")
                    reload()
                }
            )
            SettingsDivider()
            SettingsActionRow(
                title = "问题诊断",
                subtitle = "查看设备、组件和课程读取状态。",
                buttonText = "查看",
                iconRes = R.drawable.ic_settings,
                backdrop = backdrop,
                onClick = { reload(showWhenReady = true) }
            )
        }
    }

    if (showDiagnostics && current != null) {
        LiquidAlertDialog(
            title = "课程流体云诊断",
            message = current.asText(),
            actions = listOf(
                LiquidAlertAction("完成", LiquidAlertActionStyle.Primary) {
                    showDiagnostics = false
                }
            ),
            backdrop = backdrop,
            config = state.config,
            onDismissRequest = { showDiagnostics = false }
        )
    }

    componentError?.let { message ->
        LiquidAlertDialog(
            title = "课程组件未安装",
            message = message,
            actions = listOf(
                LiquidAlertAction("知道了", LiquidAlertActionStyle.Primary) {
                    componentError = null
                }
            ),
            backdrop = backdrop,
            config = state.config,
            onDismissRequest = { componentError = null }
        )
    }
}

private fun Long?.toDisplayTime(): String {
    val timestamp = this?.takeIf { it > 0 } ?: return "无"
    return Instant.ofEpochMilli(timestamp)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("MM-dd HH:mm:ss"))
}
