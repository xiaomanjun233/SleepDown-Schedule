package com.xiaomanjun.sleepdownschedule.feature.importing

import com.xiaomanjun.sleepdownschedule.*

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class ScheduleImportCustomCourseTest {
    @Test fun malformedTimeAlignedSourceFailsBeforeImport() {
        val config = defaultConfig().copy(periodAlignmentMode = com.xiaomanjun.sleepdownschedule.model.PeriodAlignmentMode.TIME)
        val token = buildSleepDownScheduleToken(config, listOf(PeriodEntity(1, "08:00", "08:45")),
            listOf(CourseEntity(name = "课程", teacher = null, location = null, weekday = 1,
                periods = listOf(13), weeks = listOf(1), weekParity = WeekParity.ALL, note = null, originalPeriodTimes = "")))
        val result = ScheduleImportParser.parse(token, defaultConfig())
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("不完整"))
    }

    @Test fun exactCustomImportDoesNotRequireAnExistingAnchorNumber() {
        val payload = """{"schemaVersion":1,"scheduleConfig":{"totalWeeks":1,"periods":[{"index":1,"startTime":"08:00","endTime":"08:45"}]},"courses":[{"name":"课程","weekday":1,"periods":[14],"weeks":[1],"customStartTime":"12:01","customEndTime":"12:29"}]}"""
        val draft = ScheduleImportParser.parse(payload, defaultConfig()).getOrThrow()
        assertEquals(listOf(14), draft.courses.single().periods)
        assertEquals("12:01", draft.courses.single().customStartTime)
    }
    @Test fun switchingSourceAndModeSurviveSharingAClippedCourse() {
        val bells = defaultPeriods()
        val raw = com.xiaomanjun.sleepdownschedule.domain.schedule.captureOriginalPeriodTimes(
            CourseEntity(9, "课程", null, null, 1, listOf(11, 12), listOf(1), WeekParity.ALL, null), bells)
        val config = defaultConfig().copy(periodAlignmentMode = com.xiaomanjun.sleepdownschedule.model.PeriodAlignmentMode.TIME)
        val clipped = com.xiaomanjun.sleepdownschedule.domain.schedule.projectCourseArrangement(raw, config, bells.take(10))
        val token = buildSleepDownScheduleToken(config, bells.take(10), listOf(clipped))
        val imported = ScheduleImportParser.parse(token, defaultConfig()).getOrThrow()
        assertEquals(raw.periods, imported.courses.single().periods)
        assertEquals(raw.originalPeriodTimes, imported.courses.single().originalPeriodTimes)
        assertEquals(config.periodAlignmentMode, imported.config.periodAlignmentMode)
    }
    @Test
    fun aiToolExposesPerCourseExactTimeFields() {
        val courseSchema = scheduleImportChatTool()["function"]!!.jsonObject["parameters"]!!
            .jsonObject["properties"]!!.jsonObject["courses"]!!.jsonObject["items"]!!.jsonObject
        val required = courseSchema["required"]!!.jsonArray.map { it.jsonPrimitive.content }
        val properties = courseSchema["properties"]!!.jsonObject

        assertTrue("customStartTime" in required)
        assertTrue("customEndTime" in required)
        assertTrue("customStartTime" in properties)
        assertTrue("customEndTime" in properties)
        assertFalse("customPeriodTimes" in required)
        assertFalse("customPeriodTimes" in properties)
    }

    @Test
    fun sameSectionCoursesKeepDistinctClockTimesFromImportedJson() {
        val payload = """{
          "schemaVersion":1,
          "scheduleConfig":{"totalWeeks":2,"periods":[
            {"index":3,"startTime":"10:20","endTime":"11:00"},
            {"index":4,"startTime":"11:15","endTime":"11:55"}]},
          "courses":[
            {"name":"实训 A","weekday":3,"periods":[3,4],"weeks":[1],"customStartTime":"10:10","customEndTime":"11:45"},
            {"name":"实训 B","weekday":3,"periods":[3,4],"weeks":[2],"customStartTime":"10:20","customEndTime":"11:55"}
          ]
        }"""

        val draft = ScheduleImportParser.parse(payload, defaultConfig()).getOrThrow()

        assertEquals(listOf("10:10", "10:20"), draft.courses.map { it.customStartTime })
        assertEquals(listOf("11:45", "11:55"), draft.courses.map { it.customEndTime })
        assertEquals(listOf(listOf(3, 4), listOf(3, 4)), draft.courses.map { it.periods })
        assertEquals(listOf(null, null), draft.courses.map { it.customPeriodTimes })
    }

    @Test
    fun sleepDownTokenRoundTripPreservesExactTimeAndCourseColor() {
        val config = defaultConfig().copy(totalWeeks = 16)
        val periods = listOf(
            PeriodEntity(1, "08:00", "08:45"),
            PeriodEntity(2, "08:55", "09:40")
        )
        val source = CourseEntity(
            name = "高等数学",
            teacher = "林老师",
            location = "A101",
            weekday = 1,
            periods = listOf(1, 2),
            weeks = (1..16).toList(),
            weekParity = WeekParity.ALL,
            note = null,
            customStartTime = "08:12",
            customEndTime = "09:26",
            customColorArgb = 0xFF6688AAL,
            customPeriodTimes = "1,08:12-08:38;2,08:50-09:26"
        )

        val token = buildSleepDownScheduleToken(config, periods, listOf(source))
        val imported = ScheduleImportParser.parse(token, defaultConfig()).getOrThrow().courses.single()

        assertEquals(12, token.lineSequence().first { it.startsWith("C=") }.removePrefix("C=").split('|').size)
        assertEquals("08:12", imported.customStartTime)
        assertEquals("09:26", imported.customEndTime)
        assertEquals(0xFF6688AAL, imported.customColorArgb)
        assertEquals(source.customPeriodTimes, imported.customPeriodTimes)
    }

    @Test
    fun ordinarySharedTokenKeepsTheExistingElevenFieldLayout() {
        val token = buildSleepDownScheduleToken(
            defaultConfig(),
            listOf(PeriodEntity(1, "08:00", "08:45")),
            listOf(CourseEntity(name = "普通课", teacher = null, location = null, weekday = 1,
                periods = listOf(1), weeks = listOf(1), weekParity = WeekParity.ALL, note = null))
        )
        assertEquals(11, token.lineSequence().first { it.startsWith("C=") }.removePrefix("C=").split('|').size)
    }

    @Test
    fun aiJsonCannotCreateImportedPerPeriodBells() {
        val payload = """{"schemaVersion":1,"scheduleConfig":{"totalWeeks":1,"periods":[{"index":1,"startTime":"08:00","endTime":"08:45"}]},"courses":[{"name":"课程","weekday":1,"periods":[1],"weeks":[1],"customPeriodTimes":"1,08:10-08:40"}]}"""
        assertTrue(ScheduleImportParser.parse(payload, defaultConfig()).isFailure)
    }
}
