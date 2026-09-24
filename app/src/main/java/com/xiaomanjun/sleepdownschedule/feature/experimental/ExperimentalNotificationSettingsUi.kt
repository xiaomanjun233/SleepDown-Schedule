package com.xiaomanjun.sleepdownschedule.feature.experimental

import android.content.Intent
import android.net.Uri
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.kyant.backdrop.Backdrop
import com.xiaomanjun.sleepdownschedule.R
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SleepDownLiquidDropdownPreference
import com.xiaomanjun.sleepdownschedule.feature.settings.GlassPreferenceSection
import com.xiaomanjun.sleepdownschedule.feature.settings.SettingsActionRow
import com.xiaomanjun.sleepdownschedule.feature.settings.SettingsDivider
import com.xiaomanjun.sleepdownschedule.feature.settings.SettingsGroup
import com.xiaomanjun.sleepdownschedule.feature.settings.SettingsInfoRow
import com.xiaomanjun.sleepdownschedule.feature.settings.SettingsValueRow
import com.xiaomanjun.sleepdownschedule.model.ScheduleConfigEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun ExperimentalNotificationSettingsRow(
    selected: ExperimentalNotificationMode,
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    onSelected: (ExperimentalNotificationMode) -> Unit
) {
    val modes = remember { ExperimentalNotificationModes.available() }
    SleepDownLiquidDropdownPreference(
        items = modes.map { it.label },
        selectedIndex = modes.indexOf(selected).coerceAtLeast(0),
        title = "通知样式",
        backdrop = backdrop,
        config = config,
        selectedBadgeText = selected.badgeText,
        maxHeight = 320.dp,
        onSelectedIndexChange = { index -> onSelected(modes[index.coerceIn(modes.indices)]) }
    )
}

@Composable
internal fun XiaomiSuperIslandSettingsSection(config: ScheduleConfigEntity, backdrop: Backdrop?) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val systemSupported = remember { XiaomiSuperIsland.isSystemSupported() }
    var shizukuRunning by remember { mutableStateOf(false) }
    var shizukuAuthorized by remember { mutableStateOf(false) }
    var restoring by remember { mutableStateOf(false) }

    fun refresh() {
        shizukuRunning = XiaomiSuperIsland.isShizukuRunning()
        shizukuAuthorized = XiaomiSuperIsland.isShizukuAuthorized()
    }
    LaunchedEffect(Unit) { refresh() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh() }

    GlassPreferenceSection("实验功能") {
        SettingsGroup(backdrop, config, Modifier.fillMaxWidth()) {
            SettingsInfoRow(
                "小米超级岛",
                "采用 Nexio 的课程模板：左侧课程名、右侧地点，课前显示倒计时。Shizuku 授权可提高显示成功率。"
            )
            SettingsDivider()
            SettingsInfoRow(
                "配置步骤",
                "1. 在本页将通知样式选为“超级岛”，并允许 SleepDown 发送通知。\n" +
                    "2. 在系统设置中搜索“超级岛”，确认总开关和 SleepDown 的显示权限已开启。\n" +
                    "3. 允许 SleepDown 自启动和后台运行，避免课程提醒延迟。\n" +
                    "4. 回到通知设置底部点“测试超级岛”；课程约 21～22 分钟后开始，持续 5 分钟。"
            )
            SettingsDivider()
            SettingsInfoRow(
                "Shizuku（可选）",
                "若系统未显示超级岛，可启动 Shizuku 并在下方授权 SleepDown，然后再次测试。未授权时课程通知仍会正常发送。"
            )
            SettingsDivider()
            SettingsValueRow("系统超级岛", if (systemSupported) "已检测到" else "未检测到，仍会尝试发送")
            SettingsDivider()
            SettingsValueRow("Shizuku 状态", when {
                !shizukuRunning -> "未运行"
                !shizukuAuthorized -> "未授权"
                else -> "已授权"
            })
            if (!shizukuRunning || !shizukuAuthorized) {
                SettingsDivider()
                SettingsActionRow(
                    title = "Shizuku 授权",
                    subtitle = if (shizukuRunning) "授权 SleepDown 使用 Shizuku。" else "安装并启动 Shizuku 后返回此页。",
                    buttonText = if (shizukuRunning) "授权" else "打开",
                    iconRes = R.drawable.ic_settings,
                    backdrop = backdrop,
                    onClick = {
                        if (shizukuRunning) {
                            XiaomiSuperIsland.requestShizukuPermission { granted ->
                                scope.launch {
                                    shizukuAuthorized = granted
                                    if (granted) {
                                        restoring = true
                                        withContext(Dispatchers.IO) { XiaomiSuperIsland.restoreInterruptedBypass(context) }
                                        restoring = false
                                    }
                                }
                            }
                        } else {
                            val launch = context.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
                                ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/download/"))
                            context.startActivity(launch)
                        }
                    }
                )
            }
            if (restoring) {
                SettingsDivider()
                SettingsValueRow("网络状态恢复", "正在检查…")
            }
        }
    }
}
