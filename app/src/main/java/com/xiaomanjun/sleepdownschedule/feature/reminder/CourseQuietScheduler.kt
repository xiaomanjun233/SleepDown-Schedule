package com.xiaomanjun.sleepdownschedule.feature.reminder

import android.app.AlarmManager
import android.app.AutomaticZenRule
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.service.notification.Condition
import android.util.Log
import com.xiaomanjun.sleepdownschedule.BuildConfig
import com.xiaomanjun.sleepdownschedule.CourseAlarmReceiver
import com.xiaomanjun.sleepdownschedule.MainActivity
import com.xiaomanjun.sleepdownschedule.R
import com.xiaomanjun.sleepdownschedule.domain.schedule.courseQuietWindows
import com.xiaomanjun.sleepdownschedule.model.AppState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.ZoneId

/** Called inside NotificationScheduler's refresh mutex, independently of notification delivery. */
internal object CourseQuietScheduler {
    val RefreshAction = "${BuildConfig.APPLICATION_ID}.action.REFRESH_COURSE_QUIET"
    private const val AlarmRequestCode = 20261004
    private const val RuleName = "SleepDown 自动课程勿扰"
    private const val Tag = "SleepDownCourseQuiet"
    private val json = Json { ignoreUnknownKeys = true }
    private fun preferences(context: Context) = context.getSharedPreferences("course_quiet_runtime", Context.MODE_PRIVATE)

    fun lastError(context: Context): String? = preferences(context).getString("last_error", null)

    suspend fun refresh(context: Context, state: AppState, forceReschedule: Boolean) = withContext(Dispatchers.IO) {
        val prefs = preferences(context)
        try {
            val settings = CourseQuietPreferences.read(context)
            val runtime = prefs.getString("runtime", null)?.let { json.decodeFromString<CourseQuietRuntime>(it) }
                ?: CourseQuietRuntime()
            val zone = ZoneId.systemDefault()
            val today = LocalDate.now(zone)
            val now = System.currentTimeMillis()
            val windows = courseQuietWindows(state, settings, today, zone)
            val errors = mutableListOf<Exception>()
            val device = AndroidQuietDevice(context)
            reconcileCourseQuietState(settings, windows.any { it.contains(now) }, runtime, device,
                persist = {
                    check(prefs.edit().putString("runtime", json.encodeToString(it)).commit()) {
                        "无法保存课程勿扰的恢复状态"
                    }
                }, onError = { errors += it; Log.w(Tag, "System quiet-mode operation failed", it) })
            prefs.edit().apply {
                if (errors.isEmpty()) remove("last_error")
                else putString("last_error", "系统未能完成声音或勿扰切换，请检查勿扰访问权限。")
            }.apply()
            val nextBoundary = windows.flatMap { listOf(it.start, it.end) }.filter { it > now }.minOrNull()
            // Daily rollover is a meaningful calendar event, including days without any lessons.
            val midnight = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val next = if (settings.enabled && device.hasPolicyAccess) minOf(nextBoundary ?: midnight, midnight) else 0L
            val exact = NotificationScheduler.canScheduleExactCourseAlarms(context)
            val signature = "$next|$exact"
            if (forceReschedule || prefs.getString("alarm", null) != signature) {
                val alarm = context.getSystemService(AlarmManager::class.java)
                    ?: error("系统闹钟服务不可用")
                val intent = PendingIntent.getBroadcast(context, AlarmRequestCode,
                    Intent(context, CourseAlarmReceiver::class.java).setAction(RefreshAction),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                alarm.cancel(intent)
                if (next > now) {
                    if (exact) alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, intent)
                    else alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, intent)
                }
                prefs.edit().putString("alarm", signature).apply()
            }
        } catch (error: Exception) {
            Log.w(Tag, "Course quiet refresh failed", error)
            prefs.edit().putString("last_error", "课程勿扰未能更新，请检查设置后重试。").apply()
        }
    }

    private class AndroidQuietDevice(private val context: Context) : CourseQuietDevice {
        private val manager = requireNotNull(context.getSystemService(NotificationManager::class.java))
        private val audio = requireNotNull(context.getSystemService(AudioManager::class.java))
        private val prefs = preferences(context)
        override val hasPolicyAccess get() = manager.isNotificationPolicyAccessGranted
        override val usesAppRule get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        override val canRestoreGlobalFilter get() = Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM
        override val ringerMode get() = audio.ringerMode
        override val interruptionFilter get() = manager.currentInterruptionFilter
        override fun setRingerMode(mode: Int) { audio.ringerMode = mode }
        override fun restoreInterruptionFilter(filter: Int) { manager.setInterruptionFilter(filter) }

        override fun setDoNotDisturb(enabled: Boolean, originalFilter: Int?) {
            check(hasPolicyAccess) { "未获得勿扰模式访问权限" }
            if (!usesAppRule) {
                // Android 8/9 compatibility: never force ALL over an existing or manually changed filter.
                if (enabled) manager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
                else if (manager.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_PRIORITY) {
                    originalFilter?.let(manager::setInterruptionFilter)
                }
                return
            }
            val conditionId = Condition.newId(context).appendPath("automatic-course").build()
            val storedId = prefs.getString("rule_id", null)
            val existing = storedId?.let { manager.getAutomaticZenRule(it)?.let { rule -> it to rule } }
                ?: manager.automaticZenRules.entries.firstOrNull { it.value.conditionId == conditionId }
                    ?.let { it.key to it.value }
            if (!enabled && existing == null) return
            val id = existing?.first ?: run {
                val activity = ComponentName(context, MainActivity::class.java)
                val rule = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                    AutomaticZenRule.Builder(RuleName, conditionId)
                        .setConfigurationActivity(activity)
                        .setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
                        .setEnabled(true).setManualInvocationAllowed(false)
                        .setType(AutomaticZenRule.TYPE_SCHEDULE_TIME)
                        .setTriggerDescription("按当前课表的上课时间自动开启，下课后自动结束")
                        .setIconResId(R.drawable.ic_moon_light).build()
                } else {
                    @Suppress("DEPRECATION")
                    AutomaticZenRule(RuleName, null, activity, conditionId, null,
                        NotificationManager.INTERRUPTION_FILTER_PRIORITY, true)
                }
                requireNotNull(manager.addAutomaticZenRule(rule)) { "系统未能创建课程勿扰规则" }
            }
            check(prefs.edit().putString("rule_id", id).commit()) { "无法保存课程勿扰规则" }
            manager.setAutomaticZenRuleState(id, Condition(existing?.second?.conditionId ?: conditionId,
                if (enabled) "正在上课" else "课程已结束",
                if (enabled) Condition.STATE_TRUE else Condition.STATE_FALSE))
        }
    }
}
