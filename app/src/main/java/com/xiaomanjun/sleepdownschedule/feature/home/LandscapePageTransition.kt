package com.xiaomanjun.sleepdownschedule.feature.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp

/** The sidebar lives outside this host. Only outgoing/incoming page pixels receive the effect. */
@Composable
internal fun <T> LandscapePageTransition(
    target: T,
    modifier: Modifier = Modifier,
    onMovingChange: (Boolean) -> Unit = {},
    content: @Composable (T) -> Unit
) {
    val easing = CubicBezierEasing(0.22f, 0.72f, 0.20f, 1f)
    val pageTransition = updateTransition(target, label = "landscape-page")
    val moving = pageTransition.isRunning || pageTransition.currentState != pageTransition.targetState
    val latestMovingCallback by rememberUpdatedState(onMovingChange)
    LaunchedEffect(moving) { latestMovingCallback(moving) }
    DisposableEffect(Unit) { onDispose { latestMovingCallback(false) } }
    val inputGuard = if (moving) Modifier.clearAndSetSemantics {}.pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
        }
    } else Modifier
    pageTransition.AnimatedContent(modifier = modifier.then(inputGuard),
        transitionSpec = {
            (fadeIn(tween(300)) + scaleIn(tween(360, easing = easing), initialScale = 0.96f))
                .togetherWith(fadeOut(tween(220)) + scaleOut(tween(300, easing = easing), targetScale = 0.96f))
                .using(null)
        }) { page ->
        val blur by transition.animateFloat(label = "landscape-page-blur", transitionSpec = { tween(320, easing = easing) }) {
            if (it == EnterExitState.Visible) 0f else 10f
        }
        Box(Modifier.fillMaxSize().graphicsLayer {
            val radius = blur.dp.toPx()
            renderEffect = if (radius > 0.5f) BlurEffect(radius, radius, TileMode.Clamp) else null
        }) { content(page) }
    }
}

@Composable
internal fun <T> AdaptivePaneTransition(
    target: T,
    landscape: Boolean,
    portraitTransitionSpec: AnimatedContentTransitionScope<T>.() -> ContentTransform,
    label: String,
    content: @Composable (T) -> Unit
) {
    if (landscape) LandscapePageTransition(target, content = content)
    else AnimatedContent(targetState = target, transitionSpec = portraitTransitionSpec, label = label) { content(it) }
}
