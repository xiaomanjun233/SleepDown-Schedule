package com.xiaomanjun.sleepdownschedule.feature.home.week

import org.junit.Assert.assertEquals
import org.junit.Test

class WeekCustomTimeLabelsTest {
    @Test fun closeCustomCoursesReserveTheGapInsteadOfDrawingTwoTimesOnTopOfEachOther() {
        val labels = weekColumnCustomTimeLabels(listOf(20f to 40f, 55f to 75f), setOf(0, 1), 120f, 10f)
        assertEquals(WeekCustomTimeLabels(true, true, false), labels[0])
        assertEquals(WeekCustomTimeLabels(false, false, true), labels[1])
    }

    @Test fun largeFontMetricsAreUsedForTheWholeColumnIncludingReferenceCards() {
        val labels = weekColumnCustomTimeLabels(listOf(30f to 50f, 80f to 100f), setOf(0, 1), 160f, 24f)
        assertEquals(WeekCustomTimeLabels(true, true, false), labels[0])
        assertEquals(WeekCustomTimeLabels(false, false, true), labels[1])
        assertEquals(listOf(WeekCustomTimeLabels(false, false, true)),
            weekColumnCustomTimeLabels(listOf(30f to 80f), setOf(0), 120f, 10f, listOf(0f to 28f)))
    }

    @Test fun regularCardsAndGridEdgesCannotBeUsedAsLabelSpace() {
        val labels = weekColumnCustomTimeLabels(listOf(0f to 25f, 30f to 120f), setOf(1), 120f, 10f)
        assertEquals(null, labels[0])
        assertEquals(WeekCustomTimeLabels(false, false, false), labels[1])
    }

    @Test fun labelPriorityFollowsTheVisualOrderRatherThanConflictGroupInsertionOrder() {
        val labels = weekColumnCustomTimeLabels(listOf(55f to 75f, 20f to 40f), setOf(0, 1), 120f, 10f)
        assertEquals(WeekCustomTimeLabels(false, false, true), labels[0])
        assertEquals(WeekCustomTimeLabels(true, true, false), labels[1])
    }

    @Test
    fun freeEdgesKeepSeparateStartAndEnd() {
        assertEquals(
            WeekCustomTimeLabels(showStart = true, showEnd = true, showRangeBelow = false),
            weekCustomTimeLabels(30f, 80f, emptyList(), 120f, 10f)
        )
    }

    @Test
    fun coveredStartMovesBothTimesBelowTheCard() {
        assertEquals(
            WeekCustomTimeLabels(showStart = false, showEnd = false, showRangeBelow = true),
            weekCustomTimeLabels(30f, 80f, listOf(0f to 25f), 120f, 10f)
        )
    }

    @Test
    fun noSpaceBelowKeepsOnlyTheFreeStart() {
        assertEquals(
            WeekCustomTimeLabels(showStart = true, showEnd = false, showRangeBelow = false),
            weekCustomTimeLabels(30f, 80f, listOf(82f to 110f), 120f, 10f)
        )
    }

    @Test
    fun bothOccupiedHideTheTimes() {
        assertEquals(
            WeekCustomTimeLabels(showStart = false, showEnd = false, showRangeBelow = false),
            weekCustomTimeLabels(30f, 80f, listOf(0f to 25f, 82f to 110f), 120f, 10f)
        )
    }
}
