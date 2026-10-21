package com.xiaomanjun.sleepdownschedule.feature.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.xiaomanjun.sleepdownschedule.DefaultWallpaperStyle
import com.xiaomanjun.sleepdownschedule.DockAlignment
import com.xiaomanjun.sleepdownschedule.HomeStartMode
import com.xiaomanjun.sleepdownschedule.ScheduleConfigEntity
import com.xiaomanjun.sleepdownschedule.core.identity.AppIconMode
import com.xiaomanjun.sleepdownschedule.core.identity.AppIconStyle
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SleepDownLiquidDropdownPreference
import com.xiaomanjun.sleepdownschedule.feature.home.day.DayViewMode
import com.xiaomanjun.sleepdownschedule.feature.home.week.WeekViewStyle
import kotlinx.coroutines.flow.map

@Composable
fun SettingsAppIconStyleRow(
    selected: AppIconStyle,
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    onSelected: (AppIconStyle) -> Unit
) {
    val options = AppIconStyle.entries
    val selectedIndex = options.indexOf(selected).coerceAtLeast(0)
    SleepDownLiquidDropdownPreference(
        items = options.map { it.label },
        selectedIndex = selectedIndex,
        title = "图标风格",
        backdrop = backdrop,
        config = config,
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        maxHeight = 260.dp,
        onExpandedChange = {},
        onSelectedIndexChange = { index -> options.getOrNull(index)?.let(onSelected) }
    )
}

@Composable
fun SettingsAppIconModeRow(
    selected: AppIconMode,
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    onSelected: (AppIconMode) -> Unit
) {
    val options = AppIconMode.entries
    val selectedIndex = options.indexOf(selected).coerceAtLeast(0)
    SleepDownLiquidDropdownPreference(
        items = options.map { it.label },
        selectedIndex = selectedIndex,
        title = "应用图标深浅色",
        backdrop = backdrop,
        config = config,
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        maxHeight = 260.dp,
        onExpandedChange = {},
        onSelectedIndexChange = { index -> options.getOrNull(index)?.let(onSelected) }
    )
}

@Composable
fun SettingsDockAlignmentRow(
    selected: DockAlignment,
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    onSelected: (DockAlignment) -> Unit
) {
    val options = DockAlignment.entries
    val labels = mapOf(
        DockAlignment.LEFT to "左侧",
        DockAlignment.CENTER to "居中",
        DockAlignment.RIGHT to "右侧"
    )
    SleepDownLiquidDropdownPreference(
        items = options.map { labels.getValue(it) },
        selectedIndex = options.indexOf(selected).coerceAtLeast(0),
        title = "Dock 栏位置",
        backdrop = backdrop,
        config = config,
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        maxHeight = 260.dp,
        onExpandedChange = {},
        onSelectedIndexChange = { index -> options.getOrNull(index)?.let(onSelected) }
    )
}

@Composable
fun SettingsHomeStartModeRow(
    selected: HomeStartMode,
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    onSelected: (HomeStartMode) -> Unit
) {
    val options = listOf(HomeStartMode.DAY, HomeStartMode.WEEK)
    val normalizedSelected = if (selected == HomeStartMode.TWO_DAY) HomeStartMode.DAY else selected
    SleepDownLiquidDropdownPreference(
        items = listOf("日视图", "周视图"),
        selectedIndex = options.indexOf(normalizedSelected).coerceAtLeast(0),
        title = "默认首页视图",
        backdrop = backdrop,
        config = config,
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        maxHeight = 240.dp,
        onExpandedChange = {},
        onSelectedIndexChange = { index -> options.getOrNull(index)?.let(onSelected) }
    )
}

@Composable
fun SettingsDefaultWallpaperRow(
    selected: DefaultWallpaperStyle,
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    onSelected: (DefaultWallpaperStyle) -> Unit
) {
    val options = DefaultWallpaperStyle.entries
    SleepDownLiquidDropdownPreference(
        items = options.map { style ->
            if (style == DefaultWallpaperStyle.KANBAN) "看板娘" else "无"
        },
        selectedIndex = options.indexOf(selected).coerceAtLeast(0),
        title = "默认壁纸",
        backdrop = backdrop,
        config = config,
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        maxHeight = 240.dp,
        onExpandedChange = {},
        onSelectedIndexChange = { index -> options.getOrNull(index)?.let(onSelected) }
    )
}

@Composable
internal fun SettingsDayViewModeRow(
    selected: DayViewMode,
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    onSelected: (DayViewMode) -> Unit
) {
    val options = DayViewMode.entries
    SleepDownLiquidDropdownPreference(
        items = listOf("默认模式", "两日模式"),
        selectedIndex = options.indexOf(selected).coerceAtLeast(0),
        title = "日视图模式",
        backdrop = backdrop,
        config = config,
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        maxHeight = 220.dp,
        onExpandedChange = {},
        onSelectedIndexChange = { index -> options.getOrNull(index)?.let(onSelected) }
    )
}

@Composable
internal fun SettingsWeekViewStyleRow(
    selected: WeekViewStyle,
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    onSelected: (WeekViewStyle) -> Unit
) {
    val options = WeekViewStyle.entries
    SleepDownLiquidDropdownPreference(
        items = listOf("普通模式", "无界模式"),
        selectedIndex = options.indexOf(selected).coerceAtLeast(0),
        title = "周视图模式",
        backdrop = backdrop,
        config = config,
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        maxHeight = 220.dp,
        onExpandedChange = {},
        onSelectedIndexChange = { index -> options.getOrNull(index)?.let(onSelected) }
    )
}
