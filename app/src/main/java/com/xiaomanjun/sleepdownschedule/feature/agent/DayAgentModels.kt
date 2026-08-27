package com.xiaomanjun.sleepdownschedule.feature.agent


import com.xiaomanjun.sleepdownschedule.*

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

data class AgentWeatherSnapshot(
    val summary: String,
    val temperature: Int,
    val apparentTemperature: Int,
    val precipitationProbability: Int,
    val windSpeed: Int,
    val fetchedAt: Long
) {
    val hasAlert: Boolean
        get() = precipitationProbability >= 60 || temperature >= 35 || temperature <= 5 || windSpeed >= 35
}

data class AgentCourseSlot(
    val course: CourseEntity,
    val date: LocalDate,
    val start: LocalTime,
    val end: LocalTime
)

data class AgentImageAttachment(
    val mimeType: String,
    val base64: String,
    val sourceName: String
)

data class AgentPersistedMessageContent(
    val text: String,
    val attachmentFileName: String? = null
)

private val AgentImageMarkerRegex =
    Regex("""^\[\[agent_image:([A-Za-z0-9._-]+)]]\r?\n?""")

fun agentMessageContent(text: String, attachmentFileName: String?): String {
    return if (attachmentFileName.isNullOrBlank()) {
        text
    } else {
        "[[agent_image:$attachmentFileName]]\n$text"
    }
}

fun parseAgentMessageContent(content: String): AgentPersistedMessageContent {
    val match = AgentImageMarkerRegex.find(content)
    return AgentPersistedMessageContent(
        text = if (match == null) content else content.removeRange(match.range),
        attachmentFileName = match?.groupValues?.getOrNull(1)
    )
}

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
    val termState: ScheduleTermState = ScheduleTermState.MANUAL,
    val termStatus: String = "手动设置 · 第 1 周",
    val settingSnapshot: Map<String, String> = emptyMap(),
    val semesterCourses: List<CourseEntity> = emptyList(),
    val periodSchemes: List<AgentPeriodSchemeSnapshot> = emptyList(),
    val activePeriodSchemeId: Long? = null
)

data class AgentPeriodSchemeSnapshot(
    val id: Long,
    val name: String,
    val mode: PeriodSchemeMode,
    val isActive: Boolean,
    val classDurationMinutes: Int,
    val breakDurationMinutes: Int,
    val morningStartTime: String,
    val noonStartTime: String,
    val afternoonStartTime: String,
    val eveningStartTime: String,
    val specialBreaks: Map<Int, Int>,
    val overriddenPeriods: Set<Int>,
    val times: List<PeriodSchemeTimeEntity>
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
    val note: String? = null,
    /** Optional exact wall-clock range for courses that do not align to period boundaries. */
    val customStartTime: String? = null,
    val customEndTime: String? = null
)

data class ParsedAgentCourseDraft(
    val displayText: String,
    val course: CourseEntity?
)

@Serializable
enum class AgentActionType {
    ADD_COURSE,
    UPDATE_COURSE,
    DELETE_COURSE,
    OPEN_SETTINGS,
    SET_SETTING,
    SET_PERIOD_SETTINGS
}

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
    val note: String? = null,
    /** Set both fields to define an exact range; omit both to preserve an existing range. */
    val customStartTime: String? = null,
    val customEndTime: String? = null
)

@Serializable
data class AgentPeriodTimePatch(
    val periodIndex: Int,
    val startTime: String,
    val endTime: String
)

@Serializable
data class AgentPeriodSettingsPatch(
    val schemeName: String? = null,
    val mode: String? = null,
    val morningPeriodCount: Int? = null,
    val noonPeriodCount: Int? = null,
    val afternoonPeriodCount: Int? = null,
    val eveningPeriodCount: Int? = null,
    val classDurationMinutes: Int? = null,
    val breakDurationMinutes: Int? = null,
    val morningStartTime: String? = null,
    val noonStartTime: String? = null,
    val afternoonStartTime: String? = null,
    val eveningStartTime: String? = null,
    val periods: List<AgentPeriodTimePatch>? = null,
    val specialBreaks: Map<String, Int>? = null,
    val overriddenPeriods: List<Int>? = null
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
    val periodSettings: AgentPeriodSettingsPatch? = null,
    val summary: String = ""
)

enum class AgentValidatedActionType {
    ADD,
    UPDATE,
    DELETE,
    OPEN_SETTINGS,
    SET_SETTING,
    SET_PERIOD_SETTINGS
}

data class AgentValidatedAction(
    val type: AgentValidatedActionType,
    val original: CourseEntity? = null,
    val edited: CourseEntity? = null,
    val scope: AgentActionScope = AgentActionScope.CURRENT_WEEK,
    val targetWeek: Int = 1,
    val settingsPage: String? = null,
    val settingKey: String? = null,
    val settingValue: String? = null,
    val periodSettings: AgentPeriodSettingsPatch? = null,
    val summary: String
)

