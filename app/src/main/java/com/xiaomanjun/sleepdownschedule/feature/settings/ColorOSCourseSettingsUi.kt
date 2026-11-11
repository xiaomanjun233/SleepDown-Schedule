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
import androidx.compose.runtime.mutableIntStateOf
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
import com.xiaomanjun.sleepdownschedule.core.ui.settings.GlassPreferenceSection
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsActionRow
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsDivider
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsGroup
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsInfoRow
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsNavigationRow
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsValueRow
import com.xiaomanjun.sleepdownschedule.feature.coloros.ColorOSCourseComponentInstaller
import com.xiaomanjun.sleepdownschedule.feature.coloros.ColorOSCourseContract
import com.xiaomanjun.sleepdownschedule.feature.coloros.ColorOSCourseDiagnostics
import com.xiaomanjun.sleepdownschedule.feature.coloros.ColorOSCourseExperiment
import com.xiaomanjun.sleepdownschedule.feature.coloros.CourseComponentDownloadCheck
import com.xiaomanjun.sleepdownschedule.feature.experimental.ExperimentalNotificationHelpSheet
import com.xiaomanjun.sleepdownschedule.feature.update.GiteeAppUpdater
import com.xiaomanjun.sleepdownschedule.feature.update.UpdateDownloadState
import com.xiaomanjun.sleepdownschedule.model.AppState
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

