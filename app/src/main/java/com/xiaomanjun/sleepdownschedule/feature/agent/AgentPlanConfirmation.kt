package com.xiaomanjun.sleepdownschedule.feature.agent

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xiaomanjun.sleepdownschedule.*
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.ChangePreviewBadge
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.ChangePreviewCard
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.ChangePreviewField
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.ChangePreviewTone
import com.xiaomanjun.sleepdownschedule.domain.agent.AgentPlan
import com.xiaomanjun.sleepdownschedule.domain.agent.AgentPlanPreview
import com.xiaomanjun.sleepdownschedule.domain.agent.agentPeriodFragment
import com.xiaomanjun.sleepdownschedule.domain.agent.scopedEditedCourse
import com.xiaomanjun.sleepdownschedule.domain.agent.sourcePeriodSet
import com.xiaomanjun.sleepdownschedule.domain.agent.sourceWeekSet
import com.xiaomanjun.sleepdownschedule.model.agent.AgentActionScope
import com.xiaomanjun.sleepdownschedule.model.agent.AgentValidatedAction
import com.xiaomanjun.sleepdownschedule.model.agent.AgentValidatedActionType

internal enum class AgentChangeKind(val label: String) {
    MOVE("移动"), DELETE("删除"), RENAME("重命名"), ADD("新增"), EDIT("修改"), OPEN("打开"), SWITCH("切换")
}

internal fun agentChangeKinds(action: AgentValidatedAction): List<AgentChangeKind> = when (action.type) {
    AgentValidatedActionType.ADD, AgentValidatedActionType.CREATE_SCHEDULE -> listOf(AgentChangeKind.ADD)
    AgentValidatedActionType.DELETE, AgentValidatedActionType.DELETE_SCHEDULE -> listOf(AgentChangeKind.DELETE)
    AgentValidatedActionType.OPEN_SETTINGS, AgentValidatedActionType.OPEN_IMPORT -> listOf(AgentChangeKind.OPEN)
    AgentValidatedActionType.ACTIVATE_SCHEDULE -> listOf(AgentChangeKind.SWITCH)
    AgentValidatedActionType.UPDATE, AgentValidatedActionType.REPLACE -> buildList {
        val before = action.original?.let { original ->
            if (action.sourcePeriods.isEmpty()) original else original.agentPeriodFragment(action.sourcePeriods)
        }
        val after = action.edited
        if (before != null && after != null) {
            if (before.name != after.name) add(AgentChangeKind.RENAME)
            if (before.weekday != after.weekday || action.sourcePeriodSet() != after.periods.toSet() ||
                action.sourceWeekSet() != after.weeks.toSet() || before.customStartTime != after.customStartTime ||
                before.customEndTime != after.customEndTime) add(AgentChangeKind.MOVE)
            if (before.teacher != after.teacher || before.location != after.location || before.note != after.note ||
                before.customColorArgb != after.customColorArgb || before.weekParity != after.weekParity) add(AgentChangeKind.EDIT)
        }
        if (isEmpty()) add(AgentChangeKind.EDIT)
    }
    AgentValidatedActionType.SET_SETTING -> listOf(if (action.settingKey == "SCHEDULE_NAME") AgentChangeKind.RENAME else AgentChangeKind.EDIT)
    else -> listOf(AgentChangeKind.EDIT)
}

internal data class AgentChangeRow(val label: String, val before: String?, val after: String)

internal fun agentScopeDescription(action: AgentValidatedAction): String {
    val weeks = when (action.scope) {
        AgentActionScope.CURRENT_WEEK -> "仅第${action.targetWeek}周"
        AgentActionScope.SELECTED_WEEKS -> "仅第${action.sourceWeeks.joinToString("、")}周"
        AgentActionScope.ALL_WEEKS -> "全部开课周次"
    }
    return weeks + if (action.sourcePeriods.isNotEmpty()) " · 仅第${action.sourcePeriods.joinToString("、")}节" else " · 整条课程安排"
}

