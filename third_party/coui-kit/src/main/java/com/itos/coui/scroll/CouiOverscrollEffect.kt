package com.itos.coui.scroll

import androidx.compose.foundation.OverscrollEffect
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.overscroll
import android.view.View
import android.view.ViewConfiguration
import android.os.SystemClock
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.interaction.MutableInteractionSource
import com.itos.coui.input.CouiHapticFeedback
import com.itos.coui.input.isCouiTouchCancelled
import com.itos.coui.input.performCouiHapticFeedback
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.LayoutAwareModifierNode
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.PointerInputModifierNode
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.sign
import kotlin.math.sqrt

/**
 * Settings COUIRecyclerView 的纵向越界阻尼和回弹；不绘制平台 glow / Compose stretch。
 * 每个滚动容器持有独立实例，传给 verticalScroll 的 overscrollEffect，不能多节点共用。
 * 只持有边界运动，常规滚动、列表数据与 fling owner 仍由宿主滚动容器提供。
 */
class CouiOverscrollEffect internal constructor(private val scope: CoroutineScope, private val overscrollDistance: Int,
    private val nominalFrameSeconds: Float, private val view: View,
    private val hapticFeedbackEnabled: Boolean) : OverscrollEffect {
    private var translation by mutableFloatStateOf(0f)
    private var mappingScreenHeight = overscrollDistance
    private val physics = CouiScrollPhysics(1, springBackTensionMultiple = 2.15f, scrollView = false)
    private var animation: Job? = null
    private var fling: Job? = null
    private var touched = false
    private var disposed = false
    private var overflowFling = false
    override val isInProgress: Boolean get() = translation != 0f || physics.running
    override val node: DelegatableNode = EdgeNode()

    override fun applyToScroll(delta: Offset, source: NestedScrollSource, performScroll: (Offset) -> Offset): Offset {
        if (disposed) return performScroll(delta)
        if (source != NestedScrollSource.UserInput) {
            val consumed = performScroll(delta)
            val available = delta.y - consumed.y
            val edge = -available.toInt()
            if (fling != null && edge != 0) {
                // ViewFlinger 先绘制本帧剩余位移，再把速度交给 notifyVerticalEdgeReached。
                physics.returnToBounds(edge, 0, preserveVelocity = true, initialVelocity = 0f)
                translation = overFlingTranslation(physics.current)
                if (!overflowFling) view.performCouiHapticFeedback(CouiHapticFeedback.ScrollEdge, hapticFeedbackEnabled)
                overflowFling = true
            }
            // 保留未消费量，使内容 fling 停在边界，并由父链决定剩余速度。
            return consumed
        }
        stop()
        overflowFling = false
        var remaining = delta.y
        var pre = 0f
        // 越界后先恢复边界，再把剩余位移交回内容；不会跨过 0 后吞掉正常滚动。
        if (translation * remaining < 0f) {
            // RecyclerView 的纵向反向分支用 scrollX；本入口只滚动纵轴，scrollX 为 0。
            // 先按原厂整数阻尼求到边界所需输入，再交还跨边界的剩余位移。
            val toEdge = -sign(translation) * ceil(abs(translation) * 5f / 2f)
            pre = if (abs(remaining) >= abs(toEdge)) toEdge else remaining
            translation = if (pre == toEdge) 0f else translation + damp(pre, 0f)
            remaining -= pre
        }
        val consumed = performScroll(Offset(delta.x, remaining))
        val available = remaining - consumed.y
        if (available != 0f) translation += damp(available)
        return Offset(consumed.x, consumed.y + pre + available)
    }

    private fun damp(delta: Float, displacement: Float = translation): Float = ((delta.toInt() *
        (1f - (abs(displacement) / overscrollDistance).coerceAtMost(1f))) / 5f * 2f).toInt().toFloat()

    override suspend fun applyToFling(velocity: Velocity, performFling: suspend (Velocity) -> Velocity) = coroutineScope {
        stop()
        val job = coroutineContext.job
        fling = job
        if (translation == 0f) overflowFling = false
        try {
            var remainingVelocity = velocity
            if (translation != 0f) {
                // 原厂只给高速释放保留速度：同向 springBack，反向恢复边界后交还内容。
                val fast = abs(velocity.y) > 6000f
                val reverse = translation * velocity.y < 0 && fast
                val y = if (reverse) reverseToBoundary(velocity.y) else 0f
                if (y == 0f) rebound(if (fast && !reverse) velocity.y else 0f, overFling = overflowFling)
                remainingVelocity = Velocity(velocity.x, y)
            }
            val consumed = performFling(remainingVelocity)
            val remaining = remainingVelocity.y - consumed.y
            if (translation != 0f && animation == null) {
                if (overflowFling) rebound(remaining, from = physics.current, overFling = true) else rebound()
            }
        } finally {
            // stop() 先撤销 owner；旧协程的 finally 不能重新启动新手势已接管的回弹。
            if (fling === job) {
                fling = null
                if (!touched && translation != 0f && animation == null && !disposed) rebound(overFling = overflowFling)
            }
        }
    }

    private suspend fun reverseToBoundary(fingerVelocity: Float): Float {
        val start = -translation
        physics.fling(start.toInt(), -fingerVelocity.toInt(), nominalFrameSeconds)
        var previous = System.nanoTime()
        val began = previous
        while (physics.running) {
            val frame = withFrameNanos { it }
            physics.update((frame - previous) / 1_000_000_000f, (frame - began) / 1_000_000f)
            previous = frame
            val position = physics.current
            if (position * start <= 0f) {
                val velocity = -physics.currentVelocity
                physics.stop(); translation = 0f
                return velocity
            }
            translation = -position.toFloat()
        }
        return 0f
    }

    internal fun absorbFling(fingerVelocity: Float): Boolean {
        if (fling == null || !overflowFling) return false
        rebound(fingerVelocity, from = physics.current, overFling = true)
        return true
    }

    private fun rebound(fingerVelocity: Float = 0f, from: Int = (-translation).toInt(), overFling: Boolean = false) {
        if (disposed) return
        animation?.cancel()
        animation = null
        physics.stop()
        overflowFling = overFling
        physics.returnToBounds(from, 0, initialVelocity = -fingerVelocity)
        if (!physics.running) { translation = 0f; return }
        animation = scope.launch {
            var previous = System.nanoTime()
            val started = previous
            do {
                val now = withFrameNanos { it }
                physics.update((now - previous) / 1_000_000_000f, (now - started) / 1_000_000f)
                previous = now
                // onOverScrolled(scrollType=3) 不把显示映射后的坐标回写积分器。
                translation = if (overflowFling) overFlingTranslation(physics.current) else -physics.current.toFloat()
            } while (physics.running)
            translation = 0f
            animation = null
        }
    }

    private fun overFlingTranslation(position: Int): Float {
        val limit = mappingScreenHeight * .3731f
        return -((position * limit).toDouble() / sqrt((position * position + limit * limit).toDouble())).toInt().toFloat()
    }

    private fun stop() {
        val runningFling = fling
        fling = null
        runningFling?.cancel()
        animation?.cancel()
        animation = null
        physics.stop()
    }
    internal fun dispose() { disposed = true; stop(); translation = 0f }

    private inner class EdgeNode : Modifier.Node(), LayoutModifierNode, LayoutAwareModifierNode, PointerInputModifierNode {
        private var lastSize = IntSize.Zero
        private var activePointer: PointerId? = null
        private var downPosition = Offset.Zero
        private var lastPosition = Offset.Zero
        private var downTime = 0L

        private fun finishTouch(cancelled: Boolean = false) {
            if (!touched) return
            val delta = lastPosition - downPosition
            val dx = delta.x.toInt()
            val dy = delta.y.toInt()
            val click = SystemClock.uptimeMillis() - downTime < ViewConfiguration.getLongPressTimeout() &&
                sqrt((dx.toDouble() * dx + dy.toDouble() * dy)).toInt() < 10
            touched = false
            activePointer = null
            // dispatchTouchEvent 只在真实 UP 把 scrollType=3 改为 2；CANCEL 保留映射，
            // 但 springBack 的起点仍取当前可见位置，不沿用上次积分器的 raw position。
            if (!cancelled) overflowFling = false
            // COUIRecyclerView.dispatchTouchEvent：越界的 UP/CANCEL 排除短点按，随后 springBack。
            if (translation != 0f && !click)
                view.performCouiHapticFeedback(CouiHapticFeedback.ScrollEdge, hapticFeedbackEnabled)
            if (fling == null && translation != 0f) rebound(overFling = overflowFling)
        }
        override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
            val child = measurable.measure(constraints)
            return layout(child.width, child.height) { child.placeWithLayer(0, translation.toInt()) }
        }
        override fun onRemeasured(size: IntSize) {
            if (size == lastSize) return
            // 原厂 onSizeChanged 只更新惯性越界映射高度；拖动距离保持初始化值。
            mappingScreenHeight = view.resources.displayMetrics.heightPixels.coerceAtLeast(1)
            lastSize = size
        }
        override fun onPointerEvent(pointerEvent: PointerEvent, pass: PointerEventPass, bounds: IntSize) {
            if (pass != PointerEventPass.Initial) return
            val changes = pointerEvent.changes
            val down = if (!touched) changes.firstOrNull { it.pressed && !it.previousPressed } else null
            if (down != null) {
                touched = true; stop()
                downTime = down.uptimeMillis
            }
            // Settings 屏蔽 POINTER_DOWN；首次之后仅活动指针抬起才换点击基准，计时不重置。
            val next = down ?: if (changes.any { it.id == activePointer && !it.pressed }) {
                changes.firstOrNull { it.pressed }
            } else null
            if (next != null) {
                activePointer = next.id
                downPosition = Offset((next.position.x + .5f).toInt().toFloat(),
                    (next.position.y + .5f).toInt().toFloat())
            }
            // isClickEvent 使用事件首指；抬指后只保留仍按下的首指，供无事件的取消回调使用。
            (changes.firstOrNull { it.pressed } ?: changes.firstOrNull())?.let { lastPosition = it.position }
            if (changes.none { it.pressed }) {
                finishTouch(cancelled = pointerEvent.isCouiTouchCancelled())
            }
        }
        override fun onCancelPointerInput() { finishTouch(cancelled = true) }
        override fun onDetach() { touched = false; activePointer = null; stop(); translation = 0f }
    }
}

