package com.xiaomanjun.sleepdownschedule.core.ui.interaction

import androidx.compose.foundation.OverscrollEffect
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.overscroll
import androidx.compose.foundation.rememberOverscrollEffect
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.scrollToIndex
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.itos.coui.scroll.couiVerticalScroll
import kotlinx.coroutines.launch

/** Keep the page's usual scrolling when it has range, and provide COUI pull feedback when it fits. */
@Composable
internal fun Modifier.pageVerticalScroll(state: ScrollState, enabled: Boolean = true): Modifier =
    if (enabled && !state.canScrollForward && !state.canScrollBackward) couiVerticalScroll(state)
    else verticalScroll(state, enabled = enabled)

/**
 * Keep one gesture owner as the header, insets, or content changes the scroll range.
 * Foundation dispatches the input to overscroll even at zero range; the real state
 * still decides what it can consume and owns the scroll mutation and item positions.
 */
@Composable
internal fun Modifier.listViewportInput(
    state: ScrollableState,
    edge: OverscrollEffect,
    itemCount: () -> Int,
    scrollTo: suspend (Int) -> Unit,
    enabled: Boolean = true,
    reverseDirection: Boolean = true,
    flingBehavior: FlingBehavior = ScrollableDefaults.flingBehavior(),
): Modifier {
    val edgeState = remember(state) {
        object : ScrollableState by state {
            override val canScrollForward = true
            override val canScrollBackward = true
        }
    }
    val scope = rememberCoroutineScope()
    return overscroll(edge).scrollable(
        edgeState, Orientation.Vertical, overscrollEffect = edge, enabled = enabled,
        reverseDirection = reverseDirection, flingBehavior = flingBehavior,
    ).then(if (enabled) Modifier.semantics {
        scrollToIndex { index ->
            if (index !in 0 until itemCount()) false
            else { scope.launch { scrollTo(index) }; true }
        }
    } else Modifier)
}

@Composable
internal fun SleepDownLazyColumn(
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    reverseLayout: Boolean = false,
    verticalArrangement: Arrangement.Vertical = if (reverseLayout) Arrangement.Bottom else Arrangement.Top,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    flingBehavior: FlingBehavior = ScrollableDefaults.flingBehavior(),
    userScrollEnabled: Boolean = true,
    overscrollEffect: OverscrollEffect? = rememberOverscrollEffect(),
    onScrollToTop: (suspend () -> Unit)? = null,
    content: LazyListScope.() -> Unit,
) {
    val input = if (overscrollEffect != null) Modifier.listViewportInput(
        state, overscrollEffect, itemCount = { state.layoutInfo.totalItemsCount },
        scrollTo = { state.scrollToItem(it) }, enabled = userScrollEnabled,
        reverseDirection = !reverseLayout, flingBehavior = flingBehavior,
    ) else Modifier
    val scrollToTop = if (userScrollEnabled && !reverseLayout) Modifier.animateScrollToTop {
        if (onScrollToTop != null) onScrollToTop()
        else if (state.canScrollBackward) state.animateScrollToItem(0)
    } else Modifier
    LazyColumn(
        modifier = modifier.then(scrollToTop).then(input), state = state, contentPadding = contentPadding,
        reverseLayout = reverseLayout, verticalArrangement = verticalArrangement,
        horizontalAlignment = horizontalAlignment, flingBehavior = flingBehavior,
        userScrollEnabled = userScrollEnabled && overscrollEffect == null, overscrollEffect = null,
        content = content,
    )
}
