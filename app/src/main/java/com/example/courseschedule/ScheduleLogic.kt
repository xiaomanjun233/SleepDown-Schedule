package com.example.courseschedule

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.max

@Serializable
data class ScheduleImportPayload(
    val schemaVersion: Int,
    val scheduleConfig: ScheduleConfigPayload,
    val courses: List<ScheduleImportCourse>
)

@Serializable
data class ScheduleConfigPayload(val totalWeeks: Int, val periods: List<PeriodPayload>)

@Serializable
data class PeriodPayload(val index: Int, val startTime: String, val endTime: String)

@Serializable
data class ScheduleImportCourse(
    val name: String,
    val teacher: String? = null,
    val location: String? = null,
    val weekday: Int,
    val periods: List<Int>,
    val weeks: List<Int>,
    val weekParity: WeekParityPayload = WeekParityPayload.ALL,
    val note: String? = null
)

@Serializable
enum class WeekParityPayload {
    @SerialName("ALL") ALL,
    @SerialName("ODD") ODD,
    @SerialName("EVEN") EVEN
}

object SchedulePromptBuilder {
    fun build(): String = """
请把我的课程表整理成严格 JSON，不要输出解释文字。
JSON 协议如下：
{
  "schemaVersion": 1,
  "scheduleConfig": {
    "totalWeeks": 20,
    "periods": [
      {"index": 1, "startTime": "08:00", "endTime": "08:45"},
      {"index": 2, "startTime": "08:55", "endTime": "09:40"}
    ]
  },
  "courses": [
    {
      "name": "高等数学",
      "teacher": "张老师",
      "location": "A101",
      "weekday": 1,
      "periods": [1, 2],
      "weeks": [1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16],
      "weekParity": "ALL",
      "note": ""
    }
  ]
}
要求：weekday 使用 1-7 表示周一到周日；periods 必须引用 scheduleConfig.periods 里的 index；weeks 必须在 1 到 totalWeeks 内；weekParity 只能是 ALL、ODD、EVEN；时间必须是 HH:mm。只返回 JSON。
""".trimIndent()

    fun buildTokenPrompt(): String = """
请读取我发给你的课表 PDF，并只输出 SleepDown 课程表口令，不要解释、不要 Markdown。
口令格式：
SDCT1
T=总周数
P=节次,开始时间-结束时间;节次,开始时间-结束时间
C=课程名|教师|地点|星期|节次|周次|单双周|备注

规则：
1. 星期用 1-7 表示周一到周日。
2. 节次用逗号或范围，例如 1,2 或 1-2。
3. 周次用逗号或范围，例如 1-16 或 1-8,10,12-16。
4. 单双周用 A/O/E，分别表示全部/单周/双周。
5. 没有教师、地点或备注时写 -。
6. 时间必须是 HH:mm。
示例：
SDCT1
T=20
P=1,08:00-08:45;2,08:55-09:40;3,10:00-10:45
C=高等数学|张老师|A101|1|1-2|1-16|A|-
C=大学英语|-|B203|3|3|2-18|O|-
""".trimIndent()
}

object ScheduleImportParser {
    private val json = Json { ignoreUnknownKeys = false; isLenient = false }
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun cleanMarkdown(input: String): String {
        val trimmed = input.trim()
        val fence = Char(96).toString().repeat(3)
        val firstFence = trimmed.indexOf(fence)
        if (firstFence < 0) return trimmed
        val afterFence = trimmed.indexOf('\n', firstFence).let { if (it < 0) firstFence + fence.length else it + 1 }
        val lastFence = trimmed.indexOf(fence, afterFence)
        if (lastFence < 0) return trimmed.removePrefix(fence).removePrefix("json").trim()
        return trimmed.substring(afterFence, lastFence).trim()
    }

    fun parse(input: String, baseConfig: ScheduleConfigEntity): Result<ImportDraft> = runCatching {
        val cleaned = cleanMarkdown(input)
        val payload = if (containsSleepDownToken(cleaned)) {
            parseSleepDownToken(cleaned)
        } else {
            json.decodeFromString(cleaned)
        }
        validatePayload(payload, baseConfig)
    }

