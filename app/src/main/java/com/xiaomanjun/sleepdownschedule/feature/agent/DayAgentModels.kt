package com.xiaomanjun.sleepdownschedule.feature.agent


import com.xiaomanjun.sleepdownschedule.*
import com.xiaomanjun.sleepdownschedule.domain.schedule.ScheduleAdjustment
import com.xiaomanjun.sleepdownschedule.domain.schedule.encodeScheduleAdjustments

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

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
    val end: LocalTime,
    val originalDate: LocalDate? = null,
    val teachingWeek: Int? = null
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
    val activePeriodSchemeId: Long? = null,
    val currentTeachingDate: LocalDate? = date,
    val currentTeachingWeek: Int = currentWeek,
    val todayIsAdjusted: Boolean = false,
    /** Current 调休/补课 table for the active schedule, decoded from `scheduleAdjustmentsJson`. */
    val scheduleAdjustments: List<ScheduleAdjustment> = emptyList(),
    /** All schedules the app knows about, used to validate switch/delete targets. */
    val schedules: List<AgentScheduleSummary> = emptyList(),
    /** Device wall-clock zone captured with [now] for deterministic relative-date answers. */
    val timeZoneId: String = ZoneId.systemDefault().id,
    val utcOffset: String = now.atZone(ZoneId.systemDefault()).offset.id
)

/** Minimal schedule descriptor exposed to the agent for multi-schedule actions. */
data class AgentScheduleSummary(
    val id: Int,
    val name: String,
    val isActive: Boolean
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
    REPLACE_COURSE,
    DELETE_COURSE,
    OPEN_SETTINGS,
    OPEN_IMPORT,
    SET_SETTING,
    SET_PERIOD_SETTINGS,
    SET_ADJUSTMENTS,
    CREATE_SCHEDULE,
    ACTIVATE_SCHEDULE,
    DELETE_SCHEDULE
}

@Serializable
enum class AgentActionScope { CURRENT_WEEK, SELECTED_WEEKS, ALL_WEEKS }

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
    val customEndTime: String? = null,
    /**
     * Explicit `#AARRGGBB` / `#RRGGBB` course colour. Absent or null preserves the stored colour;
     * a value that cannot be parsed rejects the whole action instead of silently keeping the old one.
     */
    val customColorArgb: String? = null,
    /**
     * Patch-only escape hatch for fields whose "absent" value already means "preserve". Only the
     * whitelisted names in [AgentClearableCourseField] are honoured; unknown names reject the plan.
     */
    val clearFields: List<String>? = null
)

/** Field names accepted in [AgentCoursePatch.clearFields]. */
enum class AgentClearableCourseField(val wireName: String) {
    TEACHER("teacher"),
    LOCATION("location"),
    NOTE("note"),
    CUSTOM_TIME("customTime")
}

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
    val sourceWeeks: List<Int>? = null,
    val course: AgentCoursePatch? = null,
    val settingsPage: String? = null,
    val settingKey: String? = null,
    val settingValue: String? = null,
    val periodSettings: AgentPeriodSettingsPatch? = null,
    /** Whole-table replacement payload for [AgentActionType.SET_ADJUSTMENTS]. */
    val adjustments: List<AgentAdjustmentDraft>? = null,
    /** Target schedule for activate/delete. */
    val scheduleId: Int? = null,
    /** New schedule name for [AgentActionType.CREATE_SCHEDULE]. */
    val name: String? = null,
    val importText: String? = null,
    val clearFields: List<String>? = null,
    val summary: String = ""
)

/** One 调休/补课 entry. Mirrors the persisted `ScheduleAdjustment` shape. */
@Serializable
data class AgentAdjustmentDraft(
    val date: String,
    val sourceDate: String? = null,
    val label: String = ""
)

enum class AgentValidatedActionType {
    ADD,
    UPDATE,
    REPLACE,
    DELETE,
    OPEN_SETTINGS,
    OPEN_IMPORT,
    SET_SETTING,
    SET_PERIOD_SETTINGS,
    SET_ADJUSTMENTS,
    CREATE_SCHEDULE,
    ACTIVATE_SCHEDULE,
    DELETE_SCHEDULE
}

