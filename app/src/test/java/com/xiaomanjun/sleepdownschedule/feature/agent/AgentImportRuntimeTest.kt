package com.xiaomanjun.sleepdownschedule.feature.agent

import com.xiaomanjun.sleepdownschedule.model.*
import org.junit.Assert.*
import org.junit.Test

class AgentImportRuntimeTest {
    private val source = AgentImportRuntime.workspace(ImportDraft(defaultConfig().copy(currentWeek = 3), defaultPeriods(), listOf(
        CourseEntity(name = "数学", teacher = "王老师", location = "A101", weekday = 1, periods = listOf(1, 2),
            weeks = (1..20).toList(), weekParity = WeekParity.ALL, note = "保留"),
        CourseEntity(name = "英语", teacher = null, location = "B201", weekday = 2, periods = listOf(3),
            weeks = (1..20).toList(), weekParity = WeekParity.ALL, note = null))))

    @Test fun weekOnlyRevisionPreservesTheOtherNineteenWeeksAndOtherCourses() {
        val result = AgentImportRuntime.applyPlan(source, """<agent_actions>[{"type":"UPDATE_COURSE","courseId":1,"scope":"CURRENT_WEEK","course":{"location":"A202"}}]</agent_actions>""", true)
        val math = result.courses.filter { it.name == "数学" }
        assertEquals(listOf(3), math.single { it.location == "A202" }.weeks)
        assertEquals((1..20).filter { it != 3 }, math.single { it.location == "A101" }.weeks)
        assertEquals(source.courses[1], result.courses.single { it.name == "英语" })
        assertTrue(math.all { it.teacher == "王老师" && it.note == "保留" && it.periods == listOf(1, 2) })
    }

    @Test fun modelCannotWidenAnExplicitWeekOnlyInstruction() {
        assertThrows(IllegalArgumentException::class.java) {
            AgentImportRuntime.applyPlan(source, """<agent_actions>[{"type":"DELETE_COURSE","courseId":1,"scope":"ALL_WEEKS"}]</agent_actions>""", true)
        }
        assertEquals(20, source.courses.first().weeks.size)
    }

    @Test fun malformedOrForeignCourseProposalNeverBecomesAPartialTimetable() {
        assertThrows(IllegalArgumentException::class.java) {
            AgentImportRuntime.applyPlan(source, """<agent_actions>[{"type":"DELETE_COURSE","courseId":999,"scope":"ALL_WEEKS"}]</agent_actions>""")
        }
    }
}
