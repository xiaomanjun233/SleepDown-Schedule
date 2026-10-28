package com.xiaomanjun.sleepdownschedule.feature.importing.chatgpt

import com.xiaomanjun.sleepdownschedule.feature.importing.AiProviderPresets
import com.xiaomanjun.sleepdownschedule.feature.importing.AiProviderProfile

internal fun resolveChatGptModelProfile(
    profile: AiProviderProfile,
    models: List<ChatGptModel>
): AiProviderProfile {
    val selected = models.firstOrNull { it.slug == profile.defaultModel.trim() }
    // The catalog primarily advertises account access and may omit capability metadata.
    // Let the official Responses endpoint validate an image attempt unless it explicitly says no.
    val allowImageInput = selected != null && selected.supportsImages != false
    return AiProviderPresets.normalizeChatGptProfile(profile).copy(
        availableModels = models.map { it.slug },
        supportsVision = allowImageInput,
        capabilities = AiProviderPresets.chatGpt.capabilities.copy(
            supportsImageInput = allowImageInput
        )
    )
}
