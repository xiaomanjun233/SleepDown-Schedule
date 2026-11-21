package com.xiaomanjun.sleepdownschedule.feature.schedule.autorefresh

import android.content.Context
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.xiaomanjun.sleepdownschedule.AppState
import com.xiaomanjun.sleepdownschedule.core.web.releaseSleepDownWebView
import com.xiaomanjun.sleepdownschedule.feature.importing.eduImportCookieManager
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AutoRefreshWebSessionTest {
    @Test fun backgroundFetchUsesTheVisibleBrowsersScopedSession() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        withContext(Dispatchers.Main) { assumeTrue(AutoRefreshWebSession.isSupported()) }
        ServerSocket(0, 8, InetAddress.getByName("127.0.0.1")).use { server ->
            val responder = thread(isDaemon = true) { serveFixture(server) }
            val baseUrl = "http://127.0.0.1:${server.localPort}"
            val profile = AutoRefreshScheduleProfile(
                schoolId = "fixture", schoolName = "Fixture", adapterId = "fixture", adapterName = "Fixture",
                scheduleId = 1, bindingId = AutoRefreshWebSession.newBindingId(),
                isolatedBrowser = true, authenticatedUrl = "$baseUrl/edu/timetable"
            )
            val profileName = requireNotNull(profile.browserProfileName)
            val other = profile.copy(bindingId = AutoRefreshWebSession.newBindingId())
            try {
                openLogin(context, profileName, "$baseUrl/login")
                val draft = withTimeout(20_000L) {
                    AutoRefreshShiguangRunner.fetch(context, profile, AppState(), FixtureScript).draft
                }
                assertEquals("模拟课程", draft.courses.single().name)
                val failure = runCatching {
                    withTimeout(20_000L) {
                        AutoRefreshShiguangRunner.fetch(context, other, AppState(), FixtureScript)
                    }
                }.exceptionOrNull()
                assertEquals(RefreshConnection.SignInRequired, (failure as? RefreshFailure)?.connection)
            } finally {
                withContext(Dispatchers.Main) {
                    AutoRefreshWebSession.retire(profileName)
                    AutoRefreshWebSession.retire(other.browserProfileName)
                }
                server.close()
                responder.join(1_000)
            }
        }
    }

    private suspend fun openLogin(context: Context, profileName: String, url: String) =
        withContext(Dispatchers.Main) {
            val visibleBrowser = WebView(context)
            try {
                visibleBrowser.eduImportCookieManager(standaloneImport = false, profileName = profileName)
                withTimeout(15_000L) {
                    suspendCancellableCoroutine { loaded ->
                        visibleBrowser.webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView, currentUrl: String?) {
                                if (currentUrl == url && loaded.isActive) loaded.resume(Unit)
                            }
                        }
                        visibleBrowser.loadUrl(url)
                    }
                }
                AutoRefreshWebSession.flush(visibleBrowser)
            } finally {
                visibleBrowser.releaseSleepDownWebView(clearResourceCache = false)
            }
        }

    private fun serveFixture(server: ServerSocket) {
        while (!server.isClosed) {
            val socket = runCatching { server.accept() }.getOrNull() ?: return
            try {
                socket.use(::respond)
            } catch (_: IOException) {
                // Chromium can close speculative connections without sending a request.
            }
        }
    }

    private fun respond(socket: Socket) {
        socket.soTimeout = 2_000
        val request = socket.getInputStream().bufferedReader()
        val path = request.readLine()?.split(' ')?.getOrNull(1) ?: return
        val headers = generateSequence { request.readLine()?.takeIf(String::isNotEmpty) }.toList()
        val hasSession = headers.any { it.startsWith("Cookie:", ignoreCase = true) && it.contains("fixture=valid") }
        val (status, body) = when {
            path == "/login" -> "200 OK\r\nSet-Cookie: fixture=valid; Path=/edu; HttpOnly" to "<!doctype html><title>Login</title>"
            path == "/edu/timetable" && hasSession -> "200 OK" to "<!doctype html><title>Timetable</title>"
            path == "/edu/courses" && hasSession -> "200 OK" to
                """[{"name":"模拟课程","teacher":"教师","position":"101","day":1,"startSection":1,"endSection":2,"weeks":[1,2]}]"""
            else -> "401 Unauthorized" to ""
        }
        val bytes = body.toByteArray()
        val output = socket.getOutputStream()
        output.write(("HTTP/1.1 $status\r\nContent-Type: text/html; charset=utf-8\r\n" +
            "Content-Length: ${bytes.size}\r\nConnection: close\r\n\r\n").toByteArray())
        output.write(bytes)
        output.flush()
    }

    private companion object {
        val FixtureScript = """
            (async () => {
                const response = await fetch('/edu/courses');
                if (response.status === 401) {
                    window.shiguangBridge.showToast('unauthorized');
                    return;
                }
                await window.shiguangBridgePromise.saveImportedCourses(await response.text());
                window.shiguangBridge.notifyTaskCompletion();
            })();
        """.trimIndent()
    }
}