data class ParsedAgentActions(
    val displayText: String,
    val actions: List<AgentValidatedAction>
)

private val AgentJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

fun buildDayAgentFacts(
    courses: List<CourseEntity>,
    periods: List<PeriodEntity>,
    config: ScheduleConfigEntity,
    date: LocalDate,
    weather: AgentWeatherSnapshot?,
    scheduleName: String? = null,
    now: LocalDateTime = LocalDateTime.now(),
    settingContext: android.content.Context? = null
): DayAgentFacts {
    val scheduleCourses = courses.filter { it.scheduleId == config.id }
    val schedulePeriods = periods.filter { it.scheduleId == config.id }
    val termState = derivedScheduleTermState(config, date)
    val termStatus = scheduleTermStatusDescription(config, date)
    val currentWeek = effectiveCurrentWeek(config, date)
    fun slotsFor(targetDate: LocalDate): List<AgentCourseSlot> {
        val week = scheduleWeekForDateOrNull(config, targetDate) ?: return emptyList()
        val weekday = targetDate.dayOfWeek.value
        return scheduleCourses.asSequence()
            .filter { it.weekday == weekday && week in it.weeks && parityMatches(it.weekParity, week) }
            .mapNotNull { course ->
                val start = courseStartTime(course, schedulePeriods)
                val end = courseEndTime(course, schedulePeriods)
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
        append(config.id).append('|').append(date).append('|').append(termState).append(':')
            .append(currentWeek).append('|')
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
        periodDefinitions = schedulePeriods.sortedBy { it.periodIndex },
        totalWeeks = config.totalWeeks,
        scheduleId = config.id,
        currentWeek = currentWeek,
        termState = termState,
        termStatus = termStatus,
        settingSnapshot = AgentSettingRegistry.snapshot(config, scheduleName, settingContext, date),
        semesterCourses = scheduleCourses
            .sortedWith(compareBy<CourseEntity> { it.name }.thenBy { it.weekday }.thenBy { it.periods.minOrNull() ?: Int.MAX_VALUE })
    )
}

fun parseAgentActions(content: String, facts: DayAgentFacts): ParsedAgentActions {
    val actionMarker = Regex(
        "<agent_actions\\s*>([\\s\\S]*?)(?:</agent_actions\\s*>|$)",
        RegexOption.IGNORE_CASE
    )
    val legacy = parseAgentCourseDraft(content, facts)
    val markerMatch = actionMarker.find(content)
    val payload = markerMatch?.groupValues?.getOrNull(1)
        ?: extractLooseAgentActionPayload(content)
    val displayText = legacy.displayText
        .replace(actionMarker, "")
        .let { text -> if (markerMatch == null && payload != null) text.replace(payload, "") else text }
        .replace(Regex("```(?:json)?\\s*\\s*```", RegexOption.IGNORE_CASE), "")
        .trim()
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
    val drafts = payload?.let(::decodeAgentActionDrafts).orEmpty()
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
                validPeriods = validPeriods,
                scope = draft.scope
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
                validateAgentCoursePatch(draft.course, original, facts, validPeriods, draft.scope)?.let { edited ->
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
            AgentActionType.SET_SETTING -> normalizeAgentSetting(draft.settingKey, draft.settingValue, facts)?.let { (key, value) ->
                actions += AgentValidatedAction(
                    AgentValidatedActionType.SET_SETTING,
                    settingKey = key,
                    settingValue = value,
                    targetWeek = facts.currentWeek,
                    summary = draft.summary.ifBlank { "修改应用设置" }
                )
            }
            AgentActionType.SET_PERIOD_SETTINGS ->
                validateAgentPeriodSettings(draft.periodSettings, facts)?.let { patch ->
                    actions += AgentValidatedAction(
                        type = AgentValidatedActionType.SET_PERIOD_SETTINGS,
                        periodSettings = patch,
                        targetWeek = facts.currentWeek,
                        summary = draft.summary.ifBlank { "修改当前课表的节次设置" }
                    )
                }
        }
    }
    return ParsedAgentActions(displayText, actions)
}

