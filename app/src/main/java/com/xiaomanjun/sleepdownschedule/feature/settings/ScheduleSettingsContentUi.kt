package com.xiaomanjun.sleepdownschedule.feature.settings

import com.xiaomanjun.sleepdownschedule.app.ui.*
import com.xiaomanjun.sleepdownschedule.app.startup.*
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.*
import com.xiaomanjun.sleepdownschedule.glass.ui.*
import com.xiaomanjun.sleepdownschedule.*
import com.xiaomanjun.sleepdownschedule.core.ui.settings.*
import com.xiaomanjun.sleepdownschedule.feature.schedule.picker.*
import com.xiaomanjun.sleepdownschedule.feature.home.day.*
import com.xiaomanjun.sleepdownschedule.feature.home.week.*
import com.xiaomanjun.sleepdownschedule.core.remoteconfig.*
import com.xiaomanjun.sleepdownschedule.feature.importing.*
import com.xiaomanjun.sleepdownschedule.feature.reminder.LiveUpdatePreferences
import com.xiaomanjun.sleepdownschedule.feature.reminder.NotificationScheduler
import com.xiaomanjun.sleepdownschedule.feature.agent.*
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.kyant.backdrop.catalog.components.LiquidButton
import com.kyant.backdrop.Backdrop
import kotlinx.coroutines.flow.map
import java.time.LocalTime


