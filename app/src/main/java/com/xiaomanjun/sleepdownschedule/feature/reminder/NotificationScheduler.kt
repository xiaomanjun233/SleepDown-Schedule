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
    private const val SCHEDULE_HORIZON_DAYS = 8L
    private const val EVENT_COURSE = "course"
    private const val EVENT_TOMORROW = "tomorrow"
    val ACTION_CANCEL_LIVE_UPDATE = "${BuildConfig.APPLICATION_ID}.action.CANCEL_LIVE_UPDATE"
    val ACTION_TOGGLE_DND = "${BuildConfig.APPLICATION_ID}.action.TOGGLE_DND"
    val ACTION_START_LIVE_UPDATE_SERVICE = "${BuildConfig.APPLICATION_ID}.action.START_LIVE_UPDATE_SERVICE"
    val ACTION_STOP_LIVE_UPDATE_SERVICE = "${BuildConfig.APPLICATION_ID}.action.STOP_LIVE_UPDATE_SERVICE"
    val ACTION_COURSE_REMINDER = "${BuildConfig.APPLICATION_ID}.action.COURSE_REMINDER"
    val ACTION_REFRESH_COURSE_ALARMS = "${BuildConfig.APPLICATION_ID}.action.REFRESH_COURSE_ALARMS"
    private const val EXTRA_LIVE_UPDATE_NOTIFICATION = "live_update_notification"
    const val EXTRA_LIVE_UPDATE_NAME = "live_update_name"
    const val EXTRA_LIVE_UPDATE_TIME = "live_update_time"
    const val EXTRA_LIVE_UPDATE_LOCATION = "live_update_location"
    const val EXTRA_LIVE_UPDATE_ACTIONS = "live_update_actions"
    const val EXTRA_LIVE_UPDATE_MUTE_KEY = "live_update_mute_key"
    const val EXTRA_LIVE_UPDATE_MUTE_UNTIL = "live_update_mute_until"
    const val EXTRA_LIVE_UPDATE_CHIP_MODE = "live_update_chip_mode"
    const val EXTRA_LIVE_UPDATE_KIND = "live_update_kind"
    const val EXTRA_LIVE_UPDATE_SEGMENTS = "live_update_segments"
    const val EXTRA_LIVE_UPDATE_DURING_CLASS = "live_update_during_class"
    const val EXTRA_LIVE_UPDATE_BREAK_STATUS = "live_update_break_status"
    const val EXTRA_LIVE_UPDATE_EXPIRES_AT = "live_update_expires_at"
    const val EXTRA_LIVE_UPDATE_TOMORROW_COUNT = "live_update_tomorrow_count"
    const val EXTRA_REMINDER_EVENT = "reminder_event"

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

    suspend fun refreshToday(
        context: Context,
        courses: List<CourseEntity>,
        config: ScheduleConfigEntity,
        periods: List<PeriodEntity>,
        forceReschedule: Boolean = false
    ) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val liveUpdatePreferences = LiveUpdatePreferences.read(context)
        val signature = scheduleSignature(courses, config, periods, liveUpdatePreferences = liveUpdatePreferences)
        val todayIsInTerm = scheduleWeekForDateOrNull(
            config,
            LocalDate.now()
        ) != null
        if (forceReschedule || !todayIsInTerm || prefs.getString(KEY_SCHEDULE_SIGNATURE, null) != signature) {
            // Always clear stale alarms outside the term, even if this process has
            // already seen today's signature before the boundary check was fixed.
            scheduleToday(context, courses, config, periods, liveUpdatePreferences)
            prefs.edit {putString(KEY_SCHEDULE_SIGNATURE, signature)}
        }
        checkImmediateLiveUpdate(context, courses, config, periods)
    }

    internal suspend fun scheduleToday(
        context: Context,
        courses: List<CourseEntity>,
        config: ScheduleConfigEntity,
        periods: List<PeriodEntity>,
        liveUpdatePreferences: LiveUpdatePreferencesSnapshot = LiveUpdatePreferences.read(context)
    ) {
        createChannel(context)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        cancelPreviouslyScheduled(context, alarmManager)
        if (!config.notificationsEnabled) {
            return
        }
        val state = AppState(courses = courses, config = config, periods = periods)
        val now = System.currentTimeMillis()
        val scheduleZone = ZoneId.systemDefault()
        val today = LocalDate.now(scheduleZone)
        val scheduledKeys = mutableListOf<String>()
        (0L..SCHEDULE_HORIZON_DAYS).forEach { dayOffset ->
            val scheduleDate = today.plusDays(dayOffset)
            val dayCourses = coursesForDate(state, scheduleDate)
            dayCourses.forEach { course ->
                val payload = coursePayload(
                    date = scheduleDate,
                    course = course,
                    config = config,
                    periods = periods,
                    preferences = liveUpdatePreferences,
                    zone = scheduleZone
                ) ?: return@forEach
                val firstStart = payload.startAtMillis() ?: return@forEach
                val finalEnd = payload.endAtMillis() ?: firstStart
                val reminderTrigger = firstStart - config.notificationLeadMinutes.coerceAtLeast(0) * 60_000L
                val retryTriggers = if (config.notificationMode == NotificationMode.LIVE_UPDATE) {
                    listOf(reminderTrigger, reminderTrigger + 60_000L, reminderTrigger + 3 * 60_000L, reminderTrigger + 5 * 60_000L)
                } else {
                    listOf(reminderTrigger)
                }
                retryTriggers.forEachIndexed { index, trigger ->
                    if (trigger > now && trigger < finalEnd) {
                        schedulePayloadAlarm(
                            context = context,
                            alarmManager = alarmManager,
                            trigger = trigger,
                            requestCode = eventRequestCode(scheduleDate, course.id, index, EVENT_COURSE),
                            payload = payload,
                            config = config,
                            event = EVENT_COURSE,
                            scheduledKeys = scheduledKeys
                        )
                    }
                }
                if (config.notificationMode == NotificationMode.LIVE_UPDATE && liveUpdatePreferences.duringClassEnabled) {
                    payload.segments
                        .flatMap { listOf(it.startAtMillis, it.endAtMillis) }
                        .distinct()
                        .filter { it > now }
                        .forEachIndexed { index, trigger ->
                            schedulePayloadAlarm(
                                context = context,
                                alarmManager = alarmManager,
                                trigger = trigger,
                                requestCode = eventRequestCode(scheduleDate, course.id, 20 + index, EVENT_COURSE),
                                payload = payload,
                                config = config,
                                event = EVENT_COURSE,
                                scheduledKeys = scheduledKeys
                            )
                        }
                }
            }
            if (
                dayOffset > 0L &&
                config.notificationMode == NotificationMode.LIVE_UPDATE &&
                liveUpdatePreferences.tomorrowReminderEnabled &&
                dayCourses.isNotEmpty()
            ) {
                val trigger = tomorrowReminderTriggerEpochMillis(
                    targetDate = scheduleDate,
                    reminderTime = liveUpdatePreferences.tomorrowReminderTime,
                    previousDayCourses = coursesForDate(state, scheduleDate.minusDays(1)),
                    periods = periods,
                    zone = scheduleZone
                )
                if (trigger > now) {
                    val payload = tomorrowPayload(scheduleDate, dayCourses, periods, trigger, scheduleZone)
                    schedulePayloadAlarm(
                        context = context,
                        alarmManager = alarmManager,
                        trigger = trigger,
                        requestCode = eventRequestCode(scheduleDate, 0L, 0, EVENT_TOMORROW),
                        payload = payload,
                        config = config,
                        event = EVENT_TOMORROW,
                        scheduledKeys = scheduledKeys
                    )
                    schedulePayloadAlarm(
                        context = context,
                        alarmManager = alarmManager,
                        trigger = payload.expiresAtMillis,
                        requestCode = eventRequestCode(scheduleDate, 0L, 1, EVENT_TOMORROW),
                        payload = payload,
                        config = config,
                        event = EVENT_TOMORROW,
                        scheduledKeys = scheduledKeys
                    )
                }
            }
        }
        val maintenanceTrigger = today.plusDays(1).atTime(0, 5).atZone(scheduleZone).toInstant().toEpochMilli()
        val maintenanceCode = eventRequestCode(today.plusDays(1), 0L, 99, "refresh")
        scheduleAlarm(
            alarmManager,
            maintenanceTrigger,
            PendingIntent.getBroadcast(
                context,
                maintenanceCode,
                Intent(context, CourseAlarmReceiver::class.java).setAction(ACTION_REFRESH_COURSE_ALARMS),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
        scheduledKeys += "$maintenanceCode|$ACTION_REFRESH_COURSE_ALARMS"
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putString(KEY_REQUEST_CODES, scheduledKeys.joinToString(","))
        }
        Log.d(TAG, "scheduled ${scheduledKeys.size} course/live-update alarms through ${today.plusDays(SCHEDULE_HORIZON_DAYS)}")
    }

    internal fun scheduleSignature(
        courses: List<CourseEntity>,
        config: ScheduleConfigEntity,
        periods: List<PeriodEntity>,
        today: LocalDate = LocalDate.now(),
        liveUpdatePreferences: LiveUpdatePreferencesSnapshot? = null
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
            liveUpdatePreferences?.duringClassEnabled,
            liveUpdatePreferences?.breakStatusEnabled,
            liveUpdatePreferences?.tomorrowReminderEnabled,
            liveUpdatePreferences?.tomorrowReminderTime,
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
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending)
                Log.w(TAG, "scheduled inexact alarm; exact-alarm access is unavailable trigger=$trigger")
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending)
                Log.d(TAG, "scheduled exact alarm trigger=$trigger")
            }
        } catch (error: SecurityException) {
            Log.w(TAG, "exact alarm rejected; using allow-while-idle fallback", error)
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending)
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
            Log.w(TAG, "skip immediate live update: notification delivery unavailable")
            NotificationManagerCompat.from(context).cancel(LIVE_UPDATE_ID)
            stopLiveUpdateService(context)
            return
        }
        val nowMillis = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val state = AppState(courses = courses, config = config, periods = periods)
        val preferences = LiveUpdatePreferences.read(context)
        val activePayload = coursesForDate(state, today)
            .mapNotNull { course -> coursePayload(today, course, config, periods, preferences, zone) }
            .firstOrNull { payload ->
                val start = payload.startAtMillis() ?: return@firstOrNull false
                val end = payload.endAtMillis() ?: start
                val visibleEnd = if (preferences.duringClassEnabled) end else start
                nowMillis >= start - config.notificationLeadMinutes.coerceAtLeast(0) * 60_000L &&
                    nowMillis < visibleEnd
            }
            ?: immediateTomorrowPayload(
                state = state,
                periods = periods,
                preferences = preferences,
                nowMillis = nowMillis,
                zone = zone
            )
        if (activePayload == null) {
            Log.d(TAG, "skip immediate live update: no active course or tomorrow reminder")
            NotificationManagerCompat.from(context).cancel(LIVE_UPDATE_ID)
            stopLiveUpdateService(context)
            return
        }
        if (isMutedForPayload(context, activePayload, nowMillis)) {
            Log.d(TAG, "skip immediate live update: muted key=${activePayload.muteKey}")
            NotificationManagerCompat.from(context).cancel(LIVE_UPDATE_ID)
            stopLiveUpdateService(context)
            return
        }
        Log.d(
            TAG,
            "start immediate live update: kind=${activePayload.kind}, name=${activePayload.name}, " +
                "chip=${activePayload.chipTextMode}, actions=${activePayload.showActions}"
        )
        startLiveUpdateService(context, activePayload)
    }

    private fun cancelPreviouslyScheduled(context: Context, alarmManager: AlarmManager) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val keys = prefs.getString(KEY_REQUEST_CODES, "").orEmpty().split(",").filter(String::isNotBlank)
        keys.forEach { key ->
            val requestCode = key.substringBefore('|').toIntOrNull() ?: return@forEach
            val action = key.substringAfter('|', "").ifBlank { null }
            alarmManager.cancel(emptyPendingIntent(context, requestCode, action))
            if (action == null) {
                // Cancel PendingIntents created by the pre-event-action implementation.
                alarmManager.cancel(emptyPendingIntent(context, requestCode, null))
            }
        }
        prefs.edit {remove(KEY_REQUEST_CODES)}
    }

    private fun schedulePayloadAlarm(
        context: Context,
        alarmManager: AlarmManager,
        trigger: Long,
        requestCode: Int,
        payload: LiveUpdatePayload,
        config: ScheduleConfigEntity,
        event: String,
        scheduledKeys: MutableList<String>
    ) {
        val intent = Intent(context, CourseAlarmReceiver::class.java)
            .setAction(ACTION_COURSE_REMINDER)
            .putExtra(EXTRA_REMINDER_EVENT, event)
            .putExtra("notificationMode", config.notificationMode.name)
            .putLiveUpdatePayload(payload)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        scheduleAlarm(alarmManager, trigger, pendingIntent)
        scheduledKeys += "$requestCode|$ACTION_COURSE_REMINDER"
    }

    private fun emptyPendingIntent(context: Context, requestCode: Int, action: String?): PendingIntent {
        val intent = Intent(context, CourseAlarmReceiver::class.java)
        if (action != null) intent.action = action
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun eventRequestCode(date: LocalDate, courseId: Long, eventIndex: Int, event: String): Int =
        listOf(date.toEpochDay(), courseId, eventIndex, event).hashCode() and Int.MAX_VALUE

    internal fun putPayload(intent: Intent, payload: LiveUpdatePayload): Intent = intent.putLiveUpdatePayload(payload)

    internal fun payloadFromIntent(intent: Intent): LiveUpdatePayload? = intent.liveUpdatePayloadOrNull()

    private fun Intent.putLiveUpdatePayload(payload: LiveUpdatePayload): Intent =
        putExtra(EXTRA_LIVE_UPDATE_KIND, payload.kind.name)
            .putExtra(EXTRA_LIVE_UPDATE_NAME, payload.name)
            .putExtra(EXTRA_LIVE_UPDATE_TIME, payload.timeText)
            .putExtra(EXTRA_LIVE_UPDATE_LOCATION, payload.location)
            .putExtra(EXTRA_LIVE_UPDATE_ACTIONS, payload.showActions)
            .putExtra(EXTRA_LIVE_UPDATE_MUTE_KEY, payload.muteKey)
            .putExtra(EXTRA_LIVE_UPDATE_MUTE_UNTIL, payload.muteUntil)
            .putExtra(EXTRA_LIVE_UPDATE_CHIP_MODE, payload.chipTextMode.name)
            .putExtra(EXTRA_LIVE_UPDATE_SEGMENTS, encodeSegments(payload.segments))
            .putExtra(EXTRA_LIVE_UPDATE_DURING_CLASS, payload.duringClassEnabled)
            .putExtra(EXTRA_LIVE_UPDATE_BREAK_STATUS, payload.breakStatusEnabled)
            .putExtra(EXTRA_LIVE_UPDATE_EXPIRES_AT, payload.expiresAtMillis)
            .putExtra(EXTRA_LIVE_UPDATE_TOMORROW_COUNT, payload.tomorrowCourseCount)

    private fun Intent.liveUpdatePayloadOrNull(): LiveUpdatePayload? {
        val name = getStringExtra(EXTRA_LIVE_UPDATE_NAME)
            ?: getStringExtra("courseName")
            ?: return null
        val timeText = getStringExtra(EXTRA_LIVE_UPDATE_TIME)
            ?: getStringExtra("timeText")
            ?: return null
        return LiveUpdatePayload(
            kind = runCatching {
                LiveUpdateKind.valueOf(getStringExtra(EXTRA_LIVE_UPDATE_KIND) ?: LiveUpdateKind.COURSE.name)
            }.getOrDefault(LiveUpdateKind.COURSE),
            name = name,
            timeText = timeText,
            location = getStringExtra(EXTRA_LIVE_UPDATE_LOCATION)
                ?: getStringExtra("location")
                ?: "",
            showActions = getBooleanExtra(
                EXTRA_LIVE_UPDATE_ACTIONS,
                getBooleanExtra("liveUpdateActionsEnabled", true)
            ),
            muteKey = getStringExtra(EXTRA_LIVE_UPDATE_MUTE_KEY)
                ?: getStringExtra("muteKey")
                ?: "",
            muteUntil = getStringExtra(EXTRA_LIVE_UPDATE_MUTE_UNTIL)
                ?: getStringExtra("muteUntil")
                ?: "",
            chipTextMode = runCatching {
                LiveUpdateChipTextMode.valueOf(
                    getStringExtra(EXTRA_LIVE_UPDATE_CHIP_MODE)
                        ?: getStringExtra("liveUpdateChipTextMode")
                        ?: LiveUpdateChipTextMode.LOCATION.name
                )
            }.getOrDefault(LiveUpdateChipTextMode.LOCATION),
            segments = decodeSegments(getStringExtra(EXTRA_LIVE_UPDATE_SEGMENTS).orEmpty()),
            duringClassEnabled = getBooleanExtra(EXTRA_LIVE_UPDATE_DURING_CLASS, false),
            breakStatusEnabled = getBooleanExtra(EXTRA_LIVE_UPDATE_BREAK_STATUS, true),
            expiresAtMillis = getLongExtra(EXTRA_LIVE_UPDATE_EXPIRES_AT, 0L),
            tomorrowCourseCount = getIntExtra(EXTRA_LIVE_UPDATE_TOMORROW_COUNT, 0)
        )
    }

    internal fun coursePayload(
        date: LocalDate,
        course: CourseEntity,
        config: ScheduleConfigEntity,
        periods: List<PeriodEntity>,
        preferences: LiveUpdatePreferencesSnapshot,
        zone: ZoneId = ZoneId.systemDefault()
    ): LiveUpdatePayload? {
        val timeline = courseTimeline(date, course, periods, zone)
        val end = timeline.lastOrNull()?.endAtMillis ?: return null
        return LiveUpdatePayload(
            kind = LiveUpdateKind.COURSE,
            name = course.name,
            timeText = courseTimeLabel(course, periods),
            location = course.location.orEmpty(),
            showActions = config.liveUpdateActionsEnabled,
            muteKey = course.muteKey(date),
            muteUntil = end.toString(),
            chipTextMode = config.liveUpdateChipTextMode,
            segments = timeline,
            duringClassEnabled = preferences.duringClassEnabled,
            breakStatusEnabled = preferences.breakStatusEnabled,
            expiresAtMillis = end
        )
    }

    internal fun courseTimeline(
        date: LocalDate,
        course: CourseEntity,
        periods: List<PeriodEntity>,
        zone: ZoneId = ZoneId.systemDefault()
    ): List<LiveUpdateSegment> {
        course.customTimeRangeOrNull()?.let { (start, end) ->
            return listOf(
                LiveUpdateSegment(
                    date.atTime(start).atZone(zone).toInstant().toEpochMilli(),
                    date.atTime(end).atZone(zone).toInstant().toEpochMilli()
                )
            )
        }
        val periodByIndex = periods.associateBy(PeriodEntity::periodIndex)
        return course.periods.distinct().sorted().mapNotNull { periodIndex ->
            val period = periodByIndex[periodIndex] ?: return@mapNotNull null
            val start = runCatching { LocalTime.parse(period.startTime) }.getOrNull() ?: return@mapNotNull null
            val end = runCatching { LocalTime.parse(period.endTime) }.getOrNull() ?: return@mapNotNull null
            if (!end.isAfter(start)) return@mapNotNull null
            LiveUpdateSegment(
                date.atTime(start).atZone(zone).toInstant().toEpochMilli(),
                date.atTime(end).atZone(zone).toInstant().toEpochMilli()
            )
        }
    }

    internal fun tomorrowReminderTriggerEpochMillis(
        targetDate: LocalDate,
        reminderTime: LocalTime,
        previousDayCourses: List<CourseEntity>,
        periods: List<PeriodEntity>,
        zone: ZoneId = ZoneId.systemDefault()
    ): Long {
        val reminderDate = targetDate.minusDays(1)
        val configured = reminderDate.atTime(reminderTime).atZone(zone).toInstant().toEpochMilli()
        val lastCourseEnd = previousDayCourses.mapNotNull { courseEndTime(it, periods) }.maxOrNull()
            ?.let { reminderDate.atTime(it).plusMinutes(5).atZone(zone).toInstant().toEpochMilli() }
            ?: configured
        val latestReasonable = targetDate.atStartOfDay(zone).minusMinutes(5).toInstant().toEpochMilli()
        return maxOf(configured, lastCourseEnd).coerceAtMost(latestReasonable)
    }

    private fun tomorrowPayload(
        targetDate: LocalDate,
        courses: List<CourseEntity>,
        periods: List<PeriodEntity>,
        triggerAtMillis: Long,
        zone: ZoneId
    ): LiveUpdatePayload {
        val first = courses.minByOrNull { courseStartTime(it, periods) ?: LocalTime.MAX }
        val firstTime = first?.let { courseStartTime(it, periods) }
        val firstSummary = buildString {
            first?.let { course ->
                append("第一节：${course.name}")
                course.location?.takeIf(String::isNotBlank)?.let { append(" · $it") }
            }
        }
        val expiry = tomorrowReminderExpiryEpochMillis(triggerAtMillis)
        return LiveUpdatePayload(
            kind = LiveUpdateKind.TOMORROW,
            name = "明天有${courses.size}门课",
            timeText = firstTime?.let { "${it}开始" }.orEmpty(),
            location = firstSummary,
            showActions = true,
            muteKey = "tomorrow:$targetDate",
            muteUntil = expiry.toString(),
            chipTextMode = LiveUpdateChipTextMode.NORMAL,
            expiresAtMillis = expiry,
            tomorrowCourseCount = courses.size
        )
    }

    internal fun tomorrowReminderExpiryEpochMillis(triggerAtMillis: Long): Long =
        triggerAtMillis + 5 * 60_000L

    private fun immediateTomorrowPayload(
        state: AppState,
        periods: List<PeriodEntity>,
        preferences: LiveUpdatePreferencesSnapshot,
        nowMillis: Long,
        zone: ZoneId
    ): LiveUpdatePayload? {
        if (!preferences.tomorrowReminderEnabled) return null
        val today = LocalDate.now(zone)
        val tomorrow = today.plusDays(1)
        val tomorrowCourses = coursesForDate(state, tomorrow)
        if (tomorrowCourses.isEmpty()) return null
        val trigger = tomorrowReminderTriggerEpochMillis(
            targetDate = tomorrow,
            reminderTime = preferences.tomorrowReminderTime,
            previousDayCourses = coursesForDate(state, today),
            periods = periods,
            zone = zone
        )
        val payload = tomorrowPayload(tomorrow, tomorrowCourses, periods, trigger, zone)
        return payload.takeIf { nowMillis >= trigger && !it.shouldStop(nowMillis) }
    }

    private fun encodeSegments(segments: List<LiveUpdateSegment>): String = segments.joinToString(";") {
        "${it.startAtMillis}:${it.endAtMillis}"
    }

    private fun decodeSegments(value: String): List<LiveUpdateSegment> = value.split(';').mapNotNull { encoded ->
        val start = encoded.substringBefore(':').toLongOrNull() ?: return@mapNotNull null
        val end = encoded.substringAfter(':', "").toLongOrNull() ?: return@mapNotNull null
        LiveUpdateSegment(start, end).takeIf { end > start }
    }

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
        val zone = ZoneId.systemDefault()
        val date = LocalDate.now(zone)
        val start = LocalTime.now(zone)
            .plusMinutes(previewMinutes.toLong())
            .withSecond(0)
            .withNano(0)
        val end = start.plusMinutes(45)
        val timeText = "${start.format(DateTimeFormatter.ofPattern("HH:mm"))} - ${end.format(DateTimeFormatter.ofPattern("HH:mm"))}"
        val startMillis = date.atTime(start).atZone(zone).toInstant().toEpochMilli()
        val endMillis = date.atTime(end).atZone(zone).toInstant().toEpochMilli()
        startLiveUpdateService(context, LiveUpdatePayload(
            name = "高等数学",
            timeText = timeText,
            location = "教学楼 A101",
            // A preview must always be dismissible even when the user has
            // disabled optional actions for real course reminders.
            showActions = true,
            muteKey = "preview:${System.currentTimeMillis()}",
            muteUntil = startMillis.toString(),
            chipTextMode = config.liveUpdateChipTextMode,
            segments = listOf(LiveUpdateSegment(startMillis, endMillis)),
            duringClassEnabled = false,
            expiresAtMillis = startMillis
        ))
    }

    fun liveUpdateNotification(context: Context, name: String, timeText: String, location: String, showActions: Boolean, muteKey: String, muteUntil: String, chipTextMode: LiveUpdateChipTextMode): android.app.Notification {
        return liveUpdateNotification(
            context,
            LiveUpdatePayload(
                name = name,
                timeText = timeText,
                location = location,
                showActions = showActions,
                muteKey = muteKey,
                muteUntil = muteUntil,
                chipTextMode = chipTextMode
            )
        )
    }

    internal fun liveUpdateNotification(context: Context, payload: LiveUpdatePayload): android.app.Notification {
        val nowMillis = System.currentTimeMillis()
        val status = payload.statusAt(nowMillis)
        val placeText = payload.location.ifBlank { "未设置地点" }
        val shortText = when {
            payload.kind == LiveUpdateKind.TOMORROW -> "明日${payload.tomorrowCourseCount}门"
            status.phase == LiveUpdatePhase.BEFORE_CLASS -> liveUpdateChipText(
                payload.chipTextMode,
                payload.name,
                placeText,
                status.minutesToTransition
            )
            else -> liveUpdateCountdownChipText(status.minutesToTransition)
        }
        // Chip text is strictly a compact/island presentation choice. The
        // expanded notification always keeps the same complete course content.
        val titleText = payload.name
        val bodyText = if (payload.kind == LiveUpdateKind.TOMORROW) {
            status.detailText
        } else {
            "${courseCardStatusText(status)} · ${payload.timeText}"
        }
        val expandedText = if (payload.kind == LiveUpdateKind.TOMORROW || payload.location.isBlank()) {
            bodyText
        } else {
            "$bodyText\n$placeText"
        }
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
                    payload.muteKey,
                    payload.muteUntil
                )
            )
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(android.app.Notification.CATEGORY_EVENT)
            .setColor(0xFF0A84FF.toInt())
        if (payload.kind == LiveUpdateKind.TOMORROW) {
            builder
                .addAction(android.app.Notification.Action.Builder(
                    Icon.createWithResource(context, R.drawable.ic_close_light),
                    "取消提醒",
                    actionPendingIntent(
                        context,
                        ACTION_CANCEL_LIVE_UPDATE,
                        1,
                        payload.muteKey,
                        payload.muteUntil
                    )
                ).build())
        } else if (payload.showActions) {
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
                    actionPendingIntent(context, ACTION_CANCEL_LIVE_UPDATE, 1, payload.muteKey, payload.muteUntil)
                ).build())
                .addAction(android.app.Notification.Action.Builder(
                    Icon.createWithResource(context, R.drawable.ic_moon_light),
                    dndTitle,
                    dndActionPendingIntent(context, payload.muteKey, payload.muteUntil)
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
            val promotionAllowed = canPostPromotedLiveUpdates(context)
            Log.d(
                TAG,
                "live update built: promotable=$promotable, requested=$requested, " +
                    "promotionAllowed=$promotionAllowed, flags=${notification.flags}, " +
                    "style=${notification.extras.getString("android.template")}"
            )
        }
    }

    private fun courseCardStatusText(status: LiveUpdateStatus): String = when (status.phase) {
        LiveUpdatePhase.BEFORE_CLASS -> if (status.minutesToTransition <= 0) {
            "准备上课"
        } else {
            "还剩${status.minutesToTransition}分钟"
        }
        LiveUpdatePhase.IN_CLASS,
        LiveUpdatePhase.BREAK -> status.detailText
        LiveUpdatePhase.FINISHED -> "已下课"
        LiveUpdatePhase.TOMORROW -> status.statusText
    }

    private fun liveUpdateChipText(
        mode: LiveUpdateChipTextMode,
        courseName: String,
        placeText: String,
        minutesLeft: Int
    ): CharSequence = when (mode) {
        LiveUpdateChipTextMode.COUNTDOWN -> liveUpdateCountdownChipText(minutesLeft)
        LiveUpdateChipTextMode.LOCATION -> placeText
        LiveUpdateChipTextMode.SHORT,
        LiveUpdateChipTextMode.NORMAL -> courseName
    }

    private fun liveUpdateCountdownChipText(minutesLeft: Int): CharSequence {
        val safeMinutes = minutesLeft.coerceAtLeast(0)
        // Keep the island text plain. Some promoted-notification renderers reject or partially
        // preserve spans in shortCriticalText, which made the countdown fail to render normally.
        return "${safeMinutes}分钟"
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

    private fun CourseEntity.muteKey(date: LocalDate): String =
        "$id:$date:$name:${weekday}:${periods.joinToString(",")}:${weeks.joinToString(",")}"

    private fun isPreviewLiveUpdateRunning(context: Context): Boolean {
        val prefs = context.getSharedPreferences(LiveUpdatePayload.PREFS, Context.MODE_PRIVATE)
        val muteKey = prefs.getString("mute_key", "").orEmpty()
        return muteKey.startsWith("preview:")
    }

    private fun isMutedForPayload(
        context: Context,
        payload: LiveUpdatePayload,
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val key = prefs.getString(KEY_MUTED_COURSE, null) ?: return false
        val storedUntil = prefs.getString(KEY_MUTED_UNTIL, null) ?: return false
        val active = storedUntil.toLongOrNull()?.let { nowMillis < it } ?: run {
            // Compatibility with reminders muted by builds that stored only a LocalTime.
            val until = runCatching { LocalTime.parse(storedUntil) }.getOrNull() ?: return false
            LocalTime.now().isBefore(until)
        }
        if (!active) {
            prefs.edit {remove(KEY_MUTED_COURSE).remove(KEY_MUTED_UNTIL)}
            return false
        }
        return key == payload.muteKey
    }

    internal fun isPayloadMuted(context: Context, payload: LiveUpdatePayload): Boolean =
        isMutedForPayload(context, payload)

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
        startLiveUpdateService(
            context,
            LiveUpdatePayload(
                name = name,
                timeText = timeText,
                location = location,
                showActions = showActions,
                muteKey = muteKey,
                muteUntil = muteUntil,
                chipTextMode = chipTextMode
            )
        )
    }

    internal fun startLiveUpdateService(context: Context, payload: LiveUpdatePayload) {
        val notification = liveUpdateNotification(context, payload)
        val intent = Intent(context, LiveUpdateForegroundService::class.java)
            .setAction(ACTION_START_LIVE_UPDATE_SERVICE)
            .putExtra(EXTRA_LIVE_UPDATE_NOTIFICATION, notification)
            .let { putPayload(it, payload) }
        runCatching {
            ContextCompat.startForegroundService(context, intent)
            Log.d(TAG, "startForegroundService requested kind=${payload.kind} key=${payload.muteKey}")
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
        val runtimePermissionGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!runtimePermissionGranted || !NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            return false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = context.getSystemService(NotificationManager::class.java)
                ?.getNotificationChannel(CHANNEL_ID)
            if (channel?.importance == NotificationManager.IMPORTANCE_NONE) return false
        }
        return true
    }

    fun notificationSettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun canPostPromotedLiveUpdates(context: Context): Boolean? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) return null
        return context.getSystemService(NotificationManager::class.java)
            ?.canPostPromotedNotifications()
            ?: false
    }

    fun promotedNotificationSettingsIntent(context: Context): Intent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) return null
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return intent.takeIf {
            context.packageManager.resolveActivity(it, PackageManager.MATCH_DEFAULT_ONLY) != null
        }
    }

    fun canScheduleExactCourseAlarms(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val manager = context.getSystemService(AlarmManager::class.java) ?: return false
        return manager.canScheduleExactAlarms()
    }

    fun exactAlarmSettingsIntent(context: Context): Intent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || canScheduleExactCourseAlarms(context)) return null
        return Intent(
            Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun requestReschedule(context: Context) {
        val app = context.applicationContext as? CourseScheduleApp ?: return
        app.applicationScope.launch(Dispatchers.IO) {
            val snapshot = app.repository.activeSnapshot()
            refreshToday(
                context = app,
                courses = snapshot.courses,
                config = snapshot.config,
                periods = snapshot.periods,
                forceReschedule = true
            )
        }
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
