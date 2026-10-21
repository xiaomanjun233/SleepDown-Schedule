package com.xiaomanjun.sleepdownschedule.core.ui.interaction

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned

internal class PullGestureExclusions {
    internal val controls = mutableMapOf<Any, LayoutCoordinates>()

    fun canStartAt(position: Offset): Boolean = controls.values.none {
        it.isAttached && it.boundsInRoot().contains(position)
    }
}

internal val LocalPullGestureExclusions = staticCompositionLocalOf<PullGestureExclusions?> { null }

internal fun Modifier.excludeParentPullGesture(): Modifier = composed {
    val exclusions = LocalPullGestureExclusions.current ?: return@composed this
    val key = remember { Any() }
    DisposableEffect(exclusions, key) { onDispose { exclusions.controls.remove(key) } }
    onGloballyPositioned { exclusions.controls[key] = it }
}
