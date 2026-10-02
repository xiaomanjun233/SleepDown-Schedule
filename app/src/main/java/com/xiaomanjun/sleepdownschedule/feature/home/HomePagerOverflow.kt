package com.xiaomanjun.sleepdownschedule.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal val LocalHomePagerStartOverflow = compositionLocalOf { 0.dp }

/** Enlarge the scroll viewport, then pad its pages by the same amount to keep their layout. */
@Composable
internal fun Modifier.homeSidebarOverflowViewport(): Modifier {
    val overflow = LocalHomePagerStartOverflow.current
    if (overflow <= 0.dp) return this
    return layout { measurable, constraints ->
        val extra = overflow.roundToPx()
        val child = measurable.measure(constraints.copy(
            minWidth = constraints.minWidth + extra, maxWidth = constraints.maxWidth + extra))
        layout(child.width - extra, child.height) { child.placeRelative(-extra, 0) }
    }
}

@Composable
internal fun Modifier.homeSidebarOverflowClip(): Modifier {
    val overflow: Dp = LocalHomePagerStartOverflow.current
    return drawWithContent {
        clipRect(left = -overflow.toPx(), top = 0f, right = size.width, bottom = size.height) { this@drawWithContent.drawContent() }
    }
}
