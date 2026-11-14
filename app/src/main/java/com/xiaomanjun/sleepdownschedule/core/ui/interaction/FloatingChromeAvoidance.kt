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

val LocalFloatingChromeBounds = staticCompositionLocalOf<State<Rect?>> { mutableStateOf(null) }

/** Measure toolbar content after the system insets have been applied. */
@Composable
internal fun Modifier.avoidFloatingChrome(): Modifier {
    val chrome = LocalFloatingChromeBounds.current.value
    var toolbar by remember { mutableStateOf<Rect?>(null) }
    val bounds = toolbar
    val density = LocalDensity.current
    val inset = if (chrome != null && bounds != null &&
        chrome.left < bounds.right && chrome.right > bounds.left
    ) {
        (with(density) { (chrome.bottom - bounds.top).toDp() } + 4.dp).coerceAtLeast(0.dp)
    } else 0.dp
    return this.onGloballyPositioned { toolbar = it.boundsInRoot() }.padding(top = inset)
}
