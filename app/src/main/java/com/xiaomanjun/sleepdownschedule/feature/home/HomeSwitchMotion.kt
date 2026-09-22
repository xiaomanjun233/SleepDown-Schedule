package com.xiaomanjun.sleepdownschedule.feature.home

import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/** A single reversible clock keeps both pages, their chrome and wallpaper in step. */
@Stable
internal class HomeSwitchMotion(
    private val transition: Transition<Boolean>,
    val progress: State<Float>
) {
    fun retains(secondary: Boolean): Boolean =
        moving || transition.currentState == secondary || transition.targetState == secondary

    val moving: Boolean get() = transition.isRunning || transition.currentState != transition.targetState
}

@Composable
internal fun rememberHomeSwitchMotion(secondary: Boolean, label: String): HomeSwitchMotion {
    val transition = updateTransition(secondary, label = label)
    val progress = transition.animateFloat(
        transitionSpec = { spring(dampingRatio = 1f, stiffness = 420f, visibilityThreshold = 0.001f) },
        label = "$label-progress"
    ) { if (it) 1f else 0f }
    return remember(transition, progress) { HomeSwitchMotion(transition, progress) }
}

@Composable
internal fun Modifier.homeSwitchLayer(
    motion: HomeSwitchMotion,
    secondary: Boolean,
    travel: Dp = 28.dp
): Modifier {
    val direction = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1f else 1f
    return graphicsLayer {
        val progress = motion.progress.value.coerceIn(0f, 1f)
        val visibility = if (secondary) progress else 1f - progress
        alpha = visibility
        translationX = direction * travel.toPx() * if (secondary) 1f - progress else -progress
        scaleX = 0.985f + 0.015f * visibility
        scaleY = scaleX
        // Auto compositing releases its temporary alpha surface at the settled endpoint.
        clip = false
    }
}

@Composable
internal fun HomeSwitchPane(
    motion: HomeSwitchMotion,
    secondary: Boolean,
    modifier: Modifier = Modifier,
    travel: Dp = 28.dp,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable BoxScope.() -> Unit
) {
    if (!motion.retains(secondary)) return
    val inputModifier = if (motion.moving) {
        Modifier.clearAndSetSemantics {}.pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                }
            }
        }
    } else Modifier
    Box(modifier.homeSwitchLayer(motion, secondary, travel).then(inputModifier),
        contentAlignment = contentAlignment, content = content)
}
