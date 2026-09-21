package com.xiaomanjun.sleepdownschedule.feature.coloros

import com.xiaomanjun.sleepdownschedule.model.ScheduleProfileEntity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class ColorOSCourseProviderContractTest {
    @Test
    fun enablesColorOSAndHonorExperimentFamilies() {
        assertTrue(isCourseCloudExperimentDevice("OPPO", "OPPO", null))
        assertTrue(isCourseCloudExperimentDevice("OnePlus", "OnePlus", null))
        assertTrue(isCourseCloudExperimentDevice("realme", "realme", null))
        assertTrue(isCourseCloudExperimentDevice("HONOR", "HONOR", null))
        assertTrue(isCourseCloudExperimentDevice("unknown", "unknown", "ColorOS 16"))
        assertEquals(false, isCourseCloudExperimentDevice("Xiaomi", "Redmi", null))
    }

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
        val now = Instant.parse("2026-09-20T18:10:37Z").toEpochMilli()
        val expiresAt = ColorOSCourseTestPreview.expiresAt(now)
        val today = LocalDate.of(2026, 9, 21)
        val laterStart = today.atTime(10, 0).atZone(zone).toEpochSecond()
        val laterCourse = """[{"id":1,"courseName":"上午课程","startTimestamp":$laterStart,"endTimestamp":${laterStart + 3_600}}]"""

        val courses = Json.parseToJsonElement(
            ColorOSCourseTestPreview.append(laterCourse, today, zone, now, expiresAt)
        ).jsonArray
        val preview = courses.first().jsonObject

        assertEquals("SleepDown 流体云测试", preview.getValue("courseName").jsonPrimitive.content)
        assertEquals("02:32", preview.getValue("startTime").jsonPrimitive.content)
        assertEquals("02:37", preview.getValue("endTime").jsonPrimitive.content)
        assertEquals(Instant.parse("2026-09-20T18:32:00Z").epochSecond, preview.getValue("startTimestamp").jsonPrimitive.long)
        assertEquals(Instant.parse("2026-09-20T18:37:00Z").epochSecond, preview.getValue("endTimestamp").jsonPrimitive.long)
        assertEquals(
            5 * 60L,
            preview.getValue("endTimestamp").jsonPrimitive.long -
                preview.getValue("startTimestamp").jsonPrimitive.long
        )
        assertTrue(preview.getValue("id").jsonPrimitive.long in 1..Int.MAX_VALUE.toLong())
        assertEquals("", preview.getValue("extra").jsonPrimitive.content)
        assertEquals("上午课程", courses.last().jsonObject.getValue("courseName").jsonPrimitive.content)
        assertEquals("[]", ColorOSCourseTestPreview.append("[]", today.plusDays(1), zone, now, expiresAt))
        assertEquals("[]", ColorOSCourseTestPreview.append("[]", today, zone, expiresAt, expiresAt))

        val lateNow = Instant.parse("2026-09-21T15:50:37Z").toEpochMilli()
        val lateExpiresAt = ColorOSCourseTestPreview.expiresAt(lateNow)
        val nextDayPreview = Json.parseToJsonElement(
            ColorOSCourseTestPreview.append("[]", today.plusDays(1), zone, lateNow, lateExpiresAt)
        ).jsonArray.single().jsonObject
        assertEquals("00:12", nextDayPreview.getValue("startTime").jsonPrimitive.content)
        assertEquals("[]", ColorOSCourseTestPreview.append("[]", today, zone, lateNow, lateExpiresAt))
    }
}
