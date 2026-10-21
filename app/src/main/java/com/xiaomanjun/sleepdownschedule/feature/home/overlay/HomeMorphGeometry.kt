package com.xiaomanjun.sleepdownschedule.feature.home.overlay

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize
import com.kyant.backdrop.effects.blur
import com.xiaomanjun.sleepdownschedule.core.ui.chrome.HomeInitialTopInset
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.HomeAddMenuSelectionCornerDp
import com.xiaomanjun.sleepdownschedule.feature.home.HomeAdaptiveMetrics
import com.xiaomanjun.sleepdownschedule.feature.home.HomeDropletCloseDurationMillis
import com.xiaomanjun.sleepdownschedule.feature.home.HomeDropletOpenDurationMillis
import com.xiaomanjun.sleepdownschedule.feature.home.contentRectPx
import com.xiaomanjun.sleepdownschedule.glass.GlassTransitionGeometry
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin
import kotlinx.coroutines.flow.first

internal const val HomeAnchoredMorphOpenDurationMillis = 430
internal const val HomePersonalizeMorphOpenDurationMillis = 340
internal const val HomeAnchoredMorphCloseDurationMillis = 360
internal const val HomePersonalizeMorphCloseDurationMillis = 220
internal const val HomeAnchoredOpenSettleStartFraction = 0.82f
internal const val HomeAnchoredMorphPinchFraction = 0.28f
internal const val HomeAnchoredMorphClosePinchFraction = 0.08f
internal const val HomeAnchoredMorphBackgroundDurationMillis = 460
internal const val HomeAnchoredMorphBackgroundDelayMillis = 20
internal const val HomeAnchoredMorphBackgroundScale = 1.08f

/** Motion parameters for the three-dot first-level menu's shared-object trajectory. */
internal object ThreeDotMenuMotion {
    const val OpenDurationMillis = HomeDropletOpenDurationMillis
    const val CloseDurationMillis = HomeDropletCloseDurationMillis

    const val OpenPinchFraction = 0.28f
    const val OpenPinchDiameterDp = 18f
    const val OpenMinimumDropDp = 36f
    const val OpenMaximumDropDp = 72f
    const val OpenReboundPeakFraction = 0.40f
    const val OpenReboundAmplitudeDp = 12f

    const val CloseSinkEndFraction = 0.13f
    const val CloseReturnStartFraction = 0.08f
    const val CloseReturnBlendEndFraction = 0.15f
    const val CloseControlXFraction = 0.40f
    const val CloseSinkOffsetDp = 6f
    const val CloseControlDropDp = 14f
    const val CloseSinkCompression = 0.012f

    val CloseReturnEasing = CubicBezierEasing(0.45f, 0.0f, 0.20f, 1.0f)
}

internal const val HomeAddMenuMorphOpenDurationMillis = ThreeDotMenuMotion.OpenDurationMillis
internal const val HomeAddMenuMorphCloseDurationMillis = ThreeDotMenuMotion.CloseDurationMillis
internal const val HomeAddMenuTargetCornerDp = 30f
internal const val HomeAddMenuConcentricInsetDp =
    HomeAddMenuTargetCornerDp - HomeAddMenuSelectionCornerDp
private const val HomeAddMenuActionContentWidthDp = 172f
internal const val HomeAddMenuActionColumnInsetDp = 2f
internal const val HomeAddMenuSelectionVerticalInsetDp = 1f
internal const val HomeAddMenuContentTopPaddingDp = 6f
internal const val HomeAddMenuModeHeightDp = 52f
internal const val HomeAddMenuSectionGapDp = 4f
internal const val HomeAddMenuDividerHeightDp = 1f
internal const val HomeAddMenuActionItemHeightDp = 40f
internal const val HomeAddMenuActionGapDp = 0f
internal const val CourseShortcutContentPaddingDp = 5f

// The visible trajectory is cubic-bezier for its complete duration. LinearEasing below is only
// used as the phase clock, so absolute handoff/rebound timings stay stable.
// Neither curve ends with y2=1: both retain visible terminal velocity instead of crawling through
// a long zero-speed tail. The source/content handoff hides the final endpoint stop.
private val HomeAnchoredOpenMotionEasing = CubicBezierEasing(0.10f, 0.60f, 0.88f, 0.88f)
// Closing is deliberately independent: it responds immediately, settles cleanly, and runs over
// a shorter duration instead of replaying the opening rebound backwards.
private val HomeAnchoredCloseMotionEasing = CubicBezierEasing(0.22f, 0.75f, 0.60f, 0.90f)
internal val HomeAnchoredBackgroundEasing = CubicBezierEasing(0.30f, 0.0f, 0.20f, 1.0f)

// The recovered 02:38 opening stack is shared by every Home popup trajectory. The generic
// anchored/detail transitions below still retain their older easing where they are used elsewhere.
private val HomeAnchoredFallEasing = CubicBezierEasing(0.22f, 0.0f, 0.42f, 1.0f)
private val HomeAnchoredOpenPositionEasing = CubicBezierEasing(0.16f, 0.78f, 0.18f, 1.0f)
private val HomeAnchoredOpenSizeEasing = CubicBezierEasing(0.20f, 0.48f, 0.24f, 1.0f)
private val HomeAnchoredCloseEasing = CubicBezierEasing(0.28f, 0.06f, 0.20f, 1.0f)

