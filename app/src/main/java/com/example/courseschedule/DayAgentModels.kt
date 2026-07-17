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
    val quickQuestions: List<String> = emptyList(),
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
    val currentWeek: Int = 1,
    val settingSnapshot: Map<String, String> = emptyMap(),
    val semesterCourses: List<CourseEntity> = emptyList()
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

@Serializable
enum class AgentActionType { ADD_COURSE, UPDATE_COURSE, DELETE_COURSE, OPEN_SETTINGS, SET_SETTING }

@Serializable
enum class AgentActionScope { CURRENT_WEEK, ALL_WEEKS }

@Serializable
data class AgentCoursePatch(
    val name: String? = null,
    val teacher: String? = null,
    val location: String? = null,
    val weekday: Int? = null,
    val periods: List<Int>? = null,
    val weeks: List<Int>? = null,
    val weekParity: String? = null,
    val note: String? = null
)

@Serializable
data class AgentActionDraft(
    val type: AgentActionType,
    val courseId: Long? = null,
    val scope: AgentActionScope = AgentActionScope.CURRENT_WEEK,
    val course: AgentCoursePatch? = null,
    val settingsPage: String? = null,
    val settingKey: String? = null,
    val settingValue: String? = null,
    val summary: String = ""
)

enum class AgentValidatedActionType { ADD, UPDATE, DELETE, OPEN_SETTINGS, SET_SETTING }

data class AgentValidatedAction(
    val type: AgentValidatedActionType,
    val original: CourseEntity? = null,
    val edited: CourseEntity? = null,
    val scope: AgentActionScope = AgentActionScope.CURRENT_WEEK,
    val targetWeek: Int = 1,
    val settingsPage: String? = null,
    val settingKey: String? = null,
    val settingValue: String? = null,
    val summary: String
)

data class ParsedAgentActions(
    val displayText: String,
    val actions: List<AgentValidatedAction>
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
    scheduleName: String? = null,
    now: LocalDateTime = LocalDateTime.now(ZoneId.of("Asia/Shanghai")),
    settingContext: android.content.Context? = null
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
        currentWeek = currentWeek,
        settingSnapshot = AgentSettingRegistry.snapshot(config, scheduleName, settingContext),
        semesterCourses = courses
            .filter { it.scheduleId == config.id }
            .sortedWith(compareBy<CourseEntity> { it.name }.thenBy { it.weekday }.thenBy { it.periods.minOrNull() ?: Int.MAX_VALUE })
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
            quickQuestions = pack.quickQuestions.takeIf { it.size >= 2 }
                ?: defaultAgentQuickQuestions(facts)
        )
    }
}

fun defaultAgentQuickQuestions(facts: DayAgentFacts): List<String> = when {
    facts.today.isEmpty() -> listOf("今天怎么安排更合适", "帮我添加新的课")
    facts.tomorrow.isEmpty() -> listOf("今天还有什么安排", "帮我调整一门课")
    else -> listOf("今天有什么安排", "明天要准备什么")
}

