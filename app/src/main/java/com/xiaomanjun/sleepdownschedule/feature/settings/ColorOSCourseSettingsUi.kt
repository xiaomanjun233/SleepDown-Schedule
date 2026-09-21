package com.xiaomanjun.sleepdownschedule.feature.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
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
import com.xiaomanjun.sleepdownschedule.feature.coloros.ColorOSCourseContract
import com.xiaomanjun.sleepdownschedule.feature.coloros.ColorOSCourseDiagnostics
import com.xiaomanjun.sleepdownschedule.feature.coloros.ColorOSCourseExperiment
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
    backdrop: Backdrop?
) {
    if (!BuildConfig.SLEEPDOWN_EXP_BUILD) return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var diagnostics by remember { mutableStateOf<ColorOSCourseDiagnostics?>(null) }
    var showDiagnostics by remember { mutableStateOf(false) }
    var componentError by remember { mutableStateOf<String?>(null) }
    var downloadedComponent by remember { mutableStateOf<File?>(null) }
    var checkingComponent by remember { mutableStateOf(false) }
    val downloadState by GiteeAppUpdater.downloadState.collectAsStateWithLifecycle()
    val componentDownloadState = downloadState.takeIf(ColorOSCourseComponentInstaller::isComponentDownload)

    fun reload(showWhenReady: Boolean = false) {
        scope.launch {
            diagnostics = ColorOSCourseExperiment.diagnose(context)
            if (showWhenReady) showDiagnostics = true
        }
    }

    LaunchedEffect(Unit) {
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
        if (checkingComponent || componentDownloadState is UpdateDownloadState.Downloading) return
        checkingComponent = true
        componentError = null
        scope.launch {
            try {
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
            } finally {
                checkingComponent = false
            }
        }
    }

    val current = diagnostics
    val supportedDevice = current?.device?.isColorOSFamily
        ?: ColorOSCourseExperiment.deviceStatus().isColorOSFamily
    val wakeUpConflict = current?.officialWakeUpConflict == true
    val proxyReady = current?.let { it.proxyIsSleepDown && it.proxyVersionSupported } == true
    val componentActionSubtitle = when (val state = componentDownloadState) {
        is UpdateDownloadState.Downloading -> state.progressPercent?.let { "正在下载：$it%" } ?: "正在下载…"
        is UpdateDownloadState.Completed -> "点击检查最新版本并安装。"
        is UpdateDownloadState.Failed -> "下载失败，点击重试。"
        else -> if (checkingComponent) "正在查找最新组件…" else "自动查找 Gitee 最新组件，下载后进入安装。"
    }
    val componentActionButton = when (val state = componentDownloadState) {
        is UpdateDownloadState.Downloading -> state.progressPercent?.let { "$it%" } ?: "下载中"
        else -> if (checkingComponent) "检查中" else if (proxyReady) "检查更新" else "下载安装"
    }

    GlassPreferenceSection("课程流体云") {
        SettingsGroup(
            backdrop = backdrop,
            config = state.config,
            modifier = Modifier.fillMaxWidth()
        ) {
            SettingsInfoRow(
                title = "使用前准备",
                body = "这项实验适用于 ColorOS，并已向荣耀 MagicOS 开放测试。第一次使用请先下载课程组件。"
            )
            SettingsDivider()
            SettingsInfoRow(
                title = "如何使用",
                body = "1. 安装课程组件。\n" +
                    "2. ColorOS 请打开系统流体云总开关；荣耀请在 YOYO 建议和通知相关设置中允许课程提醒。\n" +
                    "3. 点“测试流体云”，SleepDown 会自动唤醒课程组件。测试课程约 21～22 分钟后开始，持续 5 分钟。\n" +
                    "4. 如果仍未显示，请在系统的应用启动管理中允许“WakeUp课程表”自启动和关联启动，再回来测试。荣耀系统可能还会校验组件签名或应用特征，需要以真机结果为准。"
            )
            SettingsDivider()
            SettingsInfoRow(
                title = "后台说明",
                body = (if (ColorOSCourseExperiment.allowsParallelLiveUpdate()) {
                    "荣耀上的 YOYO 课程小组件与 SleepDown 实时活动可以同时使用。"
                } else "") +
                    "课程组件会保存已同步的课程快照，重启解锁或组件更新后通知系统重新读取。" +
                    "请在系统设置中允许“WakeUp课程表”自启动、关联启动和后台运行；自启动不代表系统不会冻结后台应用。" +
                    "若强行停止 SleepDown 或课程组件，请重新打开应用并同步。"
            )
            if (current?.proxyIsSleepDown == true) {
                SettingsDivider()
                SettingsActionRow(
                    title = "组件自启动",
                    subtitle = "在系统启动管理中允许“WakeUp课程表”自启动和关联启动。",
                    buttonText = "设置",
                    iconRes = R.drawable.ic_settings,
                    backdrop = backdrop,
                    onClick = { openCourseComponentSettings(context, startup = true) }
                )
                SettingsDivider()
                SettingsActionRow(
                    title = "组件后台运行",
                    subtitle = "打开“WakeUp课程表”的应用信息，检查电池与后台限制。",
                    buttonText = "设置",
                    iconRes = R.drawable.ic_settings,
                    backdrop = backdrop,
                    onClick = { openCourseComponentSettings(context, startup = false) }
                )
            }
            if (!wakeUpConflict && supportedDevice) {
                SettingsDivider()
                SettingsActionRow(
                    title = if (proxyReady) "更新课程组件" else "安装课程组件",
                    subtitle = componentActionSubtitle,
                    buttonText = componentActionButton,
                    iconRes = R.drawable.ic_download,
                    backdrop = backdrop,
                    onClick = ::downloadOrInstallComponent
                )
            }
            SettingsDivider()
            SettingsValueRow("设备支持", current?.device?.let { if (it.isColorOSFamily) "支持" else "不支持" } ?: "检测中…")
            SettingsDivider()
            SettingsValueRow("课程组件", current?.let {
                when {
                    it.proxyIsSleepDown && it.proxyVersionSupported -> "已安装 · ${it.proxyVersionName}"
                    it.proxyIsSleepDown -> "需要更新 · ${it.proxyVersionName}"
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
                    ColorOSCourseExperiment.synchronizeFromUserAction(context)
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

private fun openCourseComponentSettings(context: Context, startup: Boolean) {
    if (startup) {
        // This entry is exposed by current ColorOS. Other systems use app details.
        val opened = runCatching {
            context.startActivity(
                Intent("com.oplus.battery.permission.startup.StartupAppListActivity")
                    .setPackage("com.oplus.battery")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }.isSuccess
        if (opened) return
    }
    context.startActivity(
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${ColorOSCourseContract.PROXY_PACKAGE}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}

private fun Long?.toDisplayTime(): String {
    val timestamp = this?.takeIf { it > 0 } ?: return "无"
    return Instant.ofEpochMilli(timestamp)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("MM-dd HH:mm:ss"))
}