private fun decodeAgentActionDrafts(payload: String): List<AgentActionDraft> {
    val unfenced = payload.trim()
        .removePrefix("```json")
        .removePrefix("```JSON")
        .removePrefix("```")
        .removeSuffix("```")
        .trim()
    val candidates = buildList {
        add(unfenced)
        Regex("\\\"actions\\\"\\s*:\\s*(\\[[\\s\\S]*])", RegexOption.IGNORE_CASE)
            .find(unfenced)?.groupValues?.getOrNull(1)?.let(::add)
        val arrayStart = unfenced.indexOf('[')
        val arrayEnd = unfenced.lastIndexOf(']')
        if (arrayStart >= 0 && arrayEnd > arrayStart) add(unfenced.substring(arrayStart, arrayEnd + 1))
        val objectStart = unfenced.indexOf('{')
        val objectEnd = unfenced.lastIndexOf('}')
        if (objectStart >= 0 && objectEnd > objectStart) add(unfenced.substring(objectStart, objectEnd + 1))
    }.distinct()
    candidates.forEach { rawCandidate ->
        val candidate = normalizeLooseAgentActionJson(rawCandidate)
        runCatching { AgentJson.decodeFromString<List<AgentActionDraft>>(candidate) }
            .getOrNull()?.let { return it }
        runCatching { AgentJson.decodeFromString<AgentActionDraft>(candidate) }
            .getOrNull()?.let { return listOf(it) }
    }
    return emptyList()
}

private fun extractLooseAgentActionPayload(content: String): String? {
    if (!Regex("\\\"type\\\"\\s*:\\s*\\\"(?:ADD_COURSE|UPDATE_COURSE|DELETE_COURSE|OPEN_SETTINGS|SET_SETTING|SET_PERIOD_SETTINGS)", RegexOption.IGNORE_CASE)
            .containsMatchIn(content)) return null
    val fenced = Regex("```(?:json)?\\s*([\\s\\S]*?)```", RegexOption.IGNORE_CASE)
        .findAll(content)
        .map { it.groupValues[1] }
        .firstOrNull { it.contains("\"type\"", ignoreCase = true) }
    if (fenced != null) return fenced
    val arrayStart = content.indexOf('[')
    val arrayEnd = content.lastIndexOf(']')
    if (arrayStart >= 0 && arrayEnd > arrayStart) return content.substring(arrayStart, arrayEnd + 1)
    val objectStart = content.indexOf('{')
    val objectEnd = content.lastIndexOf('}')
    return if (objectStart >= 0 && objectEnd > objectStart) content.substring(objectStart, objectEnd + 1) else null
}

private fun normalizeLooseAgentActionJson(raw: String): String {
    var normalized = raw.trim()
    listOf("type", "scope", "weekParity").forEach { key ->
        normalized = Regex("(\\\"$key\\\"\\s*:\\s*\\\")([^\\\"]+)(\\\")", RegexOption.IGNORE_CASE)
            .replace(normalized) { match ->
                match.groupValues[1] + match.groupValues[2].trim().uppercase() + match.groupValues[3]
            }
    }
    normalized = Regex("(\\\"settingValue\\\"\\s*:\\s*)(true|false|-?\\d+(?:\\.\\d+)?)", RegexOption.IGNORE_CASE)
        .replace(normalized) { match -> match.groupValues[1] + "\"" + match.groupValues[2].uppercase() + "\"" }
    return normalized
}

private fun validateAgentCoursePatch(
    patch: AgentCoursePatch?,
    base: CourseEntity?,
    facts: DayAgentFacts,
    validPeriods: Set<Int>,
    scope: AgentActionScope
): CourseEntity? {
    patch ?: return null
    val name = patch.name?.trim()?.takeIf { it.isNotBlank() } ?: base?.name ?: return null
    val weekday = patch.weekday ?: base?.weekday ?: return null
    val periods = (patch.periods ?: base?.periods.orEmpty()).distinct().sorted().filter { it in validPeriods }
    val requestedWeeks = patch.weeks.orEmpty().filter { it in 1..facts.totalWeeks }
    val weeks = when {
        requestedWeeks.isNotEmpty() -> requestedWeeks
        base != null -> base.weeks
        scope == AgentActionScope.CURRENT_WEEK -> listOf(facts.currentWeek)
        else -> (1..facts.totalWeeks).toList()
    }.distinct().sorted().filter { it in 1..facts.totalWeeks }
    if (weekday !in 1..7 || periods.isEmpty() || weeks.isEmpty()) return null
    val parity = patch.weekParity?.let { runCatching { WeekParity.valueOf(it.uppercase()) }.getOrNull() }
        ?: base?.weekParity ?: WeekParity.ALL
    val customRange = normalizeAgentCustomTimeRange(
        start = patch.customStartTime,
        end = patch.customEndTime,
        base = base
    ) ?: return null
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
            customStartTime = customRange.first,
            customEndTime = customRange.second,
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
            customStartTime = customRange.first,
            customEndTime = customRange.second,
            scheduleId = facts.scheduleId
        )
    }
}

/**
 * Normalize the exact time range used by course-management's custom-time mode.  Agent action
 * fields are optional for updates: when omitted, the existing range is carried forward.  A
 * partial or reversed range is rejected instead of silently falling back to period times, which
 * would make a confirmed "10:10" request appear to succeed while storing a different schedule.
 */
