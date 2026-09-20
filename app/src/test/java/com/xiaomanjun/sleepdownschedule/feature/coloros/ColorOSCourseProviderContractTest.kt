package com.xiaomanjun.sleepdownschedule.feature.coloros

import com.xiaomanjun.sleepdownschedule.model.ScheduleProfileEntity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class ColorOSCourseProviderContractTest {
    @Test
    fun exposesEveryWakeUpCompatibleEndpoint() {
        assertEquals(
            setOf("has_init", "show_table_id", "table_list", "course_list", "next_course_list"),
            ColorOSCourseProviderContract.supportedPaths
        )
    }

    @Test
    fun buildsWakeUpCompatibleMetadataPayloads() {
        val initialized = Json.parseToJsonElement(ColorOSCourseProviderContract.hasInitJson(true)).jsonObject
        val shownTable = Json.parseToJsonElement(ColorOSCourseProviderContract.showTableIdJson(7)).jsonObject
        val tables = Json.parseToJsonElement(
            ColorOSCourseProviderContract.tableListJson(
                listOf(
                    ScheduleProfileEntity(7, "本学期", true),
                    ScheduleProfileEntity(8, "历史课表", false)
                )
            )
        ).jsonArray

        assertTrue(initialized.getValue("has_init").jsonPrimitive.boolean)
        assertEquals(7, shownTable.getValue("table_id").jsonPrimitive.int)
        assertEquals(listOf(7, 8), tables.map { it.jsonObject.getValue("id").jsonPrimitive.int })
        assertEquals(listOf("本学期", "历史课表"), tables.map { it.jsonObject.getValue("tableName").jsonPrimitive.content })
    }

    @Test
    fun resolvesBasicIsoSecondsAndMillisInTheRequestedZone() {
        val zone = ZoneId.of("Asia/Shanghai")
        val fallback = LocalDate.of(2026, 9, 21)
        val expected = LocalDate.of(2026, 9, 22)
        val seconds = expected.atStartOfDay(zone).toEpochSecond().toString()
        val millis = expected.atStartOfDay(zone).toInstant().toEpochMilli().toString()

        assertEquals(expected, ColorOSCourseProviderContract.requestedDate(listOf("course_list", "20260922"), zone, fallback))
        assertEquals(expected, ColorOSCourseProviderContract.requestedDate(listOf("course_list", seconds), zone, fallback))
        assertEquals(expected, ColorOSCourseProviderContract.requestedDate(listOf("course_list", millis), zone, fallback))
        assertEquals(fallback, ColorOSCourseProviderContract.requestedDate(listOf("course_list", "bad-date"), zone, fallback))
    }

    @Test
    fun testPreviewIsExplicitCurrentDayOnlyAndExpires() {
        val zone = ZoneId.of("Asia/Shanghai")
        val now = Instant.parse("2026-09-20T18:10:00Z").toEpochMilli()
        val expiresAt = now + 3 * 60 * 1_000L
        val today = LocalDate.of(2026, 9, 21)

        val preview = Json.parseToJsonElement(
            ColorOSCourseTestPreview.append("[]", today, zone, now, expiresAt)
        ).jsonArray.single().jsonObject

        assertEquals("SleepDown 流体云测试", preview.getValue("courseName").jsonPrimitive.content)
        assertEquals("测试课程将在 3 分钟后自动结束", preview.getValue("extra").jsonPrimitive.content)
        assertEquals("[]", ColorOSCourseTestPreview.append("[]", today.plusDays(1), zone, now, expiresAt))
        assertEquals("[]", ColorOSCourseTestPreview.append("[]", today, zone, expiresAt, expiresAt))
    }
}
