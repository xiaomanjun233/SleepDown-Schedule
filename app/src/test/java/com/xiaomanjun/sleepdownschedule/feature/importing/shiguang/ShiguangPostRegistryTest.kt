package com.xiaomanjun.sleepdownschedule.feature.importing.shiguang

import org.junit.Assert.*
import org.junit.Test

class ShiguangPostRegistryTest {
    private val url = "https://school.test/submit?token=a%2Bb&value=one+two"

    @Test fun bodiesAreWindowLocalSingleUseAndBoundToTheirActualRequest() {
        val first = ShiguangPostRegistry()
        val second = ShiguangPostRegistry()
        assertTrue(first.register("p1", "content", "text/plain", url, "POST", true))
        assertNull(second.take("p1", url, "POST"))
        assertEquals("content", first.take("p1", url, "POST")?.body)
        assertNull(first.take("p1", url, "POST"))
        assertTrue(first.register("p2", "content", "", url, "POST", true))
        assertNull(first.take("p2", "https://another.test/", "POST"))
        assertTrue(first.register("p3", "content", "", url, "POST", true))
        assertNull(first.take("p3", url, "GET"))
    }

    @Test fun cancelledRequestsExpireAndClosedWindowsCannotRetainMoreBodies() {
        var now = 0L
        val registry = ShiguangPostRegistry { now }
        repeat(ShiguangPostRegistry.MaxCount) { assertTrue(registry.register("p$it", "x", "", url, "POST", true)) }
        assertFalse(registry.register("overflow", "x", "", url, "POST", true))
        now += ShiguangPostRegistry.LifetimeMillis
        assertNull(registry.take("p0", url, "POST"))
        assertTrue(registry.register("fresh", "x", "", url, "POST", true))
        registry.close()
        assertNull(registry.take("fresh", url, "POST"))
        assertFalse(registry.register("after_close", "x", "", url, "POST", true))
    }

    @Test fun countAndUtf8BytesHaveIndependentLimits() {
        val registry = ShiguangPostRegistry()
        assertFalse(registry.register("huge", "课".repeat(ShiguangPostRegistry.MaxBodyBytes / 2), "", url, "POST", true))
        val body = "x".repeat(ShiguangPostRegistry.MaxBodyBytes)
        repeat(ShiguangPostRegistry.MaxPendingBytes / body.length) {
            assertTrue(registry.register("p$it", body, "", url, "POST", true))
        }
        assertFalse(registry.register("overflow", "x", "", url, "POST", true))
    }

    @Test fun strippingBridgeMarkerDoesNotReencodeSignedQueryParameters() {
        assertEquals(url, withoutInternalPostId("$url&_webview_post_id=post_1"))
        assertEquals("$url#section", withoutInternalPostId("$url&_webview_post_id=post_1#section"))
        assertTrue(sameWebOrigin("https://school.test:443/path", "https://school.test/other"))
        assertFalse(sameWebOrigin(url, "http://school.test/"))
        assertFalse(sameWebOrigin(url, "https://school.test:444/"))
    }
}
