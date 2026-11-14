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
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
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
import androidx.compose.ui.unit.IntOffset
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/** Matches the navigation motion used by the bundled Miuix system-style navigator. */
private class MiuixSettingsNavigationEasing(response: Float = 0.8f, damping: Float = 0.95f) : Easing {
    private val decay: Float
    private val frequency: Float
    private val phase: Float

    init {
        val omega = 2.0 * PI / response
        val stiffness = omega * omega
        val friction = damping * 4.0 * PI / response
        frequency = (sqrt(4.0 * stiffness - friction * friction) / 2.0).toFloat()
        decay = (-friction / 2.0).toFloat()
        phase = decay / frequency
    }

    override fun transform(fraction: Float): Float {
        val time = fraction.toDouble()
        val attenuation = exp(decay * time)
        return (attenuation * (-cos(frequency * time) + phase * sin(frequency * time)) + 1.0).toFloat()
    }
}

private val DetailPageSlide = tween<IntOffset>(500, easing = MiuixSettingsNavigationEasing())

internal fun AnimatedContentTransitionScope<*>.detailPageSlide(returning: Boolean): ContentTransform =
    if (returning) {
        (slideInHorizontally(DetailPageSlide) { width -> -width / 4 } togetherWith
            slideOutHorizontally(DetailPageSlide) { width -> width }).using(null).apply {
            targetContentZIndex = -1f
        }
    } else {
        (slideInHorizontally(DetailPageSlide) { width -> width } togetherWith
            slideOutHorizontally(DetailPageSlide) { width -> -width / 4 }).using(null)
    }

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
