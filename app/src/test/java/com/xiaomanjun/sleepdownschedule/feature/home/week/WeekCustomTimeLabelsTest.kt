package com.xiaomanjun.sleepdownschedule.feature.home.week

import org.junit.Assert.assertEquals
import org.junit.Test

class WeekCustomTimeLabelsTest {
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
