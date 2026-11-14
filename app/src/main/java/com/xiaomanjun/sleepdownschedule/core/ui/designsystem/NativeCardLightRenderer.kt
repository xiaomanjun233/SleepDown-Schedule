package com.xiaomanjun.sleepdownschedule.core.ui.designsystem

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos

/** Card-derived light behind the controls; geometry and opacity are read outside composition. */
internal data class NativeCardLightRequest(
    val bitmap: Bitmap?,
    val cardBounds: () -> Rect,
    val strength: () -> Float,
    val modifier: Modifier,
    val cornerRadius: Dp = 0.dp,
    val receiverBounds: (() -> Rect)? = null,
    val revealProgress: () -> Float = strength,
    val projectionDepth: Dp? = null,
    val fitToCardWidth: Boolean = false
)

internal val LocalNativeCardLightRenderer = compositionLocalOf<
    (@Composable (NativeCardLightRequest) -> Boolean)?
> { null }

@Stable
internal class CardLightAnimation {
    val opacity = Animatable(0f)
    val reveal = Animatable(0f)
}

private val SystemCardLightEasing = Easing { ((1.0 - cos(PI * it)) / 2.0).toFloat() }

/** Stock ViewPropertyAnimator fades for 1000ms; the COE oval reveal uses a 650ms ValueAnimator. */
@Composable
internal fun rememberCardLightAnimation(visible: Boolean, sourceKey: Any?): CardLightAnimation {
    val animation = remember { CardLightAnimation() }
    LaunchedEffect(visible, sourceKey) {
        coroutineScope {
            launch {
                animation.opacity.animateTo(if (visible) 1f else 0f,
                    tween(if (visible) 1000 else 170, easing = SystemCardLightEasing))
            }
            launch {
                if (visible) animation.reveal.snapTo(0f)
                animation.reveal.animateTo(if (visible) 1f else 0f,
                    tween(if (visible) 650 else 170, easing = SystemCardLightEasing))
            }
        }
    }
    return animation
}
