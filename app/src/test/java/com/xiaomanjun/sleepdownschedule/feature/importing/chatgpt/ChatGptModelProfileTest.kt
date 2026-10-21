package com.xiaomanjun.sleepdownschedule.feature.importing.chatgpt

import com.xiaomanjun.sleepdownschedule.feature.importing.AiImportSettings
import com.xiaomanjun.sleepdownschedule.feature.importing.AiProviderPresets
import com.xiaomanjun.sleepdownschedule.feature.importing.toProviderConfig
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatGptModelProfileTest {
    private val models = listOf(
        ChatGptModel("text", "Text", supportsImages = false),
        ChatGptModel("vision", "Vision", supportsImages = true)
    )

    @Test fun switchingEitherWayUpdatesComposerAndRequestCapabilities() {
        var profile = AiProviderPresets.chatGpt
        listOf("vision" to true, "text" to false, "vision" to true).forEach { (model, images) ->
            profile = resolveChatGptModelProfile(profile.copy(defaultModel = model), models)
            assertEquals(images, AiProviderPresets.supportsImageInput(profile))
            assertEquals(images, AiImportSettings(profile, "").toProviderConfig().supportsVision)
            assertEquals(images, profile.capabilities.supportsImageInput)
        }
    }

    @Test fun catalogArrivalAndAccountChangeReplaceStaleCapabilities() {
        val pending = resolveChatGptModelProfile(AiProviderPresets.chatGpt.copy(defaultModel = "vision"), emptyList())
        val loaded = resolveChatGptModelProfile(pending, models)
        val removed = resolveChatGptModelProfile(loaded, models.take(1))
        assertEquals(false, pending.supportsVision)
        assertEquals(true, loaded.supportsVision)
        assertEquals(false, removed.supportsVision)
        assertEquals(listOf("text"), removed.availableModels)
    }
}
