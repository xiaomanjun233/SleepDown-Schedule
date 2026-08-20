package com.xiaomanjun.sleepdownschedule

import org.junit.Assert.assertEquals
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
    fun repositoryBackedCardFallsFromLiftedPoseToExactGeometry() {
        val lifted = weekEditRealCardLandingTransform(0f)
        val landed = weekEditRealCardLandingTransform(1f)
        val overshoot = weekEditRealCardLandingTransform(1.08f)

        assertEquals(WeekEditLiftedScale, lifted.scale, 0.001f)
        assertEquals(1f, lifted.liftFactor, 0.001f)
        assertEquals(1f, landed.scale, 0.001f)
        assertEquals(0f, landed.liftFactor, 0.001f)
        assertTrue(overshoot.scale < 1f)
        assertTrue(overshoot.liftFactor < 0f)
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
}
