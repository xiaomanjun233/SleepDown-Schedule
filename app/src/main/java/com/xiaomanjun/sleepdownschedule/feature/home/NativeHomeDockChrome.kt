package com.xiaomanjun.sleepdownschedule.feature.home

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import com.itos.coui.backdrop.CouiBackdrop
import com.xiaomanjun.sleepdownschedule.ScheduleConfigEntity
import com.xiaomanjun.sleepdownschedule.core.performance.DockEntranceContainer
import com.xiaomanjun.sleepdownschedule.core.performance.StartupPhase
import com.xiaomanjun.sleepdownschedule.core.ui.material.LocalHomeMaterialControls
import com.xiaomanjun.sleepdownschedule.core.ui.material.DockImportSource
import com.xiaomanjun.sleepdownschedule.core.ui.material.LocalVisualEffects
import com.xiaomanjun.sleepdownschedule.feature.agent.homeCountdownShockwave

/**
 * Dock 路由：Android 14+ 的凝光模式优先使用 COUI-Kit 胶囊；创建失败或旧系统回到
 * 既有原生/液态呈现。偏好不在这里回写，失败原因由渲染器创建处记录。
 */
@Composable
internal fun NativeHomeDockChrome(
    visible: Boolean, selectedIndex: Int, dark: Boolean,
    dockDark: Boolean, dockForeground: Int?, dockSampleBrightness: Float,
    buttonHidden: Boolean,
    config: ScheduleConfigEntity,
    couiBackdrop: CouiBackdrop?,
    onSelect: (Int) -> Unit, onOpenMenu: (DockImportSource) -> Unit,
    fallback: State<@Composable () -> Unit>,
    phase: StartupPhase,
    modifier: Modifier = Modifier
) {
    if (!visible) return
    // Aggregate composition commits only; no user data or per-frame state is read here.
    SideEffect {
        Log.i("SleepDownCOUI", "Dock commit: selected=$selectedIndex, dark=$dark, hidden=$buttonHidden, " +
            "coui=${couiBackdrop != null}")
    }
    DockEntranceContainer(phase, modifier.homeCountdownShockwave(0.85f)) {
        val effects = LocalVisualEffects.current
        if (effects.usesCouiDock && couiBackdrop != null &&
            CouiHomeDock(
                selectedIndex = selectedIndex,
                dark = dark,
                dockDark = dockDark,
                dockForeground = dockForeground,
                dockSampleBrightness = dockSampleBrightness,
                buttonHidden = buttonHidden,
                config = config,
                backdrop = couiBackdrop,
                onSelect = onSelect,
                onOpenMenu = onOpenMenu,
            )
        ) return@DockEntranceContainer
        if (!LocalHomeMaterialControls.current.ParallelDock(
                selectedIndex, dark, buttonHidden, Modifier, onSelect, onOpenMenu
            )) {
            NativeDockFallback(fallback)
        }
    }
}

@Composable
private fun NativeDockFallback(content: State<@Composable () -> Unit>) { content.value() }
