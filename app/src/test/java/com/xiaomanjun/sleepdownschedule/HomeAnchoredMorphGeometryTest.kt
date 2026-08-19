package com.xiaomanjun.sleepdownschedule

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeAnchoredMorphGeometryTest {
    private val source = Rect(880f, 48f, 922f, 90f)
    private val target = Rect(606f, 104f, 808f, 272f)
    private val destination = Rect(100f, 200f, 900f, 1400f)
    private val menuRect = Rect(700f, 104f, 900f, 300f)
    private val button = Rect(880f, 48f, 922f, 90f)

    @Test
    fun openingStartsAtSourceButton() {
        val geometry = geometry(progress = 0f)

        assertRectEquals(source, geometry.rect)
        assertEquals(1f, geometry.sourceAlpha, Tolerance)
        assertEquals(0f, geometry.contentAlpha, Tolerance)
    }

    @Test
    fun openingKeepsTheRealSourceCornerOnItsFirstFrame() {
        val geometry = homeAnchoredMorphGeometry(
            source = source,
            target = target,
            rawProgress = 0f,
            closing = false,
            sourceCornerRadiusPx = 12f,
            pinchDiameterPx = 10f,
            minimumDropPx = 36f,
            maximumDropPx = 72f,
            maximumArcPx = 48f,
            targetCornerRadiusPx = 26f
        )

        assertEquals(12f, geometry.cornerRadiusPx, Tolerance)
    }

    @Test
    fun openingPinchesToTenPixelDropletAfterFalling() {
        val geometry = geometry(progress = HomeAnchoredMorphPinchFraction)

        assertEquals(10f, geometry.rect.width, Tolerance)
        assertEquals(10f, geometry.rect.height, Tolerance)
        assertEquals(source.center.x, geometry.rect.center.x, Tolerance)
        assertTrue(geometry.rect.center.y > source.center.y)
    }

    @Test
    fun threeDotMenuOpeningStartsAtSourceAndEndsAtTarget() {
        val start = addMenuGeometry(0f, false)
        val end = addMenuGeometry(1f, false)

        assertRectEquals(source, start.rect)
        assertRectEquals(target, end.rect)
        assertEquals(HomeAddMenuTargetCornerDp, end.cornerRadiusPx, Tolerance)
    }

    @Test
    fun addMenuOuterCornerIsConcentricWithTheSelectionCapsule() {
        assertEquals(30f, HomeAddMenuTargetCornerDp, Tolerance)
        assertEquals(19f, HomeAddMenuSelectionCornerDp, Tolerance)
        assertEquals(11f, HomeAddMenuConcentricInsetDp, Tolerance)
        assertEquals(
            HomeAddMenuSelectionCornerDp + HomeAddMenuConcentricInsetDp,
            HomeAddMenuTargetCornerDp,
            Tolerance
        )
    }

    @Test
    fun addMenuHandsTheOuterClipBackToTheSettledKyantSurface() {
        assertTrue(homeAddMenuShellClipEnabled(HomeAnchoredOverlayPhase.Opening))
        assertTrue(!homeAddMenuShellClipEnabled(HomeAnchoredOverlayPhase.Open))
        assertTrue(homeAddMenuShellClipEnabled(HomeAnchoredOverlayPhase.Closing))
    }

    @Test
    fun threeDotMenuOpeningPinchesInPlaceWhileFalling() {
        val early = addMenuGeometry(ThreeDotMenuMotion.OpenPinchFraction / 2f, false).rect
        val droplet = addMenuGeometry(ThreeDotMenuMotion.OpenPinchFraction, false).rect

        assertEquals(source.center.x, early.center.x, Tolerance)
        assertTrue(early.center.y > source.center.y)
        assertTrue(early.width < source.width)
        assertTrue(early.height < source.height)
        assertEquals(18f, droplet.width, Tolerance)
        assertEquals(18f, droplet.height, Tolerance)
        assertEquals(source.center.x, droplet.center.x, Tolerance)
        assertEquals(source.center.y + 36f, droplet.center.y, Tolerance)
    }

    @Test
    fun threeDotMenuOpeningPinchShrinksContinuously() {
        var previous = addMenuGeometry(0f, false).rect
        for (step in 1..28) {
            val progress = ThreeDotMenuMotion.OpenPinchFraction * step / 28f
            val current = addMenuGeometry(progress, false).rect
            assertEquals(source.center.x, current.center.x, Tolerance)
            assertTrue(current.center.y >= previous.center.y - Tolerance)
            assertTrue(current.width <= previous.width + Tolerance)
            assertTrue(current.height <= previous.height + Tolerance)
            assertTrue(current.width > 0f)
            assertTrue(current.height > 0f)
            previous = current
        }
    }

    @Test
    fun threeDotMenuOpeningExpansionStartsWithoutABoundaryJump() {
        val boundary = ThreeDotMenuMotion.OpenPinchFraction
        val epsilon = 0.00001f
        val before = addMenuGeometry(boundary - epsilon, false).rect
        val atBoundary = addMenuGeometry(boundary, false).rect
        val after = addMenuGeometry(boundary + epsilon, false).rect

        assertTrue(kotlin.math.abs(atBoundary.left - before.left) < 0.05f)
        assertTrue(kotlin.math.abs(atBoundary.top - before.top) < 0.05f)
        assertTrue(kotlin.math.abs(after.left - atBoundary.left) < 0.05f)
        assertTrue(kotlin.math.abs(after.top - atBoundary.top) < 0.05f)
        assertTrue(kotlin.math.abs(after.right - atBoundary.right) < 0.05f)
        assertTrue(kotlin.math.abs(after.bottom - atBoundary.bottom) < 0.05f)
    }

    @Test
    fun threeDotMenuOpeningRestoresTheGoldenExpansionCurves() {
        val earlyPosition = threeDotMenuOpeningPositionProgress(0.20f)
        val earlySize = threeDotMenuOpeningSizeProgress(0.20f)
        val latePosition = threeDotMenuOpeningPositionProgress(0.80f)
        val lateSize = threeDotMenuOpeningSizeProgress(0.80f)

        assertTrue(earlyPosition > earlySize)
        assertTrue(earlyPosition > 0.45f)
        assertTrue(latePosition > 0.95f)
        assertTrue(lateSize > 0.90f)
        assertEquals(1f, threeDotMenuOpeningPositionProgress(1f), Tolerance)
        assertEquals(1f, threeDotMenuOpeningSizeProgress(1f), Tolerance)
    }

    @Test
    fun threeDotMenuOpeningExpansionUsesRightEdgeAnchoring() {
        val expansion = 0.50f
        val raw = ThreeDotMenuMotion.OpenPinchFraction +
            expansion * (1f - ThreeDotMenuMotion.OpenPinchFraction)
        val geometry = addMenuGeometry(raw, false)
        val sizeProgress = threeDotMenuOpeningSizeProgress(expansion)
        val dropletRight = source.center.x + 9f
        val expectedRight = lerpHomeMorph(dropletRight, target.right, sizeProgress)

        assertEquals(expectedRight, geometry.rect.right, Tolerance)
        assertTrue(geometry.rect.center.x < source.center.x)
        assertTrue(geometry.rect.center.y > source.center.y + 36f)
    }

    @Test
    fun threeDotMenuOpeningExpansionGrowsContinuouslyToTheCurrentMenu() {
        var previous = addMenuGeometry(ThreeDotMenuMotion.OpenPinchFraction, false).rect
        val pulseStart = ThreeDotMenuMotion.OpenPinchFraction +
            0.82f * (1f - ThreeDotMenuMotion.OpenPinchFraction)
        var progress = ThreeDotMenuMotion.OpenPinchFraction + 0.01f
        while (progress <= pulseStart) {
            val current = addMenuGeometry(progress, false).rect
            assertTrue(
                "width shrank at $progress: ${previous.width} -> ${current.width}",
                current.width >= previous.width - 0.02f
            )
            assertTrue(
                "height shrank at $progress: ${previous.height} -> ${current.height}",
                current.height >= previous.height - 0.02f
            )
            previous = current
            progress += 0.01f
        }

        var maximumWidth = previous.width
        var maximumHeight = previous.height
        while (progress <= 1f + Tolerance) {
            val current = addMenuGeometry(progress.coerceAtMost(1f), false).rect
            maximumWidth = maxOf(maximumWidth, current.width)
            maximumHeight = maxOf(maximumHeight, current.height)
            progress += 0.01f
        }
        assertTrue(maximumWidth > target.width)
        assertTrue(maximumHeight > target.height)
        assertTrue(maximumWidth < target.width * 1.01f)
        assertTrue(maximumHeight < target.height * 1.01f)
        assertRectEquals(target, addMenuGeometry(1f, false).rect)
    }

    @Test
    fun threeDotMenuOpeningReboundPeaksDuringExpansionAndSettles() {
        val peakRaw = ThreeDotMenuMotion.OpenPinchFraction +
            ThreeDotMenuMotion.OpenReboundPeakFraction *
            (1f - ThreeDotMenuMotion.OpenPinchFraction)
        val peak = addMenuGeometry(peakRaw, false)
        val peakWithoutRebound = addMenuGeometry(
            progress = peakRaw,
            closing = false,
            verticalReboundAmplitudePx = 0f
        )

        assertEquals(12f, peak.rect.center.y - peakWithoutRebound.rect.center.y, Tolerance)
        assertTrue(peak.rect.width > 18f)
        assertTrue(peak.rect.width < target.width)
        assertTrue(peak.expansionProgress in 0.39f..0.41f)
        assertRectEquals(target, addMenuGeometry(1f, false).rect)
    }

    @Test
    fun threeDotMenuClosingSinksBeforeReturning() {
        val start = addMenuGeometry(1f, true).rect
        val sink = addMenuGeometry(0.94f, true).rect

        assertEquals(target.center.x, sink.center.x, Tolerance)
        assertTrue(sink.center.y > start.center.y)
        assertTrue(sink.height < start.height)
        assertTrue(sink.width >= start.width - Tolerance)
    }

    @Test
    fun threeDotMenuClosingSinkOverlapsTheReturnCurve() {
        var maximumCenterY = target.center.y
        var raw = 1f
        while (raw >= 0.70f) {
            maximumCenterY = maxOf(maximumCenterY, addMenuGeometry(raw, true).rect.center.y)
            raw -= 0.002f
        }

        assertTrue(maximumCenterY > target.center.y + 12f)
        val beforeBlend = addMenuGeometry(0.921f, true).rect.center
        val afterBlend = addMenuGeometry(0.919f, true).rect.center
        assertTrue(kotlin.math.abs(afterBlend.x - beforeBlend.x) < 2f)
        assertTrue(kotlin.math.abs(afterBlend.y - beforeBlend.y) < 2f)
    }

    @Test
    fun threeDotMenuClosingUsesAnIndependentTrajectoryAndReturnsExactly() {
        val openingMiddle = addMenuGeometry(0.50f, false).rect
        val closingMiddle = addMenuGeometry(0.50f, true).rect

        assertTrue(kotlin.math.abs(openingMiddle.center.x - closingMiddle.center.x) > 8f)
        assertTrue(kotlin.math.abs(openingMiddle.center.y - closingMiddle.center.y) > 8f)
        assertRectEquals(target, addMenuGeometry(1f, true).rect)
        assertRectEquals(source, addMenuGeometry(0f, true).rect)
    }

    @Test
    fun threeDotMenuUsesRequestedOpeningAndClosingDurations() {
        assertEquals(440, HomeAddMenuMorphOpenDurationMillis)
        assertEquals(285, HomeAddMenuMorphCloseDurationMillis)
    }

    @Test
    fun threeDotMenuOpeningStartsAtPressedSourceFootprint() {
        val pressed = 1.047f
        val geometry = addMenuGeometry(0f, false, pressed)
        val expected = Rect(
            left = source.center.x - source.width * pressed / 2f,
            top = source.center.y - source.height * pressed / 2f,
            right = source.center.x + source.width * pressed / 2f,
            bottom = source.center.y + source.height * pressed / 2f
        )

        assertRectEquals(expected, geometry.rect)
        assertEquals(1f, geometry.sourceScale, Tolerance)
    }

    @Test
    fun threeDotMenuSourceCloneShrinksWithThePressedShell() {
        val pressed = 1.047f
        val droplet = addMenuGeometry(ThreeDotMenuMotion.OpenPinchFraction, false, pressed)
        val expanding = addMenuGeometry(0.50f, false, pressed)
        val expectedScale = 18f / (source.width * pressed)

        assertEquals(expectedScale, droplet.sourceScale, Tolerance)
        assertEquals(expectedScale, expanding.sourceScale, Tolerance)
        assertEquals(1f, droplet.sourceAlpha, Tolerance)
        assertTrue(expanding.sourceAlpha < 1f)
    }

    @Test
    fun destinationSharedTrajectoryStartsAtMenuAndEndsAtDestination() {
        assertRectEquals(menuRect, destinationTrajectoryGeometry(0f, false).rect)
        assertRectEquals(destination, destinationTrajectoryGeometry(1f, false).rect)
    }

    @Test
    fun destinationSharedTrajectoryReturnsDirectlyToCollapseButtonBounds() {
        assertRectEquals(destination, destinationTrajectoryGeometry(1f, true).rect)
        assertRectEquals(button, destinationTrajectoryGeometry(0f, true).rect)
        assertTrue(
            kotlin.math.abs(
                destinationTrajectoryGeometry(0f, true).rect.center.x - menuRect.center.x
            ) > 20f
        )
    }

    @Test
    fun destinationLegacyOpeningMovesAndGrowsDirectlyFromTheMenu() {
        val early = destinationTrajectoryGeometry(0.16f, false).rect

        assertTrue(early.center.x < menuRect.center.x)
        assertTrue(early.center.y > menuRect.center.y)
        assertTrue(early.width > menuRect.width)
        assertTrue(early.height > menuRect.height)
        assertTrue(early.width > 72f)
    }

    @Test
    fun destinationTrajectoryHelperMatchesTheLegacyDirectGeometry() {
        val helper = destinationTrajectoryGeometry(0.42f, false)
        val explicit = homeAnchoredMorphGeometry(
            source = menuRect,
            target = destination,
            rawProgress = 0.42f,
            closing = false,
            directClosing = true,
            directSourceCornerRadiusPx = HomeAddMenuTargetCornerDp,
            pinchDiameterPx = 18f,
            minimumDropPx = 12f,
            maximumDropPx = 72f,
            maximumArcPx = 88f,
            targetCornerRadiusPx = 32f,
            motionStyle = HomeMorphEasingStyle.Legacy
        )

        assertRectEquals(explicit.rect, helper.rect)
        assertEquals(explicit.cornerRadiusPx, helper.cornerRadiusPx, Tolerance)
        assertEquals(explicit.pathProgress, helper.pathProgress, Tolerance)
    }

    @Test
    fun destinationClosingMatchesTheLegacyButtonAnchoredGeometry() {
        val helper = destinationTrajectoryGeometry(0.42f, true)
        val explicit = homeAnchoredMorphGeometry(
            source = button,
            target = destination,
            rawProgress = 0.42f,
            closing = true,
            directClosing = false,
            directSourceCornerRadiusPx = 21f,
            pinchDiameterPx = 18f,
            minimumDropPx = 12f,
            maximumDropPx = 72f,
            maximumArcPx = 88f,
            targetCornerRadiusPx = 32f,
            motionStyle = HomeMorphEasingStyle.Legacy
        )

        assertRectEquals(explicit.rect, helper.rect)
        assertEquals(explicit.pathProgress, helper.pathProgress, Tolerance)
    }

    @Test
    fun fullScreenDestinationRestoresRoundedShellImmediatelyOnClose() {
        val start = destinationTrajectoryGeometry(1f, true)
        val justStarted = destinationTrajectoryGeometry(0.99f, true)
        val endpoint = destinationTrajectoryGeometry(0f, true)

        assertEquals(
            46f,
            homeMenuDestinationRenderedCornerRadiusPx(
                geometry = start,
                rawProgress = 1f,
                isFullScreen = true,
                closing = true,
                sourceCornerRadiusPx = HomeAddMenuTargetCornerDp,
                collapseCornerRadiusPx = 21f,
                middleCornerRadiusPx = 46f
            ),
            Tolerance
        )
        assertEquals(
            46f,
            homeMenuDestinationRenderedCornerRadiusPx(
                geometry = justStarted,
                rawProgress = 0.99f,
                isFullScreen = true,
                closing = true,
                sourceCornerRadiusPx = HomeAddMenuTargetCornerDp,
                collapseCornerRadiusPx = 21f,
                middleCornerRadiusPx = 46f
            ),
            Tolerance
        )
        assertEquals(
            21f,
            homeMenuDestinationRenderedCornerRadiusPx(
                geometry = endpoint,
                rawProgress = 0f,
                isFullScreen = true,
                closing = true,
                sourceCornerRadiusPx = HomeAddMenuTargetCornerDp,
                collapseCornerRadiusPx = 21f,
                middleCornerRadiusPx = 46f
            ),
            Tolerance
        )
    }

    @Test
    fun stableActivityContentOffsetPreservesRootCoordinatesInsideAnimatedShell() {
        val rootExtent = 1080f
        val shellStart = 648f
        val shellExtent = 388f
        val rootPosition = 668f
        val stableChildPlacement = (shellExtent - rootExtent) / 2f
        val localOffset = anchoredStableContentOffsetPx(
            positionInRootPx = rootPosition,
            shellStartPx = shellStart,
            rootExtentPx = rootExtent,
            shellExtentPx = shellExtent
        )

        assertEquals(
            rootPosition,
            shellStart + stableChildPlacement + localOffset,
            Tolerance
        )
    }

    @Test
    fun personalizationReusesThreeDotMenuMotionDurations() {
        assertEquals(330, HomeMenuDestinationLegacyMotion.OpenDurationMillis)
        assertEquals(350, HomeMenuDestinationLegacyMotion.CloseDurationMillis)
        assertEquals(350, HomeMenuDestinationCloseDurationMillis)
        assertEquals(HomeAddMenuMorphOpenDurationMillis, HomePersonalizeMorphOpenDurationMillis)
        assertEquals(HomeAddMenuMorphCloseDurationMillis, HomePersonalizeMorphCloseDurationMillis)
        assertTrue(HomeMenuDestinationLegacyMotion.OpenDurationMillis != HomeAddMenuMorphOpenDurationMillis)
        assertEquals(HomePersonalizeMorphCloseDurationMillis, HomeAddMenuMorphCloseDurationMillis)
    }

    @Test
    fun personalizationWrapperDelegatesToTheThreeDotLiquidGeometry() {
        val helper = homePersonalizationTrajectoryGeometry(
            source = source,
            target = destination,
            rawProgress = 0.47f,
            pinchDiameterPx = 18f,
            minimumDropPx = 36f,
            maximumDropPx = 72f,
            maximumArcPx = 88f,
            targetCornerRadiusPx = 28f,
            sourcePressedScale = 1.04f
        )
        val explicit = homeThreeDotMenuTrajectoryGeometry(
            source = source,
            target = destination,
            rawProgress = 0.47f,
            closing = false,
            sourceCornerRadiusPx = minOf(source.width, source.height) / 2f,
            targetCornerRadiusPx = 28f,
            sourcePressedScale = 1.04f,
            openingPinchDiameterPx = 18f,
            openingMinimumDropPx = 36f,
            openingMaximumDropPx = 72f,
            openingMaximumArcPx = 88f
        )

        assertRectEquals(explicit.rect, helper.rect)
        assertEquals(explicit.sourceAlpha, helper.sourceAlpha, Tolerance)
        assertEquals(explicit.contentAlpha, helper.contentAlpha, Tolerance)
    }

    @Test
    fun normalDestinationDefersFormContentUntilTheShellIsStableAndNeverBlursItOnOpen() {
        assertEquals(
            0f,
            homeMenuDestinationContentAlpha(
                rawProgress = HomeMenuDestinationLegacyMotion.NonFullscreenContentRevealStart,
                isFullScreen = false,
                closing = false
            ),
            Tolerance
        )
        assertTrue(
            homeMenuDestinationContentAlpha(
                rawProgress = 0.62f,
                isFullScreen = false,
                closing = false
            ) > 0f
        )
        assertEquals(
            1f,
            homeMenuDestinationContentAlpha(
                rawProgress = HomeMenuDestinationLegacyMotion.NonFullscreenContentRevealEnd,
                isFullScreen = false,
                closing = false
            ),
            Tolerance
        )
        assertEquals(
            0f,
            homeMenuDestinationOpeningContentBlurMix(rawProgress = 0.62f, isFullScreen = false),
            Tolerance
        )
    }

    @Test
    fun centeredSharedTrajectoryUsesItsBezierCenterRatherThanTheMenuTrailingEdge() {
        val raw = ThreeDotMenuMotion.OpenPinchFraction +
            0.50f * (1f - ThreeDotMenuMotion.OpenPinchFraction)
        val centered = homeCenteredSharedObjectTrajectoryGeometry(
            source = source,
            target = target,
            rawProgress = raw,
            closing = false,
            sourceCornerRadiusPx = 21f,
            targetCornerRadiusPx = 32f,
            openingPinchDiameterPx = 18f,
            openingMinimumDropPx = 36f,
            openingMaximumDropPx = 72f,
            openingMaximumArcPx = 48f,
            verticalReboundAmplitudePx = 12f,
            closingSinkOffsetPx = 12f,
            closingControlDropPx = 28f
        )
        val trailingEdge = addMenuGeometry(raw, false)

        assertTrue(kotlin.math.abs(centered.rect.center.x - trailingEdge.rect.center.x) > 8f)
        assertEquals(target.center.x, homeCenteredSharedObjectTrajectoryGeometry(
            source = source,
            target = target,
            rawProgress = 1f,
            closing = false,
            targetCornerRadiusPx = 32f
        ).rect.center.x, Tolerance)
    }

    @Test
    fun openingUsesANonLinearCubicAndKeepsMovingThroughTheRebound() {
        val atTwentyPercent = homeAnchoredOpenMotionProgress(0.20f)
        val atFortyPercent = homeAnchoredOpenMotionProgress(0.40f)
        val atReboundEnd = homeAnchoredOpenMotionProgress(HomeAnchoredOpenSettleStartFraction)
        val atNinetyPercent = homeAnchoredOpenMotionProgress(0.90f)

        // A linear trajectory would make f(0.4) exactly twice f(0.2).
        assertTrue(kotlin.math.abs(atFortyPercent - atTwentyPercent * 2f) > 0.04f)
        assertTrue(atTwentyPercent > 0.20f)
        // The shell still has meaningful distance left after rebound; it has not entered a long
        // near-static tail before the final settle.
        assertTrue(atReboundEnd < 0.90f)
        assertTrue(atNinetyPercent < 0.96f)
        assertTrue(atNinetyPercent > atReboundEnd)
        val terminalVelocity = (
            homeAnchoredOpenMotionProgress(1f) - homeAnchoredOpenMotionProgress(0.98f)
            ) / 0.02f
        assertTrue(terminalVelocity > 0.30f)
        assertEquals(1f, homeAnchoredOpenMotionProgress(1f), Tolerance)
    }

    @Test
    fun closingRetainsVelocityIntoTheSourceHandoff() {
        val terminalVelocity = (
            homeAnchoredCloseMotionProgress(0.02f) - homeAnchoredCloseMotionProgress(0f)
            ) / 0.02f
        assertTrue(terminalVelocity > 0.15f)
    }

    @Test
    fun bothOverlayCloseDurationsAreShorterThanOpening() {
        assertTrue(HomeAddMenuMorphCloseDurationMillis < HomeAnchoredMorphOpenDurationMillis)
        assertTrue(HomePersonalizeMorphCloseDurationMillis < HomeAnchoredMorphOpenDurationMillis)
    }

    @Test
    fun verticalReboundOvershootsDuringOpeningAndReturnsExactlyToTarget() {
        val peakBase = geometry(
            progress = HomeAnchoredMorphPinchFraction +
                0.40f * (1f - HomeAnchoredMorphPinchFraction)
        )
        val peak = homeMorphWithVerticalRebound(
            geometry = peakBase,
            closing = false,
            overshootPx = 12f,
            peakProgress = 0.40f
        )
        val endpoint = homeMorphWithVerticalRebound(
            geometry = geometry(progress = 1f),
            closing = false,
            overshootPx = 12f,
            peakProgress = 0.40f
        )

        assertTrue(peak.rect.top > peakBase.rect.top)
        assertRectEquals(target, endpoint.rect)
    }

    @Test
    fun reboundFinishesAtItsBoundaryAndIsNotReplayedOnClose() {
        val settleRawProgress = HomeAnchoredMorphPinchFraction +
            HomeAnchoredOpenSettleStartFraction * (1f - HomeAnchoredMorphPinchFraction)
        val settleBase = geometry(progress = settleRawProgress)
        val settled = homeMorphWithVerticalRebound(
            geometry = settleBase,
            closing = false,
            overshootPx = 12f,
            peakProgress = 0.40f
        )
        assertRectEquals(settleBase.rect, settled.rect)

        val closingBase = geometry(progress = 0.58f, closing = true)
        val closingWithRebound = homeMorphWithVerticalRebound(
            geometry = closingBase,
            closing = true,
            overshootPx = 12f,
            peakProgress = 0.40f
        )
        assertRectEquals(closingBase.rect, closingWithRebound.rect)
    }

    @Test
    fun openingFinishesAtTargetPanel() {
        assertRectEquals(target, geometry(progress = 1f).rect)
    }

    @Test
    fun closingReturnsToSameAnchor() {
        assertRectEquals(target, geometry(progress = 1f, closing = true).rect)
        assertRectEquals(source, geometry(progress = 0f, closing = true).rect)
    }

    @Test
    fun closingUsesResponsiveTrajectoryWithoutMovingPhaseHandoffs() {
        val openingAtSameProgress = geometry(progress = 0.90f, closing = false)
        val closingAfterFirstTenth = geometry(progress = 0.90f, closing = true)

        val openingDistanceFromTarget =
            kotlin.math.abs(openingAtSameProgress.rect.center.x - target.center.x)
        val closingDistanceFromTarget =
            kotlin.math.abs(closingAfterFirstTenth.rect.center.x - target.center.x)
        assertTrue(closingDistanceFromTarget > openingDistanceFromTarget * 3f)
        assertEquals(
            openingAtSameProgress.expansionProgress,
            closingAfterFirstTenth.expansionProgress,
            Tolerance
        )
        assertEquals(
            openingAtSameProgress.surfaceAlpha,
            closingAfterFirstTenth.surfaceAlpha,
            Tolerance
        )
        assertEquals(
            openingAtSameProgress.contentAlpha,
            closingAfterFirstTenth.contentAlpha,
            Tolerance
        )
    }

    @Test
    fun legacyMotionStyleRestoresPreviousClosingPhaseClock() {
        val legacy = legacyGeometry(progress = 0.5f, closing = true)
        val directional = geometry(progress = 0.5f, closing = true)

        // The previous version eased the closing phase clock with the reverse close cubic, so
        // halfway through a close the legacy shell is still much closer to the target than the
        // linear phase clock used by the current directional style.
        assertTrue(legacy.pathProgress < directional.pathProgress * 0.5f)
    }

    @Test
    fun legacyClosingUsesPreviousHandoffWindow() {
        val legacy = legacyGeometry(progress = 0.4f, closing = true)
        val directional = geometry(progress = 0.4f, closing = true)

        // The previous close kept the source clone almost until the very end (0.015..0.12),
        // while the directional close fades it out through the shared opening window.
        assertTrue(legacy.surfaceAlpha < directional.surfaceAlpha * 0.5f)
    }

    @Test
    fun addTargetUsesSpecifiedSizeAndClampsToRoot() {
        val root = IntSize(1080, 1920)
        val result = homeAddMenuTargetRect(
            source = Rect(1010f, 80f, 1052f, 122f),
            rootSize = root,
            density = 2f,
            actionCount = 6
        )

        assertEquals(388f, result.width, Tolerance)
        assertEquals(634f, result.height, Tolerance)
        assertInBounds(result, root, margin = 24f)
    }

    @Test
    fun addTargetAlignsExactlyWithSourceRightEdgeWhenThereIsRoom() {
        val source = Rect(480f, 80f, 522f, 122f)
        val result = homeAddMenuTargetRect(
            source = source,
            rootSize = IntSize(1080, 1920),
            density = 2f,
            actionCount = 6
        )

        assertEquals(source.right, result.right, Tolerance)
        assertEquals(source.bottom + 8f, result.top, Tolerance)
        assertEquals(388f, result.width, Tolerance)
    }

    @Test
    fun addMenuDividerAndRowGapsRemainInsideContinuousActionHitRegions() {
        assertEquals(-1, homeAddMenuHitIndex(52f, 52f, 61f, 40f, 5))
        assertEquals(0, homeAddMenuHitIndex(53f, 52f, 61f, 40f, 5))
        assertEquals(0, homeAddMenuHitIndex(60.9f, 52f, 61f, 40f, 5))
        assertEquals(0, homeAddMenuHitIndex(100.9f, 52f, 61f, 40f, 5))
        assertEquals(1, homeAddMenuHitIndex(101f, 52f, 61f, 40f, 5))
    }

    @Test
    fun personalizeTargetAndOversizedTargetsStayInBounds() {
        val root = IntSize(1080, 2400)
        val personalize = homePersonalizeTargetRect(root, density = 3f)
        val clamped = clampHomeMorphTarget(
            target = Rect(-100f, -200f, 1400f, 2800f),
            rootSize = root,
            marginPx = 36f
        )

        assertEquals(934.8f, personalize.width, Tolerance)
        assertInBounds(personalize, root, margin = 36f)
        assertEquals(1008f, clamped.width, Tolerance)
        assertEquals(2328f, clamped.height, Tolerance)
        assertInBounds(clamped, root, margin = 36f)
    }

    @Test
    fun tabletPersonalizePanelIsTallNarrowAndInsideAdaptiveContent() {
        val metrics = calculateHomeAdaptiveMetrics(
            widthDp = 1280,
            heightDp = 800,
            safeTop = 24.dp,
            safeBottom = 24.dp,
            fontScale = 1f
        )
        val root = IntSize(2560, 1600)
        val content = metrics.contentRectPx(root, density = 2f)
        val personalize = homePersonalizeTargetRect(
            rootSize = root,
            density = 2f,
            adaptiveMetrics = metrics
        )

        assertTrue(personalize.height > personalize.width)
        assertTrue(personalize.width <= 896f)
        assertEquals(content.right, personalize.right, Tolerance)
        assertTrue(personalize.top >= content.top)
        assertTrue(personalize.bottom <= content.bottom)
    }

    @Test
    fun personalizationBackdropBlurFollowsMorphAndSliderPreview() {
        assertEquals(0f, personalizeBackdropBlurLayerProgress(0f, 0f), Tolerance)
        assertEquals(1f, personalizeBackdropBlurLayerProgress(1f, 0f), Tolerance)
        assertEquals(0f, personalizeBackdropBlurLayerProgress(1f, 1f), Tolerance)

        val opening = personalizeBackdropBlurLayerProgress(0.30f, 0f)
        val dragging = personalizeBackdropBlurLayerProgress(1f, 0.45f)
        assertTrue(opening in 0f..1f)
        assertTrue(opening > 0f && opening < 1f)
        assertEquals(0.55f, dragging, Tolerance)
    }

    private fun geometry(
        progress: Float,
        closing: Boolean = false
    ): HomeAnchoredMorphGeometry = homeAnchoredMorphGeometry(
        source = source,
        target = target,
        rawProgress = progress,
        closing = closing,
        pinchDiameterPx = 10f,
        minimumDropPx = 36f,
        maximumDropPx = 72f,
        maximumArcPx = 48f,
        targetCornerRadiusPx = 26f
    )

    private fun addMenuGeometry(
        progress: Float,
        closing: Boolean,
        sourcePressedScale: Float = 1f,
        verticalReboundAmplitudePx: Float = 12f
    ): HomeAnchoredMorphGeometry = homeThreeDotMenuTrajectoryGeometry(
        source = source,
        target = target,
        rawProgress = progress,
        closing = closing,
        sourceCornerRadiusPx = 21f,
        targetCornerRadiusPx = HomeAddMenuTargetCornerDp,
        sourcePressedScale = sourcePressedScale,
        openingPinchDiameterPx = 18f,
        openingMinimumDropPx = 36f,
        openingMaximumDropPx = 72f,
        openingMaximumArcPx = 48f,
        verticalReboundAmplitudePx = verticalReboundAmplitudePx,
        closingSinkOffsetPx = 12f,
        closingControlDropPx = 28f
    )

    private fun destinationTrajectoryGeometry(
        progress: Float,
        closing: Boolean
    ): HomeAnchoredMorphGeometry = homeMenuDestinationTrajectoryGeometry(
        sourceBoundsInRoot = menuRect,
        collapseBoundsInRoot = button,
        target = destination,
        rawProgress = progress,
        closing = closing,
        menuCornerRadiusPx = HomeAddMenuTargetCornerDp,
        buttonCornerRadiusPx = 21f,
        pinchDiameterPx = 18f,
        minimumDropPx = 12f,
        maximumDropPx = 72f,
        maximumArcPx = 88f,
        targetCornerRadiusPx = 32f
    )

    private fun legacyGeometry(
        progress: Float,
        closing: Boolean
    ): HomeAnchoredMorphGeometry = homeAnchoredMorphGeometry(
        source = source,
        target = target,
        rawProgress = progress,
        closing = closing,
        pinchDiameterPx = 10f,
        minimumDropPx = 36f,
        maximumDropPx = 72f,
        maximumArcPx = 48f,
        targetCornerRadiusPx = 26f,
        motionStyle = HomeMorphEasingStyle.Legacy
    )

    private fun assertRectEquals(expected: Rect, actual: Rect) {
        assertEquals(expected.left, actual.left, Tolerance)
        assertEquals(expected.top, actual.top, Tolerance)
        assertEquals(expected.right, actual.right, Tolerance)
        assertEquals(expected.bottom, actual.bottom, Tolerance)
    }

    private fun assertInBounds(rect: Rect, root: IntSize, margin: Float) {
        assertTrue(rect.left >= margin - Tolerance)
        assertTrue(rect.top >= margin - Tolerance)
        assertTrue(rect.right <= root.width - margin + Tolerance)
        assertTrue(rect.bottom <= root.height - margin + Tolerance)
    }

    private companion object {
        const val Tolerance = 0.01f
    }
}
