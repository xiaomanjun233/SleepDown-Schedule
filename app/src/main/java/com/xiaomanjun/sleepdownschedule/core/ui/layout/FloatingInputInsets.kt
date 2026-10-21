package com.xiaomanjun.sleepdownschedule.core.ui.layout

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.offset
import kotlin.math.roundToInt

/** Shared by the school search dock and both AI composers. Measure the root, not a morphing card. */
internal class FloatingInputInsets(
    private val ime: WindowInsets,
    private val navigation: WindowInsets,
    private val density: Density
) {
    private var closedBottom by mutableIntStateOf(0)
    private var currentBottom by mutableIntStateOf(0)

    val bottomPx: Int get() = floatingInputBottomInsetPx(
        ime.getBottom(density), navigation.getBottom(density), closedBottom, currentBottom
    )
    val bottom: Dp get() = with(density) { bottomPx.toDp() }

    val viewportModifier: Modifier = Modifier.onGloballyPositioned { coordinates ->
        val bottom = (coordinates.positionOnScreen().y + coordinates.size.height).roundToInt()
        currentBottom = bottom
        if (ime.getBottom(density) <= navigation.getBottom(density)) closedBottom = bottom
    }
}

@Composable
internal fun rememberFloatingInputInsets(): FloatingInputInsets {
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val ime = WindowInsets.ime
    val navigation = WindowInsets.navigationBars
    // IME resize can change screenHeightDp; it must not reset the closed-window baseline.
    return remember(configuration.screenWidthDp, configuration.orientation, density, ime, navigation) {
        FloatingInputInsets(ime, navigation, density)
    }
}

/** Read animated insets during measure, so the input follows the same frame as the keyboard. */
internal fun Modifier.floatingInputPadding(insets: FloatingInputInsets): Modifier = layout { measurable, constraints ->
    val bottom = insets.bottomPx.coerceAtMost(constraints.maxHeight)
    val child = measurable.measure(constraints.offset(vertical = -bottom))
    layout(child.width, child.height + bottom) { child.placeRelative(0, 0) }
}

internal fun floatingInputBottomInsetPx(ime: Int, navigation: Int, closedBottom: Int, currentBottom: Int): Int {
    if (ime <= navigation) return navigation
    val applied = if (closedBottom > 0 && currentBottom > 0) (closedBottom - currentBottom).coerceAtLeast(0) else 0
    return (ime - applied).coerceAtLeast(0)
}