internal fun homeAnchoredOpenMotionProgress(progress: Float): Float {
    return HomeAnchoredOpenMotionEasing.transform(progress.coerceIn(0f, 1f))
}

internal fun homeAnchoredCloseMotionProgress(progress: Float): Float {
    val value = progress.coerceIn(0f, 1f)
    return 1f - HomeAnchoredCloseMotionEasing.transform(1f - value)
}

/**
 * Selects the visible trajectory used by [homeAnchoredMorphGeometry].
 *
 * [Directional] is the generic anchored transition style with a linear phase clock and
 * direction-specific cubics. [Legacy] restores the previous version's easing stack (a close-eased
 * phase clock plus fall/position/size cubics and closing handoff windows) for the independently
 * owned personalization and Home menu destination domains.
 */
internal enum class HomeMorphEasingStyle {
    Directional,
    Legacy
}

internal data class HomeAnchoredMorphGeometry(
    val rect: Rect,
    val cornerRadiusPx: Float,
    val sourceScale: Float,
    val sourceAlpha: Float,
    val surfaceAlpha: Float,
    val contentAlpha: Float,
    val pathProgress: Float,
    val expansionProgress: Float
)

/**
 * Keeps the tablet personalization backdrop tied to both animation state and slider preview.
 * The preview progress is already animated by the caller, so this value can drive blur radius
 * and opacity without snapping when a drag starts or finishes.
 */
internal fun personalizeBackdropBlurLayerProgress(
    expansionProgress: Float,
    previewProgress: Float
): Float = (
    homeMorphSmoothStep(0.12f, 0.46f, expansionProgress.coerceIn(0f, 1f)) *
        (1f - previewProgress.coerceIn(0f, 1f))
    ).coerceIn(0f, 1f)

