package com.xiaomanjun.sleepdownschedule.platform.coloros

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt

internal class LauncherPagerIndicatorHost(
    context: Context
) : FrameLayout(context) {
    var nativeTouch: ((MotionEvent) -> Unit)? = null
    var pageFeedback: () -> Unit = {}
    var onSelect: (Int) -> Unit = {}
    var onScrub: (Float, Boolean, Boolean) -> Unit = { _, _, _ -> }
    private val density = resources.displayMetrics.density
    private val padding = 14f * density
    private val dot = 8f * density
    private val gap = 6f * density
    private val barHeight = 28f * density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val focus = RectF()
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var count = 0
    private var position = 0f
    private var downX = 0f
    private var dragging = false
    private var touching = false
    private var lastScrub = 0f
    private var feedbackPage = 0
    private var released = false
    init {
        isClickable = true
        clipChildren = false
        clipToPadding = false
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    fun setPages(value: Int, continuous: Float) {
        val changed = count != value
        count = value
        position = continuous.coerceIn(0f, (value - 1).coerceAtLeast(0).toFloat())
        if (changed) requestLayout()
        invalidate()
    }
    fun setInputEnabled(value: Boolean) {
        if (!value && touching) cancelGesture()
        isEnabled = value
    }
    private fun windowSize() = min(count, 22).coerceAtLeast(1)
    private fun windowStart() = (floor(position).toInt() - windowSize() / 2).coerceIn(0, (count - windowSize()).coerceAtLeast(0))
    private fun step() = if (windowSize() > 1) (width - padding * 2f - dot) / (windowSize() - 1) else 0f
    private fun center(page: Float) = padding + dot / 2f + (page - windowStart()) * step()
    private fun pageAt(x: Float): Float {
        val local = if (layoutDirection == View.LAYOUT_DIRECTION_RTL) width - x else x
        return if (step() > 0f) (windowStart() + (local - padding - dot / 2f) / step())
            .coerceIn(0f, (count - 1).coerceAtLeast(0).toFloat()) else 0f
    }
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val slots = windowSize().coerceAtLeast(4)
        setMeasuredDimension(resolveSize((padding * 2f + slots * dot + (slots - 1) * gap).roundToInt(), widthMeasureSpec),
            resolveSize(barHeight.roundToInt(), heightMeasureSpec))
    }
    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        if (count <= 0) return
        val save = canvas.save()
        if (layoutDirection == View.LAYOUT_DIRECTION_RTL) { canvas.translate(width.toFloat(), 0f); canvas.scale(-1f, 1f) }
        val y = height / 2f
        paint.color = 0x4DFFFFFF
        for (page in windowStart() until windowStart() + windowSize()) canvas.drawCircle(center(page.toFloat()), y, dot / 2f, paint)
        val start = floor(position)
        val fraction = position - start
        val left = center(start) + fraction * fraction * step()
        val right = center(start) + (1f - (1f - fraction) * (1f - fraction)) * step()
        paint.color = android.graphics.Color.WHITE
        focus.set(left - dot / 2f, y - dot / 2f, right + dot / 2f, y + dot / 2f)
        canvas.drawRoundRect(focus, dot / 2f, dot / 2f, paint)
        canvas.restoreToCount(save)
    }
    override fun onInterceptTouchEvent(event: MotionEvent) = true
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled || released || count < 1) return false
        nativeTouch?.invoke(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                touching = true
                dragging = false
                feedbackPage = position.roundToInt()
                lastScrub = pageAt(event.x)
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> {
                if (!touching) return false
                if (!dragging && abs(event.x - downX) > slop) dragging = true
                if (dragging) {
                    lastScrub = pageAt(event.x)
                    feedbackFor(lastScrub)
                    onScrub(lastScrub, true, false)
                }
            }
            MotionEvent.ACTION_UP -> {
                feedbackFor(pageAt(event.x))
                if (dragging) onScrub(pageAt(event.x), false, false) else onSelect(pageAt(event.x).roundToInt())
                touching = false; dragging = false
                parent?.requestDisallowInterceptTouchEvent(false)
                performClick()
            }
            MotionEvent.ACTION_CANCEL -> cancelGesture()
        }
        return true
    }
    override fun performClick(): Boolean { super.performClick(); return true }
    private fun feedbackFor(page: Float) {
        val next = page.roundToInt()
        if (next != feedbackPage) {
            feedbackPage = next
            pageFeedback()
        }
    }
    private fun cancelGesture() {
        if (touching) {
            val cancel = MotionEvent.obtain(0L, android.os.SystemClock.uptimeMillis(), MotionEvent.ACTION_CANCEL, downX, height / 2f, 0)
            nativeTouch?.invoke(cancel)
            cancel.recycle()
        }
        if (dragging) onScrub(lastScrub, false, true)
        touching = false; dragging = false
        parent?.requestDisallowInterceptTouchEvent(false)
    }
    fun release() {
        cancelGesture()
        released = true
        nativeTouch = null
        pageFeedback = {}
        onSelect = {}; onScrub = { _, _, _ -> }
    }
    override fun onDetachedFromWindow() { cancelGesture(); super.onDetachedFromWindow() }
}
