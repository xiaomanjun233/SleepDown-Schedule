package com.xiaomanjun.sleepdownschedule.core.ui.designsystem

import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material3.Shapes
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.kyant.shapes.RoundedRectangle
import com.kyant.shapes.UnevenRoundedRectangle

/** Material components require CornerBasedShape; preserve corner copies and RTL semantics. */
private class ContinuousCornerShape(
    topStart: CornerSize,
    topEnd: CornerSize = topStart,
    bottomEnd: CornerSize = topStart,
    bottomStart: CornerSize = topStart
) : CornerBasedShape(topStart, topEnd, bottomEnd, bottomStart) {
    constructor(radius: Dp) : this(CornerSize(radius))

    override fun createOutline(
        size: Size,
        topStart: Float,
        topEnd: Float,
        bottomEnd: Float,
        bottomStart: Float,
        layoutDirection: LayoutDirection
    ): Outline = UnevenRoundedRectangle(
        topStart.dp, topEnd.dp, bottomEnd.dp, bottomStart.dp
    ).createOutline(size, layoutDirection, Density(1f))

    override fun copy(
        topStart: CornerSize,
        topEnd: CornerSize,
        bottomEnd: CornerSize,
        bottomStart: CornerSize
    ): CornerBasedShape = ContinuousCornerShape(topStart, topEnd, bottomEnd, bottomStart)
}

internal val SleepDownContinuousShapes = Shapes(
    extraSmall = ContinuousCornerShape(4.dp),
    small = ContinuousCornerShape(8.dp),
    medium = ContinuousCornerShape(12.dp),
    large = ContinuousCornerShape(16.dp),
    extraLarge = ContinuousCornerShape(28.dp)
)

/** Pixel-space counterpart for widget bitmaps and schedule previews, using the same Kyant path. */
internal fun continuousRoundedRectPath(rect: RectF, radiusX: Float, radiusY: Float): Path {
    if (radiusX <= 0f || radiusY <= 0f || rect.isEmpty) {
        return Path().apply { addRect(rect, Path.Direction.CW) }
    }
    val scaleY = radiusY / radiusX
    val outline = RoundedRectangle(radiusX.dp).createOutline(
        Size(rect.width(), rect.height() / scaleY), LayoutDirection.Ltr, Density(1f)
    )
    return androidx.compose.ui.graphics.Path().apply { addOutline(outline) }.asAndroidPath().apply {
        transform(Matrix().apply {
            setScale(1f, scaleY)
            postTranslate(rect.left, rect.top)
        })
    }
}

internal fun Canvas.drawContinuousRoundRect(rect: RectF, radiusX: Float, radiusY: Float, paint: Paint) {
    drawPath(continuousRoundedRectPath(rect, radiusX, radiusY), paint)
}

internal fun DrawScope.drawContinuousRoundRect(
    color: Color,
    topLeft: Offset = Offset.Zero,
    size: Size = this.size,
    cornerRadius: CornerRadius = CornerRadius.Zero,
    style: DrawStyle = Fill,
    blendMode: BlendMode = BlendMode.SrcOver
) {
    if (size.width <= 0f || size.height <= 0f) return
    val outline = RoundedRectangle(cornerRadius.x.toDp()).createOutline(size, layoutDirection, this)
    translate(topLeft.x, topLeft.y) { drawOutline(outline, color, style = style, blendMode = blendMode) }
}
