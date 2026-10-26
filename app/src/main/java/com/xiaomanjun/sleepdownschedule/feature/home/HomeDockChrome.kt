package com.xiaomanjun.sleepdownschedule.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.layer.GraphicsLayer
import com.kyant.backdrop.Backdrop
import com.xiaomanjun.sleepdownschedule.ScheduleConfigEntity
import com.xiaomanjun.sleepdownschedule.core.performance.DockEntranceContainer
import com.xiaomanjun.sleepdownschedule.core.performance.StartupPhase
import com.xiaomanjun.sleepdownschedule.feature.agent.homeCountdownShockwave

/** A separate composition scope; overlay motion and its controller are not Dock inputs. */
@Composable
internal fun HomeDockChrome(
    visible: Boolean,
    selectedIndex: Int,
    config: ScheduleConfigEntity,
    backdrop: Backdrop,
    phase: StartupPhase,
    buttonHidden: Boolean,
    onSelect: (Int) -> Unit,
    onOpenMenu: (Rect, GraphicsLayer) -> Unit,
    modifier: Modifier = Modifier
) {
    if (!visible) return
    DockEntranceContainer(phase, modifier.homeCountdownShockwave(0.85f)) {
        ParallelHomeDock(selectedIndex, config, backdrop, onSelect, onOpenMenu, buttonHidden)
    }
}