data class AgentValidatedAction(
    val type: AgentValidatedActionType,
    val original: CourseEntity? = null,
    val edited: CourseEntity? = null,
    val scope: AgentActionScope = AgentActionScope.CURRENT_WEEK,
    val targetWeek: Int = 1,
    val sourceWeeks: List<Int> = emptyList(),
    val settingsPage: String? = null,
    val settingKey: String? = null,
    val settingValue: String? = null,
    val periodSettings: AgentPeriodSettingsPatch? = null,
    /** Decoded and validated replacement list for [AgentValidatedActionType.SET_ADJUSTMENTS]. */
    val adjustments: List<ScheduleAdjustment>? = null,
    /** Target schedule for activate/delete; the schedule name is carried in [summary]. */
    val scheduleId: Int? = null,
    /** New schedule name for [AgentValidatedActionType.CREATE_SCHEDULE]. */
    val scheduleName: String? = null,
    val importText: String? = null,
    /** Filled locally from this reply's user message; never accepted from model JSON. */
    val importAttachmentUri: String? = null,
    val sourceScheduleId: Int? = null,
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
    settingContext: android.content.Context? = null,
    schedules: List<AgentScheduleSummary> = emptyList()
): DayAgentFacts {
    val scheduleCourses = courses.filter { it.scheduleId == config.id }
    val schedulePeriods = periods.filter { it.scheduleId == config.id }
    val termState = derivedScheduleTermState(config, date)
    val termStatus = scheduleTermStatusDescription(config, date)
    val currentTeachingDate = com.xiaomanjun.sleepdownschedule.domain.schedule.teachingDateForSchedule(config, date)
    val currentWeek = effectiveCurrentWeek(config, date)
    val currentTeachingWeek = if (currentTeachingDate != null && currentTeachingDate != date)
        com.xiaomanjun.sleepdownschedule.domain.schedule.adjustedTeachingWeekForDate(config, currentTeachingDate)
            ?: effectiveCurrentWeek(config, date) else effectiveCurrentWeek(config, date)
    fun slotsFor(targetDate: LocalDate): List<AgentCourseSlot> {
        if (scheduleWeekForDateOrNull(config, targetDate) == null) return emptyList()
        val teachingDate = com.xiaomanjun.sleepdownschedule.domain.schedule.teachingDateForSchedule(config, targetDate) ?: return emptyList()
        val week = (if (teachingDate != targetDate)
            com.xiaomanjun.sleepdownschedule.domain.schedule.adjustedTeachingWeekForDate(config, teachingDate)
            else scheduleWeekForDateOrNull(config, targetDate)) ?: return emptyList()
        val weekday = teachingDate.dayOfWeek.value
        return scheduleCourses.asSequence()
            .filter { it.weekday == weekday && week in it.weeks && parityMatches(it.weekParity, week) }
            .mapNotNull { course ->
                val start = courseStartTime(course, schedulePeriods)
                val end = courseEndTime(course, schedulePeriods)
                if (start == null || end == null) null else AgentCourseSlot(course, targetDate, start, end,
                    teachingDate.takeIf { it != targetDate }, week)
            }
            .sortedBy { it.start }
            .toList()
    }

    val today = slotsFor(date)
    val tomorrow = slotsFor(date.plusDays(1))
    val weekStart = date.minusDays((date.dayOfWeek.value - 1).toLong())
    val week = (0L..6L).flatMap { offset -> slotsFor(weekStart.plusDays(offset)) }
    val settingsSnapshot = AgentSettingRegistry.snapshot(config, scheduleName, settingContext, date)
    val source = buildString {
        append(config).append('|').append(settingsSnapshot.toSortedMap()).append('|')
        append(scheduleCourses.sortedBy { it.id }).append('|').append(schedulePeriods.sortedBy { it.periodIndex })
        append('|').append(schedules.sortedBy { it.id }).append('|').append(weather).append('|')
        append(config.id).append('|').append(date).append('|').append(termState).append(':')
            .append(currentWeek).append('|').append(config.scheduleAdjustmentsJson).append('|')
        week.forEach { slot ->
            append(slot.date).append(':').append(slot.course.id).append(':').append(slot.course.name).append(':')
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
        currentTeachingDate = currentTeachingDate,
        currentTeachingWeek = currentTeachingWeek,
        todayIsAdjusted = currentTeachingDate != date,
        termState = termState,
        termStatus = termStatus,
        settingSnapshot = settingsSnapshot,
        semesterCourses = scheduleCourses
            .sortedWith(compareBy<CourseEntity> { it.name }.thenBy { it.weekday }.thenBy { it.periods.minOrNull() ?: Int.MAX_VALUE }),
        scheduleAdjustments = runCatching {
            com.xiaomanjun.sleepdownschedule.domain.schedule.decodeScheduleAdjustments(config.scheduleAdjustmentsJson)
        }.getOrDefault(emptyList()),
        schedules = schedules
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
    legacy.course?.takeIf { payload == null }?.let { course ->
        actions += AgentValidatedAction(
            type = AgentValidatedActionType.ADD,
            edited = course,
            targetWeek = facts.currentWeek,
            scope = AgentActionScope.ALL_WEEKS,
            summary = "添加 ${course.name}"
        )
    }
    val drafts = payload?.let(::decodeAgentActionDrafts).orEmpty()
    val plannedTotalWeeks = drafts.singleOrNull { it.type == AgentActionType.SET_SETTING && it.settingKey.equals("TOTAL_WEEKS", true) }
        ?.settingValue?.toIntOrNull()?.takeIf { it in 1..60 } ?: facts.totalWeeks
    val destinationFacts = facts.copy(totalWeeks = plannedTotalWeeks)
    val knownCourses = (facts.week.map { it.course } + facts.semesterCourses)
        .distinctBy { it.id }
        .associateBy { it.id }
    val requestedCount = drafts.singleOrNull { it.type == AgentActionType.SET_PERIOD_SETTINGS }
        ?.periodSettings?.let { patch ->
            listOf(patch.morningPeriodCount, patch.noonPeriodCount, patch.afternoonPeriodCount, patch.eveningPeriodCount)
                .takeIf { counts -> counts.all { it != null } }?.sumOf { it!! }
        }
    val validPeriods = requestedCount?.takeIf { it in 1..30 }?.let { (1..it).toHashSet() }
        ?: facts.periodDefinitions.mapTo(hashSetOf()) { it.periodIndex }
    drafts.forEach { rawDraft ->
        val draft = if (rawDraft.clearFields != null) rawDraft.copy(
            course = (rawDraft.course ?: AgentCoursePatch()).let {
                it.copy(clearFields = (it.clearFields.orEmpty() + rawDraft.clearFields).distinct())
            }
        ) else rawDraft
        val selectedWeeks = draft.sourceWeeks?.distinct()?.sorted().orEmpty()
        if (draft.scope == AgentActionScope.SELECTED_WEEKS &&
            (selectedWeeks.isEmpty() || selectedWeeks.any { it !in 1..maxOf(facts.totalWeeks, plannedTotalWeeks) })) return@forEach
        if (draft.scope != AgentActionScope.SELECTED_WEEKS && selectedWeeks.isNotEmpty()) return@forEach
        val original = knownCourses[draft.courseId]
        if (draft.course?.clearFields?.any { field ->
            AgentClearableCourseField.entries.none { it.wireName.equals(field.trim(), ignoreCase = true) }
        } == true) return@forEach
        if (draft.scope == AgentActionScope.CURRENT_WEEK && draft.course?.weeks != null &&
            draft.course.weeks.distinct() != listOf(currentCourseActionWeek(facts, original, draft.course.weekday))) return@forEach
        if (draft.type in setOf(AgentActionType.UPDATE_COURSE, AgentActionType.REPLACE_COURSE, AgentActionType.DELETE_COURSE) &&
            draft.scope != AgentActionScope.ALL_WEEKS) {
            val source = if (draft.scope == AgentActionScope.SELECTED_WEEKS) selectedWeeks
                else listOf(currentCourseActionWeek(facts, original))
            if (original == null || source.any { it !in original.weeks || !parityMatches(original.weekParity, it) }) return@forEach
        }
        fun scopedEdited(edited: CourseEntity): CourseEntity = when (draft.scope) {
            AgentActionScope.SELECTED_WEEKS -> edited.copy(
                weeks = draft.course?.weeks?.distinct()?.sorted() ?: selectedWeeks,
                weekParity = if (draft.course?.weekParity == null) WeekParity.ALL else edited.weekParity
            )
            AgentActionScope.CURRENT_WEEK -> edited.copy(weeks = listOf(currentCourseActionWeek(facts, original, draft.course?.weekday)))
            AgentActionScope.ALL_WEEKS -> edited
        }
        when (draft.type) {
            AgentActionType.ADD_COURSE -> validateAgentCoursePatch(
                patch = draft.course,
                base = null,
                facts = destinationFacts,
                validPeriods = validPeriods,
                scope = draft.scope,
                targetWeek = currentCourseActionWeek(facts, null, draft.course?.weekday)
            )?.let { course ->
                actions += AgentValidatedAction(
                    AgentValidatedActionType.ADD,
                    edited = scopedEdited(course).copy(id = 0, scheduleId = facts.scheduleId),
                    sourceWeeks = selectedWeeks,
                    scope = draft.scope,
                    targetWeek = currentCourseActionWeek(facts, null, draft.course?.weekday),
                    summary = draft.summary.ifBlank { "添加 ${course.name}" }
                )
            }
            AgentActionType.UPDATE_COURSE -> knownCourses[draft.courseId]?.let { original ->
                val targetWeek = currentCourseActionWeek(facts, original)
                validateAgentCoursePatch(draft.course, original, destinationFacts, validPeriods, draft.scope, targetWeek)?.let { edited ->
                    actions += AgentValidatedAction(
                        AgentValidatedActionType.UPDATE,
                        original = original,
                        edited = scopedEdited(edited).copy(id = original.id, scheduleId = facts.scheduleId),
                        sourceWeeks = selectedWeeks,
                        scope = draft.scope,
                        targetWeek = targetWeek,
                        summary = draft.summary.ifBlank { "修改 ${original.name}" }
                    )
                }
            }
            AgentActionType.REPLACE_COURSE -> knownCourses[draft.courseId]?.let { original ->
                val targetWeek = currentCourseActionWeek(facts, original)
                validateAgentCourseReplacement(draft.course, original, destinationFacts, validPeriods, targetWeek)?.let { edited ->
                    actions += AgentValidatedAction(
                        AgentValidatedActionType.REPLACE,
                        original = original,
                        edited = scopedEdited(edited).copy(id = original.id, scheduleId = facts.scheduleId),
                        sourceWeeks = selectedWeeks,
                        scope = draft.scope,
                        targetWeek = targetWeek,
                        summary = draft.summary.ifBlank { "整体替换 ${original.name}" }
                    )
                }
            }
            AgentActionType.DELETE_COURSE -> knownCourses[draft.courseId]?.let { original ->
                actions += AgentValidatedAction(
                    AgentValidatedActionType.DELETE,
                    original = original,
                    scope = draft.scope,
                    sourceWeeks = selectedWeeks,
                    targetWeek = currentCourseActionWeek(facts, original),
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
            AgentActionType.OPEN_IMPORT -> actions += AgentValidatedAction(
                AgentValidatedActionType.OPEN_IMPORT,
                importText = draft.importText?.takeIf { it.isNotBlank() && it.length <= 40_000 },
                targetWeek = facts.currentWeek,
                summary = draft.summary.ifBlank { "打开 AI 导入课表" }
            )
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
            AgentActionType.SET_ADJUSTMENTS -> validateAgentAdjustments(draft.adjustments)?.let { list ->
                actions += AgentValidatedAction(
                    type = AgentValidatedActionType.SET_ADJUSTMENTS,
                    adjustments = list,
                    targetWeek = facts.currentWeek,
                    summary = draft.summary.ifBlank { "更新调休安排" }
                )
            }
            AgentActionType.CREATE_SCHEDULE -> draft.name?.trim()?.takeIf { it.isNotBlank() }?.let { name ->
                actions += AgentValidatedAction(
                    type = AgentValidatedActionType.CREATE_SCHEDULE,
                    scheduleName = name.take(30),
                    targetWeek = facts.currentWeek,
                    summary = draft.summary.ifBlank { "新建课表 $name" }
                )
            }
            AgentActionType.ACTIVATE_SCHEDULE -> facts.schedules
                .firstOrNull { it.id == draft.scheduleId }
                ?.let { target ->
                    actions += AgentValidatedAction(
                        type = AgentValidatedActionType.ACTIVATE_SCHEDULE,
                        scheduleId = target.id,
                        targetWeek = facts.currentWeek,
                        summary = draft.summary.ifBlank { "切换到课表 ${target.name}" }
                    )
                }
            AgentActionType.DELETE_SCHEDULE -> facts.schedules
                .firstOrNull { it.id == draft.scheduleId }
                ?.let { target ->
                    actions += AgentValidatedAction(
                        type = AgentValidatedActionType.DELETE_SCHEDULE,
                        scheduleId = target.id,
                        targetWeek = facts.currentWeek,
                        summary = draft.summary.ifBlank { "删除课表 ${target.name}" }
                    )
                }
        }
    }
    val duplicateCourses = agentActionsHaveOverlappingCourseScopes(actions)
    val invalidPlan = payload != null && (drafts.isEmpty() || actions.size != drafts.size || duplicateCourses ||
        actions.any { action -> action.edited?.let { course ->
            course.weeks.any { it !in 1..plannedTotalWeeks } || course.weeks.none { parityMatches(course.weekParity, it) }
        } == true } ||
        AgentSettingRegistry.conflictingGroup(actions.mapNotNull { it.settingKey }) != null ||
        actions.count { it.type == AgentValidatedActionType.SET_ADJUSTMENTS } > 1 ||
        actions.count { it.type == AgentValidatedActionType.SET_PERIOD_SETTINGS } > 1)
    return if (invalidPlan) ParsedAgentActions(
        "$displayText\n\n这份操作计划包含无效、重复或无法一起执行的项目，未执行任何修改。请让助手重新生成完整计划。".trim(), emptyList()
    ) else ParsedAgentActions(displayText, actions.map { it.copy(sourceScheduleId = facts.scheduleId) })
}

/**
 * Whole-table 调休 replacement. Validation is delegated to the persistence layer's own
 * [encodeScheduleAdjustments] so the agent can never store a list the settings UI would reject
 * (duplicate dates, sourceDate equal to date, over-long labels, too many entries).
 */
private fun validateAgentAdjustments(
    drafts: List<AgentAdjustmentDraft>?
): List<ScheduleAdjustment>? {
    if (drafts == null) return null
    val list = drafts.map { draft ->
        val date = runCatching { LocalDate.parse(draft.date.trim()) }.getOrNull() ?: return null
        val sourceDate = draft.sourceDate
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() ?: return null }
        ScheduleAdjustment(date.toString(), sourceDate?.toString(), draft.label.trim())
    }
    return runCatching { encodeScheduleAdjustments(list) }.map { list.sortedBy { it.date } }.getOrNull()
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
    if (!Regex("\\\"type\\\"\\s*:\\s*\\\"(?:ADD_COURSE|UPDATE_COURSE|REPLACE_COURSE|DELETE_COURSE|OPEN_SETTINGS|OPEN_IMPORT|SET_SETTING|SET_PERIOD_SETTINGS|SET_ADJUSTMENTS|CREATE_SCHEDULE|ACTIVATE_SCHEDULE|DELETE_SCHEDULE)", RegexOption.IGNORE_CASE)
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

private fun currentCourseActionWeek(facts: DayAgentFacts, course: CourseEntity?, weekday: Int? = null): Int {
    if (!facts.todayIsAdjusted) return facts.currentWeek
    val teachingDate = facts.currentTeachingDate ?: return facts.currentWeek
    if (teachingDate == facts.date) return facts.currentWeek
    val isTodaySource = if (course != null) facts.today.any { it.course.id == course.id }
        else weekday == teachingDate.dayOfWeek.value
    return if (isTodaySource) facts.currentTeachingWeek else facts.currentWeek
}

private fun validateAgentCoursePatch(
    patch: AgentCoursePatch?,
    base: CourseEntity?,
    facts: DayAgentFacts,
    validPeriods: Set<Int>,
    scope: AgentActionScope,
    targetWeek: Int = facts.currentWeek
): CourseEntity? {
    patch ?: return null
    val name = patch.name?.trim()?.takeIf { it.isNotBlank() } ?: base?.name ?: return null
    val weekday = patch.weekday ?: base?.weekday ?: return null
    if (patch.periods?.any { it !in validPeriods } == true || patch.weeks?.any { it !in 1..facts.totalWeeks } == true) return null
    if (patch.weeks != null && patch.weeks.isEmpty()) return null
    val periods = (patch.periods ?: base?.periods.orEmpty()).distinct().sorted()
    val requestedWeeks = patch.weeks.orEmpty()
    val weeks = when {
        requestedWeeks.isNotEmpty() -> requestedWeeks
        base != null -> base.weeks
        scope == AgentActionScope.CURRENT_WEEK -> listOf(targetWeek)
        else -> (1..facts.totalWeeks).toList()
    }.distinct().sorted()
    if (weekday !in 1..7 || periods.isEmpty() || weeks.isEmpty()) return null
    val parity = patch.weekParity?.let { runCatching { WeekParity.valueOf(it.uppercase()) }.getOrNull() ?: return null }
        ?: base?.weekParity ?: WeekParity.ALL
    val cleared = patch.clearFields.orEmpty().map { wire ->
        val name = wire.trim()
        AgentClearableCourseField.entries.firstOrNull { it.wireName.equals(name, ignoreCase = true) } ?: return null
    }.toSet()
    val customRange = if (AgentClearableCourseField.CUSTOM_TIME in cleared) {
        null to null
    } else {
        normalizeAgentCustomTimeRange(
            start = patch.customStartTime,
            end = patch.customEndTime,
            base = base
        ) ?: return null
    }
    val color = normalizeAgentColor(patch.customColorArgb)
        .getOrElse { return null }
    fun teacher(): String? = when {
        AgentClearableCourseField.TEACHER in cleared -> null
        patch.teacher == null -> base?.teacher
        else -> patch.teacher.trim().takeIf { it.isNotBlank() } ?: base?.teacher
    }
    fun location(): String? = when {
        AgentClearableCourseField.LOCATION in cleared -> null
        patch.location == null -> base?.location
        else -> patch.location.trim().takeIf { it.isNotBlank() } ?: base?.location
    }
    fun note(): String? = when {
        AgentClearableCourseField.NOTE in cleared -> null
        patch.note == null -> base?.note
        else -> patch.note.trim().takeIf(String::isNotBlank)
    }
    fun colour(): Long? = color ?: base?.customColorArgb
    return if (base != null) {
        base.copy(
            name = name,
            teacher = teacher(),
            location = location(),
            weekday = weekday,
            periods = periods,
            weeks = weeks,
            weekParity = parity,
            // Omitted/null means unchanged; an explicit empty string or clearFields clears the note.
            note = note(),
            customStartTime = customRange.first,
            customEndTime = customRange.second,
            customColorArgb = colour(),
            scheduleId = facts.scheduleId
        )
    } else {
        CourseEntity(
            name = name,
            teacher = teacher(),
            location = location(),
            weekday = weekday,
            periods = periods,
            weeks = weeks,
            weekParity = parity,
            note = note(),
            customStartTime = customRange.first,
            customEndTime = customRange.second,
            customColorArgb = colour(),
            scheduleId = facts.scheduleId
        )
    }
}

/**
 * Whole-field replacement. Unlike [validateAgentCoursePatch], a `null` value here is meaningful:
 * teacher/location/note stay null (for new rows) or are explicitly cleared; the pair of custom
 * times and the colour keep the "null means preserve" rule so a full replacement cannot silently
 * wipe existing values the model did not see.
 */
private fun validateAgentCourseReplacement(
    patch: AgentCoursePatch?,
    base: CourseEntity?,
    facts: DayAgentFacts,
    validPeriods: Set<Int>,
    targetWeek: Int = facts.currentWeek
): CourseEntity? {
    patch ?: return null
    val name = patch.name?.trim()?.takeIf { it.isNotBlank() } ?: return null
    val weekday = patch.weekday ?: return null
    val periods = patch.periods?.distinct()?.sorted() ?: return null
    if (periods.any { it !in validPeriods } || patch.weeks?.any { it !in 1..facts.totalWeeks } == true) return null
    if (patch.weeks != null && patch.weeks.isEmpty()) return null
    val requestedWeeks = patch.weeks.orEmpty()
    val weeks = when {
        requestedWeeks.isNotEmpty() -> requestedWeeks
        base != null -> base.weeks
        else -> (1..facts.totalWeeks).toList()
    }.distinct().sorted()
    if (weekday !in 1..7 || periods.isEmpty() || weeks.isEmpty()) return null
    val parity = patch.weekParity?.let { runCatching { WeekParity.valueOf(it.uppercase()) }.getOrNull() ?: return null }
        ?: base?.weekParity ?: WeekParity.ALL
    val customRange = normalizeAgentCustomTimeRange(
        start = patch.customStartTime,
        end = patch.customEndTime,
        base = base
    ) ?: return null
    // Replacement treats missing note/teacher/location as explicit null (a full re-write), unlike
    // the patch path where absence means "preserve the base value".
    val note = patch.note?.trim()?.takeIf(String::isNotBlank)
    val teacher = patch.teacher?.trim()?.takeIf { it.isNotBlank() }
    val location = patch.location?.trim()?.takeIf { it.isNotBlank() }
    val color = normalizeAgentColor(patch.customColorArgb).getOrElse { return null }
    val baseEntity = base ?: CourseEntity(
        name = name,
        teacher = null,
        location = null,
        weekday = weekday,
        periods = periods,
        weeks = weeks,
        weekParity = parity,
        note = null,
        scheduleId = facts.scheduleId
    )
    return baseEntity.copy(
        name = name,
        teacher = teacher,
        location = location,
        weekday = weekday,
        periods = periods,
        weeks = weeks,
        weekParity = parity,
        note = note,
        customStartTime = customRange.first,
        customEndTime = customRange.second,
        customColorArgb = color ?: base?.customColorArgb,
        scheduleId = facts.scheduleId
    )
}

/**
 * Parses `#AARRGGBB` / `#RRGGBB` into a Long ARGB. Returns `null` (no change) only when the model
 * omitted the field entirely; an invalid present value is an error so a confirmed colour request
 * can never be stored as the wrong colour.
 */
private fun normalizeAgentColor(value: String?): Result<Long?> {
    val raw = value?.trim()
    if (raw == null || raw.isEmpty() || raw.equals("null", ignoreCase = true)) return Result.success(null)
    val parsed = when {
        raw.length == 9 && raw[0] == '#' -> raw.drop(1).toLongOrNull(16)
        raw.length == 7 && raw[0] == '#' -> ("FF" + raw.drop(1)).toLongOrNull(16)
        else -> null
    }
    return if (parsed == null) Result.failure(IllegalArgumentException("无效的自定义颜色: $raw"))
    else Result.success(parsed)
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
    // PERSONALIZATION is not a SettingsPage enum value: it opens the home-anchored personalization
    // overlay. It must still survive parsing, otherwise the prompt-advertised page is dropped
    // silently and the user never sees a confirmation card.
    "GENERAL", "PERSONALIZATION", "LIQUID_GLASS", "WIDGETS", "AI_IMPORT", "DAY_AGENT",
    "SCHEDULE", "SCHEDULE_ADJUSTMENTS", "NOTIFICATIONS", "SCHEDULE_MANAGER", "BACKUP_RESTORE", "ABOUT", "CHANGELOG",
    "DOWNLOAD", "DONATE", "PRIVACY_POLICY" -> value.trim().uppercase()
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
