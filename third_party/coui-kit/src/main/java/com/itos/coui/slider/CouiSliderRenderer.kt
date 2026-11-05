package com.itos.coui.slider

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.Path as ComposePath

@Immutable
data class CouiSliderColors(
    val activeTrack: androidx.compose.ui.graphics.Color,
    val inactiveTrack: androidx.compose.ui.graphics.Color,
    val thumb: androidx.compose.ui.graphics.Color,
    val activeTick: androidx.compose.ui.graphics.Color,
    val inactiveTick: androidx.compose.ui.graphics.Color,
    val thumbShadow: androidx.compose.ui.graphics.Color,
)

/** 原厂水平轨道与 TexturedThumbRenderer; 宿主 Shape 同时决定轨道和光效裁剪。 */
internal class CouiSliderRenderer(
    private val density: Float,
    private val shape: Shape,
    private val layoutDirection: LayoutDirection,
) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val backgroundPath = Path()
    private val progressPath = Path()
    private val backgroundBounds = RectF()
    private val progressBounds = RectF()
    private val backgroundRect = RectF()
    private val outlineDensity = Density(density)
    private val rect = RectF()
    private val textureRect = RectF()
    private val bitmapRect = RectF()
    private val matrix = Matrix()
    private val layerA = LinearGradient(
        0f, 1f, 0f, 0f,
        intArrayOf(0xe6ffffff.toInt(), 0xe6ffffff.toInt(), 0x8cffffff.toInt()),
        floatArrayOf(0f, 0.2f, 1f), Shader.TileMode.CLAMP,
    )
    private var layerB: Bitmap? = null
    private val glitterGradient = LinearGradient(0f, 0f, 1f, 0f, 0x00ffffff, 0xa0ffffff.toInt(), Shader.TileMode.CLAMP)

    fun draw(
        canvas: Canvas,
        height: Float,
        motion: CouiSliderMotion,
        colors: CouiSliderColors,
        enabled: Boolean,
        rtl: Boolean,
        tickCount: Int,
    ) {
        val centerY = (height.toInt() / 2).toFloat()
        val start = 14f * density
        val length = motion.length
        val fraction = motion.visualFraction
        val thumbX = start + length * if (rtl) 1f - fraction else fraction
        val trackRadius = 10f * density
        val backgroundRadius = backgroundHeight(motion) / 2f
        backgroundRect.set(start - backgroundRadius, centerY - backgroundRadius,
            start + length + backgroundRadius, centerY + backgroundRadius)
        updateTrackPath(backgroundPath, backgroundBounds, backgroundRect)
        paint.color = colors.inactiveTrack.toArgb()
        canvas.drawPath(backgroundPath, paint)
        val originX = if (rtl) start + length else start
        rect.set(min(originX, thumbX) - trackRadius, centerY - trackRadius,
            max(originX, thumbX) + trackRadius, centerY + trackRadius)
        updateTrackPath(progressPath, progressBounds, rect)
        paint.color = colors.activeTrack.toArgb()
        canvas.drawPath(progressPath, paint)
        val thumbRadius = motion.thumbRadius.value * density
        if (tickCount > 0) {
            for (index in 0..tickCount) {
                val markFraction = index.toFloat() / tickCount
                val x = start + length * if (rtl) 1f - markFraction else markFraction
                val markRadius = 3f * density
                if (abs(x - thumbX) + markRadius > thumbRadius) {
                    paint.color = if (markFraction <= fraction) colors.activeTick.toArgb() else colors.inactiveTick.toArgb()
                    canvas.drawCircle(x, centerY, markRadius, paint)
                }
            }
        }
        drawThumb(canvas, thumbX, centerY, thumbRadius, motion, colors, enabled)
        val glitterAlpha = (255.0 * (1.0 - exp(-ln(85.0) / 360.0 * motion.glitter.value.coerceAtLeast(0f)))).roundToInt()
        if (glitterAlpha > 0) {
            matrix.setScale(if (rtl) -rect.width() else rect.width(), 1f)
            matrix.postTranslate(if (rtl) rect.right else rect.left, 0f)
            glitterGradient.setLocalMatrix(matrix)
            paint.shader = glitterGradient
            paint.alpha = glitterAlpha.coerceIn(0, 255)
            canvas.drawPath(progressPath, paint)
            paint.shader = null
            paint.alpha = 255
        }
    }

    fun clipTrack(canvas: Canvas) {
        canvas.clipPath(backgroundPath)
    }

    fun dispose() {
        layerB?.recycle()
        layerB = null
    }

    private fun backgroundHeight(motion: CouiSliderMotion): Float {
        val height = (20f + 8f * motion.heightFraction) * density
        return if (motion.heightFraction > 0f && motion.heightFraction < 1f) height.roundToInt().toFloat() else height
    }

    private fun updateTrackPath(path: Path, previousBounds: RectF, bounds: RectF) {
        if (previousBounds == bounds) return
        previousBounds.set(bounds)
        val outline = shape.createOutline(Size(bounds.width(), bounds.height()), layoutDirection, outlineDensity)
        path.set(ComposePath().apply { addOutline(outline) }.asAndroidPath())
        path.offset(bounds.left, bounds.top)
    }

    private fun drawThumb(
        canvas: Canvas, x: Float, y: Float, radius: Float,
        motion: CouiSliderMotion, colors: CouiSliderColors, enabled: Boolean,
    ) {
        val textureAlpha = (motion.texture.value * 2.55f).toInt().coerceIn(0, 255)
        if (!enabled || textureAlpha <= 0) {
            paint.color = colors.thumb.toArgb()
            if (enabled) paint.setShadowLayer(4f * density, 0f, 2f * density, colors.thumbShadow.toArgb())
            canvas.drawCircle(x, y, radius, paint)
            paint.clearShadowLayer()
            return
        }
        textureRect.set(x - radius, y - radius, x + radius, y + radius)
        val checkpoint = canvas.saveLayerAlpha(textureRect.left, textureRect.top, textureRect.right, textureRect.bottom, textureAlpha)
        matrix.setScale(1f, radius * 2f)
        matrix.postTranslate(textureRect.left, textureRect.top)
        layerA.setLocalMatrix(matrix)
        paint.color = Color.WHITE
        paint.shader = layerA
        canvas.drawRoundRect(textureRect, radius, radius, paint)
        paint.shader = null
        // 原厂内影的 stroke、blur、留白均使用物理像素; Bitmap 只创建一次。
        val bitmap = layerB ?: createLayerB(radius).also { layerB = it }
        bitmapRect.set(textureRect.left - 12f, textureRect.top - 12f,
            textureRect.right + 12f, textureRect.bottom + 12f)
        canvas.drawBitmap(bitmap, null, bitmapRect, bitmapPaint)
        canvas.restoreToCount(checkpoint)
        paint.color = Color.WHITE
        paint.alpha = (motion.thumbCover.value * 2.55f).toInt().coerceIn(0, 255)
        canvas.drawCircle(x, y, radius, paint)
        paint.alpha = 255
    }

    private fun createLayerB(radius: Float): Bitmap {
        val diameter = radius * 2f
        val size = ceil(diameter + 24f).toInt()
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val bounds = RectF(12f, 12f, diameter + 12f, diameter + 12f)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x77ffffff }
        canvas.drawRoundRect(bounds, radius, radius, fill)
        val outline = Path().apply { addOval(bounds, Path.Direction.CCW) }
        canvas.clipPath(outline)
        fill.style = Paint.Style.STROKE
        fill.strokeWidth = 10f
        fill.color = Color.TRANSPARENT
        fill.setShadowLayer(5f, 0f, 0.4f, Color.WHITE)
        canvas.drawOval(bounds, fill)
        return bitmap
    }
}
