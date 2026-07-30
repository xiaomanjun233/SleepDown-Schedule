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

    @Test
    fun usesGeneratedQuickQuestionsWhenPackProvidesThem() {
        val facts = factsAt(9, 0, emptyList())
        val rendered = TodayAgentTimelineEngine.render(
            DailyAgentPack(quickQuestions = listOf("上午怎么安排", "帮我调整课程")),
            facts
        )

        assertEquals(listOf("上午怎么安排", "帮我调整课程"), rendered.quickQuestions)
    }

    @Test
    fun parsesAndValidatesCourseAndSettingsActions() {
        val original = slot("数据库原理", "一教 203", 8, 0, 8, 45).course.copy(
            id = 42,
            periods = listOf(1),
            weeks = (1..18).toList(),
            scheduleId = 7
        )
        val facts = factsAt(9, 0, emptyList()).copy(
            week = listOf(AgentCourseSlot(original, date, LocalTime.of(8, 0), LocalTime.of(8, 45))),
            periodDefinitions = listOf(
                PeriodEntity(1, "08:00", "08:45", 7),
                PeriodEntity(2, "08:55", "09:40", 7)
            ),
            totalWeeks = 18,
            scheduleId = 7,
            currentWeek = 3
        )
        val response = "可以。<agent_actions>[" +
            "{\"type\":\"UPDATE_COURSE\",\"courseId\":42,\"scope\":\"CURRENT_WEEK\",\"course\":{\"weekday\":2,\"periods\":[2]},\"summary\":\"移动数据库原理\"}," +
            "{\"type\":\"OPEN_SETTINGS\",\"settingsPage\":\"SCHEDULE\",\"summary\":\"打开课表设置\"}" +
            "]</agent_actions>"

        val parsed = parseAgentActions(response, facts)

        assertEquals("可以。", parsed.displayText)
        assertEquals(2, parsed.actions.size)
        assertEquals(AgentValidatedActionType.UPDATE, parsed.actions[0].type)
        assertEquals(2, parsed.actions[0].edited?.weekday)
        assertEquals(listOf(2), parsed.actions[0].edited?.periods)
        assertEquals(7, parsed.actions[0].edited?.scheduleId)
        assertEquals(AgentValidatedActionType.OPEN_SETTINGS, parsed.actions[1].type)
        assertEquals("SCHEDULE", parsed.actions[1].settingsPage)
    }

    @Test
    fun recognizesRealtimeActivityAsAConfirmableSetting() {
        val facts = factsAt(9, 0, emptyList())
        val response = "我可以帮你开启。<agent_actions>[" +
            "{\"type\":\"SET_SETTING\",\"settingKey\":\"REALTIME_ACTIVITY\",\"settingValue\":\"true\",\"summary\":\"开启实时活动\"}" +
            "]</agent_actions>"

        val action = parseAgentActions(response, facts).actions.single()

        assertEquals(AgentValidatedActionType.SET_SETTING, action.type)
        assertEquals("REALTIME_ACTIVITY", action.settingKey)
        assertEquals("TRUE", action.settingValue)
    }

    @Test
    fun recognizesScheduleRenameAndKeepsChineseName() {
        val facts = factsAt(9, 0, emptyList())
        val response = "可以修改，确认后生效。<agent_actions>[" +
            "{\"type\":\"SET_SETTING\",\"settingKey\":\"SCHEDULE_NAME\",\"settingValue\":\"大三下\",\"summary\":\"重命名当前课表\"}" +
            "]</agent_actions>"

        val action = parseAgentActions(response, facts).actions.single()

        assertEquals(AgentValidatedActionType.SET_SETTING, action.type)
        assertEquals("SCHEDULE_NAME", action.settingKey)
        assertEquals("大三下", action.settingValue)
    }

    @Test
    fun appliesValidatedPercentageSetting() {
        val next = AgentSettingRegistry.apply(defaultConfig(), "WALLPAPER_BLUR_PERCENT", "75")

        assertEquals(75f, wallpaperBlurPercent(next!!.wallpaperBlur), 0.01f)
    }

    @Test
    fun currentWeekAddActionMayOmitRedundantWeeksArray() {
        val facts = factsAt(15, 0, emptyList()).copy(
            scheduleId = 1,
            currentWeek = 20,
            totalWeeks = 20,
            periodDefinitions = (1..10).map { index ->
                PeriodEntity(index, "%02d:00".format(7 + index), "%02d:45".format(7 + index))
            }
        )
        val response = """
            已为你准备好仅本周的课程，请确认。
            <agent_actions>{"type":"ADD_COURSE","scope":"CURRENT_WEEK","course":{"name":"心理健康教育","weekday":1,"periods":[9,10],"weeks":[21]},"summary":"添加今晚课程"}</agent_actions>
        """.trimIndent()

        val parsed = parseAgentActions(response, facts)

        assertEquals(1, parsed.actions.size)
        assertEquals(listOf(20), parsed.actions.single().edited?.weeks)
        assertEquals(AgentActionScope.CURRENT_WEEK, parsed.actions.single().scope)
    }

    @Test
    fun extractsActionJsonEvenWhenModelAddsCodeFence() {
        val facts = factsAt(15, 0, emptyList()).copy(
            scheduleId = 1,
            currentWeek = 4,
            totalWeeks = 20,
            periodDefinitions = (1..4).map { index ->
                PeriodEntity(index, "%02d:00".format(7 + index), "%02d:45".format(7 + index))
            }
        )
        val response = """
            已准备好，请确认。
            <agent_actions>```json
            [{"type":"ADD_COURSE","scope":"CURRENT_WEEK","course":{"name":"测试课","weekday":3,"periods":[1,2,3,4]},"summary":"添加测试课"}]
            ```</agent_actions>
        """.trimIndent()

        val parsed = parseAgentActions(response, facts)

        assertEquals(1, parsed.actions.size)
        assertEquals("测试课", parsed.actions.single().edited?.name)
        assertEquals(listOf(1, 2, 3, 4), parsed.actions.single().edited?.periods)
        assertEquals(listOf(4), parsed.actions.single().edited?.weeks)
    }

    @Test
    fun recognizesLooseWrappedActionAndInfersAllWeeks() {
        val facts = factsAt(15, 0, emptyList()).copy(
            scheduleId = 7,
            currentWeek = 3,
            totalWeeks = 18,
            periodDefinitions = (1..4).map { index ->
                PeriodEntity(index, "%02d:00".format(7 + index), "%02d:45".format(7 + index))
            }
        )
        val response = """
            已准备好，确认后添加。
            ```json
            {"actions":[{"type":"add_course","scope":"all_weeks","course":{"name":"测试课","weekday":3,"periods":[1,2]},"summary":"添加测试课"}]}
            ```
        """.trimIndent()

        val parsed = parseAgentActions(response, facts)

        assertEquals(1, parsed.actions.size)
        assertEquals((1..18).toList(), parsed.actions.single().edited?.weeks)
        assertFalse(parsed.displayText.contains("actions"))
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
