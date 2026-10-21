package com.xiaomanjun.sleepdownschedule.feature.importing.shiguang

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class ShiguangPostTransportTest {
    private fun post(url: String, cookies: Boolean = true) = RegisteredWebPost(
        "name=数学", "application/x-www-form-urlencoded;charset=UTF-8", url, "POST", cookies, 0, 0
    )

    @Test fun forwardsUtf8BodyWithActualLengthAndPreservesAuthorization() {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse().setBody("accepted").setHeader("set-cookie", "course=ok; Path=/"))
            val cookies = mutableListOf<String>()
            val connection = openShiguangPostConnection(post(server.url("/submit").toString()), mapOf(
                "Content-Length" to "99999", "X-WebView-Post-Id" to "internal", "X-Requested-With" to "app",
                "Authorization" to "test-only", "Cookie" to "stale=1"
            ), false, { "session=1" }, { _, value -> cookies += value })
            try { assertEquals("accepted", connection.inputStream.reader().use { it.readText() }) }
            finally { connection.disconnect() }
            val sent = server.takeRequest()
            assertEquals("name=数学", sent.body.readUtf8())
            assertEquals("name=数学".toByteArray().size.toString(), sent.getHeader("Content-Length"))
            assertEquals("test-only", sent.getHeader("Authorization"))
            assertEquals("session=1", sent.getHeader("Cookie"))
            assertNull(sent.getHeader("X-WebView-Post-Id"))
            assertNull(sent.getHeader("X-Requested-With"))
            assertEquals(listOf("course=ok; Path=/"), cookies)
        }
    }

    @Test fun omittedCredentialsStayOmittedAndSameOriginRedirectBecomesGet() {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "/result"))
            server.enqueue(MockResponse().setBody("result").setHeader("Set-Cookie", "must-not-save=1"))
            val connection = openShiguangPostConnection(post(server.url("/submit").toString(), false),
                mapOf("Cookie" to "must-not-leak"), false, { error("credentials=omit must not read cookies") },
                { _, _ -> error("credentials=omit must not save cookies") })
            connection.inputStream.close()
            connection.disconnect()
            assertEquals("POST", server.takeRequest().method)
            val redirected = server.takeRequest()
            assertEquals("GET", redirected.method)
            assertNull(redirected.getHeader("Cookie"))
            assertEquals(0L, redirected.bodySize)
        }
    }

    @Test fun crossOriginRedirectNeverLeaksAuthorizationOrBypassesCors() {
        MockWebServer().use { first -> MockWebServer().use { other ->
            first.start()
            other.start()
            first.enqueue(MockResponse().setResponseCode(302).setHeader("Location", other.url("/private")))
            assertThrows(IllegalArgumentException::class.java) {
                openShiguangPostConnection(post(first.url("/submit").toString()), mapOf("Authorization" to "test-only"),
                    false, { null }, { _, _ -> })
            }
            assertEquals(1, first.requestCount)
            assertEquals(0, other.requestCount)
        } }
    }

    @Test fun redirectsAreBoundedAndMainFrameNavigationRemainsWithWebView() {
        MockWebServer().use { server ->
            server.start()
            repeat(6) { server.enqueue(MockResponse().setResponseCode(307).setHeader("Location", "/loop")) }
            assertThrows(IllegalArgumentException::class.java) {
                openShiguangPostConnection(post(server.url("/loop").toString()), emptyMap(), false, { null }, { _, _ -> })
            }
            assertEquals(6, server.requestCount)
            server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "https://login.test/"))
            val connection = openShiguangPostConnection(post(server.url("/login").toString()), emptyMap(), true, { null }, { _, _ -> })
            assertEquals(302, connection.responseCode)
            connection.disconnect()
            assertEquals(7, server.requestCount)
        }
    }
}
