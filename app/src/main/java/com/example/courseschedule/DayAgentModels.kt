package com.example.courseschedule

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

@Serializable
enum class AgentTemplateKind {
    MORNING_OVERVIEW,
    BEFORE_NEXT_CLASS,
    DURING_CLASS,
    SHORT_BREAK,
    LONG_BREAK,
    AFTER_LAST_CLASS,
    TOMORROW_PREVIEW,
    NO_CLASS_TODAY,
    NO_CLASS_TOMORROW,
    WEATHER_ALERT
}

@Serializable
data class DailyAgentPack(
    val generatedAt: Long = 0L,
    val providerId: String = "local",
    val model: String = "local",
    val sourceHash: String = "",
    val templates: Map<String, String> = defaultAgentTemplates(),
    val generationStatus: String = "LOCAL",
    val lastError: String? = null
) {
    fun encode(): String = AgentJson.encodeToString(this)

    companion object {
        fun decodeOrDefault(value: String?): DailyAgentPack = runCatching {
            AgentJson.decodeFromString<DailyAgentPack>(value.orEmpty())
        }.getOrElse { DailyAgentPack() }
    }
}

data class AgentWeatherSnapshot(
    val summary: String,
    val temperature: Int,
    val apparentTemperature: Int,
    val precipitationProbability: Int,
    val windSpeed: Int,
    val fetchedAt: Long
) {
    val rainAdvice: String
        get() = if (precipitationProbability >= 45) "建议带伞" else "暂时不用带伞"

    val hasAlert: Boolean
        get() = precipitationProbability >= 60 || temperature >= 35 || temperature <= 5 || windSpeed >= 35
}

data class AgentCourseSlot(
    val course: CourseEntity,
    val date: LocalDate,
    val start: LocalTime,
    val end: LocalTime
)

data class DayAgentFacts(
    val date: LocalDate,
    val now: LocalDateTime,
    val today: List<AgentCourseSlot>,
    val tomorrow: List<AgentCourseSlot>,
    val week: List<AgentCourseSlot>,
    val weather: AgentWeatherSnapshot?,
    val sourceHash: String,
    val periodDefinitions: List<PeriodEntity> = emptyList(),
    val totalWeeks: Int = 20,
    val scheduleId: Int = 1,
    val currentWeek: Int = 1
)

@Serializable
data class AgentCourseDraft(
    val name: String,
    val teacher: String? = null,
    val location: String? = null,
    val weekday: Int,
    val periods: List<Int>,
    val weeks: List<Int>,
    val weekParity: String = "ALL",
    val note: String? = null
)

data class ParsedAgentCourseDraft(
    val displayText: String,
    val course: CourseEntity?
)

data class RenderedAgentMessage(
    val kind: AgentTemplateKind,
    val text: String,
    val compactText: String,
    val quickQuestions: List<String>
)

private val AgentJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

val AgentAllowedPlaceholders = setOf(
    "todayCourseCount",
    "currentCourseName",
    "currentCourseEnd",
    "nextCourseName",
    "nextCourseLocation",
    "nextCourseStart",
    "timeUntilNext",
    "gapDuration",
    "lastCourseEnd",
    "weatherSummary",
    "temperature",
    "apparentTemperature",
    "rainAdvice",
    "tomorrowCourseCount",
    "tomorrowFirstCourse",
    "tomorrowCourseSummary"
)

