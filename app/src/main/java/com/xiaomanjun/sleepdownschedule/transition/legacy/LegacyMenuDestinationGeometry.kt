package com.xiaomanjun.sleepdownschedule.transition.legacy

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.lerp

internal fun destinationSmoothStep(edge0: Float, edge1: Float, value: Float): Float {
    val x = ((value - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}

internal object HomeMenuDestinationLegacyMotion {
    const val OpenDurationMillis = 360
    const val CloseDurationMillis = 320

    // The menu dissolves first, then the complete scaled form becomes readable during travel.
    const val NonFullscreenContentRevealStart = 0.14f
    const val NonFullscreenContentRevealEnd = 0.36f
}

internal fun homeMenuDestinationContentAlpha(
    rawProgress: Float,
    isFullScreen: Boolean,
    closing: Boolean
): Float = when {
    closing -> destinationSmoothStep(0.32f, 0.72f, rawProgress)
    isFullScreen -> destinationSmoothStep(0.055f, 0.24f, rawProgress)
    else -> destinationSmoothStep(
        HomeMenuDestinationLegacyMotion.NonFullscreenContentRevealStart,
        HomeMenuDestinationLegacyMotion.NonFullscreenContentRevealEnd,
        rawProgress
    )
}

/** Fit the cached page inside its moving card without stretching its text or changing layout. */
internal fun homeMenuDestinationContentScale(rect: Rect, target: Rect): Float = minOf(
    rect.width / target.width.coerceAtLeast(1f),
    rect.height / target.height.coerceAtLeast(1f)
).coerceIn(0.001f, 1f)

/**
 * Keeps the real source outline intact until the source clone has handed off. Increasing the
 * radius during that overlap clips Kyant's highlight at the exact frame where the Activity and
 * the source window exchange ownership. The middle radius is introduced only after the source is
 * visually gone, then the full-screen endpoint releases the transient shell altogether.
 */
internal fun homeMenuDestinationRenderedCornerRadiusPx(
    geometry: HomeAnchoredMorphGeometry,
    rawProgress: Float,
    isFullScreen: Boolean,
    closing: Boolean,
    sourceCornerRadiusPx: Float,
    collapseCornerRadiusPx: Float,
    middleCornerRadiusPx: Float
): Float {
    if (!isFullScreen) {
        return geometry.cornerRadiusPx
    }
    if (closing) {
        // A full-screen destination has no visible corner at rest. As soon as it starts closing,
        // restore the transient shell radius used by the 1.1.5 transition, then converge on the
        // real three-dot button radius before the source window resumes ownership.
        val path = geometry.pathProgress
        val closeRadiusEnd = 0.35f
        return if (path >= closeRadiusEnd) {
            middleCornerRadiusPx
        } else {
            collapseCornerRadiusPx + (middleCornerRadiusPx - collapseCornerRadiusPx) *
                (path / closeRadiusEnd).coerceIn(0f, 1f)
        }
    }
    if (rawProgress >= 0.999f) {
        return geometry.cornerRadiusPx
    }
    val sourceHandoffEnd = 0.20f
    val middleRadiusEnd = 0.35f
    val path = geometry.pathProgress
    return when {
        path <= sourceHandoffEnd -> sourceCornerRadiusPx
        path >= middleRadiusEnd -> middleCornerRadiusPx
        else -> sourceCornerRadiusPx + (middleCornerRadiusPx - sourceCornerRadiusPx) *
            ((path - sourceHandoffEnd) / (middleRadiusEnd - sourceHandoffEnd))
    }
}

internal const val HomeMenuDestinationCloseDurationMillis =
    HomeMenuDestinationLegacyMotion.CloseDurationMillis

internal const val HomeMenuDestinationEduBackgroundScale = 1.08f

private val DestinationReturnEasing = CubicBezierEasing(0.24f, 0f, 0.30f, 1f)

/**
 * A continuous rounded-rectangle morph for Home menu destinations.
 *
 * Opening morphs directly from the first-level menu bounds. Closing uses the real three-dot button
 * as the return endpoint. Forms retain their rectangle throughout travel instead of borrowing
 * the first-level menu's small-droplet contraction and subsequent size recovery.
 */
internal fun homeMenuDestinationTrajectoryGeometry(
    sourceBoundsInRoot: Rect,
    collapseBoundsInRoot: Rect,
    target: Rect,
    rawProgress: Float,
    closing: Boolean,
    menuCornerRadiusPx: Float,
    buttonCornerRadiusPx: Float,
    pinchDiameterPx: Float,
    minimumDropPx: Float,
    maximumDropPx: Float,
    maximumArcPx: Float,
    targetCornerRadiusPx: Float
): HomeAnchoredMorphGeometry {
    if (closing) {
        val p = rawProgress.coerceIn(0f, 1f)
        val elapsed = 1f - p
        val amount = DestinationReturnEasing.transform(elapsed)
        val returnAlpha = destinationSmoothStep(0.24f, 0.60f, elapsed)
        return HomeAnchoredMorphGeometry(
            rect = lerp(target, collapseBoundsInRoot, amount),
            cornerRadiusPx = targetCornerRadiusPx +
                (buttonCornerRadiusPx - targetCornerRadiusPx) * amount,
            sourceScale = 1f,
            sourceAlpha = returnAlpha,
            surfaceAlpha = 1f - returnAlpha,
            contentAlpha = homeMenuDestinationContentAlpha(p, false, true),
            pathProgress = p,
            expansionProgress = 1f - amount
        )
    }
    val morphSource = if (closing) collapseBoundsInRoot else sourceBoundsInRoot
    return homeAnchoredMorphGeometry(
        source = morphSource,
        target = target,
        rawProgress = rawProgress,
        closing = closing,
        directClosing = true,
        directSourceCornerRadiusPx = if (closing) buttonCornerRadiusPx else menuCornerRadiusPx,
        pinchDiameterPx = pinchDiameterPx,
        minimumDropPx = minimumDropPx,
        maximumDropPx = maximumDropPx,
        maximumArcPx = maximumArcPx,
        targetCornerRadiusPx = targetCornerRadiusPx,
        motionStyle = HomeMorphEasingStyle.Legacy
    )
}
