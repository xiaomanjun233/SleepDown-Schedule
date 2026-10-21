package com.xiaomanjun.sleepdownschedule.feature.agent

import com.xiaomanjun.sleepdownschedule.feature.importing.AiImportActivitySource
import com.xiaomanjun.sleepdownschedule.feature.importing.AiImportInteraction
import com.xiaomanjun.sleepdownschedule.feature.importing.importPublicProgressText
import com.xiaomanjun.sleepdownschedule.feature.importing.completedImportProgressSummaries

/** Provider assistant prose is public progress, never a substitute for native reasoning. */
internal fun importAgentProgressDetails(text: String?): List<String> {
    val raw = text?.trim()?.takeIf(String::isNotBlank) ?: return emptyList()
    // Reject private/protocol material before selecting or shortening a public progress block.
    if (Regex("(?i)<(?:think(?:ing)?|analysis|reasoning)(?:\\s|>)|encrypted_content|chain_of_thought|<agent_actions|DSML|tool_calls|function_call")
            .containsMatchIn(raw)) return emptyList()
    return if (raw.contains("<import_progress>")) completedImportProgressSummaries(raw)
    else listOfNotNull(importPublicProgressText(raw))
}

/** Actual tool/status events are lossless; short public model narration shares the bounded ticker. */
internal fun reportAgentImportStatus(interaction: AiImportInteraction?, status: AgentRunStatus) {
    interaction ?: return
    interaction.checkActive()
    if (status.text !in setOf("正在思考", "准备下一步")) interaction.reportExecutionStep(status.text)
    // Only this status carries the provider's assistant prose; other detail fields can be
    // local metadata (for example the fallback profile's model name).
    val details = if (status.text == "准备下一步") importAgentProgressDetails(status.detail) else emptyList()
    if (details.isNotEmpty()) details.forEach { detail ->
        interaction.publishActivity(detail, AiImportActivitySource.MODEL_PROGRESS)
    }
    // A following tool event must not replace a pending provider sentence in the ticker.
    // Consumers without the semantic channel retain the previous status-only fallback.
    else if (interaction.onExecutionStep == null) interaction.publishActivity(status.text)
}
