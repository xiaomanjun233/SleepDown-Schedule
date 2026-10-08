package com.xiaomanjun.sleepdownschedule.feature.importing.progress

import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xiaomanjun.sleepdownschedule.feature.importing.AiEduImportProgressSession
import com.xiaomanjun.sleepdownschedule.feature.agent.AgentMarkdownText

@Composable
internal fun AiImportReasoningPanel(taskId: String, textColor: Color, listState: LazyListState, summary: String) {
    // Only the output at the conversation tail collects streaming text; the glass host
    // continue to observe coarse task progress, not individual model tokens.
    val live by AiEduImportProgressSession.liveReasoning.collectAsStateWithLifecycle()
    val text = live.text.takeIf { live.taskId == taskId }.orEmpty()
    val courses = live.courses.takeIf { live.taskId == taskId }.orEmpty()
    val native = live.taskId == taskId && live.nativeReasoning
    val reasoningScroll = rememberScrollState()
    LaunchedEffect(text) { if (!reasoningScroll.isScrollInProgress) reasoningScroll.scrollTo(reasoningScroll.maxValue) }
    val dragging by listState.interactionSource.collectIsDraggedAsState()
    var following by remember(taskId) { mutableStateOf(true) }
    LaunchedEffect(dragging) {
        if (dragging) following = false
        else if (!listState.canScrollForward) following = true
    }
    LaunchedEffect(taskId, text, courses.size) {
        if (following) {
            withFrameNanos { }
            val last = listState.layoutInfo.totalItemsCount - 1
            if (last >= 0) {
                if (listState.layoutInfo.visibleItemsInfo.none { it.index == last }) listState.scrollToItem(last)
                // The output item may be taller than the viewport. Scroll to its actual tail.
                val layout = listState.layoutInfo
                layout.visibleItemsInfo.lastOrNull { it.index == last }?.let { item ->
                    val distance = item.offset + item.size - layout.viewportEndOffset + layout.afterContentPadding
                    if (distance > 0) listState.scrollBy(distance.toFloat())
                }
            }
        }
    }
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(if (native) "模型思考" else "正在处理", color = textColor.copy(alpha = 0.60f), style = MaterialTheme.typography.labelMedium)
        Column(if (native) Modifier.fillMaxWidth()
            .background(textColor.copy(alpha = 0.055f), RoundedCornerShape(16.dp))
            .heightIn(max = 180.dp).verticalScroll(reasoningScroll).padding(12.dp) else Modifier.fillMaxWidth()) {
            AgentMarkdownText(text.ifBlank { summary.ifBlank { "等待模型返回阶段摘要…" } },
                textColor.copy(alpha = 0.85f), MaterialTheme.typography.bodyMedium)
        }
        courses.forEach { course ->
            Column(Modifier.fillMaxWidth().background(textColor.copy(alpha = 0.055f), RoundedCornerShape(16.dp)).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(course.name, color = textColor, style = MaterialTheme.typography.titleSmall)
                Text("周${"一二三四五六日"[course.weekday - 1]} · 第 ${course.periods.joinToString("、")} 节 · ${course.weeks.joinToString("、")} 周",
                    color = textColor.copy(alpha = 0.72f), style = MaterialTheme.typography.bodySmall)
                listOfNotNull(course.location, course.teacher).filter(String::isNotBlank).joinToString(" · ").takeIf(String::isNotBlank)?.let {
                    Text(it, color = textColor.copy(alpha = 0.72f), style = MaterialTheme.typography.bodySmall)
                }
                Text("已识别 · 等待整体验证", color = textColor.copy(alpha = 0.5f), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
