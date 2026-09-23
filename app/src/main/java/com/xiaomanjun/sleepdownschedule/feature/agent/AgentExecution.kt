package com.xiaomanjun.sleepdownschedule.feature.agent

import com.xiaomanjun.sleepdownschedule.domain.course.*

import com.xiaomanjun.sleepdownschedule.*

/**
 * Deterministic Agent execution model. The language model proposes actions; this layer owns
 * simulation, collision detection and the success criteria used again inside the Room transaction.
 */
data class AgentPlan(
    val actions: List<AgentValidatedAction>
)

data class AgentCourseConflict(
    val first: CourseEntity,
    val second: CourseEntity,
    val weeks: List<Int>,
    val periods: List<Int>
)

data class AgentPlanPreview(
    val before: List<CourseEntity>,
    val after: List<CourseEntity>,
    val changedCourseCount: Int,
    val affectedWeeks: List<Int>,
    val newConflicts: List<AgentCourseConflict>
) {
    /*
     * A preview reports observable consequences; it must not infer whether a collision is
     * intentional or veto the model's complete plan. Database ownership/schema checks remain
     * hard safety boundaries at execution time.
     */
    val canExecute: Boolean get() = true
    val hasWarnings: Boolean get() = newConflicts.isNotEmpty()
}

data class AgentPlanExecutionResult(
    val success: Boolean,
    val preview: AgentPlanPreview?,
    val verified: Boolean,
    val message: String,
    val undo: (((AgentPlanExecutionResult) -> Unit) -> Unit)? = null
)

typealias AgentActionHandler =
    (AgentPlan, onResult: (AgentPlanExecutionResult) -> Unit) -> Unit

internal class AgentPlanRejectedException(message: String) : IllegalStateException(message)

internal fun AgentValidatedAction.sourceWeekSet(): Set<Int> = when (scope) {
    AgentActionScope.CURRENT_WEEK -> setOf(targetWeek)
    AgentActionScope.SELECTED_WEEKS -> sourceWeeks.toSet()
    AgentActionScope.ALL_WEEKS -> original?.weeks.orEmpty().toSet()
}

internal fun AgentValidatedAction.scopedEditedCourse(): CourseEntity? = edited?.let {
    if (scope == AgentActionScope.CURRENT_WEEK) it.copy(weeks = listOf(targetWeek)) else it
}

/** The same stored course can participate in several edits when their source weeks are disjoint. */
internal fun agentActionsHaveOverlappingCourseScopes(actions: List<AgentValidatedAction>): Boolean =
    actions.filter { it.original != null }.groupBy { it.original!!.id }.values.any { group ->
        if (group.size < 2) false
        else if (group.any { it.scope == AgentActionScope.ALL_WEEKS }) true
        else {
            val seen = mutableSetOf<Int>()
            group.any { action -> action.sourceWeekSet().any { !seen.add(it) } }
        }
    }

