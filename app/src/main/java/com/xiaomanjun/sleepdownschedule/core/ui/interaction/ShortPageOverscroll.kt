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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.itos.coui.scroll.CouiOverscrollEffect
import com.itos.coui.scroll.couiVerticalScroll
import com.itos.coui.scroll.rememberCouiOverscrollEffect

/** Keep the page's usual scrolling when it has range, and provide COUI pull feedback when it fits. */
@Composable
internal fun Modifier.pageVerticalScroll(state: ScrollState, enabled: Boolean = true): Modifier =
    if (enabled && !state.canScrollForward && !state.canScrollBackward) couiVerticalScroll(state)
    else verticalScroll(state, enabled = enabled)

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
    content: LazyListScope.() -> Unit,
) {
    val short = userScrollEnabled && overscrollEffect != null && !state.canScrollForward && !state.canScrollBackward
    val edge = if (short) overscrollEffect as? CouiOverscrollEffect ?: rememberCouiOverscrollEffect() else null
    val input = if (edge != null) {
        val edgeState = remember(state) {
            object : ScrollableState by state {
                override val canScrollForward = true
                override val canScrollBackward = true
            }
        }
        Modifier.overscroll(edge).scrollable(edgeState, Orientation.Vertical, overscrollEffect = edge,
            reverseDirection = true)
    } else Modifier
    LazyColumn(
        modifier = modifier.then(input), state = state, contentPadding = contentPadding,
        reverseLayout = reverseLayout, verticalArrangement = verticalArrangement,
        horizontalAlignment = horizontalAlignment, flingBehavior = flingBehavior,
        userScrollEnabled = userScrollEnabled && !short, overscrollEffect = if (short) null else overscrollEffect,
        content = content,
    )
}
