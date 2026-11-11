package com.xiaomanjun.sleepdownschedule.feature.settings

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import com.kyant.backdrop.Backdrop
import com.xiaomanjun.sleepdownschedule.*
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LiquidAlertAction
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LiquidAlertActionStyle
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LiquidAlertDialog
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.SleepDownPickerDialog
import com.xiaomanjun.sleepdownschedule.core.ui.scaffold.settingsVisualConfig
import com.xiaomanjun.sleepdownschedule.core.ui.settings.LocalSettingsPopupBackdrop
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsDatePickerContent
import com.xiaomanjun.sleepdownschedule.domain.schedule.coursesOutsideShortenedTerm
import com.xiaomanjun.sleepdownschedule.feature.course.editor.compactWeekSelectionLabel
import com.xiaomanjun.sleepdownschedule.feature.course.editor.courseEditorScopeDescription
import com.xiaomanjun.sleepdownschedule.transition.legacy.ScheduleCustomizeIdExtra
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun ScheduleConfigScreen(
    state: AppState,
    backdrop: Backdrop?,
    section: SettingsSection,
    onSave: (ScheduleConfigEntity, List<PeriodEntity>) -> Unit,
    onPreviewLiveUpdate: (ScheduleConfigEntity) -> Unit,
    exitCommitRequest: Int = 0,
    onExitCommitFinished: (Boolean) -> Unit = {},
    onExitInterceptionChange: (Boolean) -> Unit = {},
    onOpenPeriodSchemes: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val repository = remember(context) { (context.applicationContext as CourseScheduleApp).repository }
    val scope = rememberCoroutineScope()
    val id = state.config.id
    val visualConfig = settingsVisualConfig(state.config)
    val popupBackdrop = LocalSettingsPopupBackdrop.current ?: backdrop
    var original by remember(id, section) { mutableStateOf(state.config) }
    var draft by remember(id, section) { mutableStateOf(state.config) }
    var totalWeeks by remember(id) { mutableStateOf(state.config.totalWeeks.toString()) }
    var adjustments by remember(id) { mutableStateOf(state.config.scheduleAdjustmentsJson) }
    var error by remember(id, section) { mutableStateOf<String?>(null) }
    var saving by remember(id) { mutableStateOf(false) }
    var askToSave by remember(id) { mutableStateOf(false) }
    var confirmShortening by remember(id) { mutableStateOf(false) }
    var choosingNewTerm by remember { mutableStateOf(false) }

    val dirty = draft != original || (section == SettingsSection.Schedule && totalWeeks != original.totalWeeks.toString())
    LaunchedEffect(state.config) {
        if (!dirty) {
            original = state.config
            draft = state.config
            totalWeeks = state.config.totalWeeks.toString()
        }
    }
    LaunchedEffect(state.config.scheduleAdjustmentsJson) { adjustments = state.config.scheduleAdjustmentsJson }
    LaunchedEffect(section, dirty, saving, choosingNewTerm) {
        onExitInterceptionChange(choosingNewTerm || shouldInterceptSettingsBack(section, dirty, saving))
    }
    DisposableEffect(Unit) { onDispose { onExitInterceptionChange(false) } }

    fun saveNotifications() {
        val updated = state.config.copy(
            notificationLeadMinutes = draft.notificationLeadMinutes,
            notificationsEnabled = draft.notificationsEnabled,
            notificationMode = draft.notificationMode,
            liveUpdateChipTextMode = draft.liveUpdateChipTextMode,
            liveUpdateActionsEnabled = draft.liveUpdateActionsEnabled,
        )
        onSave(updated, state.periods)
        original = updated
        draft = updated
    }

    fun saveSchedule(shorteningConfirmed: Boolean = false) {
        if (saving) return
        val weeks = totalWeeks.toIntOrNull()
        if (weeks == null || weeks !in 1..60) {
            error = "总周数必须在 1 到 60 之间"
            onExitCommitFinished(false)
            return
        }
        if (!shorteningConfirmed && coursesOutsideShortenedTerm(state.courses, id, original.totalWeeks, weeks).isNotEmpty()) {
            confirmShortening = true
            return
        }
        val updated = draft.copy(totalWeeks = weeks, currentWeek = original.currentWeek.coerceIn(1, weeks), autoCurrentWeek = true)
        saving = true
        error = null
        scope.launch {
            try {
                repository.saveConfigChanges(original, updated)
                val saved = repository.scheduleSnapshot(id)
                original = saved.config
                draft = saved.config
                totalWeeks = saved.config.totalWeeks.toString()
                onSave(saved.config, saved.periods)
                onExitCommitFinished(true)
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                error = failure.message ?: "设置保存失败"
                onExitCommitFinished(false)
            } finally {
                saving = false
            }
        }
    }

    LaunchedEffect(exitCommitRequest) {
        if (exitCommitRequest <= 0 || saving || choosingNewTerm) return@LaunchedEffect
        if (section == SettingsSection.Notifications) {
            if (dirty) saveNotifications()
            onExitCommitFinished(true)
        } else if (dirty) {
            askToSave = true
        } else {
            onExitCommitFinished(true)
        }
    }
    LaunchedEffect(section, draft) {
        if (section == SettingsSection.Notifications && dirty) saveNotifications()
    }

    if (section == SettingsSection.Notifications) {
        ScheduleNotificationSettingsContent(
            state = state.copy(config = visualConfig),
            backdrop = backdrop,
            leadMinutes = draft.notificationLeadMinutes.toString(),
            onLeadMinutesChange = { value -> draft = draft.copy(notificationLeadMinutes = value.toIntOrNull() ?: draft.notificationLeadMinutes) },
            notificationsEnabled = draft.notificationsEnabled,
            onNotificationsEnabledChange = { draft = draft.copy(notificationsEnabled = it) },
            notificationMode = draft.notificationMode,
            onNotificationModeChange = { draft = draft.copy(notificationMode = it) },
            liveUpdateChipTextMode = draft.liveUpdateChipTextMode,
            onLiveUpdateChipTextModeChange = { draft = draft.copy(liveUpdateChipTextMode = it) },
            liveUpdateActionsEnabled = draft.liveUpdateActionsEnabled,
            onLiveUpdateActionsEnabledChange = { draft = draft.copy(liveUpdateActionsEnabled = it) },
            onPreviewLiveUpdate = onPreviewLiveUpdate,
        )
        return
    }

    fun openSchedulePage(page: SettingsPage) {
        if (saving) return
        if (dirty) {
            error = "请先保存或放弃当前课表设置修改"
            return
        }
        if (page == SettingsPage.PeriodSchemes && onOpenPeriodSchemes != null) {
            onOpenPeriodSchemes()
        } else {
            context.startActivity(Intent(context, SettingsDetailActivity::class.java)
                .putExtra("settings_page", page.name).putExtra(ScheduleCustomizeIdExtra, id))
        }
    }

    val preview = draft.copy(totalWeeks = (totalWeeks.toIntOrNull() ?: original.totalWeeks).coerceIn(1, 60),
        autoCurrentWeek = true, scheduleAdjustmentsJson = adjustments)
    ScheduleSettingsContent(
        state = state.copy(config = visualConfig),
        backdrop = backdrop,
        draftConfig = preview,
        totalWeeks = totalWeeks,
        onTotalWeeksChange = { totalWeeks = it },
        currentWeek = effectiveCurrentWeek(preview).toString(),
        onWeekStartsOnSundayChange = { draft = draft.copy(weekStartsOnSunday = it) },
        onHideEmptyWeekendsChange = { draft = draft.copy(hideEmptyWeekends = it) },
        onShowNonCurrentWeekCoursesChange = { draft = draft.copy(showNonCurrentWeekCourses = it) },
        onTermStartDateChange = { draft = draft.copy(termStartDate = it.ifBlank { null }) },
        onStartNewTerm = { if (!saving) choosingNewTerm = true },
        scheduleAdjustmentsJson = adjustments,
        onScheduleAdjustmentsChange = { adjustments = it },
        onOpenPeriodSchemes = { openSchedulePage(SettingsPage.PeriodSchemes) },
        onOpenSpecialCourses = { openSchedulePage(SettingsPage.SpecialCourses) },
        error = error,
    )

    if (choosingNewTerm) {
        NewTermDateDialog(
            backdrop = popupBackdrop,
            config = visualConfig,
            onDismiss = { choosingNewTerm = false },
            onCreate = { date ->
                val weeks = requireNotNull(totalWeeks.toIntOrNull()?.takeIf { it in 1..60 }) {
                    "总周数必须在 1 到 60 之间"
                }
                repository.createNewTerm(original, draft.copy(totalWeeks = weeks), date)
            },
            onCreated = { scheduleId ->
                context.startActivity(Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    .putExtra(OpenScheduleWeekExtra, scheduleId))
            },
        )
    }

    if (askToSave) {
        LiquidAlertDialog(
            title = "保存课表设置？",
            message = "保存学期与显示设置后退出。",
            actions = listOf(
                LiquidAlertAction("保存并退出", LiquidAlertActionStyle.Primary) {
                    askToSave = false
                    saveSchedule()
                },
                LiquidAlertAction("不保存", LiquidAlertActionStyle.Destructive) {
                    askToSave = false
                    onExitCommitFinished(true)
                },
                LiquidAlertAction("继续编辑", LiquidAlertActionStyle.Cancel) {
                    askToSave = false
                    onExitCommitFinished(false)
                }
            ),
            config = visualConfig,
            onDismissRequest = {
                askToSave = false
                onExitCommitFinished(false)
            },
        )
    }

    if (confirmShortening) {
        val weeks = requireNotNull(totalWeeks.toIntOrNull())
        val affected = coursesOutsideShortenedTerm(state.courses, id, original.totalWeeks, weeks)
        fun cancel() {
            confirmShortening = false
            onExitCommitFinished(false)
        }
        LiquidAlertDialog(
            title = "缩短学期到 $weeks 周？",
            message = "以下 ${affected.size} 条课程安排包含超出新学期的周次，缩短后会暂时隐藏。延长学期后可重新显示。\n\n" +
                affected.joinToString("\n\n") { course ->
                    val hidden = course.copy(weeks = course.weeks.filter { it > weeks })
                    "${course.name} · ${compactWeekSelectionLabel(hidden.weeks)}\n${courseEditorScopeDescription(listOf(hidden), state.config)}"
                },
            actions = listOf(
                LiquidAlertAction("继续编辑", LiquidAlertActionStyle.Cancel) { cancel() },
                LiquidAlertAction("保留课程并缩短", LiquidAlertActionStyle.Primary) {
                    confirmShortening = false
                    saveSchedule(shorteningConfirmed = true)
                },
            ),
            config = visualConfig,
            onDismissRequest = ::cancel,
        )
    }
}

