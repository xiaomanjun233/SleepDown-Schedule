package com.xiaomanjun.sleepdownschedule.feature.home.week

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import java.time.LocalDate

internal const val HomeHeaderMotionMillis = 260
internal const val HomeHeaderTextMinimumScale = 0.88f

internal fun homeHeaderTextTransform(origin: TransformOrigin): ContentTransform {
    val timing = tween<Float>(HomeHeaderMotionMillis, easing = FastOutSlowInEasing)
    return ContentTransform(
        targetContentEnter = fadeIn(timing) + scaleIn(timing, HomeHeaderTextMinimumScale, origin),
        initialContentExit = fadeOut(timing) + scaleOut(timing, HomeHeaderTextMinimumScale, origin),
        sizeTransform = null
    )
}

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

private val HeaderMotionSpec = tween<Float>(HomeHeaderMotionMillis, easing = FastOutSlowInEasing)

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

@Composable
internal fun <T> WeekHeaderCrossfade(
    target: T,
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit
) {
    AnimatedContent(
        targetState = target,
        modifier = modifier,
        contentAlignment = Alignment.Center,
        transitionSpec = { homeHeaderTextTransform(TransformOrigin.Center) },
        label = "week-header-range"
    ) { content(it) }
}