    fun parseTimeForUi(value: String): LocalTime = LocalTime.parse(value, timeFormatter)

    private fun validatePayload(payload: ScheduleImportPayload, baseConfig: ScheduleConfigEntity): ImportDraft {
        require(payload.schemaVersion == 1) { "schemaVersion 目前只支持 1" }
        require(payload.scheduleConfig.totalWeeks in 1..60) { "totalWeeks 必须在 1 到 60 之间" }
        require(payload.scheduleConfig.periods.isNotEmpty()) { "periods 不能为空" }
        val indexes = payload.scheduleConfig.periods.map { it.index }
        require(indexes.distinct().size == indexes.size) { "节次 index 不能重复" }
        require(indexes.all { it > 0 }) { "节次 index 必须大于 0" }
        val periods = payload.scheduleConfig.periods.sortedBy { it.index }.map {
            val start = parseTime(it.startTime, "第 ${it.index} 节 startTime")
            val end = parseTime(it.endTime, "第 ${it.index} 节 endTime")
            require(start < end) { "第 ${it.index} 节结束时间必须晚于开始时间" }
            PeriodEntity(it.index, it.startTime, it.endTime)
        }
        val validPeriodIndexes = indexes.toSet()
        val courses = payload.courses.mapIndexed { position, course ->
            val row = "第 ${position + 1} 门课程"
            require(course.name.isNotBlank()) { "$row name 不能为空" }
            require(course.weekday in 1..7) { "$row weekday 必须在 1 到 7 之间" }
            require(course.periods.isNotEmpty()) { "$row periods 不能为空" }
            require(course.periods.all { it in validPeriodIndexes }) { "$row 引用了不存在的节次" }
            require(course.weeks.isNotEmpty()) { "$row weeks 不能为空" }
            require(course.weeks.all { it in 1..payload.scheduleConfig.totalWeeks }) { "$row weeks 超出 totalWeeks" }
            CourseEntity(
                name = course.name.trim(),
                teacher = course.teacher?.trim()?.ifBlank { null },
                location = course.location?.trim()?.ifBlank { null },
                weekday = course.weekday,
                periods = course.periods.distinct().sorted(),
                weeks = course.weeks.distinct().sorted(),
                weekParity = when (course.weekParity) {
                    WeekParityPayload.ALL -> WeekParity.ALL
                    WeekParityPayload.ODD -> WeekParity.ODD
                    WeekParityPayload.EVEN -> WeekParity.EVEN
                },
                note = course.note?.trim()?.ifBlank { null }
            )
        }
        return ImportDraft(
            config = baseConfig.copy(
                totalWeeks = payload.scheduleConfig.totalWeeks,
                currentWeek = effectiveCurrentWeek(baseConfig).coerceIn(1, payload.scheduleConfig.totalWeeks)
            ),
            periods = periods,
            courses = courses
        )
    }

    private fun parseTime(value: String, label: String): LocalTime = try {
        LocalTime.parse(value, timeFormatter)
    } catch (error: Exception) {
        throw IllegalArgumentException("$label 必须是 HH:mm")
    }

    private fun containsSleepDownToken(input: String): Boolean {
        return input.lineSequence().any { it.trim() == "SDCT1" }
    }