@Composable
fun ScheduleSettingsContent(
    section: SettingsSection,
    state: AppState,
    backdrop: Backdrop?,
    totalWeeks: String,
    onTotalWeeksChange: (String) -> Unit,
    currentWeek: String,
    onCurrentWeekChange: (String) -> Unit,
    leadMinutes: String,
    onLeadMinutesChange: (String) -> Unit,
    notificationsEnabled: Boolean,
    onNotificationsEnabledChange: (Boolean) -> Unit,
    notificationMode: NotificationMode,
    onNotificationModeChange: (NotificationMode) -> Unit,
    liveUpdateChipTextMode: LiveUpdateChipTextMode,
    onLiveUpdateChipTextModeChange: (LiveUpdateChipTextMode) -> Unit,
    liveUpdateActionsEnabled: Boolean,
    onLiveUpdateActionsEnabledChange: (Boolean) -> Unit,
    autoCurrentWeek: Boolean,
    onAutoCurrentWeekChange: (Boolean) -> Unit,
    hideEmptyWeekends: Boolean,
    onHideEmptyWeekendsChange: (Boolean) -> Unit,
    termStartDate: String,
    onTermStartDateChange: (String) -> Unit,
    classDurationMinutes: String,
    onClassDurationMinutesChange: (String) -> Unit,
    breakDurationMinutes: String,
    onBreakDurationMinutesChange: (String) -> Unit,
    morningPeriodCount: Int,
    noonPeriodCount: Int,
    afternoonPeriodCount: Int,
    eveningPeriodCount: Int,
    onPeriodCountsChange: (Int, Int, Int, Int) -> Unit,
    schemeDraft: SchedulePeriodSchemesDraft?,
    onSchemeDraftChange: (SchedulePeriodSchemesDraft) -> Unit,
    onAutoMatchPeriodEnds: () -> Unit,
    periods: List<PeriodEntity>,
    onPeriodsChange: (List<PeriodEntity>) -> Unit,
    detectedWeek: Int,
    detectedWeekDescription: String,
    error: String?,
    onPreviewLiveUpdate: () -> Unit
) {
    val appContext = LocalContext.current
    var livePreferences by remember(appContext) {
        mutableStateOf(LiveUpdatePreferences.read(appContext))
    }
    fun updateLivePreferences(update: () -> Unit) {
        update()
        livePreferences = LiveUpdatePreferences.read(appContext)
        NotificationScheduler.requestReschedule(appContext)
    }
    val topPadding = detailContentTopPadding()
    if (section == SettingsSection.Schedule) {
        ScheduleSettingsContentFixed(
            state = state,
            backdrop = backdrop,
            totalWeeks = totalWeeks,
            onTotalWeeksChange = onTotalWeeksChange,
            currentWeek = currentWeek,
            onCurrentWeekChange = onCurrentWeekChange,
            autoCurrentWeek = autoCurrentWeek,
            onAutoCurrentWeekChange = onAutoCurrentWeekChange,
            hideEmptyWeekends = hideEmptyWeekends,
            onHideEmptyWeekendsChange = onHideEmptyWeekendsChange,
            termStartDate = termStartDate,
            onTermStartDateChange = onTermStartDateChange,
            classDurationMinutes = classDurationMinutes,
            onClassDurationMinutesChange = onClassDurationMinutesChange,
            breakDurationMinutes = breakDurationMinutes,
            onBreakDurationMinutesChange = onBreakDurationMinutesChange,
            morningPeriodCount = morningPeriodCount,
            noonPeriodCount = noonPeriodCount,
            afternoonPeriodCount = afternoonPeriodCount,
            eveningPeriodCount = eveningPeriodCount,
            onPeriodCountsChange = onPeriodCountsChange,
            schemeDraft = schemeDraft,
            onSchemeDraftChange = onSchemeDraftChange,
            onAutoMatchPeriodEnds = onAutoMatchPeriodEnds,
            periods = periods,
            onPeriodsChange = onPeriodsChange,
            detectedWeek = detectedWeek,
            detectedWeekDescription = detectedWeekDescription,
            error = error,
            topPadding = topPadding
        )
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = topPadding, bottom = DockScrollPadding),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (section == SettingsSection.Schedule) {
            item {
                SettingsGroup(backdrop = backdrop, config = state.config, modifier = Modifier.fillMaxWidth()) {
                    SettingsTextFieldRow("总周数", totalWeeks, { onTotalWeeksChange(it.filter(Char::isDigit)) }, KeyboardType.Number)
                    SettingsDivider()
                    SettingsTextFieldRow("当前周", currentWeek, { onCurrentWeekChange(it.filter(Char::isDigit)) }, KeyboardType.Number, enabled = !autoCurrentWeek)
                    SettingsDivider()
                    SettingsToggleRow("自动计算当前周", detectedWeekDescription, autoCurrentWeek, backdrop, onCheckedChange = onAutoCurrentWeekChange)
                    SettingsDivider()
                    SettingsDatePickerRow("学期开始日期", termStartDate, onTermStartDateChange, backdrop, state.config)
                }
            }
            item { Text("节次时间", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 4.dp, top = 6.dp)) }
            item {
                SettingsGroup(backdrop = backdrop, config = state.config, modifier = Modifier.fillMaxWidth()) {
                    val sortedPeriods = periods.sortedBy { it.periodIndex }
                    sortedPeriods.forEachIndexed { idx, period ->
                        val bounds = periodTimePickerBounds(
                            previousEnd = sortedPeriods.getOrNull(idx - 1)?.endTime,
                            nextStart = sortedPeriods.getOrNull(idx + 1)?.startTime
                        )
                        val startMinute = parseMinuteOfDay(period.startTime) ?: bounds.minimumStartMinute
                        val endMinute = parseMinuteOfDay(period.endTime) ?: (startMinute + 1)
                        SettingsValueRow("第 ${period.periodIndex} 节", "")
                        SettingsTimePickerRow("开始时间", period.startTime, { value ->
                            onPeriodsChange(periods.map { if (it.periodIndex == period.periodIndex) it.copy(startTime = value) else it })
                        }, backdrop, state.config,
                            minimumMinute = bounds.minimumStartMinute,
                            maximumMinute = minOf(endMinute - 1, bounds.maximumEndMinute - 1)
                                .coerceAtLeast(bounds.minimumStartMinute)
                        )
                        SettingsTimePickerRow("结束时间", period.endTime, { value ->
                            onPeriodsChange(periods.map { if (it.periodIndex == period.periodIndex) it.copy(endTime = value) else it })
                        }, backdrop, state.config,
                            minimumMinute = maxOf(startMinute + 1, bounds.minimumStartMinute + 1)
                                .coerceAtMost(bounds.maximumEndMinute),
                            maximumMinute = bounds.maximumEndMinute
                        )
                        if (idx != periods.lastIndex) SettingsDivider()
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SettingsActionButton("添加节次", backdrop, onClick = {
                        val next = (periods.maxOfOrNull { it.periodIndex } ?: 0) + 1
                        onPeriodsChange(periods + PeriodEntity(next, "08:00", "08:45"))
                    })
                    SettingsActionButton("删除末节", backdrop, onClick = {
                        if (periods.isNotEmpty()) onPeriodsChange(periods.dropLast(1))
                    })
                }
            }
        } else {
            item(key = "notification-before-title") {
                Text("课前提醒", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 4.dp, top = 6.dp))
            }
            item(key = "notification-options") {
                SettingsGroup(backdrop = backdrop, config = state.config, modifier = Modifier.fillMaxWidth()) {
                    SettingsToggleRow(
                        title = "课程提醒",
                        subtitle = if (notificationsEnabled) "将按设置提前提醒即将开始的课程。" else "关闭后不会发送课程提醒。",
                        checked = notificationsEnabled,
                        backdrop = backdrop,
                        onCheckedChange = onNotificationsEnabledChange
                    )
                    SettingsDivider()
                    SettingsMinutePickerRow(
                        title = "提前提醒时间",
                        value = leadMinutes.toIntOrNull() ?: state.config.notificationLeadMinutes,
                        onValueChange = { onLeadMinutesChange(it.toString()) },
                        backdrop = backdrop,
                        config = state.config,
                        enabled = notificationsEnabled
                    )
                    SettingsDivider()
                    SettingsChoiceRow("通知样式", notificationMode, backdrop, state.config, onNotificationModeChange)
                    if (notificationMode == NotificationMode.LIVE_UPDATE) {
                        SettingsDivider()
                        SettingsLiveUpdateChipTextRow(
                            liveUpdateChipTextMode,
                            backdrop,
                            state.config,
                            onLiveUpdateChipTextModeChange
                        )
                    }
                }
            }
            if (notificationMode == NotificationMode.LIVE_UPDATE) {
                item(key = "notification-live-course-title") {
                    Text("课程实时活动", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 4.dp, top = 6.dp))
                }
                item(key = "notification-live-course") {
                    SettingsGroup(backdrop = backdrop, config = state.config, modifier = Modifier.fillMaxWidth()) {
                        SettingsToggleRow(
                            title = "实时活动按钮",
                            subtitle = "显示取消提醒和课程勿扰按钮。",
                            checked = liveUpdateActionsEnabled,
                            backdrop = backdrop,
                            enabled = notificationsEnabled,
                            onCheckedChange = onLiveUpdateActionsEnabledChange
                        )
                        SettingsDivider()
                        SettingsToggleRow(
                            title = "上课中实时活动",
                            subtitle = "开启后会用实时活动提醒距离最近课间还有多久",
                            checked = livePreferences.duringClassEnabled,
                            backdrop = backdrop,
                            enabled = notificationsEnabled,
                            onCheckedChange = { enabled ->
                                updateLivePreferences {
                                    LiveUpdatePreferences.setDuringClassEnabled(appContext, enabled)
                                }
                            }
                        )
                        SettingsDivider()
                        SettingsToggleRow(
                            title = "课间提醒",
                            subtitle = "开启后会在课间用实时活动提醒你还有多久上课",
                            checked = livePreferences.breakStatusEnabled,
                            backdrop = backdrop,
                            enabled = notificationsEnabled && livePreferences.duringClassEnabled,
                            onCheckedChange = { enabled ->
                                updateLivePreferences {
                                    LiveUpdatePreferences.setBreakStatusEnabled(appContext, enabled)
                                }
                            }
                        )
                    }
                }
                item(key = "notification-tomorrow-title") {
                    Text("明日课程", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 4.dp, top = 6.dp))
                }
                item(key = "notification-tomorrow") {
                    SettingsGroup(backdrop = backdrop, config = state.config, modifier = Modifier.fillMaxWidth()) {
                        SettingsToggleRow(
                            title = "睡前提醒",
                            subtitle = "第二天有课时，在你设置的时间提醒你",
                            checked = livePreferences.tomorrowReminderEnabled,
                            backdrop = backdrop,
                            enabled = notificationsEnabled,
                            onCheckedChange = { enabled ->
                                updateLivePreferences {
                                    LiveUpdatePreferences.setTomorrowReminderEnabled(appContext, enabled)
                                }
                            }
                        )
                        SettingsDivider()
                        SettingsTimePickerRow(
                            title = "提醒时间",
                            value = livePreferences.tomorrowReminderTime.toString(),
                            onValueChange = { value ->
                                val time = runCatching { LocalTime.parse(value) }.getOrNull()
                                    ?: return@SettingsTimePickerRow
                                updateLivePreferences {
                                    LiveUpdatePreferences.setTomorrowReminderTime(appContext, time)
                                }
                            },
                            backdrop = backdrop,
                            config = state.config,
                            enabled = notificationsEnabled && livePreferences.tomorrowReminderEnabled
                        )
                    }
                }
            }
            item(key = "notification-live-settings") {
                SettingsGroup(backdrop = backdrop, config = state.config, modifier = Modifier.fillMaxWidth()) {
                    SettingsInfoRow(
                        title = "设置实时活动",
                        body = "请在系统中允许 SleepDown 显示通知和实时活动。"
                    )
                    SettingsDivider()
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
                        SettingsActionButton(
                            "打开通知设置",
                            backdrop,
                            onClick = {
                                val intent = NotificationScheduler.promotedNotificationSettingsIntent(appContext)
                                    ?: NotificationScheduler.notificationSettingsIntent(appContext)
                                appContext.startActivity(intent)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            monochrome = true
                        )
                    }
                }
            }
            item(key = "notification-background-settings") {
                SettingsGroup(backdrop = backdrop, config = state.config, modifier = Modifier.fillMaxWidth()) {
                    SettingsInfoRow(
                        title = "允许后台活动",
                        body = "允许应用在后台运行，避免锁屏或切到后台后延迟课程提醒与实时活动更新。"
                    )
                    SettingsDivider()
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
                        SettingsActionButton(
                            "打开后台运行设置",
                            backdrop,
                            onClick = {
                                // 系统后台入口各不相同，统一打开本应用的权限管理页
                                val intent = Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.fromParts("package", appContext.packageName, null)
                                )
                                appContext.startActivity(intent)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            monochrome = true
                        )
                    }
                }
            }
            item(key = "notification-preview") { SettingsActionButton("测试实时活动", backdrop, onClick = onPreviewLiveUpdate, modifier = Modifier.fillMaxWidth()) }
        }
        error?.let {
            item(key = "notification-error") { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 4.dp)) }
        }
    }
}

