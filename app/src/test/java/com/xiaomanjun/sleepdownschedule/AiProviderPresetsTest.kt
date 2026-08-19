package com.xiaomanjun.sleepdownschedule

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
    fun managedDailyFreeProviderHasNoBundledEndpointOrModel() {
        val profile = AiProviderPresets.dailyFree
        val models = AiProviderPresets.modelOptions(profile)

        assertEquals("每日免费 AI", profile.displayName)
        assertEquals("", profile.baseUrl)
        assertEquals("", profile.defaultModel)
        assertEquals(AiEndpointStyle.RESPONSES, profile.endpointStyle)
        assertEquals(AiAuthType.CustomHeader, profile.authType)
        assertEquals(emptyList<String>(), models.map(AiModelOption::model))
        assertTrue(AiProviderPresets.shouldUseResponses(profile))
        assertTrue(AiProviderPresets.supportsImageInput(profile))
    }

    @Test
    fun managedCredentialIsNotBundledInThePreset() {
        assertTrue(AiProviderPresets.dailyFree.baseUrl.isBlank())
        assertTrue(AiProviderPresets.dailyFree.defaultModel.isBlank())
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
