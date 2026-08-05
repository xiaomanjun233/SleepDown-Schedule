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
        assertTrue("gpt-5.6-sol" in models)
        assertTrue("gpt-5.5" in models)
        assertTrue("gpt-5.4" in models)
        assertTrue("gpt-5.3-codex" in models)
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
    fun managedDailyFreeProviderIsFixedToLunaResponses() {
        val profile = AiProviderPresets.dailyFree
        val models = AiProviderPresets.modelOptions(profile)

        assertEquals("每日免费 AI", profile.displayName)
        assertEquals("https://api.chunxiao.pro/v1", profile.baseUrl)
        assertEquals("gpt-5.6-luna", profile.defaultModel)
        assertEquals(AiEndpointStyle.RESPONSES, profile.endpointStyle)
        assertEquals(listOf("gpt-5.6-luna"), models.map(AiModelOption::model))
        assertTrue(AiProviderPresets.shouldUseResponses(profile))
        assertTrue(AiProviderPresets.supportsImageInput(profile))
    }

    @Test
    fun managedCredentialIsReconstructedWithoutAPlainTextPresetField() {
        val key = ManagedFreeAiCredentials.apiKey()

        assertTrue(key.startsWith("sk-"))
        assertTrue(key.length > 40)
        assertTrue(key.none(Char::isWhitespace))
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
            defaultModel = "gpt-5.3-codex",
            supportsVision = true,
            endpointStyle = AiEndpointStyle.RESPONSES
        )

        assertTrue(AiProviderPresets.supportsImageInput(custom))
        assertTrue(!AiProviderPresets.supportsImageInput(custom.copy(supportsVision = false)))
    }

    @Test
    fun responsesSupportFollowsConcreteProviderModel() {
        assertTrue(AiProviderPresets.supportsResponses(AiProviderPresets.deepSeek))
        assertTrue(
            !AiProviderPresets.supportsResponses(
                AiProviderPresets.deepSeek.copy(defaultModel = "deepseek-v4-pro")
            )
        )
        assertTrue(AiProviderPresets.supportsResponses(AiProviderPresets.mimo))
    }
}