@Composable
fun ScheduleSettingsContentFixed(
    state: AppState,
    backdrop: Backdrop?,
    totalWeeks: String,
    onTotalWeeksChange: (String) -> Unit,
    currentWeek: String,
    onCurrentWeekChange: (String) -> Unit,
    autoCurrentWeek: Boolean,
    onAutoCurrentWeekChange: (Boolean) -> Unit,
    hideEmptyWeekends: Boolean,
    onHideEmptyWeekendsChange: (Boolean) -> Unit,
    termStartDate: String,
    onTermStartDateChange: (String) -> Unit,
    classDurationMinutes: String,
    onClassDurationMinutesChange: (String) -> Unit,
    breakDurationMinutes: String,
    onBreakDurationMinutesChange: (String) -> Unit,
    morningPeriodCount: Int,
    noonPeriodCount: Int,
    afternoonPeriodCount: Int,
    eveningPeriodCount: Int,
    onPeriodCountsChange: (Int, Int, Int, Int) -> Unit,
    schemeDraft: SchedulePeriodSchemesDraft?,
    onSchemeDraftChange: (SchedulePeriodSchemesDraft) -> Unit,
    onAutoMatchPeriodEnds: () -> Unit,
    periods: List<PeriodEntity>,
    onPeriodsChange: (List<PeriodEntity>) -> Unit,
    detectedWeek: Int,
    detectedWeekDescription: String,
    error: String?,
    topPadding: Dp = detailContentTopPadding()
) {

    val draftConfig = state.config.copy(
        morningPeriodCount = morningPeriodCount,
        noonPeriodCount = noonPeriodCount,
        afternoonPeriodCount = afternoonPeriodCount,
        eveningPeriodCount = eveningPeriodCount
    )
    val termSettings: @Composable () -> Unit = {
        SettingsGroup(backdrop = backdrop, config = state.config, modifier = Modifier.fillMaxWidth()) {
            SettingsTextFieldRow("总周数", totalWeeks, { onTotalWeeksChange(it.filter(Char::isDigit)) }, KeyboardType.Number)
            SettingsDivider()
            SettingsTextFieldRow("当前周", currentWeek, { onCurrentWeekChange(it.filter(Char::isDigit)) }, KeyboardType.Number, enabled = !autoCurrentWeek)
            SettingsDivider()
            SettingsToggleRow("自动计算当前周", detectedWeekDescription, autoCurrentWeek, backdrop, onCheckedChange = onAutoCurrentWeekChange)
            SettingsDivider()
            SettingsToggleRow("隐藏空周末", "当前周周六、周日没有课程时自动收起周末列", hideEmptyWeekends, backdrop, onCheckedChange = onHideEmptyWeekendsChange)
            SettingsDivider()
            SettingsDatePickerRow("学期开始日期", termStartDate, onTermStartDateChange, backdrop, state.config)
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
    if (schemeDraft != null) {
        PeriodSchemeEditor(
            state, backdrop, draftConfig, schemeDraft,
            onSchemeDraftChange, onPeriodCountsChange, topPadding,
            leadingContent = termSettings
        )
    } else {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = topPadding + 12.dp, bottom = DockScrollPadding),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item { termSettings() }
            item { Text("正在读取作息…", modifier = Modifier.padding(20.dp)) }
        }
    }
}

