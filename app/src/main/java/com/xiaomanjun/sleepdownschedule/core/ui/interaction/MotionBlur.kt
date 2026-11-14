package com.xiaomanjun.sleepdownschedule.core.ui.interaction

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

private val NoMotionBlur: (Float) -> RenderEffect? = { null }

@Composable
internal fun rememberPageMotionBlur(): (Float) -> RenderEffect? = rememberMotionBlur(TileMode.Clamp)

/** Reuse native effects across frames over a 0–10dp blur envelope. */
@Composable
internal fun rememberMotionBlur(tileMode: TileMode): (Float) -> RenderEffect? {
    if (Build.VERSION.SDK_INT < 31) return NoMotionBlur
    val density = LocalDensity.current
    return remember(density.density, tileMode) {
        val effects = List(21) { step ->
            if (step == 0) null else with(density) {
                val radius = (step * 0.5f).dp.toPx()
                BlurEffect(radius, radius, tileMode)
            }
        }
        val lookup: (Float) -> RenderEffect? = { radius -> effects[(radius * 2).roundToInt().coerceIn(0, 20)] }
        lookup
    }
}
