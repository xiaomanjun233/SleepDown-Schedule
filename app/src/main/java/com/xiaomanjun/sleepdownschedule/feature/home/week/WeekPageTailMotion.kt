package com.xiaomanjun.sleepdownschedule.feature.home.week

import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.xiaomanjun.sleepdownschedule.feature.home.LocalHomeTextContrastFrozen
import com.xiaomanjun.sleepdownschedule.glass.LocalGlassCoordinatesFrozen
import com.xiaomanjun.sleepdownschedule.glass.LocalGlassSampleRecordKey
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs

internal const val WeekTailGroups = 6

/** A bounded time history: followers keep advancing even when the finger stops halfway. */
internal class WeekTailTimeline(initialPosition: Float) {
    private data class Sample(val time: Long, val position: Float)
    private val history = ArrayDeque<Sample>()
    private var lastTarget = initialPosition
    private var settled = true

    fun advance(timeNanos: Long, target: Float, anchor: Int, durationScale: Float): List<Float> {
        val delayNanos = (24_000_000L * durationScale.coerceAtLeast(0f)).toLong()
        val longestDelay = delayNanos * (WeekTailGroups - 1)
        if (delayNanos == 0L || abs(target - lastTarget) > 1.25f) {
            history.clear()
            lastTarget = target
            settled = true
            return List(WeekTailGroups) { target }
        }
        if (history.isEmpty() || settled && timeNanos - history.last().time > 32_000_000L) {
            history.clear()
            history.addLast(Sample(timeNanos - 16_666_667L, lastTarget))
        }
        history.addLast(Sample(timeNanos, target))
        lastTarget = target
        while (history.size > 2 && history[1].time <= timeNanos - longestDelay) history.removeFirst()
        val positions = List(WeekTailGroups) { group ->
            val delayedTime = timeNanos - abs(group - anchor.coerceIn(0, WeekTailGroups - 1)) * delayNanos
            var previous = history.first()
            if (delayedTime <= previous.time) previous.position else {
                var value = target
                for (index in 1 until history.size) {
                    val next = history[index]
                    if (next.time >= delayedTime) {
                        val fraction = (delayedTime - previous.time).toFloat() /
                            (next.time - previous.time).coerceAtLeast(1L)
                        value = previous.position + (next.position - previous.position) * fraction
                        break
                    }
                    previous = next
                }
                value
            }
        }
        settled = positions.all { abs(it - target) < 0.00001f }
        return positions
    }
}

@Stable
internal class WeekPageTailMotion(val pager: PagerState) {
    private val initial = pager.currentPage + pager.currentPageOffsetFraction
    private val timeline = WeekTailTimeline(initial)
    private var positions by mutableStateOf(List(WeekTailGroups) { initial })
    private var following by mutableStateOf(false)
    private var anchor by mutableIntStateOf(0)
    private var rootTop = 0f
    private var rootHeight = 1f
    val position: Float get() = pager.currentPage + pager.currentPageOffsetFraction
    val moving: Boolean get() = pager.isScrollInProgress || following
    val sampleKey: Any by derivedStateOf { Triple(position, positions, anchor) }

    fun setViewport(top: Float, height: Float) { rootTop = top; rootHeight = height.coerceAtLeast(1f) }
    fun touch(y: Float) { anchor = groupForRootY(rootTop + y) }
    fun leadFromTop() { anchor = 0 }
    fun groupForRootY(y: Float): Int = (y / rootHeight * WeekTailGroups).toInt().coerceIn(0, WeekTailGroups - 1)
    fun offset(group: Int): Float = if (group == anchor) 0f else position - positions[group.coerceIn(0, WeekTailGroups - 1)]
    fun pageVisible(page: Int): Boolean =
        page > minOf(position, positions.min()) - 1f && page < maxOf(position, positions.max()) + 1f

    suspend fun follow(scale: () -> Float) = coroutineScope {
        val changes = Channel<Unit>(Channel.CONFLATED)
        launch {
            snapshotFlow { position }.collect { changes.send(Unit) }
        }
        for (change in changes) {
            do {
                withFrameNanos { time ->
                    positions = timeline.advance(time, position, anchor, scale())
                    following = positions.any { abs(it - position) > 0.00001f }
                }
            } while (following)
        }
    }
}

@Composable
internal fun rememberWeekPageTailMotion(pager: PagerState): WeekPageTailMotion {
    val motion = remember(pager) { WeekPageTailMotion(pager) }
    LaunchedEffect(motion) {
        val scale = coroutineContext[MotionDurationScale]
        motion.follow { scale?.scaleFactor ?: 1f }
    }
    return motion
}

internal val LocalWeekPageTail = staticCompositionLocalOf<WeekPageTailMotion?> { null }

/** Observes the original pointer without stealing horizontal paging, vertical scroll or editing. */
internal fun Modifier.weekTailTouchAnchor(motion: WeekPageTailMotion): Modifier =
    onGloballyPositioned {
        motion.setViewport(it.localToRoot(Offset.Zero).y, it.findRootCoordinates().size.height.toFloat())
    }.pointerInput(motion) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                event.changes.firstOrNull { it.pressed && !it.previousPressed }?.let { motion.touch(it.position.y) }
            }
        }
    }

@Composable
internal fun Modifier.weekPageTail(): Modifier {
    val motion = LocalWeekPageTail.current ?: return this
    val group = remember { mutableIntStateOf(-1) }
    val direction = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1f else 1f
    return onGloballyPositioned {
        if (group.intValue < 0 || !motion.moving) {
            group.intValue = motion.groupForRootY(it.localToRoot(Offset.Zero).y)
        }
    }.graphicsLayer {
        translationX = direction * motion.pager.layoutInfo.pageSize * motion.offset(group.intValue)
    }
}

@Composable
internal fun WeekPageSamplingScope(
    motion: WeekPageTailMotion,
    page: Int,
    homeSwitching: Boolean,
    content: @Composable () -> Unit
) {
    val parentFrozen = LocalGlassCoordinatesFrozen.current
    val parentKey = LocalGlassSampleRecordKey.current
    val hiddenKey = remember(page) { Any() }
    // Retain the already-created adjacent page materials, but do not re-record invisible samples.
    // The tail's complete position envelope keeps a trailing page live until it actually leaves.
    val visible = remember(motion, page, homeSwitching) {
        { if (homeSwitching) page == motion.pager.settledPage else motion.pageVisible(page) }
    }
    val frozen = remember(visible, parentFrozen) { { !visible() || parentFrozen() } }
    val key = remember(visible, motion, parentKey) {
        derivedStateOf { if (visible()) Pair(parentKey(), motion.sampleKey) else hiddenKey }
    }
    val sampleKey = remember(key) { { key.value } }
    CompositionLocalProvider(
        LocalGlassCoordinatesFrozen provides frozen,
        LocalGlassSampleRecordKey provides sampleKey,
        LocalHomeTextContrastFrozen provides (LocalHomeTextContrastFrozen.current || motion.moving)
    ) {
        // Keep adjacent composition warm, but never draw its cards during a home/settings rebound.
        Box(Modifier.drawWithContent { if (visible()) drawContent() }) { content() }
    }
}