internal fun homeAnchoredMorphGeometry(
    source: Rect,
    target: Rect,
    rawProgress: Float,
    closing: Boolean,
    directClosing: Boolean = false,
    directSourceCornerRadiusPx: Float? = null,
    sourceCornerRadiusPx: Float? = null,
    pinchDiameterPx: Float,
    minimumDropPx: Float,
    maximumDropPx: Float,
    maximumArcPx: Float,
    targetCornerRadiusPx: Float,
    sourcePressedScale: Float = 1f,
    pinchFractionOverride: Float? = null,
    cornerMorphDuringPinchFraction: Float = 0f,
    handoffStartFraction: Float = 0.05f,
    handoffEndFraction: Float = 0.34f,
    contentStartFraction: Float = 0.18f,
    contentEndFraction: Float = 0.55f,
    motionStyle: HomeMorphEasingStyle = HomeMorphEasingStyle.Directional
): HomeAnchoredMorphGeometry {
    val raw = rawProgress.coerceIn(0f, 1f)
    val legacy = motionStyle == HomeMorphEasingStyle.Legacy
    // Phase progress stays linear in both directions. This keeps the droplet section, content
    // handoff thresholds and rebound peak at their existing absolute times; only the visual
    // trajectory inside each phase receives direction-specific easing.
    // The legacy path instead reverses the previous close easing onto the phase clock and keeps
    // the old open/close pinch split, matching the previous version frame for frame.
    val pathProgress = if (legacy && closing) {
        1f - HomeAnchoredCloseEasing.transform(1f - raw)
    } else {
        raw
    }.coerceIn(0f, 1f)
    val pinchFraction = pinchFractionOverride?.coerceIn(0.08f, 0.72f)
        ?: if (legacy && closing) {
            HomeAnchoredMorphClosePinchFraction
        } else {
            HomeAnchoredMorphPinchFraction
        }
    fun motionProgress(progress: Float): Float = if (closing) {
        homeAnchoredCloseMotionProgress(progress)
    } else {
        homeAnchoredOpenMotionProgress(progress)
    }
    fun positionProgress(progress: Float): Float =
        if (legacy) HomeAnchoredOpenPositionEasing.transform(progress) else motionProgress(progress)
    fun sizeProgress(progress: Float): Float =
        if (legacy) HomeAnchoredOpenSizeEasing.transform(progress) else motionProgress(progress)
    fun fallProgress(progress: Float): Float =
        if (legacy) HomeAnchoredFallEasing.transform(progress) else motionProgress(progress)
    val sourceCenter = source.center
    val pressedScale = sourcePressedScale.coerceIn(1f, 1.16f)
    val initialSourceWidth = source.width * pressedScale
    val initialSourceHeight = source.height * pressedScale
    val targetCenter = target.center
    val dropDistance = (abs(targetCenter.y - sourceCenter.y) * 0.18f)
        .coerceIn(minimumDropPx, maximumDropPx)
    val pinchCenterX = sourceCenter.x
    val pinchCenterY = sourceCenter.y + dropDistance

    val expansionProgress: Float
    val centerX: Float
    val centerY: Float
    val width: Float
    val height: Float
    val cornerRadius: Float
    val sourceScale: Float

    val sourceRadius = (sourceCornerRadiusPx
        ?: directSourceCornerRadiusPx
        ?: (min(source.width, source.height) / 2f)) * pressedScale
    val pinchEndCornerRadius = lerpHomeMorph(
        sourceRadius,
        targetCornerRadiusPx,
        cornerMorphDuringPinchFraction
    )

    if (directClosing) {
        val position = positionProgress(pathProgress)
        val size = sizeProgress(pathProgress)
        centerX = lerpHomeMorph(sourceCenter.x, targetCenter.x, position)
        centerY = lerpHomeMorph(sourceCenter.y, targetCenter.y, position)
        width = lerpHomeMorph(initialSourceWidth, target.width, size)
        height = lerpHomeMorph(initialSourceHeight, target.height, size)
        cornerRadius = lerpHomeMorph(
            sourceRadius,
            targetCornerRadiusPx,
            size
        )
        sourceScale = (min(width, height) / min(source.width, source.height).coerceAtLeast(1f))
            .coerceIn(0f, pressedScale)
        expansionProgress = pathProgress
    } else if (pathProgress <= pinchFraction) {
        val local = (pathProgress / pinchFraction).coerceIn(0f, 1f)
        val fall = fallProgress(local)
        val diameterProgress = sizeProgress(local)
        width = lerpHomeMorph(initialSourceWidth, pinchDiameterPx, diameterProgress)
        height = lerpHomeMorph(initialSourceHeight, pinchDiameterPx, diameterProgress)
        centerX = sourceCenter.x
        centerY = lerpHomeMorph(sourceCenter.y, pinchCenterY, fall)
        cornerRadius = if (cornerMorphDuringPinchFraction > 0f) {
            // The drawable still clamps naturally while the droplet is tiny, but its requested
            // corner is already converging on the panel. As the shell expands there is therefore
            // no second, visibly separate circle-to-panel corner phase.
            lerpHomeMorph(sourceRadius, pinchEndCornerRadius, diameterProgress)
        } else {
            min(
                lerpHomeMorph(sourceRadius, pinchDiameterPx / 2f, diameterProgress),
                min(width, height) / 2f
            )
        }
        sourceScale = (min(width, height) / min(source.width, source.height).coerceAtLeast(1f))
            .coerceIn(0f, pressedScale)
        expansionProgress = 0f
    } else {
        val local = ((pathProgress - pinchFraction) /
            (1f - pinchFraction)).coerceIn(0f, 1f)
        val position = positionProgress(local)
        val size = sizeProgress(local)
        val deltaY = targetCenter.y - pinchCenterY
        val arc = min(maximumArcPx, abs(deltaY) * 0.22f)
        val controlX = (pinchCenterX + targetCenter.x) / 2f
        val controlY = (pinchCenterY + targetCenter.y) / 2f + sign(deltaY) * arc
        val inverse = 1f - position
        centerX = inverse * inverse * pinchCenterX +
            2f * inverse * position * controlX +
            position * position * targetCenter.x
        centerY = inverse * inverse * pinchCenterY +
            2f * inverse * position * controlY +
            position * position * targetCenter.y
        val pulseWindow = if (legacy) {
            ((local - 0.82f) / 0.18f).coerceIn(0f, 1f)
        } else {
            ((local - 0.64f) /
                (HomeAnchoredOpenSettleStartFraction - 0.64f)).coerceIn(0f, 1f)
        }
        val pulseScale = if (legacy) {
            1f + sin(PI.toFloat() * pulseWindow) * 0.008f
        } else {
            1f
        }
        width = lerpHomeMorph(pinchDiameterPx, target.width, size) * pulseScale
        height = lerpHomeMorph(pinchDiameterPx, target.height, size) * pulseScale
        cornerRadius = lerpHomeMorph(
            if (cornerMorphDuringPinchFraction > 0f) pinchEndCornerRadius else pinchDiameterPx / 2f,
            targetCornerRadiusPx,
            size
        )
        sourceScale = (pinchDiameterPx / min(source.width, source.height).coerceAtLeast(1f))
            .coerceIn(0f, 1f)
        expansionProgress = local
    }

    val handoff = if (legacy && closing) {
        homeMorphSmoothStep(0.015f, 0.12f, expansionProgress)
    } else {
        homeMorphSmoothStep(handoffStartFraction, handoffEndFraction, expansionProgress)
    }
    val contentAlpha = if (legacy && closing) {
        homeMorphSmoothStep(0.04f, 0.20f, expansionProgress)
    } else {
        homeMorphSmoothStep(contentStartFraction, contentEndFraction, expansionProgress)
    }
    return HomeAnchoredMorphGeometry(
        rect = Rect(
            left = centerX - width / 2f,
            top = centerY - height / 2f,
            right = centerX + width / 2f,
            bottom = centerY + height / 2f
        ),
        cornerRadiusPx = cornerRadius,
        sourceScale = sourceScale,
        sourceAlpha = 1f - handoff,
        surfaceAlpha = handoff,
        contentAlpha = contentAlpha,
        pathProgress = pathProgress,
        expansionProgress = expansionProgress
    )
}

