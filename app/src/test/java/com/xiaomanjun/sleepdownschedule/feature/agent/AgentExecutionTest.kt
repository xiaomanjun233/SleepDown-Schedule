package com.xiaomanjun.sleepdownschedule.feature.agent

import com.xiaomanjun.sleepdownschedule.*

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun customTimeCollisionUsesWallClockRangeInsteadOfPeriodAnchor() {
        val first = course(1, "高数", weekday = 1, periods = listOf(1)).copy(
            customStartTime = "10:10",
            customEndTime = "11:00"
        )
        val second = course(2, "英语", weekday = 1, periods = listOf(2)).copy(
            customStartTime = "11:10",
            customEndTime = "12:00"
        )
        val plan = AgentPlan(
            listOf(
                update(
                    second,
                    second.copy(customStartTime = "10:45", customEndTime = "11:30")
                )
            )
        )

        val preview = previewAgentPlan(
            before = listOf(first, second),
            plan = plan,
            periodDefinitions = listOf(
                PeriodEntity(1, "08:00", "08:45"),
                PeriodEntity(2, "08:55", "09:40")
            )
        )

        assertTrue(preview.hasWarnings)
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

    @Test
    fun verificationRejectsChangedOriginalTimeOnRemainingFragment() {
        val original = course(1, "早课", 1, listOf(1, 2)).copy(
            originalPeriodTimes = "1,08:00-08:40;2,08:50-09:30")
        val plan = AgentPlan(listOf(delete(original).copy(sourcePeriods = listOf(1))))
        val remaining = original.copy(periods = listOf(2), originalPeriodTimes = "2,08:50-09:30")
        assertTrue(verifyAgentPlan(listOf(remaining.copy(id = 99)), plan, listOf(original)))
        assertFalse(verifyAgentPlan(listOf(remaining.copy(originalPeriodTimes = "2,14:50-15:30")), plan, listOf(original)))
        assertFalse(verifyAgentPlan(listOf(remaining.copy(originalPeriodTimes = null)), plan, listOf(original)))
    }

    @Test
    fun verificationChecksOriginalClockOfUnrelatedCoursesToo() {
        val original = course(1, "早课", 1, listOf(1)).copy(originalPeriodTimes = "1,08:00-08:40")
        val neighbor = course(2, "未修改", 2, listOf(2)).copy(originalPeriodTimes = "2,08:50-09:30")
        val edited = original.copy(name = "改名")
        val plan = AgentPlan(listOf(update(original, edited)))
        assertTrue(verifyAgentPlan(listOf(edited, neighbor), plan, listOf(original, neighbor)))
        assertFalse(verifyAgentPlan(listOf(edited, neighbor.copy(originalPeriodTimes = "2,14:50-15:30")),
            plan, listOf(original, neighbor)))
        assertFalse(verifyAgentPlan(listOf(edited.copy(originalPeriodTimes = null)), plan))
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