@Composable
private fun NewTermDateDialog(
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    onDismiss: () -> Unit,
    onCreate: suspend (LocalDate) -> Int,
    onCreated: (Int) -> Unit,
) {
    val today = remember { LocalDate.now() }
    var year by remember { mutableIntStateOf(today.year) }
    var month by remember { mutableIntStateOf(today.monthValue) }
    var day by remember { mutableIntStateOf(today.dayOfMonth) }
    var visible by remember { mutableStateOf(true) }
    var creating by remember { mutableStateOf(false) }
    var failureMessage by remember { mutableStateOf<String?>(null) }
    var createdId by remember { mutableStateOf<Int?>(null) }
    val scope = rememberCoroutineScope()

    fun dismiss() {
        if (!creating) visible = false
    }

    SleepDownPickerDialog(
        show = visible,
        title = "开启新学期",
        message = "选择新学期的开学日期。",
        onDismissRequest = ::dismiss,
        onDismissFinished = {
            onDismiss()
            createdId?.let(onCreated)
        },
        config = config,
        actions = listOf(
            LiquidAlertAction("取消", LiquidAlertActionStyle.Cancel, enabled = !creating, onClick = ::dismiss),
            LiquidAlertAction(
                if (creating) "正在创建…" else "创建并切换",
                LiquidAlertActionStyle.Primary,
                enabled = !creating,
                dismissOnClick = false,
            ) {
                if (!creating) {
                    val selectedMonth = YearMonth.of(year, month)
                    val selected = selectedMonth.atDay(day.coerceAtMost(selectedMonth.lengthOfMonth()))
                    creating = true
                    failureMessage = null
                    scope.launch {
                        try {
                            createdId = onCreate(selected)
                            visible = false
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (failure: Exception) {
                            failureMessage = failure.message ?: "创建新学期失败，请重试"
                            creating = false
                        }
                    }
                }
            },
        ),
    ) {
        SettingsDatePickerContent(
            year = year, month = month, day = day,
            onYearChange = { year = it }, onMonthChange = { month = it }, onDayChange = { day = it },
        )
        failureMessage?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    }
}

internal fun shouldInterceptSettingsBack(
    section: SettingsSection,
    hasUnsavedChanges: Boolean,
    saveInProgress: Boolean,
): Boolean = section == SettingsSection.Schedule && (hasUnsavedChanges || saveInProgress)

fun openKeepAliveSettings(context: Context) {
    val intents = listOf(
        Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")),
        Intent().setComponent(ComponentName("com.coloros.oppoguardelf", "com.coloros.powermanager.fuelgaue.PowerUsageModelActivity")),
        Intent().setComponent(ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")),
        Intent().setComponent(ComponentName("com.oplus.safecenter", "com.oplus.safecenter.permission.startup.StartupAppListActivity")),
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri())
    )
    intents.firstOrNull { intent ->
        runCatching {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.isSuccess
    }
}

@SuppressLint("BatteryLife")
private fun openBatteryOptimizationSettings(context: Context) {
    runCatching {
        context.startActivity(
            Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                "package:${context.packageName}".toUri()
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }.onFailure {
        context.startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                "package:${context.packageName}".toUri()
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
