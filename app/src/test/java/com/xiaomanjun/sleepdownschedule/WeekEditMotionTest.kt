package com.xiaomanjun.sleepdownschedule

import com.xiaomanjun.sleepdownschedule.feature.home.week.*
import com.xiaomanjun.sleepdownschedule.feature.home.day.occurrenceOverrideKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeekEditMotionTest {

    @Test
    fun resizeHeightFollowsContinuousPixelsBeforeReleaseQuantization() {
        val first = weekEditResizeHeightAfterDrag(
            currentHeightPx = 96f,
            dragDeltaYPx = 10f,
            periodRowHeightPx = 50f,
            maxSpan = 5,
            resizePaddingPx = 4f
        )
        val second = weekEditResizeHeightAfterDrag(
            currentHeightPx = first,
            dragDeltaYPx = -4f,
            periodRowHeightPx = 50f,
            maxSpan = 5,
            resizePaddingPx = 4f
        )

        assertEquals(101.2f, first, 0.001f)
        assertEquals(99.12f, second, 0.001f)
    }

    @Test
    fun resizeSpanChangesOnlyAfterCrossingTheHalfRowThreshold() {
        assertEquals(2, weekEditResizeTargetSpan(124.9f, 50f, 5))
        assertEquals(3, weekEditResizeTargetSpan(125f, 50f, 5))
        assertEquals(5, weekEditResizeTargetSpan(500f, 50f, 5))
        assertEquals(1, weekEditResizeTargetSpan(-20f, 50f, 5))
    }

    @Test
    fun liftedCardIsNoticeablyLargerWithoutBecomingDisruptive() {
        assertTrue(WeekEditLiftedScale >= 1.06f)
        assertTrue(WeekEditLiftedScale <= 1.14f)
    }

    @Test
    fun releaseProjectionUsesVelocityButCannotSkipUnboundedDistance() {
        assertEquals(130f, weekEditProjectedOffset(100f, 2_000f, 72f), 0.001f)
        assertEquals(70f, weekEditProjectedOffset(100f, -2_000f, 72f), 0.001f)
        assertEquals(101.5f, weekEditProjectedOffset(100f, 100f, 72f), 0.001f)
    }

    @Test
    fun landingRippleDoesNotAffectCardsOutsideItsRadius() {
        val transform = weekEditNeighborRippleTransform(
            distancePx = 401f,
            radiusPx = 400f,
            progress = 0.5f
        )

        assertEquals(0f, transform.translationFactor, 0.001f)
        assertEquals(1f, transform.scale, 0.001f)
        assertEquals(0f, transform.rotationFactor, 0.001f)
    }

    @Test
    fun landingRippleReachesNearbyCardsThenSettlesExactly() {
        val active = weekEditNeighborRippleTransform(
            distancePx = 80f,
            radiusPx = 400f,
            progress = 0.30f,
            horizontalDirection = 0.7f
        )
        val settled = weekEditNeighborRippleTransform(
            distancePx = 80f,
            radiusPx = 400f,
            progress = 1f,
            horizontalDirection = 0.7f
        )

        assertEquals(0f, active.translationFactor, 0.001f)
        assertTrue(kotlin.math.abs(active.scale - 1f) > 0.0001f)
        assertEquals(0f, active.rotationFactor, 0.001f)
        assertEquals(0f, settled.translationFactor, 0.001f)
        assertEquals(1f, settled.scale, 0.001f)
        assertEquals(0f, settled.rotationFactor, 0.001f)
    }

    @Test
    fun landingImpactOscillatesUniformlyOnTheZAxisBeforeSettling() {
        val impact = weekEditLandingImpactTransform(0.18f)
        val rebound = weekEditLandingImpactTransform(0.46f)
        val settled = weekEditLandingImpactTransform(1f)

        assertEquals(0f, impact.translationFactor, 0.001f)
        assertTrue(impact.scaleX < 0.93f)
        assertEquals(impact.scaleX, impact.scaleY, 0.001f)
        assertTrue(rebound.scaleX > 1.04f)
        assertEquals(rebound.scaleX, rebound.scaleY, 0.001f)
        assertEquals(0f, settled.translationFactor, 0.001f)
        assertEquals(1f, settled.scaleX, 0.001f)
        assertEquals(1f, settled.scaleY, 0.001f)
    }

    @Test
    fun adjacentCardsRespondWithinTheFirstTwo120HzFramesAfterContact() {
        val near = weekEditNeighborRippleTransform(100f, 400f, 2f / 120f / 0.9f)
        val far = weekEditNeighborRippleTransform(300f, 400f, 2f / 120f / 0.9f)

        assertTrue(kotlin.math.abs(near.scale - 1f) > 0.001f)
        assertEquals(1f, far.scale, 0.0001f)
        assertEquals(1f, weekEditNeighborRippleTransform(100f, 400f, 0f).scale, 0f)
    }

    @Test
    fun landingWaveReachesNearCardsBeforeFarCards() {
        val near = weekEditNeighborRippleTransform(40f, 400f, 0.18f)
        val far = weekEditNeighborRippleTransform(300f, 400f, 0.18f)

        assertTrue(kotlin.math.abs(near.scale - 1f) > 0.001f)
        assertEquals(0f, far.translationFactor, 0.001f)
        assertEquals(1f, far.scale, 0.001f)
    }

    @Test
    fun landingWaveDeceleratesContinuouslyIntoItsRestingScale() {
        val earlier = weekEditNeighborRippleTransform(80f, 400f, 0.970f).scale
        val later = weekEditNeighborRippleTransform(80f, 400f, 0.985f).scale
        val settled = weekEditNeighborRippleTransform(80f, 400f, 1f).scale

        assertTrue(kotlin.math.abs(later - settled) < kotlin.math.abs(earlier - settled))
        assertTrue(kotlin.math.abs(later - settled) < 0.001f)
        assertEquals(1f, settled, 0.0001f)
    }

    @Test
    fun commitHandoffWaitsForTheActualDestinationOccurrence() {
        val moved = CourseEntity(
            id = 23L,
            name = "专业英语",
            teacher = null,
            location = null,
            weekday = 3,
            periods = listOf(4, 5),
            weeks = listOf(2),
            weekParity = WeekParity.ALL,
            note = null
        )

        assertTrue(
            weekEditCommitTargetPresent(
                courses = listOf(moved),
                targetKey = moved.occurrenceOverrideKey(),
                targetWeek = 2
            )
        )
        assertTrue(
            !weekEditCommitTargetPresent(
                courses = listOf(moved),
                targetKey = moved.occurrenceOverrideKey(),
                targetWeek = 3
            )
        )
    }

    @Test
    fun moveDropBelowTheGridSnapsToTheLastCompleteSpan() {
        val target = weekCourseEditTarget(
            periodIndexes = (1..14).toList(),
            weekday = 3,
            startPeriod = 16,
            span = 2,
            weekdayCount = 7
        )

        assertTrue(target.valid)
        assertEquals(listOf(13, 14), target.periods)
    }

    @Test
    fun moveDropAboveTheGridStillReturnsToTheSource() {
        val target = weekCourseEditTarget(
            periodIndexes = (1..14).toList(),
            weekday = 3,
            startPeriod = 0,
            span = 2,
            weekdayCount = 7
        )

        assertTrue(!target.valid)
    }

    @Test
    fun dragMoveAndResizeAllowOverlappingCoursesWhenTargetIsValid() {
        val original = CourseEntity(
            id = 1L,
            name = "高等数学",
            teacher = null,
            location = null,
            weekday = 1,
            periods = listOf(1, 2),
            weeks = listOf(1),
            weekParity = WeekParity.ALL,
            note = null
        )
        val occupiedTarget = weekCourseEditTarget(
            periodIndexes = (1..14).toList(),
            weekday = 2,
            startPeriod = 1,
            span = 2,
            weekdayCount = 7
        )
        val movedToOccupied = original.copy(weekday = occupiedTarget.weekday, periods = occupiedTarget.periods)
        assertTrue(
            canCommitWeekCourseEditMove(
                target = occupiedTarget,
                canMoveToWeekday = true,
                crossingWeek = false,
                original = original,
                edited = movedToOccupied
            )
        )

        val resizeTarget = weekCourseEditTarget(
            periodIndexes = (1..14).toList(),
            weekday = 1,
            startPeriod = 1,
            span = 4,
            weekdayCount = 7
        )
        val resizedOverOccupied = original.copy(periods = resizeTarget.periods)
        assertTrue(
            canCommitWeekCourseEditResize(
                target = resizeTarget,
                original = original,
                edited = resizedOverOccupied
            )
        )
    }

    @Test
    fun tuckUnderLandingDipsDownwardShrinksBehindFrontCardAndTriggersInhaleRecoil() {
        val tuckStart = weekEditTuckUnderTransform(0f, dipMaxPx = 40f)
        val tuckPeakDip = weekEditTuckUnderTransform(0.44f, dipMaxPx = 40f)
        val tuckFlyBehind = weekEditTuckUnderTransform(0.52f, dipMaxPx = 40f)
        val tuckSeated = weekEditTuckUnderTransform(0.80f, dipMaxPx = 40f)
        val tuckEnd = weekEditTuckUnderTransform(1f, dipMaxPx = 40f)

        assertFalse(tuckStart.behindFront)
        assertEquals(1f, tuckStart.scale, 0.001f)
        assertEquals(1f, tuckStart.alpha, 0.001f)
        assertEquals(0f, tuckStart.dipOffsetY, 0.001f)

        // First drops downward on top while shrinking and fading toward the bottom limit.
        assertFalse(tuckPeakDip.behindFront)
        assertTrue(tuckPeakDip.dipOffsetY > 36f)
        assertTrue(tuckPeakDip.scale < 0.82f)
        assertTrue(tuckPeakDip.alpha < 0.38f)

        // After hitting the bottom limit, switches behind front and flies back into its slot.
        assertTrue(tuckFlyBehind.behindFront)
        assertEquals(0f, tuckFlyBehind.shadowFraction, 0.001f)
        assertTrue(tuckFlyBehind.dipOffsetY > 0f && tuckFlyBehind.dipOffsetY < tuckPeakDip.dipOffsetY)

        // Seated in its underneath slot before the end rebound settles.
        assertTrue(tuckSeated.behindFront)
        assertEquals(0f, tuckSeated.dipOffsetY, 0.001f)
        assertEquals(1f, tuckSeated.scale, 0.001f)
        assertEquals(1f, tuckSeated.alpha, 0.001f)
        assertEquals(0f, tuckEnd.dipOffsetY, 0.001f)
        assertEquals(1f, tuckEnd.scale, 0.001f)
        assertEquals(1f, tuckEnd.alpha, 0.001f)

        val preSeat = weekEditFrontAbsorbTransform(0.30f)
        val compressOnSeat = weekEditFrontAbsorbTransform(0.72f)
        val postSeatRebound = weekEditFrontAbsorbTransform(0.86f)
        val absorbEnd = weekEditFrontAbsorbTransform(1f)
        assertEquals(1f, preSeat.scale, 0.001f)
        assertEquals(0f, preSeat.liftOffsetY, 0.001f)
        assertTrue(compressOnSeat.scale < 0.97f)
        assertTrue(postSeatRebound.scale > 1.04f)
        assertTrue(postSeatRebound.liftOffsetY < -0.2f)
        assertEquals(1f, absorbEnd.scale, 0.001f)
        assertEquals(0f, absorbEnd.liftOffsetY, 0.001f)
    }

    @Test
    fun predictWeekEditOverlapOutcomeUsesFinalStackOrderToDecideTuckAnimation() {
        val periods = (1..14).toList()
        val existingTwoPeriod = CourseEntity(
            id = 10L,
            name = "材料化学",
            teacher = null,
            location = null,
            weekday = 5,
            periods = listOf(7, 8),
            weeks = listOf(1),
            weekParity = WeekParity.ALL,
            note = null
        )
        val movedEqualSpan = CourseEntity(
            id = 20L,
            name = "形势与政策",
            teacher = null,
            location = null,
            weekday = 3,
            periods = listOf(1, 2),
            weeks = listOf(1),
            weekParity = WeekParity.ALL,
            note = null
        )
        val longHost = CourseEntity(
            id = 30L,
            name = "大课",
            teacher = null,
            location = null,
            weekday = 2,
            periods = listOf(1, 2, 3, 4),
            weeks = listOf(1),
            weekParity = WeekParity.ALL,
            note = null
        )

        // Equal span dropped onto existing card: newly saved occurrence lands BEHIND -> play tuck animation.
        val equalSpanDrop = predictWeekEditOverlapOutcome(
            original = movedEqualSpan,
            edited = movedEqualSpan.copy(weekday = 5, periods = listOf(7, 8)),
            weekCourses = listOf(existingTwoPeriod, movedEqualSpan),
            periodIndexes = periods,
            week = 1
        )
        assertTrue(equalSpanDrop.movedCardEndsBehindFront)
        assertEquals(existingTwoPeriod.id, equalSpanDrop.frontHostCourse?.id)
        assertEquals(2, equalSpanDrop.overlapCount)

        // Shorter card dropped onto longer card: shorter card lands ON TOP -> no tuck animation.
        val shortOnLongDrop = predictWeekEditOverlapOutcome(
            original = movedEqualSpan,
            edited = movedEqualSpan.copy(weekday = 2, periods = listOf(2, 3)),
            weekCourses = listOf(longHost, movedEqualSpan),
            periodIndexes = periods,
            week = 1
        )
        assertFalse(shortOnLongDrop.movedCardEndsBehindFront)
        assertEquals(2, shortOnLongDrop.overlapCount)

        // Longer card dropped onto shorter card: longer card lands BEHIND shorter card -> play tuck animation.
        val longOnShortDrop = predictWeekEditOverlapOutcome(
            original = longHost,
            edited = longHost.copy(weekday = 5, periods = listOf(6, 7, 8, 9)),
            weekCourses = listOf(existingTwoPeriod, longHost),
            periodIndexes = periods,
            week = 1
        )
        assertTrue(longOnShortDrop.movedCardEndsBehindFront)
        assertEquals(existingTwoPeriod.id, longOnShortDrop.frontHostCourse?.id)
        assertEquals(2, longOnShortDrop.overlapCount)

        // Empty slot: no overlap, no tuck animation.
        val emptyDrop = predictWeekEditOverlapOutcome(
            original = movedEqualSpan,
            edited = movedEqualSpan.copy(weekday = 1, periods = listOf(3, 4)),
            weekCourses = listOf(existingTwoPeriod, movedEqualSpan),
            periodIndexes = periods,
            week = 1
        )
        assertFalse(emptyDrop.movedCardEndsBehindFront)
        assertEquals(1, emptyDrop.overlapCount)
    }
}
