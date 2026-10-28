package com.xiaomanjun.sleepdownschedule.transition.legacy

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.ui.geometry.Rect
import kotlin.math.abs
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sin

internal const val HomeAnchoredMorphOpenDurationMillis = 430

internal const val HomeAnchoredMorphCloseDurationMillis = 360

internal const val HomeAnchoredOpenSettleStartFraction = 0.82f

internal const val HomeAnchoredMorphPinchFraction = 0.28f

internal const val HomeAnchoredMorphClosePinchFraction = 0.08f

internal const val HomeAnchoredMorphBackgroundDurationMillis = 460

internal const val HomeAnchoredMorphBackgroundDelayMillis = 20

internal const val HomeAnchoredMorphBackgroundScale = 1.08f

// The visible trajectory is cubic-bezier for its complete duration. LinearEasing below is only
// used as the phase clock, so absolute handoff/rebound timings stay stable.
// Neither curve ends with y2=1: both retain visible terminal velocity instead of crawling through
// a long zero-speed tail. The source/content handoff hides the final endpoint stop.
internal val HomeAnchoredOpenMotionEasing = CubicBezierEasing(0.10f, 0.60f, 0.88f, 0.88f)

// Closing is deliberately independent: it responds immediately, settles cleanly, and runs over
// a shorter duration instead of replaying the opening rebound backwards.
internal val HomeAnchoredCloseMotionEasing = CubicBezierEasing(0.22f, 0.75f, 0.60f, 0.90f)

internal val HomeAnchoredBackgroundEasing = CubicBezierEasing(0.30f, 0.0f, 0.20f, 1.0f)

// The recovered 02:38 opening stack is shared by every Home popup trajectory. The generic
// anchored/detail transitions below still retain their older easing where they are used elsewhere.
internal val HomeAnchoredFallEasing = CubicBezierEasing(0.22f, 0.0f, 0.42f, 1.0f)

internal val HomeAnchoredOpenPositionEasing = CubicBezierEasing(0.16f, 0.78f, 0.18f, 1.0f)

internal val HomeAnchoredOpenSizeEasing = CubicBezierEasing(0.20f, 0.48f, 0.24f, 1.0f)

internal val HomeAnchoredCloseEasing = CubicBezierEasing(0.28f, 0.06f, 0.20f, 1.0f)

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

internal fun lerpHomeMorph(start: Float, stop: Float, fraction: Float): Float {
    val safe = fraction.coerceIn(0f, 1f)
    return start + (stop - start) * safe
}

internal fun homeMorphSmoothStep(edge0: Float, edge1: Float, value: Float): Float {
    val t = ((value - edge0) / (edge1 - edge0).coerceAtLeast(0.0001f)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}
