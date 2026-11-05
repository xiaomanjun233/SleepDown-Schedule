package com.itos.coui.slider

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.view.Choreographer
import android.view.animation.PathInterpolator
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import com.itos.coui.motion.CouiSpring
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

/** 移植 COUISeekBarAdvanced / SectionAnimationHandler, 逻辑进度与绘制位置分别维护。 */
internal class CouiSliderMotion(
    private val range: ClosedFloatingPointRange<Float>,
    private val steps: Int,
    private val section: Boolean,
    private val density: Float,
    initialValue: Float,
    private val onValueChange: (Float) -> Unit,
    private val onValueChangeFinished: () -> Unit,
    private val feedback: (Int, Boolean) -> Unit,
) {
    private val intervals = steps + 1
    private val span = range.endInclusive - range.start
    private var lastExternalValue = initialValue
    private var pendingRelease = false
    private var downX = 0f
    private var anchorSection = -1
    private var anchorX = 0f
    private var direction = 0
    private var lastFingerX = 0f
    private var fingerOffset = 0f
    private var dragAnimValue = 0f
    private var dragAnimStart = 0f
    private var dragAnimEnd = Float.NaN
    private var fastRelease = false
    private var bumpingEnd = false
    private var transition: ValueAnimator? = null
    private var dragSnap: ValueAnimator? = null
    private var fling: Choreographer.FrameCallback? = null
    private var pressed = false
    private var dragging = false

    val height: CouiSpring = CouiSpring(0f) {
        if (pendingRelease && heightFraction >= 0.5f) {
            pendingRelease = false
            height.animateTo(0f, 0.5f)
        }
    }
    val thumbRadius = CouiSpring(6f)
    val texture = CouiSpring(0f)
    val thumbCover = CouiSpring(100f)
    val position = CouiSpring(0f)
    val glitter = CouiSpring(0f)
    var value by mutableFloatStateOf(initialValue.coerceIn(range))
        private set
    var length = 0f
        private set
    val heightFraction: Float get() = (height.value / 100f).coerceIn(0f, 1f)
    val fraction: Float get() = normalize(value)
    val visualFraction: Float get() = if (length > 0f) (position.value / length).coerceIn(0f, 1f) else fraction

    fun syncValue(externalValue: Float) {
        if (externalValue == lastExternalValue) return
        lastExternalValue = externalValue
        val next = externalValue.coerceIn(range)
        // 调用方可以把等分档位转成整数, 不让该回传反复打断同一档的视觉动画。
        if (next == value || steps > 0 && index(normalize(next)) == index(fraction)) return
        cancelGesture()
        stopPositionAnimations()
        value = next
        position.snapTo(fraction * length)
    }

    fun resize(nextLength: Float) {
        if (nextLength == length) return
        cancelGesture()
        stopPositionAnimations()
        length = nextLength.coerceAtLeast(0f)
        position.snapTo(fraction * length)
    }

    fun press(fingerX: Float) {
        stopPositionAnimations()
        resetAnchor()
        pressed = true
        dragging = false
        pendingRelease = false
        bumpingEnd = false
        downX = fingerX
        height.animateTo(100f, 0.5f)
        thumbRadius.animateTo(8f, 0.5f, 0.3f, minimumChange = 1f / density)
        texture.animateTo(100f, 0.3f)
        thumbCover.snapTo(100f)
        thumbCover.animateTo(0f, 0.5f)
    }

    fun drag(fingerX: Float, velocity: Float) {
        if (!pressed || length <= 0f) return
        dragging = true
        val x = fingerX.coerceIn(0f, length)
        if (section) {
            dragSection(x)
        } else {
            setFraction(x / length, vibrate = true)
            // 原厂 FlexibleFollowHand 使用千分比, 首次远距离拖动也走同一响应时间。
            position.animateTo(x, 0.15f, minimumChange = length / 1000f)
        }
        if (x < length) bumpingEnd = false
        if (!section && x >= length && velocity > 0f && !bumpingEnd) startGlitter(velocity)
    }

    fun release(fingerX: Float, velocity: Float, frameSeconds: Float) {
        if (!pressed) return
        releaseAppearance()
        pressed = false
        if (!dragging) {
            val target = quantize(normalizePosition(fingerX))
            if (section) {
                setFraction(target, vibrate = true)
                animateSection(target * length, 200L)
            } else {
                if (target != fraction && (target == 0f || target == 1f)) feedback(306, false)
                position.animateTo(target * length, 0.3f, onFrame = {
                    setFraction(normalizePosition(it), vibrate = false, truncate = true)
                }) {
                    setFraction(target, vibrate = false)
                    onValueChangeFinished()
                }
            }
            return
        }
        dragging = false
        if (section) {
            if (dragSnap?.isRunning == true) {
                fastRelease = true
            } else {
                val segment = length / intervals
                val target = (index(fraction) + ((fingerX.coerceIn(0f, length) - fraction * length) / segment).toInt())
                    .coerceIn(0, intervals)
                snapSection(target)
                resetAnchor()
            }
        } else {
            position.snapTo(fingerX.coerceIn(0f, length))
            setFraction(normalizePosition(fingerX), vibrate = true)
            if (abs(velocity) >= 100f) startFling(velocity.coerceIn(-8000f, 8000f), frameSeconds)
            else onValueChangeFinished()
        }
    }

    fun cancelGesture() {
        if (pressed) releaseAppearance()
        pressed = false
        dragging = false
        fastRelease = false
        stopPositionAnimations()
        resetAnchor()
    }

    fun setFromAction(nextValue: Float) {
        cancelGesture()
        setFraction(quantize(normalize(nextValue)), vibrate = true)
        position.snapTo(fraction * length)
        onValueChangeFinished()
    }

    fun dispose() {
        pressed = false
        stopPositionAnimations()
        listOf(height, thumbRadius, texture, thumbCover, position, glitter).forEach { it.cancel() }
    }

    private fun releaseAppearance() {
        if (height.isRunning && heightFraction < 0.5f) pendingRelease = true
        else height.animateTo(0f, 0.5f)
        thumbRadius.animateTo(6f, 0.5f, minimumChange = 1f / density)
        texture.animateTo(0f, 0.3f)
        thumbCover.animateTo(100f, 0.3f)
    }

    private fun dragSection(x: Float) {
        val segment = length / intervals
        if (anchorSection < 0) {
            anchorSection = index(normalizePosition(downX))
            anchorX = anchorSection * segment
            setFraction(anchorSection.toFloat() / intervals, vibrate = true)
            position.snapTo(anchorX)
            direction = if (x < anchorX) -1 else 1
            lastFingerX = x
            return
        }
        val nextDirection = when {
            x > lastFingerX + 0.5f -> 1
            x < lastFingerX - 0.5f -> -1
            else -> 0
        }
        if (nextDirection != 0 && nextDirection == -direction) {
            direction = nextDirection
            if (anchorSection != index(fraction)) {
                anchorSection = index(fraction)
                anchorX = anchorSection * segment
                dragAnimValue = 0f
            }
            dragSnap?.cancel()
        }
        fingerOffset = x - anchorX
        val crossed = ((fingerOffset + if (fingerOffset >= 0f) 0.1f else -0.1f) / segment).toInt()
        val offset = crossed * segment
        if ((anchorSection + crossed).coerceIn(0, intervals) != index(fraction)) {
            startDragSnap(anchorX + offset)
        } else if (dragSnap?.isRunning != true) {
            position.snapTo((anchorX + offset + (fingerOffset - offset) * 0.6f).coerceIn(0f, length))
        }
        lastFingerX = x
    }

    private fun startDragSnap(target: Float) {
        if (position.value == target || dragSnap?.isRunning == true && dragAnimEnd == target) return
        dragSnap?.cancel()
        dragAnimStart = anchorX
        dragAnimEnd = target
        dragSnap = ValueAnimator.ofFloat(dragAnimValue, target - anchorX).apply {
            duration = 100L
            interpolator = DragInterpolator
            addUpdateListener {
                dragAnimValue = it.animatedValue as Float
                val x = (dragAnimStart + dragAnimValue * 0.4f + fingerOffset * 0.6f).coerceIn(0f, length)
                position.snapTo(x)
                val selected = if (dragAnimEnd > dragAnimStart) (x / (length / intervals)).roundToInt()
                    else ceil(x / (length / intervals)).toInt()
                setFraction(selected.coerceIn(0, intervals).toFloat() / intervals, vibrate = true)
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (fastRelease) {
                        fastRelease = false
                        val targetSection = index(normalizePosition(lastFingerX))
                        resetAnchor()
                        snapSection(targetSection)
                    }
                }
            })
            start()
        }
    }

    private fun snapSection(target: Int) {
        setFraction(target.toFloat() / intervals, vibrate = false)
        val x = fraction * length
        if (abs(position.value - x) < 0.5f) {
            position.snapTo(x)
            onValueChangeFinished()
        } else animateSection(x, 150L)
    }

    private fun animateSection(target: Float, durationMs: Long) {
        transition?.cancel()
        transition = ValueAnimator.ofFloat(position.value, target).apply {
            duration = durationMs
            addUpdateListener { position.snapTo(it.animatedValue as Float) }
            addListener(object : AnimatorListenerAdapter() {
                private var cancelled = false
                override fun onAnimationCancel(animation: Animator) { cancelled = true }
                override fun onAnimationEnd(animation: Animator) {
                    if (!cancelled) onValueChangeFinished()
                }
            })
            start()
        }
    }

    private fun startFling(initialVelocity: Float, frameSeconds: Float) {
        var velocity = initialVelocity
        var x = position.value
        val choreographer = Choreographer.getInstance()
        val callback = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                // Oplus World.solve 的无约束单轴分支, 不是 AndroidX 的指数衰减。
                velocity /= 1f + 15f * frameSeconds
                x += velocity * frameSeconds
                val clamped = x.coerceIn(0f, length)
                if (x >= length && !bumpingEnd) startGlitter(velocity)
                position.snapTo(clamped)
                setFraction(normalizePosition(clamped), vibrate = false)
                // shortcut: 可见边界处结束惯性; 需要越界回弹或精确结束回调时再移植原厂约束体。
                if (x != clamped || abs(velocity) < 0.1f) {
                    fling = null
                    onValueChangeFinished()
                } else choreographer.postFrameCallback(this)
            }
        }
        fling = callback
        choreographer.postFrameCallback(callback)
    }

    private fun startGlitter(velocity: Float) {
        bumpingEnd = true
        glitter.animateTo(0f, 0.6f, velocity = abs(velocity))
    }

    private fun setFraction(next: Float, vibrate: Boolean, truncate: Boolean = false) {
        val raw = next.coerceIn(0f, 1f)
        val selected = if (steps > 0 || section) {
            val i = if (truncate) (raw * intervals).toInt() else index(raw)
            i.toFloat() / intervals
        } else raw
        val nextValue = range.start + span * selected
        if (value == nextValue) return
        value = nextValue
        onValueChange(nextValue)
        if (vibrate && (section || dragging || selected == 0f || selected == 1f)) {
            feedback(if (selected == 0f || selected == 1f) 306 else if (section) 308 else 305, section)
        }
    }

    private fun stopPositionAnimations() {
        fastRelease = false
        transition?.cancel()
        dragSnap?.cancel()
        position.cancelComplete()
        fling?.let { Choreographer.getInstance().removeFrameCallback(it) }
        fling = null
    }

    private fun resetAnchor() {
        fastRelease = false
        dragSnap?.cancel()
        anchorSection = -1
        anchorX = 0f
        direction = 0
        fingerOffset = 0f
        dragAnimValue = 0f
        dragAnimStart = 0f
        dragAnimEnd = Float.NaN
        lastFingerX = 0f
    }

    private fun normalize(next: Float): Float = if (span > 0f) ((next - range.start) / span).coerceIn(0f, 1f) else 0f
    private fun normalizePosition(x: Float): Float = if (length > 0f) (x / length).coerceIn(0f, 1f) else fraction
    private fun index(next: Float): Int = (next * intervals).roundToInt().coerceIn(0, intervals)
    private fun quantize(next: Float): Float = if (steps > 0 || section) index(next).toFloat() / intervals else next

    private companion object {
        val DragInterpolator = PathInterpolator(0f, 0f, 0.25f, 1f)
    }
}
