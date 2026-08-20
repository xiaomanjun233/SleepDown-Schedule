package com.xiaomanjun.sleepdownschedule

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteConfigClientTest {
    @Test
    fun bootstrapUsesEtagAndAccepts304() {
        val receivedEtag = AtomicReference<String?>(null)
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/api/v1/bootstrap") { exchange ->
                receivedEtag.set(exchange.requestHeaders.getFirst("If-None-Match"))
                if (receivedEtag.get() == "W/\"bootstrap-9-v26\"") {
                    exchange.sendResponseHeaders(304, -1)
                } else {
                    val body = """{"schemaVersion":1,"serverTime":1787000000,"notices":[],"agreements":{"privacy":null,"terms":null},"ai":null}"""
                        .toByteArray()
                    exchange.responseHeaders.add("Content-Type", "application/json")
                    exchange.responseHeaders.add("ETag", "W/\"bootstrap-9-v26\"")
                    exchange.sendResponseHeaders(200, body.size.toLong())
                    exchange.responseBody.use { it.write(body) }
                }
                exchange.close()
            }
            start()
        }
        try {
            val client = RemoteConfigClient("http://127.0.0.1:${server.address.port}")
            val first = client.fetchBootstrap(null)
            assertTrue(first is BootstrapFetchResult.Updated)
            first as BootstrapFetchResult.Updated
            assertEquals("W/\"bootstrap-9-v26\"", first.etag)

            val second = client.fetchBootstrap(first.etag)
            assertEquals(BootstrapFetchResult.NotModified, second)
            assertEquals(first.etag, receivedEtag.get())
        } finally {
            server.stop(0)
        }
    }
}