@Composable
internal fun ColorOSCourseSettingsSection(
    state: AppState,
    backdrop: Backdrop?
) {
    if (!BuildConfig.SLEEPDOWN_EXPERIMENTAL_FEATURES) return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var diagnostics by remember { mutableStateOf<ColorOSCourseDiagnostics?>(null) }
    var showHelp by remember { mutableStateOf(false) }
    var diagnosticRefresh by remember { mutableIntStateOf(0) }
    var refreshingDiagnostics by remember { mutableStateOf(false) }
    var componentError by remember { mutableStateOf<String?>(null) }
    var downloadedComponent by remember { mutableStateOf<File?>(null) }
    var checkingComponent by remember { mutableStateOf(false) }
    var componentStatus by remember { mutableStateOf<String?>(null) }
    val downloadState by GiteeAppUpdater.downloadState.collectAsStateWithLifecycle()
    val componentDownloadState = downloadState.takeIf(ColorOSCourseComponentInstaller::isComponentDownload)

    fun reload() { diagnosticRefresh++ }

    LaunchedEffect(diagnosticRefresh) {
        refreshingDiagnostics = true
        try {
            diagnostics = ColorOSCourseExperiment.diagnose(context)
        } finally {
            refreshingDiagnostics = false
        }
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
        componentStatus = null
        scope.launch {
            try {
                ColorOSCourseComponentInstaller.download(context).fold(
                    onSuccess = { result ->
                        when (result) {
                            is CourseComponentDownloadCheck.Install -> {
                                downloadedComponent = result.apk
                                componentStatus = "发现课程组件 ${result.versionName}，请完成安装。"
                                if (GiteeAppUpdater.canRequestPackageInstalls(context)) {
                                    installComponent(result.apk)
                                } else {
                                    installPermissionLauncher.launch(GiteeAppUpdater.unknownSourcesSettingsIntent(context))
                                }
                            }
                            is CourseComponentDownloadCheck.NoUpdate -> {
                                downloadedComponent = null
                                componentStatus = if (result.downloadedIsOlder) {
                                    "已安装 ${result.installedVersionName}，高于可下载的 ${result.downloadedVersionName}"
                                } else {
                                    "已是最新组件版本 ${result.installedVersionName}"
                                }
                            }
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
    val isHonor = current?.device?.isHonor ?: ColorOSCourseExperiment.deviceStatus().isHonor
    val supportedDevice = current?.device?.isColorOSFamily
        ?: ColorOSCourseExperiment.deviceStatus().isColorOSFamily
    val wakeUpConflict = current?.officialWakeUpConflict == true
    val proxyReady = current?.let { it.proxyIsSleepDown && it.proxyVersionSupported } == true
    val vendorName = if (isHonor) "YOYO 建议" else "流体云"
    val componentSummary = current?.let {
        when {
            proxyReady -> "已安装 · ${it.proxyVersionName}"
            it.proxyIsSleepDown -> "需要更新 · ${it.proxyVersionName}"
            wakeUpConflict -> "与已安装的 WakeUp 课程表冲突"
            else -> "未安装"
        }
    } ?: "检测中…"
    val syncSummary = when {
        current == null -> "检测中…"
        !current.proxyProviderAccessible -> "组件未连接"
        !current.exportValid -> "课表读取异常"
        current.lastSystemQueryAt > 0 -> "系统最近读取 ${current.lastSystemQueryAt.toDisplayTime()}"
        else -> "等待系统读取"
    }
    val componentActionSubtitle = when {
        checkingComponent && componentDownloadState is UpdateDownloadState.Downloading ->
            componentDownloadState.progressPercent?.let { "正在下载：$it%" } ?: "正在下载…"
        checkingComponent -> "正在检查课程组件版本…"
        componentStatus != null -> componentStatus.orEmpty()
        componentDownloadState is UpdateDownloadState.Failed -> "下载失败，点击重试"
        else -> componentSummary
    }
    val componentActionButton = when (val state = componentDownloadState) {
        is UpdateDownloadState.Downloading -> state.progressPercent?.let { "$it%" } ?: "下载中"
        else -> if (checkingComponent) "检查中" else if (proxyReady) "检查更新" else "下载安装"
    }

    GlassPreferenceSection(vendorName) {
        SettingsGroup(
            backdrop = backdrop,
            config = state.config,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (!wakeUpConflict && supportedDevice) {
                SettingsActionRow(
                    title = "课程组件",
                    subtitle = componentActionSubtitle,
                    buttonText = componentActionButton,
                    iconRes = R.drawable.ic_download,
                    backdrop = backdrop,
                    onClick = ::downloadOrInstallComponent
                )
            } else {
                SettingsValueRow("课程组件", if (wakeUpConflict) componentSummary else "当前设备不支持")
            }
            if (current?.proxyIsSleepDown == true) {
                SettingsDivider()
                SettingsActionRow(
                    title = "重新同步",
                    subtitle = syncSummary,
                    buttonText = "同步",
                    iconRes = R.drawable.ic_refresh,
                    backdrop = backdrop,
                    onClick = {
                        ColorOSCourseExperiment.synchronizeFromUserAction(context)
                        reload()
                    }
                )
            }
            SettingsDivider()
            SettingsNavigationRow("教程与诊断", onClick = {
                showHelp = true
                reload()
            })
        }
    }

    ExperimentalNotificationHelpSheet(
        visible = showHelp,
        config = state.config,
        refreshing = refreshingDiagnostics,
        onRefresh = ::reload,
        onDismiss = { showHelp = false },
    ) { sheetBackdrop ->
        item("tutorial") {
            GlassPreferenceSection("使用教程") {
                SettingsGroup(sheetBackdrop, state.config) {
                    SettingsInfoRow(
                        title = "配置$vendorName",
                        body = (if (isHonor) {
                            "安装课程组件后，在系统的 YOYO 建议和通知设置中允许课程提醒。"
                        } else {
                            "安装课程组件后，在系统设置中打开流体云总开关。"
                        }) + "为「WakeUp课程表」开启自启动、关联启动和后台运行，再回到通知设置同步课表。"
                    )
                    if (current?.proxyIsSleepDown == true) {
                        SettingsDivider()
                        SettingsNavigationRow("组件启动权限", onClick = { openCourseComponentSettings(context, startup = true) })
                        SettingsDivider()
                        SettingsNavigationRow("组件后台运行", onClick = { openCourseComponentSettings(context, startup = false) })
                    }
                    SettingsDivider()
                    SettingsInfoRow(
                        "后台运行",
                        "课程组件会保存最近同步的课表，重启解锁或组件更新后会请求系统读取。" +
                            "如果系统限制了后台运行，检查组件的电池设置。强行停止 SleepDown 或课程组件后，重新打开并同步。" +
                            (if (isHonor) "部分荣耀机型还会校验组件签名或应用特征。" else "")
                    )
                    if (ColorOSCourseExperiment.allowsParallelLiveUpdate(context)) {
                        SettingsDivider()
                        SettingsInfoRow(
                            "同时使用实时活动",
                            if (isHonor) "当前模式同时使用 YOYO 建议与 SleepDown 实时活动。"
                            else "流体云显示课前提醒，实时活动显示课中、课间及次日课程提醒。"
                        )
                    }
                    SettingsDivider()
                    SettingsInfoRow(
                        "测试课程",
                        "点击通知设置底部的测试按钮后回到桌面，约 1～2 分钟后观察。临时课程约 21～22 分钟后开始，持续 5 分钟。可通过「删除测试课程」结束测试。"
                    )
                }
            }
        }
        item("diagnostics") {
            GlassPreferenceSection("当前诊断") {
                SettingsGroup(sheetBackdrop, state.config) {
                    if (current == null) {
                        SettingsValueRow("检查状态", "检测中…")
                    } else {
                        SettingsValueRow("设备", "${current.device.manufacturer} ${current.device.brand}")
                        SettingsDivider()
                        val support = if (supportedDevice) "支持" else "不支持"
                        val systemVersion = current.device.colorOSVersion?.let { " · $it" }.orEmpty()
                        SettingsValueRow("系统支持", support + systemVersion)
                        SettingsDivider()
                        SettingsValueRow("课程组件", componentSummary)
                        SettingsDivider()
                        SettingsValueRow("组件连接", if (current.proxyProviderAccessible) "正常" else "未连接")
                        SettingsDivider()
                        SettingsValueRow("课程读取", if (current.exportValid) {
                            "正常 · 今天 ${current.todayCourseCount} 门，明天 ${current.tomorrowCourseCount} 门"
                        } else "异常")
                        SettingsDivider()
                        SettingsValueRow("最近同步", current.lastRefreshAt.toDisplayTime())
                        SettingsDivider()
                        SettingsValueRow("系统读取", if (current.lastSystemQueryAt > 0) {
                            current.lastSystemQueryAt.toDisplayTime()
                        } else "等待系统读取")
                        if (current.lastSystemQueryPath.isNotBlank()) {
                            SettingsDivider()
                            SettingsValueRow("读取项目", current.lastSystemQueryPath)
                        }
                        current.error?.let { error ->
                            SettingsDivider()
                            SettingsInfoRow("异常详情", error)
                        }
                    }
                }
            }
        }
    }

    componentError?.let { message ->
        LiquidAlertDialog(
            title = "课程组件检查失败",
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