    private fun parseSleepDownToken(input: String): ScheduleImportPayload {
        val lines = input.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val startIndex = lines.indexOfFirst { it == "SDCT1" }
        require(startIndex >= 0) { "未找到 SleepDown 课程表口令 SDCT1" }
        var totalWeeks = 20
        val periods = mutableListOf<PeriodPayload>()
        val courses = mutableListOf<ScheduleImportCourse>()
        lines.drop(startIndex + 1).forEach { line ->
            when {
                line.startsWith("T=") -> totalWeeks = line.removePrefix("T=").trim().toInt()
                line.startsWith("P=") -> {
                    line.removePrefix("P=").split(';').map { it.trim() }.filter { it.isNotEmpty() }.forEach { item ->
                        val parts = item.split(',', limit = 2)
                        require(parts.size == 2) { "节次口令格式应为 节次,开始-结束" }
                        val times = parts[1].split('-', limit = 2)
                        require(times.size == 2) { "节次时间格式应为 HH:mm-HH:mm" }
                        periods += PeriodPayload(parts[0].trim().toInt(), times[0].trim(), times[1].trim())
                    }
                }
                line.startsWith("C=") -> {
                    val fields = line.removePrefix("C=").split('|')
                    require(fields.size >= 7) { "课程口令至少需要 7 个字段" }
                    courses += ScheduleImportCourse(
                        name = fields[0].trim(),
                        teacher = tokenText(fields.getOrNull(1)),
                        location = tokenText(fields.getOrNull(2)),
                        weekday = fields[3].trim().toInt(),
                        periods = parseNumberRanges(fields[4]),
                        weeks = parseNumberRanges(fields[5]),
                        weekParity = when (fields[6].trim().uppercase()) {
                            "O", "ODD" -> WeekParityPayload.ODD
                            "E", "EVEN" -> WeekParityPayload.EVEN
                            else -> WeekParityPayload.ALL
                        },
                        note = tokenText(fields.getOrNull(7))
                    )
                }
            }
        }
        require(periods.isNotEmpty()) { "口令缺少 P= 节次定义" }
        require(courses.isNotEmpty()) { "口令缺少 C= 课程定义" }
        return ScheduleImportPayload(1, ScheduleConfigPayload(totalWeeks, periods), courses)
    }

    private fun tokenText(value: String?): String? {
        val text = value?.trim().orEmpty()
        return text.takeUnless { it.isBlank() || it == "-" }
    }

    private fun parseNumberRanges(value: String): List<Int> {
        return value.replace('，', ',')
            .split(',')
            .flatMap { part ->
                val token = part.trim()
                if (token.contains('-')) {
                    val bounds = token.split('-', limit = 2).map { it.trim().toInt() }
                    val start = minOf(bounds[0], bounds[1])
                    val end = maxOf(bounds[0], bounds[1])
                    (start..end).toList()
                } else {
                    listOf(token.toInt())
                }
            }
            .distinct()
            .sorted()
    }
}

fun todayCourses(state: AppState): List<CourseEntity> {
    val weekday = LocalDate.now(ZoneId.of("Asia/Shanghai")).dayOfWeek.toChineseWeekday()
    val currentWeek = effectiveCurrentWeek(state.config)
    return state.courses.filter { it.weekday == weekday && it.weeks.contains(currentWeek) && parityMatches(it.weekParity, currentWeek) }
        .sortedBy { courseStartTime(it, state.periods) ?: LocalTime.MAX }
}

fun effectiveCurrentWeek(config: ScheduleConfigEntity, today: LocalDate = LocalDate.now(ZoneId.of("Asia/Shanghai"))): Int {
    if (!config.autoCurrentWeek || config.termStartDate.isNullOrBlank()) return config.currentWeek.coerceIn(1, config.totalWeeks)
    val startDate = parseScheduleDate(config.termStartDate) ?: return config.currentWeek.coerceIn(1, config.totalWeeks)
    val start = startDate.minusDays((startDate.dayOfWeek.toChineseWeekday() - 1).toLong())
    val days = ChronoUnit.DAYS.between(start, today)
    val calculated = (Math.floorDiv(days, 7) + 1).toInt()
    return calculated.coerceIn(1, config.totalWeeks)
}

fun parseScheduleDate(value: String?): LocalDate? {
    val text = value?.trim().orEmpty()
    if (text.isBlank()) return null
    val normalized = text.replace('.', '-').replace('/', '-')
    return runCatching { LocalDate.parse(normalized) }.getOrNull()
}

fun formatScheduleDate(date: LocalDate): String {
    return DateTimeFormatter.ofPattern("yyyy.MM.dd").format(date)
}

fun DayOfWeek.toChineseWeekday(): Int = when (this) {
    DayOfWeek.MONDAY -> 1
    DayOfWeek.TUESDAY -> 2
    DayOfWeek.WEDNESDAY -> 3
    DayOfWeek.THURSDAY -> 4
    DayOfWeek.FRIDAY -> 5
    DayOfWeek.SATURDAY -> 6
    DayOfWeek.SUNDAY -> 7
}

