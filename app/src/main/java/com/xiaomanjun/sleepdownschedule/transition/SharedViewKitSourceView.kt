package com.xiaomanjun.sleepdownschedule.transition

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import kotlin.math.roundToInt

/** A session-owned projection of the exact Compose source, attached at its full window bounds.
 * The ROM receives a single card View with an Outline, never the page's ComposeView or a clipped
 * Lazy-list child. The payload keeps its original pixels for the existing fallback renderer.
 */
internal class SharedViewKitSourceView private constructor(
    activity: Activity, val bitmap: Bitmap, val radiusPx: Float
) : View(activity) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val clipPath = Path()
    private val destination = RectF()
    private var host: ViewGroup? = null

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        isClickable = false
        clipToOutline = true
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, radiusPx)
            }
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        destination.set(0f, 0f, w.toFloat(), h.toFloat())
        clipPath.reset()
        clipPath.addRoundRect(destination, radiusPx, radiusPx, Path.Direction.CW)
        invalidateOutline()
    }

    override fun onDraw(canvas: Canvas) {
        if (bitmap.isRecycled) return
        val count = canvas.save()
        // Also preserve transparent corners in software capture paths that ignore clipToOutline.
        canvas.clipPath(clipPath)
        canvas.drawBitmap(bitmap, null, destination, paint)
        canvas.restoreToCount(count)
    }

    fun release() {
        host?.overlay?.remove(this)
        host = null
    }

    companion object {
        fun attach(activity: Activity, anchor: TransitionAnchorFrame, bitmap: Bitmap): SharedViewKitSourceView? {
            val root = activity.window.decorView as? ViewGroup ?: return null
            if (!root.isAttachedToWindow || !anchor.isValid) return null
            val bounds = anchor.boundsInWindow
            val position = IntArray(2).also(root::getLocationInWindow)
            val left = bounds.left.roundToInt() - position[0]
            val top = bounds.top.roundToInt() - position[1]
            val width = bounds.width.roundToInt()
            val height = bounds.height.roundToInt()
            if (width < 2 || height < 2) return null
            if (kotlin.math.abs(bitmap.width - width) > 1 || kotlin.math.abs(bitmap.height - height) > 1) return null
            if (left < 0 || top < 0 || left + width > root.width || top + height > root.height) return null
            val view = SharedViewKitSourceView(activity, bitmap,
                anchor.cornerRadiusPx.coerceIn(0f, minOf(width, height) / 2f))
            view.host = root
            root.overlay.add(view)
            view.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY))
            view.layout(left, top, left + width, top + height)
            return view.takeIf { it.isAttachedToWindow } ?: run { view.release(); null }
        }
    }
}
