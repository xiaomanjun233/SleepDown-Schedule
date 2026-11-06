package com.xiaomanjun.sleepdownschedule.app.ui

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xiaomanjun.sleepdownschedule.core.ui.material.LocalVisualEffects
import com.xiaomanjun.sleepdownschedule.core.ui.material.VisualEffectsState
import com.xiaomanjun.sleepdownschedule.data.preferences.VisualEffectsPreferences
import com.xiaomanjun.sleepdownschedule.platform.coloros.ColorOsNativeUi
import com.xiaomanjun.sleepdownschedule.platform.coloros.ProvideColorOsMaterials

@Composable
internal fun ProvideVisualEffects(content: @Composable () -> Unit) {
    val context = LocalContext.current.applicationContext
    val preferences = remember(context) { VisualEffectsPreferences.get(context) }
    val selected by preferences.mode.collectAsStateWithLifecycle()
    // Private ColorOS controls retain their own capability check; the selectable mode does not.
    val nativeAvailable = remember(context) { ColorOsNativeUi.source(context) != null }
    val couiKitAvailable = Build.VERSION.SDK_INT >= 34
    val state = remember(selected, nativeAvailable, couiKitAvailable, preferences) {
        VisualEffectsState(
            selectedMode = selected,
            effectiveMode = selected,
            colorOsAvailable = nativeAvailable,
            couiKitAvailable = couiKitAvailable,
        ) { mode -> preferences.select(mode) }
    }
    CompositionLocalProvider(LocalVisualEffects provides state) {
        ProvideColorOsMaterials(
            enabled = state.usesNativeControls,
            couiControlsEnabled = state.usesCouiControls,
            content = content,
        )
    }
}
