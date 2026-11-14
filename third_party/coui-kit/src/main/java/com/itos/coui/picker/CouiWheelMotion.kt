package com.itos.coui.picker

import android.content.Context
import android.view.ViewConfiguration
import android.view.animation.PathInterpolator
import android.widget.Scroller
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln

/** Clock 17 COUINumberPicker 的主 Scroller + 对齐 Scroller；不替换原厂运动曲线。 */
internal class CouiWheelMotion(
    context: Context,
    private val scope: CoroutineScope,
    initial: Int,
    val range: IntRange,
    wrap: Boolean,
    step: Float,
    private val diffusion: Float,
) {
    var step: Float = step
        private set
    private val count = range.last.toLong() - range.first.toLong() + 1L
    val wrap = wrap && count > 3L
    var value by mutableIntStateOf(initial)
        private set
    var offset by mutableFloatStateOf(0f)
        private set
    var onChange: (Int) -> Unit = {}
    private val fling = Scroller(context, PathInterpolator(0f, .23f, .1f, 1f))
    private val adjust = Scroller(context, PathInterpolator(0f, 0f, .4f, 1f))
    private val density = context.resources.displayMetrics.density
    private var job: Job? = null
    private var mainRunning = false
    private var touching = false
    private var controlled = initial
    private var disposed = false
    var busy by mutableStateOf(false)
        private set
    private var lastDelta = 0
    private var dragRemainder = 0f
    private val pitch get() = step + diffusion

    /** 一次只渲染少量邻项；范围大小必须为常量时间计算，不能在 draw 中遍历 IntRange。 */
    fun display(relative: Int): Int? {
        val next = value.toLong() + relative
        return if (wrap) (range.first.toLong() + Math.floorMod(next - range.first.toLong(), count)).toInt()
        else next.takeIf { it >= range.first.toLong() && it <= range.last.toLong() }?.toInt()
    }

    fun sync(next: Int) {
        if (next == controlled) return
        controlled = next
        if (next != value) reset(next)
    }

    /** 吸附完成后才处理宿主拒绝的候选；拖动期间不能回写旧值。 */
    fun reconcile(next: Int) {
        if (!disposed && !busy && !touching && value != next) {
            value = next
            offset = 0f
        }
    }

    private fun stopAnimation() {
        job?.cancel()
        job = null
        mainRunning = false
        fling.forceFinished(true)
        adjust.forceFinished(true)
    }

    fun stop() {
        touching = false
        stopAnimation()
        busy = false
    }

    fun reset(next: Int) { stop(); value = next; offset = 0f }
    fun dispose() { disposed = true; stop() }

    /** 测量阶段同步切换步长；旧像素位移不能继续驱动新几何，不产生业务回调。 */
    fun resize(step: Float) {
        reset(controlled)
        this.step = step
    }

    /** DOWN 中断旧曲线，但保留当前像素位置供下一个事件按原厂规则对齐。 */
    fun begin() {
        if (disposed) return
        stopAnimation()
        touching = true
        busy = true
        dragRemainder = 0f
        lastDelta = 0
    }

    fun beginDrag() {
        if (disposed) return
        stopAnimation()
        touching = true
        busy = true
    }

    fun drag(delta: Float) {
        if (disposed) return
        val total = dragRemainder + delta
        lastDelta = total.toInt()
        dragRemainder = total - lastDelta
        scroll(lastDelta)
    }

    private fun scroll(delta: Int) {
        if (!wrap && ((value == range.first && delta > 0 && offset + delta >= 0) ||
                (value == range.last && delta < 0 && offset + delta <= 0))) {
            offset = 0f
            return
        }
        offset += delta
        while (offset > step * .95f + diffusion) {
            val next = display(-1) ?: run { offset = 0f; return }
            offset = (offset - pitch).toInt().toFloat()
            change(next)
        }
        while (offset < -step * .95f - diffusion) {
            val next = display(1) ?: run { offset = 0f; return }
            offset = (offset + pitch).toInt().toFloat()
            change(next)
        }
    }

    private fun change(next: Int) {
        if (value != next) {
            value = next
            onChange(next)
        }
    }

    /** 同步结算中途停止的一步，然后再对新的方向做范围判断。 */
    private fun alignInterruptedStep() {
        val direction = if (abs(offset) > pitch / 2f) {
            if (offset > 0f) -1 else 1
        } else 0
        if (direction != 0) display(direction)?.let(::change)
        offset = 0f
    }

    fun canStep(direction: Int) = display(direction) != null
    fun keyboardStep(direction: Int): Boolean {
        if (disposed || touching || !canStep(direction)) return false
        if (!mainRunning) tap(direction)
        return true
    }

    fun tap(direction: Int) {
        if (disposed) return
        stopAnimation()
        alignInterruptedStep()
        if (display(direction) == null) {
            busy = touching
            return
        }
        animate(fling, (-direction * pitch).toInt(), 300)
    }

    /** 普通 UP 保留已启动的单步；CANCEL 终止旧任务后仅吸附已存在的位移。 */
    fun releaseTap(cancelled: Boolean = false) {
        if (disposed) return
        touching = false
        if (cancelled) {
            stopAnimation()
            settle()
        } else if (job == null) {
            settle()
        }
    }

    fun end(velocity: Float, cancelled: Boolean) {
        if (disposed) return
        touching = false
        if (cancelled) {
            stopAnimation()
            settle()
            return
        }
        if (abs(velocity) > 750f) {
            val raw = velocity.coerceIn(-5000f, 5000f).toInt()
            val speed = (raw * 1.6f).toInt()
            val decelerationRate = ln(.78) / ln(.9)
            val physical = density * 160f * 386.0878f * .84f
            val friction = ViewConfiguration.getScrollFriction()
            val logarithm = ln(abs(speed) * .35 / (friction * physical))
            val distance = friction * physical * exp(decelerationRate / (decelerationRate - 1) * logarithm)
            val rounded = if (distance > pitch) distance - distance % pitch else distance % pitch
            val travel = if (speed < 0) -(rounded + lastDelta + (offset - lastDelta) % pitch)
                else rounded + lastDelta - (offset + lastDelta) % pitch
            val duration = (exp(logarithm / (decelerationRate - 1)) * 1000).toInt()
            animate(fling, travel.toInt(), (duration * 1.5f).toInt())
        } else settle()
    }

    fun settle() {
        if (disposed) return
        var target = -offset.toInt()
        if (abs(target) > pitch / 2f) target += (if (target > 0) -pitch else pitch).toInt()
        if (target == 0) {
            stopAnimation()
            offset = 0f
            busy = touching
            return
        }
        animate(adjust, target, 300)
    }

    private fun animate(scroller: Scroller, distance: Int, duration: Int) {
        if (disposed) return
        stopAnimation()
        scroller.startScroll(0, 0, 0, distance, duration.coerceAtLeast(1))
        mainRunning = scroller === fling
        busy = true
        job = scope.launch {
            var previous = 0
            do {
                withFrameNanos { }
                scroller.computeScrollOffset()
                val current = scroller.currY
                scroll(current - previous)
                previous = current
            } while (!scroller.isFinished)
            job = null
            mainRunning = false
            if (scroller === fling) settle() else {
                offset = 0f
                busy = touching
            }
        }
    }
}
