package com.xiaomanjun.sleepdownschedule.core.ui.material

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
internal fun controlAccentColor(liquidGlassColor: Color): Color =
    if (LocalVisualEffects.current.usesNativeControls) MaterialTheme.colorScheme.primary else liquidGlassColor
