package com.xiaomanjun.sleepdownschedule.feature.backup.webdav

import kotlinx.coroutines.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.*
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class WebDavClientTest {
    @get:Rule val temporary = TemporaryFolder()
    private val server = MockWebServer()
    @Before fun start() { server.start() }
    @After fun stop() { server.shutdown() }
    private fun client(readMillis: Long = 1000) = WebDavClient(
        WebDavConnection(server.url("/backup/").toString(), "user", "private-secret"),
        OkHttpClient.Builder().readTimeout(readMillis, TimeUnit.MILLISECONDS).build())
    private fun response(href: String, collection: Boolean = false, etag: String = "\"v1\"") = """
        <d:response><d:href>$href</d:href><d:propstat><d:prop>
        <d:resourcetype>${if (collection) "<d:collection/>" else ""}</d:resourcetype>
        <d:getcontentlength>64</d:getcontentlength><d:getetag>$etag</d:getetag>
        </d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response>
    """.trimIndent()

    private data class RemoteFile(val content: String, val etag: String)
    private inner class StatefulDavDispatcher : Dispatcher() {
        val files = ConcurrentHashMap<String, RemoteFile>()

        override fun dispatch(request: RecordedRequest): MockResponse {
            val path = request.path!!
            return when (request.method) {
                "PROPFIND" -> MockResponse().setResponseCode(207).setBody(
                    """<d:multistatus xmlns:d="DAV:">${files.entries.filter { it.key.endsWith(".sleepdown") }
                        .joinToString("") { response(it.key, etag = it.value.etag) }}</d:multistatus>""")
                "PUT" -> {
                    if (request.getHeader("If-None-Match") == "*" && files.containsKey(path))
                        MockResponse().setResponseCode(412)
                    else {
                        files[path] = RemoteFile(request.body.readUtf8(), "\"uploaded\"")
                        MockResponse().setResponseCode(201)
                    }
                }
                "MOVE" -> {
                    val source = files[path] ?: return MockResponse().setResponseCode(404)
                    val destination = request.getHeader("Destination")?.toHttpUrlOrNull()
                        ?: return MockResponse().setResponseCode(400)
                    val current = files[destination.encodedPath]
                    val condition = request.getHeader("If")
                    if ((condition != null && (current == null || condition != "<$destination> ([${current.etag}])")) ||
                        (current != null && request.getHeader("Overwrite") == "F")) {
                        MockResponse().setResponseCode(412)
                    } else {
                        files[destination.encodedPath] = source
                        files.remove(path)
                        MockResponse().setResponseCode(if (current == null) 201 else 204)
                    }
                }
                "DELETE" -> {
                    files.remove(path)
                    MockResponse().setResponseCode(204)
                }
                else -> MockResponse().setResponseCode(405)
            }
        }
    }
    private fun statefulServer() = StatefulDavDispatcher().also { server.dispatcher = it }

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
        val remote = statefulServer()
        for (etag in listOf("\"v1\"", "\"\"")) {
            remote.files["/backup/existing.sleepdown"] = RemoteFile("original backup", etag)
            client().upload(file, "existing.sleepdown", true, etag)
            val put = server.takeRequest(); val move = server.takeRequest(); val delete = server.takeRequest()
            assertEquals("T", move.getHeader("Overwrite"))
            assertEquals("<${server.url("/backup/existing.sleepdown")}> ([$etag])", move.getHeader("If"))
            assertEquals(put.path, delete.path)
            assertEquals("DELETE", delete.method)
            assertEquals(mapOf("/backup/existing.sleepdown" to RemoteFile("backup", "\"uploaded\"")), remote.files)
        }
    }
    @Test fun confirmedOverwriteWithoutUsableStrongEtagFailsBeforeNetworking() = runBlocking {
        val file = temporary.newFile().apply { writeText("replacement backup") }
        val remote = statefulServer()
        val existing = RemoteFile("original backup", "\"v1\"")
        remote.files["/backup/existing.sleepdown"] = existing
        val unusableEtags = listOf(null, "", "v1", "W/\"v1\"", "w/\"v1\"", "\"", "\"v\"1\"",
            "\"v1\", \"v2\"", "\"v 1\"", "\"v\t1\"", "\"v\r1\"", "\"v\n1\"", "\"v\u00001\"",
            "\"v\u007f1\"", "\"v\u00801\"", "\"v\u01001\"", "\"v]1\"", "\"${"v".repeat(510)}\"")
        for (etag in unusableEtags) {
            try { client().upload(file, "existing.sleepdown", true, etag); fail("unsafe overwrite") }
            catch (failure: WebDavFailure) {
                assertEquals("UNSAFE_OVERWRITE", failure.reason.name)
                assertTrue(failure.message!!.contains("更换名称"))
            }
            assertEquals("An unusable validator must not send any request", 0, server.requestCount)
            assertEquals(mapOf("/backup/existing.sleepdown" to existing), remote.files)
        }
    }
    @Test fun destinationChangedAfterConfirmationIsPreservedAndOnlyOwnStageIsCleaned() = runBlocking {
        val file = temporary.newFile().apply { writeText("replacement backup") }
        val remote = statefulServer()
        val destination = "/backup/existing.sleepdown"
        val unrelatedStage = "/backup/.sleepdown-another-client.upload"
        val unrelated = RemoteFile("another client's upload", "\"other\"")
        remote.files[destination] = RemoteFile("confirmed backup", "\"v1\"")
        remote.files[unrelatedStage] = unrelated
        val client = client()
        val confirmed = client.list().single()
        // Another client replaces the destination after the user has selected/confirmed v1.
        val changed = RemoteFile("newer remote backup", "\"v2\"")
        remote.files[destination] = changed
        try { client.upload(file, confirmed.name, true, confirmed.etag); fail("changed destination") }
        catch (failure: WebDavFailure) { assertEquals(WebDavFailure.Reason.CHANGED, failure.reason) }
        assertEquals("PROPFIND", server.takeRequest().method)
        val put = server.takeRequest(); val move = server.takeRequest(); val delete = server.takeRequest()
        assertEquals("PUT", put.method)
        assertTrue(put.path!!.endsWith(".upload"))
        assertEquals("*", put.getHeader("If-None-Match"))
        assertEquals("MOVE", move.method)
        assertEquals("<${server.url(destination)}> ([\"v1\"])", move.getHeader("If"))
        assertEquals("DELETE", delete.method)
        assertEquals(put.path, delete.path)
        assertNotEquals(destination, delete.path)
        assertNotEquals(unrelatedStage, delete.path)
        assertEquals(mapOf(destination to changed, unrelatedStage to unrelated), remote.files)
    }
    @Test fun newNamesIncludingAutomaticBackupsDoNotRequireAnEtag() = runBlocking {
        val file = temporary.newFile().apply { writeText("new backup") }
        val remote = statefulServer()
        val existing = RemoteFile("original backup", "\"v1\"")
        remote.files["/backup/existing.sleepdown"] = existing
        for (name in listOf("new-name.sleepdown", "SleepDown-auto-1234-unique.sleepdown")) {
            client().upload(file, name)
            val put = server.takeRequest(); val move = server.takeRequest(); val delete = server.takeRequest()
            assertEquals("F", move.getHeader("Overwrite"))
            assertNull(move.getHeader("If"))
            assertEquals(put.path, delete.path)
            assertEquals("new backup", remote.files["/backup/$name"]?.content)
        }
        assertEquals(existing, remote.files["/backup/existing.sleepdown"])
        assertEquals(3, remote.files.size)
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
