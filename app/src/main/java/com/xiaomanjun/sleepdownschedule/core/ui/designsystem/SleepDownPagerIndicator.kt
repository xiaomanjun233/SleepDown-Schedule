package com.xiaomanjun.sleepdownschedule.core.ui.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.dp
import com.kyant.shapes.Capsule
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.Channel
import kotlin.math.floor
import kotlin.math.roundToInt

internal data class NativePagerIndicatorRequest(
    val pageCount: Int,
    val position: () -> Float,
    val selectedPage: () -> Int,
    val onSelect: (Int) -> Unit,
    val enabled: Boolean,
    val modifier: Modifier,
    val onScrub: ((Float, Boolean, Boolean) -> Unit)? = null
)

internal val LocalNativePagerIndicatorRenderer = compositionLocalOf<
    (@Composable (NativePagerIndicatorRequest) -> Boolean)?
> { null }

@Composable
internal fun SleepDownPagerIndicator(
    pagerState: PagerState,
    pageCount: Int,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val scope = rememberCoroutineScope()
    val scrubEvents = remember(pagerState) { Channel<IndicatorScrub>(Channel.CONFLATED) }
    LaunchedEffect(pagerState) {
        for (first in scrubEvents) {
            if (!first.active) {
                if (!first.cancelled) pagerState.animateScrollToPage(first.position.roundToInt()
                    .coerceIn(0, (pagerState.pageCount - 1).coerceAtLeast(0)))
                continue
            }
            val originalPage = pagerState.settledPage
            var event = first
            pagerState.scroll(MutatePriority.UserInput) {
                while (event.active) {
                    val step = pagerState.layoutInfo.pageSize + pagerState.layoutInfo.pageSpacing
                    val current = pagerState.currentPage + pagerState.currentPageOffsetFraction
                    scrollBy((event.position - current) * step)
                    event = scrubEvents.receive()
                }
            }
            val target = if (event.cancelled) originalPage else event.position.roundToInt()
            pagerState.animateScrollToPage(target.coerceIn(0, (pagerState.pageCount - 1).coerceAtLeast(0)))
        }
    }
    SleepDownPageIndicator(pageCount,
        position = { pagerState.currentPage + pagerState.currentPageOffsetFraction },
        selectedPage = { pagerState.settledPage },
        onSelect = { if (enabled) scope.launch { pagerState.animateScrollToPage(it) } },
        modifier = modifier, enabled = enabled,
        onScrub = { position, active, cancelled -> scrubEvents.trySend(IndicatorScrub(position, active, cancelled)) })
}

private data class IndicatorScrub(val position: Float, val active: Boolean, val cancelled: Boolean)

/** All finite page indicators share native motion, direct selection and adjustable semantics. */
@Composable
internal fun SleepDownPageIndicator(
    pageCount: Int,
    position: () -> Float,
    selectedPage: () -> Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onScrub: ((Float, Boolean, Boolean) -> Unit)? = null
) {
    if (pageCount <= 1) return
    val latestOnSelect = rememberUpdatedState(onSelect)
    val accessible = modifier.semantics {
        progressBarRangeInfo = ProgressBarRangeInfo(selectedPage().toFloat(), 0f..(pageCount - 1).toFloat(), pageCount - 2)
        setProgress { value ->
            if (enabled) onSelect(value.roundToInt().coerceIn(0, pageCount - 1))
            enabled
        }
    }
    val renderer = LocalNativePagerIndicatorRenderer.current
    if (renderer?.invoke(NativePagerIndicatorRequest(pageCount, position, selectedPage, onSelect, enabled, accessible, onScrub)) == true) return
    Canvas(accessible.width((24 + (pageCount - 1) * 15).dp).height(32.dp)
        .background(Color.Black.copy(alpha = 0.22f), Capsule())
        .pointerInput(pageCount, enabled) {
            detectTapGestures { point ->
                if (enabled) latestOnSelect.value(((point.x - 12.dp.toPx()) / 15.dp.toPx()).roundToInt().coerceIn(0, pageCount - 1))
            }
        }) {
        repeat(pageCount) { page ->
            drawCircle(Color.White.copy(alpha = 0.30f), 3.dp.toPx(),
                Offset(12.dp.toPx() + page * 15.dp.toPx(), size.height / 2f))
        }
        val page = position().coerceIn(0f, (pageCount - 1).toFloat())
        drawCircle(Color.White, 4.dp.toPx(), Offset(12.dp.toPx() + page * 15.dp.toPx(), size.height / 2f))
    }
}