/** 记住容器独占的回弹；空页和短页通过 couiVerticalScroll 接收完整输入。 */
@Composable
fun rememberCouiOverscrollEffect(hapticFeedbackEnabled: Boolean = true): CouiOverscrollEffect {
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val refresh = checkNotNull(view.display).refreshRate
    require(refresh.isFinite() && refresh > 0f)
    val effect = remember(scope, refresh, view, hapticFeedbackEnabled) {
        val height = view.resources.displayMetrics.heightPixels.coerceAtLeast(1)
        CouiOverscrollEffect(scope, height, 1f / refresh, view, hapticFeedbackEnabled)
    }
    DisposableEffect(effect) { onDispose { effect.dispose() } }
    return effect
}


/**
 * 二级页纵向滚动入口；空页和短页同样有 COUI 回弹，不安装平台 OverscrollEffect。
 * state 持有真实位置、范围、滚动互斥及 isScrollInProgress；拖动通知由 interactionSource 提供，
 * 不发送到 state.interactionSource。焦点滚动与视口变化继续由标准 verticalScroll 处理。
 */
@Composable
fun Modifier.couiVerticalScroll(
    state: ScrollState,
    enabled: Boolean = true,
    overscrollEffect: CouiOverscrollEffect? = rememberCouiOverscrollEffect(),
    interactionSource: MutableInteractionSource? = null,
): Modifier {
    val visual = if (enabled && overscrollEffect != null) Modifier.overscroll(overscrollEffect) else Modifier
    val flingBehavior = rememberCouiScrollFlingBehavior(overscrollEffect)
    // Foundation 的 canScroll=true 允许分发但不保证消费；边界运动在零内容范围时仍可消费。
    // 只开放输入门控，其余操作直接委托真实 state，共用同一互斥，不另建滚动状态。
    val inputState = remember(state) {
        object : ScrollableState by state {
            override val canScrollForward = true
            override val canScrollBackward = true
        }
    }
    return then(visual).scrollable(inputState, Orientation.Vertical,
        overscrollEffect = overscrollEffect, enabled = enabled, reverseDirection = true,
        flingBehavior = flingBehavior, interactionSource = interactionSource,
        bringIntoViewSpec = InputOnlyBringIntoViewSpec)
        .verticalScroll(state, flingBehavior = flingBehavior, overscrollEffect = null, enabled = false)
}

// 内侧 verticalScroll 持有唯一的焦点/视口自动滚动，外侧只承接输入与标准滚动动作。
private object InputOnlyBringIntoViewSpec : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = 0f
}