internal fun agentChangeRows(action: AgentValidatedAction, facts: DayAgentFacts): List<AgentChangeRow> = buildList {
    val before = action.original
    val after = action.edited
    fun field(label: String, old: String?, new: String?) {
        if (old != new) add(AgentChangeRow(label, old?.ifBlank { "未设置" } ?: "未设置", new?.ifBlank { "清空" } ?: "清空"))
    }
    if (before != null || after != null) {
        add(AgentChangeRow("生效范围", null, if (before == null) "第${after?.weeks?.joinToString("、")}周" else agentScopeDescription(action)))
        if (before != null) {
            val dates = (facts.today + facts.tomorrow + facts.week).filter {
                it.course.id == before.id && (it.teachingWeek ?: facts.currentWeek) in action.sourceWeekSet()
            }.map { it.date }.distinct().sorted()
            if (dates.isNotEmpty()) add(AgentChangeRow("已定位日期", null, dates.joinToString("、")))
        }
        val source = before?.let { if (action.sourcePeriods.isEmpty()) it else it.agentPeriodFragment(action.sourcePeriods) }
        if (action.type == AgentValidatedActionType.DELETE) {
            add(AgentChangeRow("课程安排", agentCourseSlotText(source), "删除所选安排"))
        } else if (after != null) {
            if (before == null) add(AgentChangeRow("课程安排", null, agentCourseSlotText(after)))
            else {
                field("名称", before.name, after.name)
                field("星期与时间", agentCourseSlotText(source), agentCourseSlotText(after))
                val targetWeeks = action.scopedEditedCourse()?.weeks.orEmpty().toSet()
                if (action.sourceWeekSet() != targetWeeks) field("周次", action.sourceWeekSet().sorted().joinToString("、"), targetWeeks.sorted().joinToString("、"))
            }
            field("教师", before?.teacher, after.teacher)
            field("地点", before?.location, after.location)
            field("备注", before?.note, after.note)
            if (before != null && before.weekParity != after.weekParity) field("单双周", parityLabel(before.weekParity), parityLabel(after.weekParity))
            if (before?.customColorArgb != after.customColorArgb) add(AgentChangeRow("课程颜色", null, "使用新颜色"))
        }
        if (before != null && (action.scope != AgentActionScope.ALL_WEEKS || action.sourcePeriods.isNotEmpty())) {
            add(AgentChangeRow("保留", null, "未选中的周次和节次保持原安排"))
        }
    } else when (action.type) {
        AgentValidatedActionType.SET_SETTING -> if (action.settingKey == "TIME_SLOT_COURSES") {
            val old = facts.settingSnapshot[action.settingKey].orEmpty().let { if (it == "[]") "" else it }
            val updated = action.settingValue?.let { applySpecialCoursePatch(old, it) }
            add(AgentChangeRow("特殊课程", specialCourseSummary(old), updated?.let(::specialCourseSummary) ?: "补丁未通过校验"))
        } else add(AgentChangeRow(
            AgentSettingRegistry.definitions.firstOrNull { it.key == action.settingKey }?.description ?: "节次时间",
            agentReadableSetting(facts.settingSnapshot[action.settingKey]), agentReadableSetting(action.settingValue)))
        AgentValidatedActionType.SET_PERIOD_SETTINGS -> action.periodSettings?.let { patch ->
            patch.schemeName?.let { add(AgentChangeRow("作息方案", null, it)) }
            listOf("上午节数" to patch.morningPeriodCount, "中午节数" to patch.noonPeriodCount,
                "下午节数" to patch.afternoonPeriodCount, "晚间节数" to patch.eveningPeriodCount,
                "每节时长（分钟）" to patch.classDurationMinutes, "课间（分钟）" to patch.breakDurationMinutes)
                .forEach { (label, value) -> value?.let { add(AgentChangeRow(label, null, it.toString())) } }
            listOf("上午开始" to patch.morningStartTime, "中午开始" to patch.noonStartTime,
                "下午开始" to patch.afternoonStartTime, "晚间开始" to patch.eveningStartTime)
                .forEach { (label, value) -> value?.let { add(AgentChangeRow(label, null, it)) } }
            patch.periods?.forEach { period ->
                val old = facts.periodDefinitions.firstOrNull { it.periodIndex == period.periodIndex }
                add(AgentChangeRow("第${period.periodIndex}节", old?.let { "${it.startTime}–${it.endTime}" }, "${period.startTime}–${period.endTime}"))
            }
            patch.specialBreaks?.let { add(AgentChangeRow("特殊课间", null, if (it.isEmpty()) "清空" else it.entries.joinToString("；") { (period, minutes) -> "第${period}节后 ${minutes}分钟" })) }
            patch.overriddenPeriods?.let { add(AgentChangeRow("手动调整节次", null, it.joinToString("、").ifEmpty { "无" })) }
            patch.mode?.let { add(AgentChangeRow("作息模式", null, if (it == "AUTO_MATCH") "自动匹配" else "手动设置")) }
        }
        AgentValidatedActionType.SET_ADJUSTMENTS -> add(AgentChangeRow("完整调休安排",
            agentAdjustmentDescription(facts.scheduleAdjustments), agentAdjustmentDescription(action.adjustments.orEmpty())))
        AgentValidatedActionType.CREATE_SCHEDULE -> add(AgentChangeRow("课表名称", null, action.scheduleName.orEmpty()))
        AgentValidatedActionType.ACTIVATE_SCHEDULE, AgentValidatedActionType.DELETE_SCHEDULE -> add(AgentChangeRow("目标课表", null,
            facts.schedules.firstOrNull { it.id == action.scheduleId }?.name ?: action.summary))
        AgentValidatedActionType.OPEN_IMPORT -> add(AgentChangeRow("下一步", null, "打开导入预览，核对课程后再保存"))
        AgentValidatedActionType.OPEN_SETTINGS -> add(AgentChangeRow("目标页面", null, agentSettingsPageLabel(action.settingsPage)))
        else -> Unit
    }
}

