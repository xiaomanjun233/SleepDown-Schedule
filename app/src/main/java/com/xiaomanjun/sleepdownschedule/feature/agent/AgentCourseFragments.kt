package com.xiaomanjun.sleepdownschedule.feature.agent

import com.xiaomanjun.sleepdownschedule.*
import com.xiaomanjun.sleepdownschedule.domain.schedule.parseCoursePeriodTimes
import com.xiaomanjun.sleepdownschedule.domain.schedule.encodeCoursePeriodTimes
import com.xiaomanjun.sleepdownschedule.domain.schedule.originalArrangement

/** Shared by preview and Room execution. A selection is a set of original week/period cells. */
internal fun AgentValidatedAction.sourcePeriodSet(): Set<Int> =
    sourcePeriods.takeIf { it.isNotEmpty() }?.toSet() ?: original?.originalArrangement()?.periods.orEmpty().toSet()

internal fun CourseEntity.agentPeriodFragment(selected: List<Int>): CourseEntity {
    val periods = selected.distinct().sorted()
    require(periods.isNotEmpty() && periods.all { it in this.periods }) { "所选节次不属于原课程" }
    if (periods == this.periods.distinct().sorted()) return this
    val bells = parseCoursePeriodTimes(customPeriodTimes)
    require((customStartTime == null && customEndTime == null) || bells.isNotEmpty()) {
        "课程只有整体自定义时间，无法推算单节起止时间；请修改整体课程或先明确时间"
    }
    val selectedBells = bells.filter { it.index in periods }
    val completeCustom = selectedBells.map { it.index } == periods
    return copy(
        periods = periods,
        customStartTime = selectedBells.firstOrNull()?.start?.toString()?.takeIf { completeCustom },
        customEndTime = selectedBells.lastOrNull()?.end?.toString()?.takeIf { completeCustom },
        originalPeriodTimes = originalPeriodTimes?.let { encodeCoursePeriodTimes(parseCoursePeriodTimes(it).filter { time -> time.index in periods }) ?: "" },
        arrangementProjection = null,
        customPeriodTimes = selectedBells.takeIf { it.isNotEmpty() }?.joinToString(";") { "${it.index},${it.start}-${it.end}" }
    )
}

internal fun agentCourseFragments(original: CourseEntity, actions: List<AgentValidatedAction>): List<CourseEntity> {
    val remainingByPeriods = linkedMapOf<List<Int>, MutableList<Int>>()
    original.weeks.forEach { week ->
        val removed = actions.filter { week in it.sourceWeekSet() &&
                (it.scope == AgentActionScope.ALL_WEEKS || parityMatches(original.weekParity, week)) }
            .flatMap { it.sourcePeriodSet() }.toSet()
        val kept = original.periods.filterNot { it in removed }.distinct().sorted()
        if (kept.isNotEmpty()) remainingByPeriods.getOrPut(kept) { mutableListOf() }.add(week)
    }
    return buildList {
        remainingByPeriods.forEach { (periods, weeks) -> add(original.agentPeriodFragment(periods).copy(weeks = weeks)) }
        actions.filter { it.type == AgentValidatedActionType.UPDATE || it.type == AgentValidatedActionType.REPLACE }
            .mapNotNull { it.scopedEditedCourse() }.forEach(::add)
    }
}
