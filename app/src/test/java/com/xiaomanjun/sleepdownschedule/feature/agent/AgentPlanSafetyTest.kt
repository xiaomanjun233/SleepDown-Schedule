package com.xiaomanjun.sleepdownschedule.feature.agent

import com.xiaomanjun.sleepdownschedule.model.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class AgentPlanSafetyTest {
    private val course = CourseEntity(id = 42, name = "高数", teacher = "老师", location = "A101",
        weekday = 1, periods = listOf(1), weeks = listOf(1, 2), weekParity = WeekParity.ALL, note = "备注")
    private val date = LocalDate.of(2026, 9, 7)
    private val config = defaultConfig().copy(autoCurrentWeek = true, termStartDate = "2026-09-07")
    private val facts = buildDayAgentFacts(listOf(course), defaultPeriods(), config, date, null)
    private fun parse(json: String) = parseAgentActions("<agent_actions>$json</agent_actions>", facts)

    @Test fun missingAdjustmentArrayCannotClearTheSchedule() {
        assertTrue(parse("""[{"type":"SET_ADJUSTMENTS"}]""").actions.isEmpty())
        assertEquals(emptyList<Any>(), parse("""[{"type":"SET_ADJUSTMENTS","adjustments":[]}]""").actions.single().adjustments)
    }

    @Test fun invalidSecondOperationRejectsWholePlan() {
        val parsed = parse("""[{"type":"DELETE_COURSE","courseId":42},{"type":"UPDATE_COURSE","courseId":999,"course":{"name":"新名"}}]""")
        assertTrue(parsed.actions.isEmpty())
        assertTrue(parsed.displayText.contains("未执行任何修改"))
    }

    @Test fun rootClearFieldsMatchesPromptAndKeepsOtherFields() {
        val changed = parse("""[{"type":"UPDATE_COURSE","courseId":42,"clearFields":["teacher","note"]}]""").actions.single().edited!!
        assertNull(changed.teacher)
        assertNull(changed.note)
        assertEquals("A101", changed.location)
    }

    @Test fun unknownClearFieldCannotSilentlySucceed() {
        assertTrue(parse("""[{"type":"UPDATE_COURSE","courseId":42,"clearFields":["everything"]}]""").actions.isEmpty())
    }

    @Test fun followSystemAndDarkModeAreIndependentButDuplicateKeysAreRejected() {
        assertNull(AgentSettingRegistry.conflictingGroup(listOf("FOLLOW_SYSTEM_DARK_MODE", "DARK_MODE")))
        assertNotNull(AgentSettingRegistry.conflictingGroup(listOf("TOTAL_WEEKS", "TOTAL_WEEKS")))
    }

    @Test fun naturalLanguageImportCarriesOriginalTextAndCanOpenAdjustments() {
        assertEquals("周一 1-2 节高数", parse("""[{"type":"OPEN_IMPORT","importText":"周一 1-2 节高数"}]""").actions.single().importText)
        assertEquals("SCHEDULE_ADJUSTMENTS", parse("""[{"type":"OPEN_SETTINGS","settingsPage":"SCHEDULE_ADJUSTMENTS"}]""").actions.single().settingsPage)
    }

    @Test fun combinedMigrationCanReferenceNewPeriodTopology() {
        val result = parse("""[
            {"type":"SET_PERIOD_SETTINGS","periodSettings":{"morningPeriodCount":4,"noonPeriodCount":2,"afternoonPeriodCount":4,"eveningPeriodCount":4}},
            {"type":"UPDATE_COURSE","courseId":42,"course":{"periods":[14]}}
        ]""")
        assertEquals(2, result.actions.size)
        assertEquals(listOf(14), result.actions.last().edited?.periods)
    }

    @Test fun increasingTermAndMovingCourseCanUseNewWeeksInTheSamePlan() {
        val actions = parse("""[
            {"type":"SET_SETTING","settingKey":"TOTAL_WEEKS","settingValue":"30"},
            {"type":"UPDATE_COURSE","courseId":42,"scope":"SELECTED_WEEKS","sourceWeeks":[1],"course":{"weeks":[25]}}
        ]""").actions
        assertEquals(2, actions.size)
        assertEquals(listOf(25), actions.last().edited?.weeks)
    }

    @Test fun outOfRangePeriodsAreRejectedInsteadOfPartiallyApplied() {
        assertTrue(parse("""[{"type":"UPDATE_COURSE","courseId":42,"course":{"periods":[1,99]}}]""").actions.isEmpty())
    }

    @Test fun selectedWeekMoveSeparatesSourceAndDestinationAndPreservesOtherWeeks() {
        val action = parse("""[{"type":"UPDATE_COURSE","courseId":42,"scope":"SELECTED_WEEKS","sourceWeeks":[1],"course":{"weeks":[3],"weekday":4}}]""").actions.single()
        val plan = AgentPlan(listOf(action))
        val after = previewAgentPlan(listOf(course), plan).after
        assertTrue(after.any { it.weekday == 1 && it.weeks == listOf(2) })
        assertTrue(after.any { it.weekday == 4 && it.weeks == listOf(3) })
        assertTrue(verifyAgentPlan(after.mapIndexed { i, c -> c.copy(id = 100L + i) }, plan, listOf(course)))
        assertFalse(verifyAgentPlan(after.drop(1), plan, listOf(course)))
    }

    @Test fun movingOddWeekCourseToEvenWeekKeepsDestinationVisible() {
        val odd = course.copy(weekParity = WeekParity.ODD)
        val oddFacts = buildDayAgentFacts(listOf(odd), defaultPeriods(), config, date, null)
        val action = parseAgentActions("""<agent_actions>[{"type":"UPDATE_COURSE","courseId":42,"scope":"SELECTED_WEEKS","sourceWeeks":[1],"course":{"weeks":[2],"weekday":4}}]</agent_actions>""", oddFacts).actions.single()
        assertEquals(WeekParity.ALL, action.edited?.weekParity)
        assertEquals(listOf(2), action.edited?.weeks)
    }

    @Test fun sourceMustExistAndCurrentScopeCannotSilentlyIgnoreOtherWeeks() {
        assertTrue(parse("""[{"type":"UPDATE_COURSE","courseId":42,"scope":"SELECTED_WEEKS","sourceWeeks":[3],"course":{"weekday":4}}]""").actions.isEmpty())
        assertTrue(parse("""[{"type":"UPDATE_COURSE","courseId":42,"course":{"weeks":[2],"weekday":4}}]""").actions.isEmpty())
        assertTrue(parse("""[{"type":"ADD_COURSE","course":{"name":"实验","weekday":4,"periods":[1],"weeks":[99]}}]""").actions.isEmpty())
    }

    @Test fun noOpAndFragmentMergeDoNotFailBecauseIdsChanged() {
        val plan = AgentPlan(parse("""[{"type":"UPDATE_COURSE","courseId":42,"course":{"name":"高数"}}]""").actions)
        assertTrue(verifyAgentPlan(listOf(course.copy(id = 100)), plan, listOf(course)))
        assertFalse(verifyAgentPlan(listOf(course.copy(id = 100, note = "丢失了原备注")), plan, listOf(course)))
    }

    @Test fun differentOccurrencesOfSameCourseCanHaveDifferentEdits() {
        val actions = parse("""[
            {"type":"UPDATE_COURSE","courseId":42,"scope":"SELECTED_WEEKS","sourceWeeks":[1],"course":{"weekday":4}},
            {"type":"UPDATE_COURSE","courseId":42,"scope":"SELECTED_WEEKS","sourceWeeks":[2],"course":{"weekday":5}}
        ]""").actions
        assertEquals(2, actions.size)
        val plan = AgentPlan(actions)
        val after = previewAgentPlan(listOf(course), plan).after
        assertEquals(2, after.size)
        assertTrue(after.any { it.weekday == 4 && it.weeks == listOf(1) })
        assertTrue(after.any { it.weekday == 5 && it.weeks == listOf(2) })
        assertFalse(agentActionsHaveOverlappingCourseScopes(actions))
        assertTrue(agentActionsHaveOverlappingCourseScopes(listOf(actions.first(), actions.first())))
    }

    @Test fun settingsCoursesAndNavigationCanComposeInOneOrderedPlan() {
        val actions = parse("""[{"type":"SET_SETTING","settingKey":"TOTAL_WEEKS","settingValue":"18"},{"type":"UPDATE_COURSE","courseId":42,"course":{"weekday":4}},{"type":"OPEN_SETTINGS","settingsPage":"SCHEDULE"}]""").actions
        assertEquals(3, actions.size)
        assertTrue(actions.all { it.sourceScheduleId == facts.scheduleId })
    }

    @Test fun semesterOrSettingChangeInvalidatesFactVersion() {
        val future = course.copy(id = 43, weeks = listOf(12))
        assertNotEquals(facts.sourceHash, buildDayAgentFacts(listOf(course, future), defaultPeriods(), config, date, null).sourceHash)
        assertNotEquals(facts.sourceHash, buildDayAgentFacts(listOf(course), defaultPeriods(), config.copy(wallpaperBlur = 8f), date, null).sourceHash)
    }

    @Test fun actionExamplesAreValidJsonWithoutLiteralBackslashQuotes() {
        val examples = DayAgentPrompts.FinalAnswerStage.lines().filter { it.firstOrNull()?.isDigit() == true }
            .flatMap { it.substringAfter('：').split('；') }.filter { it.startsWith("{\"type\"") }
        assertTrue(examples.size >= 8)
        examples.forEach { assertNotNull(Json.parseToJsonElement(it).jsonObject["type"]?.jsonPrimitive?.content) }
    }
}
