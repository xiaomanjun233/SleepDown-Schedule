package com.xiaomanjun.sleepdownschedule.feature.experimental

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.kyant.backdrop.Backdrop
import com.xiaomanjun.sleepdownschedule.BuildConfig
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LiquidAlertAction
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LiquidAlertActionStyle
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LiquidAlertDialog
import com.xiaomanjun.sleepdownschedule.feature.coloros.ColorOSCourseDiagnostics
import com.xiaomanjun.sleepdownschedule.feature.coloros.ColorOSCourseExperiment
import com.xiaomanjun.sleepdownschedule.feature.reminder.NotificationScheduler
import com.xiaomanjun.sleepdownschedule.feature.settings.ColorOSCourseSettingsSection
import com.xiaomanjun.sleepdownschedule.feature.settings.SettingsActionButton
import com.xiaomanjun.sleepdownschedule.feature.settings.SettingsChoiceRow
import com.xiaomanjun.sleepdownschedule.model.AppState
import com.xiaomanjun.sleepdownschedule.model.LiveUpdateChipTextMode
import com.xiaomanjun.sleepdownschedule.model.NotificationMode
import com.xiaomanjun.sleepdownschedule.model.ScheduleConfigEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Everything behind these few settings hooks can be removed from a store build as one feature. */
internal class ExperimentalNotificationUiState(
    private val context: Context,
    notificationMode: NotificationMode
) {
    var selected by mutableStateOf(ExperimentalNotificationModes.selected(context, notificationMode))
        private set
    var testResult by mutableStateOf<ColorOSCourseDiagnostics?>(null)
    var testActive by mutableStateOf(
        BuildConfig.SLEEPDOWN_EXPERIMENTAL_FEATURES && ColorOSCourseExperiment.hasActiveTestPreview(context)
    )
        private set

    val cloudEnabled: Boolean get() = selected == ExperimentalNotificationMode.FLUID_CLOUD ||
        selected == ExperimentalNotificationMode.FLUID_CLOUD_LIVE_UPDATE ||
        selected == ExperimentalNotificationMode.YOYO_LIVE_UPDATE
    val parallelLiveUpdate: Boolean get() =
        selected == ExperimentalNotificationMode.FLUID_CLOUD_LIVE_UPDATE ||
            selected == ExperimentalNotificationMode.YOYO_LIVE_UPDATE
    val superIslandEnabled: Boolean get() = selected == ExperimentalNotificationMode.SUPER_ISLAND
    val hasDetails: Boolean get() = BuildConfig.SLEEPDOWN_EXPERIMENTAL_FEATURES && (cloudEnabled || superIslandEnabled)

    fun allowsLiveUpdateOptions(baseMode: NotificationMode): Boolean =
        baseMode == NotificationMode.LIVE_UPDATE && (!cloudEnabled || parallelLiveUpdate)

    fun select(
        choice: ExperimentalNotificationMode,
        currentBaseMode: NotificationMode,
        onBaseModeChange: (NotificationMode) -> Unit
    ) {
        if (choice == selected) return
        val baseMode = ExperimentalNotificationModes.activate(context, choice)
        selected = choice
        testActive = false
        testResult = null
        if (choice == ExperimentalNotificationMode.FLUID_CLOUD) {
            NotificationScheduler.cancelCurrentLiveUpdate(context, null, null)
        }
        onBaseModeChange(baseMode)
        // A changed base mode is written asynchronously; its writer refreshes the island after
        // persistence. When the base mode is unchanged, the vendor selection needs its own refresh.
        if (baseMode == currentBaseMode) NotificationScheduler.requestReschedule(context)
    }

    fun cancelTest() {
        ColorOSCourseExperiment.cancelTestPreview(context)
        testActive = false
        testResult = null
    }

    suspend fun runTest() {
        testResult = ColorOSCourseExperiment.testFluidCloud(context)
        testActive = ColorOSCourseExperiment.hasActiveTestPreview(context)
    }
}