fun defaultAgentTemplates(): Map<String, String> = mapOf(
    AgentTemplateKind.MORNING_OVERVIEW.name to "早上好，今天有 {{todayCourseCount}} 门课。{{weatherSummary}} 第一节是 {{nextCourseName}}，{{nextCourseStart}} 在 {{nextCourseLocation}}。",
    AgentTemplateKind.BEFORE_NEXT_CLASS.name to "距 {{nextCourseName}} 还有 {{timeUntilNext}}，上课地点是 {{nextCourseLocation}}。{{rainAdvice}}。",
    AgentTemplateKind.DURING_CLASS.name to "现在是 {{currentCourseName}}，预计 {{currentCourseEnd}} 下课。下一段安排我会继续帮你留意。",
    AgentTemplateKind.SHORT_BREAK.name to "课间还有 {{gapDuration}}，下一节是 {{nextCourseName}}，{{nextCourseStart}} 开始。",
    AgentTemplateKind.LONG_BREAK.name to "现在有 {{gapDuration}} 空档，下一节 {{nextCourseName}} 在 {{nextCourseStart}}，可以安排吃饭、休息或复习。",
    AgentTemplateKind.AFTER_LAST_CLASS.name to "今天的课程已经结束，最后一节在 {{lastCourseEnd}} 下课。明天有 {{tomorrowCourseCount}} 门课。",
    AgentTemplateKind.TOMORROW_PREVIEW.name to "明天有 {{tomorrowCourseCount}} 门课，第一节是 {{tomorrowFirstCourse}}。{{tomorrowCourseSummary}}",
    AgentTemplateKind.NO_CLASS_TODAY.name to "今天没有课程。{{weatherSummary}} 可以按自己的节奏安排学习和休息。",
    AgentTemplateKind.NO_CLASS_TOMORROW.name to "明天没有课程，今晚可以放心整理今天的内容。",
    AgentTemplateKind.WEATHER_ALERT.name to "天气提醒：{{weatherSummary}}，{{rainAdvice}}。"
)

fun buildDayAgentFacts(
    courses: List<CourseEntity>,
    periods: List<PeriodEntity>,
    config: ScheduleConfigEntity,
    date: LocalDate,
    weather: AgentWeatherSnapshot?,
    now: LocalDateTime = LocalDateTime.now(ZoneId.of("Asia/Shanghai"))
): DayAgentFacts {
    val periodMap = periods.associateBy { it.periodIndex }
    val currentWeek = effectiveCurrentWeek(config, date)
    fun slotsFor(targetDate: LocalDate): List<AgentCourseSlot> {
        val week = effectiveCurrentWeek(config, targetDate)
        val weekday = targetDate.dayOfWeek.value
        return courses.asSequence()
            .filter { it.weekday == weekday && week in it.weeks && parityMatches(it.weekParity, week) }
            .mapNotNull { course ->
                val first = course.periods.minOrNull()?.let(periodMap::get)
                val last = course.periods.maxOrNull()?.let(periodMap::get)
                val start = first?.startTime?.let(::parseAgentTime)
                val end = last?.endTime?.let(::parseAgentTime)
                if (start == null || end == null) null else AgentCourseSlot(course, targetDate, start, end)
            }
            .sortedBy { it.start }
            .toList()
    }

    val today = slotsFor(date)
    val tomorrow = slotsFor(date.plusDays(1))
    val weekStart = date.minusDays((date.dayOfWeek.value - 1).toLong())
    val week = (0L..6L).flatMap { offset -> slotsFor(weekStart.plusDays(offset)) }
    val source = buildString {
        append(config.id).append('|').append(date).append('|').append(currentWeek).append('|')
        week.forEach { slot ->
            append(slot.course.id).append(':').append(slot.course.name).append(':')
            append(slot.start).append('-').append(slot.end).append(':')
            append(slot.course.location.orEmpty()).append('|')
        }
    }
    return DayAgentFacts(
        date = date,
        now = now,
        today = today,
        tomorrow = tomorrow,
        week = week,
        weather = weather,
        sourceHash = source.sha256(),
        periodDefinitions = periods.sortedBy { it.periodIndex },
        totalWeeks = config.totalWeeks,
        scheduleId = config.id,
        currentWeek = currentWeek
    )
}