internal fun homeAddMenuTargetRect(
    source: Rect,
    rootSize: IntSize,
    density: Float,
    actionCount: Int,
    adaptiveMetrics: HomeAdaptiveMetrics? = null,
    showModeSwitch: Boolean = true,
    aboveSource: Boolean = false,
    actionItemHeightDp: Float = HomeAddMenuActionItemHeightDp
): Rect {
    // R_outer - R_inner = 30dp - 19dp = 11dp. Derive the shell from the compact 172dp
    // action-content width so the selected capsule has identical left, right and bottom insets.
    val width = (HomeAddMenuActionContentWidthDp + HomeAddMenuConcentricInsetDp * 2f) * density
    val height = (
        HomeAddMenuContentTopPaddingDp +
            (if (showModeSwitch) HomeAddMenuModeHeightDp +
                HomeAddMenuSectionGapDp * 2f + HomeAddMenuDividerHeightDp else 0f) +
            actionItemHeightDp * actionCount +
            HomeAddMenuActionGapDp * (actionCount - 1).coerceAtLeast(0) +
            (HomeAddMenuConcentricInsetDp - HomeAddMenuSelectionVerticalInsetDp)
        ) * density
    val marginPx = 12f * density
    val sourceGapPx = 4f * density
    val safeBounds = adaptiveMetrics?.contentRectPx(rootSize, density)
        ?: Rect(0f, 0f, rootSize.width.toFloat(), rootSize.height.toFloat())
    val proposedTop = if (aboveSource) source.top - 12f * density - height else source.bottom + sourceGapPx
    val proposed = Rect(
        left = source.right - width,
        top = proposedTop,
        right = source.right,
        bottom = proposedTop + height
    )
    val clamped = clampHomeMorphTarget(proposed, rootSize, marginPx, safeBounds)
    // Preserve the capsule's exact trailing edge whenever the panel fits. The generic clamp keeps
    // the vertical/safe-area guarantees, while this final horizontal placement avoids reintroducing
    // a fixed 12dp inward shift when the capsule itself intentionally sits 8dp or 11dp from screen.
    val minimumRight = safeBounds.left + marginPx + clamped.width
    val alignedRight = source.right.coerceIn(minimumRight, safeBounds.right)
    return Rect(
        left = alignedRight - clamped.width,
        top = clamped.top,
        right = alignedRight,
        bottom = clamped.bottom
    )
}

internal fun homeAddMenuHitIndex(
    y: Float,
    modeHeight: Float,
    actionTop: Float,
    actionStep: Float,
    actionCount: Int
): Int {
    if (y <= modeHeight || actionCount <= 0 || actionStep <= 0f) return -1
    val actionY = (y - actionTop).coerceAtLeast(0f)
    return (actionY / actionStep).toInt().takeIf { it in 0 until actionCount } ?: -1
}

internal fun homeAddMenuShellClipEnabled(phase: HomeAnchoredOverlayPhase): Boolean =
    phase != HomeAnchoredOverlayPhase.Idle && phase != HomeAnchoredOverlayPhase.Open

internal fun threeDotMenuOpeningPositionProgress(expansionProgress: Float): Float =
    HomeAnchoredOpenPositionEasing.transform(expansionProgress.coerceIn(0f, 1f))

internal fun threeDotMenuOpeningSizeProgress(expansionProgress: Float): Float =
    HomeAnchoredOpenSizeEasing.transform(expansionProgress.coerceIn(0f, 1f))

private fun threeDotQuadraticBezier(
    start: Offset,
    control: Offset,
    end: Offset,
    progress: Float
): Offset {
    val value = progress.coerceIn(0f, 1f)
    val inverse = 1f - value
    return Offset(
        x = inverse * inverse * start.x +
            2f * inverse * value * control.x +
            value * value * end.x,
        y = inverse * inverse * start.y +
            2f * inverse * value * control.y +
            value * value * end.y
    )
}

/** Personalization uses the exact same liquid-object trajectory as the three-dot menu. */
internal fun homePersonalizationTrajectoryGeometry(
    source: Rect,
    target: Rect,
    rawProgress: Float,
    pinchDiameterPx: Float,
    minimumDropPx: Float,
    maximumDropPx: Float,
    maximumArcPx: Float,
    targetCornerRadiusPx: Float,
    sourcePressedScale: Float = 1f,
    closing: Boolean = false,
    verticalReboundAmplitudePx: Float = 0f,
    closingSinkOffsetPx: Float = 0f,
    closingControlDropPx: Float = 0f
): HomeAnchoredMorphGeometry = homeThreeDotMenuTrajectoryGeometry(
    source = source,
    target = target,
    rawProgress = rawProgress,
    closing = closing,
    sourceCornerRadiusPx = min(source.width, source.height) / 2f,
    targetCornerRadiusPx = targetCornerRadiusPx,
    sourcePressedScale = sourcePressedScale,
    openingPinchDiameterPx = pinchDiameterPx,
    openingMinimumDropPx = minimumDropPx,
    openingMaximumDropPx = maximumDropPx,
    openingMaximumArcPx = maximumArcPx,
    verticalReboundAmplitudePx = verticalReboundAmplitudePx,
    closingSinkOffsetPx = closingSinkOffsetPx,
    closingControlDropPx = closingControlDropPx
)

