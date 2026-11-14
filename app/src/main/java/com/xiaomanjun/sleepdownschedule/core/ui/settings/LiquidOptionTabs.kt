package com.xiaomanjun.sleepdownschedule.core.ui.settings

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.xiaomanjun.sleepdownschedule.ScheduleConfigEntity
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.ControlSegments
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.SegmentControlRequest
import com.xiaomanjun.sleepdownschedule.core.ui.theme.appUsesDarkTheme
import com.xiaomanjun.sleepdownschedule.glass.ui.glassUsesLightStyle

@Composable
fun LiquidOptionTabs(
    selectedIndex: Int,
    labels: List<String>,
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    width: Dp,
    highContrast: Boolean = false,
    followAppTheme: Boolean = false,
    onSelected: (Int) -> Unit
) {
    ControlSegments(SegmentControlRequest(
        labels, selectedIndex, onSelected, Modifier.width(width), height = 40.dp,
        dark = if (followAppTheme) appUsesDarkTheme(config) else !glassUsesLightStyle(config),
        backdrop = backdrop), highContrast)
}
