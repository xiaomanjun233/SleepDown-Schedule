package com.xiaomanjun.sleepdownschedule.feature.reminder

import com.xiaomanjun.sleepdownschedule.*

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.AutomaticZenRule
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.service.notification.Condition
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.max

object NotificationScheduler {
    private const val TAG = "SleepDownLiveUpdate"
    private const val CHANNEL_ID = "course_reminders"
    private const val PREFS = "course_alarm_prefs"
    private const val KEY_REQUEST_CODES = "request_codes"
    private const val KEY_SCHEDULE_SIGNATURE = "schedule_signature"
    private const val KEY_MUTED_COURSE = "muted_course"
    private const val KEY_MUTED_UNTIL = "muted_until"
    private const val KEY_DND_ENABLED_BY_APP = "dnd_enabled_by_app"
    private const val KEY_DND_RULE_ID = "dnd_rule_id"
    private const val KEY_DND_RULE_MIGRATED = "dnd_rule_migrated"
    private const val DND_RULE_NAME = "SleepDown 课程勿扰"
    private const val LIVE_UPDATE_ID = 20260522
    val ACTION_CANCEL_LIVE_UPDATE = "${BuildConfig.APPLICATION_ID}.action.CANCEL_LIVE_UPDATE"
    val ACTION_TOGGLE_DND = "${BuildConfig.APPLICATION_ID}.action.TOGGLE_DND"
    val ACTION_START_LIVE_UPDATE_SERVICE = "${BuildConfig.APPLICATION_ID}.action.START_LIVE_UPDATE_SERVICE"
    val ACTION_STOP_LIVE_UPDATE_SERVICE = "${BuildConfig.APPLICATION_ID}.action.STOP_LIVE_UPDATE_SERVICE"
    private const val EXTRA_LIVE_UPDATE_NOTIFICATION = "live_update_notification"
    const val EXTRA_LIVE_UPDATE_NAME = "live_update_name"
    const val EXTRA_LIVE_UPDATE_TIME = "live_update_time"
    const val EXTRA_LIVE_UPDATE_LOCATION = "live_update_location"
    const val EXTRA_LIVE_UPDATE_ACTIONS = "live_update_actions"
    const val EXTRA_LIVE_UPDATE_MUTE_KEY = "live_update_mute_key"
    const val EXTRA_LIVE_UPDATE_MUTE_UNTIL = "live_update_mute_until"
    const val EXTRA_LIVE_UPDATE_CHIP_MODE = "live_update_chip_mode"

    inline fun withShortWakeLock(context: Context, tagSuffix: String, block: () -> Unit) {
        val powerManager = context.getSystemService(PowerManager::class.java)
        val wakeLock = powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SleepDown:$tagSuffix")
        runCatching { wakeLock?.acquire(5_000L) }
        try {
            block()
        } finally {
            if (wakeLock?.isHeld == true) {
                runCatching { wakeLock.release() }
            }
        }
    }