private fun agentAdjustmentDescription(adjustments: List<com.xiaomanjun.sleepdownschedule.domain.schedule.ScheduleAdjustment>): String =
    adjustments.joinToString("\n") { "${it.date}：" + (it.sourceDate?.let { date -> "补 $date 的课" } ?: "停课") +
        it.label.takeIf(String::isNotBlank)?.let { label -> "（$label）" }.orEmpty() }.ifBlank { "无调休安排" }

private fun agentReadableSetting(value: String?): String = when (value?.uppercase()) {
    "TRUE" -> "开启"; "FALSE" -> "关闭"; "LIGHT" -> "浅色"; "DARK" -> "深色"
    "DAY" -> "日视图"; "WEEK" -> "周视图"; "LIVE_UPDATE" -> "实时活动"; "STANDARD" -> "普通通知"
    else -> value ?: "未设置"
}

private fun agentSettingsPageLabel(page: String?): String = mapOf(
    "GENERAL" to "通用设置", "PERSONALIZATION" to "首页外观", "LIQUID_GLASS" to "液态玻璃", "WIDGETS" to "小组件",
    "AI_IMPORT" to "AI 模型设置", "DAY_AGENT" to "AI 助理", "SCHEDULE" to "课表设置", "SCHEDULE_ADJUSTMENTS" to "调休安排",
    "NOTIFICATIONS" to "课程提醒", "SCHEDULE_MANAGER" to "多课表管理", "BACKUP_RESTORE" to "备份与恢复",
    "ABOUT" to "关于", "CHANGELOG" to "更新日志", "DOWNLOAD" to "下载", "DONATE" to "捐赠", "PRIVACY_POLICY" to "隐私政策"
)[page] ?: "设置"

@Composable
internal fun AgentPlanConfirmation(plan: AgentPlan, preview: AgentPlanPreview, facts: DayAgentFacts, foreground: Color, applied: Boolean = false) {
    val dark = foreground.luminance() > 0.5f
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(if (applied) "已保存的变更" else "待确认变更", color = foreground, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        plan.actions.forEach { action ->
            val title = action.original?.name ?: action.edited?.name ?: when (action.type) {
                AgentValidatedActionType.SET_SETTING -> AgentSettingRegistry.definitions.firstOrNull { it.key == action.settingKey }?.description ?: "节次时间"
                AgentValidatedActionType.SET_PERIOD_SETTINGS -> "节次与作息"
                AgentValidatedActionType.SET_ADJUSTMENTS -> "停课与补课"
                AgentValidatedActionType.ACTIVATE_SCHEDULE, AgentValidatedActionType.DELETE_SCHEDULE ->
                    facts.schedules.firstOrNull { it.id == action.scheduleId }?.name ?: "目标课表"
                AgentValidatedActionType.OPEN_IMPORT -> "导入课程"
                else -> action.scheduleName ?: agentSettingsPageLabel(action.settingsPage)
            }
            ChangePreviewCard(title, foreground, badges = {
                agentChangeKinds(action).forEach { kind ->
                    val tone = when (kind) {
                        AgentChangeKind.MOVE -> ChangePreviewTone.Move
                        AgentChangeKind.DELETE -> ChangePreviewTone.Remove
                        AgentChangeKind.RENAME -> ChangePreviewTone.Rename
                        AgentChangeKind.ADD -> ChangePreviewTone.Add
                        else -> ChangePreviewTone.Edit
                    }
                    ChangePreviewBadge(kind.label, tone, foreground)
                }
            }) {
                agentChangeRows(action, facts).forEach { row ->
                    ChangePreviewField(row.label, row.before, row.after, foreground)
                }
            }
        }
        preview.newConflicts.forEach { conflict ->
            Text("时间重叠：${conflict.first.name} 与 ${conflict.second.name}，第${conflict.weeks.joinToString("、")}周，第${conflict.periods.joinToString("、")}节。",
                color = if (dark) Color(0xFFFFD36B) else Color(0xFF856100), style = MaterialTheme.typography.bodyMedium)
        }
    }
}
