package com.xiaomanjun.sleepdownschedule.core.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.catalog.components.LiquidBottomTab
import com.kyant.backdrop.catalog.components.LiquidBottomTabs
import com.xiaomanjun.sleepdownschedule.ScheduleConfigEntity
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LocalNativeSegmentRenderer
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.NativeSegmentRequest
import com.xiaomanjun.sleepdownschedule.glass.ui.appPanelForegroundColor
import com.xiaomanjun.sleepdownschedule.glass.ui.appUsesDarkTheme
import com.xiaomanjun.sleepdownschedule.glass.ui.glassForegroundColor
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
    if (com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LocalNativeSegmentRenderer.current?.invoke(
            com.xiaomanjun.sleepdownschedule.core.ui.designsystem.NativeSegmentRequest(
                labels, selectedIndex, onSelected, Modifier.width(width),
                dark = if (followAppTheme) appUsesDarkTheme(config) else !glassUsesLightStyle(config)
            )) == true) return
    if (backdrop != null) {
        val tabContentColor = if (followAppTheme) {
            appPanelForegroundColor(config)
        } else {
            glassForegroundColor(config)
        }
        CompositionLocalProvider(LocalContentColor provides tabContentColor) {
            LiquidBottomTabs(
                selectedTabIndex = { selectedIndex.coerceIn(labels.indices) },
                onTabSelected = { index -> onSelected(index.coerceIn(labels.indices)) },
                backdrop = backdrop,
                tabsCount = labels.size,
                modifier = Modifier.width(width),
                containerHeight = 42.dp,
                indicatorHeight = 34.dp,
                horizontalPadding = 4.dp,
                blurRadius = if (highContrast) 7.dp else 4.dp,
                containerAlpha = if (highContrast) 0.62f else 0.4f,
                lensHeight = 24.dp,
                lensAmount = 24.dp,
                indicatorWidthOverflow = 0.dp,
                indicatorHeightOverflow = 0.dp,
                pressedContentScale = 1.04f,
                chromaticAberrationEnabled = !highContrast,
                isLightThemeOverride = if (followAppTheme) {
                    !appUsesDarkTheme(config)
                } else {
                    glassUsesLightStyle(config)
                },
                useOfficialGlassParameters = true
            ) {
                labels.forEachIndexed { index, label ->
                    LiquidBottomTab(onClick = { onSelected(index) }) {
                        Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            labels.forEachIndexed { index, label ->
                SettingsFallbackChip(label, selectedIndex == index) { onSelected(index) }
            }
        }
    }
}