internal fun previewAgentPlan(
    before: List<CourseEntity>,
    plan: AgentPlan,
    periodDefinitions: List<PeriodEntity> = emptyList()
): AgentPlanPreview {
    val working = before.toMutableList()
    var temporaryId = -1L

    plan.actions.forEach { action ->
        when (action.type) {
            AgentValidatedActionType.ADD -> action.edited?.let { edited ->
                working += edited.copy(id = temporaryId--)
            }

            AgentValidatedActionType.UPDATE,
            AgentValidatedActionType.REPLACE -> {
                val original = action.original ?: return@forEach
                val edited = action.scopedEditedCourse() ?: return@forEach
                val index = working.indexOfFirst { it.id == original.id }
                if (index < 0) return@forEach
                if (action.scope != AgentActionScope.ALL_WEEKS) {
                    val remaining = working[index].weeks.filterNot { it in action.sourceWeekSet() }
                    if (remaining.isEmpty()) working.removeAt(index)
                    else working[index] = working[index].copy(weeks = remaining)
                    // Preserve the logical course id in simulation. This prevents an already
                    // existing conflict from being misclassified as new merely because the
                    // current-week edit will be stored as a physical fragment in Room.
                    working += edited.copy(id = original.id)
                } else {
                    working[index] = edited.copy(id = original.id)
                }
            }

            AgentValidatedActionType.DELETE -> {
                val original = action.original ?: return@forEach
                val index = working.indexOfFirst { it.id == original.id }
                if (index < 0) return@forEach
                if (action.scope != AgentActionScope.ALL_WEEKS) {
                    val remaining = working[index].weeks.filterNot { it in action.sourceWeekSet() }
                    if (remaining.isEmpty()) working.removeAt(index)
                    else working[index] = working[index].copy(weeks = remaining)
                } else {
                    working.removeAt(index)
                }
            }

            AgentValidatedActionType.OPEN_SETTINGS,
            AgentValidatedActionType.OPEN_IMPORT,
            AgentValidatedActionType.SET_SETTING,
            AgentValidatedActionType.SET_PERIOD_SETTINGS,
            AgentValidatedActionType.SET_ADJUSTMENTS,
            AgentValidatedActionType.CREATE_SCHEDULE,
            AgentValidatedActionType.ACTIVATE_SCHEDULE,
            AgentValidatedActionType.DELETE_SCHEDULE -> Unit
        }
    }

    val beforeConflicts = agentConflictKeys(findAgentCourseConflicts(before, periodDefinitions))
    val newConflicts = findAgentCourseConflicts(working, periodDefinitions)
        .filterNot { conflict -> agentConflictKey(conflict) in beforeConflicts }
    val changedIds = buildSet {
        plan.actions.forEach { action ->
            action.original?.id?.let(::add)
            action.edited?.id?.takeIf { it > 0 }?.let(::add)
        }
    }
    val affectedWeeks = plan.actions.flatMap { action ->
        when (action.scope) {
            AgentActionScope.CURRENT_WEEK -> listOf(action.targetWeek)
            AgentActionScope.SELECTED_WEEKS -> action.sourceWeeks + action.edited?.weeks.orEmpty()
            AgentActionScope.ALL_WEEKS ->
                (action.original?.weeks.orEmpty() + action.edited?.weeks.orEmpty())
        }
    }.distinct().sorted()

    return AgentPlanPreview(
        before = before,
        after = working,
        changedCourseCount = changedIds.size +
            plan.actions.count { it.type == AgentValidatedActionType.ADD },
        affectedWeeks = affectedWeeks,
        newConflicts = newConflicts
    )
}

internal fun verifyAgentPlan(
    actual: List<CourseEntity>,
    plan: AgentPlan,
    before: List<CourseEntity>? = null
): Boolean {
    if (before != null) return agentSemanticSchedule(actual) == agentSemanticSchedule(previewAgentPlan(before, plan).after)
    return plan.actions.all { action ->
    when (action.type) {
        AgentValidatedActionType.ADD -> action.edited?.let { expected ->
            actual.any { it.agentContentEquals(expected) }
        } ?: false

        AgentValidatedActionType.UPDATE,
        AgentValidatedActionType.REPLACE -> {
            val original = action.original
            val edited = action.edited
            if (original == null || edited == null) false
            else if (action.scope != AgentActionScope.ALL_WEEKS) {
                actual.any {
                    it.agentContentEquals(action.scopedEditedCourse()!!)
                } && actual.none {
                    it.id == original.id && it.weeks.any { week -> week in action.sourceWeekSet() }
                }
            } else {
                actual.firstOrNull { it.id == original.id }?.agentContentEquals(edited) == true
            }
        }

        AgentValidatedActionType.DELETE -> action.original?.let { original ->
            if (action.scope != AgentActionScope.ALL_WEEKS) {
                actual.none { it.id == original.id && it.weeks.any { week -> week in action.sourceWeekSet() } }
            } else {
                actual.none { it.id == original.id }
            }
        } ?: false

        AgentValidatedActionType.OPEN_SETTINGS,
        AgentValidatedActionType.OPEN_IMPORT,
        AgentValidatedActionType.SET_SETTING,
        AgentValidatedActionType.SET_PERIOD_SETTINGS,
        AgentValidatedActionType.SET_ADJUSTMENTS,
        AgentValidatedActionType.CREATE_SCHEDULE,
        AgentValidatedActionType.ACTIVATE_SCHEDULE,
        AgentValidatedActionType.DELETE_SCHEDULE -> true
    }
}
}

