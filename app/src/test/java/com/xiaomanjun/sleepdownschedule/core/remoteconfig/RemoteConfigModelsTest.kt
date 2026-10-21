package com.xiaomanjun.sleepdownschedule.core.remoteconfig

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class RemoteConfigModelsTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test fun poolSupportsLegacyFallbackAndExplicitRevocation() {
        val primary = RemoteAiConfig(true, 1, "one", "https://one.example/responses", "one", "responses", true, "AES-256-GCM", 1, 1, "nonce", "cipher", 10, 100)
        val legacy = RemoteBootstrap(1, 10, ai = primary)
        assertEquals(listOf(primary), legacy.managedAiConfigs())
        assertEquals(emptyList<RemoteAiConfig>(), legacy.copy(aiConfigs = emptyList()).managedAiConfigs())
        val backup = primary.copy(keyId = "two", configVersion = 2, priority = 10)
        val current = legacy.copy(aiConfigs = listOf(backup, primary))
        assertEquals(listOf(primary, backup), current.managedAiConfigs())
        val encoded = Json.encodeToString(RemoteBootstrap.serializer(), current)
        assertEquals(current, json.decodeFromString<RemoteBootstrap>(encoded))
    }

    @Test
    fun bootstrapParsesUnknownFieldsAndOptionalSections() {
        val bootstrap = json.decodeFromString<RemoteBootstrap>(
            """{"schemaVersion":1,"serverTime":1787000000,"notices":[],"agreements":{"privacy":null,"terms":null},"ai":null,"future":true}"""
        )
        assertEquals(1, bootstrap.schemaVersion)
        assertEquals(1787000000L, bootstrap.serverTime)
        assertFalse(bootstrap.notices.isNotEmpty())
        assertNull(bootstrap.ai)
    }

    @Test
    fun disabledExpiredAndUnknownConfigsFailAvailabilityCheck() {
        val active = RemoteAiConfig(true, 1, "key", "https://example/v1", "model", "responses", true, "AES-256-GCM", 1, 1, "nonce", "cipher", 10, 100)
        assertEquals(RemoteAiAvailability.AVAILABLE, active.availability(99))
        assertEquals(RemoteAiAvailability.EXPIRED, active.availability(100))
        assertEquals(RemoteAiAvailability.DISABLED, active.copy(enabled = false).availability(99))
        assertEquals(RemoteAiAvailability.UNSUPPORTED, active.copy(kdfVersion = 2).availability(99))
    }
}
