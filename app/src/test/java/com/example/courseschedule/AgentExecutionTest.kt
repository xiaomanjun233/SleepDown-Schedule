package com.example.courseschedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentExecutionTest {
    @Test
    fun swapIsPreviewedAsOneConflictFreePlan() {
        val first = course(1, "高数", weekday = 1, periods = listOf(1, 2))
        val second = course(2, "英语", weekday = 2, periods = listOf(3, 4))
        val plan = AgentPlan(
            listOf(
                update(first, first.copy(weekday = 2, periods = listOf(3, 4))),
                update(second, second.copy(weekday = 1, periods = listOf(1, 2)))
            )
        )

        val preview = previewAgentPlan(listOf(first, second), plan)

        assertTrue(preview.canExecute)
        assertEquals(2, preview.changedCourseCount)
        assertTrue(verifyAgentPlan(preview.after, plan))
    }

    @Test
    fun newlyCreatedCollisionIsReportedWithoutReplacingModelDecision() {
        val first = course(1, "高数", weekday = 1, periods = listOf(1, 2))
        val second = course(2, "英语", weekday = 2, periods = listOf(3, 4))
        val plan = AgentPlan(
            listOf(update(second, second.copy(weekday = 1, periods = listOf(1, 2))))
        )

        val preview = previewAgentPlan(listOf(first, second), plan)

        assertTrue(preview.canExecute)
        assertTrue(preview.hasWarnings)
        assertEquals(1, preview.newConflicts.size)
        assertEquals(listOf(1, 2), preview.newConflicts.single().periods)
    }

    @Test
    fun currentWeekUpdatePreservesOtherWeeks() {
        val original = course(
            id = 1,
            name = "高数",
            weekday = 1,
            periods = listOf(1, 2),
            weeks = listOf(1, 2, 3)
        )
        val edited = original.copy(weekday = 5, periods = listOf(7, 8))
        val plan = AgentPlan(
            listOf(
                AgentValidatedAction(
                    type = AgentValidatedActionType.UPDATE,
                    original = original,
                    edited = edited,
                    scope = AgentActionScope.CURRENT_WEEK,
                    targetWeek = 2,
                    summary = "仅修改第2周"
                )
            )
        )

        val preview = previewAgentPlan(listOf(original), plan)

        assertTrue(preview.canExecute)
        assertTrue(preview.after.any { it.weekday == 1 && it.weeks == listOf(1, 3) })
        assertTrue(preview.after.any { it.weekday == 5 && it.weeks == listOf(2) })
        val persistedShape = preview.after.map {
            if (it.weekday == 5 && it.weeks == listOf(2)) it.copy(id = 99) else it
        }
        assertTrue(verifyAgentPlan(persistedShape, plan))
    }

    @Test
    fun highLevelMergeCanBeComposedFromDeleteAndAddPrimitives() {
        val oddWeeks = course(
            id = 1,
            name = "材料化学",
            weekday = 3,
            periods = listOf(3, 4),
            weeks = listOf(1, 3)
        )
        val evenWeeks = course(
            id = 2,
            name = "材料化学",
            weekday = 3,
            periods = listOf(3, 4),
            weeks = listOf(2, 4)
        )
        val merged = oddWeeks.copy(
            id = 0,
            weeks = listOf(1, 2, 3, 4)
        )
        val plan = AgentPlan(
            listOf(
                delete(oddWeeks),
                delete(evenWeeks),
                add(merged)
            )
        )

        val preview = previewAgentPlan(listOf(oddWeeks, evenWeeks), plan)

        assertTrue(preview.canExecute)
        assertEquals(1, preview.after.size)
        assertEquals(listOf(1, 2, 3, 4), preview.after.single().weeks)
        assertTrue(verifyAgentPlan(preview.after, plan))
    }

    private fun update(
        original: CourseEntity,
        edited: CourseEntity
    ) = AgentValidatedAction(
        type = AgentValidatedActionType.UPDATE,
        original = original,
        edited = edited,
        scope = AgentActionScope.ALL_WEEKS,
        targetWeek = 1,
        summary = "移动 ${original.name}"
    )

    private fun delete(original: CourseEntity) = AgentValidatedAction(
        type = AgentValidatedActionType.DELETE,
        original = original,
        scope = AgentActionScope.ALL_WEEKS,
        targetWeek = 1,
        summary = "删除旧记录"
    )

    private fun add(course: CourseEntity) = AgentValidatedAction(
        type = AgentValidatedActionType.ADD,
        edited = course,
        scope = AgentActionScope.ALL_WEEKS,
        targetWeek = 1,
        summary = "新增合并记录"
    )

    private fun course(
        id: Long,
        name: String,
        weekday: Int,
        periods: List<Int>,
        weeks: List<Int> = listOf(1, 2)
    ) = CourseEntity(
        id = id,
        name = name,
        teacher = null,
        location = null,
        weekday = weekday,
        periods = periods,
        weeks = weeks,
        weekParity = WeekParity.ALL,
        note = null,
        scheduleId = 1
    )
}
