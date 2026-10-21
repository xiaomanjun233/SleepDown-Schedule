package com.xiaomanjun.sleepdownschedule.feature.importing

import android.app.Application
import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class ChatGptSettingsStoreTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val preferences get() = context.getSharedPreferences("ai_import_settings", Context.MODE_PRIVATE)

    @Before
    fun clearPreferences() {
        preferences.edit().clear().commit()
    }

    @Test
    fun accountReasoningChoicePersistsAcrossRuntimeAndProviderSwitches() {
        val profile = AiProviderPresets.chatGpt.copy(defaultModel = "gpt-6.1-sol", reasoningEffort = AiReasoningEffort.XHIGH)
        AiImportSettingsStore.save(context, AiImportSettings(profile, ""))
        assertEquals(AiReasoningEffort.XHIGH, AiImportSettingsStore.loadForRuntime(context)!!.profile.reasoningEffort)
        AiImportSettingsStore.save(context, AiImportSettings(AiProviderPresets.none, ""))
        assertEquals(AiReasoningEffort.XHIGH, AiImportSettingsStore.loadProvider(context, "chatgpt").profile.reasoningEffort)
        assertEquals(AiReasoningEffort.MEDIUM, AiProviderPresets.normalizeChatGptProfile(
            profile.copy(reasoningEffort = AiReasoningEffort.NONE)).reasoningEffort)
    }

    @Test
    fun freshInstallOffersAccountLoginWithoutTheLegacyApiForm() {
        val ids = AiImportSettingsStore.selectableProfiles(context).map { it.id }

        assertTrue(AiProviderPresets.chatGpt.id in ids)
        assertFalse(AiProviderPresets.openAI.id in ids)
        assertTrue(AiProviderPresets.custom.id in ids)
    }

    @Test
    fun existingOpenAiCompatibleConfigurationAndEncryptedKeyRemainIntact() {
        preferences.edit()
            .putString("provider_id", "openai")
            .putString("base_url", "https://compatible.example/v1")
            .putString("model", "existing-model")
            .putString("encrypted_api_key_openai", "existing-opaque-ciphertext")
            .commit()

        val settings = AiImportSettingsStore.load(context)
        val profiles = AiImportSettingsStore.selectableProfiles(context)

        assertEquals(AiImportSettingsStore.MigratedOpenAiId, settings.profile.id)
        assertEquals("https://compatible.example/v1", settings.profile.baseUrl)
        assertEquals("existing-model", settings.profile.defaultModel)
        assertEquals("已迁移的自定义接口", settings.profile.displayName)
        assertFalse(profiles.any { it.id == "openai" })
        assertEquals("existing-opaque-ciphertext", preferences.getString("encrypted_api_key_custom:legacy-openai", null))
        assertFalse(preferences.contains("encrypted_api_key_openai"))
        AiImportSettingsStore.load(context)
        assertEquals(1, AiImportSettingsStore.selectableProfiles(context).count { it.id == AiImportSettingsStore.MigratedOpenAiId })
    }

    @Test
    fun switchingToChatGptDoesNotRemoveAnotherProvidersSavedKey() {
        preferences.edit()
            .putString("base_url_openai", "https://api.openai.com/v1")
            .putString("encrypted_api_key_openai", "retained-ciphertext")
            .putString("encrypted_api_key_custom:existing", "retained-custom-ciphertext")
            .commit()

        AiImportSettingsStore.save(context, AiImportSettings(AiProviderPresets.chatGpt, ""))
        val backup = AiImportSettingsStore.exportForBackup(context)

        assertEquals("retained-ciphertext", preferences.getString("encrypted_api_key_custom:legacy-openai", null))
        assertEquals("retained-custom-ciphertext", preferences.getString("encrypted_api_key_custom:existing", null))
        assertTrue(backup.providers.any { it.id == AiImportSettingsStore.MigratedOpenAiId })
        assertEquals("chatgpt", backup.selectedProviderId)
    }

    @Test
    fun signedOutChatGptStaysSelectedForAllRuntimeResolutionPaths() {
        AiImportSettingsStore.save(context, AiImportSettings(AiProviderPresets.chatGpt, ""))

        assertEquals("chatgpt", AiImportSettingsStore.resolveAvailableSettings(context)?.profile?.id)
        assertEquals("chatgpt", AiImportSettingsStore.activateAvailableSettings(context)?.profile?.id)
        assertEquals("chatgpt", AiImportSettingsStore.loadForRuntime(context)?.profile?.id)
        assertEquals("chatgpt", preferences.getString("provider_id", null))
        assertFalse(AiImportSettingsStore.shouldOfferManagedFreeAi(context))
    }

    @Test
    fun chatGptRejectsEditableEndpointsApiKeysAndUnverifiedModelCatalogs() {
        val tampered = AiProviderPresets.chatGpt.copy(
            baseUrl = "https://untrusted.example",
            responsesPath = "/steal-token",
            authType = AiAuthType.ApiKeyBearer,
            endpointStyle = AiEndpointStyle.CHAT_COMPLETIONS,
            supportsFileUpload = true,
            supportsPdfDirect = true,
            availableModels = listOf("unverified-model"),
            defaultModel = "unverified-model"
        )
        AiImportSettingsStore.save(context, AiImportSettings(tampered, "must-not-be-persisted"))

        val loaded = AiImportSettingsStore.load(context)
        assertEquals("https://api.openai.com/v1", loaded.profile.baseUrl)
        assertEquals("/responses", loaded.profile.responsesPath)
        assertEquals(AiAuthType.ChatGptOAuth, loaded.profile.authType)
        assertEquals(AiEndpointStyle.RESPONSES, loaded.profile.endpointStyle)
        assertTrue(loaded.profile.supportsFileUpload)
        assertFalse(loaded.profile.supportsPdfDirect)
        assertTrue(loaded.profile.availableModels.isEmpty())
        assertTrue(loaded.apiKey.isEmpty())
        assertFalse(preferences.contains("encrypted_api_key_chatgpt"))
    }

    @Test
    fun legacyOpenAiBackupRestoresItsOriginalProviderAndKeepsCiphertext() {
        preferences.edit()
            .putString("provider_id", "openai")
            .putString("base_url", "https://compatible.example/v1")
            .putString("model", "retained-model")
            .putString("encrypted_api_key_openai", "retained-ciphertext")
            .commit()
        val exported = AiImportSettingsStore.exportForBackup(context)
        val backup = exported.copy(selectedProviderId = "openai", providers = exported.providers.map {
            if (it.id == AiImportSettingsStore.MigratedOpenAiId) it.copy(id = "openai") else it
        })
        // A backup can be restored before this installation has a legacy provider entry.
        preferences.edit().remove("provider_id").remove("base_url_openai").commit()

        AiImportSettingsStore.applyBackupPreferences(context, backup)

        val restored = AiImportSettingsStore.load(context)
        assertEquals(AiImportSettingsStore.MigratedOpenAiId, restored.profile.id)
        assertEquals("https://compatible.example/v1", restored.profile.baseUrl)
        assertEquals("retained-model", restored.profile.defaultModel)
        assertEquals("已迁移的自定义接口", restored.profile.displayName)
        assertEquals("retained-ciphertext", preferences.getString("encrypted_api_key_custom:legacy-openai", null))
    }
}
