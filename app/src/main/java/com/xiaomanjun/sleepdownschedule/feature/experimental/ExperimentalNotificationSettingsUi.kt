package com.xiaomanjun.sleepdownschedule.feature.experimental

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import com.xiaomanjun.sleepdownschedule.core.ui.settings.GlassPreferenceSection
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsActionRow
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsDivider
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsGroup
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsInfoRow
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsToggleRow
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsValueRow
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SleepDownLiquidDropdownPreference
import com.xiaomanjun.sleepdownschedule.feature.reminder.NotificationScheduler
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
    var rootAuthorized by remember { mutableStateOf(false) }
    var focusPermission by remember { mutableStateOf<Boolean?>(null) }
    var focusPermissionChecked by remember { mutableStateOf(false) }
    var restoring by remember { mutableStateOf(false) }
    var requestingShizuku by remember { mutableStateOf(false) }
    val permissionRequest = remember { arrayOfNulls<() -> Unit>(1) }
    var options by remember { mutableStateOf(XiaomiSuperIsland.options(context)) }

    fun refresh() {
        shizukuRunning = XiaomiSuperIsland.isShizukuRunning()
        shizukuAuthorized = XiaomiSuperIsland.isShizukuAuthorized()
        rootAuthorized = XiaomiSuperIsland.isRootAuthorized(context)
        scope.launch {
            focusPermission = withContext(Dispatchers.IO) { XiaomiSuperIsland.focusPermission(context) }
            focusPermissionChecked = true
        }
    }
    LaunchedEffect(Unit) { refresh() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh() }
    DisposableEffect(context) {
        val stop = XiaomiSuperIsland.observeShizukuState { refresh() }
        onDispose { permissionRequest[0]?.invoke(); stop() }
    }

    GlassPreferenceSection("实验功能") {
        SettingsGroup(backdrop, config, Modifier.fillMaxWidth()) {
            SettingsInfoRow(
                "小米超级岛",
                "可自定义左右内容、息屏文字和展开光效。使用前须选择 Shizuku 或 root 完成授权；未授权时会使用普通实时活动。"
            )
            SettingsDivider()
            SettingsInfoRow(
                "配置步骤",
                "1. 选择 Shizuku 或 root，完成下方授权。\n" +
                    "2. 在系统设置中搜索“超级岛”，开启总开关及 SleepDown 的显示权限，并允许通知。\n" +
                    "3. 允许 SleepDown 自启动和后台运行，避免课程提醒延迟。\n" +
                    "4. 回到通知设置底部点“测试超级岛”；课程约 21～22 分钟后开始，持续 5 分钟。"
            )
            SettingsDivider()
            SettingsInfoRow(
                "Shizuku 授权方法",
                "从 Shizuku 官网下载安装；在系统开发者选项打开无线调试，在 Shizuku 内按提示配对并启动服务，再返回这里授权 SleepDown。已 root 的设备也可以在 Shizuku 内通过 root 启动服务。"
            )
            SettingsDivider()
            SettingsValueRow("系统超级岛", if (systemSupported) "已检测到" else "未检测到，请确认系统版本")
            SettingsDivider()
            SettingsValueRow("焦点通知权限", when {
                !focusPermissionChecked -> "检测中…"
                focusPermission == true -> "已开启"
                focusPermission == false -> "未开启"
                else -> "系统未返回状态"
            })
            if (focusPermissionChecked && focusPermission != true) {
                SettingsDivider()
                SettingsActionRow(
                    title = "打开通知权限设置",
                    subtitle = "检查 SleepDown 的通知和焦点通知显示权限。",
                    buttonText = "打开",
                    iconRes = R.drawable.ic_settings,
                    backdrop = backdrop,
                    onClick = { context.startActivity(NotificationScheduler.notificationSettingsIntent(context)) }
                )
            }
            SettingsDivider()
            SettingsActionRow(
                title = "重新显示当前超级岛",
                subtitle = "课中没有显示时，立即按当前课程重新发送。",
                buttonText = "重试",
                iconRes = R.drawable.ic_settings,
                backdrop = backdrop,
                onClick = { NotificationScheduler.reopenCurrentIsland(context) }
            )
            SettingsDivider()
            SettingsValueRow("授权状态", if (shizukuAuthorized || rootAuthorized) "已就绪" else "需选择 Shizuku 或 root")
            SettingsDivider()
            SettingsValueRow("Shizuku 状态", when {
                !shizukuRunning -> "未运行"
                !shizukuAuthorized -> "未授权"
                else -> "已授权"
            })
            if (!shizukuRunning) {
                SettingsDivider()
                SettingsActionRow(
                    title = "下载 Shizuku",
                    subtitle = "打开官网下载安装，并按上方步骤完成配对与启动。",
                    buttonText = "下载",
                    iconRes = R.drawable.ic_settings,
                    backdrop = backdrop,
                    onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/download/"))) }
                )
            }
            SettingsDivider()
            SettingsActionRow(
                title = "Shizuku 授权",
                subtitle = if (shizukuRunning) "授权 SleepDown 使用 Shizuku。" else "先下载、配对并启动 Shizuku。",
                buttonText = if (requestingShizuku) "等待授权" else if (shizukuRunning) "申请授权" else "打开",
                iconRes = R.drawable.ic_settings,
                backdrop = backdrop,
                onClick = {
                    if (requestingShizuku) return@SettingsActionRow
                    if (XiaomiSuperIsland.isShizukuRunning()) {
                        requestingShizuku = true
                        permissionRequest[0] = XiaomiSuperIsland.requestShizukuPermission { granted ->
                            scope.launch {
                                requestingShizuku = false
                                shizukuAuthorized = granted
                                if (!granted) android.widget.Toast.makeText(context,
                                    "未获得 Shizuku 授权，请确认服务正在运行并允许 SleepDown。",
                                    android.widget.Toast.LENGTH_LONG).show()
                                if (granted) {
                                    restoring = true
                                    withContext(Dispatchers.IO) { XiaomiSuperIsland.restoreInterruptedBypass(context) }
                                    restoring = false
                                    NotificationScheduler.requestReschedule(context)
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
            SettingsDivider()
            SettingsValueRow("root 状态", if (rootAuthorized) "已授权" else "未授权")
            SettingsDivider()
            SettingsActionRow(
                title = "root 授权",
                subtitle = "有 root 的设备可直接授予 SleepDown 权限，与 Shizuku 二选一。",
                buttonText = if (rootAuthorized) "重新验证" else "验证",
                iconRes = R.drawable.ic_settings,
                backdrop = backdrop,
                onClick = {
                    scope.launch {
                        rootAuthorized = withContext(Dispatchers.IO) {
                            XiaomiSuperIsland.requestRootAuthorization(context)
                        }
                        if (rootAuthorized) {
                            restoring = true
                            withContext(Dispatchers.IO) { XiaomiSuperIsland.restoreInterruptedBypass(context) }
                            restoring = false
                            NotificationScheduler.requestReschedule(context)
                        }
                    }
                }
            )
            if (restoring) {
                SettingsDivider()
                SettingsValueRow("网络状态恢复", "正在检查…")
            }
        }
    }
    GlassPreferenceSection("超级岛显示") {
        SettingsGroup(backdrop, config, Modifier.fillMaxWidth()) {
            val leftChoices = listOf("课程名称", "上课地点", "下一节点")
            val rightChoices = listOf("课程名称", "上课地点", "倒计时")
            SleepDownLiquidDropdownPreference(
                items = leftChoices,
                selectedIndex = options.left,
                title = "超级岛左侧",
                backdrop = backdrop,
                config = config,
                modifier = Modifier.fillMaxWidth(),
                insideMargin = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                onSelectedIndexChange = { mode ->
                    XiaomiSuperIsland.setLeft(context, mode)
                    options = XiaomiSuperIsland.options(context)
                    NotificationScheduler.requestRefresh(context)
                }
            )
            SettingsDivider()
            SleepDownLiquidDropdownPreference(
                items = rightChoices,
                selectedIndex = options.right,
                title = "超级岛右侧",
                backdrop = backdrop,
                config = config,
                modifier = Modifier.fillMaxWidth(),
                insideMargin = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                onSelectedIndexChange = { mode ->
                    XiaomiSuperIsland.setRight(context, mode)
                    options = XiaomiSuperIsland.options(context)
                    NotificationScheduler.requestRefresh(context)
                }
            )
            SettingsDivider()
            SleepDownLiquidDropdownPreference(
                items = listOf("课程名称", "上课地点"),
                selectedIndex = options.aod,
                title = "息屏显示",
                backdrop = backdrop,
                config = config,
                modifier = Modifier.fillMaxWidth(),
                insideMargin = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                onSelectedIndexChange = { mode ->
                    XiaomiSuperIsland.setAod(context, mode)
                    options = XiaomiSuperIsland.options(context)
                    NotificationScheduler.requestRefresh(context)
                }
            )
            SettingsDivider()
            SettingsToggleRow("展开光效", "在超级岛展开态显示流动光效", options.expandGlow, backdrop) { enabled ->
                XiaomiSuperIsland.setExpandGlow(context, enabled)
                options = XiaomiSuperIsland.options(context)
                NotificationScheduler.requestRefresh(context)
            }
        }
    }
}