object TodayAgentTimelineEngine {
    fun render(pack: DailyAgentPack, facts: DayAgentFacts): RenderedAgentMessage {
        val now = facts.now.toLocalTime()
        val today = facts.today
        val current = today.firstOrNull { !now.isBefore(it.start) && now.isBefore(it.end) }
        val next = today.firstOrNull { now.isBefore(it.start) }
        val previous = today.lastOrNull { !now.isBefore(it.end) }
        val first = today.firstOrNull()
        val last = today.lastOrNull()

        val kind = when {
            today.isEmpty() && now >= LocalTime.of(18, 0) && facts.tomorrow.isEmpty() -> AgentTemplateKind.NO_CLASS_TOMORROW
            today.isEmpty() -> AgentTemplateKind.NO_CLASS_TODAY
            current != null -> AgentTemplateKind.DURING_CLASS
            first != null && now.isBefore(first.start) -> {
                if (Duration.between(now, first.start).toMinutes() <= 60) AgentTemplateKind.BEFORE_NEXT_CLASS
                else AgentTemplateKind.MORNING_OVERVIEW
            }
            next != null && previous != null -> {
                if (Duration.between(previous.end, next.start).toMinutes() >= 45) AgentTemplateKind.LONG_BREAK
                else AgentTemplateKind.SHORT_BREAK
            }
            last != null && !now.isBefore(last.end) && (now >= LocalTime.of(18, 0) || facts.tomorrow.isNotEmpty()) -> {
                if (facts.tomorrow.isEmpty()) AgentTemplateKind.NO_CLASS_TOMORROW else AgentTemplateKind.TOMORROW_PREVIEW
            }
            else -> AgentTemplateKind.AFTER_LAST_CLASS
        }

        val values = placeholderValues(facts, current, next, previous)
        val main = renderTemplate(pack.templates[kind.name] ?: defaultAgentTemplates().getValue(kind.name), values)
        val alert = if (facts.weather?.hasAlert == true && kind != AgentTemplateKind.WEATHER_ALERT) {
            renderTemplate(
                pack.templates[AgentTemplateKind.WEATHER_ALERT.name]
                    ?: defaultAgentTemplates().getValue(AgentTemplateKind.WEATHER_ALERT.name),
                values
            )
        } else null
        val text = listOfNotNull(alert, main).joinToString("\n")
        val compact = when {
            current != null -> "${current.course.name} · ${current.end} 下课"
            next != null -> "距${next.course.name} ${formatDuration(Duration.between(now, next.start))}"
            facts.tomorrow.isNotEmpty() -> "明天 ${facts.tomorrow.size} 门课 · ${facts.tomorrow.first().start} 开始"
            else -> "今日安排已整理"
        }
        return RenderedAgentMessage(
            kind = kind,
            text = text,
            compactText = compact,
            quickQuestions = listOf("今天有什么安排", "帮我添加新的课", "帮我安排复习")
        )
    }
}

fun parseAgentCourseDraft(content: String, facts: DayAgentFacts): ParsedAgentCourseDraft {
    val marker = Regex("<course_draft>([\\s\\S]*?)</course_draft>")
    val json = marker.find(content)?.groupValues?.getOrNull(1)
    val displayText = content
        .replace(marker, "")
        .substringBefore("<course_draft>")
        .trim()
    if (json.isNullOrBlank()) return ParsedAgentCourseDraft(displayText, null)
    val draft = runCatching { AgentJson.decodeFromString<AgentCourseDraft>(json) }.getOrNull()
        ?: return ParsedAgentCourseDraft(displayText, null)
    val validPeriodIndexes = facts.periodDefinitions.mapTo(hashSetOf()) { it.periodIndex }
    val periods = draft.periods.distinct().sorted().filter { it in validPeriodIndexes }
    val weeks = draft.weeks.distinct().sorted().filter { it in 1..facts.totalWeeks }
    if (draft.name.isBlank() || draft.weekday !in 1..7 || periods.isEmpty() || weeks.isEmpty()) {
        return ParsedAgentCourseDraft(displayText, null)
    }
    val parity = runCatching { WeekParity.valueOf(draft.weekParity.uppercase()) }.getOrDefault(WeekParity.ALL)
    return ParsedAgentCourseDraft(
        displayText = displayText,
        course = CourseEntity(
            name = draft.name.trim(),
            teacher = draft.teacher?.trim()?.takeIf(String::isNotBlank),
            location = draft.location?.trim()?.takeIf(String::isNotBlank),
            weekday = draft.weekday,
            periods = periods,
            weeks = weeks,
            weekParity = parity,
            note = draft.note?.trim()?.takeIf(String::isNotBlank),
            scheduleId = facts.scheduleId
        )
    )
}

