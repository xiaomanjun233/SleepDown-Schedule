package com.xiaomanjun.sleepdownschedule.feature.home.week

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.updateTransition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.unit.dp
import com.xiaomanjun.sleepdownschedule.core.ui.interaction.rememberMotionBlur
import com.xiaomanjun.sleepdownschedule.feature.home.HomeHeaderMotionSpec
import com.xiaomanjun.sleepdownschedule.feature.home.HomeHeaderText
import java.time.LocalDate

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
    val progress: Animatable<Float, AnimationVector1D>
)

@Composable
internal fun rememberWeekdayStripTransition(strip: WeekdayStrip): WeekdayStripTransition {
    var motion by remember { mutableStateOf(WeekdayStripTransition(strip, strip, Animatable(1f))) }
    LaunchedEffect(strip) {
        val previous = motion
        if (previous.from == strip && previous.to == strip) return@LaunchedEffect
        val transition = when (strip) {
            previous.from, previous.to -> previous
            else -> {
                val visible = if (previous.progress.value < 0.5f) previous.from else previous.to
                // Publish both strips and their initial fraction together.
                WeekdayStripTransition(visible, strip, Animatable(0f)).also { motion = it }
            }
        }
        transition.progress.animateTo(if (strip == transition.to) 1f else 0f, HomeHeaderMotionSpec)
        motion = WeekdayStripTransition(strip, strip, Animatable(1f))
    }
    return motion
}

@Composable
internal fun <T> WeekHeaderCrossfade(
    target: T,
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit
) {
    updateTransition(target, label = "week-header-range").HomeHeaderText(
        contentKey = { it },
        blur = rememberMotionBlur(TileMode.Decal),
        peakBlur = 3.5.dp,
        modifier = modifier,
        content = content
    )
}
