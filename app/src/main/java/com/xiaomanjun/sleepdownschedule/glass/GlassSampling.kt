package com.xiaomanjun.sleepdownschedule.glass

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize
import kotlin.math.ceil

internal const val CourseGlassThreeQuarterSampleThreshold = 13
internal const val CourseGlassHalfSampleThreshold = 24

/**
 * Only the sampled backdrop/effect texture changes resolution. Card layout, text, interaction,
 * tint edges and Kyant decorations remain at the device resolution.
 */
internal fun adaptiveCourseGlassSampleScale(
    composedCardCount: Int,
    enabled: Boolean
): Float = when {
    !enabled -> 1f
    composedCardCount >= CourseGlassHalfSampleThreshold -> 0.5f
    composedCardCount >= CourseGlassThreeQuarterSampleThreshold -> 0.75f
    else -> 1f
}

/**
 * Backdrop 2.0 validates the concrete shape type before constructing its lens SDF. A generic
 * [androidx.compose.ui.graphics.Shape] wrapper is therefore unsafe even when it returns a rounded
 * outline. Callers may downsample only when they can provide an equivalent supported shape at the
 * sampled scale; every other shape stays on the reference-resolution path.
 */
internal fun referenceLensSampleScale(
    requestedScale: Float,
    hasSupportedSampledShape: Boolean
): Float {
    val scale = requestedScale.coerceIn(0.5f, 1f)
    return if (scale < 0.999f && !hasSupportedSampledShape) 1f else scale
}

internal fun GlassEffectFrame.sampledBackdropOnly(sampleScale: Float): GlassEffectFrame {
    val scale = sampleScale.coerceIn(0.5f, 1f)
    return copy(
        blur = blur?.times(scale),
        lensHeight = lensHeight?.times(scale),
        lensAmount = lensAmount?.times(scale),
        highlight = null,
        shadowAlpha = null,
        innerShadow = null
    )
}

internal fun GlassEffectFrame.decorationOnly(): GlassEffectFrame = copy(
    blur = null,
    lensHeight = null,
    lensAmount = null,
    useVibrancy = false
)

/** Keeps the layer origin stable while reducing its actual allocation and local SDF geometry. */
internal fun GlassGroupLayerPlan.sampled(sampleScale: Float): GlassGroupLayerPlan {
    val scale = sampleScale.coerceIn(0.5f, 1f)
    if (scale >= 0.999f) return this
    val scaledWidth = ceil(size.width * scale).toInt().coerceAtLeast(1)
    val scaledHeight = ceil(size.height * scale).toInt().coerceAtLeast(1)
    val scaledMembers = localPlan.members.map { member ->
        val bounds = member.boundsInViewport
        member.copy(
            boundsInViewport = Rect(
                left = bounds.left * scale,
                top = bounds.top * scale,
                right = bounds.right * scale,
                bottom = bounds.bottom * scale
            ),
            cornerRadiusPx = member.cornerRadiusPx * scale
        )
    }
    return copy(
        size = IntSize(scaledWidth, scaledHeight),
        localPlan = localPlan.copy(
            viewport = Rect(0f, 0f, scaledWidth.toFloat(), scaledHeight.toFloat()),
            members = scaledMembers
        )
    )
}
