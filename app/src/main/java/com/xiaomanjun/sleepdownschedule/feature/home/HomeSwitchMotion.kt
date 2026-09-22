package com.xiaomanjun.sleepdownschedule.feature.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.min

private const val SwitchGroupCount = 6
private const val SwitchGroupDelayMillis = 18
private val SwitchSpring = spring<Float>(dampingRatio = 0.74f, stiffness = 260f, visibilityThreshold = 0.0015f)

internal enum class HomeSwitchClip { None, Page, TopBar }

/** Parallel pages share a spring response, with the lower content following the upper groups. */
@Stable
internal class HomeSwitchMotion(initialSecondary: Boolean, private val target: State<Boolean>) {
    private val tracks = List(SwitchGroupCount) { Animatable(if (initialSecondary) 1f else 0f) }
    // Animatable resets velocity on cancellation. Keep the last frame for a continuous reversal.
    private val velocities = FloatArray(SwitchGroupCount)
    private var settledSecondary by mutableStateOf(initialSecondary)
    private var running by mutableStateOf(false)
    val progress: State<Float> = tracks.first().asState()
    val moving: Boolean get() = running || settledSecondary != target.value

    // Keep sampling throughout the stagger and the spring's overshoot/settling frames.
    val sampleKey: List<Float> get() = tracks.map { it.value }

    fun retains(secondary: Boolean): Boolean = moving || settledSecondary == secondary
    fun groupProgress(group: Int): Float = tracks[group.coerceIn(0, SwitchGroupCount - 1)].value

    suspend fun animateTo(secondary: Boolean) {
        val destination = if (secondary) 1f else 0f
        if (tracks.all { it.value == destination } && velocities.all { it == 0f }) {
            settledSecondary = secondary
            return
        }
        // On reversal keep every group's current position, without another initial pause.
        val settledPosition = if (settledSecondary) 1f else 0f
        val interrupted = tracks.any { it.value != settledPosition } || velocities.any { it != 0f }
        val durationScale = currentCoroutineContext()[MotionDurationScale]?.scaleFactor ?: 1f
        running = true
        try {
            coroutineScope {
                tracks.forEachIndexed { group, track ->
                    launch {
                        if (!interrupted) delay((group * SwitchGroupDelayMillis * durationScale).toLong())
                        track.animateTo(destination, animationSpec = SwitchSpring,
                            initialVelocity = velocities[group]) {
                            velocities[group] = velocity
                        }
                        velocities[group] = 0f
                    }
                }
            }
            settledSecondary = secondary
        } finally {
            running = false
        }
    }
}

@Composable
internal fun rememberHomeSwitchMotion(secondary: Boolean, label: String): HomeSwitchMotion {
    val target = rememberUpdatedState(secondary)
    val motion = remember(label) { HomeSwitchMotion(secondary, target) }
    LaunchedEffect(motion, secondary) { motion.animateTo(secondary) }
    return motion
}

@Composable
internal fun Modifier.homeSwitchLayer(
    motion: HomeSwitchMotion,
    secondary: Boolean,
    pageClip: HomeSwitchClip = HomeSwitchClip.None
): Modifier {
    val direction = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1f else 1f
    return graphicsLayer {
        translationX = direction * size.width * ((if (secondary) 1f else 0f) - motion.progress.value)
        // Round the complete page while it travels; ease the corners back at either endpoint.
        // Do not clamp the translation: the spring must be allowed to overshoot and return.
        clip = pageClip != HomeSwitchClip.None && motion.moving
        shape = if (clip) {
            val position = motion.progress.value.coerceIn(0f, 1f)
            val radius = 32.dp * (min(position, 1f - position) * 8f).coerceIn(0f, 1f)
            val bottom = if (pageClip == HomeSwitchClip.TopBar) 0.dp else radius
            RoundedCornerShape(topStart = radius, topEnd = radius, bottomEnd = bottom, bottomStart = bottom)
        } else RectangleShape
    }
}

private class SwitchPageScope(val motion: HomeSwitchMotion, val width: State<Int>, val direction: Float)
private val LocalSwitchPages = staticCompositionLocalOf<List<SwitchPageScope>> { emptyList() }

/** Apply once per real content group, so text, cards and their decorations move together. */
@Composable
internal fun Modifier.homeSwitchGroup(): Modifier {
    val pages = LocalSwitchPages.current
    if (pages.isEmpty()) return this
    val group = remember { mutableIntStateOf(-1) }
    return onGloballyPositioned { coordinates ->
        if (group.intValue < 0 || pages.none { it.motion.moving }) {
            val height = coordinates.findRootCoordinates().size.height.coerceAtLeast(1)
            val top = coordinates.localToRoot(Offset.Zero).y
            // Use the visible position, then hold the group while an existing fling settles.
            group.intValue = (top / height * SwitchGroupCount).toInt().coerceIn(0, SwitchGroupCount - 1)
        }
    }.graphicsLayer {
        translationX = pages.sumOf { page ->
            (page.direction * page.width.value *
                (page.motion.progress.value - page.motion.groupProgress(group.intValue))).toDouble()
        }.toFloat()
        clip = false
    }
}

@Composable
internal fun HomeSwitchPane(
    motion: HomeSwitchMotion,
    secondary: Boolean,
    modifier: Modifier = Modifier,
    pageClip: HomeSwitchClip = HomeSwitchClip.None,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable BoxScope.() -> Unit
) {
    if (!motion.retains(secondary)) return
    val width = remember { mutableIntStateOf(0) }
    val direction = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1f else 1f
    val parents = LocalSwitchPages.current
    val pages = remember(parents, motion, direction) { parents + SwitchPageScope(motion, width, direction) }
    val inputModifier = if (motion.moving) {
        Modifier.clearAndSetSemantics {}.pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                }
            }
        }
    } else Modifier
    Box(
        modifier.onSizeChanged { width.intValue = it.width }
            .homeSwitchLayer(motion, secondary, pageClip).then(inputModifier),
        contentAlignment = contentAlignment
    ) {
        CompositionLocalProvider(LocalSwitchPages provides pages) { content() }
    }
}