fun parityMatches(parity: WeekParity, week: Int): Boolean = when (parity) {
    WeekParity.ALL -> true
    WeekParity.ODD -> week % 2 == 1
    WeekParity.EVEN -> week % 2 == 0
}

fun courseStartTime(course: CourseEntity, periods: List<PeriodEntity>): LocalTime? {
    val first = course.periods.minOrNull() ?: return null
    return periods.firstOrNull { it.periodIndex == first }?.startTime?.let { LocalTime.parse(it) }
}

fun courseEndTime(course: CourseEntity, periods: List<PeriodEntity>): LocalTime? {
    val last = course.periods.maxOrNull() ?: return null
    return periods.firstOrNull { it.periodIndex == last }?.endTime?.let { LocalTime.parse(it) }
}

fun courseTimeLabel(course: CourseEntity, periods: List<PeriodEntity>): String {
    val first = course.periods.minOrNull()
    val last = course.periods.maxOrNull()
    val start = periods.firstOrNull { it.periodIndex == first }?.startTime ?: "--:--"
    val end = periods.firstOrNull { it.periodIndex == last }?.endTime ?: "--:--"
    return start + " - " + end
}

fun parityLabel(parity: WeekParity): String = when (parity) {
    WeekParity.ALL -> "每周"
    WeekParity.ODD -> "单周"
    WeekParity.EVEN -> "双周"
}

object NotificationScheduler {
    private const val TAG = "SleepDownLiveUpdate"
    private const val CHANNEL_ID = "course_reminders"
    private const val PREFS = "course_alarm_prefs"
    private const val KEY_REQUEST_CODES = "request_codes"
    private const val KEY_SCHEDULE_SIGNATURE = "schedule_signature"
    private const val KEY_MUTED_COURSE = "muted_course"
    private const val KEY_MUTED_UNTIL = "muted_until"
    private const val KEY_DND_ENABLED_BY_APP = "dnd_enabled_by_app"
    private const val LIVE_UPDATE_ID = 20260522
    const val ACTION_CANCEL_LIVE_UPDATE = "com.example.courseschedule.action.CANCEL_LIVE_UPDATE"
    const val ACTION_TOGGLE_DND = "com.example.courseschedule.action.TOGGLE_DND"
    const val ACTION_START_LIVE_UPDATE_SERVICE = "com.example.courseschedule.action.START_LIVE_UPDATE_SERVICE"
    const val ACTION_STOP_LIVE_UPDATE_SERVICE = "com.example.courseschedule.action.STOP_LIVE_UPDATE_SERVICE"
    private const val EXTRA_LIVE_UPDATE_NOTIFICATION = "live_update_notification"

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
        if (prefs.getString(KEY_SCHEDULE_SIGNATURE, null) != signature) {
            scheduleToday(context, courses, config, periods)
            prefs.edit().putString(KEY_SCHEDULE_SIGNATURE, signature).apply()
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
        val scheduledCodes = mutableListOf<Int>()
        today.forEach { course ->
            val start = courseStartTime(course, periods) ?: return@forEach
            val trigger = LocalDate.now(ZoneId.of("Asia/Shanghai")).atTime(start).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() -
                config.notificationLeadMinutes.coerceAtLeast(0) * 60_000L
            if (trigger > now) {
                val retryTriggers = if (config.notificationMode == NotificationMode.LIVE_UPDATE) {
                    listOf(trigger, trigger + 60_000L, trigger + 3 * 60_000L, trigger + 5 * 60_000L)
                } else {
                    listOf(trigger)
                }
                retryTriggers.forEachIndexed { index, retryTrigger ->
                    val courseEnd = courseEndTime(course, periods) ?: start
                    val retryEnd = LocalDate.now(ZoneId.of("Asia/Shanghai")).atTime(courseEnd).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    if (retryTrigger > now && retryTrigger < retryEnd) {
                        val requestCode = course.requestCode(index)
                        scheduleAlarm(alarmManager, retryTrigger, pendingIntent(context, course, config, periods, requestCode))
                        scheduledCodes += requestCode
                    }
                }
            }
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_REQUEST_CODES, scheduledCodes.joinToString(",")).apply()
    }

