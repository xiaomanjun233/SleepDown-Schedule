package com.xiaomanjun.sleepdownschedule.feature.backup.webdav

import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.*
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder
import java.util.concurrent.TimeUnit

class WebDavClientTest {
    @get:Rule val temporary = TemporaryFolder()
    private val server = MockWebServer()
    @Before fun start() { server.start() }
    @After fun stop() { server.shutdown() }
    private fun client(readMillis: Long = 1000) = WebDavClient(
        WebDavConnection(server.url("/backup/").toString(), "user", "private-secret"),
        OkHttpClient.Builder().readTimeout(readMillis, TimeUnit.MILLISECONDS).build())
    private fun response(href: String, collection: Boolean = false) = """
        <d:response><d:href>$href</d:href><d:propstat><d:prop>
        <d:resourcetype>${if (collection) "<d:collection/>" else ""}</d:resourcetype>
        <d:getcontentlength>64</d:getcontentlength><d:getetag>"v1"</d:getetag>
        </d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response>
    """.trimIndent()

    @Test fun listingKeepsOnlyDirectBackupFilesAndDoesNotFollowExternalLinks() = runBlocking {
        val xml = """<d:multistatus xmlns:d="DAV:">${response("/backup/a.sleepdown")}${response("/backup/sub/b.sleepdown")}${response("https://example.org/backup/c.sleepdown")}${response("/backup/d.sleepdown", true)}</d:multistatus>"""
        server.enqueue(MockResponse().setResponseCode(207).setBody(xml))
        assertEquals(listOf(WebDavEntry("a.sleepdown", 64, null, "\"v1\"")), client().list())
        val request = server.takeRequest()
        assertEquals("PROPFIND", request.method)
        assertEquals("1", request.getHeader("Depth"))
    }
    @Test fun uploadCollisionNeverWritesOverExistingArchiveAndCleansOnlyOwnStage() = runBlocking {
        val file = temporary.newFile().apply { writeText("backup payload") }
        server.enqueue(MockResponse().setResponseCode(201))
        server.enqueue(MockResponse().setResponseCode(412))
        server.enqueue(MockResponse().setResponseCode(204))
        try { client().upload(file, "existing.sleepdown"); fail("collision") }
        catch (failure: WebDavFailure) { assertEquals(WebDavFailure.Reason.COLLISION, failure.reason) }
        val put = server.takeRequest(); val move = server.takeRequest(); val delete = server.takeRequest()
        assertEquals("PUT", put.method)
        assertTrue(put.path!!.endsWith(".upload"))
        assertEquals("*", put.getHeader("If-None-Match"))
        assertEquals("MOVE", move.method)
        assertEquals("F", move.getHeader("Overwrite"))
        assertEquals(server.url("/backup/existing.sleepdown").toString(), move.getHeader("Destination"))
        assertEquals(put.path, delete.path)
        assertEquals("DELETE", delete.method)
    }
    @Test fun confirmedOverwriteUsesDestinationVersionCondition() = runBlocking {
        val file = temporary.newFile().apply { writeText("backup") }
        repeat(3) { server.enqueue(MockResponse().setResponseCode(204)) }
        client().upload(file, "existing.sleepdown", true, "\"v1\"")
        server.takeRequest()
        val move = server.takeRequest()
        assertEquals("T", move.getHeader("Overwrite"))
        assertEquals("<${server.url("/backup/existing.sleepdown")}> ([\"v1\"])", move.getHeader("If"))
    }
    @Test fun authAndRedirectFailuresDoNotExposeResponseSecretsOrForwardCredentials() = runBlocking {
        for (code in listOf(401, 302)) {
            server.enqueue(MockResponse().setResponseCode(code).setHeader("Location", "https://example.org/stolen")
                .setBody("private-secret"))
            try { client().list(); fail("must reject") } catch (failure: WebDavFailure) {
                assertFalse(failure.toString().contains("private-secret"))
                assertNull(failure.cause)
                assertEquals(if (code == 401) WebDavFailure.Reason.AUTH else WebDavFailure.Reason.HTTP, failure.reason)
            }
        }
        assertEquals(2, server.requestCount)
    }
    @Test fun timeoutAndCancellationDeletePartialDownload() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("partial archive").setBodyDelay(2, TimeUnit.SECONDS))
        val output = temporary.newFile()
        try { client(80).download(WebDavEntry("a.sleepdown", null, null, null), output); fail("timeout") }
        catch (failure: WebDavFailure) { assertEquals(WebDavFailure.Reason.TIMEOUT, failure.reason) }
        assertFalse(output.exists())
        server.enqueue(MockResponse().setResponseCode(200).setBody("slow archive").setBodyDelay(2, TimeUnit.SECONDS))
        val cancelled = temporary.newFile()
        val work = launch { client().download(WebDavEntry("b.sleepdown", null, null, null), cancelled) }
        delay(100); work.cancelAndJoin()
        assertFalse(cancelled.exists())
    }
    @Test fun rejectsTraversalCredentialsInUrlAndExternalEntities() {
        assertFalse(WebDavClient.validName("../a.sleepdown"))
        try { WebDavClient.folderUrl("https://user:secret@example.org/backups/"); fail("URL credentials") }
        catch (failure: WebDavFailure) { assertEquals(WebDavFailure.Reason.ADDRESS, failure.reason) }
        val unsafe = """<!DOCTYPE d:multistatus [<!ENTITY xxe SYSTEM "file:///private">]><d:multistatus xmlns:d="DAV:">&xxe;</d:multistatus>"""
        for (bytes in listOf(unsafe.toByteArray(), unsafe.toByteArray(Charsets.UTF_16))) {
            try { WebDavClient.parseListing(server.url("/backup/"), bytes); fail("XXE") }
            catch (failure: WebDavFailure) { assertEquals(WebDavFailure.Reason.RESPONSE, failure.reason) }
        }
    }
}
