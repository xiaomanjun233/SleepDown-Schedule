package com.xiaomanjun.sleepdownschedule.feature.importing

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/** Thinking-mode DeepSeek rejects forced tools. Keep thinking and let the model select. */
internal fun scheduleToolChoice(
    config: AiProviderConfig, name: String, allowProgressUpdates: Boolean = false
): JsonElement? =
    if (config.providerId == AiProviderPresets.deepSeek.id && config.reasoningEffort != AiReasoningEffort.NONE) {
        // Chat defaults to auto when tools exist; omit the unsupported parameter entirely.
        if (config.endpointStyle == AiEndpointStyle.CHAT_COMPLETIONS) null else JsonPrimitive("auto")
    } else if (allowProgressUpdates || config.usesMimoProtocol()) {
        JsonPrimitive("auto")
    } else buildJsonObject {
        put("type", JsonPrimitive("function"))
        if (config.endpointStyle == AiEndpointStyle.RESPONSES) {
            put("name", JsonPrimitive(name))
        } else {
            put("function", buildJsonObject { put("name", JsonPrimitive(name)) })
        }
    }
