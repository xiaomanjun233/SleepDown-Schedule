package com.xiaomanjun.sleepdownschedule.feature.home.week

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.xiaomanjun.sleepdownschedule.feature.home.day.LocalHomeTextShadowScale
import java.time.LocalDate

/**
 * Home top-bar text motion.
 *
 * Every label on the bar behaves as a shared element: the same words keep their identity while
 * they are carried to a new slot, different words dissolve into each other, and a label that is
 * both replaced and moved does the two at the same time.
 *
 * The dissolve keeps the first version's recipe (alpha + a 2% settle on the text only) and the
 * travelling weekday row keeps its soft-blur envelope; a single small label does not mount a
 * `RenderEffect` layer, because that layer's bounds are exactly the hard-edged translucent
 * rectangle that used to flash behind the top-left "第 X 周".
 */

/** One week of weekday columns: the visible days, and the date the first visible column starts on. */
internal data class WeekdayStrip(
    val weekStart: LocalDate,
    val weekdays: List<Int>
)

/**
 * Two weekday strips plus the 0..1 progress that carries one into the other.
 *
 * [progress] is read from layout and draw lambdas only, so a running transition invalidates
 * drawing instead of recomposing the strip.
 */
@Stable
internal class WeekdayStripTransition(
    val from: WeekdayStrip,
    val to: WeekdayStrip,
    val progress: () -> Float
)

private val NoHeaderMotion: () -> Float = { 1f }

/** The first version's 280 ms cubic ease, now also carrying the columns to their new slots. */
private val HeaderMotionSpec = tween<Float>(280, easing = FastOutSlowInEasing)

/**
 * One-shot transition for a header that changes without a pager, such as a week jump or a
 * hide-weekends change.
 */
@Composable
internal fun rememberWeekdayStripTransition(strip: WeekdayStrip): WeekdayStripTransition {
    var from by remember { mutableStateOf(strip) }
    var to by remember { mutableStateOf(strip) }
    val progress = remember { Animatable(1f) }
    LaunchedEffect(strip) {
        if (strip == to) return@LaunchedEffect
        if (strip == from) {
            // A reversal that arrives mid-flight runs the same pair back instead of snapping to
            // the outgoing strip, so a swiped-back week never jumps.
            progress.animateTo(0f, HeaderMotionSpec)
            val outgoing = from
            from = to
            to = outgoing
            progress.snapTo(1f)
            return@LaunchedEffect
        }
        from = to
        to = strip
        progress.snapTo(0f)
        progress.animateTo(1f, HeaderMotionSpec)
        from = to
    }
    val settled = from == to
    val readProgress: () -> Float = { progress.value }
    return remember(from, to, settled) {
        WeekdayStripTransition(from, to, if (settled) NoHeaderMotion else readProgress)
    }
}

/**
 * Text that changes in place dissolves without a blur layer: the rest state draws the current
 * content directly, and only a running change mounts the two alpha layers. The 2% settle is
 * limited to the text itself, so capsules, pills and the header glass never scale.
 *
 * The outgoing text is resolved while composing, not from a `LaunchedEffect`: starting the swap
 * in an effect let the new text render for one frame at full opacity before the fade began.
 */
@Composable
internal fun <T> WeekHeaderCrossfade(
    target: T,
    modifier: Modifier = Modifier,
    durationMillis: Int = 280,
    content: @Composable (T) -> Unit
) {
    val committed = remember { CommittedHeaderTarget(target) }
    val outgoing = remember(target) { committed.value.takeIf { it != target } }
    val progress = remember(target) { Animatable(0f) }
    var finished by remember(target) { mutableStateOf(false) }
    LaunchedEffect(progress) {
        if (outgoing != null) {
            progress.animateTo(1f, tween(durationMillis, easing = FastOutSlowInEasing))
        }
        finished = true
    }
    SideEffect { committed.value = target }
    val animating = outgoing != null && !finished
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (!animating) {
            content(target)
            return@Box
        }
        // Two stacked labels would otherwise stack their legibility shadows and darken the
        // wallpaper behind the text for the length of the dissolve.
        CompositionLocalProvider(LocalHomeTextShadowScale provides 0.5f) {
            Box(
                Modifier.matchParentSize().graphicsLayer {
                    val fraction = progress.value.coerceIn(0f, 1f)
                    alpha = 1f - fraction
                    scaleX = 1f - 0.02f * fraction
                    scaleY = scaleX
                }
            ) {
                content(checkNotNull(outgoing))
            }
            Box(
                Modifier.graphicsLayer {
                    val fraction = progress.value.coerceIn(0f, 1f)
                    alpha = fraction
                    val scale = 0.98f + 0.02f * fraction
                    scaleX = scale
                    scaleY = scale
                }
            ) {
                content(target)
            }
        }
    }
}

private class CommittedHeaderTarget<T>(initial: T) {
    var value: T = initial
}
