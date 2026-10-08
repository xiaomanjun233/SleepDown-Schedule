package com.xiaomanjun.sleepdownschedule.feature.importing.progress

import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
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

@Composable
internal fun AiImportReasoningPanel(taskId: String, textColor: Color, listState: LazyListState) {
    // Only the output at the conversation tail collects streaming text; the glass host
    // continue to observe coarse task progress, not individual model tokens.
    val live by AiEduImportProgressSession.liveReasoning.collectAsStateWithLifecycle()
    val text = live.text.takeIf { live.taskId == taskId }.orEmpty()
    val dragging by listState.interactionSource.collectIsDraggedAsState()
    var following by remember(taskId) { mutableStateOf(true) }
    LaunchedEffect(dragging) {
        if (dragging) following = false
        else if (!listState.canScrollForward) following = true
    }
    LaunchedEffect(taskId, text) {
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
        Text("正在处理", color = textColor.copy(alpha = 0.60f), style = MaterialTheme.typography.labelMedium)
        Text(text.ifBlank { "等待模型返回阶段摘要…" },
            color = textColor.copy(alpha = if (text.isBlank()) 0.48f else 0.85f),
            style = MaterialTheme.typography.bodyMedium)
    }
}
