package com.xiaomanjun.sleepdownschedule.feature.home.week

import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.kyant.shapes.RoundedRectangle
import kotlin.math.ceil

/** A short glass stroke cut from the card's actual continuous outline, not a separate button. */
internal class WeekResizeCornerShape(
    private val cardSize: DpSize,
    private val cardCorner: Dp,
    private val badgeSize: Dp,
    topStart: CornerSize = CornerSize(0.dp),
    topEnd: CornerSize = CornerSize(0.dp),
    bottomEnd: CornerSize = CornerSize(cardCorner),
    bottomStart: CornerSize = CornerSize(0.dp)
) : CornerBasedShape(topStart, topEnd, bottomEnd, bottomStart) {
    override fun createOutline(
        size: Size, topStart: Float, topEnd: Float, bottomEnd: Float, bottomStart: Float,
        layoutDirection: LayoutDirection
    ): Outline {
        val scale = size.width / badgeSize.value
        val cardPixels = Size(cardSize.width.value * scale, cardSize.height.value * scale)
        val outline = RoundedRectangle((cardCorner.value * scale).dp)
            .createOutline(cardPixels, layoutDirection, Density(1f))
        val cardPath = androidx.compose.ui.graphics.Path().apply { addOutline(outline) }.asAndroidPath()
        // The 44dp input host is offset by 4dp. Cancel that offset so the material hugs the
        // source outline at every corner setting and at every step of a resize gesture.
        cardPath.offset(size.width - cardPixels.width - 4f * scale, size.height - cardPixels.height - 4f * scale)
        // Trim the centerline first, leaving room for genuine round end caps. Intersecting an
        // already filled stroke with the badge rectangle cut both outer ends flat.
        val radius = 4f * scale
        val measure = PathMeasure(cardPath, false)
        val point = FloatArray(2)
        fun inside(distance: Float): Boolean {
            measure.getPosTan(distance, point, null)
            return point[0] >= radius && point[1] >= radius
        }
        val centerline = Path()
        val steps = ceil(measure.length / scale.coerceAtLeast(0.1f)).toInt().coerceAtLeast(1)
        var previousDistance = 0f
        var previousInside = inside(0f)
        var startDistance = 0f
        for (step in 1..steps) {
            val distance = measure.length * step / steps
            val currentInside = inside(distance)
            if (currentInside != previousInside) {
                var low = previousDistance
                var high = distance
                repeat(10) {
                    val middle = (low + high) / 2f
                    if (inside(middle) == previousInside) low = middle else high = middle
                }
                val boundary = (low + high) / 2f
                if (currentInside) startDistance = boundary
                else measure.getSegment(startDistance, boundary, centerline, true)
            }
            previousDistance = distance
            previousInside = currentInside
        }
        if (previousInside) measure.getSegment(startDistance, measure.length, centerline, true)
        val rounded = Path()
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = radius * 2f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }.getFillPath(centerline, rounded)
        return Outline.Generic(rounded.asComposePath())
    }

    override fun copy(
        topStart: CornerSize, topEnd: CornerSize, bottomEnd: CornerSize, bottomStart: CornerSize
    ): CornerBasedShape = WeekResizeCornerShape(cardSize, cardCorner, badgeSize, topStart, topEnd, bottomEnd, bottomStart)
}
