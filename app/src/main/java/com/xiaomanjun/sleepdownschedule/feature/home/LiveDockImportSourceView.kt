package com.xiaomanjun.sleepdownschedule.feature.home

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import android.view.ViewOutlineProvider
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

/** Hosts the visible FAB throughout its lifetime and is also the View passed to SharedViewKit. */
internal class LiveDockImportSourceView(
    activity: Activity,
    private val drawing: GraphicsLayer,
    private var drawingDensity: Density,
    private var direction: LayoutDirection,
) : View(activity) {
    private val scope = CanvasDrawScope()
    private val softwarePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val softwareBounds = RectF()
    private val softwareOutline = Path()
    private var softwareFrame: Bitmap? = null
    private var cropLeft = 0f
    private var renderButton = true

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, minOf(view.width, view.height) / 2f)
            }
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        softwareBounds.set(0f, 0f, w.toFloat(), h.toFloat())
        softwareOutline.reset()
        val radius = minOf(w, h) / 2f
        softwareOutline.addRoundRect(softwareBounds, radius, radius, Path.Direction.CW)
    }

    fun updatePresentation(density: Density, layoutDirection: LayoutDirection, render: Boolean) {
        if (drawingDensity == density && direction == layoutDirection && renderButton == render) return
        drawingDensity = density
        direction = layoutDirection
        renderButton = render
        invalidate()
    }

    fun updateCrop(left: Float) {
        if (cropLeft == left) return
        cropLeft = left
        invalidate()
    }

    /** The SDK can call View.draw with a software canvas. Sample the original button's pixels. */
    fun prepareSoftwareFrame(bitmap: Bitmap?): Boolean {
        softwareFrame = bitmap?.takeUnless { it.isRecycled }?.let {
            if (it.config == Bitmap.Config.HARDWARE) {
                runCatching { it.copy(Bitmap.Config.ARGB_8888, false) }.getOrNull()
            } else it
        }
        return softwareFrame != null
    }

    fun clearSoftwareFrame() {
        softwareFrame = null
    }

    override fun onDraw(canvas: AndroidCanvas) {
        if (!renderButton) return
        if (!canvas.isHardwareAccelerated || drawing.isReleased) {
            softwareFrame?.takeUnless { it.isRecycled }?.let { frame ->
                val count = canvas.save()
                canvas.clipPath(softwareOutline)
                canvas.drawBitmap(frame, null, softwareBounds, softwarePaint)
                canvas.restoreToCount(count)
            }
            return
        }
        val count = canvas.save()
        canvas.clipRect(0, 0, width, height)
        scope.draw(drawingDensity, direction, Canvas(canvas), Size(width.toFloat(), height.toFloat())) {
            translate(-cropLeft, 0f) { drawLayer(drawing) }
        }
        canvas.restoreToCount(count)
    }
}
