package com.xiaomanjun.sleepdownschedule.core.web

import java.net.URL
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class WebDownloadConnectionTest {
    @Test fun redirectReadsCookiesForEachDestination() {
        MockWebServer().use { first ->
            MockWebServer().use { second ->
                first.start()
                second.start()
                val source = first.url("/file").toString()
                val target = second.url("/file").toString()
                first.enqueue(MockResponse().setResponseCode(302).setHeader("Location", target))
                second.enqueue(MockResponse().setBody("file"))
                val connection = openWebDownloadConnection(source, null, "$source?private=value",
                    cookieForUrl = { if (it == source) "session=original" else null },
                    receiveCookie = { _, _ -> })
                try {
                    connection.inputStream.use { assertEquals("file", it.reader().readText()) }
                } finally {
                    connection.disconnect()
                }
                assertEquals("session=original", first.takeRequest().getHeader("Cookie"))
                val redirected = second.takeRequest()
                assertNull(redirected.getHeader("Cookie"))
                assertEquals(first.url("/").toString(), redirected.getHeader("Referer"))
            }
        }
    }

    @Test fun redirectLoopHasAFiniteLimit() {
        MockWebServer().use { server ->
            server.start()
            repeat(11) { server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "/loop")) }
            assertThrows(IllegalStateException::class.java) {
                openWebDownloadConnection(server.url("/loop").toString(), null, null, { null }, { _, _ -> })
            }
            assertEquals(11, server.requestCount)
        }
    }

    @Test fun errorResponseFailsBeforeCreatingAFile() {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse().setResponseCode(404))
            assertThrows(IllegalStateException::class.java) {
                openWebDownloadConnection(server.url("/missing").toString(), null, null, { null }, { _, _ -> })
            }
        }
    }

    @Test fun refererNeverSendsCredentialsFragmentsOrSecureUrlsToHttp() {
        assertNull(downloadReferer("https://school.test/private", URL("http://school.test/file")))
        assertNull(downloadReferer("https://name:password@school.test/", URL("https://school.test/file")))
        assertEquals("https://school.test/page", downloadReferer("https://school.test/page#secret", URL("https://school.test/file")))
        assertEquals("https://school.test/", downloadReferer("https://school.test/page?token=secret", URL("https://cdn.test/file")))
    }
}