    private fun scheduleSignature(courses: List<CourseEntity>, config: ScheduleConfigEntity, periods: List<PeriodEntity>): String {
        val today = LocalDate.now(ZoneId.of("Asia/Shanghai"))
        val coursePart = courses
            .sortedBy { it.id }
            .joinToString(";") {
                listOf(
                    it.id,
                    it.name,
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
        if (!config.notificationsEnabled || config.notificationMode != NotificationMode.LIVE_UPDATE) {
            Log.d(TAG, "skip immediate live update: disabled or mode=${config.notificationMode}")
            NotificationManagerCompat.from(context).cancel(LIVE_UPDATE_ID)
            stopLiveUpdateService(context)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "skip immediate live update: POST_NOTIFICATIONS denied")
            NotificationManagerCompat.from(context).cancel(LIVE_UPDATE_ID)
            stopLiveUpdateService(context)
            return
        }
        val now = LocalTime.now(ZoneId.of("Asia/Shanghai"))
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
        val notification = liveUpdateNotification(context, active.name, courseTimeLabel(active, periods), active.location.orEmpty(), config.liveUpdateActionsEnabled, active.muteKey(), activeEnd.toString(), config.liveUpdateChipTextMode)
        startLiveUpdateService(context, notification)
    }

    private fun cancelPreviouslyScheduled(context: Context, alarmManager: AlarmManager) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val codes = prefs.getString(KEY_REQUEST_CODES, "").orEmpty().split(",").mapNotNull { it.toIntOrNull() }
        codes.forEach { alarmManager.cancel(emptyPendingIntent(context, it)) }
        prefs.edit().remove(KEY_REQUEST_CODES).apply()
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "课程提醒", NotificationManager.IMPORTANCE_DEFAULT)
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    fun channelId(): String = CHANNEL_ID

    fun liveUpdateId(): Int = LIVE_UPDATE_ID

