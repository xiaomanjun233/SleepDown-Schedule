package com.xiaomanjun.sleepdownschedule.feature.agent

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiaomanjun.sleepdownschedule.CourseEntity
import com.xiaomanjun.sleepdownschedule.R
import com.xiaomanjun.sleepdownschedule.domain.agent.AgentPlan
import com.xiaomanjun.sleepdownschedule.model.agent.AgentValidatedAction
import com.xiaomanjun.sleepdownschedule.model.agent.AgentValidatedActionType

private fun agentActionButtonLabel(action: AgentValidatedAction): String =
    "确认" + agentChangeKinds(action).joinToString("并") { it.label }

internal data class AgentMessageParts(
    val reasoning: String,
    val toolResults: List<String>,
    val answer: String,
    val statuses: List<AgentRunStatus> = emptyList()
)

internal fun splitAgentReasoning(content: String): AgentMessageParts {
    val storedMessage = parseAgentStoredMessage(content)
    val visibleContent = storedMessage.content
    val complete = Regex(
        "<(?:think|thinking)[^>]*>([\\s\\S]*?)</(?:think|thinking)>",
        RegexOption.IGNORE_CASE
    )
    val taggedReasoning = complete.findAll(visibleContent)
        .map { it.groupValues[1].trim() }
        .filter(String::isNotBlank)
        .joinToString("\n")
    val toolPrelude = extractAgentToolPrelude(visibleContent)
        .replace(complete, "")
        .trim()
    val reasoning = listOf(taggedReasoning, toolPrelude)
        .filter(String::isNotBlank)
        .distinct()
        .joinToString("\n")
    val withoutComplete = sanitizeAgentToolOutput(visibleContent)
        .replace(complete, "")
        .trim()
    val open = Regex("<(?:think|thinking)[^>]*>", RegexOption.IGNORE_CASE)
        .find(withoutComplete)
    return if (open != null) {
        AgentMessageParts(
            reasoning = listOf(reasoning, withoutComplete.substring(open.range.last + 1).trim())
                .filter(String::isNotBlank)
                .joinToString("\n"),
            toolResults = emptyList(),
            answer = withoutComplete.substring(0, open.range.first).trim(),
            statuses = storedMessage.statuses
        )
    } else {
        AgentMessageParts(
            reasoning = reasoning,
            toolResults = emptyList(),
            answer = withoutComplete,
            statuses = storedMessage.statuses
        )
    }
}

@Composable
internal fun AgentRunTrace(
    statuses: List<AgentRunStatus>,
    expanded: Boolean,
    foreground: Color,
    active: Boolean,
    onToggle: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onToggle
            ),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        if (expanded) {
            statuses.forEachIndexed { index, status ->
                AgentRunStatusRow(
                    status = status,
                    foreground = foreground,
                    shimmer = active && index == statuses.lastIndex
                )
            }
        } else {
            AgentRunStatusRow(
                // Folding is only a presentation choice. It must never manufacture a completed
                // state while the request is still thinking or executing a tool.
                status = if (!active && statuses.isNotEmpty()) {
                    AgentRunStatus(
                        statuses.last().icon,
                        "已完成 ${statuses.size} 个处理步骤 · 点击回看"
                    )
                } else {
                    statuses.lastOrNull()
                        ?: AgentRunStatus(AgentRunStatusIcon.THINKING, "正在思考")
                },
                foreground = foreground,
                shimmer = active
            )
        }
    }
}

@Composable
internal fun AgentRunStatusRow(
    status: AgentRunStatus,
    foreground: Color,
    shimmer: Boolean = false
) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(16.dp), contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(agentRunStatusIcon(status.icon)),
                    contentDescription = null,
                    tint = Color(0xFF55C2FF).copy(
                        alpha = if (status.icon == AgentRunStatusIcon.THINKING) 0.68f else 0.86f
                    ),
                    modifier = Modifier.size(15.dp)
                )
            }
            AgentStatusText(
                text = if (status.icon == AgentRunStatusIcon.THINKING || status.text.startsWith("已完成")) {
                    status.text
                } else {
                    "调用工具 · ${status.text}"
                },
                baseColor = foreground.copy(alpha = if (status.icon == AgentRunStatusIcon.THINKING) 0.43f else 0.57f),
                shimmer = shimmer
            )
        }
        status.detail?.takeIf(String::isNotBlank)?.let { note ->
            Text(
                text = note,
                modifier = Modifier.padding(start = 19.dp, end = 6.dp),
                color = foreground.copy(alpha = 0.84f),
                style = MaterialTheme.typography.bodyMedium,
                lineHeight = 20.sp
            )
        }
    }
}

