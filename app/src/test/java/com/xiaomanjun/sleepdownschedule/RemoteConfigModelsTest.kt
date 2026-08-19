package com.xiaomanjun.sleepdownschedule

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class RemoteConfigModelsTest {
    private val json = Json { ignoreUnknownKeys = true }

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
