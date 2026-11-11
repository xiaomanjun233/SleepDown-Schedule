package com.xiaomanjun.sleepdownschedule.app.ui

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.itos.coui.menu.ProvideCouiMenuBackdrop
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xiaomanjun.sleepdownschedule.core.ui.material.LocalVisualEffects
import com.xiaomanjun.sleepdownschedule.core.ui.material.VisualEffectsState
import com.xiaomanjun.sleepdownschedule.data.preferences.VisualEffectsPreferences
import com.xiaomanjun.sleepdownschedule.platform.coloros.ProvideCouiMaterials
import com.xiaomanjun.sleepdownschedule.model.VisualEffectMode

@Composable
internal fun ProvideVisualEffects(content: @Composable () -> Unit) {
    val context = LocalContext.current.applicationContext
    val preferences = remember(context) { VisualEffectsPreferences.get(context) }
    val selected by preferences.mode.collectAsStateWithLifecycle()
    val couiKitAvailable = Build.VERSION.SDK_INT >= 34
    // Old systems render the Compose edition without changing the saved selection.
    val effective = if (couiKitAvailable) selected else VisualEffectMode.LiquidGlass
    val state = remember(selected, effective, couiKitAvailable, preferences) {
        VisualEffectsState(
            selectedMode = selected,
            effectiveMode = effective,
            couiKitAvailable = couiKitAvailable,
        ) { mode -> preferences.select(mode) }
    }
    CompositionLocalProvider(LocalVisualEffects provides state) {
        ProvideCouiMaterials(
            couiControlsEnabled = state.usesCouiControls,
            content = {
                // Keep screen state mounted when switching the window source on or off.
                ProvideCouiMenuBackdrop(isolateContent = false, recordWhenIdle = false,
                    enabled = true) { source ->
                    Box(Modifier.fillMaxSize().then(source)) { content() }
                }
            },
        )
    }
}
