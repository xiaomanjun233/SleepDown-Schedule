package com.xiaomanjun.sleepdownschedule.feature.settings

import com.xiaomanjun.sleepdownschedule.core.ui.theme.appUsesDarkTheme

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.SleepDownSecondaryPageList
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.xiaomanjun.sleepdownschedule.*
import com.xiaomanjun.sleepdownschedule.core.remoteconfig.*
import com.xiaomanjun.sleepdownschedule.core.ui.chrome.DockScrollPadding
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.*
import com.xiaomanjun.sleepdownschedule.core.ui.scaffold.detailContentBottomPadding
import com.xiaomanjun.sleepdownschedule.core.ui.scaffold.detailContentTopPadding
import com.xiaomanjun.sleepdownschedule.core.ui.scaffold.settingsPageBackground
import com.xiaomanjun.sleepdownschedule.core.ui.settings.*
import com.xiaomanjun.sleepdownschedule.core.ui.settings.GlassPreferenceSection
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsActionButton
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsDatePickerRow
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsDivider
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsGroup
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsInfoRow
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsLiveUpdateChipTextRow
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsMinutePickerRow
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsNavigationRow
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsTextFieldRow
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsTimePickerRow
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsToggleRow
import com.xiaomanjun.sleepdownschedule.feature.experimental.ExperimentalNotificationChoiceRow
import com.xiaomanjun.sleepdownschedule.feature.experimental.ExperimentalNotificationDetails
import com.xiaomanjun.sleepdownschedule.feature.experimental.ExperimentalNotificationPreview
import com.xiaomanjun.sleepdownschedule.feature.experimental.rememberExperimentalNotificationUiState
import com.xiaomanjun.sleepdownschedule.feature.reminder.LiveUpdatePreferences
import com.xiaomanjun.sleepdownschedule.feature.reminder.NotificationScheduler
import com.xiaomanjun.sleepdownschedule.glass.GlassBackdropDomain
import com.xiaomanjun.sleepdownschedule.glass.glassBackdropProducer
import com.xiaomanjun.sleepdownschedule.glass.rememberGlassLayerBackdrop
import com.xiaomanjun.sleepdownschedule.glass.ui.*
import java.time.LocalTime
import kotlinx.coroutines.flow.map

