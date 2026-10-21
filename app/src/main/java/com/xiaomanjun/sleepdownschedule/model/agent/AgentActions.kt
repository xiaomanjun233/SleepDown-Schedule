package com.xiaomanjun.sleepdownschedule.model.agent

import com.xiaomanjun.sleepdownschedule.*
import com.xiaomanjun.sleepdownschedule.domain.schedule.ScheduleAdjustment
import kotlinx.serialization.Serializable

@Serializable
enum class AgentActionScope { CURRENT_WEEK, SELECTED_WEEKS, ALL_WEEKS }

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
    val sourcePeriods: List<Int> = emptyList(),
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
