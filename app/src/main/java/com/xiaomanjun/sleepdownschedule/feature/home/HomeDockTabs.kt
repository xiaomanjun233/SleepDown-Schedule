package com.xiaomanjun.sleepdownschedule.feature.home

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.catalog.components.LiquidBottomTab
import com.kyant.backdrop.catalog.components.LiquidBottomTabs
import com.xiaomanjun.sleepdownschedule.R
import com.xiaomanjun.sleepdownschedule.ScheduleConfigEntity
import com.xiaomanjun.sleepdownschedule.core.ui.chrome.HomeLightGlassSelectedAccentColor
import com.xiaomanjun.sleepdownschedule.core.ui.chrome.HomeLightGlassSurfaceColor
import com.xiaomanjun.sleepdownschedule.core.ui.chrome.homeChromeBlur
import com.xiaomanjun.sleepdownschedule.core.ui.chrome.homeChromeGlassSurfaceAlpha
import com.xiaomanjun.sleepdownschedule.core.ui.material.LocalHomeMaterialControls
import com.xiaomanjun.sleepdownschedule.glass.ui.GlassPill

/** Both phone modes use the same Dock cells and material; parallel mode adds one cell. */
@Composable
internal fun HomeDockTabs(
    selectedIndex: Int,
    parallel: Boolean,
    config: ScheduleConfigEntity,
    backdrop: Backdrop?,
    lightGlass: Boolean,
    modifier: Modifier = Modifier,
    containerSurfaceEnabled: Boolean = true,
    onSelect: (Int) -> Unit
) {
    val tabs = if (parallel) listOf(
        R.drawable.ic_day_view to "今日", R.drawable.ic_week_view to "课程表", R.drawable.ic_settings to "设置"
    ) else listOf(R.drawable.ic_week_view to "课程", R.drawable.ic_settings to "设置")
    if (LocalHomeMaterialControls.current.Dock(tabs, selectedIndex, dark = !lightGlass,
            modifier = modifier, onSelect = onSelect)) return
    if (backdrop == null) {
        GlassPill(backdrop = null, config = config, modifier = modifier) {
            Row(Modifier.height(54.dp).padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                tabs.forEachIndexed { index, (icon, label) ->
                    DockItem(selectedIndex == index, null, config, icon, label) { onSelect(index) }
                }
            }
        }
        return
    }
    LiquidBottomTabs(
        selectedTabIndex = { selectedIndex }, onTabSelected = onSelect,
        backdrop = backdrop, tabsCount = tabs.size, modifier = modifier,
        containerHeight = 54.dp, indicatorHeight = 46.dp,
        blurRadius = homeChromeBlur(1.3.dp, config),
        containerAlpha = homeChromeGlassSurfaceAlpha(lightGlass), lensHeight = 10.dp, lensAmount = 40.dp,
        indicatorWidthOverflow = 8.dp, indicatorHeightOverflow = 4.dp,
        indicatorLensHeight = 12.dp, indicatorLensAmount = 17.dp,
        officialHighlightAlpha = 0.07f, officialShadowAlpha = 0.05f, officialInnerShadowAlpha = 0.08f,
        chromaticAberrationEnabled = true, isLightThemeOverride = lightGlass,
        lightContainerColor = HomeLightGlassSurfaceColor, lightAccentColor = HomeLightGlassSelectedAccentColor,
        useOfficialGlassParameters = true, containerSurfaceEnabled = containerSurfaceEnabled
    ) {
        tabs.forEachIndexed { index, (icon, label) ->
            LiquidBottomTab(onClick = { onSelect(index) }) {
                DockTabContent(icon, label, if (icon == R.drawable.ic_settings) 24.dp else 23.dp)
            }
        }
    }
}
