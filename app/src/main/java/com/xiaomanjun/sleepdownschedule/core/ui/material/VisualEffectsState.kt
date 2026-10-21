package com.xiaomanjun.sleepdownschedule.core.ui.material

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import com.xiaomanjun.sleepdownschedule.model.VisualEffectMode

@Immutable
internal data class VisualEffectsState(
    val selectedMode: VisualEffectMode = VisualEffectMode.LiquidGlass,
    val effectiveMode: VisualEffectMode = VisualEffectMode.LiquidGlass,
    val colorOsAvailable: Boolean = false,
    val selectMode: (VisualEffectMode) -> Unit = {}
) {
    val usesNativeControls: Boolean get() = effectiveMode == VisualEffectMode.ColorOsGlow
}

internal val LocalVisualEffects = staticCompositionLocalOf { VisualEffectsState() }
