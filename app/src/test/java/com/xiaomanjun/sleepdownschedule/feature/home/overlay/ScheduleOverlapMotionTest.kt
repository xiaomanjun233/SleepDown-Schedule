package com.xiaomanjun.sleepdownschedule.feature.home.overlay

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ScheduleOverlapMotionTest {

    @Test
    fun layoutGroupsOnlyDirectIntersectionsAndIgnoresTransitiveOrAdjacentNeighbors() {
        val slots = listOf(
            ScheduleOverlapSlot(id = "a", weekday = 1, start = 0.0, end = 4.0, stableOrder = 3),
            ScheduleOverlapSlot(id = "b", weekday = 1, start = 0.0, end = 2.0, stableOrder = 2),
            ScheduleOverlapSlot(id = "c", weekday = 1, start = 2.0, end = 4.0, stableOrder = 1),
            ScheduleOverlapSlot(id = "next", weekday = 1, start = 4.0, end = 6.0, stableOrder = 0),
            ScheduleOverlapSlot(id = "tue", weekday = 2, start = 0.0, end = 2.0, stableOrder = 0)
        )

        val placements = layoutScheduleOverlapSlots(slots).associateBy { it.slot.id }

        assertEquals(setOf("a", "b", "c"), placements.getValue("a").overlapGroup.map { it.id }.toSet())
        assertEquals(setOf("a", "b"), placements.getValue("b").overlapGroup.map { it.id }.toSet())
        assertEquals(setOf("a", "c"), placements.getValue("c").overlapGroup.map { it.id }.toSet())
        assertEquals(listOf("next"), placements.getValue("next").overlapGroup.map { it.id })
        assertEquals(listOf("tue"), placements.getValue("tue").overlapGroup.map { it.id })

        // "b" covers "a"'s top edge; "next" does not overlap.
        assertFalse(placements.getValue("a").showOverlapBadge)
        assertTrue(placements.getValue("b").showOverlapBadge)
        assertTrue(placements.getValue("c").showOverlapBadge)
        assertFalse(placements.getValue("next").showOverlapBadge)
        assertFalse(placements.getValue("tue").showOverlapBadge)
    }

    @Test
    fun preferredTopIdOverridesStackOrderAndReceivesBadge() {
        val slots = listOf(
            ScheduleOverlapSlot(id = "long", weekday = 3, start = 0.0, end = 4.0, stableOrder = 1),
            ScheduleOverlapSlot(id = "nested", weekday = 3, start = 1.0, end = 3.0, stableOrder = 2)
        )

        val defaultPlacements = layoutScheduleOverlapSlots(slots).associateBy { it.slot.id }
        assertTrue(defaultPlacements.getValue("nested").stackOrder > defaultPlacements.getValue("long").stackOrder)
        assertFalse(defaultPlacements.getValue("long").showOverlapBadge)
        assertTrue(defaultPlacements.getValue("nested").showOverlapBadge)

        val focusedPlacements = layoutScheduleOverlapSlots(slots, preferredTopId = "long").associateBy { it.slot.id }
        assertTrue(focusedPlacements.getValue("long").stackOrder > focusedPlacements.getValue("nested").stackOrder)
        assertTrue(focusedPlacements.getValue("long").showOverlapBadge)
        // "long" spans 0..4 and covers "nested" (1..3) completely.
        assertFalse(focusedPlacements.getValue("nested").showOverlapBadge)
    }

    @Test
    fun staggerLaunchesTopCardFirstOnOpenAndTucksReverseOnClose() {
        val topOpenDelay = ScheduleOverlapMotion.staggerDelay(index = 2, totalCards = 3, closing = false)
        val bottomOpenDelay = ScheduleOverlapMotion.staggerDelay(index = 0, totalCards = 3, closing = false)
        assertEquals(0f, topOpenDelay, 0.0001f)
        assertTrue(bottomOpenDelay > topOpenDelay)

        val topCloseDelay = ScheduleOverlapMotion.staggerDelay(index = 2, totalCards = 3, closing = true)
        val bottomCloseDelay = ScheduleOverlapMotion.staggerDelay(index = 0, totalCards = 3, closing = true)
        assertTrue(topCloseDelay > bottomCloseDelay)
    }

    @Test
    fun cardMotionDocksExactlyAtEndpointsFollows2dArcAndCrossfadesCleanly() {
        val sourceRect = Rect(40f, 180f, 120f, 340f)
        val targetRect = Rect(240f, 300f, 440f, 660f)

        val openStart = ScheduleOverlapMotion.computeCardMotion(
            rawProgress = 0f,
            closing = false,
            index = 0,
            totalCards = 2,
            sourceRect = sourceRect,
            targetRect = targetRect,
            sourceCornerPx = 12f,
            targetCornerPx = 18f
        )
        assertRectNear(sourceRect, openStart.rect, 0.5f)
        assertEquals(1f, openStart.compactOpacity, 0.001f)
        assertEquals(0f, openStart.expandedOpacity, 0.001f)
        assertEquals(12f, openStart.cornerRadiusPx, 0.01f)

        val openMid = ScheduleOverlapMotion.computeCardMotion(
            rawProgress = 0.52f,
            closing = false,
            index = 1,
            totalCards = 2,
            sourceRect = sourceRect,
            targetRect = targetRect,
            sourceCornerPx = 12f,
            targetCornerPx = 18f
        )
        val linearMidX = sourceRect.center.x + (targetRect.center.x - sourceRect.center.x) *
            ScheduleOverlapMotion.OpenPositionCurve.transform(0.52f)
        // Parabolic X offset makes flight visibly curved instead of strictly straight-line.
        assertTrue(abs(openMid.rect.center.x - linearMidX) > 2f)
        assertTrue(openMid.pulseScale > 1.0f)

        val openEnd = ScheduleOverlapMotion.computeCardMotion(
            rawProgress = 1f,
            closing = false,
            index = 0,
            totalCards = 2,
            sourceRect = sourceRect,
            targetRect = targetRect,
            sourceCornerPx = 12f,
            targetCornerPx = 18f
        )
        assertRectNear(targetRect, openEnd.rect, 0.5f)
        assertEquals(0f, openEnd.compactOpacity, 0.001f)
        assertEquals(1f, openEnd.expandedOpacity, 0.001f)
        assertEquals(18f, openEnd.cornerRadiusPx, 0.01f)

        val closeEnd = ScheduleOverlapMotion.computeCardMotion(
            rawProgress = 0f,
            closing = true,
            index = 0,
            totalCards = 2,
            sourceRect = sourceRect,
            targetRect = targetRect,
            sourceCornerPx = 12f,
            targetCornerPx = 18f
        )
        assertRectNear(sourceRect, closeEnd.rect, 0.5f)
        assertEquals(1f, closeEnd.compactOpacity, 0.001f)
        assertEquals(0f, closeEnd.expandedOpacity, 0.001f)
    }

    @Test
    fun interruptedReversalContinuesFromCapturedLiveRectWithoutJump() {
        val sourceRect = Rect(40f, 180f, 120f, 340f)
        val targetRect = Rect(240f, 300f, 440f, 660f)
        val interruptedAt = ScheduleOverlapMotion.computeCardMotion(
            rawProgress = 0.45f,
            closing = false,
            index = 1,
            totalCards = 2,
            sourceRect = sourceRect,
            targetRect = targetRect,
            sourceCornerPx = 12f,
            targetCornerPx = 18f
        )

        val reversedInitial = ScheduleOverlapMotion.computeCardMotion(
            rawProgress = 0.45f,
            closing = true,
            index = 1,
            totalCards = 2,
            sourceRect = sourceRect,
            targetRect = targetRect,
            sourceCornerPx = 12f,
            targetCornerPx = 18f,
            interruptedFromRect = interruptedAt.rect,
            interruptedStartRaw = 0.45f
        )
        assertRectNear(interruptedAt.rect, reversedInitial.rect, 0.5f)

        val reversedReturned = ScheduleOverlapMotion.computeCardMotion(
            rawProgress = 0f,
            closing = true,
            index = 1,
            totalCards = 2,
            sourceRect = sourceRect,
            targetRect = targetRect,
            sourceCornerPx = 12f,
            targetCornerPx = 18f,
            interruptedFromRect = interruptedAt.rect,
            interruptedStartRaw = 0.45f
        )
        assertRectNear(sourceRect, reversedReturned.rect, 0.5f)
    }

    @Test
    fun twoStaggeredCoursesShowOverlapBadgeOnlyOnceOnTopCard() {
        val slots = listOf(
            ScheduleOverlapSlot(id = "early", weekday = 2, start = 0.0, end = 3.0, stableOrder = 2),
            ScheduleOverlapSlot(id = "late", weekday = 2, start = 1.0, end = 4.0, stableOrder = 1)
        )

        val placements = layoutScheduleOverlapSlots(slots).associateBy { it.slot.id }
        assertEquals(2, placements.getValue("early").overlapCount)
        assertEquals(2, placements.getValue("late").overlapCount)
        val badgeCount = placements.values.count { it.showOverlapBadge }
        assertEquals(1, badgeCount)
        assertFalse(placements.getValue("early").showOverlapBadge)
        assertTrue(placements.getValue("late").showOverlapBadge)
    }

    @Test
    fun overlapExpandedCardCornerRadiusStaysCompactAndRespectsCornerSetting() {
        assertEquals(4f, overlapExpandedCardCornerRadius(160.dp, 220.dp, cornerProgress = 0f).value, 0.01f)
        assertEquals(14f, overlapExpandedCardCornerRadius(160.dp, 220.dp, cornerProgress = 0.5f).value, 0.01f)
        assertEquals(18f, overlapExpandedCardCornerRadius(160.dp, 220.dp, cornerProgress = 1f).value, 0.01f)
        // Narrow/short card respects the 0.18x short-edge cap so it never looks overly pill-shaped.
        assertEquals(10.8f, overlapExpandedCardCornerRadius(60.dp, 140.dp, cornerProgress = 1f).value, 0.01f)
    }

    private fun assertRectNear(expected: Rect, actual: Rect, tolerance: Float) {
        assertEquals(expected.left, actual.left, tolerance)
        assertEquals(expected.top, actual.top, tolerance)
        assertEquals(expected.right, actual.right, tolerance)
        assertEquals(expected.bottom, actual.bottom, tolerance)
    }
}
