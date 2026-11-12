package com.xiaomanjun.sleepdownschedule.core.ui.interaction

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibilityScope
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics

/** Shared sidebar-page motion. Feature navigation owns the target and the portrait transition. */
private object LandscapePageMotion {
    val Easing = CubicBezierEasing(0.22f, 0.72f, 0.20f, 1f)
    val EnterFade = tween<Float>(300)
    val ExitFade = tween<Float>(220)
    val EnterScale = tween<Float>(360, easing = Easing)
    val ExitScale = tween<Float>(300, easing = Easing)
    val Blur = tween<Float>(320, easing = Easing)
    const val HiddenScale = 0.96f
    const val HiddenBlurDp = 10f
}

internal fun Modifier.blockPaneInputWhile(moving: Boolean): Modifier {
    if (!moving) return this
    return clearAndSetSemantics {}.pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
        }
    }
}

internal fun AnimatedContentTransitionScope<*>.landscapePageTransform(): ContentTransform =
    (fadeIn(LandscapePageMotion.EnterFade) +
        scaleIn(LandscapePageMotion.EnterScale, initialScale = LandscapePageMotion.HiddenScale))
        .togetherWith(fadeOut(LandscapePageMotion.ExitFade) +
            scaleOut(LandscapePageMotion.ExitScale, targetScale = LandscapePageMotion.HiddenScale))
        .using(null)

@Composable
internal fun AnimatedVisibilityScope.landscapePageBlur(enabled: Boolean = true): Modifier {
    if (!enabled) return Modifier
    val motionBlur = rememberPageMotionBlur()
    val blur = transition.animateFloat(label = "landscape-page-blur", transitionSpec = { LandscapePageMotion.Blur }) {
        if (it == EnterExitState.Visible) 0f else LandscapePageMotion.HiddenBlurDp
    }
    return Modifier.graphicsLayer { renderEffect = motionBlur(blur.value) }
}

/** The sidebar lives outside this host. Only outgoing/incoming page pixels receive the effect. */
@Composable
internal fun <T> LandscapePageTransition(
    target: T,
    modifier: Modifier = Modifier,
    onMovingChange: (Boolean) -> Unit = {},
    content: @Composable (T) -> Unit
) {
    val pageTransition = updateTransition(target, label = "landscape-page")
    val moving = pageTransition.isRunning || pageTransition.currentState != pageTransition.targetState
    val latestMovingCallback by rememberUpdatedState(onMovingChange)
    LaunchedEffect(moving) { latestMovingCallback(moving) }
    DisposableEffect(Unit) { onDispose { latestMovingCallback(false) } }
    pageTransition.AnimatedContent(
        modifier = modifier.blockPaneInputWhile(moving),
        transitionSpec = { landscapePageTransform() },
    ) { page ->
        Box(Modifier.fillMaxSize().then(landscapePageBlur())) { content(page) }
    }
}
