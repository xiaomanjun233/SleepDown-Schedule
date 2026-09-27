package com.xiaomanjun.sleepdownschedule.feature.agent

import com.xiaomanjun.sleepdownschedule.*
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.*
import org.junit.Test

class AgentFineAdjustmentTest {
    private val original = CourseEntity(41, "数学", "王老师", "A101", 1, listOf(7,8,9), listOf(1,2,3,4), WeekParity.ALL, "保留")
    private val facts = buildDayAgentFacts(listOf(original), defaultPeriods(), defaultConfig(), LocalDate.of(2026,9,28), null,
        now = LocalDateTime.of(2026,9,28,8,0)).copy(currentWeek = 3, semesterCourses = listOf(original))
    private fun parse(json: String, source: CourseEntity = original) = parseAgentActions("<agent_actions>$json</agent_actions>",
        facts.copy(semesterCourses = listOf(source), today = emptyList(), tomorrow = emptyList(), week = emptyList())).actions
    private fun cells(courses: List<CourseEntity>) = courses.flatMap { course -> course.weeks.filter { parityMatches(course.weekParity,it) }.flatMap { week ->
        course.periods.map { period -> Triple(week,course.weekday,period) }
    } }.toSet()

    @Test fun deletingOnePeriodKeepsSameWeekNeighborsAndAllOtherWeeks() {
        val actions = parse("""[{"type":"DELETE_COURSE","courseId":41,"scope":"SELECTED_WEEKS","sourceWeeks":[3],"sourcePeriods":[8]}]""")
        assertEquals(1, actions.size)
        val preview = previewAgentPlan(listOf(original), AgentPlan(actions))
        assertEquals(cells(listOf(original)) - Triple(3,1,8), cells(preview.after))
        assertTrue(verifyAgentPlan(preview.after.mapIndexed { i,c -> c.copy(id = 80L+i) }, AgentPlan(actions), listOf(original)))
        assertEquals("仅第3周 · 仅第8节", agentScopeDescription(actions.single()))
        assertEquals(listOf(AgentChangeKind.DELETE), agentChangeKinds(actions.single()))
    }

    @Test fun disjointMovesWithinOneRecordDoNotOverwriteEachOther() {
        val actions = parse("""[
            {"type":"UPDATE_COURSE","courseId":41,"scope":"SELECTED_WEEKS","sourceWeeks":[3],"sourcePeriods":[7],"course":{"weeks":[4],"weekday":2,"periods":[2]}},
            {"type":"DELETE_COURSE","courseId":41,"scope":"SELECTED_WEEKS","sourceWeeks":[3],"sourcePeriods":[9]}
        ]""")
        assertEquals(2, actions.size)
        val preview = previewAgentPlan(listOf(original), AgentPlan(actions))
        assertEquals(cells(listOf(original)) - Triple(3,1,7) - Triple(3,1,9) + Triple(4,2,2), cells(preview.after))
        assertTrue(preview.after.all { it.teacher == "王老师" && it.note == "保留" })
        assertTrue(agentChangeKinds(actions.first()).contains(AgentChangeKind.MOVE))
    }

    @Test fun movingAFragmentOntoItsRemainingNeighborShowsAConflict() {
        val actions = parse("""[{"type":"UPDATE_COURSE","courseId":41,"scope":"SELECTED_WEEKS","sourceWeeks":[3],"sourcePeriods":[7],"course":{"periods":[8]}}]""")
        assertTrue(previewAgentPlan(listOf(original), AgentPlan(actions)).hasWarnings)
    }

    @Test fun overlappingSelectionsAndForeignPeriodsRejectTheWholePlan() {
        assertTrue(parse("""[{"type":"DELETE_COURSE","courseId":41,"sourcePeriods":[2]}]""").isEmpty())
        assertTrue(parse("""[{"type":"DELETE_COURSE","courseId":41,"sourcePeriods":[]}]""").isEmpty())
        assertTrue(parse("""[{"type":"DELETE_COURSE","courseId":41,"sourcePeriods":[8]},{"type":"UPDATE_COURSE","courseId":41,"sourcePeriods":[8,9],"course":{"name":"新名"}}]""").isEmpty())
        assertTrue(parse("""[{"type":"DELETE_COURSE","courseId":41,"sourcePeriod":[8]}]""").isEmpty())
        val invalid = """[{"type":"DELETE_COURSE","courseId":41,"sourcePeriod":[8]},{"type":"UPDATE_COURSE","courseId":41,"course":{"name":"新名"}}]"""
        assertFalse(prepareAgentActions(AgentToolCall("plan", AgentToolName.PROPOSE_ACTIONS, mapOf("actionsJson" to invalid)), facts).success)
    }

    @Test fun importedBellsSurviveSplittingButUnknownCustomTimesAreNeverInferred() {
        val bells = original.copy(customStartTime="14:00", customEndTime="16:40", customPeriodTimes="7,14:00-14:45;8,14:55-15:40;9,15:55-16:40")
        val fragment = bells.agentPeriodFragment(listOf(9))
        assertEquals("15:55", fragment.customStartTime)
        assertEquals("9,15:55-16:40", fragment.customPeriodTimes)
        val rename = parse("""[{"type":"UPDATE_COURSE","courseId":41,"scope":"SELECTED_WEEKS","sourceWeeks":[3],"sourcePeriods":[9],"course":{"name":"新名"}}]""", bells).single()
        assertEquals(listOf(AgentChangeKind.RENAME), agentChangeKinds(rename))
        val uncertain = bells.copy(customPeriodTimes = null)
        assertTrue(parse("""[{"type":"DELETE_COURSE","courseId":41,"sourcePeriods":[9]}]""", uncertain).isEmpty())
    }

    @Test fun proposalProducesOnlyAnUnexecutedConfirmationAndRenameHasItsOwnLabel() {
        val json = """[{"type":"UPDATE_COURSE","courseId":41,"scope":"ALL_WEEKS","course":{"name":"高等数学"}}]"""
        val result = prepareAgentActions(AgentToolCall("plan",AgentToolName.PROPOSE_ACTIONS,mapOf("actionsJson" to json)), facts)
        assertTrue(result.success)
        assertTrue(result.content.contains("尚未执行"))
        assertNotNull(result.proposedAnswer)
        assertEquals(listOf(AgentChangeKind.RENAME), agentChangeKinds(parse(json).single()))
        assertEquals("数学", original.name)
    }
}
