package com.xiaomanjun.sleepdownschedule.core.ui.interaction

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

/** The sidebar lives outside this host. Only outgoing/incoming page pixels receive the effect. */
@Composable
internal fun <T> LandscapePageTransition(
    target: T,
    modifier: Modifier = Modifier,
    onMovingChange: (Boolean) -> Unit = {},
    content: @Composable (T) -> Unit
) {
    val pageTransition = updateTransition(target, label = "landscape-page")
    val motionBlur = rememberPageMotionBlur()
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
            (fadeIn(LandscapePageMotion.EnterFade) +
                scaleIn(LandscapePageMotion.EnterScale, initialScale = LandscapePageMotion.HiddenScale))
                .togetherWith(fadeOut(LandscapePageMotion.ExitFade) +
                    scaleOut(LandscapePageMotion.ExitScale, targetScale = LandscapePageMotion.HiddenScale))
                .using(null)
        }) { page ->
        val blur = transition.animateFloat(label = "landscape-page-blur", transitionSpec = { LandscapePageMotion.Blur }) {
            if (it == EnterExitState.Visible) 0f else LandscapePageMotion.HiddenBlurDp
        }
        Box(Modifier.fillMaxSize().graphicsLayer {
            renderEffect = motionBlur(blur.value)
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
