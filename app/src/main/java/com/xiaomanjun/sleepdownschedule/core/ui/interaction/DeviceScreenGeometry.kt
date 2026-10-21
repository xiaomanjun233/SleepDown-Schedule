package com.xiaomanjun.sleepdownschedule.core.ui.interaction

import android.os.Build
import android.view.RoundedCorner
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp

/** Read current window insets, including after rotation; never cache a missing startup inset. */
@Composable
internal fun deviceScreenCornerRadiusPx(): Float {
    val view = LocalView.current
    val density = LocalDensity.current
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        listOf(
            RoundedCorner.POSITION_TOP_LEFT, RoundedCorner.POSITION_TOP_RIGHT,
            RoundedCorner.POSITION_BOTTOM_LEFT, RoundedCorner.POSITION_BOTTOM_RIGHT
        ).mapNotNull { view.rootWindowInsets?.getRoundedCorner(it)?.radius }
            .maxOrNull()?.toFloat()?.takeIf { it > 0f }
            ?: with(density) { 32.dp.toPx() }
    } else with(density) { 32.dp.toPx() }
}
