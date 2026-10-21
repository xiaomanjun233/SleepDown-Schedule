package com.xiaomanjun.sleepdownschedule.platform.coloros

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.kyant.shapes.RoundedRectangularShape

internal data class ColorOsMaterialCorners(val radius: Float, val weight: Float)

/** COUI's RenderNode stroke needs its own geometry; clipping a square stroke is insufficient. */
internal fun colorOsMaterialCorners(
    shape: Shape, size: Size, density: Density, direction: LayoutDirection
): ColorOsMaterialCorners? {
    if (size.minDimension <= 0f) return null
    val radius = if (shape is RoundedRectangularShape) {
        // Continuous Kyant corners produce Outline.Generic, but expose their actual radii here.
        val corners = shape.corners(size, direction, density)
        if (corners.topLeft != corners.topRight || corners.topLeft != corners.bottomLeft ||
            corners.topLeft != corners.bottomRight) return null
        corners.topLeft
    } else when (val outline = shape.createOutline(size, direction, density)) {
        is Outline.Rectangle -> 0f
        is Outline.Rounded -> {
            val rect = outline.roundRect
            val corner = rect.topLeftCornerRadius
            if (corner.x != corner.y || corner != rect.topRightCornerRadius ||
                corner != rect.bottomLeftCornerRadius || corner != rect.bottomRightCornerRadius) return null
            corner.x
        }
        is Outline.Generic -> return null // Keep arbitrary morph paths; never replace them with a capsule.
    }.coerceIn(0f, size.minDimension / 2f)
    // Same shape categories as COUIButton.updateStrokeCorner, with the owner's actual radius.
    val weight = when {
        radius == size.minDimension / 2f -> if (size.width == size.height) 2f else 1f
        else -> 3f // COUIButton treats every explicit non-capsule radius, including zero, as 3.
    }
    return ColorOsMaterialCorners(radius, weight)
}
