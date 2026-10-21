package com.xiaomanjun.sleepdownschedule.feature.importing

/** Official model capabilities, shared by settings and the OAuth request boundary.
 * https://developers.openai.com/api/docs/guides/deployment-checklist
 * https://developers.openai.com/api/docs/models (verified 2026-10-11).
 * Unknown account aliases retain the server default rather than advertising unsupported levels.
 */
internal fun chatGptReasoningEfforts(model: String): List<AiReasoningEffort> {
    val name = model.trim().lowercase().replace(Regex("-\\d{4}-\\d{2}-\\d{2}$"), "")
    val standard = listOf(AiReasoningEffort.LOW, AiReasoningEffort.MEDIUM, AiReasoningEffort.HIGH)
    return when (name) {
        "gpt-6-astra", "gpt-6.1-sol" -> standard + listOf(AiReasoningEffort.XHIGH, AiReasoningEffort.MAX)
        "gpt-6-sol", "gpt-6-luna", "gpt-5.6", "gpt-5.6-sol", "gpt-5.6-terra", "gpt-5.6-luna" ->
            listOf(AiReasoningEffort.NONE) + standard + listOf(AiReasoningEffort.XHIGH, AiReasoningEffort.MAX)
        "gpt-5.5", "gpt-5.4", "gpt-5.2" -> listOf(AiReasoningEffort.NONE) + standard + AiReasoningEffort.XHIGH
        "gpt-5.3-codex" -> standard + AiReasoningEffort.XHIGH
        else -> emptyList()
    }
}

internal fun chatGptReasoningEffort(model: String, selected: AiReasoningEffort): AiReasoningEffort =
    selected.takeIf { it in chatGptReasoningEfforts(model) } ?: AiReasoningEffort.MEDIUM
