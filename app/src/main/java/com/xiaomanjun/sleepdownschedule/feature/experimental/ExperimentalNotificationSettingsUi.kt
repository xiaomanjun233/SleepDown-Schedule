package com.xiaomanjun.sleepdownschedule.feature.experimental

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.xiaomanjun.sleepdownschedule.BuildConfig
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SleepDownLiquidDropdownPreference
import com.xiaomanjun.sleepdownschedule.feature.settings.GlassPreferenceSection
import com.xiaomanjun.sleepdownschedule.feature.settings.SettingsDivider
import com.xiaomanjun.sleepdownschedule.feature.settings.SettingsGroup
import com.xiaomanjun.sleepdownschedule.feature.settings.SettingsInfoRow
import com.xiaomanjun.sleepdownschedule.feature.settings.SettingsValueRow
import com.xiaomanjun.sleepdownschedule.model.ScheduleConfigEntity
import kotlinx.coroutines.Dispatchers
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
    val protocol = remember(context) { XiaomiSuperIsland.protocolVersion(context) }
    var focusPermission by remember(context) { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(context) {
        focusPermission = withContext(Dispatchers.IO) { XiaomiSuperIsland.hasFocusPermission(context) }
    }
    GlassPreferenceSection("实验功能") {
        SettingsGroup(backdrop, config, Modifier.fillMaxWidth()) {
            SettingsInfoRow(
                "小米超级岛",
                "采用小米官方客户端通知扩展。课程提醒仍由 SleepDown 发送；系统未开放超级岛时会显示普通实时活动。"
            )
            SettingsDivider()
            SettingsValueRow("开发者服务", if (BuildConfig.SLEEPDOWN_XIAOMI_APP_ID.isBlank()) {
                "尚未配置小米 App ID"
            } else "已配置，需完成平台场景审核")
            SettingsDivider()
            SettingsValueRow("系统超级岛协议", if (protocol >= 3) "支持" else "需要澎湃 OS 3 或更新版本")
            SettingsDivider()
            SettingsValueRow("焦点通知权限", when (focusPermission) {
                true -> "已开放"
                false -> "未开放，请在系统设置和小米开发者平台检查"
                null -> "检查中…"
            })
        }
    }
}
