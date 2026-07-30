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
}
