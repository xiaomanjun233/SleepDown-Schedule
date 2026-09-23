package com.xiaomanjun.sleepdownschedule.feature.experimental

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.PaddingValues
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
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        maxHeight = 300.dp,
        onExpandedChange = {},
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
                "小米超级岛（实验功能）",
                "参考 Nexio 的 Shizuku 方案。授权后发送岛通知时会短暂调整小米服务的联网规则并恢复；未就绪时仍显示普通实时活动。"
            )
            SettingsDivider()
            SettingsValueRow("系统超级岛", if (systemSupported) "已检测到" else "未检测到系统支持")
            SettingsDivider()
            SettingsValueRow("Shizuku 状态", when {
                !shizukuRunning -> "未运行"
                !shizukuAuthorized -> "未授权"
                else -> "已授权"
            })
            if (!shizukuRunning || !shizukuAuthorized) {
                SettingsDivider()
                SettingsActionRow(
                    title = "Shizuku 授权（实验功能）",
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
