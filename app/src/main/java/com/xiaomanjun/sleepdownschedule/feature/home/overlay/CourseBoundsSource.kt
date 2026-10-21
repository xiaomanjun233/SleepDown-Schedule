package com.xiaomanjun.sleepdownschedule.feature.home.overlay

import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer

@Suppress("UNUSED_PARAMETER")
@Composable
internal fun CourseBoundsSource(
    courseId: Long,
    visible: Boolean,
    sharedScope: SharedTransitionScope?,
    shape: Shape,
    modifier: Modifier = Modifier,
    content: @Composable (Modifier) -> Unit
) {
    content(modifier.then(if (visible) Modifier else Modifier.graphicsLayer { alpha = 0f }))
}
