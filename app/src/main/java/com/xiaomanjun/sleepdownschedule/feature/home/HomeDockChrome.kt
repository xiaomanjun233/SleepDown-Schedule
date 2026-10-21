package com.xiaomanjun.sleepdownschedule.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.layer.GraphicsLayer
import com.kyant.backdrop.Backdrop
import com.xiaomanjun.sleepdownschedule.ScheduleConfigEntity
import com.xiaomanjun.sleepdownschedule.app.ui.Screen
import com.xiaomanjun.sleepdownschedule.core.performance.DockEntranceContainer
import com.xiaomanjun.sleepdownschedule.core.performance.StartupPhase
import com.xiaomanjun.sleepdownschedule.feature.agent.homeCountdownShockwave

/** A separate composition scope; overlay motion and its controller are not Dock inputs. */
@Composable
internal fun HomeDockChrome(
    visible: Boolean,
    parallel: Boolean,
    selectedIndex: Int,
    config: ScheduleConfigEntity,
    backdrop: Backdrop,
    phase: StartupPhase,
    buttonHidden: Boolean,
    onSelect: (Int) -> Unit,
    onOrdinaryHome: () -> Unit,
    onOpenMenu: (Rect, GraphicsLayer) -> Unit,
    modifier: Modifier = Modifier
) {
    if (!visible) return
    DockEntranceContainer(phase, modifier.homeCountdownShockwave(0.85f)) {
        if (parallel) {
            ParallelHomeDock(selectedIndex, config, backdrop, onSelect, onOpenMenu, buttonHidden)
        } else {
            val onConfig = remember(onSelect) { { onSelect(2) } }
            FloatingDock(
                selected = if (selectedIndex == 2) Screen.Config else Screen.Home,
                backdrop = backdrop,
                config = config,
                onHome = onOrdinaryHome,
                onConfig = onConfig
            )
        }
    }
}