private fun placeholderValues(
    facts: DayAgentFacts,
    current: AgentCourseSlot?,
    next: AgentCourseSlot?,
    previous: AgentCourseSlot?
): Map<String, String> {
    val now = facts.now.toLocalTime()
    val firstTomorrow = facts.tomorrow.firstOrNull()
    val gap = if (next != null) Duration.between(previous?.end ?: now, next.start) else Duration.ZERO
    val weather = facts.weather
    return mapOf(
        "todayCourseCount" to facts.today.size.toString(),
        "currentCourseName" to (current?.course?.name ?: "当前课程"),
        "currentCourseEnd" to (current?.end?.toString() ?: "稍后"),
        "nextCourseName" to (next?.course?.name ?: "下一节课"),
        "nextCourseLocation" to (next?.course?.location?.takeIf { it.isNotBlank() } ?: "地点待确认"),
        "nextCourseStart" to (next?.start?.toString() ?: "稍后"),
        "timeUntilNext" to if (next == null) "暂无" else formatDuration(Duration.between(now, next.start)),
        "gapDuration" to formatDuration(gap),
        "lastCourseEnd" to (facts.today.lastOrNull()?.end?.toString() ?: "今天"),
        "weatherSummary" to (weather?.summary ?: "天气信息暂不可用"),
        "temperature" to (weather?.temperature?.toString() ?: "--"),
        "apparentTemperature" to (weather?.apparentTemperature?.toString() ?: "--"),
        "rainAdvice" to (weather?.rainAdvice ?: "出门前可以再确认天气"),
        "tomorrowCourseCount" to facts.tomorrow.size.toString(),
        "tomorrowFirstCourse" to (firstTomorrow?.let { "${it.start} ${it.course.name}" } ?: "暂无课程"),
        "tomorrowCourseSummary" to facts.tomorrow.joinToString("；") { "${it.start} ${it.course.name}${it.course.location?.let { location -> " · $location" }.orEmpty()}" }
            .ifBlank { "明天暂无课程安排。" }
    )
}

fun validateAgentTemplates(candidate: Map<String, String>): Map<String, String> {
    val placeholderRegex = Regex("\\{\\{([A-Za-z0-9_]+)\\}\\}")
    return candidate.filter { (key, value) ->
        key in AgentTemplateKind.entries.map { it.name } &&
            value.isNotBlank() &&
            placeholderRegex.findAll(value).all { it.groupValues[1] in AgentAllowedPlaceholders }
    }
}

private fun renderTemplate(template: String, values: Map<String, String>): String {
    var result = template
    values.forEach { (key, value) -> result = result.replace("{{$key}}", value) }
    return result.replace(Regex("\\{\\{[^}]+\\}\\}"), "").replace(Regex("[ ]{2,}"), " ").trim()
}

private fun parseAgentTime(value: String): LocalTime? = runCatching { LocalTime.parse(value) }.getOrNull()

private fun formatDuration(duration: Duration): String {
    val minutes = duration.toMinutes().coerceAtLeast(0)
    val hours = minutes / 60
    val rest = minutes % 60
    return when {
        hours > 0 && rest > 0 -> "${hours}小时${rest}分钟"
        hours > 0 -> "${hours}小时"
        else -> "${rest}分钟"
    }
}

private fun String.sha256(): String = MessageDigest.getInstance("SHA-256")
    .digest(toByteArray(Charsets.UTF_8))
    .joinToString("") { "%02x".format(it) }
