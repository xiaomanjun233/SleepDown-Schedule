package com.xiaomanjun.sleepdownschedule.feature.settings

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
import com.kyant.backdrop.Backdrop
import com.xiaomanjun.sleepdownschedule.BuildConfig
import com.xiaomanjun.sleepdownschedule.R
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LiquidAlertAction
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LiquidAlertActionStyle
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LiquidAlertDialog
import com.xiaomanjun.sleepdownschedule.feature.coloros.ColorOSCourseBridge
import com.xiaomanjun.sleepdownschedule.feature.coloros.ColorOSCourseDiagnostics
import com.xiaomanjun.sleepdownschedule.feature.coloros.ColorOSCourseExperiment
import com.xiaomanjun.sleepdownschedule.feature.reminder.NotificationScheduler
import com.xiaomanjun.sleepdownschedule.model.AppState
import kotlinx.coroutines.launch
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

    fun reload(showWhenReady: Boolean = false) {
        scope.launch {
            diagnostics = ColorOSCourseExperiment.diagnose(context)
            if (showWhenReady) showDiagnostics = true
        }
    }

    LaunchedEffect(enabled) {
        diagnostics = ColorOSCourseExperiment.diagnose(context)
    }

    val current = diagnostics
    val supportedDevice = current?.device?.isColorOSFamily
        ?: ColorOSCourseExperiment.deviceStatus().isColorOSFamily
    val wakeUpConflict = current?.officialWakeUpConflict == true
    val proxyReady = current?.proxyIsSleepDown == true
    val toggleEnabled = supportedDevice && !wakeUpConflict && (proxyReady || enabled)
    val subtitle = when {
        !supportedDevice -> "仅支持 ColorOS / OPlus 设备，当前设备不可用。"
        wakeUpConflict -> "检测到 WakeUp 课程表，实验兼容组件无法同时安装。"
        enabled && !proxyReady -> "已启用，但 SleepDown 兼容组件当前不可用。"
        enabled -> "已启用；SleepDown 实时活动已关闭，由系统课程服务接管。"
        !proxyReady -> "请先安装 SleepDown 实验兼容组件。"
        else -> "通过 WakeUp 兼容接口向 ColorOS 课程服务提供当前课表。"
    }

    GlassPreferenceSection("ColorOS 课程流体云") {
        SettingsGroup(
            backdrop = backdrop,
            config = state.config,
            modifier = Modifier.fillMaxWidth()
        ) {
            SettingsInfoRow(
                title = "实验性",
                body = "仅在 exp 构建中提供。启用后会关闭 SleepDown 原实时活动，两套提醒不会同时运行。"
            )
            SettingsDivider()
            SettingsInfoRow(
                title = "配置与使用",
                body = "1. 确认下方兼容组件已就绪；如果安装了官方 WakeUp，需由你自行选择保留哪一个。\n" +
                    "2. 开启“ColorOS 课程流体云”，SleepDown 会导出当前课表并关闭原实时活动。\n" +
                    "3. 前往系统“设置 → 通知与控制中心 → 流体云”，确认流体云总开关已开启；不同系统版本的名称可能略有差异。\n" +
                    "4. 回到本页底部点击“测试流体云”。测试会请求系统重新读取课程，是否显示及显示时机仍由 ColorOS 决定。"
            )
            SettingsDivider()
            SettingsToggleRow(
                title = "ColorOS 课程流体云",
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
            SettingsValueRow("当前系统厂商", current?.let { "${it.device.manufacturer} / ${it.device.brand}" } ?: "检测中…")
            SettingsDivider()
            SettingsValueRow("ColorOS / OPlus", current?.device?.let { if (it.isColorOSFamily) "已识别" else "未识别" } ?: "检测中…")
            SettingsDivider()
            SettingsValueRow("兼容组件", current?.let {
                when {
                    it.proxyIsSleepDown -> "SleepDown 实验组件"
                    it.proxyInstalled -> "其他应用占用包名"
                    else -> "未安装"
                }
            } ?: "检测中…")
            SettingsDivider()
            SettingsValueRow("实验开关", if (enabled) "已完成" else "待开启")
            SettingsDivider()
            SettingsValueRow("系统流体云", "请在 ColorOS 设置中确认")
            SettingsDivider()
            SettingsValueRow("Provider", current?.let { if (it.proxyProviderAccessible) "可访问" else "不可访问" } ?: "检测中…")
            SettingsDivider()
            SettingsValueRow("课程导出", current?.let {
                if (it.exportValid) "正常 · 今日 ${it.todayCourseCount} / 明日 ${it.tomorrowCourseCount}" else "异常"
            } ?: "检测中…")
            SettingsDivider()
            SettingsValueRow("最近 refresh", current?.lastRefreshAt.toDisplayTime())
            SettingsDivider()
            SettingsValueRow("ColorOS 查询", current?.let {
                if (it.lastSystemQueryAt > 0) {
                    it.lastSystemQueryAt.toDisplayTime()
                } else {
                    "尚未检测到可识别查询"
                }
            } ?: "检测中…")
            SettingsDivider()
            SettingsActionRow(
                title = "刷新课程服务",
                subtitle = "立即通知 ColorOS 重新读取课程。",
                buttonText = "刷新",
                iconRes = R.drawable.ic_refresh,
                backdrop = backdrop,
                onClick = {
                    ColorOSCourseBridge.notifyScheduleChanged(context, "manual_settings")
                    reload()
                }
            )
            SettingsDivider()
            SettingsActionRow(
                title = "查看诊断信息",
                subtitle = "查看 Provider、导出和系统查询明细。",
                buttonText = "查看",
                iconRes = R.drawable.ic_settings,
                backdrop = backdrop,
                onClick = { reload(showWhenReady = true) }
            )
        }
    }

    if (showDiagnostics && current != null) {
        LiquidAlertDialog(
            title = "ColorOS 课程流体云诊断",
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
}

private fun Long?.toDisplayTime(): String {
    val timestamp = this?.takeIf { it > 0 } ?: return "无"
    return Instant.ofEpochMilli(timestamp)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("MM-dd HH:mm:ss"))
}