/** Fragment merging may retain any physical ID. Compare all content and teaching weeks instead. */
private fun agentSemanticSchedule(courses: List<CourseEntity>): Map<CourseEntity, Set<Int>> = courses
    .groupBy { it.copy(id = 0, weeks = emptyList(), periods = it.periods.distinct().sorted(), weekParity = WeekParity.ALL) }
    .mapValues { (_, rows) -> rows.flatMap { row -> row.weeks.filter { parityMatches(row.weekParity, it) } }.toSet() }
    .filterValues { it.isNotEmpty() }

private fun CourseEntity.agentContentEquals(other: CourseEntity): Boolean =
    name == other.name &&
        teacher == other.teacher &&
        location == other.location &&
        weekday == other.weekday &&
        periods.sorted() == other.periods.sorted() &&
        weeks.sorted() == other.weeks.sorted() &&
        weekParity == other.weekParity &&
        note == other.note &&
        customStartTime == other.customStartTime &&
        customEndTime == other.customEndTime &&
        customPeriodTimes == other.customPeriodTimes &&
        customColorArgb == other.customColorArgb &&
        scheduleId == other.scheduleId

private fun findAgentCourseConflicts(
    courses: List<CourseEntity>,
    periodDefinitions: List<PeriodEntity> = emptyList()
): List<AgentCourseConflict> {
    val result = mutableListOf<AgentCourseConflict>()
    for (firstIndex in courses.indices) {
        val first = courses[firstIndex]
        for (secondIndex in firstIndex + 1 until courses.size) {
            val second = courses[secondIndex]
            if (first.weekday != second.weekday) continue
            val weeks = first.activeAgentWeeks().intersect(second.activeAgentWeeks()).sorted()
            if (weeks.isEmpty()) continue
            val conflictingWeeks = weeks.filter { week ->
                first.conflictsWith(second, week, periodDefinitions)
            }
            if (conflictingWeeks.isEmpty()) continue
            // Exact-time courses can overlap even when their period anchors differ. Keep the
            // shared anchors for the normal case and expose both anchors for that diagnostic
            // case so the preview still tells the Agent which timetable rows are involved.
            val periods = first.periods.intersect(second.periods.toSet()).sorted().ifEmpty {
                (first.periods + second.periods).distinct().sorted()
            }
            result += AgentCourseConflict(first, second, conflictingWeeks, periods)
        }
    }
    return result
}

private fun CourseEntity.activeAgentWeeks(): Set<Int> = weeks.asSequence()
    .filter { week ->
        when (weekParity) {
            WeekParity.ALL -> true
            WeekParity.ODD -> week % 2 == 1
            WeekParity.EVEN -> week % 2 == 0
        }
    }
    .toSet()

private fun agentConflictKeys(conflicts: List<AgentCourseConflict>): Set<String> =
    conflicts.mapTo(hashSetOf(), ::agentConflictKey)

private fun agentConflictKey(conflict: AgentCourseConflict): String {
    val first = minOf(conflict.first.id, conflict.second.id)
    val second = maxOf(conflict.first.id, conflict.second.id)
    return "$first:$second:${conflict.weeks.joinToString(",")}:${conflict.periods.joinToString(",")}"
}