@Composable
internal fun ScheduleNotificationSettingsContent(
    state: AppState,
    backdrop: Backdrop?,
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
    onPreviewLiveUpdate: (ScheduleConfigEntity) -> Unit,
) {
    val appContext = LocalContext.current
    val experimentalNotifications = rememberExperimentalNotificationUiState(notificationMode)
    var livePreferences by remember(appContext) {
        mutableStateOf(LiveUpdatePreferences.read(appContext))
    }
    fun updateLivePreferences(update: () -> Unit) {
        update()
        livePreferences = LiveUpdatePreferences.read(appContext)
        NotificationScheduler.requestReschedule(appContext)
    }
    val topPadding = detailContentTopPadding()
    val previewPageColor = settingsPageBackground(state.config)
    val previewBackdrop = rememberGlassLayerBackdrop(GlassBackdropDomain.Content, "notification-settings-body") {
        drawRect(previewPageColor)
        drawContent()
    }
    Box(Modifier.fillMaxSize()) {
        SleepDownSecondaryPageList(
            modifier = Modifier.fillMaxSize().glassBackdropProducer(previewBackdrop),
            contentTopPadding = topPadding,
            contentBottomPadding = DockScrollPadding,
        ) {
            item(key = "course-quiet-settings") {
                CourseQuietSettingsGroup(backdrop, state.config)
            }
            item(key = "notification-options") {
                GlassPreferenceSection("课前提醒") {
                    SettingsGroup(backdrop = backdrop, config = state.config, modifier = Modifier.fillMaxWidth()) {
                        SettingsToggleRow(
                            title = "课程提醒",
                            subtitle = "",
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
                        ExperimentalNotificationChoiceRow(
                            state = experimentalNotifications,
                            notificationMode = notificationMode,
                            config = state.config,
                            backdrop = backdrop,
                            onNotificationModeChange = onNotificationModeChange
                        )
                        if (experimentalNotifications.allowsLiveUpdateOptions(notificationMode) &&
                            !experimentalNotifications.superIslandEnabled) {
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
            }
            if (experimentalNotifications.hasDetails) {
                item(key = "notification-experimental") {
                    ExperimentalNotificationDetails(
                        state = experimentalNotifications,
                        appState = state,
                        backdrop = backdrop
                    )
                }
            }
            if (experimentalNotifications.allowsLiveUpdateOptions(notificationMode)) {
                item(key = "notification-live-course") {
                    GlassPreferenceSection(
                        if (experimentalNotifications.superIslandEnabled) "课程超级岛" else "课程实时活动"
                    ) {
                        SettingsGroup(backdrop = backdrop, config = state.config, modifier = Modifier.fillMaxWidth()) {
                            SettingsToggleRow(
                                title = if (experimentalNotifications.superIslandEnabled) "提醒按钮" else "实时活动按钮",
                                subtitle = "显示取消提醒和课程勿扰按钮",
                                checked = liveUpdateActionsEnabled,
                                backdrop = backdrop,
                                enabled = notificationsEnabled,
                                onCheckedChange = onLiveUpdateActionsEnabledChange
                            )
                            SettingsDivider()
                            SettingsToggleRow(
                                title = if (experimentalNotifications.superIslandEnabled) "上课中超级岛" else "上课中实时活动",
                                subtitle = if (experimentalNotifications.superIslandEnabled)
                                    "开启后显示课中提醒与距下课倒计时"
                                else "开启后会用实时活动提醒距离最近课间还有多久",
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
                                subtitle = if (experimentalNotifications.superIslandEnabled)
                                    "开启后在课间显示距离下次上课还有多久"
                                else "开启后会在课间用实时活动提醒你还有多久上课",
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
                }
                item(key = "notification-tomorrow") {
                    GlassPreferenceSection("明日课程") {
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
            }
            item(key = "notification-live-settings") {
                SettingsGroup(backdrop = backdrop, config = state.config, modifier = Modifier.fillMaxWidth()) {
                    SettingsInfoRow(
                        title = "系统权限",
                        body = "允许通知与后台运行，及时接收课程提醒"
                    )
                    SettingsDivider()
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SettingsActionButton(
                            "通知设置",
                            backdrop,
                            onClick = {
                                val intent = if (experimentalNotifications.superIslandEnabled) {
                                    NotificationScheduler.notificationSettingsIntent(appContext)
                                } else {
                                    NotificationScheduler.promotedNotificationSettingsIntent(appContext)
                                        ?: NotificationScheduler.notificationSettingsIntent(appContext)
                                }
                                appContext.startActivity(intent)
                            },
                            modifier = Modifier.weight(1f),
                            monochrome = true
                        )
                        SettingsActionButton(
                            "后台运行",
                            backdrop,
                            onClick = {
                                // 系统后台入口各不相同，统一打开本应用的权限管理页
                                val intent = Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.fromParts("package", appContext.packageName, null)
                                )
                                appContext.startActivity(intent)
                            },
                            modifier = Modifier.weight(1f),
                            monochrome = true
                        )
                    }
                }
            }
        }
        SettingsFloatingActionArea(previewPageColor, appUsesDarkTheme(state.config)) { modifier ->
            ExperimentalNotificationPreview(
                state = experimentalNotifications,
                config = state.config,
                backdrop = previewBackdrop,
                dialogBackdrop = backdrop,
                notificationsEnabled = notificationsEnabled,
                leadMinutes = leadMinutes,
                notificationMode = notificationMode,
                liveUpdateChipTextMode = liveUpdateChipTextMode,
                liveUpdateActionsEnabled = liveUpdateActionsEnabled,
                onPreviewLiveUpdate = onPreviewLiveUpdate,
                modifier = modifier
            )
        }
    }
}

@Composable
internal fun ScheduleSettingsContent(
    state: AppState,
    backdrop: Backdrop?,
    draftConfig: ScheduleConfigEntity,
    totalWeeks: String,
    onTotalWeeksChange: (String) -> Unit,
    currentWeek: String,
    onWeekStartsOnSundayChange: (Boolean) -> Unit,
    onHideEmptyWeekendsChange: (Boolean) -> Unit,
    onShowNonCurrentWeekCoursesChange: (Boolean) -> Unit,
    onTermStartDateChange: (String) -> Unit,
    onStartNewTerm: () -> Unit,
    scheduleAdjustmentsJson: String,
    onScheduleAdjustmentsChange: (String) -> Unit,
    onOpenPeriodSchemes: () -> Unit,
    onOpenSpecialCourses: () -> Unit,
    error: String?,
) {
    val config = state.config

    SleepDownSecondaryPageList(
        contentTopPadding = detailContentTopPadding(),
        contentBottomPadding = detailContentBottomPadding(),
    ) {
        item {
            GlassPreferenceSection("学期与显示") {
                SettingsGroup(backdrop = backdrop, config = config, modifier = Modifier.fillMaxWidth()) {
                    SettingsTextFieldRow("总周数", totalWeeks, { onTotalWeeksChange(it.filter(Char::isDigit)) }, KeyboardType.Number)
                    SettingsDivider()
                    SettingsTextFieldRow("当前周（自动）", currentWeek, {}, KeyboardType.Number, enabled = false)
                    SettingsDivider()
                    SettingsToggleRow("周日作为周首日", "", draftConfig.weekStartsOnSunday, backdrop,
                        onCheckedChange = onWeekStartsOnSundayChange)
                    SettingsDivider()
                    SettingsToggleRow("隐藏空周末", "", draftConfig.hideEmptyWeekends, backdrop,
                        onCheckedChange = onHideEmptyWeekendsChange)
                    SettingsDivider()
                    SettingsToggleRow("显示非本周课程", "在周视图中显示非本周课程，如对应位置处本周有课，会优先显示本周课程",
                        draftConfig.showNonCurrentWeekCourses, backdrop, onCheckedChange = onShowNonCurrentWeekCoursesChange)
                    SettingsDivider()
                    SettingsDatePickerRow("学期开始日期", draftConfig.termStartDate.orEmpty(), onTermStartDateChange, backdrop, config)
                    SettingsDivider()
                    SettingsNavigationRow("开启新学期", "沿用当前课表设置，创建空课表", onClick = onStartNewTerm)
                }
            }
        }
        item {
            GlassPreferenceSection("课程与作息") {
                SettingsGroup(backdrop = backdrop, config = config, modifier = Modifier.fillMaxWidth()) {
                    TimeSlotCoursesSettingsRow(config, onOpenSpecialCourses)
                    SettingsDivider()
                    ScheduleAdjustmentsSettingsRow(draftConfig, scheduleAdjustmentsJson, onScheduleAdjustmentsChange)
                    SettingsDivider()
                    SettingsNavigationRow("作息", "编辑课表作息和上课时间", onClick = onOpenPeriodSchemes)
                }
            }
        }
        error?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
    }
}
