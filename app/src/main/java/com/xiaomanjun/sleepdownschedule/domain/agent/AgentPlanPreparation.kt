package com.xiaomanjun.sleepdownschedule.domain.agent

import com.xiaomanjun.sleepdownschedule.model.*
import com.xiaomanjun.sleepdownschedule.model.agent.AgentActionScope
import com.xiaomanjun.sleepdownschedule.model.agent.AgentValidatedActionType
import com.xiaomanjun.sleepdownschedule.domain.schedule.hasCustomTime
import com.xiaomanjun.sleepdownschedule.domain.schedule.parityMatches
import com.xiaomanjun.sleepdownschedule.domain.schedule.originalArrangement
import com.xiaomanjun.sleepdownschedule.domain.schedule.projectCourseArrangements

/** Pure preparation against the snapshot read inside the committing Room transaction. */
internal data class PreparedAgentCoursePlan(
    val preview: AgentPlanPreview,
    val replacements: Map<Long, List<CourseEntity>>,
    val additions: List<CourseEntity>
)

internal fun prepareAgentCoursePlan(
    plan: AgentPlan,
    scheduleId: Int,
    before: List<CourseEntity>,
    config: ScheduleConfigEntity,
    periodDefinitions: List<PeriodEntity>
): PreparedAgentCoursePlan {
    require(plan.actions.all { it.sourceScheduleId == null || it.sourceScheduleId == scheduleId }) {
        "当前课表已切换，请基于新课表重新生成后续修改"
    }
    plan.actions.forEach { action ->
        if (
            action.type == AgentValidatedActionType.UPDATE ||
            action.type == AgentValidatedActionType.REPLACE ||
            action.type == AgentValidatedActionType.DELETE
        ) {
            val original = action.original
            val stored = original?.let { candidate ->
                before.firstOrNull { it.id == candidate.id }
            }
            if (stored == null) {
                throw AgentPlanRejectedException("操作对象不属于当前课表，已拒绝执行")
            }
            if (stored != original?.originalArrangement()) {
                throw AgentPlanRejectedException("课程在确认前已发生变化，请让 AI 基于最新课表重新生成操作")
            }
            if (action.scope != AgentActionScope.ALL_WEEKS) require(action.sourceWeekSet().let { weeks ->
                weeks.isNotEmpty() && weeks.all { it in stored.weeks && parityMatches(stored.weekParity, it) }
            }) { "所选周次没有这门课程，请重新读取开课周次" }
        }
    }
    require(!agentActionsHaveOverlappingCourseScopes(plan.actions)) {
        "同一课程的同一周有多份修改，请合并后再执行"
    }
    val periodIndexes = periodDefinitions.map { it.periodIndex }.toSet()
    require(plan.actions.mapNotNull { it.scopedEditedCourse() }.all { course ->
        val previous = before.firstOrNull { it.id == course.id }
        course.weekday in 1..7 && course.periods.isNotEmpty() &&
            (course.periods.all { it in periodIndexes } || course.periods == previous?.periods || course.hasCustomTime()) &&
            course.weeks.isNotEmpty() && course.weeks.all { it in 1..config.totalWeeks } &&
            course.weeks.any { parityMatches(course.weekParity, it) }
    }) { "课程星期、节次或周次无效，请修正完整计划" }
    val preview = previewAgentPlan(
        before = projectCourseArrangements(before, config, periodDefinitions),
        plan = plan,
        periodDefinitions = periodDefinitions
    )
    val replacements = plan.actions.filter { it.original != null }.groupBy { it.original!!.id }.mapValues { (id, actions) ->
        val original = before.first { it.id == id }
        actions.forEach { action ->
            require(action.sourcePeriodSet().isNotEmpty() && action.sourcePeriodSet().all { it in original.periods }) {
                "所选节次不属于原课程，请重新生成计划"
            }
        }
        agentCourseFragments(original, actions, periodDefinitions)
    }
    return PreparedAgentCoursePlan(preview, replacements,
        plan.actions.filter { it.type == AgentValidatedActionType.ADD }.mapNotNull { it.edited })
}
