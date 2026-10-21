package com.xiaomanjun.sleepdownschedule.feature.importing.shiguang

import android.net.Uri
import android.app.Application
import android.webkit.WebResourceRequest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, application = Application::class)
class ShiguangWebRequestInterceptorTest {
    private fun request(url: String, id: String) = object : WebResourceRequest {
        override fun getUrl() = Uri.parse(url)
        override fun isForMainFrame() = false
        override fun isRedirect() = false
        override fun hasGesture() = false
        override fun getMethod() = "POST"
        override fun getRequestHeaders() = mapOf("X-WebView-Post-Id" to id)
    }

    @Test fun failedSubmissionReturnsAnErrorInsteadOfResubmittingThroughWebView() {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
            val url = server.url("/submit").toString()
            val interceptor = ShiguangWebRequestInterceptor().apply { documentUrl = url }
            assertTrue(interceptor.bridge.register("one", "name=test", "text/plain", url, "POST", true))
            val result = interceptor.intercept(request(url, "one"))
            assertNotNull(result)
            assertEquals(502, result!!.statusCode)
            result.data.close()
            assertEquals(1, server.requestCount)
            val consumed = interceptor.intercept(request(url, "one"))!!
            assertEquals(409, consumed.statusCode)
            consumed.data.close()
            assertEquals(1, server.requestCount)
        }
    }

    @Test fun javascriptBridgeCannotForwardCrossOriginFetchOrUseAClosedWindow() {
        val interceptor = ShiguangWebRequestInterceptor().apply { documentUrl = "https://school.test/" }
        val url = "https://different.test/submit"
        assertTrue(interceptor.bridge.register("cross", "private", "text/plain", url, "POST", true))
        val blocked = interceptor.intercept(request(url, "cross"))!!
        assertEquals(403, blocked.statusCode)
        blocked.data.close()
        interceptor.close()
        assertFalse(interceptor.bridge.register("closed", "private", "", url, "POST", true))
    }
}
