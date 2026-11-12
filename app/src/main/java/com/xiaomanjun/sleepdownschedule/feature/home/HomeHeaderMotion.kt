package com.xiaomanjun.sleepdownschedule.feature.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.LayerOutsets
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp

internal val HomeHeaderMotionSpec = tween<Float>(
    durationMillis = 420,
    easing = CubicBezierEasing(0.32f, 0.06f, 0.45f, 1f)
)

internal fun GraphicsLayerScope.homeHeaderBlur(
    fraction: Float,
    peak: Dp,
    effect: (Float) -> RenderEffect?,
) {
    val progress = fraction.coerceIn(0f, 1f)
    renderEffect = effect(peak.value * 4f * progress * (1f - progress))
    if (renderEffect != null) outsets = LayerOutsets(all = peak * 2f)
}

internal fun Modifier.homeHeaderTextLayer(
    visibility: () -> Float,
    origin: TransformOrigin = TransformOrigin.Center,
    peakBlur: Dp = 0.dp,
    blur: ((Float) -> RenderEffect?)? = null,
): Modifier = graphicsLayer {
    val progress = visibility().coerceIn(0f, 1f)
    val scale = lerp(0.88f, 1f, progress)
    scaleX = scale
    scaleY = scale
    transformOrigin = origin
    alpha = progress
    if (blur != null) homeHeaderBlur(progress, peakBlur, blur)
    if (renderEffect == null) compositingStrategy = CompositingStrategy.ModulateAlpha
}

/** The child transition owns the text until its opacity, scale and blur finish together. */
@Composable
internal fun <T> Transition<T>.HomeHeaderText(
    contentKey: (T) -> Any?,
    blur: (Float) -> RenderEffect?,
    peakBlur: Dp,
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.Center,
    origin: TransformOrigin = TransformOrigin.Center,
    content: @Composable (T) -> Unit,
) {
    val targetKey = contentKey(targetState)
    AnimatedContent(
        modifier = modifier,
        contentAlignment = contentAlignment,
        contentKey = contentKey,
        transitionSpec = {
            ContentTransform(EnterTransition.None, ExitTransition.None, sizeTransform = null)
        },
    ) { frame ->
        val visibility = transition.animateFloat(
            transitionSpec = { HomeHeaderMotionSpec },
            label = "header-text-visibility"
        ) { if (it == EnterExitState.Visible) 1f else 0f }
        val settled = transition.currentState == EnterExitState.Visible &&
            transition.targetState == EnterExitState.Visible
        Box(
            (if (settled) Modifier else Modifier.homeHeaderTextLayer({ visibility.value }, origin, peakBlur, blur))
                .then(if (contentKey(frame) == targetKey) Modifier else Modifier.clearAndSetSemantics {})
        ) { content(frame) }
    }
}
