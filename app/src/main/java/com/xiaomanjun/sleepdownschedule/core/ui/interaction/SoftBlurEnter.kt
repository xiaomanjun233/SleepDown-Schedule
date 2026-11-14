package com.xiaomanjun.sleepdownschedule.core.ui.interaction

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/** A control enters once per identity; updates and press feedback retain their own motion. */
@Composable
internal fun Modifier.softBlurEnter(identity: Any? = Unit): Modifier {
    val progress = remember(identity) { Animatable(0f) }
    var entering by remember(identity) { mutableStateOf(true) }
    LaunchedEffect(progress) {
        progress.animateTo(1f, tween(260, easing = CubicBezierEasing(0.22f, 0.72f, 0.2f, 1f)))
        entering = false
    }
    if (!entering) return this
    val blur = if (Build.VERSION.SDK_INT >= 31) rememberPageMotionBlur() else null
    return graphicsLayer {
        val fraction = progress.value
        scaleX = 0.94f + fraction * 0.06f
        scaleY = scaleX
        alpha = fraction
        renderEffect = blur?.invoke(6f * (1f - fraction))
    }
}