/**
 * Shared liquid-object trajectory used by the Home popups.
 *
 * Opening starts as a pressed source, drops into a small droplet, then grows while following a
 * quadratic Bezier. Closing is intentionally not the reverse: it first sinks a little, then
 * follows a separate Bezier back into the source. The first-level menu keeps its recovered
 * trailing-edge growth; larger panels use center growth so their complete shell follows the path.
 */
private fun homeLiquidSharedObjectTrajectoryGeometry(
    source: Rect,
    target: Rect,
    rawProgress: Float,
    closing: Boolean,
    sourceCornerRadiusPx: Float? = null,
    targetCornerRadiusPx: Float,
    sourcePressedScale: Float = 1f,
    openingPinchDiameterPx: Float = min(source.width, source.height),
    openingPinchFraction: Float,
    openingMinimumDropPx: Float = 0f,
    openingMaximumDropPx: Float = Float.MAX_VALUE,
    openingMaximumArcPx: Float = Float.MAX_VALUE,
    verticalReboundAmplitudePx: Float = 0f,
    closingSinkOffsetPx: Float = 0f,
    closingControlDropPx: Float = 0f,
    openingRightEdgeAnchored: Boolean
): HomeAnchoredMorphGeometry {
    val raw = rawProgress.coerceIn(0f, 1f)
    val motion = ThreeDotMenuMotion
    val pressedScale = sourcePressedScale.coerceIn(1f, 1.16f)
    val initialWidth = source.width * pressedScale
    val initialHeight = source.height * pressedScale
    val sourceRadius =
        (sourceCornerRadiusPx ?: (min(source.width, source.height) / 2f)) * pressedScale
    val center: Offset
    val width: Float
    val height: Float
    val cornerRadius: Float
    val openAmount: Float
    var sourceScale = 1f
    var anchoredRight: Float? = null

    if (!closing) {
        val pinchFraction = openingPinchFraction.coerceIn(0f, 0.90f)
        val hasOpeningPinch = pinchFraction > 0.0001f
        val pinchDiameter = openingPinchDiameterPx.coerceAtLeast(1f)
        val dropDistance = (abs(target.center.y - source.center.y) * 0.18f).coerceIn(
            openingMinimumDropPx.coerceAtLeast(0f),
            openingMaximumDropPx.coerceAtLeast(openingMinimumDropPx)
        )
        val pinchCenter = Offset(
            x = source.center.x,
            y = source.center.y + dropDistance
        )

        if (hasOpeningPinch && raw <= pinchFraction) {
            val pinchProgress = (raw / pinchFraction).coerceIn(0f, 1f)
            val fallProgress = HomeAnchoredFallEasing.transform(pinchProgress)
            val diameterProgress = threeDotMenuOpeningSizeProgress(pinchProgress)
            width = lerpHomeMorph(initialWidth, pinchDiameter, diameterProgress)
            height = lerpHomeMorph(initialHeight, pinchDiameter, diameterProgress)
            center = Offset(
                x = source.center.x,
                y = lerpHomeMorph(source.center.y, pinchCenter.y, fallProgress)
            )
            cornerRadius = min(
                lerpHomeMorph(sourceRadius, pinchDiameter / 2f, diameterProgress),
                min(width, height) / 2f
            )
            sourceScale = min(width, height) /
                min(initialWidth, initialHeight).coerceAtLeast(1f)
            openAmount = 0f
        } else {
            val expansion = if (hasOpeningPinch) {
                ((raw - pinchFraction) / (1f - pinchFraction)).coerceIn(0f, 1f)
            } else {
                raw
            }
            val positionProgress = threeDotMenuOpeningPositionProgress(expansion)
            val sizeProgress = threeDotMenuOpeningSizeProgress(expansion)
            val expansionStartCenter = if (hasOpeningPinch) pinchCenter else source.center
            val expansionStartWidth = if (hasOpeningPinch) pinchDiameter else initialWidth
            val expansionStartHeight = if (hasOpeningPinch) pinchDiameter else initialHeight
            val expansionStartCorner = if (hasOpeningPinch) pinchDiameter / 2f else sourceRadius
            val deltaY = target.center.y - expansionStartCenter.y
            val arc = min(openingMaximumArcPx.coerceAtLeast(0f), abs(deltaY) * 0.22f)
            val control = Offset(
                x = (expansionStartCenter.x + target.center.x) / 2f,
                y = (expansionStartCenter.y + target.center.y) / 2f + sign(deltaY) * arc
            )
            val trajectoryCenter = threeDotQuadraticBezier(
                start = expansionStartCenter,
                control = control,
                end = target.center,
                progress = positionProgress
            )
            val reboundPeak = motion.OpenReboundPeakFraction
            val reboundOffset = if (expansion <= reboundPeak) {
                verticalReboundAmplitudePx * homeMorphSmoothStep(
                    reboundPeak * 0.45f,
                    reboundPeak,
                    expansion
                )
            } else {
                verticalReboundAmplitudePx * (
                    1f - homeMorphSmoothStep(reboundPeak, 1f, expansion)
                    )
            }
            center = Offset(
                x = trajectoryCenter.x,
                y = trajectoryCenter.y + reboundOffset
            )
            val pulseWindow = ((expansion - 0.82f) / 0.18f).coerceIn(0f, 1f)
            val pulseScale = 1f + sin(PI.toFloat() * pulseWindow) * 0.008f
            width = lerpHomeMorph(expansionStartWidth, target.width, sizeProgress) * pulseScale
            height = lerpHomeMorph(expansionStartHeight, target.height, sizeProgress) * pulseScale
            cornerRadius = lerpHomeMorph(expansionStartCorner, targetCornerRadiusPx, sizeProgress)
            if (openingRightEdgeAnchored) {
                val expansionStartRight = expansionStartCenter.x + expansionStartWidth / 2f
                anchoredRight = lerpHomeMorph(expansionStartRight, target.right, sizeProgress)
            }
            sourceScale = if (hasOpeningPinch) {
                pinchDiameter / min(initialWidth, initialHeight).coerceAtLeast(1f)
            } else {
                1f
            }
            openAmount = expansion
        }
    } else {
        val closeProgress = 1f - raw
        val sinkProgress = homeMorphSmoothStep(0f, motion.CloseSinkEndFraction, closeProgress)
        val returnClock = ((closeProgress - motion.CloseReturnStartFraction) /
            (1f - motion.CloseReturnStartFraction)).coerceIn(0f, 1f)
        val returnProgress = motion.CloseReturnEasing.transform(returnClock)
        val returnBlend = homeMorphSmoothStep(
            motion.CloseReturnStartFraction,
            motion.CloseReturnBlendEndFraction,
            closeProgress
        )
        val sunkCenter = Offset(
            x = target.center.x,
            y = target.center.y + closingSinkOffsetPx * sinkProgress
        )
        val returnStart = Offset(
            x = target.center.x,
            y = target.center.y + closingSinkOffsetPx
        )
        val returnControl = Offset(
            x = lerpHomeMorph(
                returnStart.x,
                source.center.x,
                motion.CloseControlXFraction
            ),
            y = returnStart.y + closingControlDropPx
        )
        val returnCenter = threeDotQuadraticBezier(
            start = returnStart,
            control = returnControl,
            end = source.center,
            progress = returnProgress
        )
        center = Offset(
            x = lerpHomeMorph(sunkCenter.x, returnCenter.x, returnBlend),
            y = lerpHomeMorph(sunkCenter.y, returnCenter.y, returnBlend)
        )

        val shrinkProgress = 1f - (1f - returnProgress).pow(1.12f)
        val compressionProgress = when {
            closeProgress <= 0.10f -> homeMorphSmoothStep(0f, 0.10f, closeProgress)
            closeProgress <= 0.22f -> 1f - homeMorphSmoothStep(0.10f, 0.22f, closeProgress)
            else -> 0f
        }
        width = lerpHomeMorph(target.width, initialWidth, shrinkProgress)
        height = lerpHomeMorph(target.height, initialHeight, shrinkProgress) *
            (1f - motion.CloseSinkCompression * compressionProgress)
        cornerRadius = lerpHomeMorph(targetCornerRadiusPx, sourceRadius, shrinkProgress)
        openAmount = 1f - shrinkProgress
    }

    val handoff = if (closing) {
        homeMorphSmoothStep(0f, 0.18f, openAmount)
    } else {
        homeMorphSmoothStep(0.05f, 0.34f, openAmount)
    }
    val left = anchoredRight?.minus(width) ?: (center.x - width / 2f)
    val right = anchoredRight ?: (center.x + width / 2f)
    return HomeAnchoredMorphGeometry(
        rect = Rect(
            left = left,
            top = center.y - height / 2f,
            right = right,
            bottom = center.y + height / 2f
        ),
        cornerRadiusPx = cornerRadius,
        sourceScale = sourceScale,
        sourceAlpha = 1f - handoff,
        surfaceAlpha = handoff,
        contentAlpha = if (closing) {
            homeMorphSmoothStep(0.16f, 0.46f, openAmount)
        } else {
            homeMorphSmoothStep(0.18f, 0.55f, openAmount)
        },
        pathProgress = raw,
        expansionProgress = openAmount
    )
}

