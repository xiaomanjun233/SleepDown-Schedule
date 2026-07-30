package com.example.courseschedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiProviderPresetsTest {
    @Test
    fun openAiDefaultsToCurrentStableAliasAndKeepsPreviousModels() {
        val models = AiProviderPresets.modelOptions(AiProviderPresets.openAI.id).map { it.model }

        assertEquals("gpt-5.6", AiProviderPresets.openAI.defaultModel)
        assertEquals("gpt-5.6", models.first())
        assertTrue("gpt-5.5" in models)
        assertTrue("gpt-5.4" in models)
    }

    @Test
    fun dynamicCustomProviderKeepsItsStableIdentity() {
        val id = "custom:profile-id"
        val profile = AiProviderPresets.byId(id)

        assertTrue(AiProviderPresets.isCustomId(id))
        assertEquals(id, profile.id)
        assertEquals(AiProviderType.OpenAIChatCompatible, profile.providerType)
    }

    @Test
    fun blankCustomProviderDraftDoesNotCountAsContent() {
        assertTrue(!customProviderDraftHasContent("", " ", "", ""))
        assertTrue(customProviderDraftHasContent("武科大接口", "", "", ""))
        assertTrue(customProviderDraftHasContent("", "https://example.com/v1", "", ""))
    }

    @Test
    fun newCustomProviderStartsAsUnnamedTransientDraft() {
        val draft = AiImportSettingsStore.createCustomProvider()

        assertTrue(AiProviderPresets.isCustomId(draft.id))
        assertTrue(draft.displayName.isBlank())
    }

    @Test
    fun imageInputFollowsTheSelectedModelInsteadOfOnlyTheProvider() {
        val qwenText = AiProviderPresets.dashScope.copy(defaultModel = "qwen-plus")
        val qwenVision = AiProviderPresets.dashScope.copy(defaultModel = "qwen-vl-max")
        val deepSeekText = AiProviderPresets.deepSeek.copy(defaultModel = "deepseek-v4-pro")

        assertTrue(!AiProviderPresets.supportsImageInput(qwenText))
        assertTrue(AiProviderPresets.supportsImageInput(qwenVision))
        assertTrue(!AiProviderPresets.supportsImageInput(deepSeekText))
    }

    @Test
    fun customModelKeepsItsExplicitImageCapabilitySwitch() {
        val custom = AiProviderPresets.customProfile("custom:test").copy(
            defaultModel = "my-model",
            supportsVision = true
        )

        assertTrue(AiProviderPresets.supportsImageInput(custom))
        assertTrue(!AiProviderPresets.supportsImageInput(custom.copy(supportsVision = false)))
    }
}
