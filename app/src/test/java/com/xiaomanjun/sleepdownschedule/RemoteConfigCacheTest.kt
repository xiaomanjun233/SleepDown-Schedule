package com.xiaomanjun.sleepdownschedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RemoteConfigCacheTest {
    @Test
    fun bootstrapCacheRoundTripsAndRejectsCorruption() {
        val original = RemoteBootstrap(
            schemaVersion = 1,
            serverTime = 1_787_000_000,
            notices = listOf(
                RemoteNotice(7, "维护通知", "正文", "warning", "dialog", 1_786_000_000)
            ),
            agreements = RemoteAgreementSet(
                privacy = RemoteAgreementSummary(4, "隐私政策", "https://api.sleepdownschedule.cn/public/agreements/privacy/4", true, true)
			),
			ai = RemoteAiConfig(
				enabled = true,
				configVersion = 9,
				keyId = "managed-test",
				baseUrl = "https://api.example.com/v1/responses",
				model = "managed-model",
				endpointStyle = "responses",
				supportsVision = true,
				cipher = "AES-256-GCM",
				cipherVersion = 1,
				kdfVersion = 1,
				nonce = "AAAAAAAAAAAAAAAA",
				ciphertext = "persisted-encrypted-credential",
				issuedAt = 1_786_000_000,
				expiresAt = 1_788_000_000
			),
			donations = RemoteDonationSection(
				published = true,
				title = "捐赠致谢",
				entries = listOf(RemoteDonationEntry(1, "sleep-friend", 1234, "CNY"))
			)
        )

        val encoded = RemoteConfigCacheCodec.encode(original)
        assertEquals(original, RemoteConfigCacheCodec.decode(encoded))
        assertNull(RemoteConfigCacheCodec.decode("{not-json"))
    }
}
