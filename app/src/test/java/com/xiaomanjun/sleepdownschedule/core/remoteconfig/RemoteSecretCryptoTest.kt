package com.xiaomanjun.sleepdownschedule.core.remoteconfig

import java.util.Base64
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RemoteSecretCryptoTest {
    private val vector: CryptoVector by lazy {
        val encoded = requireNotNull(javaClass.classLoader?.getResourceAsStream("crypto_vector_v1.json"))
            .bufferedReader().use { it.readText() }
        Json.decodeFromString<CryptoVector>(encoded)
    }

    @Test
    fun goKotlinKnownVectorDerivesAndDecrypts() {
        val derived = RemoteSecretCrypto.deriveKey(vector.remoteSecret, vector.applicationId, vector.certificateSha256)
        assertEquals(vector.expectedDerivedKey, Base64.getUrlEncoder().withoutPadding().encodeToString(derived))
        val config = vector.remoteConfig()
        assertEquals(vector.plaintextApiKey, RemoteSecretCrypto.decrypt(vector.remoteSecret, vector.certificateSha256, config, vector.applicationId))
        assertEquals(vector.expectedNonce, config.nonce)
        assertEquals(vector.expectedCiphertext, config.ciphertext)
    }

    @Test
    fun wrongSecretCertificateAndCiphertextFailClosed() {
        val config = vector.remoteConfig()
        assertThrows(Exception::class.java) { RemoteSecretCrypto.decrypt("wrong-secret-which-is-still-long", vector.certificateSha256, config, vector.applicationId) }
        assertThrows(Exception::class.java) { RemoteSecretCrypto.decrypt(vector.remoteSecret, "f".repeat(64), config, vector.applicationId) }
        assertThrows(Exception::class.java) { RemoteSecretCrypto.decrypt(vector.remoteSecret, vector.certificateSha256, config.copy(ciphertext = config.ciphertext.dropLast(2) + "aa"), vector.applicationId) }
    }

    @Test
    fun certificateNormalizationIsStable() {
        val colonSeparated = vector.certificateSha256.chunked(2).joinToString(":").uppercase()
        assertEquals(vector.certificateSha256, RemoteSecretCrypto.normalizeCertificateSha256(colonSeparated))
    }

    @Serializable
    private data class CryptoVector(
        val remoteSecret: String, val applicationId: String, val certificateSha256: String,
        val configVersion: Long, val keyId: String, val baseUrl: String, val model: String,
        val expiresAt: Long, val nonceHex: String, val plaintextApiKey: String,
        val expectedDerivedKey: String, val expectedNonce: String, val expectedCiphertext: String
    ) {
        fun remoteConfig() = RemoteAiConfig(
            enabled = true, configVersion = configVersion, keyId = keyId, baseUrl = baseUrl,
            model = model, endpointStyle = "responses", supportsVision = true,
            cipher = "AES-256-GCM", cipherVersion = 1, kdfVersion = 1,
            nonce = expectedNonce, ciphertext = expectedCiphertext, issuedAt = 1L,
            expiresAt = expiresAt
        )
    }
}
