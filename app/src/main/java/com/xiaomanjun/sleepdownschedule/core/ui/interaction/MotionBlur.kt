package com.xiaomanjun.sleepdownschedule.core.ui.interaction

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** Reuse native effects across frames and both panes; preserve the 0–10dp blur envelope. */
@Composable
internal fun rememberPageMotionBlur(): (Float) -> RenderEffect? {
    val density = LocalDensity.current
    return remember(density.density) {
        val effects = List(21) { step ->
            if (step == 0) null else with(density) {
                val radius = (step * 0.5f).dp.toPx()
                BlurEffect(radius, radius, TileMode.Clamp)
            }
        }
        val lookup: (Float) -> RenderEffect? = { radius -> effects[(radius * 2).roundToInt().coerceIn(0, 20)] }
        lookup
    }
}