    suspend fun refreshToday(context: Context, courses: List<CourseEntity>, config: ScheduleConfigEntity, periods: List<PeriodEntity>) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val signature = scheduleSignature(courses, config, periods)
        val todayIsInTerm = scheduleWeekForDateOrNull(
            config,
            LocalDate.now()
        ) != null
        if (!todayIsInTerm || prefs.getString(KEY_SCHEDULE_SIGNATURE, null) != signature) {
            // Always clear stale alarms outside the term, even if this process has
            // already seen today's signature before the boundary check was fixed.
            scheduleToday(context, courses, config, periods)
            prefs.edit {putString(KEY_SCHEDULE_SIGNATURE, signature)}
        }
        checkImmediateLiveUpdate(context, courses, config, periods)
    }

    suspend fun scheduleToday(context: Context, courses: List<CourseEntity>, config: ScheduleConfigEntity, periods: List<PeriodEntity>) {
        createChannel(context)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        cancelPreviouslyScheduled(context, alarmManager)
        if (!config.notificationsEnabled) {
            return
        }
        val today = todayCourses(AppState(courses = courses, config = config, periods = periods))
        val now = System.currentTimeMillis()
        val scheduleZone = ZoneId.systemDefault()
        val scheduleDate = LocalDate.now(scheduleZone)
        val scheduledCodes = mutableListOf<Int>()
        today.forEach { course ->
            val start = courseStartTime(course, periods) ?: return@forEach
            val trigger = notificationTriggerEpochMillis(
                date = scheduleDate,
                time = start,
                leadMinutes = config.notificationLeadMinutes,
                zone = scheduleZone
            )
            if (trigger > now) {
                val retryTriggers = if (config.notificationMode == NotificationMode.LIVE_UPDATE) {
                    listOf(trigger, trigger + 60_000L, trigger + 3 * 60_000L, trigger + 5 * 60_000L)
                } else {
                    listOf(trigger)
                }
                retryTriggers.forEachIndexed { index, retryTrigger ->
                    val courseEnd = courseEndTime(course, periods) ?: start
                    val retryEnd = scheduleDate.atTime(courseEnd).atZone(scheduleZone).toInstant().toEpochMilli()
                    if (retryTrigger > now && retryTrigger < retryEnd) {
                        val requestCode = course.requestCode(index)
                        scheduleAlarm(alarmManager, retryTrigger, pendingIntent(context, course, config, periods, requestCode))
                        scheduledCodes += requestCode
                    }
                }
            }
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {putString(KEY_REQUEST_CODES, scheduledCodes.joinToString(","))}
    }

    internal fun scheduleSignature(
        courses: List<CourseEntity>,
        config: ScheduleConfigEntity,
        periods: List<PeriodEntity>,
        today: LocalDate = LocalDate.now()
    ): String {
        val coursePart = courses
            .sortedBy { it.id }
            .joinToString(";") {
                listOf(
                    it.id,
                    it.name,
                    it.location.orEmpty(),
                    it.weekday,
                    it.periods.joinToString(","),
                    it.weeks.joinToString(","),
                    it.weekParity.name
                ).joinToString(":")
            }
        val periodPart = periods.joinToString(";") { "${it.periodIndex},${it.startTime},${it.endTime}" }
        return listOf(
            today.toString(),
            config.totalWeeks,
            config.currentWeek,
            config.termStartDate.orEmpty(),
            config.autoCurrentWeek,
            config.notificationsEnabled,
            config.notificationLeadMinutes,
            config.notificationMode.name,
            config.liveUpdateActionsEnabled,
            config.liveUpdateChipTextMode.name,
            coursePart,
            periodPart
        ).joinToString("|")
    }

    internal fun notificationTriggerEpochMillis(
        date: LocalDate,
        time: LocalTime,
        leadMinutes: Int,
        zone: ZoneId = ZoneId.systemDefault()
    ): Long = date
        .atTime(time)
        .atZone(zone)
        .toInstant()
        .toEpochMilli() - leadMinutes.coerceAtLeast(0) * 60_000L

    private fun scheduleAlarm(alarmManager: AlarmManager, trigger: Long, pending: PendingIntent) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                alarmManager.set(AlarmManager.RTC_WAKEUP, trigger, pending)
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending)
            }
        } catch (_: SecurityException) {
            alarmManager.set(AlarmManager.RTC_WAKEUP, trigger, pending)
        }
    }

    fun checkImmediateLiveUpdate(context: Context, courses: List<CourseEntity>, config: ScheduleConfigEntity, periods: List<PeriodEntity>) {
        if (isPreviewLiveUpdateRunning(context)) {
            Log.d(TAG, "keep preview live update while app state refreshes")
            return
        }
        if (!config.notificationsEnabled || config.notificationMode != NotificationMode.LIVE_UPDATE) {
            Log.d(TAG, "skip immediate live update: disabled or mode=${config.notificationMode}")
            NotificationManagerCompat.from(context).cancel(LIVE_UPDATE_ID)
            stopLiveUpdateService(context)
            return
        }
        if (!canPostNotifications(context)) {
            Log.w(TAG, "skip immediate live update: POST_NOTIFICATIONS denied")
            NotificationManagerCompat.from(context).cancel(LIVE_UPDATE_ID)
            stopLiveUpdateService(context)
            return
        }
        val now = LocalTime.now()
        val lead = config.notificationLeadMinutes.coerceAtLeast(0).toLong()
        val active = todayCourses(AppState(courses = courses, config = config, periods = periods))
            .firstOrNull { course ->
                val start = courseStartTime(course, periods) ?: return@firstOrNull false
                !now.isBefore(start.minusMinutes(lead)) && now.isBefore(start)
            }
        if (active == null) {
            Log.d(TAG, "skip immediate live update: no active course")
            NotificationManagerCompat.from(context).cancel(LIVE_UPDATE_ID)
            stopLiveUpdateService(context)
            return
        }
        val activeEnd = courseEndTime(active, periods) ?: courseStartTime(active, periods) ?: now
        if (isMutedForCurrentCourse(context, active, activeEnd)) {
            Log.d(TAG, "skip immediate live update: muted course=${active.name}")
            NotificationManagerCompat.from(context).cancel(LIVE_UPDATE_ID)
            stopLiveUpdateService(context)
            return
        }
        Log.d(TAG, "start immediate live update: course=${active.name}, chip=${config.liveUpdateChipTextMode}, actions=${config.liveUpdateActionsEnabled}")
        startLiveUpdateService(
            context = context,
            name = active.name,
            timeText = courseTimeLabel(active, periods),
            location = active.location.orEmpty(),
            showActions = config.liveUpdateActionsEnabled,
            muteKey = active.muteKey(),
            muteUntil = activeEnd.toString(),
            chipTextMode = config.liveUpdateChipTextMode
        )
    }

    private fun cancelPreviouslyScheduled(context: Context, alarmManager: AlarmManager) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val codes = prefs.getString(KEY_REQUEST_CODES, "").orEmpty().split(",").mapNotNull { it.toIntOrNull() }
        codes.forEach { alarmManager.cancel(emptyPendingIntent(context, it)) }
        prefs.edit {remove(KEY_REQUEST_CODES)}
    }

    private fun pendingIntent(context: Context, course: CourseEntity, config: ScheduleConfigEntity, periods: List<PeriodEntity>, requestCode: Int = course.requestCode()): PendingIntent {
        val intent = Intent(context, CourseAlarmReceiver::class.java)
            .putExtra("courseName", course.name)
            .putExtra("location", course.location ?: "")
            .putExtra("timeText", courseTimeLabel(course, periods))
            .putExtra("notificationMode", config.notificationMode.name)
            .putExtra("liveUpdateActionsEnabled", config.liveUpdateActionsEnabled)
            .putExtra("liveUpdateChipTextMode", config.liveUpdateChipTextMode.name)
            .putExtra("muteKey", course.muteKey())
            .putExtra("muteUntil", (courseEndTime(course, periods) ?: courseStartTime(course, periods))?.toString().orEmpty())
        return PendingIntent.getBroadcast(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun emptyPendingIntent(context: Context, requestCode: Int): PendingIntent {
        return PendingIntent.getBroadcast(context, requestCode, Intent(context, CourseAlarmReceiver::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun CourseEntity.requestCode(retryIndex: Int = 0): Int = ((id * 10 + retryIndex) % Int.MAX_VALUE).toInt()

    fun createChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL_ID, "课程提醒", NotificationManager.IMPORTANCE_DEFAULT)
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun channelId(): String = CHANNEL_ID

    fun liveUpdateId(): Int = LIVE_UPDATE_ID

    fun showLiveUpdatePreview(context: Context, config: ScheduleConfigEntity) {
        createChannel(context)
        if (!canPostNotifications(context)) return
        val previewMinutes = config.notificationLeadMinutes.coerceIn(1, 30)
        val start = LocalTime.now()
            .plusMinutes(previewMinutes.toLong())
            .withSecond(0)
            .withNano(0)
        val end = start.plusMinutes(45)
        val timeText = "${start.format(DateTimeFormatter.ofPattern("HH:mm"))} - ${end.format(DateTimeFormatter.ofPattern("HH:mm"))}"
        startLiveUpdateService(
            context = context,
            name = "高等数学",
            timeText = timeText,
            location = "教学楼 A101",
            // A preview must always be dismissible even when the user has
            // disabled optional actions for real course reminders.
            showActions = true,
            muteKey = "preview:${System.currentTimeMillis()}",
            muteUntil = end.toString(),
            chipTextMode = config.liveUpdateChipTextMode
        )
    }

    fun liveUpdateNotification(context: Context, name: String, timeText: String, location: String, showActions: Boolean, muteKey: String, muteUntil: String, chipTextMode: LiveUpdateChipTextMode): android.app.Notification {
        return buildLiveUpdateNotification(context, name, timeText, location, minutesUntil(timeText), showActions, muteKey, muteUntil, chipTextMode)
    }

    private fun buildLiveUpdateNotification(context: Context, name: String, timeText: String, location: String, minutesLeft: Int, showActions: Boolean, muteKey: String, muteUntil: String, chipTextMode: LiveUpdateChipTextMode): android.app.Notification {
        val placeText = location.ifBlank { "未设置地点" }
        val countdownText = if (minutesLeft <= 0) "准备上课" else "还剩${minutesLeft}分钟"
        val shortText = liveUpdateChipText(chipTextMode, name, placeText, minutesLeft)
        // Chip text is strictly a compact/island presentation choice. The
        // expanded notification always keeps the same complete course content.
        val titleText = name
        val bodyText = "$countdownText · $timeText"
        val expandedText = if (location.isBlank()) bodyText else "$bodyText\n$placeText"
        val openAppIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val contentIntent = PendingIntent.getActivity(
            context,
            20260522,
            openAppIntent ?: Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = android.app.Notification.Builder(context, CHANNEL_ID)
        builder
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(titleText)
            .setContentText(bodyText)
            .setStyle(android.app.Notification.BigTextStyle().bigText(expandedText))
            .setContentIntent(contentIntent)
            .setDeleteIntent(
                actionPendingIntent(
                    context,
                    ACTION_CANCEL_LIVE_UPDATE,
                    3,
                    muteKey,
                    muteUntil
                )
            )
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(android.app.Notification.CATEGORY_EVENT)
            .setColor(0xFF0A84FF.toInt())
        if (showActions) {
            val notificationManager = context.getSystemService(NotificationManager::class.java)
            val hasDndAccess = notificationManager?.isNotificationPolicyAccessGranted == true
            val dndEnabled = isDoNotDisturbEnabledByApp(context)
            val dndTitle = when {
                !hasDndAccess -> "授权勿扰"
                dndEnabled -> "关闭勿扰"
                else -> "开启勿扰"
            }
            builder
                .addAction(android.app.Notification.Action.Builder(
                    Icon.createWithResource(context, R.drawable.ic_close_light),
                    "取消本次提醒",
                    actionPendingIntent(context, ACTION_CANCEL_LIVE_UPDATE, 1, muteKey, muteUntil)
                ).build())
                .addAction(android.app.Notification.Action.Builder(
                    Icon.createWithResource(context, R.drawable.ic_moon_light),
                    dndTitle,
                    dndActionPendingIntent(context, muteKey, muteUntil)
                ).build())
        }
        runCatching {
            builder.javaClass
                .getMethod("setRequestPromotedOngoing", java.lang.Boolean.TYPE)
                .invoke(builder, true)
            Log.d(TAG, "setRequestPromotedOngoing called")
        }.onFailure {
            Log.w(TAG, "setRequestPromotedOngoing unavailable: ${it.javaClass.simpleName}")
        }
        runCatching {
            builder.extras.putBoolean("android.requestPromotedOngoing", true)
        }
        // Android's promoted chip API is String on some releases and CharSequence on newer
        // releases. Prefer the latter so the urgency-colored number and white unit can survive
        // where the platform supports spans, then keep the plain-string fallback for old builds.
        runCatching {
            builder.javaClass
                .getMethod("setShortCriticalText", CharSequence::class.java)
                .invoke(builder, shortText)
        }.recoverCatching {
            builder.javaClass
                .getMethod("setShortCriticalText", String::class.java)
                .invoke(builder, shortText.toString())
        }
        runCatching {
            builder.extras.putCharSequence("android.shortCriticalText", shortText)
        }
        return builder.build().also { notification ->
            val promotable = runCatching {
                notification.javaClass
                    .getMethod("hasPromotableCharacteristics")
                    .invoke(notification) as? Boolean
            }.getOrNull()
            val requested = notification.extras.getBoolean("android.requestPromotedOngoing", false)
            Log.d(TAG, "live update built: promotable=$promotable, requested=$requested, flags=${notification.flags}, style=${notification.extras.getString("android.template")}")
        }
    }

    private fun liveUpdateChipText(
        mode: LiveUpdateChipTextMode,
        courseName: String,
        placeText: String,
        minutesLeft: Int
    ): CharSequence {
        return when (mode) {
            LiveUpdateChipTextMode.COUNTDOWN -> liveUpdateCountdownChipText(minutesLeft)
            LiveUpdateChipTextMode.LOCATION -> placeText
            // SHORT remains readable for old persisted settings, but no longer exposes a short
            // label. Both legacy SHORT and the new NORMAL value show the actual course name.
            LiveUpdateChipTextMode.SHORT,
            LiveUpdateChipTextMode.NORMAL -> courseName
        }
    }

    private fun liveUpdateCountdownChipText(minutesLeft: Int): CharSequence {
        val safeMinutes = minutesLeft.coerceAtLeast(0)
        // Keep the island text plain. Some promoted-notification renderers reject or partially
        // preserve spans in shortCriticalText, which made the countdown fail to render normally.
        return "${safeMinutes}分钟"
    }

    private fun minutesUntil(timeText: String): Int {
        val startText = timeText.substringBefore("-").trim()
        val start = runCatching { LocalTime.parse(startText) }.getOrNull() ?: return 0
        val now = LocalTime.now()
        return max(0, ChronoUnit.MINUTES.between(now, start).toInt())
    }

    private fun actionPendingIntent(context: Context, action: String, requestCode: Int, muteKey: String, muteUntil: String): PendingIntent {
        val intent = Intent(context, LiveUpdateActionReceiver::class.java)
            .setAction(action)
            .putExtra("muteKey", muteKey)
            .putExtra("muteUntil", muteUntil)
        return PendingIntent.getBroadcast(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun dndActionPendingIntent(context: Context, muteKey: String, muteUntil: String): PendingIntent {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager?.isNotificationPolicyAccessGranted == true) {
            return actionPendingIntent(context, ACTION_TOGGLE_DND, 2, muteKey, muteUntil)
        }
        // A notification action must open Settings directly. Routing through a BroadcastReceiver
        // is a notification trampoline and is blocked on modern Android releases.
        val settingsIntent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context,
            20260825,
            settingsIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun CourseEntity.muteKey(): String = "$id:$name:${weekday}:${periods.joinToString(",")}:${weeks.joinToString(",")}"

    private fun isPreviewLiveUpdateRunning(context: Context): Boolean {
        val prefs = context.getSharedPreferences(LiveUpdatePayload.PREFS, Context.MODE_PRIVATE)
        val muteKey = prefs.getString("mute_key", "").orEmpty()
        return muteKey.startsWith("preview:")
    }

    private fun isMutedForCurrentCourse(context: Context, course: CourseEntity, endTime: LocalTime): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val key = prefs.getString(KEY_MUTED_COURSE, null) ?: return false
        val until = prefs.getString(KEY_MUTED_UNTIL, null)?.let { runCatching { LocalTime.parse(it) }.getOrNull() } ?: return false
        val now = LocalTime.now()
        if (!now.isBefore(until)) {
            prefs.edit {remove(KEY_MUTED_COURSE).remove(KEY_MUTED_UNTIL)}
            return false
        }
        return key == course.muteKey() && until == endTime
    }

    fun cancelCurrentLiveUpdate(context: Context, muteKey: String?, muteUntil: String?) {
        if (!muteKey.isNullOrBlank() && !muteUntil.isNullOrBlank()) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
                    putString(KEY_MUTED_COURSE, muteKey)
                    .putString(KEY_MUTED_UNTIL, muteUntil)
                }
        }
        NotificationManagerCompat.from(context).cancel(LIVE_UPDATE_ID)
        stopLiveUpdateService(context)
    }

    fun toggleDoNotDisturb(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager?.isNotificationPolicyAccessGranted == true) {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val enable = !isDoNotDisturbEnabledByApp(context)
            val changed = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                    setApplicationDndRuleState(context, manager, prefs, enable)
                } else {
                    manager.setInterruptionFilter(
                        if (enable) {
                            NotificationManager.INTERRUPTION_FILTER_PRIORITY
                        } else {
                            NotificationManager.INTERRUPTION_FILTER_ALL
                        }
                    )
                }
            }.recoverCatching { ruleError ->
                // Keep the platform's implicit-rule compatibility path as a last resort for OEMs
                // whose Android 15+ rule manager rejects explicit rules.
                Log.w(TAG, "Explicit DND rule failed; using platform compatibility rule", ruleError)
                manager.setInterruptionFilter(
                    if (enable) {
                        NotificationManager.INTERRUPTION_FILTER_PRIORITY
                    } else {
                        NotificationManager.INTERRUPTION_FILTER_ALL
                    }
                )
            }.isSuccess
            if (changed) {
                prefs.edit { putBoolean(KEY_DND_ENABLED_BY_APP, enable) }
                refreshVisibleLiveUpdate(context)
            }
        } else {
            // Non-notification callers still get a safe fallback. The notification itself uses a
            // direct Activity PendingIntent so it is not subject to trampoline restrictions.
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                )
            }.onFailure { Log.w(TAG, "Unable to open DND access settings", it) }
        }
    }

    private fun isDoNotDisturbEnabledByApp(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            val manager = context.getSystemService(NotificationManager::class.java)
            val ruleId = prefs.getString(KEY_DND_RULE_ID, null)
            if (manager?.isNotificationPolicyAccessGranted == true && !ruleId.isNullOrBlank()) {
                val state = runCatching { manager.getAutomaticZenRuleState(ruleId) }.getOrNull()
                when (state) {
                    Condition.STATE_TRUE -> return true
                    Condition.STATE_FALSE -> return false
                }
            }
        }
        return prefs.getBoolean(KEY_DND_ENABLED_BY_APP, false)
    }

    private fun dndConditionId(context: Context): Uri =
        Condition.newId(context).appendPath("live-update-button").build()

    private fun setApplicationDndRuleState(
        context: Context,
        manager: NotificationManager,
        prefs: android.content.SharedPreferences,
        enabled: Boolean
    ) {
        // Deactivate the implicit rule left by pre-migration builds once. Android 15+ maps this
        // call to the app's own compatibility rule rather than changing the user's global mode.
        if (!prefs.getBoolean(KEY_DND_RULE_MIGRATED, false)) {
            runCatching {
                manager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
            }
            prefs.edit { putBoolean(KEY_DND_RULE_MIGRATED, true) }
        }

        val conditionId = dndConditionId(context)
        val storedRuleId = prefs.getString(KEY_DND_RULE_ID, null)
        val storedRule = storedRuleId?.let { id ->
            runCatching { manager.getAutomaticZenRule(id) }.getOrNull()
        }
        val existingEntry = if (storedRule != null) {
            storedRuleId to storedRule
        } else {
            runCatching { manager.automaticZenRules.entries }
                .getOrNull()
                ?.firstOrNull { (_, rule) ->
                    rule.conditionId == conditionId || rule.name == DND_RULE_NAME
                }
                ?.let { it.key to it.value }
        }
        val ruleId = existingEntry?.first ?: manager.addAutomaticZenRule(
            AutomaticZenRule.Builder(DND_RULE_NAME, conditionId)
                .setConfigurationActivity(ComponentName(context, MainActivity::class.java))
                .setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
                .setEnabled(true)
                .setManualInvocationAllowed(true)
                .setType(AutomaticZenRule.TYPE_OTHER)
                .setTriggerDescription("由课程实时活动按钮控制")
                .setIconResId(R.drawable.ic_moon_light)
                .build()
        ) ?: error("System did not create the SleepDown DND rule")
        prefs.edit { putString(KEY_DND_RULE_ID, ruleId) }

        val ruleConditionId = existingEntry?.second?.conditionId ?: conditionId
        manager.setAutomaticZenRuleState(
            ruleId,
            Condition(
                ruleConditionId,
                if (enabled) "课程勿扰已开启" else "课程勿扰已关闭",
                "",
                "",
                R.drawable.ic_moon_light,
                if (enabled) Condition.STATE_TRUE else Condition.STATE_FALSE,
                Condition.FLAG_RELEVANT_NOW,
                Condition.SOURCE_USER_ACTION
            )
        )
    }

    private fun refreshVisibleLiveUpdate(context: Context) {
        val app = context.applicationContext as? CourseScheduleApp ?: return
        app.applicationScope.launch(Dispatchers.IO) {
            val snapshot = app.repository.activeSnapshot()
            checkImmediateLiveUpdate(context, snapshot.courses, snapshot.config, snapshot.periods)
        }
    }

    fun startLiveUpdateService(
        context: Context,
        name: String,
        timeText: String,
        location: String,
        showActions: Boolean,
        muteKey: String,
        muteUntil: String,
        chipTextMode: LiveUpdateChipTextMode
    ) {
        val notification = liveUpdateNotification(
            context,
            name,
            timeText,
            location,
            showActions,
            muteKey,
            muteUntil,
            chipTextMode
        )
        val intent = Intent(context, LiveUpdateForegroundService::class.java)
            .setAction(ACTION_START_LIVE_UPDATE_SERVICE)
            .putExtra(EXTRA_LIVE_UPDATE_NOTIFICATION, notification)
            .putExtra(EXTRA_LIVE_UPDATE_NAME, name)
            .putExtra(EXTRA_LIVE_UPDATE_TIME, timeText)
            .putExtra(EXTRA_LIVE_UPDATE_LOCATION, location)
            .putExtra(EXTRA_LIVE_UPDATE_ACTIONS, showActions)
            .putExtra(EXTRA_LIVE_UPDATE_MUTE_KEY, muteKey)
            .putExtra(EXTRA_LIVE_UPDATE_MUTE_UNTIL, muteUntil)
            .putExtra(EXTRA_LIVE_UPDATE_CHIP_MODE, chipTextMode.name)
        runCatching {
            ContextCompat.startForegroundService(context, intent)
            Log.d(TAG, "startForegroundService requested")
        }.onFailure {
            Log.w(TAG, "startForegroundService failed, fallback notify: ${it.javaClass.simpleName}: ${it.message}")
            if (!canPostNotifications(context)) {
                Log.w(TAG, "fallback notify skipped: notification permission missing")
                return@onFailure
            }
            runCatching {
                postLiveUpdateNotification(context, notification)
            }.onFailure { notifyError ->
                Log.w(TAG, "fallback notify failed: ${notifyError.javaClass.simpleName}: ${notifyError.message}")
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun postLiveUpdateNotification(context: Context, notification: Notification) {
        NotificationManagerCompat.from(context).notify(LIVE_UPDATE_ID, notification)
    }

    internal fun canPostNotifications(context: Context): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    fun stopLiveUpdateService(context: Context) {
        context.getSharedPreferences(LiveUpdatePayload.PREFS, Context.MODE_PRIVATE)
            .edit {
                clear()
            }
        runCatching {
            context.stopService(Intent(context, LiveUpdateForegroundService::class.java))
        }
    }

    fun notificationFromIntent(intent: Intent): android.app.Notification? {
        @Suppress("DEPRECATION")
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_LIVE_UPDATE_NOTIFICATION, android.app.Notification::class.java)
        } else {
            intent.getParcelableExtra(EXTRA_LIVE_UPDATE_NOTIFICATION)
        }
    }
}
