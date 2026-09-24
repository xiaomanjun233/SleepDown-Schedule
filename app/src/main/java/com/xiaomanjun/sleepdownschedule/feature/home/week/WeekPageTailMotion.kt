package com.xiaomanjun.sleepdownschedule.feature.home.week

import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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
import com.xiaomanjun.sleepdownschedule.feature.home.LocalHomePaneVisible
import com.xiaomanjun.sleepdownschedule.glass.LocalGlassSampleRecordKey
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlin.math.abs

internal const val WeekTailRows = 6
internal const val WeekTailColumns = 6
internal const val WeekTailGroups = WeekTailRows * WeekTailColumns

internal fun weekPageIntersectsTail(page: Int, position: Float, followers: List<Float>): Boolean =
    page > minOf(position, followers.min()) - 1f &&
        page < maxOf(position, followers.max()) + 1f

internal fun weekTailGroup(row: Int, column: Int): Int =
    row.coerceIn(0, WeekTailRows - 1) * WeekTailColumns +
        column.coerceIn(0, WeekTailColumns - 1)

internal fun weekTailGroupForCard(
    screenX: Float,
    screenY: Float,
    cardOrderFraction: Float?,
    columnOrderFraction: Float?
): Int {
    // Cards with the same top edge must share a row even if their heights or order differ.
    // Mixing global card order into this coordinate changed their horizontal spacing.
    val rowOnScreen = if (screenY.isFinite()) screenY.coerceIn(0f, 1f)
        else cardOrderFraction?.coerceIn(0f, 1f) ?: 0.5f
    val columnOnScreen = screenX.coerceIn(0f, 1f)
    val column = columnOrderFraction?.let {
        it.coerceIn(0f, 1f) * 0.6f + columnOnScreen * 0.4f
    } ?: columnOnScreen
    return weekTailGroup((rowOnScreen * WeekTailRows).toInt(), (column * WeekTailColumns).toInt())
}

/** A bounded time history: followers keep advancing even when the finger stops halfway. */
internal class WeekTailTimeline(initialPosition: Float) {
    private data class Sample(val time: Long, val position: Float)
    private val history = ArrayDeque<Sample>()
    private var lastTarget = initialPosition
    private var settled = true

    fun snapTo(position: Float): List<Float> {
        history.clear()
        lastTarget = position
        settled = true
        return List(WeekTailGroups) { position }
    }