/** The recovered 02:38 menu motion grows from its trailing edge. */
internal fun homeThreeDotMenuTrajectoryGeometry(
    source: Rect,
    target: Rect,
    rawProgress: Float,
    closing: Boolean,
    sourceCornerRadiusPx: Float? = null,
    targetCornerRadiusPx: Float,
    sourcePressedScale: Float = 1f,
    openingPinchDiameterPx: Float = min(source.width, source.height),
    openingMinimumDropPx: Float = 0f,
    openingMaximumDropPx: Float = Float.MAX_VALUE,
    openingMaximumArcPx: Float = Float.MAX_VALUE,
    verticalReboundAmplitudePx: Float = 0f,
    closingSinkOffsetPx: Float = 0f,
    closingControlDropPx: Float = 0f
): HomeAnchoredMorphGeometry = homeLiquidSharedObjectTrajectoryGeometry(
    source = source,
    target = target,
    rawProgress = rawProgress,
    closing = closing,
    sourceCornerRadiusPx = sourceCornerRadiusPx,
    targetCornerRadiusPx = targetCornerRadiusPx,
    sourcePressedScale = sourcePressedScale,
    openingPinchDiameterPx = openingPinchDiameterPx,
    openingPinchFraction = ThreeDotMenuMotion.OpenPinchFraction,
    openingMinimumDropPx = openingMinimumDropPx,
    openingMaximumDropPx = openingMaximumDropPx,
    openingMaximumArcPx = openingMaximumArcPx,
    verticalReboundAmplitudePx = verticalReboundAmplitudePx,
    closingSinkOffsetPx = closingSinkOffsetPx,
    closingControlDropPx = closingControlDropPx,
    openingRightEdgeAnchored = true
)

