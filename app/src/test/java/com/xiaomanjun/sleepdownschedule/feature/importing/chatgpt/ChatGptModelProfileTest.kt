package com.xiaomanjun.sleepdownschedule.feature.importing.chatgpt

import com.xiaomanjun.sleepdownschedule.feature.importing.AiImportSettings
import com.xiaomanjun.sleepdownschedule.feature.importing.AiProviderPresets
import com.xiaomanjun.sleepdownschedule.feature.importing.toProviderConfig
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatGptModelProfileTest {
    @Test fun missingCatalogCapabilityAllowsAnImageAttemptButExplicitRefusalDoesNot() {
        val profile = AiProviderPresets.chatGpt.copy(defaultModel = "account-model")
        val unknown = ChatGptProtocol.models(org.json.JSONObject(
            """{"models":[{"slug":"account-model","visibility":"list","display_name":"Account model"}]}"""))
        assertEquals(null, unknown.single().supportsImages)
        assertEquals(true, resolveChatGptModelProfile(profile, unknown).supportsVision)
        val textOnly = ChatGptProtocol.models(org.json.JSONObject(
            """{"models":[{"slug":"account-model","visibility":"list","input_modalities":["text"]}]}"""))
        assertEquals(false, resolveChatGptModelProfile(profile, textOnly).supportsVision)
        val vision = ChatGptProtocol.models(org.json.JSONObject(
            """{"models":[{"slug":"account-model","visibility":"list","input_modalities":["text","image"]}]}"""))
        assertEquals(true, resolveChatGptModelProfile(profile, vision).supportsVision)
        assertEquals(false, resolveChatGptModelProfile(profile, emptyList()).supportsVision)
    }

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
