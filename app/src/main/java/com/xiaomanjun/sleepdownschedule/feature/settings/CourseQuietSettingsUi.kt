package com.xiaomanjun.sleepdownschedule.feature.settings

import android.app.NotificationManager
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.kyant.backdrop.Backdrop
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SleepDownLiquidDropdownPreference
import com.xiaomanjun.sleepdownschedule.domain.schedule.CourseQuietSoundMode
import com.xiaomanjun.sleepdownschedule.feature.reminder.CourseQuietPreferences
import com.xiaomanjun.sleepdownschedule.feature.reminder.CourseQuietScheduler
import com.xiaomanjun.sleepdownschedule.feature.reminder.NotificationScheduler
import com.xiaomanjun.sleepdownschedule.model.ScheduleConfigEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun CourseQuietSettingsGroup(backdrop: Backdrop?, config: ScheduleConfigEntity) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var settings by remember { mutableStateOf(CourseQuietPreferences.read(context)) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var hasAccess by remember { mutableStateOf(false) }
    var exactAlarms by remember { mutableStateOf(true) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        hasAccess = context.getSystemService(NotificationManager::class.java)?.isNotificationPolicyAccessGranted == true
        exactAlarms = NotificationScheduler.canScheduleExactCourseAlarms(context)
        error = CourseQuietScheduler.lastError(context)
        settings = CourseQuietPreferences.read(context)
    }
    fun update(next: com.xiaomanjun.sleepdownschedule.domain.schedule.CourseQuietSettings) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                withContext(Dispatchers.IO) { CourseQuietPreferences.write(context, next) }
                settings = next
                error = null
                NotificationScheduler.requestReschedule(context)
            } catch (failure: Exception) { error = failure.message ?: "设置保存失败" }
            finally { busy = false }
        }
    }
    GlassPreferenceSection("上课自动安静") {
        SettingsGroup(backdrop, config, Modifier.fillMaxWidth()) {
            SettingsInfoRow("跟随当前课表", "独立于课程提醒。下课后恢复原声音模式；课中手动调整声音时保留你的选择。")
            SettingsDivider()
            SettingsToggleRow("自动勿扰", "只在上课期间启用 SleepDown 的课程勿扰。", settings.doNotDisturbEnabled,
                backdrop, enabled = !busy, onCheckedChange = { update(settings.copy(doNotDisturbEnabled = it)) })
            SettingsDivider()
            SettingsToggleRow("自动静音／震动", "与自动勿扰分别开关。", settings.soundEnabled,
                backdrop, enabled = !busy, onCheckedChange = { update(settings.copy(soundEnabled = it)) })
            if (settings.soundEnabled) {
                SettingsDivider()
                SleepDownLiquidDropdownPreference(items = listOf("静音", "震动"), title = "上课声音模式",
                    selectedIndex = if (settings.soundMode == CourseQuietSoundMode.SILENT) 0 else 1,
                    backdrop = backdrop, config = config, enabled = !busy,
                    onSelectedIndexChange = { update(settings.copy(soundMode = if (it == 0) CourseQuietSoundMode.SILENT else CourseQuietSoundMode.VIBRATE)) })
            }
            SettingsDivider()
            SettingsMinutePickerRow("提前开启", settings.advanceMinutes, { update(settings.copy(advanceMinutes = it)) },
                backdrop, config, enabled = settings.enabled && !busy, range = 0..30)
            SettingsDivider()
            SettingsMinutePickerRow("下课后延迟恢复", settings.delayMinutes, { update(settings.copy(delayMinutes = it)) },
                backdrop, config, enabled = settings.enabled && !busy, range = 0..30, pickerTitle = "选择恢复延迟")
            SettingsDivider()
            SettingsMinutePickerRow("课间保持时长", settings.keepDuringBreakMinutes, { update(settings.copy(keepDuringBreakMinutes = it)) },
                backdrop, config, enabled = settings.enabled && !busy, range = 0..60, pickerTitle = "选择课间保持时长")
            SettingsInfoRow("连续课程", "相邻课程的课间不超过此时长时，保持安静模式，避免反复切换。")
            if (!hasAccess) {
                SettingsDivider()
                SettingsInfoRow("需要勿扰访问权限", "静音／震动也可能涉及系统勿扰切换。授权后自动生效；请同时允许应用在后台运行。")
                Row(Modifier.fillMaxWidth().padding(14.dp)) {
                    SettingsActionButton("授予勿扰访问权限", backdrop, onClick = {
                        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                    }, modifier = Modifier.fillMaxWidth(), monochrome = true)
                }
            }
            if (!exactAlarms) {
                SettingsDivider()
                SettingsInfoRow("精确触发", "允许精确闹钟可按上课和下课时间切换；未授权时可能延迟。")
                Row(Modifier.fillMaxWidth().padding(14.dp)) {
                    SettingsActionButton("允许精确闹钟", backdrop, onClick = {
                        NotificationScheduler.exactAlarmSettingsIntent(context)?.let(context::startActivity)
                    }, modifier = Modifier.fillMaxWidth(), monochrome = true)
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(14.dp)) }
        }
    }
}