/**
 * Center-growing variant for large Home panels. Its rect is always derived from the Bezier center
 * plus width/height, avoiding the first-level menu's intentional trailing-edge expansion.
 */
internal fun homeCenteredSharedObjectTrajectoryGeometry(
    source: Rect,
    target: Rect,
    rawProgress: Float,
    closing: Boolean,
    sourceCornerRadiusPx: Float? = null,
    targetCornerRadiusPx: Float,
    sourcePressedScale: Float = 1f,
    openingPinchDiameterPx: Float = min(source.width, source.height),
    openingPinchFraction: Float = ThreeDotMenuMotion.OpenPinchFraction,
    openingMinimumDropPx: Float = 0f,
    openingMaximumDropPx: Float = Float.MAX_VALUE,
    openingMaximumArcPx: Float = Float.MAX_VALUE,
    verticalReboundAmplitudePx: Float = 0f,
    closingSinkOffsetPx: Float = 0f,
    closingControlDropPx: Float = 0f
): HomeAnchoredMorphGeometry = homeLiquidSharedObjectTrajectoryGeometry(
    source = source,
    target = target,
    rawProgress = rawProgress,
    closing = closing,
    sourceCornerRadiusPx = sourceCornerRadiusPx,
    targetCornerRadiusPx = targetCornerRadiusPx,
    sourcePressedScale = sourcePressedScale,
    openingPinchDiameterPx = openingPinchDiameterPx,
    openingPinchFraction = openingPinchFraction,
    openingMinimumDropPx = openingMinimumDropPx,
    openingMaximumDropPx = openingMaximumDropPx,
    openingMaximumArcPx = openingMaximumArcPx,
    verticalReboundAmplitudePx = verticalReboundAmplitudePx,
    closingSinkOffsetPx = closingSinkOffsetPx,
    closingControlDropPx = closingControlDropPx,
    openingRightEdgeAnchored = false
)

internal fun homeMorphWithVerticalRebound(
    geometry: HomeAnchoredMorphGeometry,
    closing: Boolean,
    overshootPx: Float,
    peakProgress: Float,
    legacyClosingRebound: Boolean = false
): HomeAnchoredMorphGeometry {
    // Closing has its own direct return and must not replay the opening bounce backwards.
    if (!legacyClosingRebound && (closing || overshootPx <= 0f)) return geometry
        val reboundOffset = run {
            val expansion = geometry.expansionProgress
            val peak = peakProgress.coerceIn(0.20f, 0.80f)
            if (legacyClosingRebound) {
                if (expansion <= peak) {
                    overshootPx * homeMorphSmoothStep(peak * 0.45f, peak, expansion)
                } else {
                    overshootPx * (1f - homeMorphSmoothStep(peak, 1f, expansion))
                }
            } else if (expansion <= peak) {
                // The bounce starts earlier, during the expansion, so the shell dips down while
                // it is still growing instead of wobbling after arrival.
                overshootPx * homeMorphSmoothStep(peak * 0.20f, peak, expansion)
            } else if (expansion >= HomeAnchoredOpenSettleStartFraction) {
                0f
            } else {
                // One-shot bounce: after bottoming out at the overshoot limit the shell returns
                // to the target in a single smooth pass and settles — no oscillation.
                overshootPx * (
                    1f - homeMorphSmoothStep(
                        peak,
                        HomeAnchoredOpenSettleStartFraction,
                        expansion
                    )
                    )
            }
        }
    return geometry.copy(
        rect = Rect(
            left = geometry.rect.left,
            top = geometry.rect.top + reboundOffset,
            right = geometry.rect.right,
            bottom = geometry.rect.bottom + reboundOffset
        )
    )
}

