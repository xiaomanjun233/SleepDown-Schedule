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
    fun addMenuSqueezesHorizontallyInPlaceBeforeExpansion() {
        val geometry = homeAddMenuMorphGeometry(
            source = source,
            target = target,
            rawProgress = HomeAddMenuSqueezeFraction,
            closing = false,
            pinchDiameterPx = 10f,
            minimumDropPx = 36f,
            maximumDropPx = 72f,
            maximumArcPx = 48f,
            targetCornerRadiusPx = 26f,
            reboundOvershootPx = 12f
        )

        assertEquals(source.center.x, geometry.rect.center.x, Tolerance)
        assertEquals(source.center.y, geometry.rect.center.y, Tolerance)
        assertEquals(source.width * HomeAddMenuSqueezedWidthFraction, geometry.rect.width, Tolerance)
        assertEquals(source.height, geometry.rect.height, Tolerance)
    }

    @Test
    fun addMenuCornerStartsResolvingDuringTheSqueeze() {
        val start = addMenuGeometry(progress = 0f, closing = false)
        val midway = addMenuGeometry(
            progress = HomeAddMenuSqueezeFraction / 2f,
            closing = false
        )
        val squeezed = addMenuGeometry(
            progress = HomeAddMenuSqueezeFraction,
            closing = false
        )

        assertTrue(midway.cornerRadiusPx > start.cornerRadiusPx)
        assertTrue(squeezed.cornerRadiusPx > midway.cornerRadiusPx)
        assertTrue(squeezed.cornerRadiusPx < 32f)
        assertEquals(0f, midway.contentAlpha, Tolerance)
    }

    @Test
    fun productionAddMenuStartsCornerMorphDuringShorterDropWithoutMovingContentHandoff() {
        fun productionGeometry(progress: Float) = homeAnchoredMorphGeometry(
            source = source,
            target = target,
            rawProgress = progress,
            closing = false,
            pinchDiameterPx = 18f,
            minimumDropPx = 36f,
            maximumDropPx = 72f,
            maximumArcPx = 48f,
            targetCornerRadiusPx = 30f,
            pinchFractionOverride = HomeAddMenuPinchFraction,
            cornerMorphDuringPinchFraction = 0.42f,
            handoffStartFraction = 0.1135f,
            handoffEndFraction = 0.28f,
            contentStartFraction = 0.154f,
            contentEndFraction = 0.424f
        )

        val start = productionGeometry(0f)
        val duringDrop = productionGeometry(HomeAddMenuPinchFraction / 2f)
        val endOfDrop = productionGeometry(HomeAddMenuPinchFraction)
        assertTrue(duringDrop.cornerRadiusPx > start.cornerRadiusPx)
        assertTrue(endOfDrop.cornerRadiusPx > duringDrop.cornerRadiusPx)
        assertTrue(endOfDrop.rect.center.y > source.center.y)

        val oldHandoff = homeAnchoredMorphGeometry(
            source = source,
            target = target,
            rawProgress = 0.43f,
            closing = false,
            pinchDiameterPx = 18f,
            minimumDropPx = 36f,
            maximumDropPx = 72f,
            maximumArcPx = 48f,
            targetCornerRadiusPx = 32f,
            handoffStartFraction = 0.015f,
            handoffEndFraction = 0.20f,
            contentStartFraction = 0.06f,
            contentEndFraction = 0.36f
        )
        val newHandoff = productionGeometry(0.43f)
        assertEquals(oldHandoff.surfaceAlpha, newHandoff.surfaceAlpha, 0.002f)
        assertEquals(oldHandoff.contentAlpha, newHandoff.contentAlpha, 0.002f)
    }

    @Test
    fun addMenuClosingExactlyReversesTheCapturedOpeningGeometry() {
        val capturedScale = 1.047f
        val opening = addMenuGeometry(
            progress = 0f,
            closing = false,
            sourcePressedScale = capturedScale
        )
        val closing = addMenuGeometry(
            progress = 0f,
            closing = true,
            sourcePressedScale = capturedScale
        )

        assertEquals(source.width * capturedScale, opening.rect.width, Tolerance)
        assertEquals(source.height * capturedScale, opening.rect.height, Tolerance)
        assertEquals(source.center.x, opening.rect.center.x, Tolerance)
        assertEquals(source.center.y, opening.rect.center.y, Tolerance)
        assertRectEquals(opening.rect, closing.rect)

        val openingMid = addMenuGeometry(0.58f, false, capturedScale)
        val closingMid = addMenuGeometry(0.58f, true, capturedScale)
        assertRectEquals(openingMid.rect, closingMid.rect)
        assertEquals(openingMid.contentAlpha, closingMid.contentAlpha, Tolerance)
    }

    @Test
    fun addMenuKeepsDownwardReboundButFinishesAtOriginalTargetHeight() {
        val peakProgress = HomeAddMenuSqueezeFraction +
            HomeAddMenuReboundPeakFraction * (1f - HomeAddMenuSqueezeFraction)
        val peak = addMenuGeometry(progress = peakProgress, closing = false)
        val peakWithoutRebound = homeAddMenuMorphGeometry(
            source = source,
            target = target,
            rawProgress = peakProgress,
            closing = false,
            pinchDiameterPx = 10f,
            minimumDropPx = 36f,
            maximumDropPx = 72f,
            maximumArcPx = 48f,
            targetCornerRadiusPx = 32f,
            reboundOvershootPx = 0f
        )
        val endpoint = addMenuGeometry(progress = 1f, closing = false)

        assertTrue(peak.rect.top > peakWithoutRebound.rect.top)
        assertRectEquals(target, endpoint.rect)
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
    fun openingFinishesAtTargetPanel() {
        assertRectEquals(target, geometry(progress = 1f).rect)
    }

    @Test
    fun closingReturnsToSameAnchor() {
        assertRectEquals(target, geometry(progress = 1f, closing = true).rect)
        assertRectEquals(source, geometry(progress = 0f, closing = true).rect)
    }

    @Test
    fun addTargetUsesSpecifiedSizeAndClampsToRoot() {
        val root = IntSize(1080, 1920)
        val result = homeAddMenuTargetRect(
            source = Rect(1010f, 80f, 1052f, 122f),
            rootSize = root,
            density = 2f,
            actionCount = 3
        )

        assertEquals(416f, result.width, Tolerance)
        assertEquals(440f, result.height, Tolerance)
        assertInBounds(result, root, margin = 24f)
    }

    @Test
    fun addTargetAlignsExactlyWithSourceRightEdgeWhenThereIsRoom() {
        val source = Rect(480f, 80f, 522f, 122f)
        val result = homeAddMenuTargetRect(
            source = source,
            rootSize = IntSize(1080, 1920),
            density = 2f,
            actionCount = 3
        )

        assertEquals(source.right, result.right, Tolerance)
        assertEquals(source.bottom + 8f, result.top, Tolerance)
        assertEquals(416f, result.width, Tolerance)
    }

    @Test
    fun addMenuDividerAndRowGapsRemainInsideContinuousActionHitRegions() {
        assertEquals(-1, homeAddMenuHitIndex(60f, 60f, 73f, 44f, 3))
        assertEquals(0, homeAddMenuHitIndex(61f, 60f, 73f, 44f, 3))
        assertEquals(0, homeAddMenuHitIndex(72.9f, 60f, 73f, 44f, 3))
        assertEquals(0, homeAddMenuHitIndex(116.9f, 60f, 73f, 44f, 3))
        assertEquals(1, homeAddMenuHitIndex(117f, 60f, 73f, 44f, 3))
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
        sourcePressedScale: Float = 1f
    ): HomeAnchoredMorphGeometry = homeAddMenuMorphGeometry(
        source = source,
        target = target,
        rawProgress = progress,
        closing = closing,
        pinchDiameterPx = 10f,
        minimumDropPx = 36f,
        maximumDropPx = 72f,
        maximumArcPx = 48f,
        targetCornerRadiusPx = 32f,
        reboundOvershootPx = 12f,
        sourcePressedScale = sourcePressedScale
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
