package com.xiaomanjun.sleepdownschedule.feature.home

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.xiaomanjun.sleepdownschedule.DockAlignment
import com.xiaomanjun.sleepdownschedule.R
import com.xiaomanjun.sleepdownschedule.core.performance.DockEntranceContainer
import com.xiaomanjun.sleepdownschedule.core.performance.StartupPhase
import com.xiaomanjun.sleepdownschedule.core.ui.interaction.dockImeCompensationPx
import com.xiaomanjun.sleepdownschedule.core.ui.material.LocalHomeMaterialControls
import com.xiaomanjun.sleepdownschedule.feature.agent.homeCountdownShockwave

/** Native-only input scope: no schedule config, backdrop, overlay controller or animation value. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NativeHomeDockChrome(
    visible: Boolean, parallel: Boolean, selectedIndex: Int, dark: Boolean,
    alignment: DockAlignment, buttonHidden: Boolean,
    onSelect: (Int) -> Unit, onHome: () -> Unit, onOpenMenu: (Rect, GraphicsLayer) -> Unit,
    fallback: State<@Composable () -> Unit>,
    phase: StartupPhase,
    modifier: Modifier = Modifier
) {
    if (!visible) return
    // Aggregate composition commits only; no user data or per-frame state is read here.
    SideEffect { Log.i("SleepDownCOUI", "Native dock commit: parallel=$parallel, selected=$selectedIndex, dark=$dark, hidden=$buttonHidden") }
    val restingModifier = modifier.homeCountdownShockwave(0.85f)
    DockEntranceContainer(phase, restingModifier) {
    if (parallel) {
        if (!LocalHomeMaterialControls.current.ParallelDock(selectedIndex, dark, buttonHidden, Modifier, onSelect, onOpenMenu)) NativeDockFallback(fallback)
    } else {
        val density = LocalDensity.current
        val systemBottom = WindowInsets.navigationBarsIgnoringVisibility.only(WindowInsetsSides.Bottom).getBottom(density)
        val bottom = with(density) { systemBottom.toDp() } + 8.dp
        val compensation = dockImeCompensationPx(WindowInsets.ime.getBottom(density), systemBottom)
        val position = when (alignment) {
            DockAlignment.LEFT -> Alignment.BottomStart
            DockAlignment.CENTER -> Alignment.BottomCenter
            DockAlignment.RIGHT -> Alignment.BottomEnd
        }
        val click = remember(onSelect, onHome) { { index: Int -> if (index == 0) onHome() else onSelect(2) } }
        val tabs = remember { listOf(R.drawable.ic_week_view to "课程", R.drawable.ic_settings to "设置") }
        Box(Modifier.fillMaxSize(), contentAlignment = position) {
            Box(Modifier.padding(start = 18.dp, end = 18.dp, bottom = bottom)
                .graphicsLayer { translationY = compensation.toFloat() }) {
                if (!LocalHomeMaterialControls.current.Dock(tabs, if (selectedIndex == 2) 1 else 0, dark,
                        Modifier.width(LocalHomeMaterialControls.current.ordinaryDockWidth()), click)) NativeDockFallback(fallback)
            }
        }
    }
    }
}

@Composable
private fun NativeDockFallback(content: State<@Composable () -> Unit>) { content.value() }
