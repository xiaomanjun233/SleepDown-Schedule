package com.xiaomanjun.sleepdownschedule.feature.importing

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, application = Application::class)
class AiProviderModelCatalogTest {
    @Test fun officialEndpointsCannotRedirectProviderKeysToACustomSite() {
        assertEquals("https://api.deepseek.com/models", AiProviderModelCatalog.endpoint(AiProviderPresets.deepSeek))
        assertEquals("https://api.xiaomimimo.com/v1/models", AiProviderModelCatalog.endpoint(AiProviderPresets.mimo))
        assertNull(AiProviderModelCatalog.endpoint(AiProviderPresets.deepSeek.copy(baseUrl = "https://example.test/v1")))
        assertNull(AiProviderModelCatalog.endpoint(AiProviderPresets.custom))
        assertNull(AiProviderModelCatalog.endpoint(AiProviderPresets.chatGpt))
    }

    @Test fun serverOrderAndModalitiesDriveTheDeepSeekPicker() {
        val catalog = parseOfficialAiModels("""{"data":[
            {"id":"future-vision","name":"Next","input_modalities":["text","image"],"output_modalities":["text"]},
            {"id":"future-text","input_modalities":["text"],"output_modalities":["text"]},
            {"id":"speech","input_modalities":["audio"],"output_modalities":["audio"]},
            {"id":"future-text"}
        ]}""", AiProviderPresets.deepSeek)
        assertEquals(listOf("future-vision", "future-text"), catalog.map { it.model })
        assertTrue(catalog[0].supportsImageInput)
        assertFalse(catalog[1].supportsImageInput)
        val profile = AiProviderPresets.deepSeek.copy(defaultModel = "future-vision").withModelCatalog(catalog)
        assertEquals(catalog, AiProviderPresets.modelOptions(profile))
        assertFalse(AiProviderPresets.modelOptions(profile).any { it.model == "deepseek-flash" })
    }

    @Test fun mimoAudioOnlyEntriesDoNotEnterTheCourseAssistantPicker() {
        val catalog = parseOfficialAiModels("""{"data":[
            {"id":"mimo-v2.6-pro"},{"id":"mimo-v2.5-tts"},{"id":"mimo-v2.5-tts-voiceclone"},
            {"id":"mimo-v2.5-asr"},{"id":"mimo-v2.6-flash"}
        ]}""", AiProviderPresets.mimo)
        assertEquals(listOf("mimo-v2.6-pro", "mimo-v2.6-flash"), catalog.map { it.model })
        assertTrue(catalog.all { it.supportsImageInput && it.supportsResponses })
    }

    @Test fun refreshKeepsTheCurrentCustomChoiceAndSelectsItsActualRow() {
        val fetched = listOf(AiModelOption("Live model", "server-model"))
        val profile = AiProviderPresets.deepSeek.copy(defaultModel = "my-model").withModelCatalog(fetched)
        val options = AiProviderPresets.modelOptions(profile)
        assertEquals(listOf("server-model", "my-model"), options.map { it.model })
        assertEquals(1, aiModelSelectionIndex(options, "my-model"))
        assertEquals(1, aiModelSelectionIndex(options, " MY-MODEL "))
        assertEquals(options.size, aiModelSelectionIndex(options, ""))
        assertEquals("my-model", profile.defaultModel)
    }

    @Test fun cacheStoresMetadataWithoutChangingSelectedProviderOrBackupShape() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val profile = AiProviderPresets.deepSeek
        val selectedBefore = AiImportSettingsStore.load(context).profile.id
        val catalog = listOf(AiModelOption("New", "new-model", supportsImageInput = true))
        AiProviderModelCatalog.save(context, profile, catalog)
        assertEquals(catalog, AiProviderModelCatalog.cached(context, profile))
        assertEquals(selectedBefore, AiImportSettingsStore.load(context).profile.id)
        assertTrue(AiProviderModelCatalog.cached(context, profile.copy(baseUrl = "https://example.test")).isEmpty())
        val encoded = Json.encodeToString(profile.withModelCatalog(catalog))
        assertFalse(encoded.contains("modelCatalog"))
    }

    @Test fun emptyOrInvalidDiscoveryCannotReplaceTheLastUsableList() {
        assertThrows(IllegalArgumentException::class.java) { parseOfficialAiModels("""{"data":[]}""", AiProviderPresets.mimo) }
        assertThrows(IllegalStateException::class.java) { parseOfficialAiModels("""{"unexpected":[]}""", AiProviderPresets.mimo) }
    }
}
