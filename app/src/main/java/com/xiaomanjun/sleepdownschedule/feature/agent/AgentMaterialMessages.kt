package com.xiaomanjun.sleepdownschedule.feature.agent

import kotlinx.serialization.json.JsonObject

/** Keep every tool result paired first, then attach requested views as ordinary multimodal input. */
internal fun agentMaterialImageMessages(results: List<AgentToolResult>): List<JsonObject> = results.mapNotNull { result ->
    result.images.takeIf { it.isNotEmpty() }?.let { agentUserMessage(result.content, it) }
}
