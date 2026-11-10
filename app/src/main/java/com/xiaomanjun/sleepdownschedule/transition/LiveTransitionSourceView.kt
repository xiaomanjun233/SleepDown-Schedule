package com.xiaomanjun.sleepdownschedule.transition

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import android.view.ViewOutlineProvider
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.viewinterop.AndroidView
import com.xiaomanjun.sleepdownschedule.core.ui.interaction.findActivity
import com.xiaomanjun.sleepdownschedule.glass.withRecordingDensity

/** Presents the real control from its first frame and serves as the SharedViewKit source. */
internal class LiveTransitionSourceView(
    activity: Activity,
    private val drawing: GraphicsLayer,
    private var drawingDensity: Density,
    private var direction: LayoutDirection,
    private val cornerRadiusPx: Float? = null,
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
                outline.setRoundRect(0, 0, view.width, view.height, radius(view.width, view.height))
            }
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        softwareBounds.set(0f, 0f, w.toFloat(), h.toFloat())
        softwareOutline.reset()
        val radius = radius(w, h)
        softwareOutline.addRoundRect(softwareBounds, radius, radius, Path.Direction.CW)
    }

    private fun radius(w: Int, h: Int): Float {
        val maximum = minOf(w, h) / 2f
        return cornerRadiusPx?.coerceIn(0f, maximum) ?: maximum
    }

    val hasSoftwareFrame: Boolean get() = softwareFrame?.isRecycled == false

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

/** Draw the Compose control in its original backdrop coordinates, then present it in its View. */
@Composable
internal fun LiveTransitionButton(
    modifier: Modifier = Modifier,
    cornerRadius: Dp? = null,
    show: Boolean = true,
    content: @Composable (View?) -> Unit,
) {
    val activity = LocalContext.current.findActivity()
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val layer = rememberGraphicsLayer()
    val radius = cornerRadius?.let { with(density) { it.toPx() } }
    val source = remember(activity, layer, radius) {
        activity?.let { LiveTransitionSourceView(it, layer, density, direction, radius) }
    }
    Box(modifier) {
        if (source != null) key(source) {
            AndroidView(factory = { source }, modifier = Modifier.matchParentSize(),
                update = { it.updatePresentation(density, direction, render = show) })
        }
        Box(Modifier.drawWithContent {
            if (show) {
                if (source == null || !source.isAttachedToWindow || source.width <= 0) {
                    drawContent()
                } else {
                    layer.record { withRecordingDensity(density) { this@drawWithContent.drawContent() } }
                    // The View owns the button bounds. Shadows and glow can extend into
                    // adjacent Compose space without changing the SDK's source rectangle.
                    clipRect(0f, 0f, size.width, size.height, ClipOp.Difference) { drawLayer(layer) }
                }
            }
        }) { content(source) }
    }
}

internal fun View.isLiveTransitionSource(activity: Activity): Boolean =
    context === activity && isAttachedToWindow && isShown && width > 1 && height > 1 &&
        windowToken != null && display == activity.window.decorView.display
