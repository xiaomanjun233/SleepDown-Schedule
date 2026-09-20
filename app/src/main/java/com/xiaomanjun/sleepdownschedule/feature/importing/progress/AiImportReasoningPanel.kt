package com.xiaomanjun.sleepdownschedule.feature.importing.progress

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kyant.shapes.RoundedRectangle
import com.xiaomanjun.sleepdownschedule.feature.importing.AiEduImportProgressSession
import kotlinx.coroutines.flow.collectLatest

@Composable
internal fun AiImportReasoningPanel(taskId: String, textColor: Color) {
    // Only this small reading window collects streaming text; the conversation and glass host
    // continue to observe coarse task progress, not individual model tokens.
    val live by AiEduImportProgressSession.liveReasoning.collectAsStateWithLifecycle()
    val text = live.text.takeIf { live.taskId == taskId }.orEmpty()
    val scroll = rememberScrollState()
    LaunchedEffect(taskId, text) {
        // Follow every new chunk and the measured height it produces. Comparing the old scroll
        // offset with the newly grown maxValue incorrectly stopped following on the first wrap.
        snapshotFlow { scroll.maxValue }.collectLatest { bottom ->
            if (bottom != Int.MAX_VALUE) {
                scroll.animateScrollTo(bottom, animationSpec = tween(80, easing = LinearEasing))
            }
        }
    }
    Column(
        Modifier.fillMaxWidth().clip(RoundedRectangle(20.dp))
            .background(textColor.copy(alpha = 0.055f)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("模型思考", color = textColor.copy(alpha = 0.60f), style = MaterialTheme.typography.labelMedium)
        Box(Modifier.fillMaxWidth().height(140.dp), contentAlignment = Alignment.Center) {
            if (text.isBlank()) {
                Text("等待模型返回思考内容…", color = textColor.copy(alpha = 0.48f), style = MaterialTheme.typography.bodySmall)
            } else {
                Text(
                    text, modifier = Modifier.fillMaxSize().verticalScroll(scroll),
                    color = textColor.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
