package com.xiaomanjun.sleepdownschedule.feature.home

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.util.lerp
import com.xiaomanjun.sleepdownschedule.glass.GlassDropletGeometry
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin

internal val HomeDropletEasing = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)
// Travel already accelerates while the source contracts. Starting both tracks after the
// contraction created a second zero-velocity start that felt like a pause in the reference.
private val HomeDropletTravelEasing = CubicBezierEasing(0.30f, 0f, 0.18f, 1f)
private val HomeDropletOpenEasing = CubicBezierEasing(0.24f, 0f, 0.22f, 1f)
// Shrink the body before pulling its near edge to the anchor. Sharing the travel curve
// delayed the visible contraction and stretched a still-wide menu into an oblique neck.
private val HomeDropletCloseVolumeEasing = CubicBezierEasing(0.16f, 0f, 0.70f, 1f)
private val HomeDropletCloseTravelEasing = CubicBezierEasing(0.32f, 0f, 0.55f, 1f)
internal const val HomeDropletOpenDurationMillis = 260
internal const val HomeDropletCloseDurationMillis = 300
private const val HomeDropletContractionFraction = 0.12f
private const val HomeDropletExpansionStart = 0.08f
private const val HomeDropletExpansionEnd = 0.84f
internal fun dropletReveal(start: Float, end: Float, value: Float): Float {
    val t = ((value - start) / (end - start)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

internal data class HomeDropletFrame(
    val glass: GlassDropletGeometry,
    val contentAlpha: Float,
    val sourceAlpha: Float,
    val motionBlur: Float,
    val menuBlend: Float
)

/** One continuous volume: contraction overlaps travel, then height leads the lateral opening. */
internal fun homeDropletFrame(
    source: Rect, target: Rect, progress: Float, closing: Boolean,
    density: Float, targetCorner: Float = 28f * density
): HomeDropletFrame {
    val p = progress.coerceIn(0f, 1f)
    // Preserve the first 150ms of the accepted contraction. Stretch only its recovery clock,
    // with matching velocity at both ends, so the small drop has time to become the button.
    val closeTimeMs = (1f - p) * HomeDropletCloseDurationMillis
    val recoveryDelay = (HomeDropletCloseDurationMillis - 240f) *
        dropletReveal(150f, HomeDropletCloseDurationMillis.toFloat(), closeTimeMs)
    val closeElapsed = ((closeTimeMs - recoveryDelay) / 240f).coerceIn(0f, 1f)
    val direction = if (target.center.y >= source.center.y) 1f else -1f
    // Keep one contraction. The overlapping expansion has zero initial velocity, so the
    // source still visibly shrinks without a hold or an abrupt jump at either boundary.
    val contraction = if (closing) {
        dropletReveal(0.10f, 0.50f, closeElapsed) *
            (1f - dropletReveal(0.70f, 0.92f, closeElapsed))
    } else dropletReveal(0f, HomeDropletContractionFraction, p)
    val expansion = if (closing) {
        1f - HomeDropletCloseVolumeEasing.transform((closeElapsed / 0.74f).coerceIn(0f, 1f))
    } else HomeDropletOpenEasing.transform(
            ((p - HomeDropletExpansionStart) /
                (HomeDropletExpansionEnd - HomeDropletExpansionStart)).coerceIn(0f, 1f)
        )
    // One small overshoot, with zero velocity at both ends of its window. Finish the main
    // growth first so this crosses the final size instead of merely slowing below it.
    val reboundStart = if (closing) 0.78f else 0.64f
    val reboundClock = (((if (closing) closeElapsed else p) - reboundStart) /
        (1f - reboundStart)).coerceIn(0f, 1f)
    val reboundWindow = 4f * reboundClock * (1f - reboundClock)
    val rebound = reboundWindow * reboundWindow
    val reboundScale = 1f + (if (closing) 0.035f else 0.022f) * rebound
    val sourceWidth = source.width * (1f - 0.54f * contraction)
    val sourceHeight = source.height * (1f - 0.54f * contraction)
    val bodyWidth = lerp(sourceWidth, target.width, expansion.pow(1.25f))
    val bodyHeight = lerp(sourceHeight, target.height,
        expansion.pow(if (closing) 0.82f else 0.85f))
    val width = bodyWidth * reboundScale
    val height = bodyHeight * reboundScale
    val travel = if (closing) {
        1f - HomeDropletCloseTravelEasing.transform((closeElapsed / 0.84f).coerceIn(0f, 1f))
    } else HomeDropletTravelEasing.transform((p / 0.86f).coerceIn(0f, 1f))
    val centerY = if (closing) {
        // Preserve one connected body while its anchor-facing edge follows the shrink.
        // Recover the button size during the final approach, instead of making a second
        // contraction after the body has already reached the button.
        val sourceNearEdge = source.center.y - direction * sourceHeight / 2f
        val targetNearEdge = target.center.y - direction * target.height / 2f
        lerp(sourceNearEdge, targetNearEdge, travel) + direction * bodyHeight / 2f
    } else lerp(source.center.y, target.center.y, travel)
    val center = Offset(lerp(source.center.x, target.center.x, travel),
        centerY + direction * (if (closing) -0.8f else 1.5f) * density * rebound)
    val motion = sin(PI * expansion).toFloat().coerceAtLeast(0f)
    // The leading edge opens first, then the silhouette relaxes before the rows become clear.
    // This single-direction taper has no trailing lobe or separate landing oscillation.
    val openingStretch = 1f - dropletReveal(0.45f, 0.88f, expansion)
    val taper = direction * motion * if (closing) {
        0.06f * (1f - dropletReveal(0.50f, 0.72f, closeElapsed))
    } else 0.14f * openingStretch
    val corner = lerp(minOf(width, height) / 2f, targetCorner * reboundScale, expansion.pow(3f))
    val contentAlpha = if (closing) 1f - dropletReveal(0f, 0.24f, closeElapsed)
        else dropletReveal(0.24f, 0.76f, expansion)
    val sourceAlpha = if (closing) dropletReveal(0.74f, 0.96f, closeElapsed)
        else 1f - dropletReveal(0.01f, 0.12f, p)
    val menuBlend = dropletReveal(0.10f, 0.52f, expansion)
    val rect = Rect(center.x - width / 2f, center.y - height / 2f,
        center.x + width / 2f, center.y + height / 2f)
    return HomeDropletFrame(
        glass = GlassDropletGeometry(
            body = Rect.Zero, bodyRadius = 0f, drop = rect, dropRadius = corner,
            joinRadius = 0f, dropTaper = taper
        ), contentAlpha = contentAlpha, sourceAlpha = sourceAlpha,
        motionBlur = motion * 3f * density * if (closing) 1f else openingStretch,
        menuBlend = menuBlend
    )
}
