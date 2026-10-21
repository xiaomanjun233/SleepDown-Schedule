package com.xiaomanjun.sleepdownschedule.feature.importing

import android.app.Application
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, application = Application::class)
class EduWebResourceInterceptorTest {
    private fun request(
        url: String,
        method: String = "GET",
        main: Boolean = true,
        headers: Map<String, String> = emptyMap()
    ) = object : WebResourceRequest {
        override fun getUrl() = Uri.parse(url)
        override fun isForMainFrame() = main
        override fun isRedirect() = false
        override fun hasGesture() = false
        override fun getMethod() = method
        override fun getRequestHeaders() = headers
    }

    private fun interceptor() = EduWebResourceInterceptor(
        OkHttpClient.Builder().followRedirects(false).followSslRedirects(false).build()
    )

    @Test fun brokenLoginPageCanReloadWithoutThePackageHeaderAndKeepSessionAndBrowserHeaders() {
        MockWebServer().use { server ->
            server.start()
            val url = server.url("/eams/loginExt.action").toString()
            val loader = interceptor()
            val headers = mapOf("X-Requested-With" to "com.xiaomanjun.sleepdownschedule",
                "Referer" to server.url("/").toString(), "Accept-Language" to "zh-CN", "Cookie" to "stale=1")
            assertNull(loader.intercept(request(url, headers = headers), "installed-webview"))
            assertEquals(0, server.requestCount)
            assertTrue(loader.policy.enable(url))
            CookieManager.getInstance().setCookie(url, "session=current; Path=/")
            server.enqueue(MockResponse().setHeader("Content-Type", "text/html; charset=UTF-8")
                .setHeader("Set-Cookie", "updated=1; Path=/")
                .setBody("<head><script src='/eams/jquery.js'></script></head><body>登录</body>"))
            val response = loader.intercept(request(url, headers = headers), "installed-webview")!!
            assertTrue(response.data.bufferedReader().use { it.readText() }.contains("<head>"))
            val sent = server.takeRequest()
            assertNull(sent.getHeader("X-Requested-With"))
            assertEquals("installed-webview", sent.getHeader("User-Agent"))
            assertEquals(headers["Referer"], sent.getHeader("Referer"))
            assertEquals("zh-CN", sent.getHeader("Accept-Language"))
            assertTrue(sent.getHeader("Cookie")!!.contains("session=current"))
            assertFalse(sent.getHeader("Cookie")!!.contains("stale"))
            assertTrue(CookieManager.getInstance().getCookie(url).contains("updated=1"))
            assertFalse(loader.policy.enable(url))
        }
    }

    @Test fun gzipScriptIsDecodedWithMatchingResponseHeaders() {
        MockWebServer().use { server ->
            server.start()
            val page = server.url("/eams/loginExt.action").toString()
            val loader = interceptor()
            loader.policy.recordNavigation(page, "GET")
            assertTrue(loader.policy.enable(page))
            val text = "window.jQuery = {}; // 中文"
            val compressed = ByteArrayOutputStream().apply {
                GZIPOutputStream(this).use { it.write(text.toByteArray()) }
            }.toByteArray()
            server.enqueue(MockResponse().setHeader("Content-Type", "text/javascript; charset=utf-8")
                .setHeader("Content-Encoding", "gzip").setBody(Buffer().write(compressed)))
            val response = loader.intercept(request(server.url("/jquery.js").toString(), main = false,
                headers = mapOf("Accept-Encoding" to "gzip, br", "Sec-Fetch-Dest" to "script")), "browser")!!
            assertEquals(text, response.data.bufferedReader().use { it.readText() })
            assertNull(response.responseHeaders.webHeader("Content-Encoding"))
            assertNull(response.responseHeaders.webHeader("Content-Length"))
            assertEquals("gzip", server.takeRequest().getHeader("Accept-Encoding"))
        }
    }

    @Test fun redirectsRemainNativeAndAreNotFollowedUnderTheOriginalDocumentOrigin() {
        MockWebServer().use { server ->
            server.start()
            val page = server.url("/login").toString()
            val loader = interceptor()
            loader.policy.recordNavigation(page, "GET")
            assertTrue(loader.policy.enable(page))
            server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "https://sso.example/login"))
            assertNull(loader.intercept(request(page), "browser"))
            assertEquals(1, server.requestCount)
        }
    }

    @Test fun postNavigationCannotBeForwardedOrAutomaticallyReplayedEvenAtTheSameUrl() {
        val loader = interceptor()
        val page = "https://school.example/login"
        loader.policy.recordNavigation(page, "GET")
        assertTrue(loader.policy.enable(page))
        assertNull(loader.intercept(request(page, method = "POST"), "browser"))
        assertFalse(loader.policy.canReload(page))
        assertFalse(loader.policy.enable(page))
    }
}
