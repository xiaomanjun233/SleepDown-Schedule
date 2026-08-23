package com.xiaomanjun.sleepdownschedule.glass

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private const val CircleBezierKappa = 0.5522848f

/** Immutable per-frame outline used inside a fixed transition envelope. */
data class Issue70LiquidShellOutlineShape(
    val bounds: Rect,
    val cornerRadiusPx: Float
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline = Outline.Generic(
        liquidMotionOutlinePath(
            bounds = bounds,
            cornerRadiusPx = cornerRadiusPx,
            deformation = LiquidDeformationFrame.None
        )
    )
}

/**
 * Stable-identity Kyant surface shape. The backing effect layer keeps one maximum size while this
 * outline grows from the source footprint and overshoots around its center.
 */
class Issue70CenteredLiquidShellShape(
    private val shellSize: () -> Size,
    private val cornerRadiusPx: () -> Float
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val current = shellSize()
        val width = current.width.coerceIn(1f, size.width.coerceAtLeast(1f))
        val height = current.height.coerceIn(1f, size.height.coerceAtLeast(1f))
        val bounds = Rect(
            left = (size.width - width) * 0.5f,
            top = (size.height - height) * 0.5f,
            right = (size.width + width) * 0.5f,
            bottom = (size.height + height) * 0.5f
        )
        return Outline.Generic(
            liquidMotionOutlinePath(
                bounds = bounds,
                cornerRadiusPx = cornerRadiusPx(),
                deformation = LiquidDeformationFrame.None
            )
        )
    }
}

/**
 * Clips only the transient glass shell. The child remains measured and placed at its true target
 * size, so tangent stretch, squeeze and tail lag cannot distort text or interactive content.
 */
class LiquidMotionOutlineShape(
    private val contentInsetPx: Float,
    private val cornerRadiusPx: () -> Float,
    private val deformation: () -> LiquidDeformationFrame
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val inset = contentInsetPx.coerceIn(0f, min(size.width, size.height) * 0.5f)
        val bounds = Rect(
            left = inset,
            top = inset,
            right = (size.width - inset).coerceAtLeast(inset),
            bottom = (size.height - inset).coerceAtLeast(inset)
        )
        return Outline.Generic(
            liquidMotionOutlinePath(
                bounds = bounds,
                cornerRadiusPx = cornerRadiusPx(),
                deformation = deformation()
            )
        )
    }
}

internal fun liquidMotionOutlinePath(
    bounds: Rect,
    cornerRadiusPx: Float,
    deformation: LiquidDeformationFrame
): Path {
    val radius = cornerRadiusPx.coerceIn(0f, min(bounds.width, bounds.height) * 0.5f)
    val control = radius * CircleBezierKappa
    fun point(x: Float, y: Float): Offset = liquidMotionTransformPoint(
        point = Offset(x, y),
        bounds = bounds,
        deformation = deformation
    )

    return Path().apply {
        point(bounds.left + radius, bounds.top).also { moveTo(it.x, it.y) }
        point(bounds.right - radius, bounds.top).also { lineTo(it.x, it.y) }
        cubic(
            point(bounds.right - radius + control, bounds.top),
            point(bounds.right, bounds.top + radius - control),
            point(bounds.right, bounds.top + radius)
        )
        point(bounds.right, bounds.bottom - radius).also { lineTo(it.x, it.y) }
        cubic(
            point(bounds.right, bounds.bottom - radius + control),
            point(bounds.right - radius + control, bounds.bottom),
            point(bounds.right - radius, bounds.bottom)
        )
        point(bounds.left + radius, bounds.bottom).also { lineTo(it.x, it.y) }
        cubic(
            point(bounds.left + radius - control, bounds.bottom),
            point(bounds.left, bounds.bottom - radius + control),
            point(bounds.left, bounds.bottom - radius)
        )
        point(bounds.left, bounds.top + radius).also { lineTo(it.x, it.y) }
        cubic(
            point(bounds.left, bounds.top + radius - control),
            point(bounds.left + radius - control, bounds.top),
            point(bounds.left + radius, bounds.top)
        )
        close()
    }
}

private fun Path.cubic(first: Offset, second: Offset, end: Offset) {
    cubicTo(first.x, first.y, second.x, second.y, end.x, end.y)
}

/** Pure affine deformation used by the renderer and endpoint/parity tests. */
internal fun liquidMotionTransformPoint(
    point: Offset,
    bounds: Rect,
    deformation: LiquidDeformationFrame
): Offset {
    val angle = deformation.tangentAngleRadians
    val tangentX = cos(angle)
    val tangentY = sin(angle)
    val crossX = -tangentY
    val crossY = tangentX
    val relative = point - bounds.center
    val along = relative.x * tangentX + relative.y * tangentY
    val across = relative.x * crossX + relative.y * crossY
    val halfTangentExtent = abs(tangentX) * bounds.width * 0.5f +
        abs(tangentY) * bounds.height * 0.5f

    val stretch = deformation.tangentStretch.coerceIn(0f, 0.18f)
    val tail = deformation.tailLag.coerceIn(0f, 0.14f)
    val rebound = deformation.rebound.coerceIn(0f, 0.12f)
    val alongScale = 1f + stretch + (tail + rebound) * 0.5f
    val alongShift = halfTangentExtent * (rebound - tail) * 0.5f
    val acrossScale = 1f - deformation.crossAxisSqueeze.coerceIn(0f, 0.14f)
    val transformedAlong = along * alongScale + alongShift
    val transformedAcross = across * acrossScale
    return bounds.center + Offset(
        x = tangentX * transformedAlong + crossX * transformedAcross,
        y = tangentY * transformedAlong + crossY * transformedAcross
    )
}
