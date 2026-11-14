package com.xiaomanjun.sleepdownschedule.core.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/** Draws the fixed action after the scrolling body that supplies its material. */
@Composable
internal fun BoxScope.SettingsFloatingActionArea(
    pageColor: Color,
    dark: Boolean,
    followIme: Boolean = true,
    action: @Composable (Modifier) -> Unit,
) {
    val density = LocalDensity.current
    val imeLift = if (followIme) with(density) {
        (WindowInsets.ime.getBottom(this) - WindowInsets.navigationBars.getBottom(this))
            .coerceAtLeast(0).toDp()
    } else 0.dp
    Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(168.dp + imeLift)
        .background(Brush.verticalGradient(listOf(
            pageColor.copy(alpha = 0f),
            pageColor.copy(alpha = if (dark) 0.42f else 0.36f),
            pageColor.copy(alpha = if (dark) 0.76f else 0.70f),
            pageColor.copy(alpha = if (dark) 0.94f else 0.92f)
        ))))
    val imeAvoidance = if (followIme) Modifier.imePadding() else Modifier
    action(Modifier.align(Alignment.BottomCenter).then(imeAvoidance).navigationBarsPadding()
        .padding(start = 16.dp, end = 16.dp, bottom = 18.dp).fillMaxWidth())
}
