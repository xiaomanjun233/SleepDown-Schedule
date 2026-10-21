package com.xiaomanjun.sleepdownschedule.feature.importing.chatgpt

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.net.Socket
import java.net.URI
import java.util.Base64
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.CancellationException
import javax.crypto.KeyGenerator

class ChatGptLocalSecurityTest {
    @Test fun cancellationDuringDurableWriteRestoresOldAccountBeforePublication() {
        val gate = ChatGptLoginCommit()
        var stored = "old-account"
        var published = "old-account"
        assertThrows(CancellationException::class.java) {
            gate.install(write = { stored = "new-account"; gate.cancel() },
                restore = { stored = "old-account" }, publish = { published = "new-account" })
        }
        assertEquals("old-account", stored)
        assertEquals("old-account", published)
    }

    @Test fun completedLoginIsPublishedExactlyOnceAndCancelBeforeSaveDoesNotWrite() {
        val gate = ChatGptLoginCommit()
        var writes = 0
        gate.cancel()
        assertThrows(CancellationException::class.java) { gate.install({ writes++ }, {}, {}) }
        assertEquals(0, writes)
        val valid = ChatGptLoginCommit()
        var publications = 0
        valid.install({ writes++ }, {}, { publications++ })
        valid.cancel()
        assertEquals(1, writes)
        assertEquals(1, publications)
    }

    @Test fun credentialsAreAuthenticatedEncryptedWithFreshIv() {
        val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val plaintext = "fake-test-access-and-refresh-tokens-only"
        val first = ChatGptCredentialCipher.encrypt(plaintext, key)
        val second = ChatGptCredentialCipher.encrypt(plaintext, key)
        assertFalse(String(first).contains(plaintext))
        assertFalse(first.contentEquals(second))
        assertEquals(plaintext, ChatGptCredentialCipher.decrypt(first, key))
        val tampered = JSONObject(String(first))
        val data = Base64.getDecoder().decode(tampered.getString("data"))
        data[0] = (data[0].toInt() xor 1).toByte()
        tampered.put("data", Base64.getEncoder().encodeToString(data))
        assertThrows(Exception::class.java) { ChatGptCredentialCipher.decrypt(tampered.toString().toByteArray(), key) }
        val otherKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        assertThrows(Exception::class.java) { ChatGptCredentialCipher.decrypt(first, otherKey) }
    }

    @Test fun loopbackIgnoresInvalidCallbacksAndAcceptsOnlyThePendingAttempt() {
        val executor = Executors.newSingleThreadExecutor()
        try {
            ChatGptLoopback().use { listener ->
                val uri = URI(listener.redirectUri)
                assertEquals("127.0.0.1", uri.host)
                assertEquals("/auth/callback", uri.path)
                assertTrue(uri.port > 0)
                val attempt = AuthAttempt(null, listener.redirectUri, "pending")
                val result = executor.submit<AuthCallback> { listener.awaitCallback(attempt, 5_000) }
                assertTrue(request(uri, "/auth/callback?state=wrong&code=fake&client_id=issued").contains("400 Bad Request"))
                assertTrue(request(uri, "/callback?state=pending&code=fake&client_id=issued").contains("400 Bad Request"))
                val response = request(uri, "/auth/callback?state=pending&code=fake&client_id=issued")
                assertTrue(response.contains("200 OK"))
                assertTrue(response.contains("Cache-Control: no-store"))
                assertFalse(response.contains("code=fake"))
                assertEquals("issued", result.get(3, TimeUnit.SECONDS).clientId)
            }
        } finally { executor.shutdownNow() }
    }

    @Test fun cancellationClosesListenerAndReleasesPort() {
        val listener = ChatGptLoopback()
        val uri = URI(listener.redirectUri)
        listener.close()
        assertThrows(Exception::class.java) { Socket(uri.host, uri.port).close() }
        assertThrows(ChatGptAuthException::class.java) { listener.awaitCallback(AuthAttempt(null, listener.redirectUri), 10) }
    }

    private fun request(uri: URI, target: String): String = Socket(uri.host, uri.port).use {
        it.soTimeout = 3_000
        it.getOutputStream().write("GET $target HTTP/1.1\r\nHost: ${uri.host}:${uri.port}\r\n\r\n".toByteArray())
        it.getInputStream().bufferedReader().readText()
    }
}
