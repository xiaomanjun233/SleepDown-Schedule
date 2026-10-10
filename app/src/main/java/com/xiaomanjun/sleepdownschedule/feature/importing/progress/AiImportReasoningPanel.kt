package com.xiaomanjun.sleepdownschedule.feature.importing.progress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xiaomanjun.sleepdownschedule.feature.importing.AiEduImportProgressSession
import com.xiaomanjun.sleepdownschedule.feature.importing.AiImportActivitySource
import com.xiaomanjun.sleepdownschedule.feature.importing.aiImportWaitLabel
import kotlinx.coroutines.delay

/** Token updates stay in this small leaf. They never resize or scroll the conversation. */
@Composable
internal fun AiImportReasoningPanel(taskId: String, textColor: Color, summary: String) {
    val live by AiEduImportProgressSession.liveReasoning.collectAsStateWithLifecycle()
    val activity = live.activity.takeIf { live.taskId == taskId }
    val label = when (activity?.source) {
        AiImportActivitySource.PROVIDER_SUMMARY -> "正在思考"
        AiImportActivitySource.MODEL_PROGRESS -> "模型进度"
        else -> "正在处理"
    }
    val text = activity?.text?.takeIf(String::isNotBlank) ?: summary.ifBlank { "等待模型响应" }
    val nowNanos by produceState(System.nanoTime(), taskId) {
        while (true) {
            delay(1_000)
            value = System.nanoTime()
        }
    }
    val waitLabel = aiImportWaitLabel(nowNanos, live.activityAtNanos, live.waitPhase)
    val recognizedCount = live.courses.takeIf { live.taskId == taskId }?.size ?: 0
    val tickerScroll = rememberScrollState()
    LaunchedEffect(text) {
        withFrameNanos { }
        tickerScroll.scrollTo(tickerScroll.maxValue)
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("$label ·", color = textColor.copy(alpha = 0.58f), style = MaterialTheme.typography.bodyMedium)
            Text(
                text,
                modifier = Modifier.weight(1f).horizontalScroll(tickerScroll, enabled = false),
                color = textColor.copy(alpha = 0.78f),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip
            )
        }
        Text(
            waitLabel ?: if (recognizedCount > 0) "$recognizedCount 门已识别 · 正在等待完整数据校验"
                else "收到真实进度时会在这里更新；通过校验后才生成可用阶段",
            color = textColor.copy(alpha = 0.5f),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