@Composable
internal fun rememberExperimentalNotificationUiState(
    notificationMode: NotificationMode
): ExperimentalNotificationUiState {
    val context = LocalContext.current.applicationContext
    return remember(context, notificationMode) {
        ExperimentalNotificationUiState(context, notificationMode)
    }
}

@Composable
internal fun ExperimentalNotificationChoiceRow(
    state: ExperimentalNotificationUiState,
    notificationMode: NotificationMode,
    config: ScheduleConfigEntity,
    backdrop: Backdrop?,
    onNotificationModeChange: (NotificationMode) -> Unit
) {
    if (BuildConfig.SLEEPDOWN_EXPERIMENTAL_FEATURES && ExperimentalNotificationModes.available().size > 2) {
        ExperimentalNotificationSettingsRow(
            selected = state.selected,
            backdrop = backdrop,
            config = config,
            onSelected = { state.select(it, notificationMode, onNotificationModeChange) }
        )
    } else {
        SettingsChoiceRow("通知样式", notificationMode, backdrop, config, onNotificationModeChange)
    }
}

@Composable
internal fun ExperimentalNotificationDetails(
    state: ExperimentalNotificationUiState,
    appState: AppState,
    backdrop: Backdrop?
) {
    when {
        state.cloudEnabled -> ColorOSCourseSettingsSection(state = appState, backdrop = backdrop)
        state.superIslandEnabled -> XiaomiSuperIslandSettingsSection(appState.config, backdrop)
    }
}