@Composable
private fun AgentStatusText(
    text: String,
    baseColor: Color,
    shimmer: Boolean
) {
    if (!shimmer) {
        Text(
            text = text,
            color = baseColor,
            style = MaterialTheme.typography.labelMedium
        )
        return
    }
    var textWidth by remember(text) { mutableIntStateOf(1) }
    val transition = rememberInfiniteTransition(label = "agent-status-shimmer")
    val phase by transition.animateFloat(
        initialValue = -0.85f,
        targetValue = 1.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 1_550,
                delayMillis = 180,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "agent-status-shimmer-phase"
    )
    val width = textWidth.coerceAtLeast(1).toFloat()
    val brush = Brush.linearGradient(
        colors = listOf(
            baseColor,
            baseColor,
            Color.White.copy(alpha = 0.96f),
            baseColor,
            baseColor
        ),
        start = Offset((phase - 0.42f) * width, 0f),
        end = Offset((phase + 0.42f) * width, 0f)
    )
    Text(
        text = text,
        modifier = Modifier.onSizeChanged { textWidth = it.width.coerceAtLeast(1) },
        color = Color.Unspecified,
        style = MaterialTheme.typography.labelMedium.copy(brush = brush)
    )
}

@Composable
internal fun AgentReasoningTrace(
    reasoning: String,
    foreground: Color,
    forceCollapsed: Boolean = true
) {
    var expanded by remember { mutableStateOf(!forceCollapsed) }
    LaunchedEffect(forceCollapsed) {
        if (forceCollapsed) expanded = false
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { expanded = !expanded },
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        AgentRunStatusRow(
            status = AgentRunStatus(
                AgentRunStatusIcon.THINKING,
                if (expanded) "收起处理摘要" else "查看处理摘要"
            ),
            foreground = foreground
        )
        if (expanded) {
            AgentMarkdownText(
                markdown = reasoning,
                color = foreground.copy(alpha = 0.38f),
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

private fun agentRunStatusIcon(icon: AgentRunStatusIcon): Int = when (icon) {
    AgentRunStatusIcon.OVERVIEW -> R.drawable.ic_agent_overview
    AgentRunStatusIcon.SEARCH -> R.drawable.ic_agent_search
    AgentRunStatusIcon.SCHEDULE -> R.drawable.ic_agent_schedule
    AgentRunStatusIcon.PERIOD -> R.drawable.ic_agent_period
    AgentRunStatusIcon.SETTINGS -> R.drawable.ic_agent_settings
    AgentRunStatusIcon.THINKING -> R.drawable.ic_agent_thinking
}

/** Actions that go through the setting execution channel rather than the course transaction. */
private fun AgentValidatedAction.isSettingAction(): Boolean =
    type == AgentValidatedActionType.SET_SETTING ||
        type == AgentValidatedActionType.SET_PERIOD_SETTINGS ||
        type == AgentValidatedActionType.SET_ADJUSTMENTS

internal fun agentPlanSummary(plan: AgentPlan): String =
    if (plan.actions.size == 1) {
        plan.actions.first().summary
    } else if (plan.actions.all { it.isSettingAction() }) {
        if (plan.actions.all {
                it.type == AgentValidatedActionType.SET_PERIOD_SETTINGS ||
                    AgentSettingRegistry.isPeriodTimeSetting(it.settingKey)
            }) {
            "${plan.actions.size} 项节次设置"
        } else {
            "${plan.actions.size} 项设置"
        }
    } else {
        "${plan.actions.size} 项操作"
    }

internal fun agentPlanButtonLabel(plan: AgentPlan): String =
    if (plan.actions.size == 1) agentActionButtonLabel(plan.actions.first())
    else "确认" + plan.actions.flatMap(::agentChangeKinds).groupingBy { it }.eachCount()
        .entries.joinToString("、") { (kind, count) -> "${kind.label}${count}项" }

internal fun agentCourseSlotText(course: CourseEntity?): String {
    if (course == null) return ""
    val periods = course.periods.sorted()
    val periodText = when {
        periods.isEmpty() -> "未设置节次"
        periods.size == 1 -> "第${periods.first()}节"
        else -> "第${periods.joinToString("、")}节"
    }
    val weekday = "一二三四五六日".getOrNull(course.weekday - 1)?.toString()
        ?: course.weekday.toString()
    val time = if (course.customStartTime != null && course.customEndTime != null) {
        "${course.customStartTime}–${course.customEndTime}"
    } else periodText
    return "周$weekday $time"
}
