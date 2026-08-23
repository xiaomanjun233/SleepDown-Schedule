package com.xiaomanjun.sleepdownschedule.glass

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
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

/** Scales absolute-Dp corners with the low-resolution target; percent corners remain relative. */
@Immutable
internal data class DensityScaledShape(
    val delegate: Shape,
    val sampleScale: Float
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline = delegate.createOutline(
        size = size,
        layoutDirection = layoutDirection,
        density = Density(
            density = density.density * sampleScale.coerceIn(0.5f, 1f),
            fontScale = density.fontScale
        )
    )
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
