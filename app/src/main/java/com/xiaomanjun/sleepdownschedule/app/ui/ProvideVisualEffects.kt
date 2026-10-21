package com.xiaomanjun.sleepdownschedule.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xiaomanjun.sleepdownschedule.core.ui.material.LocalVisualEffects
import com.xiaomanjun.sleepdownschedule.core.ui.material.VisualEffectsState
import com.xiaomanjun.sleepdownschedule.data.preferences.VisualEffectsPreferences
import com.xiaomanjun.sleepdownschedule.model.VisualEffectMode
import com.xiaomanjun.sleepdownschedule.model.effectiveVisualEffectMode
import com.xiaomanjun.sleepdownschedule.platform.coloros.ColorOsNativeUi
import com.xiaomanjun.sleepdownschedule.platform.coloros.ProvideColorOsMaterials

@Composable
internal fun ProvideVisualEffects(content: @Composable () -> Unit) {
    val context = LocalContext.current.applicationContext
    val preferences = remember(context) { VisualEffectsPreferences.get(context) }
    val selected by preferences.mode.collectAsStateWithLifecycle()
    val available = remember(context) { ColorOsNativeUi.source(context) != null }
    val state = remember(selected, available, preferences) {
        VisualEffectsState(selected, effectiveVisualEffectMode(selected, available), available) { mode ->
            if (mode == VisualEffectMode.LiquidGlass || available) preferences.select(mode)
        }
    }
    CompositionLocalProvider(LocalVisualEffects provides state) {
        ProvideColorOsMaterials(enabled = state.usesNativeControls, content = content)
    }
}
