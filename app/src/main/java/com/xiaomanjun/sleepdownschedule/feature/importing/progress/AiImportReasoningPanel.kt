package com.xiaomanjun.sleepdownschedule.feature.importing.progress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xiaomanjun.sleepdownschedule.feature.importing.AiEduImportProgressSession
import com.xiaomanjun.sleepdownschedule.feature.importing.aiImportElapsedLabel
import com.xiaomanjun.sleepdownschedule.feature.importing.AiImportReport
import com.xiaomanjun.sleepdownschedule.feature.importing.AiImportReportKind
import com.xiaomanjun.sleepdownschedule.feature.importing.AiImportActivitySource
import com.xiaomanjun.sleepdownschedule.feature.importing.remainingAiImportPublicText
import kotlinx.coroutines.delay

/** Live status stays in this leaf; completed reports live in the persistent transcript above it. */
@Composable
internal fun AiImportReasoningPanel(taskId: String, reports: List<AiImportReport>) {
    val colors = LocalAiImportTextColors.current
    val live by AiEduImportProgressSession.liveReasoning.collectAsStateWithLifecycle()
    val activity = live.activity.takeIf { live.taskId == taskId }
    val publicActivity = activity?.takeUnless { it.source == AiImportActivitySource.STATUS }?.text.orEmpty()
    val text = remainingAiImportPublicText(live.text.takeIf(String::isNotBlank) ?: publicActivity, reports)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (text.isNotBlank()) AiImportTranscriptEntry(AiImportReport(text, AiImportReportKind.MODEL), streaming = true)
    }
}