private fun normalizeAgentCustomTimeRange(
    start: String?,
    end: String?,
    base: CourseEntity?
): Pair<String?, String?>? {
    if (start == null && end == null) {
        return base?.customStartTime to base?.customEndTime
    }
    if (start.isNullOrBlank() || end.isNullOrBlank()) return null
    val parsedStart = parseAgentTime(start) ?: return null
    val parsedEnd = parseAgentTime(end) ?: return null
    if (!parsedEnd.isAfter(parsedStart)) return null
    return parsedStart.toString() to parsedEnd.toString()
}

private fun normalizeAgentSettingsPage(value: String?): String? = when (value?.trim()?.uppercase()) {
    "GENERAL", "AI_IMPORT", "DAY_AGENT", "SCHEDULE", "NOTIFICATIONS",
    "SCHEDULE_MANAGER", "ABOUT", "CHANGELOG", "DOWNLOAD", "DONATE" -> value.trim().uppercase()
    else -> null
}

private fun normalizeAgentSetting(
    keyValue: String?,
    rawValue: String?,
    facts: DayAgentFacts
): Pair<String, String>? {
    val normalized = AgentSettingRegistry.normalize(keyValue, rawValue) ?: return null
    if (AgentSettingRegistry.isPeriodTimeSetting(normalized.first)) {
        val periodIndex = Regex("PERIOD_(\\d+)_TIME").matchEntire(normalized.first)
            ?.groupValues?.getOrNull(1)?.toIntOrNull() ?: return null
        if (facts.periodDefinitions.none { it.periodIndex == periodIndex }) return null
    }
    return normalized
}

private fun validateAgentPeriodSettings(
    patch: AgentPeriodSettingsPatch?,
    facts: DayAgentFacts
): AgentPeriodSettingsPatch? {
    patch ?: return null
    val mode = patch.mode?.trim()?.uppercase()
    if (mode != null && mode !in setOf("MANUAL", "AUTO_MATCH")) return null
    val counts = listOf(
        patch.morningPeriodCount,
        patch.noonPeriodCount,
        patch.afternoonPeriodCount,
        patch.eveningPeriodCount
    )
    if (counts.filterNotNull().any { it !in 0..30 }) return null
    if (counts.any { it != null } && counts.any { it == null }) return null
    val currentCount = facts.periodDefinitions.size
    val requestedCount = if (counts.any { it != null }) {
        (patch.morningPeriodCount ?: 0) +
            (patch.noonPeriodCount ?: 0) +
            (patch.afternoonPeriodCount ?: 0) +
            (patch.eveningPeriodCount ?: 0)
    } else {
        currentCount
    }
    if (requestedCount !in 1..30) return null
    if (patch.classDurationMinutes != null && patch.classDurationMinutes !in 1..300) return null
    if (patch.breakDurationMinutes != null && patch.breakDurationMinutes !in 0..300) return null
    val startTimes = listOf(
        patch.morningStartTime,
        patch.noonStartTime,
        patch.afternoonStartTime,
        patch.eveningStartTime
    )
    if (startTimes.filterNotNull().any { runCatching { LocalTime.parse(it) }.isFailure }) return null
    if (patch.specialBreaks.orEmpty().any { (key, value) ->
            key.toIntOrNull() !in 1..requestedCount || value !in 0..300
        }) return null
    if (patch.overriddenPeriods.orEmpty().any { it !in 1..requestedCount }) return null
    patch.periods?.let { periods ->
        if (periods.size != requestedCount) return null
        if (periods.map { it.periodIndex }.sorted() != (1..requestedCount).toList()) return null
        val timeline = periods.sortedBy { it.periodIndex }.map {
            PeriodSchemeTimeEntity(
                schemeId = 0,
                periodIndex = it.periodIndex,
                startTime = it.startTime,
                endTime = it.endTime
            )
        }
        if (validateResolvedPeriodTimes(timeline) != null) return null
    }
    return patch.copy(mode = mode)
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
    val customRange = normalizeAgentCustomTimeRange(
        start = draft.customStartTime,
        end = draft.customEndTime,
        base = null
    )
    if (draft.name.isBlank() || draft.weekday !in 1..7 || periods.isEmpty() || weeks.isEmpty()) {
        return ParsedAgentCourseDraft(displayText, null)
    }
    if ((draft.customStartTime != null || draft.customEndTime != null) && customRange == null) {
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
            customStartTime = customRange?.first,
            customEndTime = customRange?.second,
            scheduleId = facts.scheduleId
        )
    )
}

private fun parseAgentTime(value: String): LocalTime? = runCatching { LocalTime.parse(value) }.getOrNull()

private fun String.sha256(): String = MessageDigest.getInstance("SHA-256")
    .digest(toByteArray(Charsets.UTF_8))
    .joinToString("") { "%02x".format(it) }
