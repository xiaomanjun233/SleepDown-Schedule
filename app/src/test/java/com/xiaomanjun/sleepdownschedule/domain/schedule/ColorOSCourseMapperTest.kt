package com.xiaomanjun.sleepdownschedule.domain.schedule

import com.xiaomanjun.sleepdownschedule.model.AppState
import com.xiaomanjun.sleepdownschedule.model.CourseEntity
import com.xiaomanjun.sleepdownschedule.model.PeriodEntity
import com.xiaomanjun.sleepdownschedule.model.WeekParity
import com.xiaomanjun.sleepdownschedule.model.defaultConfig
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class ColorOSCourseMapperTest {
    private val scheduleId = 7
    private val termStart = LocalDate.of(2026, 9, 14)
    private val wednesday = termStart.plusDays(2)
    private val periods = listOf(
        PeriodEntity(1, "08:00", "08:45", scheduleId),
        PeriodEntity(2, "08:55", "09:40", scheduleId),
        PeriodEntity(3, "10:00", "10:45", scheduleId),
        PeriodEntity(4, "10:55", "11:40", scheduleId)
    )
    private val config = defaultConfig(scheduleId).copy(
        autoCurrentWeek = true,
        termStartDate = termStart.toString(),
        totalWeeks = 4
    )

    @Test
    fun exportsSplitPeriodsCustomTimesStableIdsAndZoneTimestamps() {
        val ordinary = course(
            id = 10,
            name = "离散数学",
            periods = listOf(1, 2, 4),
            color = 0xFFAABBCCL
        )
        val custom = course(
            id = 20,
            name = "实验课",
            periods = emptyList(),
            customStart = "13:05",
            customEnd = "14:10"
        )
        val invalid = course(id = 30, name = "无效节次", periods = listOf(99))
        val state = AppState(courses = listOf(ordinary, custom, invalid), config = config, periods = periods)

        val export = ColorOSCourseMapper.export(wednesday, state, ZoneId.of("Asia/Shanghai"))
        val rows = Json.parseToJsonElement(export.json).jsonArray.map { it.jsonObject }

        assertEquals(3, export.exportedCount)
        assertEquals(1, export.skippedCount)
        assertEquals(listOf("08:00", "10:55", "13:05"), rows.map { it.getValue("startTime").jsonPrimitive.content })
        assertEquals(listOf("09:40", "11:40", "14:10"), rows.map { it.getValue("endTime").jsonPrimitive.content })
        assertEquals(listOf("10001", "10004", "20000"), rows.map { it.getValue("id").jsonPrimitive.content })
        assertEquals("#ffaabbcc", rows.first().getValue("color").jsonPrimitive.content)
        assertEquals(
            wednesday.atTime(LocalTime.of(8, 0)).atZone(ZoneId.of("Asia/Shanghai")).toEpochSecond().toString(),
            rows.first().getValue("startTimestamp").jsonPrimitive.content
        )
    }

    @Test
    fun appliesParityAndScheduleAdjustmentsBeforeExporting() {
        val course = course(
            id = 42,
            name = "高数",
            periods = listOf(1),
            weeks = listOf(1, 2),
            parity = WeekParity.ODD
        )
        val saturday = termStart.plusDays(5)
        val adjustedConfig = config.copy(
            scheduleAdjustmentsJson = encodeScheduleAdjustments(
                listOf(
                    ScheduleAdjustment(wednesday.toString()),
                    ScheduleAdjustment(saturday.toString(), wednesday.toString())
                )
            )
        )
        val state = AppState(courses = listOf(course), config = adjustedConfig, periods = periods)

        assertEquals(0, ColorOSCourseMapper.export(wednesday, state).exportedCount)
        assertEquals(1, ColorOSCourseMapper.export(saturday, state).exportedCount)
        assertTrue(ColorOSCourseMapper.export(wednesday.plusWeeks(1), state).json == "[]")
    }

    @Test
    fun exportsOnlyTheActiveScheduleSnapshot() {
        val active = course(id = 1, name = "当前课表", periods = listOf(1))
        val inactive = course(id = 2, name = "其他课表", periods = listOf(1)).copy(scheduleId = 8)
        val state = AppState(
            courses = listOf(active),
            allCourses = listOf(active, inactive),
            config = config,
            periods = periods,
            allPeriods = periods + PeriodEntity(1, "09:00", "09:45", 8)
        )

        val names = Json.parseToJsonElement(ColorOSCourseMapper.export(wednesday, state).json)
            .jsonArray
            .map { it.jsonObject.getValue("courseName").jsonPrimitive.content }

        assertEquals(listOf("当前课表"), names)
    }

    private fun course(
        id: Long,
        name: String,
        periods: List<Int>,
        weeks: List<Int> = listOf(1),
        parity: WeekParity = WeekParity.ALL,
        color: Long? = null,
        customStart: String? = null,
        customEnd: String? = null
    ) = CourseEntity(
        id = id,
        name = name,
        teacher = "教师",
        location = "教室",
        weekday = 3,
        periods = periods,
        weeks = weeks,
        weekParity = parity,
        note = null,
        customStartTime = customStart,
        customEndTime = customEnd,
        customColorArgb = color,
        scheduleId = scheduleId
    )
}
