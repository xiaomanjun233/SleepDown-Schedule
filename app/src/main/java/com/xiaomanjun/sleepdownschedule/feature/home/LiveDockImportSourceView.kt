package com.xiaomanjun.sleepdownschedule.feature.home

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.roundToInt

/** The View replays the actual Dock drawing commands at the FAB's location. */
internal class LiveDockImportSourceView private constructor(
    activity: Activity,
    private val root: ViewGroup,
    private val drawing: GraphicsLayer,
    private val crop: Rect,
    private val drawingDensity: Density,
    private val direction: LayoutDirection
) : View(activity) {
    private val scope = CanvasDrawScope()
    private val softwarePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val softwareBounds = RectF()
    private val softwareOutline = Path()
    private var softwareFrame: Bitmap? = null

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        clipToOutline = true
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

    /** SharedViewKit can call View.draw on a software Canvas while registering a live View. */
    fun prepareSoftwareFrame(bitmap: Bitmap?): Boolean {
        softwareFrame = bitmap?.takeUnless { it.isRecycled }?.let {
            if (it.config == Bitmap.Config.HARDWARE) {
                runCatching { it.copy(Bitmap.Config.ARGB_8888, false) }.getOrNull()
            } else it
        }
        return softwareFrame != null
    }

    override fun onDraw(canvas: AndroidCanvas) {
        if (!canvas.isHardwareAccelerated || drawing.isReleased) {
            softwareFrame?.takeUnless { it.isRecycled }?.let { frame ->
                val count = canvas.save()
                canvas.clipPath(softwareOutline)
                canvas.drawBitmap(frame, null, softwareBounds, softwarePaint)
                canvas.restoreToCount(count)
            }
            return
        }
        scope.draw(drawingDensity, direction, Canvas(canvas), Size(width.toFloat(), height.toFloat())) {
            translate(-crop.left, -crop.top) { drawLayer(drawing) }
        }
    }

    fun release() {
        root.overlay.remove(this)
        softwareFrame = null
    }

    companion object {
        fun attach(
            activity: Activity,
            composeRoot: View,
            boundsInRoot: Rect,
            drawing: GraphicsLayer,
            crop: Rect,
            density: Density,
            direction: LayoutDirection
        ): LiveDockImportSourceView? {
            val root = activity.window.decorView as? ViewGroup ?: return null
            val width = boundsInRoot.width.roundToInt()
            val height = boundsInRoot.height.roundToInt()
            if (!root.isAttachedToWindow || drawing.isReleased || drawing.size.width < 2 ||
                drawing.size.height < 2 || width < 2 || height < 2) return null
            val compose = IntArray(2).also(composeRoot::getLocationInWindow)
            val decor = IntArray(2).also(root::getLocationInWindow)
            val left = boundsInRoot.left.roundToInt() + compose[0] - decor[0]
            val top = boundsInRoot.top.roundToInt() + compose[1] - decor[1]
            if (left < 0 || top < 0 || left + width > root.width || top + height > root.height) return null
            val source = LiveDockImportSourceView(activity, root, drawing, crop, density, direction)
            root.overlay.add(source)
            source.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY))
            source.layout(left, top, left + width, top + height)
            return source.takeIf { it.isAttachedToWindow } ?: run { source.release(); null }
        }
    }
}
