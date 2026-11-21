package com.xiaomanjun.sleepdownschedule.core.ui.interaction

import android.graphics.Matrix
import android.os.Build
import android.view.RoundedCorner
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** Map local View coordinates to screen coordinates, including ancestor scroll and transforms. */
internal fun View.localToScreenMatrix(): Matrix {
    val transform = Matrix()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        transformMatrixToGlobal(transform)
        return transform
    }
    accumulateWindowTransform(transform)
    val origin = FloatArray(2)
    transform.mapPoints(origin)
    val location = IntArray(2)
    getLocationOnScreen(location)
    // The View hierarchy lacks the window's position on screen, such as a floating dialog's offset.
    transform.postTranslate(location[0] - origin[0], location[1] - origin[1])
    return transform
}

private fun View.accumulateWindowTransform(transform: Matrix) {
    val parentView = parent as? View
    if (parentView != null) {
        parentView.accumulateWindowTransform(transform)
        transform.preTranslate(-parentView.scrollX.toFloat(), -parentView.scrollY.toFloat())
    }
    transform.preTranslate(left.toFloat(), top.toFloat())
    transform.preConcat(matrix)
}

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

/** The shared popup/Activity arc follows window size; safe insets and font scale do not affect it. */
internal fun adaptiveMorphArcDp(widthDp: Int, heightDp: Int): Dp = when {
    widthDp < 600 -> 48.dp
    widthDp <= heightDp -> 44.dp
    else -> 40.dp
}

@Composable
internal fun windowMorphArc(): Dp {
    val size = LocalWindowInfo.current.containerSize
    return with(LocalDensity.current) {
        adaptiveMorphArcDp(size.width.toDp().value.roundToInt().coerceAtLeast(1),
            size.height.toDp().value.roundToInt().coerceAtLeast(1))
    }
}