internal fun homePersonalizeTargetRect(
    rootSize: IntSize,
    density: Float,
    adaptiveMetrics: HomeAdaptiveMetrics? = null
): Rect {
    if (adaptiveMetrics != null) {
        val content = adaptiveMetrics.contentRectPx(rootSize, density)
        if (!adaptiveMetrics.isLargeScreen) {
            val maxHeightDp = content.height / density.coerceAtLeast(0.001f)
            val heightRatio = when {
                maxHeightDp < 520f -> 0.78f
                maxHeightDp < 700f -> 0.74f
                else -> 0.70f
            }
            val panelHeight = minOf(content.height * heightRatio, 680f * density)
                .coerceAtLeast(minOf(content.height, 280f * density))
            val panelWidth = (content.width * 0.95f).coerceAtLeast(1f)
            val proposed = Rect(
                left = content.left + (content.width - panelWidth) / 2f,
                top = content.top + (content.height - panelHeight) / 2f,
                right = content.left + (content.width + panelWidth) / 2f,
                bottom = content.top + (content.height + panelHeight) / 2f
            )
            return clampHomeMorphTarget(proposed, rootSize, 0f, content)
        }
        val landscape = adaptiveMetrics.isTabletLandscape
        val minWidth = minOf((if (landscape) 380f else 400f) * density, content.width)
        val panelWidth = minOf(
            content.width * if (landscape) 0.40f else 0.72f,
            (if (landscape) 448f else 480f) * density
        ).coerceAtLeast(minWidth)
        val panelHeight = minOf(
            content.height * if (landscape) 0.84f else 0.78f,
            (if (landscape) 760f else 720f) * density
        ).coerceAtLeast(minOf(content.height, 420f * density))
        val left = if (adaptiveMetrics.isTabletLandscape) {
            content.right - panelWidth
        } else {
            content.left + (content.width - panelWidth) / 2f
        }
        val top = content.top + (content.height - panelHeight) / 2f
        return Rect(
            left = left,
            top = top,
            right = left + panelWidth,
            bottom = top + panelHeight
        )
    }
    val rootWidth = rootSize.width.toFloat()
    val rootHeight = rootSize.height.toFloat()
    val maxHeightDp = rootHeight / density.coerceAtLeast(0.001f)
    val heightRatio = when {
        maxHeightDp < 520f -> 0.74f
        maxHeightDp < 700f -> 0.72f
        else -> 0.68f
    }
    val topClearance = (HomeInitialTopInset.value + 8f) * density
    val centeredSafeHeight = (rootHeight - topClearance * 2f).coerceAtLeast(280f * density)
    val panelHeight = minOf(rootHeight * heightRatio, centeredSafeHeight, 680f * density)
    val panelWidth = ((rootWidth - 32f * density).coerceAtLeast(1f) * 0.95f)
    val proposed = Rect(
        left = (rootWidth - panelWidth) / 2f,
        top = (rootHeight - panelHeight) / 2f,
        right = (rootWidth + panelWidth) / 2f,
        bottom = (rootHeight + panelHeight) / 2f
    )
    return clampHomeMorphTarget(proposed, rootSize, 12f * density)
}

internal fun homePersonalizationAuraGeometry(
    geometry: HomeAnchoredMorphGeometry,
    leftFeatherMaximumPx: Float,
    rightFeatherPx: Float,
    verticalFeatherPx: Float
): GlassTransitionGeometry {
    val leftFeather = leftFeatherMaximumPx.coerceAtLeast(0f) *
        geometry.expansionProgress.coerceIn(0f, 1f)
    val left = (geometry.rect.left - leftFeather).coerceAtLeast(0f)
    val top = (geometry.rect.top - verticalFeatherPx.coerceAtLeast(0f)).coerceAtLeast(0f)
    val width = geometry.rect.width + leftFeather + rightFeatherPx.coerceAtLeast(0f)
    val height = geometry.rect.height + verticalFeatherPx.coerceAtLeast(0f) * 2f
    return GlassTransitionGeometry(
        rectInRoot = Rect(left, top, left + width, top + height),
        cornerRadiusPx = 0f
    )
}

internal fun HomeAnchoredMorphGeometry.asGlassTransitionGeometry(): GlassTransitionGeometry =
    GlassTransitionGeometry(rectInRoot = rect, cornerRadiusPx = cornerRadiusPx)

internal fun clampHomeMorphTarget(
    target: Rect,
    rootSize: IntSize,
    marginPx: Float,
    bounds: Rect? = null
): Rect {
    if (rootSize.width <= 0 || rootSize.height <= 0) return Rect.Zero
    val safeBounds = bounds ?: Rect(0f, 0f, rootSize.width.toFloat(), rootSize.height.toFloat())
    val minLeft = safeBounds.left + marginPx
    val minTop = safeBounds.top + marginPx
    val maxRight = safeBounds.right - marginPx
    val maxBottom = safeBounds.bottom - marginPx
    val availableWidth = (maxRight - minLeft).coerceAtLeast(1f)
    val availableHeight = (maxBottom - minTop).coerceAtLeast(1f)
    val width = min(target.width, availableWidth)
    val height = min(target.height, availableHeight)
    val left = target.left.coerceIn(minLeft, maxRight - width)
    val top = target.top.coerceIn(minTop, maxBottom - height)
    return Rect(left, top, left + width, top + height)
}

internal fun lerpHomeMorph(start: Float, stop: Float, fraction: Float): Float {
    val safe = fraction.coerceIn(0f, 1f)
    return start + (stop - start) * safe
}

internal fun homeMorphSmoothStep(edge0: Float, edge1: Float, value: Float): Float {
    val t = ((value - edge0) / (edge1 - edge0).coerceAtLeast(0.0001f)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}