    fun advance(timeNanos: Long, target: Float, anchor: Int, durationScale: Float): List<Float> {
        val rowDelayNanos = (28_000_000L * durationScale.coerceAtLeast(0f)).toLong()
        // Horizontal column delays pull cards in one row apart. Keep the 6×6 card grouping for
        // hit position and anchoring, but move each row as one unit during a horizontal swipe.
        val longestDelay = rowDelayNanos * (WeekTailRows - 1)
        if (rowDelayNanos == 0L || abs(target - lastTarget) > 1.25f) {
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
        val anchorGroup = anchor.coerceIn(0, WeekTailGroups - 1)
        val rowPositions = FloatArray(WeekTailRows) { row ->
            val rowDistance = abs(row - anchorGroup / WeekTailColumns)
            val delayedTime = timeNanos - rowDistance * rowDelayNanos
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
        val positions = List(WeekTailGroups) { group -> rowPositions[group / WeekTailColumns] }
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
    private var settledSampleRevision by mutableIntStateOf(0)
    private var rootLeft = 0f
    private var rootTop = 0f
    private var rootWidth = 1f
    private var rootHeight = 1f
    val position: Float get() = pager.currentPage + pager.currentPageOffsetFraction
    val moving: Boolean get() = pager.isScrollInProgress || following
    val sampleKey: Any by derivedStateOf {
        Pair(Triple(position, positions, anchor), settledSampleRevision)
    }

    fun setViewport(left: Float, top: Float, width: Float, height: Float) {
        rootLeft = left
        rootTop = top
        rootWidth = width.coerceAtLeast(1f)
        rootHeight = height.coerceAtLeast(1f)
    }
    fun touch(x: Float, y: Float) {
        anchor = groupForRootPosition(rootLeft + x, rootTop + y)
    }
    fun leadFromTop() { anchor = weekTailGroup(0, 0) }
    fun snapTo(position: Float) {
        positions = timeline.snapTo(position)
        following = false
    }
    suspend fun awaitSettled() {
        snapshotFlow {
            !pager.isScrollInProgress && positions.all { abs(it - position) < 0.00001f }
        }.first { it }
    }
    fun groupForRootPosition(
        x: Float,
        y: Float,
        cardOrderFraction: Float? = null,
        columnOrderFraction: Float? = null
    ): Int {
        return weekTailGroupForCard(
            x / rootWidth, y / rootHeight, cardOrderFraction, columnOrderFraction
        )
    }
    fun offset(group: Int): Float = if (group == anchor) 0f else position - positions[group.coerceIn(0, WeekTailGroups - 1)]
    fun pageVisible(page: Int): Boolean =
        weekPageIntersectsTail(page, position, positions)

    suspend fun follow(scale: () -> Float) = coroutineScope {
        val changes = Channel<Unit>(Channel.CONFLATED)
        launch {
            snapshotFlow { position to pager.isScrollInProgress }.collect { changes.send(Unit) }
        }
        var lastObservedPosition = position
        var needsSettledSample = false
        for (change in changes) {
            val target = position
            if (pager.isScrollInProgress || target != lastObservedPosition) needsSettledSample = true
            lastObservedPosition = target
            do {
                withFrameNanos { time ->
                    positions = timeline.advance(time, position, anchor, scale())
                    following = positions.any { abs(it - position) > 0.00001f }
                }
            } while (following)
            if (needsSettledSample && !pager.isScrollInProgress) {
                // A fast swipe can finish placement after the last follower's recording.
                // Refresh once on the following frame at the final layout coordinates.
                withFrameNanos { }
                settledSampleRevision++
                needsSettledSample = false
            }
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
private val LocalWeekPageSlot = staticCompositionLocalOf<Int?> { null }

/** Observes the original pointer without stealing horizontal paging, vertical scroll or editing. */
internal fun Modifier.weekTailTouchAnchor(motion: WeekPageTailMotion): Modifier =
    onGloballyPositioned {
        val origin = it.localToRoot(Offset.Zero)
        val root = it.findRootCoordinates().size
        motion.setViewport(origin.x, origin.y, root.width.toFloat(), root.height.toFloat())
    }.pointerInput(motion) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                event.changes.firstOrNull { it.pressed && !it.previousPressed }?.let {
                    motion.touch(it.position.x, it.position.y)
                }
            }
        }
    }

@Composable
internal fun Modifier.weekPageTail(
    cardOrderFraction: Float? = null,
    columnOrderFraction: Float? = null
): Modifier {
    val motion = LocalWeekPageTail.current ?: return this
    val pageSlot = LocalWeekPageSlot.current
    val group = remember { mutableIntStateOf(-1) }
    val direction = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1f else 1f
    val positionTracker = onGloballyPositioned {
        if (group.intValue < 0 || !motion.moving) {
            val topCenter = it.localToRoot(Offset(it.size.width / 2f, 0f))
            // Prefetched pages sit offscreen; classify each card where it rests in the grid.
            val restingX = topCenter.x - (pageSlot?.let { slot ->
                direction * (slot - motion.position) * motion.pager.layoutInfo.pageSize
            } ?: 0f)
            group.intValue = motion.groupForRootPosition(
                restingX, topCenter.y, cardOrderFraction, columnOrderFraction
            )
        }
    }
    // An idle zero-translation layer changes the clipping stack of every glass card.
    return if (motion.moving) positionTracker.graphicsLayer {
        translationX = direction * motion.pager.layoutInfo.pageSize * motion.offset(group.intValue)
    } else positionTracker
}

@Composable
internal fun WeekPageSamplingScope(
    motion: WeekPageTailMotion,
    page: Int,
    homeSwitching: Boolean,
    jump: AdjacentWeekJump? = null,
    content: @Composable () -> Unit
) {
    val parentKey = LocalGlassSampleRecordKey.current
    val paneVisible = LocalHomePaneVisible.current
    // An idle retained pane keeps only its settled page measured and composed. The outer pane
    // suppresses its drawing/sampling, so switching modes can reuse these glass nodes.
    // While visible, release other weeks only after their last tail group leaves.
    val mounted by remember(motion, page, homeSwitching, jump, paneVisible) {
        derivedStateOf {
            if (!paneVisible) page == motion.pager.settledPage else
                (jump == null || jump.contains(page)) &&
                    (if (homeSwitching) page == motion.pager.settledPage else
                        jump?.targetPage == page || motion.pageVisible(page))
        }
    }
    if (!mounted) return
    val mountedSampleRevision = remember(page) { mutableIntStateOf(0) }
    LaunchedEffect(page) {
        // The first visible frame can precede the shared wallpaper recorder. Refresh once
        // after its layer and this page have both reached a stable placement.
        withFrameNanos { }
        withFrameNanos { }
        mountedSampleRevision.intValue++
    }
    val key = remember(motion, parentKey) {
        derivedStateOf { Triple(parentKey(), motion.sampleKey, mountedSampleRevision.intValue) }
    }
    val sampleKey = remember(key) { { key.value } }
    CompositionLocalProvider(
        LocalWeekPageSlot provides page,
        LocalGlassSampleRecordKey provides sampleKey,
        LocalHomeTextContrastFrozen provides (LocalHomeTextContrastFrozen.current || motion.moving)
    ) {
        content()
    }
}