@Composable
internal fun ExperimentalNotificationPreview(
    state: ExperimentalNotificationUiState,
    config: ScheduleConfigEntity,
    backdrop: Backdrop?,
    dialogBackdrop: Backdrop? = backdrop,
    modifier: Modifier,
    notificationsEnabled: Boolean,
    leadMinutes: String,
    notificationMode: NotificationMode,
    liveUpdateChipTextMode: LiveUpdateChipTextMode,
    liveUpdateActionsEnabled: Boolean,
    onPreviewLiveUpdate: (ScheduleConfigEntity) -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var needsIslandPrivilege by remember { mutableStateOf(false) }
    var islandTestRunning by remember { mutableStateOf(false) }
    var islandTestResult by remember { mutableStateOf<NotificationScheduler.LiveUpdatePreviewResult?>(null) }
    val deletingTest = state.cloudEnabled && state.testActive
    SettingsActionButton(
        label = when {
            islandTestRunning -> "正在发送超级岛…"
            deletingTest -> "删除测试课程"
            state.selected == ExperimentalNotificationMode.YOYO_LIVE_UPDATE -> "测试YOYO建议+实时活动"
            state.selected == ExperimentalNotificationMode.FLUID_CLOUD_LIVE_UPDATE -> "测试流体云+实时活动"
            state.cloudEnabled -> "测试流体云"
            state.superIslandEnabled -> "测试超级岛"
            else -> "测试实时活动"
        },
        backdrop = backdrop,
        glowing = true,
        destructive = deletingTest,
        badgeText = if (deletingTest) null else state.selected.badgeText,
        onClick = {
            if (islandTestRunning) return@SettingsActionButton
            if (state.superIslandEnabled && !XiaomiSuperIsland.hasPrivilege(context)) {
                needsIslandPrivilege = true
                return@SettingsActionButton
            }
            if (state.superIslandEnabled) {
                islandTestRunning = true
                scope.launch {
                    try {
                        islandTestResult = withContext(Dispatchers.IO) {
                            NotificationScheduler.showLiveUpdatePreview(context, config.copy(
                                notificationsEnabled = notificationsEnabled,
                                notificationLeadMinutes = leadMinutes.toIntOrNull() ?: config.notificationLeadMinutes,
                                notificationMode = NotificationMode.LIVE_UPDATE,
                                liveUpdateChipTextMode = liveUpdateChipTextMode,
                                liveUpdateActionsEnabled = liveUpdateActionsEnabled
                            ))
                        }
                    } finally {
                        islandTestRunning = false
                    }
                }
            } else if (state.cloudEnabled) {
                if (state.testActive) {
                    state.cancelTest()
                } else {
                    scope.launch { state.runTest() }
                    if (state.parallelLiveUpdate) {
                        onPreviewLiveUpdate(config.copy(
                            notificationsEnabled = notificationsEnabled,
                            notificationMode = NotificationMode.LIVE_UPDATE,
                            liveUpdateActionsEnabled = liveUpdateActionsEnabled
                        ))
                    }
                }
            } else {
                onPreviewLiveUpdate(config.copy(
                    notificationsEnabled = notificationsEnabled,
                    notificationLeadMinutes = leadMinutes.toIntOrNull() ?: config.notificationLeadMinutes,
                    notificationMode = notificationMode,
                    liveUpdateChipTextMode = liveUpdateChipTextMode,
                    liveUpdateActionsEnabled = liveUpdateActionsEnabled
                ))
            }
        },
        modifier = modifier
    )
    if (needsIslandPrivilege) {
        LiquidAlertDialog(
            title = "先完成超级岛授权",
            message = "请在上方选择 Shizuku 或 root 并完成授权，再测试超级岛。",
            actions = listOf(LiquidAlertAction("知道了", LiquidAlertActionStyle.Primary) {
                needsIslandPrivilege = false
            }),
            backdrop = dialogBackdrop,
            config = config,
            onDismissRequest = { needsIslandPrivilege = false }
        )
    }
    islandTestResult?.let { result ->
        LiquidAlertDialog(
            title = if (result == NotificationScheduler.LiveUpdatePreviewResult.POSTED)
                "测试通知已发送" else "超级岛测试未完成",
            message = when (result) {
                NotificationScheduler.LiveUpdatePreviewResult.POSTED ->
                    "已将测试课程通知交给系统。请查看状态栏超级岛；若未显示，请检查系统的超级岛与焦点通知开关。"
                NotificationScheduler.LiveUpdatePreviewResult.NOTIFICATIONS_UNAVAILABLE ->
                    "应用通知或课程提醒超级岛通知渠道不可用，请到系统通知设置中开启。"
                NotificationScheduler.LiveUpdatePreviewResult.VENDOR_HANDLES_PREVIEW ->
                    "当前通知由厂商课程组件处理，请先切换通知样式后重试。"
                NotificationScheduler.LiveUpdatePreviewResult.DELIVERY_FAILED ->
                    "通知发送失败，请检查授权状态后重试。"
            },
            actions = listOf(LiquidAlertAction("知道了", LiquidAlertActionStyle.Primary) {
                islandTestResult = null
            }),
            backdrop = dialogBackdrop,
            config = config,
            onDismissRequest = { islandTestResult = null }
        )
    }
    state.testResult?.let { result ->
        val success = result.proxyProviderAccessible && result.proxyVersionSupported && result.exportValid
        LiquidAlertDialog(
            title = if (success) "已请求测试课程" else "实验功能测试未就绪",
            message = when {
                result.officialWakeUpConflict -> "检测到 WakeUp 课程表，实验兼容组件无法同时安装。"
                !result.proxyIsSleepDown -> "请先在通知设置中下载并安装课程组件。"
                !result.proxyVersionSupported -> "课程组件版本过低，请先到通知设置中更新。"
                !result.exportValid -> "课程读取失败，请到通知设置中查看问题诊断。"
                else -> "已创建一门约 21～22 分钟后开始的测试课程。请返回桌面，约 1～2 分钟后观察实验功能。"
            },
            actions = listOf(
                LiquidAlertAction("完成", LiquidAlertActionStyle.Primary) { state.testResult = null }
            ),
            backdrop = dialogBackdrop,
            config = config,
            onDismissRequest = { state.testResult = null }
        )
    }
}
