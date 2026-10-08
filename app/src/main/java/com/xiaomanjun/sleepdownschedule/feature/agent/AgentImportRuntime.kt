package com.xiaomanjun.sleepdownschedule.feature.agent

import android.content.Context
import com.xiaomanjun.sleepdownschedule.feature.importing.*
import com.xiaomanjun.sleepdownschedule.model.ImportDraft
import com.xiaomanjun.sleepdownschedule.model.AgentMessageEntity
import java.time.LocalDate

/** Import is a draft workspace for the same Agent tools, scopes and local plan validator. */
internal object AgentImportRuntime {
    fun workspace(draft: ImportDraft): ImportDraft = draft.copy(courses = draft.courses.mapIndexed { index, course ->
        course.copy(id = index + 1L, scheduleId = draft.config.id)
    })

    fun applyPlan(draft: ImportDraft, answer: String, onlyCurrentWeek: Boolean = false): ImportDraft {
        val facts = buildDayAgentFacts(draft.courses, draft.periods, draft.config, LocalDate.now(), null)
        val parsed = parseAgentActions(answer, facts)
        require(parsed.validationErrors.isEmpty()) { parsed.validationErrors.joinToString("；") { it.message } }
        require(parsed.actions.all { it.type in courseActions }) { "导入草稿只能修改课程；应用设置请在今日助手中修改" }
        if (onlyCurrentWeek) require(parsed.actions.all { action ->
            (action.original == null || action.sourceWeekSet().all { it == facts.currentWeek }) &&
                action.scopedEditedCourse()?.weeks.orEmpty().all { it == facts.currentWeek }
        }) { "本次只允许修改第 ${facts.currentWeek} 周，请缩小操作范围；其他周必须保留" }
        val preview = previewAgentPlan(draft.courses, AgentPlan(parsed.actions), draft.periods)
        return draft.copy(courses = preview.after)
    }

    suspend fun revise(context: Context, draft: ImportDraft, instruction: String,
        history: AiEduImportProgress, settings: AiImportSettings, interaction: AiImportInteraction?,
        onReasoning: (String) -> Unit): AiScheduleImportResult {
        val workspace = workspace(draft)
        val facts = buildDayAgentFacts(workspace.courses, workspace.periods, workspace.config, LocalDate.now(), null)
        var reasoning = ""
        val onlyCurrentWeek = Regex("(?:仅|只)(?:调整|修改|改动|改)?(?:本|这)周").containsMatchIn(instruction.replace(" ", ""))
        val service = DayAgentService(context, interaction) { text -> reasoning = text; onReasoning(text) }
        val messages = (history.conversationTurns + AiEduImportConversationTurn(
            history.userPrompt, assistantMessage = history.assistantMessage)).flatMap { turn ->
            listOf("user" to turn.userPrompt, "assistant" to turn.assistantMessage).mapNotNull { (role, content) ->
                content.takeIf(String::isNotBlank)?.let {
                    AgentMessageEntity(scheduleId = workspace.config.id, sessionDate = facts.date.toString(),
                        role = role, content = it, createdAt = 0, status = "completed")
                }
            }
        }
        val answer = service.chat(facts, messages, instruction,
            onStatus = { status ->
                interaction?.checkActive()
                if (reasoning.isBlank()) onReasoning(status.detail ?: status.text)
            }, onDelta = {}, settingsOverride = settings,
            taskBoundary = "当前工作区是尚待用户确认的完整导入课表。IMPORT_SCHEDULE 已完成材料提取。" +
                "后续使用当前 Agent 的查询和 PROPOSE_ACTIONS 精细修改草稿，不能重新生成整张课表。" +
                "只允许 ADD_COURSE、UPDATE_COURSE、REPLACE_COURSE、DELETE_COURSE；其他应用操作不在此草稿内执行。" +
                "仅本周必须使用 CURRENT_WEEK，指定周使用 SELECTED_WEEKS；未提及的课程、字段、周次、节次全部保留。" +
                "缺少定位信息时直接向用户提问。结果只是待确认交付物，不得宣称已经保存。",
            answerConstraint = { candidate -> runCatching { applyPlan(workspace, candidate, onlyCurrentWeek) }.exceptionOrNull()?.message })
        interaction?.checkActive()
        val parsed = parseAgentActions(answer, facts)
        if (parsed.actions.isEmpty()) throw AiImportClarificationRequired(listOf(parsed.displayText.ifBlank {
            "请补充要修改的课程、周次和具体内容。"
        }))
        val revised = applyPlan(workspace, answer, onlyCurrentWeek)
        return AiScheduleImportResult(output = draftToPayload(revised).toString(),
            rawOutput = answer, reasoningOutput = reasoning,
            routeMessage = parsed.displayText)
    }

    private val courseActions = setOf(AgentValidatedActionType.ADD, AgentValidatedActionType.UPDATE,
        AgentValidatedActionType.REPLACE, AgentValidatedActionType.DELETE)
}
