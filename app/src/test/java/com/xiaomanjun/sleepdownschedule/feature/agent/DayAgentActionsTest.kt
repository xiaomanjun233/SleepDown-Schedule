package com.xiaomanjun.sleepdownschedule.feature.agent

import com.xiaomanjun.sleepdownschedule.core.wallpaper.*

import com.xiaomanjun.sleepdownschedule.*
import com.xiaomanjun.sleepdownschedule.domain.schedule.ScheduleAdjustment

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class DayAgentActionsTest {
    private val date = LocalDate.of(2026, 7, 15)

    @Test
    fun editsTodayTeacherAndLocationWithoutRecreatingOrChangingOtherWeeks() {
        val first = slot("第一门课", "原教室", 8, 0, 8, 45).course.copy(
            id = 41, teacher = "原教师", weeks = (1..18).toList(), note = "保留备注"
        )
        val second = first.copy(id = 42, name = "第二门课", periods = listOf(2))
        val unrelated = first.copy(id = 43, name = "其他日期课程", weekday = 2)
        val courses = listOf(first, second, unrelated)
        val facts = factsAt(9, 0, listOf(
            AgentCourseSlot(first, date, LocalTime.of(8, 0), LocalTime.of(8, 45), teachingWeek = 4),
            AgentCourseSlot(second, date, LocalTime.of(9, 0), LocalTime.of(9, 45), teachingWeek = 4)
        )).copy(
            currentWeek = 4, totalWeeks = 18, semesterCourses = courses,
            periodDefinitions = listOf(PeriodEntity(1, "08:00", "08:45"), PeriodEntity(2, "09:00", "09:45"))
        )
        val response = """请确认今天两节课的修改。<agent_actions>[
            {"type":"UPDATE_COURSE","courseId":41,"scope":"SELECTED_WEEKS","sourceWeeks":[4],"course":{"teacher":"王老师","location":"A101"}},
            {"type":"UPDATE_COURSE","courseId":42,"scope":"SELECTED_WEEKS","sourceWeeks":[4],"course":{"teacher":"王老师","location":"A101"}}
        ]</agent_actions>"""
        val parsed = parseAgentActions(response, facts)
        assertEquals(2, parsed.actions.size)
        assertTrue(parsed.actions.all { it.type == AgentValidatedActionType.UPDATE })
        val plan = AgentPlan(parsed.actions)
        val preview = previewAgentPlan(courses, plan)
        val changed = preview.after.filter { it.name in listOf(first.name, second.name) && 4 in it.weeks }
        assertEquals(2, changed.size)
        assertTrue(changed.all { it.teacher == "王老师" && it.location == "A101" && it.note == "保留备注" })
        for (original in listOf(first, second)) {
            val remaining = preview.after.single { it.name == original.name && 4 !in it.weeks }
            assertEquals(original.copy(weeks = original.weeks - 4), remaining)
            val edited = changed.single { it.name == original.name }
            assertEquals(original.weekday, edited.weekday)
            assertEquals(original.periods, edited.periods)
        }
        assertTrue(preview.after.contains(unrelated))
        assertTrue(verifyAgentPlan(preview.after, plan, courses))
        assertFalse(verifyAgentPlan(courses, plan, courses))
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
            "{\"type\":\"SET_SETTING\",\"settingKey\":\"TOTAL_WEEKS\",\"settingValue\":\"18\",\"summary\":\"设置学期周数\"}" +
            "]</agent_actions>"

        val parsed = parseAgentActions(response, facts)

        assertEquals("可以。", parsed.displayText)
        assertEquals(2, parsed.actions.size)
        assertEquals(AgentValidatedActionType.UPDATE, parsed.actions[0].type)
        assertEquals(2, parsed.actions[0].edited?.weekday)
        assertEquals(listOf(2), parsed.actions[0].edited?.periods)
        assertEquals(7, parsed.actions[0].edited?.scheduleId)
        assertEquals(AgentValidatedActionType.SET_SETTING, parsed.actions[1].type)
        assertEquals("TOTAL_WEEKS", parsed.actions[1].settingKey)
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
    fun exposesAndValidatesCustomCourseTimeInWritePlan() {
        val facts = factsAt(9, 0, emptyList()).copy(
            scheduleId = 7,
            totalWeeks = 18,
            periodDefinitions = listOf(
                PeriodEntity(1, "08:00", "08:45", 7),
                PeriodEntity(2, "08:55", "09:40", 7)
            )
        )
        val response = "请确认。<agent_actions>[" +
            "{\"type\":\"ADD_COURSE\",\"scope\":\"CURRENT_WEEK\",\"course\":{" +
            "\"name\":\"无机非金属材料学\",\"weekday\":1,\"periods\":[1]," +
            "\"customStartTime\":\"10:10\",\"customEndTime\":\"11:55\"" +
            "}}]</agent_actions>"

        val action = parseAgentActions(response, facts).actions.single()

        assertEquals("10:10", action.edited?.customStartTime)
        assertEquals("11:55", action.edited?.customEndTime)
    }

    @Test
    fun rejectsPartialOrReversedCustomCourseTime() {
        val facts = factsAt(9, 0, emptyList()).copy(
            periodDefinitions = listOf(PeriodEntity(1, "08:00", "08:45", 7))
        )
        val partial = "<agent_actions>{\"type\":\"ADD_COURSE\",\"scope\":\"CURRENT_WEEK\"," +
            "\"course\":{\"name\":\"测试\",\"weekday\":1,\"periods\":[1]," +
            "\"customStartTime\":\"10:10\"}}</agent_actions>"
        val reversed = "<agent_actions>{\"type\":\"ADD_COURSE\",\"scope\":\"CURRENT_WEEK\"," +
            "\"course\":{\"name\":\"测试\",\"weekday\":1,\"periods\":[1]," +
            "\"customStartTime\":\"12:00\",\"customEndTime\":\"11:00\"}}</agent_actions>"

        assertTrue(parseAgentActions(partial, facts).actions.isEmpty())
        assertTrue(parseAgentActions(reversed, facts).actions.isEmpty())
    }

    @Test
    fun appliesValidatedPercentageSetting() {
        val next = AgentSettingRegistry.apply(defaultConfig(), "WALLPAPER_BLUR_PERCENT", "75")

        assertEquals(75f, wallpaperBlurPercent(next!!.wallpaperBlur), 0.01f)
    }

    @Test
    fun agentUsesAdaptiveWeekHeightAndCornerFields() {
        val height = AgentSettingRegistry.apply(defaultConfig(), "WEEK_CARD_HEIGHT_PERCENT", "50")
        val corner = AgentSettingRegistry.apply(height!!, "WEEK_CARD_CORNER_PERCENT", "80")

        assertEquals(1f, corner!!.weekCardHeightScale, 0.0001f)
        assertEquals(null, corner.weekCardHeightDp)
        assertEquals(0.8f, corner.weekCardCornerProgress, 0.0001f)
    }

    @Test
    fun agentPaletteBecomesColorfulAlgorithmSeeds() {
        val normalized = AgentSettingRegistry.normalize(
            "COURSE_CARD_PALETTE",
            "#77BDF2, #F09AB6, #8DD3A8, #FFD166"
        )
        val next = AgentSettingRegistry.apply(defaultConfig(), normalized!!.first, normalized.second)

        assertEquals(CourseCardColorMode.COLORFUL, next!!.courseCardColorMode)
        assertEquals("FF77BDF2,FFF09AB6,FF8DD3A8,FFFFD166", next.courseCardPalette)
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
            <agent_actions>{"type":"ADD_COURSE","scope":"CURRENT_WEEK","course":{"name":"心理健康教育","weekday":1,"periods":[9,10]},"summary":"添加今晚课程"}</agent_actions>
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

    @Test
    fun clearsAllSemesterNotesWithoutChangingOtherFields() {
        val courses = (1L..3L).map { id ->
            slot("同名课程", "教室", 8, 0, 8, 45).course.copy(
                id = id, note = "原备注$id", weeks = listOf(id.toInt()), scheduleId = 7
            )
        }
        val facts = factsAt(9, 0, emptyList()).copy(
            semesterCourses = courses, totalWeeks = 18, currentWeek = 1, scheduleId = 7,
            periodDefinitions = listOf(PeriodEntity(1, "08:00", "08:45", 7))
        )
        val payload = courses.joinToString(",") {
            """{"type":"UPDATE_COURSE","courseId":${it.id},"scope":"ALL_WEEKS","course":{"note":""}}"""
        }
        val parsed = parseAgentActions("请确认。<agent_actions>[$payload]</agent_actions>", facts)
        assertEquals(3, parsed.actions.size)
        val plan = AgentPlan(parsed.actions)
        val preview = previewAgentPlan(courses, plan)
        assertEquals(courses.map { it.copy(note = null) }, preview.after)
        assertTrue(verifyAgentPlan(preview.after, plan))
        assertFalse(verifyAgentPlan(courses, plan))
        val tool = executeAgentReadTools(
            listOf(AgentToolCall("semester", AgentToolName.GET_SEMESTER_SCHEDULE)), facts
        ).single()
        courses.forEach { assertTrue(tool.content.contains("n=${it.note}")) }
    }

    @Test
    fun omittedOrNullNoteIsPreservedButWhitespaceClears() {
        val original = slot("课程", "教室", 8, 0, 8, 45).course.copy(id = 7, note = "保留", scheduleId = 7)
        val facts = factsAt(9, 0, emptyList()).copy(
            semesterCourses = listOf(original), totalWeeks = 18, scheduleId = 7,
            periodDefinitions = listOf(PeriodEntity(1, "08:00", "08:45", 7))
        )
        for ((patch, expected) in listOf("{}" to "保留", """{"note":null}""" to "保留", """{"note":"  "}""" to null)) {
            val content = """<agent_actions>[{"type":"UPDATE_COURSE","courseId":7,"scope":"ALL_WEEKS","course":$patch}]</agent_actions>"""
            assertEquals(expected, parseAgentActions(content, facts).actions.single().edited?.note)
        }
    }

    @Test
    fun personalizationPageSurvivesParsingInsteadOfBeingDroppedSilently() {
        val facts = factsAt(9, 0, emptyList())
        val response = "可以打开首页外观设置。<agent_actions>[" +
            "{\"type\":\"OPEN_SETTINGS\",\"settingsPage\":\"PERSONALIZATION\",\"summary\":\"打开首页外观\"}" +
            "]</agent_actions>"

        val action = parseAgentActions(response, facts).actions.single()

        assertEquals(AgentValidatedActionType.OPEN_SETTINGS, action.type)
        assertEquals("PERSONALIZATION", action.settingsPage)
    }

    @Test
    fun newlyReachableSettingsPagesAreAccepted() {
        val facts = factsAt(9, 0, emptyList())
        listOf("LIQUID_GLASS", "WIDGETS", "BACKUP_RESTORE", "PRIVACY_POLICY").forEach { page ->
            val response = "<agent_actions>[" +
                "{\"type\":\"OPEN_SETTINGS\",\"settingsPage\":\"$page\"}]</agent_actions>"
            assertEquals(page, parseAgentActions(response, facts).actions.single().settingsPage)
        }
    }

    @Test
    fun overlappingSettingKeysAreReportedAsOneConflictGroup() {
        assertNull(AgentSettingRegistry.conflictingGroup(listOf("TOTAL_WEEKS", "DARK_MODE")))
        assertNull(AgentSettingRegistry.conflictingGroup(listOf("REALTIME_ACTIVITY")))
        assertEquals(
            setOf("NOTIFICATION_MODE", "REALTIME_ACTIVITY"),
            AgentSettingRegistry.conflictingGroup(listOf("NOTIFICATION_MODE", "REALTIME_ACTIVITY"))
        )
        assertEquals(
            setOf("COURSE_CARD_COLOR", "COURSE_CARD_COLOR_MODE", "COURSE_CARD_PALETTE"),
            AgentSettingRegistry.conflictingGroup(
                listOf("COURSE_CARD_COLOR", "COURSE_CARD_PALETTE")
            )
        )
    }

    @Test
    fun replaceCourseRewritesWholeEntityIncludingColourAndClearFields() {
        val original = slot("数据库原理", "一教 203", 8, 0, 8, 45).course.copy(
            id = 42,
            periods = listOf(1),
            weeks = (1..18).toList(),
            note = "旧备注",
            scheduleId = 7
        )
        val facts = factsAt(9, 0, emptyList()).copy(
            week = listOf(AgentCourseSlot(original, date, LocalTime.of(8, 0), LocalTime.of(8, 45))),
            periodDefinitions = listOf(
                PeriodEntity(1, "08:00", "08:45", 7),
                PeriodEntity(2, "08:55", "09:40", 7)
            ),
            totalWeeks = 18,
            scheduleId = 7
        )
        val response = "<agent_actions>[{\"type\":\"REPLACE_COURSE\",\"courseId\":42,\"scope\":\"ALL_WEEKS\"," +
            "\"course\":{\"name\":\"数据库原理\",\"weekday\":2,\"periods\":[2],\"customColorArgb\":\"#FF8800\"}}]</agent_actions>"

        val action = parseAgentActions(response, facts).actions.single()

        assertEquals(AgentValidatedActionType.REPLACE, action.type)
        assertEquals(2, action.edited?.weekday)
        assertEquals(listOf(2), action.edited?.periods)
        // Replacement nulls teacher/location/note that the model did not supply.
        assertEquals(null, action.edited?.note)
        assertEquals(0xFFFF8800L, action.edited?.customColorArgb)
    }

    @Test
    fun patchClearFieldsClearsTeacherAndCustomTimeInsteadOfPreserving() {
        val original = slot("课程", "教室", 8, 0, 8, 45).course.copy(
            id = 42,
            teacher = "张三",
            customStartTime = "09:00",
            customEndTime = "09:45",
            note = "备注",
            scheduleId = 7
        )
        val facts = factsAt(9, 0, emptyList()).copy(
            week = listOf(AgentCourseSlot(original, date, LocalTime.of(9, 0), LocalTime.of(9, 45))),
            periodDefinitions = listOf(PeriodEntity(1, "08:00", "08:45", 7)),
            totalWeeks = 18,
            scheduleId = 7
        )
        val response = "<agent_actions>[{\"type\":\"UPDATE_COURSE\",\"courseId\":42,\"scope\":\"ALL_WEEKS\"," +
            "\"course\":{\"clearFields\":[\"teacher\",\"customTime\",\"note\"]}}]</agent_actions>"

        val edited = parseAgentActions(response, facts).actions.single().edited!!

        assertEquals(null, edited.teacher)
        assertEquals(null, edited.note)
        assertEquals(null, edited.customStartTime)
        assertEquals(null, edited.customEndTime)
    }

    @Test
    fun validatesWholeTableAdjustmentsReplacement() {
        val facts = factsAt(9, 0, emptyList()).copy(scheduleId = 7)
        val response = "<agent_actions>[{\"type\":\"SET_ADJUSTMENTS\"," +
            "\"adjustments\":[{\"date\":\"2026-10-02\",\"sourceDate\":\"2026-10-05\",\"label\":\"国庆补课\"}]," +
            "\"summary\":\"设置调休\"}]</agent_actions>"
        val action = parseAgentActions(response, facts).actions.single()

        assertEquals(AgentValidatedActionType.SET_ADJUSTMENTS, action.type)
        assertEquals(1, action.adjustments?.size)
        assertEquals("2026-10-02", action.adjustments?.single()?.date)
        assertEquals("2026-10-05", action.adjustments?.single()?.sourceDate)

        val sameDay = "<agent_actions>[{\"type\":\"SET_ADJUSTMENTS\",\"adjustments\":[" +
            "{\"date\":\"2026-10-02\",\"sourceDate\":\"2026-10-02\"}]}]</agent_actions>"
        assertTrue(parseAgentActions(sameDay, facts).actions.isEmpty())
    }

    @Test
    fun scheduleActionsRequireARealScheduleIdFromTheFacts() {
        val facts = factsAt(9, 0, emptyList()).copy(
            schedules = listOf(
                AgentScheduleSummary(1, "课程表", isActive = true),
                AgentScheduleSummary(2, "二课表", isActive = false)
            )
        )

        val create = "<agent_actions>[{\"type\":\"CREATE_SCHEDULE\",\"name\":\"三课表\"}]</agent_actions>"
        val activate = "<agent_actions>[{\"type\":\"ACTIVATE_SCHEDULE\",\"scheduleId\":2}]</agent_actions>"
        val missing = "<agent_actions>[{\"type\":\"ACTIVATE_SCHEDULE\",\"scheduleId\":99}]</agent_actions>"

        val createAction = parseAgentActions(create, facts).actions.single()
        assertEquals(AgentValidatedActionType.CREATE_SCHEDULE, createAction.type)
        assertEquals("三课表", createAction.scheduleName)

        val activateAction = parseAgentActions(activate, facts).actions.single()
        assertEquals(AgentValidatedActionType.ACTIVATE_SCHEDULE, activateAction.type)
        assertEquals(2, activateAction.scheduleId)

        assertTrue(parseAgentActions(missing, facts).actions.isEmpty())
    }

    @Test
    fun openImportSurvivesParsingAsNavigationAction() {
        val facts = factsAt(9, 0, emptyList())
        val response = "<agent_actions>[{\"type\":\"OPEN_IMPORT\",\"summary\":\"打开AI导入\"}]</agent_actions>"

        val action = parseAgentActions(response, facts).actions.single()

        assertEquals(AgentValidatedActionType.OPEN_IMPORT, action.type)
    }

    @Test
    fun adjustmentsAndSchedulesReadToolsRenderTheirFacts() {
        val facts = factsAt(9, 0, emptyList()).copy(
            scheduleAdjustments = listOf(
                ScheduleAdjustment("2026-10-02", "2026-10-05", "国庆补课")
            ),
            schedules = listOf(
                AgentScheduleSummary(1, "课程表", isActive = true),
                AgentScheduleSummary(2, "二课表", isActive = false)
            )
        )
        val results = executeAgentReadTools(
            listOf(
                AgentToolCall("adj", AgentToolName.GET_SCHEDULE_ADJUSTMENTS),
                AgentToolCall("sched", AgentToolName.GET_SCHEDULES)
            ),
            facts
        )

        assertTrue(results[0].content.contains("date=2026-10-02"))
        assertTrue(results[0].content.contains("sourceDate=2026-10-05"))
        assertTrue(results[1].content.contains("课程表"))
        assertTrue(results[1].content.contains("true"))
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
