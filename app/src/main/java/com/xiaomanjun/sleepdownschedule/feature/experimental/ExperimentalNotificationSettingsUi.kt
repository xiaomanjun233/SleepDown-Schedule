package com.xiaomanjun.sleepdownschedule.feature.experimental

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LiquidAlertAction
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LiquidAlertActionStyle
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LiquidAlertDialog
import com.xiaomanjun.sleepdownschedule.core.ui.settings.GlassPreferenceSection
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsActionRow
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsDivider
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsGroup
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsInfoRow
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsNavigationRow
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
    var requestingRoot by remember { mutableStateOf(false) }
    var showAuthorization by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }
    var diagnosticRefresh by remember { mutableIntStateOf(0) }
    var refreshingDiagnostics by remember { mutableStateOf(false) }
    val permissionRequest = remember { arrayOfNulls<() -> Unit>(1) }
    var options by remember { mutableStateOf(XiaomiSuperIsland.options(context)) }

    fun refresh() { diagnosticRefresh++ }

    LaunchedEffect(diagnosticRefresh) {
        refreshingDiagnostics = true
        try {
            shizukuRunning = XiaomiSuperIsland.isShizukuRunning()
            shizukuAuthorized = XiaomiSuperIsland.isShizukuAuthorized()
            rootAuthorized = XiaomiSuperIsland.isRootAuthorized(context)
            focusPermission = withContext(Dispatchers.IO) { XiaomiSuperIsland.focusPermission(context) }
            focusPermissionChecked = true
        } finally {
            refreshingDiagnostics = false
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh() }
    DisposableEffect(context) {
        val stop = XiaomiSuperIsland.observeShizukuState { scope.launch { refresh() } }
        onDispose { permissionRequest[0]?.invoke(); stop() }
    }

    val shizukuDownload = Uri.parse("https://shizuku.rikka.app/download/")
    val authorizationBusy = requestingShizuku || requestingRoot || restoring
    val authorizationStatus = when {
        restoring -> "正在检查网络状态…"
        requestingShizuku -> "等待 Shizuku 授权…"
        requestingRoot -> "正在验证 root…"
        shizukuAuthorized -> "Shizuku 已授权"
        rootAuthorized -> "root 已授权"
        else -> "选择 Shizuku 或 root"
    }
    val focusStatus = when {
        !focusPermissionChecked -> "检测中…"
        focusPermission == true -> "已开启"
        focusPermission == false -> "未开启"
        else -> "系统未返回状态"
    }

    suspend fun finishAuthorization() {
        restoring = true
        try {
            withContext(Dispatchers.IO) { XiaomiSuperIsland.restoreInterruptedBypass(context) }
        } finally {
            restoring = false
        }
        NotificationScheduler.requestReschedule(context)
        refresh()
    }

    fun authorizeShizuku() {
        if (authorizationBusy) return
        if (!XiaomiSuperIsland.isShizukuRunning()) {
            val launch = context.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
                ?: Intent(Intent.ACTION_VIEW, shizukuDownload)
            context.startActivity(launch)
            return
        }
        requestingShizuku = true
        permissionRequest[0] = XiaomiSuperIsland.requestShizukuPermission { granted ->
            scope.launch {
                requestingShizuku = false
                shizukuAuthorized = granted
                if (granted) finishAuthorization()
                else android.widget.Toast.makeText(context,
                    "未获得 Shizuku 授权，请确认服务正在运行并允许 SleepDown。",
                    android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    fun authorizeRoot() {
        if (authorizationBusy) return
        requestingRoot = true
        scope.launch {
            try {
                rootAuthorized = withContext(Dispatchers.IO) { XiaomiSuperIsland.requestRootAuthorization(context) }
                if (rootAuthorized) finishAuthorization()
            } finally {
                requestingRoot = false
            }
        }
    }

    GlassPreferenceSection("超级岛") {
        SettingsGroup(backdrop, config, Modifier.fillMaxWidth()) {
            SettingsNavigationRow("授权方式", authorizationStatus, enabled = !authorizationBusy) {
                showAuthorization = true
            }
            SettingsDivider()
            SettingsNavigationRow("焦点通知", focusStatus) {
                context.startActivity(NotificationScheduler.notificationSettingsIntent(context))
            }
            SettingsDivider()
            SettingsNavigationRow("教程与诊断") {
                showHelp = true
                refresh()
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

    if (showAuthorization) {
        LiquidAlertDialog(
            title = "授权方式",
            message = "选择一种方式为 SleepDown 授权。已有 root 的设备可以直接验证。",
            actions = listOf(
                LiquidAlertAction("Shizuku", LiquidAlertActionStyle.Primary) {
                    showAuthorization = false
                    authorizeShizuku()
                },
                LiquidAlertAction("root", LiquidAlertActionStyle.Secondary) {
                    showAuthorization = false
                    authorizeRoot()
                },
                LiquidAlertAction("取消", LiquidAlertActionStyle.Cancel) { showAuthorization = false },
            ),
            config = config,
            onDismissRequest = { showAuthorization = false },
        )
    }

    ExperimentalNotificationHelpSheet(
        visible = showHelp,
        config = config,
        refreshing = refreshingDiagnostics,
        onRefresh = ::refresh,
        onDismiss = { showHelp = false },
    ) { sheetBackdrop ->
        item("tutorial") {
            GlassPreferenceSection("使用教程") {
                SettingsGroup(sheetBackdrop, config) {
                    SettingsInfoRow(
                        "开启超级岛",
                        "通过「授权方式」完成 Shizuku 或 root 授权。在系统设置中开启超级岛总开关及 SleepDown 的显示权限，并允许应用自启动和后台运行。" +
                            "授权未生效时，课程提醒会使用实时活动。"
                    )
                    SettingsDivider()
                    SettingsInfoRow(
                        "启动 Shizuku",
                        "安装 Shizuku 后，在系统开发者选项中打开无线调试。按 Shizuku 内的提示配对并启动服务，再返回通知设置授权。已 root 的设备也可通过 root 启动 Shizuku。"
                    )
                    if (!shizukuRunning) {
                        SettingsDivider()
                        SettingsNavigationRow("下载 Shizuku") {
                            context.startActivity(Intent(Intent.ACTION_VIEW, shizukuDownload))
                        }
                    }
                    SettingsDivider()
                    SettingsInfoRow(
                        "测试超级岛",
                        "点击通知设置底部的「测试超级岛」，立即发送一条预览。若没有显示，检查焦点通知权限和授权状态。"
                    )
                }
            }
        }
        item("diagnostics") {
            GlassPreferenceSection("当前诊断") {
                SettingsGroup(sheetBackdrop, config) {
                    SettingsValueRow("系统超级岛", if (systemSupported) "已检测到" else "未检测到，请确认系统版本")
                    SettingsDivider()
                    SettingsValueRow("焦点通知权限", focusStatus)
                    SettingsDivider()
                    SettingsValueRow("Shizuku", when {
                        !shizukuRunning -> "未运行"
                        !shizukuAuthorized -> "未授权"
                        else -> "已授权"
                    })
                    SettingsDivider()
                    SettingsValueRow("root", if (rootAuthorized) "已授权" else "未授权")
                    SettingsDivider()
                    SettingsActionRow(
                        title = "重新显示当前超级岛",
                        subtitle = "按当前课程重新发送。",
                        buttonText = "重试",
                        iconRes = R.drawable.ic_refresh,
                        backdrop = sheetBackdrop,
                        onClick = { NotificationScheduler.reopenCurrentIsland(context) },
                    )
                }
            }
        }
    }
}
