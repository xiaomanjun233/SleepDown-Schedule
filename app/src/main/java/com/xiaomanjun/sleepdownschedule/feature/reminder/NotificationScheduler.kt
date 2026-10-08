package com.xiaomanjun.sleepdownschedule.feature.reminder

import com.xiaomanjun.sleepdownschedule.core.identity.applyAppNotificationIcon
import com.xiaomanjun.sleepdownschedule.core.identity.refreshAppNotificationIcons
import com.xiaomanjun.sleepdownschedule.*
import com.xiaomanjun.sleepdownschedule.domain.schedule.courseReminderSessions
import com.xiaomanjun.sleepdownschedule.domain.schedule.courseTimeSegments
import com.xiaomanjun.sleepdownschedule.feature.coloros.ColorOSCourseExperiment
import com.xiaomanjun.sleepdownschedule.feature.experimental.XiaomiSuperIsland

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
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.service.notification.Condition
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
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
    private const val KEY_DND_PREVIOUS_FILTER = "dnd_previous_filter"
    private const val KEY_DND_APPLIED_FILTER = "dnd_applied_filter"
    private const val KEY_DND_BOOT_COUNT = "dnd_boot_count"
    private const val KEY_DND_OVERRIDDEN = "dnd_overridden"
    private const val DND_RULE_NAME = "SleepDown 课程勿扰"
    private const val LIVE_UPDATE_ID = 20260522
    private const val LIVE_UPDATE_ALTERNATE_ID = 20260523
    private const val SUPER_ISLAND_ID = 20260524
    private const val SUPER_ISLAND_ALTERNATE_ID = 20260525
    private const val EXTRA_LIVE_UPDATE_IDENTITY = "sleepdown.live_update_identity"
    private const val EXTRA_ISLAND_PREVIEW = "sleepdown.island_preview"
    private const val SUPER_ISLAND_TEST_ID = 20261008
    private const val SUPER_ISLAND_TEST_ALTERNATE_ID = 20261009
    private val liveUpdatePostLock = Any()
    private const val SCHEDULE_HORIZON_DAYS = 8L
    private const val EVENT_COURSE = "course"
    private const val EVENT_TOMORROW = "tomorrow"
    private val refreshMutex = Mutex()
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
    ) = refreshMutex.withLock {
        CourseQuietScheduler.refresh(context, AppState(courses = courses, config = config, periods = periods), forceReschedule)
        val effectiveConfig = ColorOSCourseExperiment.suppressLiveUpdate(context, config)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val liveUpdatePreferences = LiveUpdatePreferences.read(context)
        val signature = scheduleSignature(courses, effectiveConfig, periods, liveUpdatePreferences = liveUpdatePreferences) +
            "|exact=${canScheduleExactCourseAlarms(context)}"
        val todayIsInTerm = scheduleWeekForDateOrNull(
            effectiveConfig,
            LocalDate.now()
        ) != null
        if (forceReschedule || !todayIsInTerm || prefs.getString(KEY_SCHEDULE_SIGNATURE, null) != signature) {
            // Always clear stale alarms outside the term, even if this process has
            // already seen today's signature before the boundary check was fixed.
            scheduleToday(context, courses, effectiveConfig, periods, liveUpdatePreferences)
            prefs.edit {putString(KEY_SCHEDULE_SIGNATURE, signature)}
        }
        LiveUpdateRecoveryWorker.updateSchedule(
            context, effectiveConfig.notificationsEnabled && effectiveConfig.notificationMode == NotificationMode.LIVE_UPDATE
        )
        if (effectiveConfig.notificationsEnabled &&
            effectiveConfig.notificationMode == NotificationMode.LIVE_UPDATE &&
            liveUpdatePreferences.duringClassEnabled && XiaomiSuperIsland.isEnabled(context)
        ) {
            val zone = ZoneId.systemDefault()
            val now = System.currentTimeMillis()
            val state = AppState(courses = courses, config = effectiveConfig, periods = periods)
            val payloads = (0L..1L).flatMap { dayOffset ->
                val date = LocalDate.now(zone).plusDays(dayOffset)
                coursesForDate(state, date)
                    .flatMap { courseReminderSessions(it, periods) }
                    .mapNotNull { course ->
                        coursePayload(date, course, effectiveConfig, periods, liveUpdatePreferences, zone)
                    }
            }
            LiveUpdateRecoveryWorker.scheduleIslandCheckpoint(
                context, nextIslandRecoveryAt(now, payloads)
            )
        }
        withContext(Dispatchers.IO) {
            checkImmediateLiveUpdate(context, courses, effectiveConfig, periods)
        }
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
        val cloudHandlesPreClass = ColorOSCourseExperiment.suppressesPreClassLiveUpdate(context)
        val scheduledKeys = mutableListOf<String>()
        (0L..SCHEDULE_HORIZON_DAYS).forEach { dayOffset ->
            val scheduleDate = today.plusDays(dayOffset)
            val dayCourses = coursesForDate(state, scheduleDate)
            dayCourses.flatMap { courseReminderSessions(it, periods) }.forEach { course ->
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
                // Allow-while-idle alarms share a per-app Doze quota. Speculative 1/3/5-minute
                // retries can postpone the real class boundary; schedule only meaningful events.
                if (reminderTrigger > now && reminderTrigger < finalEnd && !cloudHandlesPreClass) {
                    schedulePayloadAlarm(
                        context = context,
                        alarmManager = alarmManager,
                        trigger = reminderTrigger,
                        requestCode = eventRequestCode(scheduleDate, course.id, 0, "$EVENT_COURSE:$firstStart"),
                        payload = payload,
                        config = config,
                        event = EVENT_COURSE,
                        scheduledKeys = scheduledKeys
                    )
                }
                if (config.notificationMode == NotificationMode.LIVE_UPDATE) {
                    payload.refreshBoundaries()
                        .filter { it > now && it != reminderTrigger }
                        .forEachIndexed { index, trigger ->
                            schedulePayloadAlarm(
                                context = context,
                                alarmManager = alarmManager,
                                trigger = trigger,
                                requestCode = eventRequestCode(scheduleDate, course.id, 20 + index, "$EVENT_COURSE:$firstStart"),
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
                val payload = tomorrowPayload(scheduleDate, dayCourses, periods, trigger, scheduleZone)
                if (trigger > now) {
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
                }
                if (payload.expiresAtMillis > now) {
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
                    it.customStartTime.orEmpty(),
                    it.customEndTime.orEmpty(),
                    courseTimeSegments(it, periods).joinToString(",") { bell -> "${bell.index},${bell.start}-${bell.end}" },
                    it.weekParity.name
                ).joinToString(":")
            }
        val periodPart = periods.joinToString(";") { "${it.periodIndex},${it.startTime},${it.endTime}" }
        return listOf(
            "live-update-boundaries-v4-system-timer",
            today.toString(),
            ZoneId.systemDefault().id,
            config.totalWeeks,
            config.currentWeek,
            config.termStartDate.orEmpty(),
            config.autoCurrentWeek,
            config.notificationsEnabled,
            config.notificationLeadMinutes,
            config.notificationMode.name,
            config.periodAlignmentMode.name,
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
        if (ColorOSCourseExperiment.suppressesLiveUpdate(context)) {
            cancelLiveUpdateNotifications(context)
            stopLiveUpdateService(context)
            return
        }
        if (!config.notificationsEnabled || config.notificationMode != NotificationMode.LIVE_UPDATE) {
            Log.d(TAG, "skip immediate live update: disabled or mode=${config.notificationMode}")
            cancelLiveUpdateNotifications(context, includePreview = !XiaomiSuperIsland.hasActivePreview(context))
            stopLiveUpdateService(context)
            return
        }
        if (!canPostNotifications(context)) {
            Log.w(TAG, "skip immediate live update: notification delivery unavailable")
            cancelLiveUpdateNotifications(context)
            stopLiveUpdateService(context)
            return
        }
        val nowMillis = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val state = AppState(courses = courses, config = config, periods = periods)
        val preferences = LiveUpdatePreferences.read(context)
        val cloudHandlesPreClass = ColorOSCourseExperiment.suppressesPreClassLiveUpdate(context)
        val activePayload = coursesForDate(state, today)
            .flatMap { courseReminderSessions(it, periods) }
            .mapNotNull { course -> coursePayload(today, course, config, periods, preferences, zone) }
            .filter { payload ->
                !cloudHandlesPreClass ||
                    (payload.startAtMillis() ?: Long.MAX_VALUE) <= nowMillis
            }
            .let { selectImmediateCoursePayload(it, nowMillis, config.notificationLeadMinutes) }
            ?: immediateTomorrowPayload(
                state = state,
                periods = periods,
                preferences = preferences,
                nowMillis = nowMillis,
                zone = zone
            )
        if (activePayload == null) {
            if (isPreviewLiveUpdateRunning(context)) {
                Log.d(TAG, "keep preview live update while app state refreshes")
                return
            }
            Log.d(TAG, "skip immediate live update: no active course or tomorrow reminder")
            cancelLiveUpdateNotifications(context)
            stopLiveUpdateService(context)
            return
        }
        if (isMutedForPayload(context, activePayload, nowMillis)) {
            Log.d(TAG, "skip immediate live update: muted key=${activePayload.muteKey}")
            cancelLiveUpdateNotifications(context, includePreview = !XiaomiSuperIsland.hasActivePreview(context))
            stopLiveUpdateService(context)
            return
        }
        // Real courses and test previews use separate slots, so either can update independently.
        startLiveUpdateService(context, activePayload)
    }

    internal fun selectImmediateCoursePayload(
        payloads: List<LiveUpdatePayload>,
        nowMillis: Long,
        leadMinutes: Int
    ): LiveUpdatePayload? = payloads
            .filter { payload ->
                val start = payload.startAtMillis() ?: return@filter false
                val end = payload.endAtMillis() ?: start
                val visibleEnd = if (payload.duringClassEnabled) end else start
                nowMillis >= start - leadMinutes.coerceAtLeast(0) * 60_000L &&
                    nowMillis < visibleEnd
            }
            .minWithOrNull(compareBy<LiveUpdatePayload> {
                when {
                    it.segments.any { segment -> nowMillis >= segment.startAtMillis && nowMillis < segment.endAtMillis } -> 0
                    nowMillis < (it.startAtMillis() ?: Long.MAX_VALUE) -> 1
                    else -> 2
                }
            }.thenBy { it.startAtMillis() })

    /** A small set of in-class WorkManager checks repairs a missed boundary alarm. */
    internal fun nextIslandRecoveryAt(nowMillis: Long, payloads: List<LiveUpdatePayload>): Long? {
        val firstDelay = 2 * 60_000L
        val interval = 20 * 60_000L
        return payloads.asSequence()
            .filter { it.kind == LiveUpdateKind.COURSE && it.duringClassEnabled && !it.isPreview() }
            .flatMap { it.segments.asSequence() }
            .mapNotNull { segment ->
                val first = segment.startAtMillis + firstDelay
                val next = if (nowMillis < first) first else {
                    first + ((nowMillis - first) / interval + 1L) * interval
                }
                next.takeIf { it < segment.endAtMillis }
            }
            .minOrNull()
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
        return com.xiaomanjun.sleepdownschedule.domain.schedule.courseTimeSegments(course, periods).map { segment ->
            LiveUpdateSegment(
                date.atTime(segment.start).atZone(zone).toInstant().toEpochMilli(),
                date.atTime(segment.end).atZone(zone).toInstant().toEpochMilli()
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
        XiaomiSuperIsland.ensureChannel(context)
    }

    fun channelId(): String = CHANNEL_ID

    fun liveUpdateId(): Int = LIVE_UPDATE_ID

    internal enum class LiveUpdatePreviewResult {
        POSTED, NOTIFICATIONS_UNAVAILABLE, VENDOR_HANDLES_PREVIEW, DELIVERY_FAILED,
        ISLAND_PRIVILEGE_REQUIRED, ISLAND_FOCUS_REQUIRED
    }

    internal fun showLiveUpdatePreview(context: Context, config: ScheduleConfigEntity): LiveUpdatePreviewResult {
        if (ColorOSCourseExperiment.suppressesLiveUpdate(context)) {
            cancelLiveUpdateNotifications(context)
            stopLiveUpdateService(context)
            return LiveUpdatePreviewResult.VENDOR_HANDLES_PREVIEW
        }
        createChannel(context)
        if (!canPostNotifications(context)) return LiveUpdatePreviewResult.NOTIFICATIONS_UNAVAILABLE
        if (XiaomiSuperIsland.isSelected(context)) {
            if (!XiaomiSuperIsland.hasPrivilege(context)) return LiveUpdatePreviewResult.ISLAND_PRIVILEGE_REQUIRED
            if (XiaomiSuperIsland.focusPermission(context) == false) return LiveUpdatePreviewResult.ISLAND_FOCUS_REQUIRED
            if (context.getSystemService(NotificationManager::class.java)
                    ?.getNotificationChannel(XiaomiSuperIsland.ChannelId)?.importance == NotificationManager.IMPORTANCE_NONE) {
                return LiveUpdatePreviewResult.NOTIFICATIONS_UNAVAILABLE
            }
        }
        // Island tests alternate dedicated slots, producing a fresh focus event without
        // replacing an active course notification.
        stopLiveUpdateService(context)
        if (!XiaomiSuperIsland.isEnabled(context)) cancelLiveUpdateNotifications(context)
        return runCatching { startLiveUpdateService(context, liveUpdatePreviewPayload(config)) }
            .onFailure { Log.e(TAG, "live update preview delivery failed", it) }
            .getOrDefault(false)
            .let { posted ->
                if (posted) LiveUpdatePreviewResult.POSTED else LiveUpdatePreviewResult.DELIVERY_FAILED
            }
    }

    internal fun liveUpdatePreviewPayload(
        config: ScheduleConfigEntity,
        now: ZonedDateTime = ZonedDateTime.now()
    ): LiveUpdatePayload {
        val previewMinutes = config.notificationLeadMinutes.coerceIn(1, 30)
        // Keep the date and zone attached: a late-night preview can start and end tomorrow.
        // Reattaching today's date to LocalTime would make it expire as soon as it is posted.
        val start = now
            .plusMinutes(previewMinutes.toLong())
            .withSecond(0)
            .withNano(0)
        val end = start.plusMinutes(45)
        val timeText = "${start.format(DateTimeFormatter.ofPattern("HH:mm"))} - ${end.format(DateTimeFormatter.ofPattern("HH:mm"))}"
        val startMillis = start.toInstant().toEpochMilli()
        val endMillis = end.toInstant().toEpochMilli()
        return LiveUpdatePayload(
            name = "高等数学",
            timeText = timeText,
            location = "教学楼 A101",
            // Mirror the real course reminder so the preview proves whether the action buttons are
            // hidden. The preview keeps its own mute key, so it stays cancellable from the app.
            showActions = config.liveUpdateActionsEnabled,
            muteKey = "preview:${now.toInstant().toEpochMilli()}",
            muteUntil = startMillis.toString(),
            chipTextMode = config.liveUpdateChipTextMode,
            segments = listOf(LiveUpdateSegment(startMillis, endMillis)),
            duringClassEnabled = false,
            expiresAtMillis = startMillis
        )
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
        val superIslandSelected = XiaomiSuperIsland.isEnabled(context)
        val notificationIdentity = payload.notificationIdentityAt(nowMillis)
        val placeText = payload.location.ifBlank { "未设置地点" }
        val shortText = when {
            payload.kind == LiveUpdateKind.TOMORROW -> "明日${payload.tomorrowCourseCount}门"
            status.phase == LiveUpdatePhase.BEFORE_CLASS -> liveUpdateChipText(
                payload.chipTextMode,
                payload.name,
                placeText,
                status.minutesToTransition
            )
            status.phase == LiveUpdatePhase.FINISHED -> "已下课"
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
        if (superIslandSelected) {
            XiaomiSuperIsland.ensureChannel(context)
            val expiresAt = when {
                payload.expiresAtMillis > nowMillis -> payload.expiresAtMillis
                payload.isPreview() -> payload.startAtMillis()
                payload.duringClassEnabled -> payload.endAtMillis()
                else -> payload.startAtMillis()
            }
            return Notification.Builder(context, XiaomiSuperIsland.ChannelId)
                .applyAppNotificationIcon(context)
                .setContentTitle(titleText)
                .setContentText(bodyText)
                .setContentIntent(contentIntent)
                .setAutoCancel(false)
                .apply {
                    expiresAt?.takeIf { it > nowMillis }?.let { setTimeoutAfter(it - nowMillis) }
                }
                .build().also { notification ->
                    notification.extras.putString(EXTRA_LIVE_UPDATE_IDENTITY, notificationIdentity)
                    notification.extras.putBoolean(EXTRA_ISLAND_PREVIEW, payload.isPreview())
                    val dndTitle = dndActionTitle(context)
                    notification.extras.putBundle("miui.focus.actions", Bundle().apply {
                        putParcelable(XiaomiSuperIsland.DndActionKey,
                            Notification.Action.Builder(
                                Icon.createWithResource(context, R.drawable.ic_moon_light),
                                dndTitle,
                                dndActionPendingIntent(context, payload.muteKey, payload.muteUntil)
                            ).build())
                    })
                    XiaomiSuperIsland.decorate(
                        context, notification, payload, status, shortText.toString(), dndTitle
                    )
                }
        }
        val builder = android.app.Notification.Builder(context, CHANNEL_ID)
        builder
            .applyAppNotificationIcon(context)
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
            // User-confirmed ColorOS behavior: the native timer is swallowed by the promoted
            // notification renderer. Keep both the chip and card on explicit minute text.
            .setShowWhen(false)
            .setUsesChronometer(false)
            .setChronometerCountDown(false)
            .setCategory(android.app.Notification.CATEGORY_EVENT)
            .setColor(Notification.COLOR_DEFAULT)
        status.progressPercent?.takeUnless { superIslandSelected }?.let { progress ->
            val countdownLine = if (status.phase == LiveUpdatePhase.BREAK) {
                "还有${status.minutesToTransition}分钟上课"
            } else {
                "还有${status.minutesToTransition}分钟下课"
            }
            val infoLine = "${status.detailText.substringBefore(" · 还有")} · ${payload.timeText}"
            builder
                .setCategory(Notification.CATEGORY_PROGRESS)
                .setContentText("$infoLine\n$countdownLine")
            if (Build.VERSION.SDK_INT >= 36) {
                builder.setStyle(
                    Notification.ProgressStyle()
                        // 参照 llpower 的写法：用单个白色小圆点作为 tracker 图标，
                        // 不再叠加 Point，避免进度条上出现两个重叠的进度图示。
                        .setProgressTrackerIcon(
                            Icon.createWithResource(context, R.drawable.ic_live_dot)
                        )
                        .setProgressSegments(listOf(
                            Notification.ProgressStyle.Segment(100)
                        ))
                        .setProgress(progress)
                )
            } else {
                builder.setStyle(null).setProgress(100, progress, false)
            }
        }
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
        } else if (payload.showActions && (superIslandSelected || status.progressPercent == null)) {
            val dndTitle = dndActionTitle(context)
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
        if (!superIslandSelected) {
            runCatching {
                builder.javaClass
                    .getMethod("setRequestPromotedOngoing", java.lang.Boolean.TYPE)
                    .invoke(builder, true)
                Log.d(TAG, "setRequestPromotedOngoing called")
            }.onFailure {
                Log.w(TAG, "setRequestPromotedOngoing unavailable: ${it.javaClass.simpleName}")
            }
            builder.extras.putBoolean("android.requestPromotedOngoing", true)
            // Plain short text remains visible on ColorOS; never delegate the chip to a chronometer.
            runCatching {
                builder.javaClass
                    .getMethod("setShortCriticalText", CharSequence::class.java)
                    .invoke(builder, shortText)
            }.recoverCatching {
                builder.javaClass
                    .getMethod("setShortCriticalText", String::class.java)
                    .invoke(builder, shortText.toString())
            }
            builder.extras.putCharSequence("android.shortCriticalText", shortText)
        }
        builder.extras.putString(EXTRA_LIVE_UPDATE_IDENTITY, notificationIdentity)
        return builder.build().also { notification ->
            XiaomiSuperIsland.decorate(context, notification, payload, status, shortText.toString())
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

    private fun liveUpdateCountdownChipText(minutesLeft: Int): String =
        "${minutesLeft.coerceAtLeast(0)}分钟"

    private fun actionPendingIntent(context: Context, action: String, requestCode: Int, muteKey: String, muteUntil: String): PendingIntent {
        val intent = Intent(context, LiveUpdateActionReceiver::class.java)
            .setAction(action)
            .putExtra("muteKey", muteKey)
            .putExtra("muteUntil", muteUntil)
            .addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
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

    private fun dndActionTitle(context: Context): String {
        val manager = context.getSystemService(NotificationManager::class.java)
        return when {
            manager?.isNotificationPolicyAccessGranted != true -> "授权勿扰"
            isDoNotDisturbEnabledByApp(context) -> "关闭勿扰"
            else -> "开启勿扰"
        }
    }

    private fun CourseEntity.muteKey(date: LocalDate): String =
        "$id:$date:$name:${weekday}:${periods.joinToString(",")}:${weeks.joinToString(",")}"

    private fun isPreviewLiveUpdateRunning(context: Context): Boolean {
        if (XiaomiSuperIsland.hasActivePreview(context)) return true
        val prefs = context.getSharedPreferences(LiveUpdatePayload.PREFS, Context.MODE_PRIVATE)
        val muteKey = prefs.getString("mute_key", "").orEmpty()
        return muteKey.startsWith("preview:") &&
            prefs.getLong("expires_at", 0L) > System.currentTimeMillis()
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
        cancelLiveUpdateNotifications(context)
        stopLiveUpdateService(context)
    }

    fun toggleDoNotDisturb(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager?.isNotificationPolicyAccessGranted == true) {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val enable = !isDoNotDisturbEnabledByApp(context)
            val changed = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                    runCatching { setApplicationDndRuleState(context, manager, prefs, enable) }
                        .recoverCatching { ruleError ->
                            // Android 15+ scopes this fallback to the app's implicit rule.
                            Log.w(TAG, "Explicit DND rule failed; using platform compatibility rule", ruleError)
                            manager.setInterruptionFilter(
                                if (enable) NotificationManager.INTERRUPTION_FILTER_PRIORITY
                                else NotificationManager.INTERRUPTION_FILTER_ALL
                            )
                        }.getOrThrow()
                } else {
                    setLegacyDndState(context, manager, prefs, enable)
                }
            }.onFailure { Log.w(TAG, "Unable to change course DND state", it) }.isSuccess
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

    private fun legacyDndSession(prefs: android.content.SharedPreferences): LegacyDndSession? {
        if (!prefs.contains(KEY_DND_PREVIOUS_FILTER) || !prefs.contains(KEY_DND_APPLIED_FILTER) ||
            !prefs.contains(KEY_DND_BOOT_COUNT)) return null
        return LegacyDndSession(
            previousFilter = prefs.getInt(KEY_DND_PREVIOUS_FILTER, NotificationManager.INTERRUPTION_FILTER_UNKNOWN),
            appliedFilter = prefs.getInt(KEY_DND_APPLIED_FILTER, NotificationManager.INTERRUPTION_FILTER_UNKNOWN),
            bootCount = prefs.getInt(KEY_DND_BOOT_COUNT, -1),
            overridden = prefs.getBoolean(KEY_DND_OVERRIDDEN, false)
        )
    }

    private fun setLegacyDndState(
        context: Context,
        manager: NotificationManager,
        prefs: android.content.SharedPreferences,
        enabled: Boolean
    ) {
        val currentFilter = manager.currentInterruptionFilter
        val bootCount = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)
        if (enabled) {
            val session = LegacyDndSession.start(currentFilter, bootCount)
            // Persist ownership before changing system state so process death cannot lose the snapshot.
            check(prefs.edit()
                .putInt(KEY_DND_PREVIOUS_FILTER, session.previousFilter)
                .putInt(KEY_DND_APPLIED_FILTER, session.appliedFilter)
                .putInt(KEY_DND_BOOT_COUNT, session.bootCount)
                .putBoolean(KEY_DND_OVERRIDDEN, false)
                .putBoolean(KEY_DND_ENABLED_BY_APP, true)
                .commit()) { "Unable to save the previous DND mode" }
            if (currentFilter != session.appliedFilter) manager.setInterruptionFilter(session.appliedFilter)
        } else {
            val restore = legacyDndSession(prefs)?.filterToRestore(currentFilter, bootCount)
            if (restore != null && restore != currentFilter) manager.setInterruptionFilter(restore)
            // Older builds have no snapshot: release ownership without guessing an original mode.
            prefs.edit(commit = true) {
                putBoolean(KEY_DND_ENABLED_BY_APP, false)
                remove(KEY_DND_PREVIOUS_FILTER)
                remove(KEY_DND_APPLIED_FILTER)
                remove(KEY_DND_BOOT_COUNT)
                remove(KEY_DND_OVERRIDDEN)
            }
        }
    }

    internal fun observeLegacyDndState(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_DND_ENABLED_BY_APP, false)) return
        val session = legacyDndSession(prefs) ?: return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val bootCount = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)
        if (session.observe(manager.currentInterruptionFilter, bootCount).overridden) {
            // Once relinquished, returning manually to the same filter does not regain ownership.
            prefs.edit(commit = true) { putBoolean(KEY_DND_OVERRIDDEN, true) }
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

    fun refreshLiveUpdateIcon(context: Context) {
        refreshAppNotificationIcons(context)
    }

    private fun refreshVisibleLiveUpdate(context: Context) {
        requestRefresh(context)
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

    internal fun startLiveUpdateService(context: Context, payload: LiveUpdatePayload): Boolean {
        if (!payload.isPreview() && payload.kind == LiveUpdateKind.COURSE &&
            ColorOSCourseExperiment.suppressesPreClassLiveUpdate(context) &&
            payload.statusAt().phase == LiveUpdatePhase.BEFORE_CLASS) return false
        val notification = liveUpdateNotification(context, payload)
        // Submit while the event receiver still holds its wake lock. Delivery must not wait
        // for the FGS (or its optional minute loop) to be scheduled by an OEM background policy.
        if (!canPostNotifications(context)) return false
        if (XiaomiSuperIsland.isEnabled(context)) {
            // A foreground service immediately reposts the focus notification as ongoing and
            // prevents the Xiaomi float from appearing.
            stopLiveUpdateService(context)
            try {
                if (!postLiveUpdateNotification(context, notification)) return false
                if (payload.isPreview()) {
                    XiaomiSuperIsland.markPreview(context, payload.startAtMillis() ?: 0L)
                }
            } catch (error: SecurityException) {
                Log.w(TAG, "super island rejected: notification permission revoked", error)
                return false
            }
            return true
        }
        try {
            postLiveUpdateNotification(context, notification)
        } catch (error: SecurityException) {
            Log.w(TAG, "live update rejected: notification permission revoked", error)
            return false
        }
        val intent = Intent(context, LiveUpdateForegroundService::class.java)
            .setAction(ACTION_START_LIVE_UPDATE_SERVICE)
            .putExtra(EXTRA_LIVE_UPDATE_NOTIFICATION, notification)
            .let { putPayload(it, payload) }
        runCatching {
            ContextCompat.startForegroundService(context, intent)
            Log.d(TAG, "startForegroundService requested kind=${payload.kind} key=${payload.muteKey}")
        }.onFailure {
            Log.w(TAG, "minute refresh service unavailable; event notification already posted", it)
        }
        return true
    }

    @SuppressLint("MissingPermission")
    internal fun postLiveUpdateNotification(
        context: Context,
        notification: Notification,
        attachForeground: ((Int, Notification) -> Unit)? = null
    ) = synchronized(liveUpdatePostLock) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return@synchronized false
        val active = manager.activeNotifications.filter {
            it.id == LIVE_UPDATE_ID || it.id == LIVE_UPDATE_ALTERNATE_ID ||
                it.id == SUPER_ISLAND_ID || it.id == SUPER_ISLAND_ALTERNATE_ID ||
                it.id == SUPER_ISLAND_TEST_ID || it.id == SUPER_ISLAND_TEST_ALTERNATE_ID
        }.sortedByDescending { it.postTime }
        val identity = notification.extras.getString(EXTRA_LIVE_UPDATE_IDENTITY).orEmpty()
        val island = XiaomiSuperIsland.isEnabled(context) &&
            notification.channelId == XiaomiSuperIsland.ChannelId
        val preview = island && notification.extras.getBoolean(EXTRA_ISLAND_PREVIEW)
        val id = if (preview) {
            liveUpdateNotificationSlot(active.filter {
                it.id == SUPER_ISLAND_TEST_ID || it.id == SUPER_ISLAND_TEST_ALTERNATE_ID
            }.map { it.id to it.notification.extras.getString(EXTRA_LIVE_UPDATE_IDENTITY) },
                identity, SUPER_ISLAND_TEST_ID, SUPER_ISLAND_TEST_ALTERNATE_ID)
        } else if (island) {
            liveUpdateNotificationSlot(
                active.filter { it.id == SUPER_ISLAND_ID || it.id == SUPER_ISLAND_ALTERNATE_ID }
                    .map { it.id to it.notification.extras.getString(EXTRA_LIVE_UPDATE_IDENTITY) },
                identity, SUPER_ISLAND_ID, SUPER_ISLAND_ALTERNATE_ID
            )
        } else {
            liveUpdateNotificationSlot(
                active.filter { it.id == LIVE_UPDATE_ID || it.id == LIVE_UPDATE_ALTERNATE_ID }
                    .map { it.id to it.notification.extras.getString(EXTRA_LIVE_UPDATE_IDENTITY) },
                identity, LIVE_UPDATE_ID, LIVE_UPDATE_ALTERNATE_ID
            )
        }
        logLiveUpdateIcon(context, notification)
        // Post first. Reattach the running foreground service before removing its former slot.
        val delivered = XiaomiSuperIsland.post(context, notification) {
            manager.notify(id, notification)
            attachForeground?.invoke(id, notification)
        }
        (if (preview) listOf(SUPER_ISLAND_TEST_ID, SUPER_ISLAND_TEST_ALTERNATE_ID)
            else listOf(LIVE_UPDATE_ID, LIVE_UPDATE_ALTERNATE_ID, SUPER_ISLAND_ID, SUPER_ISLAND_ALTERNATE_ID))
            .filter { it != id }.forEach(manager::cancel)
        delivered
    }

    internal fun cancelLiveUpdateNotifications(context: Context, includePreview: Boolean = true) = synchronized(liveUpdatePostLock) {
        val manager = NotificationManagerCompat.from(context)
        manager.cancel(LIVE_UPDATE_ID)
        manager.cancel(LIVE_UPDATE_ALTERNATE_ID)
        manager.cancel(SUPER_ISLAND_ID)
        manager.cancel(SUPER_ISLAND_ALTERNATE_ID)
        if (includePreview) {
            XiaomiSuperIsland.clearPreview(context)
            manager.cancel(SUPER_ISLAND_TEST_ID)
            manager.cancel(SUPER_ISLAND_TEST_ALTERNATE_ID)
        }
    }

    internal fun logLiveUpdateIcon(context: Context, notification: Notification) {
        val icon = notification.smallIcon
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            Log.d(TAG, "smallIcon=$icon, appPackage=${context.packageName}")
            return
        }
        val isResource = icon?.type == Icon.TYPE_RESOURCE
        val resId = if (isResource) icon?.resId else null
        val resourcePackage = if (isResource) icon?.resPackage else null
        val resource = resId?.let { runCatching { context.resources.getResourceName(it) }.getOrNull() }
        Log.d(TAG, "smallIcon type=${icon?.type}, resId=$resId, resPackage=$resourcePackage, " +
            "resource=$resource, appPackage=${context.packageName}")
    }

    internal fun canPostNotifications(context: Context): Boolean {
        val runtimePermissionGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!runtimePermissionGranted || !NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            return false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = context.getSystemService(NotificationManager::class.java)
                ?.getNotificationChannel(
                    if (XiaomiSuperIsland.isEnabled(context)) XiaomiSuperIsland.ChannelId else CHANNEL_ID
                )
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
        requestRefresh(context, forceReschedule = true)
    }

    fun reopenCurrentIsland(context: Context) {
        if (XiaomiSuperIsland.isEnabled(context)) cancelLiveUpdateNotifications(context)
        requestRefresh(context)
    }

    fun requestRefresh(context: Context, forceReschedule: Boolean = false, onComplete: () -> Unit = {}) {
        val app = context.applicationContext as CourseScheduleApp
        // Acquire before the receiver returns; goAsync alone does not keep the CPU awake.
        app.applicationScope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                withShortWakeLock(app, "live_update_refresh") {
                    withContext(Dispatchers.IO) {
                        if (forceReschedule) app.repository.ensureDefaults()
                        val snapshot = app.repository.activeSnapshot()
                        refreshToday(app, snapshot.courses, snapshot.config, snapshot.periods, forceReschedule)
                    }
                }
            } finally {
                onComplete()
            }
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
