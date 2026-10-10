package com.xiaomanjun.sleepdownschedule.feature.agent

import android.content.Context
import com.xiaomanjun.sleepdownschedule.feature.importing.*
import com.xiaomanjun.sleepdownschedule.model.ImportDraft
import com.xiaomanjun.sleepdownschedule.model.AgentMessageEntity
import java.time.LocalDate

/** Import is a draft workspace for the same Agent tools, scopes and local plan validator. */
internal object AgentImportRuntime {
    fun workspace(draft: ImportDraft): ImportDraft = draft.copy(
        periods = draft.periods.map { it.copy(scheduleId = draft.config.id) },
        courses = draft.courses.mapIndexed { index, course ->
            course.copy(id = index + 1L, scheduleId = draft.config.id,
                periods = course.periods.toList(), weeks = course.weeks.toList())
        })

    fun applyPlan(draft: ImportDraft, answer: String, onlyCurrentWeek: Boolean = false): ImportDraft {
        val facts = buildDayAgentFacts(draft.courses, draft.periods, draft.config, LocalDate.now(), null)
        val parsed = parseAgentActions(answer, facts)
        require(parsed.validationErrors.isEmpty()) { parsed.validationErrors.joinToString("；") { it.message } }
        require(parsed.actions.all { it.type in courseActions }) { "导入草稿只能修改课程；应用设置请在今日助手中修改" }
        if (parsed.actions.isEmpty()) return draft
        if (onlyCurrentWeek) require(parsed.actions.all { action ->
            (action.original == null || action.sourceWeekSet().all { it == facts.currentWeek }) &&
                action.scopedEditedCourse()?.weeks.orEmpty().all { it == facts.currentWeek }
        }) { "本次只允许修改第 ${facts.currentWeek} 周，请缩小操作范围；其他周必须保留" }
        val preview = previewAgentPlan(draft.courses, AgentPlan(parsed.actions), draft.periods)
        val candidate = draft.copy(courses = preview.after)
        ScheduleImportParser.validateEditedDraft(candidate)
        return candidate
    }

    suspend fun revise(context: Context, draft: ImportDraft, instruction: String,
        history: AiEduImportProgress, settings: AiImportSettings, interaction: AiImportInteraction?,
        onReasoning: (String) -> Unit,
        onCheckpoint: (ImportDraft, String) -> Unit = { _, _ -> }): AiScheduleImportResult {
        val workspace = workspace(draft)
        val facts = buildDayAgentFacts(workspace.courses, workspace.periods, workspace.config, LocalDate.now(), null)
        var reasoning = ""
        val onlyCurrentWeek = Regex("(?:仅|只)(?:调整|修改|改动|改)?(?:本|这)周").containsMatchIn(instruction.replace(" ", ""))
        val jsonWorkspace = AgentImportWorkspace(workspace, onlyCurrentWeek, onCheckpoint)
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
                interaction?.publishActivity(status.text)
                if (reasoning.isBlank()) onReasoning(status.detail ?: status.text)
            }, onDelta = {}, settingsOverride = settings,
            taskBoundary = "当前工作区是尚待用户确认的完整导入课表。IMPORT_SCHEDULE 已完成材料提取，不重复 OCR 或上传原文件。" +
                "你继续使用同一 Agent 的任务循环读取、编辑、校验结构化 JSON；该工作区专用工具及以下规则优先于普通课表操作协议。" +
                "按用户目标完成所有必要阶段，每次成功编辑都会生成可选完整版本；不能在仅完成第一个阶段后提前结束。" +
                "只允许导入草稿编辑，禁止应用设置、日历、数据库、记忆或联网搜索操作。" +
                "未提及的课程、字段、周次、节次和真实来源铃声全部保留。" +
                "缺少定位信息时直接向用户提问。结果只是待确认交付物，不得宣称已经保存。",
            importWorkspace = jsonWorkspace)
        interaction?.checkActive()
        val finalFacts = buildDayAgentFacts(jsonWorkspace.draft.courses, jsonWorkspace.draft.periods,
            jsonWorkspace.draft.config, facts.date, null)
        val parsed = parseAgentActions(answer, finalFacts)
        if (parsed.actions.isNotEmpty()) jsonWorkspace.applyAnswer(answer, parsed.displayText.ifBlank { "已校验课程修改" })
        if (jsonWorkspace.revision == 0) throw AiImportClarificationRequired(listOf(parsed.displayText.ifBlank {
            "请补充要修改的课程、周次和具体内容。"
        }))
        val revised = jsonWorkspace.draft
        return AiScheduleImportResult(output = draftToPayload(revised).toString(),
            rawOutput = answer, reasoningOutput = reasoning,
            routeMessage = parsed.displayText)
    }

    private val courseActions = setOf(AgentValidatedActionType.ADD, AgentValidatedActionType.UPDATE,
        AgentValidatedActionType.REPLACE, AgentValidatedActionType.DELETE)
}