fun parseAgentActions(content: String, facts: DayAgentFacts): ParsedAgentActions {
    val actionMarker = Regex("<agent_actions>([\\s\\S]*?)</agent_actions>")
    val legacy = parseAgentCourseDraft(content, facts)
    val displayText = legacy.displayText.replace(actionMarker, "").trim()
    val actions = mutableListOf<AgentValidatedAction>()
    legacy.course?.let { course ->
        actions += AgentValidatedAction(
            type = AgentValidatedActionType.ADD,
            edited = course,
            targetWeek = facts.currentWeek,
            scope = AgentActionScope.ALL_WEEKS,
            summary = "添加 ${course.name}"
        )
    }
    val payload = actionMarker.find(content)?.groupValues?.getOrNull(1)
    val drafts = payload?.let {
        runCatching { AgentJson.decodeFromString<List<AgentActionDraft>>(it) }.getOrNull()
    }.orEmpty()
    val knownCourses = (facts.week.map { it.course } + facts.semesterCourses)
        .distinctBy { it.id }
        .associateBy { it.id }
    val validPeriods = facts.periodDefinitions.mapTo(hashSetOf()) { it.periodIndex }
    drafts.forEach { draft ->
        when (draft.type) {
            AgentActionType.ADD_COURSE -> validateAgentCoursePatch(
                patch = draft.course,
                base = null,
                facts = facts,
                validPeriods = validPeriods
            )?.let { course ->
                actions += AgentValidatedAction(
                    AgentValidatedActionType.ADD,
                    edited = course.copy(id = 0, scheduleId = facts.scheduleId),
                    scope = draft.scope,
                    targetWeek = facts.currentWeek,
                    summary = draft.summary.ifBlank { "添加 ${course.name}" }
                )
            }
            AgentActionType.UPDATE_COURSE -> knownCourses[draft.courseId]?.let { original ->
                validateAgentCoursePatch(draft.course, original, facts, validPeriods)?.let { edited ->
                    actions += AgentValidatedAction(
                        AgentValidatedActionType.UPDATE,
                        original = original,
                        edited = edited.copy(id = original.id, scheduleId = facts.scheduleId),
                        scope = draft.scope,
                        targetWeek = facts.currentWeek,
                        summary = draft.summary.ifBlank { "修改 ${original.name}" }
                    )
                }
            }
            AgentActionType.DELETE_COURSE -> knownCourses[draft.courseId]?.let { original ->
                actions += AgentValidatedAction(
                    AgentValidatedActionType.DELETE,
                    original = original,
                    scope = draft.scope,
                    targetWeek = facts.currentWeek,
                    summary = draft.summary.ifBlank { "删除 ${original.name}" }
                )
            }
            AgentActionType.OPEN_SETTINGS -> normalizeAgentSettingsPage(draft.settingsPage)?.let { page ->
                actions += AgentValidatedAction(
                    AgentValidatedActionType.OPEN_SETTINGS,
                    settingsPage = page,
                    targetWeek = facts.currentWeek,
                    summary = draft.summary.ifBlank { "打开相关设置" }
                )
            }
            AgentActionType.SET_SETTING -> normalizeAgentSetting(draft.settingKey, draft.settingValue)?.let { (key, value) ->
                actions += AgentValidatedAction(
                    AgentValidatedActionType.SET_SETTING,
                    settingKey = key,
                    settingValue = value,
                    targetWeek = facts.currentWeek,
                    summary = draft.summary.ifBlank { "修改应用设置" }
                )
            }
        }
    }
    return ParsedAgentActions(displayText, actions)
}

private fun validateAgentCoursePatch(
    patch: AgentCoursePatch?,
    base: CourseEntity?,
    facts: DayAgentFacts,
    validPeriods: Set<Int>
): CourseEntity? {
    patch ?: return null
    val name = patch.name?.trim()?.takeIf { it.isNotBlank() } ?: base?.name ?: return null
    val weekday = patch.weekday ?: base?.weekday ?: return null
    val periods = (patch.periods ?: base?.periods.orEmpty()).distinct().sorted().filter { it in validPeriods }
    val weeks = (patch.weeks ?: base?.weeks.orEmpty()).distinct().sorted().filter { it in 1..facts.totalWeeks }
    if (weekday !in 1..7 || periods.isEmpty() || weeks.isEmpty()) return null
    val parity = patch.weekParity?.let { runCatching { WeekParity.valueOf(it.uppercase()) }.getOrNull() }
        ?: base?.weekParity ?: WeekParity.ALL
    return if (base != null) {
        base.copy(
            name = name,
            teacher = patch.teacher?.trim()?.takeIf { it.isNotBlank() } ?: base.teacher,
            location = patch.location?.trim()?.takeIf { it.isNotBlank() } ?: base.location,
            weekday = weekday,
            periods = periods,
            weeks = weeks,
            weekParity = parity,
            note = patch.note?.trim()?.takeIf { it.isNotBlank() } ?: base.note,
            scheduleId = facts.scheduleId
        )
    } else {
        CourseEntity(
            name = name,
            teacher = patch.teacher?.trim()?.takeIf { it.isNotBlank() },
            location = patch.location?.trim()?.takeIf { it.isNotBlank() },
            weekday = weekday,
            periods = periods,
            weeks = weeks,
            weekParity = parity,
            note = patch.note?.trim()?.takeIf { it.isNotBlank() },
            scheduleId = facts.scheduleId
        )
    }
}

private fun normalizeAgentSettingsPage(value: String?): String? = when (value?.trim()?.uppercase()) {
    "GENERAL", "AI_IMPORT", "DAY_AGENT", "SCHEDULE", "NOTIFICATIONS",
    "SCHEDULE_MANAGER", "ABOUT", "CHANGELOG", "DOWNLOAD", "DONATE" -> value.trim().uppercase()
    else -> null
}

private fun normalizeAgentSetting(keyValue: String?, rawValue: String?): Pair<String, String>? =
    AgentSettingRegistry.normalize(keyValue, rawValue)

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
