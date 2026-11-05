package com.itos.coui.picker

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateTo
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.TargetedFlingBehavior
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs
import kotlin.math.floor

/** Calendar 原厂 COUICalendarViewPagerScroller：300ms，COUIMoveEaseInterpolator。 */
internal val CouiCalendarPageEasing = CubicBezierEasing(.3f, 0f, .1f, 1f)

/** Calendar 原厂 ViewPager 的翻页目标判定与 300ms 收尾。 */
internal class CouiCalendarFlingBehavior(
    private val pager: PagerState,
    private val density: Float,
    private val startPage: () -> Int,
) : TargetedFlingBehavior {
    override suspend fun ScrollScope.performFling(initialVelocity: Float,
        onRemainingDistanceUpdated: (Float) -> Unit): Float {
        val width = pager.layoutInfo.pageSize + pager.layoutInfo.pageSpacing
        if (width <= 0 || pager.pageCount <= 0) return 0f
        val position = pager.currentPage + pager.currentPageOffsetFraction
        val base = floor(position).toInt()
        val offset = position - base
        val initialPage = startPage()
        val moved = abs(position - initialPage) * width
        val fling = moved > 25f * density && abs(initialVelocity) > 400f * density
        // 原厂 ViewPager.determineTargetPage：慢拖的 0.4/0.6 偏置以 DOWN 时页号为准。
        // Compose 的 scroll 速度正向表示页号增加，对应原厂负的触摸 X 速度。
        val target = (if (fling) {
            if (initialVelocity > 0f) base + 1 else base
        } else {
            (base + offset + if (base >= initialPage) .4f else .6f).toInt()
        }).coerceIn(0, pager.pageCount - 1)
        val distance = (target - position) * width
        var previous = 0f
        onRemainingDistanceUpdated(distance)
        AnimationState(0f, initialVelocity).animateTo(distance, tween(300, easing = CouiCalendarPageEasing)) {
            val delta = value - previous
            previous = value
            val consumed = scrollBy(delta)
            onRemainingDistanceUpdated(distance - value)
            if (abs(delta - consumed) > .5f) cancelAnimation()
        }
        return 0f
    }
}

/** 月历选中动画采用单一 owner，并将日期、旧圆及进度切换作为一次快照提交。 */
internal class CouiCalendarSelectionMotion(
    private val scope: CoroutineScope,
    initial: LocalDate,
) {
    var selectedDate by mutableStateOf(initial)
        private set
    var oldDate by mutableStateOf<LocalDate?>(null)
        private set
    var enter by mutableFloatStateOf(1f)
        private set
    var exit by mutableFloatStateOf(0f)
        private set
    private var job: Job? = null
    private var generation = 0L

    /** 只有被接受的网格点击和返回网格才动画；旧圆限于同一月份。 */
    fun update(date: LocalDate, animate: Boolean) {
        val currentGeneration = ++generation
        job?.cancel()
        job = null
        val previous = selectedDate
        val outgoing = previous.takeIf {
            animate && it != date && YearMonth.from(it) == YearMonth.from(date)
        }
        // 不能让新一轮 exit=1 与前一轮 oldDate 同时暴露给 Compose。
        Snapshot.withMutableSnapshot {
            oldDate = outgoing
            selectedDate = date
            enter = if (animate) 0f else 1f
            exit = if (outgoing != null) 1f else 0f
        }
        if (!animate) return
        job = scope.launch {
            launch {
                Animatable(0f).animateTo(1f, tween(280, easing = CouiCalendarPageEasing)) {
                    if (generation == currentGeneration) enter = value
                }
            }
            if (outgoing != null) launch {
                Animatable(1f).animateTo(0f, tween(150, easing = CouiCalendarExitEasing)) {
                    if (generation == currentGeneration) exit = value
                }
                if (generation == currentGeneration) {
                    Snapshot.withMutableSnapshot {
                        exit = 0f
                        oldDate = null
                    }
                }
            }
        }
    }

    fun dispose() {
        generation++
        job?.cancel()
        job = null
    }
}

internal val CouiCalendarExitEasing = CubicBezierEasing(.33f, 0f, .67f, 1f)
