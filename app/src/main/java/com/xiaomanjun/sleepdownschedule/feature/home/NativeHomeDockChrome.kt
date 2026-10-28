package com.xiaomanjun.sleepdownschedule.feature.home

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.layer.GraphicsLayer
import com.xiaomanjun.sleepdownschedule.core.performance.DockEntranceContainer
import com.xiaomanjun.sleepdownschedule.core.performance.StartupPhase
import com.xiaomanjun.sleepdownschedule.core.ui.material.LocalHomeMaterialControls
import com.xiaomanjun.sleepdownschedule.feature.agent.homeCountdownShockwave

/** Native-only input scope: no schedule config, backdrop, overlay controller or animation value. */
@Composable
internal fun NativeHomeDockChrome(
    visible: Boolean, selectedIndex: Int, dark: Boolean,
    buttonHidden: Boolean,
    onSelect: (Int) -> Unit, onOpenMenu: (Rect, GraphicsLayer) -> Unit,
    fallback: State<@Composable () -> Unit>,
    phase: StartupPhase,
    modifier: Modifier = Modifier
) {
    if (!visible) return
    // Aggregate composition commits only; no user data or per-frame state is read here.
    SideEffect { Log.i("SleepDownCOUI", "Native dock commit: selected=$selectedIndex, dark=$dark, hidden=$buttonHidden") }
    DockEntranceContainer(phase, modifier.homeCountdownShockwave(0.85f)) {
        if (!LocalHomeMaterialControls.current.ParallelDock(selectedIndex, dark, buttonHidden, Modifier, onSelect, onOpenMenu)) {
            NativeDockFallback(fallback)
        }
    }
}

@Composable
private fun NativeDockFallback(content: State<@Composable () -> Unit>) { content.value() }
