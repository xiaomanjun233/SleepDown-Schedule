package com.xiaomanjun.sleepdownschedule.feature.importing.chatgpt

import com.xiaomanjun.sleepdownschedule.feature.importing.AiProviderPresets
import com.xiaomanjun.sleepdownschedule.feature.importing.AiProviderProfile

internal fun resolveChatGptModelProfile(
    profile: AiProviderProfile,
    models: List<ChatGptModel>
): AiProviderProfile {
    val selected = models.firstOrNull { it.slug == profile.defaultModel.trim() }
    return AiProviderPresets.normalizeChatGptProfile(profile).copy(
        availableModels = models.map { it.slug },
        supportsVision = selected?.supportsImages == true,
        capabilities = AiProviderPresets.chatGpt.capabilities.copy(
            supportsImageInput = selected?.supportsImages == true
        )
    )
}
