package com.xiaomanjun.sleepdownschedule.core.ui.interaction

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp

val LocalFloatingChromeBounds = staticCompositionLocalOf<State<Rect?>> { mutableStateOf(null) }

/** Reserve space only in a toolbar whose actual horizontal span intersects floating chrome. */
@Composable
fun Modifier.avoidFloatingChrome(centerContentWidth: Dp, startContentWidth: Dp = 0.dp): Modifier {
    val chrome = LocalFloatingChromeBounds.current.value
    var toolbar by remember { mutableStateOf<Rect?>(null) }
    val bounds = toolbar
    val density = LocalDensity.current
    val overlaps = if (chrome != null && bounds != null) with(density) {
        val width = centerContentWidth.toPx().coerceAtMost(bounds.width)
        chrome.overlaps(Rect(bounds.center.x - width / 2, bounds.top, bounds.center.x + width / 2, bounds.bottom)) ||
            (startContentWidth > 0.dp && chrome.overlaps(Rect(bounds.left, bounds.top,
                bounds.left + startContentWidth.toPx(), bounds.bottom)))
    } else false
    val inset = if (overlaps && chrome != null && bounds != null) {
        with(density) { (chrome.bottom - bounds.top).coerceAtLeast(0f).toDp() } + 4.dp
    } else 0.dp
    return this.onGloballyPositioned { toolbar = it.boundsInRoot() }.padding(top = inset)
}
