package com.xiaomanjun.sleepdownschedule.feature.importing

import com.sun.net.httpserver.HttpServer
import com.xiaomanjun.sleepdownschedule.CourseEntity
import com.xiaomanjun.sleepdownschedule.PeriodEntity
import com.xiaomanjun.sleepdownschedule.WeekParity
import com.xiaomanjun.sleepdownschedule.defaultConfig
import com.xiaomanjun.sleepdownschedule.model.TimetableCatalog
import java.net.InetSocketAddress
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudShortCodeTest {
    @Test
    fun extractsFormattedAndBareCodes() {
        assertEquals(
            "7K3M9Q2PX4",
            CloudShortCode.extractCode("SleepDown 课表短口令（15 分钟内有效）\nSD-7K3M9-Q2PX4")
        )
        assertEquals("7K3M9Q2PX4", CloudShortCode.extractCode(" 7k3m9-q2px4 "))
        assertEquals("7K3M9Q2PX4", CloudShortCode.extractCode("短口令：7K3M9 Q2PX4"))
        assertNull(CloudShortCode.extractCode("早上八点的高等数学"))
    }

    @Test
    fun normalizesCrockfordCharactersAndDisplayPrefix() {
        assertEquals("0117K3M9Q2", CloudShortCode.normalize("OIL7K3M9Q2"))
        assertEquals("7K3M9Q2PX4", CloudShortCode.normalize("SD-7K3M9-Q2PX4"))
        assertNull(CloudShortCode.normalize("not a code"))
        assertEquals("SD-7K3M9-Q2PX4", CloudShortCode.display("7K3M9Q2PX4"))
    }

    @Test
    fun compactPayloadStillImportsLocally() {
        val config = defaultConfig().copy(totalWeeks = 16)
        val periods = listOf(PeriodEntity(1, "08:00", "08:45"), PeriodEntity(2, "08:55", "09:40"))
        val course = CourseEntity(
            name = "高等数学", teacher = "林老师", location = "A101", weekday = 1,
            periods = listOf(1, 2), weeks = (1..16).toList(), weekParity = WeekParity.ALL, note = null
        )
        val payload = CloudShortCode.buildPayload(config, periods, listOf(course), TimetableCatalog())
        val restored = ScheduleImportParser.parse(payload, config).getOrThrow().courses.single()
        assertEquals(course.name, restored.name)
        assertEquals(course.periods, restored.periods)
        assertTrue(payload.length < buildCanonicalScheduleJson(config, periods, listOf(course), TimetableCatalog()).length)
    }

    @Test
    fun uploadsAndResolvesThroughTheCloudContract() {
        val payload = """{"courses":[{"name":"高等数学","day":1,"weeks":[1]}],"timeSlots":[]}"""
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/api/v1/short-codes") { exchange ->
            val body = exchange.requestBody.use { it.readBytes().toString(Charsets.UTF_8) }
            val path = exchange.requestURI.path
            val response = when {
                path.endsWith("/resolve") -> {
                    assertTrue(body.contains("code"))
                    """{"payload":${JsonPrimitive(payload)},"expiresAt":1760000900}"""
                }
                exchange.requestMethod == "POST" -> {
                    assertTrue(body.contains("payload"))
                    """{"code":"7K3M9Q2PX4","expiresAt":1760000900}"""
                }
                else -> """{"error":{"code":"METHOD_NOT_ALLOWED"}}"""
            }
            val bytes = response.toByteArray(Charsets.UTF_8)
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(if (path.endsWith("/resolve")) 200 else 201, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            val baseUrl = "http://127.0.0.1:${server.address.port}"
            val created = CloudShortCode.create(payload, baseUrl)
            assertEquals("7K3M9Q2PX4", created.code)
            assertEquals("SD-7K3M9-Q2PX4", created.displayCode)
            val resolved = CloudShortCode.resolve("SD-7K3M9-Q2PX4", baseUrl)
            assertEquals(payload, resolved)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun missingCloudCodeHasAUserFacingMessage() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/api/v1/short-codes") { exchange ->
            val bytes = """{"error":{"code":"SHORT_CODE_NOT_FOUND"}}""".toByteArray()
            exchange.sendResponseHeaders(404, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            val error = assertThrows(IllegalArgumentException::class.java) {
                CloudShortCode.resolve("SD-7K3M9-Q2PX4", "http://127.0.0.1:${server.address.port}")
            }
            assertTrue(error.message.orEmpty().contains("不存在或已过期"))
        } finally {
            server.stop(0)
        }
    }
}
