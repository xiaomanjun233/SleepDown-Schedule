package com.example.courseschedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class DayAgentTimelineEngineTest {
    private val date = LocalDate.of(2026, 7, 15)

    @Test
    fun selectsBeforeClassAndFillsLiveCountdown() {
        val facts = factsAt(
            hour = 7,
            minute = 30,
            today = listOf(slot("数据库原理", "一教 203", 8, 0, 8, 45))
        )

        val rendered = TodayAgentTimelineEngine.render(DailyAgentPack(), facts)

        assertEquals(AgentTemplateKind.BEFORE_NEXT_CLASS, rendered.kind)
        assertTrue(rendered.text.contains("数据库原理"))
        assertTrue(rendered.text.contains("30分钟"))
    }

    @Test
    fun selectsDuringClass() {
        val facts = factsAt(
            hour = 8,
            minute = 20,
            today = listOf(slot("数据库原理", "一教 203", 8, 0, 8, 45))
        )

        val rendered = TodayAgentTimelineEngine.render(DailyAgentPack(), facts)

        assertEquals(AgentTemplateKind.DURING_CLASS, rendered.kind)
        assertTrue(rendered.text.contains("08:45"))
    }

    @Test
    fun selectsLongBreakBetweenCourses() {
        val facts = factsAt(
            hour = 9,
            minute = 10,
            today = listOf(
                slot("高等数学", "二教 101", 8, 0, 8, 45),
                slot("大学英语", "三教 302", 10, 0, 10, 45)
            )
        )

        val rendered = TodayAgentTimelineEngine.render(DailyAgentPack(), facts)

        assertEquals(AgentTemplateKind.LONG_BREAK, rendered.kind)
        assertTrue(rendered.text.contains("1小时15分钟"))
    }

    @Test
    fun showsTomorrowPreviewAfterLastClass() {
        val tomorrow = slot("材料力学", "北区 38-0304", 8, 0, 8, 45, date.plusDays(1))
        val facts = factsAt(
            hour = 20,
            minute = 0,
            today = listOf(slot("数据库原理", "一教 203", 8, 0, 8, 45)),
            tomorrow = listOf(tomorrow)
        )

        val rendered = TodayAgentTimelineEngine.render(DailyAgentPack(), facts)

        assertEquals(AgentTemplateKind.TOMORROW_PREVIEW, rendered.kind)
        assertTrue(rendered.text.contains("材料力学"))
    }

    @Test
    fun rejectsTemplatesWithUnknownPlaceholders() {
        val validated = validateAgentTemplates(
            mapOf(
                AgentTemplateKind.MORNING_OVERVIEW.name to "今天有 {{todayCourseCount}} 门课",
                AgentTemplateKind.BEFORE_NEXT_CLASS.name to "请前往 {{inventedLocation}}",
                "UNKNOWN" to "无效模板"
            )
        )

        assertTrue(AgentTemplateKind.MORNING_OVERVIEW.name in validated)
        assertFalse(AgentTemplateKind.BEFORE_NEXT_CLASS.name in validated)
        assertFalse("UNKNOWN" in validated)
    }

    @Test
    fun parsesConfirmedCourseDraftIntoActiveSchedule() {
        val facts = factsAt(9, 0, emptyList()).copy(
            periodDefinitions = listOf(
                PeriodEntity(1, "08:00", "08:45", 7),
                PeriodEntity(2, "08:55", "09:40", 7)
            ),
            totalWeeks = 18,
            scheduleId = 7
        )
        val response = "可以，先确认下面的课程。<course_draft>{\"name\":\"高等数学\",\"weekday\":3,\"periods\":[1,2],\"weeks\":[1,2,3],\"weekParity\":\"ALL\"}</course_draft>"

        val parsed = parseAgentCourseDraft(response, facts)

        assertEquals("可以，先确认下面的课程。", parsed.displayText)
        assertEquals("高等数学", parsed.course?.name)
        assertEquals(7, parsed.course?.scheduleId)
        assertEquals(listOf(1, 2), parsed.course?.periods)
    }

    private fun factsAt(
        hour: Int,
        minute: Int,
        today: List<AgentCourseSlot>,
        tomorrow: List<AgentCourseSlot> = emptyList()
    ) = DayAgentFacts(
        date = date,
        now = LocalDateTime.of(date, LocalTime.of(hour, minute)),
        today = today,
        tomorrow = tomorrow,
        week = today + tomorrow,
        weather = null,
        sourceHash = "test"
    )

    private fun slot(
        name: String,
        location: String,
        startHour: Int,
        startMinute: Int,
        endHour: Int,
        endMinute: Int,
        targetDate: LocalDate = date
    ) = AgentCourseSlot(
        course = CourseEntity(
            id = name.hashCode().toLong(),
            name = name,
            teacher = null,
            location = location,
            weekday = targetDate.dayOfWeek.value,
            periods = listOf(1),
            weeks = listOf(1),
            weekParity = WeekParity.ALL,
            note = null,
            scheduleId = 1
        ),
        date = targetDate,
        start = LocalTime.of(startHour, startMinute),
        end = LocalTime.of(endHour, endMinute)
    )
}