    fun showLiveUpdatePreview(context: Context) {
        createChannel(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        NotificationManagerCompat.from(context).notify(
            20260521,
            buildLiveUpdateNotification(context, "课程提醒", "21:30 - 22:15", "教学楼 A101", 10, true, "preview", "22:15", LiveUpdateChipTextMode.LOCATION)
        )
    }

    fun liveUpdateNotification(context: Context, name: String, timeText: String, location: String, showActions: Boolean, muteKey: String, muteUntil: String, chipTextMode: LiveUpdateChipTextMode): android.app.Notification {
        return buildLiveUpdateNotification(context, name, timeText, location, minutesUntil(timeText), showActions, muteKey, muteUntil, chipTextMode)
    }

    private fun buildLiveUpdateNotification(context: Context, name: String, timeText: String, location: String, minutesLeft: Int, showActions: Boolean, muteKey: String, muteUntil: String, chipTextMode: LiveUpdateChipTextMode): android.app.Notification {
        val placeText = location.ifBlank { "未设置地点" }
        val countdownText = if (minutesLeft <= 0) "准备上课" else "还剩${minutesLeft}分钟"
        val shortText = liveUpdateChipText(chipTextMode, placeText, countdownText, minutesLeft)
        val useShortChipText = chipTextMode == LiveUpdateChipTextMode.SHORT
        val titleText = if (useShortChipText) "课程提醒" else name
        val bodyText = if (useShortChipText) "$name · $timeText" else "$countdownText · $timeText"
        val expandedText = if (location.isBlank()) bodyText else "$bodyText\n$placeText"
        val openAppIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val contentIntent = PendingIntent.getActivity(
            context,
            20260522,
            openAppIntent ?: Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            android.app.Notification.Builder(context, CHANNEL_ID)
        } else {
            android.app.Notification.Builder(context)
        }
        builder
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(titleText)
            .setContentText(bodyText)
            .setStyle(android.app.Notification.BigTextStyle().bigText(expandedText))
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(android.app.Notification.CATEGORY_EVENT)
            .setPriority(android.app.Notification.PRIORITY_HIGH)
            .setColor(0xFF0A84FF.toInt())
        if (showActions) {
            val dndEnabled = isDoNotDisturbEnabledByApp(context)
            val dndTitle = if (dndEnabled) "关闭勿扰" else "开启勿扰"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                builder
                    .addAction(android.app.Notification.Action.Builder(
                        Icon.createWithResource(context, R.drawable.ic_close_light),
                        "取消本次提醒",
                        actionPendingIntent(context, ACTION_CANCEL_LIVE_UPDATE, 1, muteKey, muteUntil)
                    ).build())
                    .addAction(android.app.Notification.Action.Builder(
                        Icon.createWithResource(context, R.drawable.ic_moon_light),
                        dndTitle,
                        actionPendingIntent(context, ACTION_TOGGLE_DND, 2, muteKey, muteUntil)
                    ).build())
            } else {
                builder
                    .addAction(R.drawable.ic_close_light, "取消本次提醒", actionPendingIntent(context, ACTION_CANCEL_LIVE_UPDATE, 1, muteKey, muteUntil))
                    .addAction(R.drawable.ic_moon_light, dndTitle, actionPendingIntent(context, ACTION_TOGGLE_DND, 2, muteKey, muteUntil))
            }
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
        runCatching {
            builder.javaClass
                .getMethod("setShortCriticalText", String::class.java)
                .invoke(builder, shortText)
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

    private fun liveUpdateChipText(mode: LiveUpdateChipTextMode, placeText: String, countdownText: String, minutesLeft: Int): String {
        val shortLabel = if (minutesLeft <= 0) "上课中" else "快上课"
        return when (mode) {
            LiveUpdateChipTextMode.SHORT -> shortLabel
            LiveUpdateChipTextMode.COUNTDOWN -> countdownText
            LiveUpdateChipTextMode.LOCATION,
            LiveUpdateChipTextMode.NORMAL -> placeText
        }
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

    private fun CourseEntity.muteKey(): String = "$id:$name:${weekday}:${periods.joinToString(",")}:${weeks.joinToString(",")}"

    private fun isMutedForCurrentCourse(context: Context, course: CourseEntity, endTime: LocalTime): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val key = prefs.getString(KEY_MUTED_COURSE, null) ?: return false
        val until = prefs.getString(KEY_MUTED_UNTIL, null)?.let { runCatching { LocalTime.parse(it) }.getOrNull() } ?: return false
        val now = LocalTime.now()
        if (!now.isBefore(until)) {
            prefs.edit().remove(KEY_MUTED_COURSE).remove(KEY_MUTED_UNTIL).apply()
            return false
        }
        return key == course.muteKey() && until == endTime
    }

    fun cancelCurrentLiveUpdate(context: Context, muteKey: String?, muteUntil: String?) {
        if (!muteKey.isNullOrBlank() && !muteUntil.isNullOrBlank()) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY_MUTED_COURSE, muteKey)
                .putString(KEY_MUTED_UNTIL, muteUntil)
                .apply()
        }
        NotificationManagerCompat.from(context).cancel(LIVE_UPDATE_ID)
        stopLiveUpdateService(context)
    }

    fun toggleDoNotDisturb(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && manager?.isNotificationPolicyAccessGranted == true) {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            if (prefs.getBoolean(KEY_DND_ENABLED_BY_APP, false)) {
                manager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
                prefs.edit().putBoolean(KEY_DND_ENABLED_BY_APP, false).apply()
            } else {
                manager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
                prefs.edit().putBoolean(KEY_DND_ENABLED_BY_APP, true).apply()
            }
            refreshVisibleLiveUpdate(context)
        } else {
            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    private fun isDoNotDisturbEnabledByApp(context: Context): Boolean {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_DND_ENABLED_BY_APP, false)
    }

    private fun refreshVisibleLiveUpdate(context: Context) {
        val app = context.applicationContext as? CourseScheduleApp ?: return
        CoroutineScope(Dispatchers.IO).launch {
            val snapshot = app.repository.snapshot()
            checkImmediateLiveUpdate(context, snapshot.courses, snapshot.config, snapshot.periods)
        }
    }

    fun startLiveUpdateService(context: Context, notification: android.app.Notification) {
        val intent = Intent(context, LiveUpdateForegroundService::class.java)
            .setAction(ACTION_START_LIVE_UPDATE_SERVICE)
            .putExtra(EXTRA_LIVE_UPDATE_NOTIFICATION, notification)
        runCatching {
            ContextCompat.startForegroundService(context, intent)
            Log.d(TAG, "startForegroundService requested")
        }.onFailure {
            Log.w(TAG, "startForegroundService failed, fallback notify: ${it.javaClass.simpleName}: ${it.message}")
            NotificationManagerCompat.from(context).notify(LIVE_UPDATE_ID, notification)
        }
    }

    fun stopLiveUpdateService(context: Context) {
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

class LiveUpdateForegroundService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            NotificationScheduler.ACTION_STOP_LIVE_UPDATE_SERVICE -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            else -> {
                NotificationScheduler.createChannel(this)
                val notification = NotificationScheduler.notificationFromIntent(intent ?: Intent())
                if (notification != null) {
                    startForeground(NotificationScheduler.liveUpdateId(), notification)
                    Log.d("SleepDownLiveUpdate", "foreground service started")
                } else {
                    Log.w("SleepDownLiveUpdate", "foreground service missing notification")
                    stopSelf()
                }
            }
        }
        return START_NOT_STICKY
    }
}

class CourseAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        NotificationScheduler.withShortWakeLock(context, "course_alarm") {
            NotificationScheduler.createChannel(context)
            val name = intent.getStringExtra("courseName") ?: "课程"
            val location = intent.getStringExtra("location").orEmpty()
            val timeText = intent.getStringExtra("timeText").orEmpty()
            val mode = runCatching { NotificationMode.valueOf(intent.getStringExtra("notificationMode") ?: NotificationMode.STANDARD.name) }.getOrDefault(NotificationMode.STANDARD)
            val showActions = intent.getBooleanExtra("liveUpdateActionsEnabled", true)
            val chipTextMode = runCatching { LiveUpdateChipTextMode.valueOf(intent.getStringExtra("liveUpdateChipTextMode") ?: LiveUpdateChipTextMode.LOCATION.name) }.getOrDefault(LiveUpdateChipTextMode.LOCATION)
            val muteKey = intent.getStringExtra("muteKey").orEmpty()
            val muteUntil = intent.getStringExtra("muteUntil").orEmpty()
            val startTime = runCatching { LocalTime.parse(timeText.substringBefore("-").trim()) }.getOrNull()
            if (mode == NotificationMode.LIVE_UPDATE && startTime != null && !LocalTime.now(ZoneId.of("Asia/Shanghai")).isBefore(startTime)) {
                Log.d("SleepDownLiveUpdate", "skip alarm live update: course already started name=$name, start=$startTime")
                return@withShortWakeLock
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return@withShortWakeLock
            val notification = if (mode == NotificationMode.LIVE_UPDATE) {
                NotificationScheduler.liveUpdateNotification(context, name, timeText, location, showActions, muteKey, muteUntil, chipTextMode)
            } else {
                NotificationCompat.Builder(context, NotificationScheduler.channelId())
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentTitle("快上课了：$name")
                    .setContentText(if (location.isBlank()) "请准备上课" else "地点：$location")
                    .setAutoCancel(true)
                    .build()
            }
            if (mode == NotificationMode.LIVE_UPDATE) {
                Log.d("SleepDownLiveUpdate", "alarm receiver live update: course=$name, chip=$chipTextMode")
                NotificationScheduler.startLiveUpdateService(context, notification)
            } else {
                val notificationId = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
                NotificationManagerCompat.from(context).notify(notificationId, notification)
            }
        }
    }
}

class LiveUpdateActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            NotificationScheduler.ACTION_CANCEL_LIVE_UPDATE -> {
                NotificationScheduler.cancelCurrentLiveUpdate(
                    context,
                    intent.getStringExtra("muteKey"),
                    intent.getStringExtra("muteUntil")
                )
            }
            NotificationScheduler.ACTION_TOGGLE_DND -> {
                NotificationScheduler.toggleDoNotDisturb(context)
            }
        }
    }
}

class CourseBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as CourseScheduleApp
                app.repository.ensureDefaults()
                val snapshot = app.repository.snapshot()
                NotificationScheduler.refreshToday(context, snapshot.courses, snapshot.config, snapshot.periods)
            } finally {
                pending.finish()
            }
        }
    }
}
