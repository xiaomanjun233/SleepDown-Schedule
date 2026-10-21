package com.xiaomanjun.sleepdownschedule.feature.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.kyant.backdrop.Backdrop
import com.xiaomanjun.sleepdownschedule.AppState
import com.xiaomanjun.sleepdownschedule.HomeStartMode
import com.xiaomanjun.sleepdownschedule.ScheduleConfigEntity
import com.xiaomanjun.sleepdownschedule.core.identity.AppDistribution
import com.xiaomanjun.sleepdownschedule.core.identity.AppIconManager
import com.xiaomanjun.sleepdownschedule.core.ui.chrome.DockScrollPadding
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.SleepDownSecondaryPageList
import com.xiaomanjun.sleepdownschedule.core.ui.material.LocalVisualEffects
import com.xiaomanjun.sleepdownschedule.core.ui.scaffold.detailContentTopPadding
import com.xiaomanjun.sleepdownschedule.core.ui.scaffold.settingsVisualConfig
import com.xiaomanjun.sleepdownschedule.core.ui.settings.GlassPreferenceSection
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsDivider
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsGroup
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsNavigationRow
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsToggleRow
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SleepDownLiquidDropdownPreference
import com.xiaomanjun.sleepdownschedule.feature.home.day.DayViewPreferences
import com.xiaomanjun.sleepdownschedule.feature.home.week.WeekViewPreferences
import com.xiaomanjun.sleepdownschedule.feature.update.GiteeAppUpdater
import com.xiaomanjun.sleepdownschedule.glass.ui.appUsesDarkTheme
import com.xiaomanjun.sleepdownschedule.model.VisualEffectMode
import com.xiaomanjun.sleepdownschedule.withGeneralSettingsFrom
import com.xiaomanjun.sleepdownschedule.switchCourseCardGlassMode

@Composable
fun GeneralSettingsScreen(
    state: AppState,
    backdrop: Backdrop?,
    onUpdateConfig: (ScheduleConfigEntity) -> Unit,
    onOpenLiquidGlass: () -> Unit = {},
    exitCommitRequest: Int = 0,
    onExitCommitFinished: (Boolean) -> Unit = {},
    onCommitAndExit: ((ScheduleConfigEntity, (Boolean) -> Unit) -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val topPadding = detailContentTopPadding()
    val effects = LocalVisualEffects.current
    var draft by remember(state.config.id) { mutableStateOf(state.config) }
    var hasLocalEdits by remember(state.config.id) { mutableStateOf(false) }
    var dayViewMode by remember(context, state.config.id) {
        mutableStateOf(
            DayViewPreferences.mode(
                context = context,
                legacyTwoDay = state.config.defaultHomeMode == HomeStartMode.TWO_DAY
            )
        )
    }
    var weekViewStyle by remember(context, state.config.id) {
        mutableStateOf(WeekViewPreferences.style(context))
    }
    var parallelNavigation by remember(context) {
        mutableStateOf(com.xiaomanjun.sleepdownschedule.feature.home.HomeNavigationPreferences.isParallel(context))
    }
    var appIconMode by remember(context) {
        mutableStateOf(AppIconManager.currentMode(context))
    }
    var appIconStyle by remember(context) {
        mutableStateOf(AppIconManager.currentStyle(context))
    }
    var includeBetaUpdates by remember(context) { mutableStateOf(GiteeAppUpdater.includesBeta(context)) }
    LaunchedEffect(state.config) {
        if (hasLocalEdits) {
            val rebased = state.config.withGeneralSettingsFrom(draft)
            draft = rebased
            if (rebased == state.config) hasLocalEdits = false
        } else {
            draft = state.config
        }
    }
    val visualConfig = settingsVisualConfig(draft)
    fun applyChange(next: ScheduleConfigEntity) {
        draft = next
        hasLocalEdits = true
        onUpdateConfig(state.config.withGeneralSettingsFrom(next))
    }
    LaunchedEffect(exitCommitRequest) {
        if (exitCommitRequest > 0) {
            // Rebase the latest local taps onto the newest database row. This keeps a stale
            // eager-save emission from rolling the UI back and preserves settings owned by
            // other pages (for example notification options).
            val latestDraft = state.config.withGeneralSettingsFrom(draft)
            if (onCommitAndExit != null) {
                onCommitAndExit(latestDraft, onExitCommitFinished)
            } else {
                if (hasLocalEdits) onUpdateConfig(latestDraft)
                onExitCommitFinished(true)
            }
        }
    }
    SleepDownSecondaryPageList(
        contentTopPadding = topPadding,
        contentBottomPadding = DockScrollPadding
    ) {
        item(key = "general-appearance") {
            GlassPreferenceSection("外观") {
                SettingsGroup(backdrop = backdrop, config = visualConfig, modifier = Modifier.fillMaxWidth()) {
                    val effectiveDarkMode = appUsesDarkTheme(draft)
                    SettingsThemeModeOptions(
                        darkMode = effectiveDarkMode,
                        defaultHomeMode = draft.defaultHomeMode,
                        onSelected = { applyChange(draft.copy(darkMode = it, followSystemDarkMode = false)) }
                    )
                    SettingsToggleRow(
                        title = "跟随系统",
                        subtitle = "",
                        checked = draft.followSystemDarkMode,
                        backdrop = backdrop,
                        onCheckedChange = {
                            applyChange(draft.copy(followSystemDarkMode = it, darkMode = effectiveDarkMode))
                        }
                    )
                    if (effects.colorOsAvailable) {
                        SettingsDivider()
                        SleepDownLiquidDropdownPreference(
                            title = "视效模式",
                            summary = if (effects.usesNativeControls) "使用 ColorOS 17 系统控件与凝光材质" else "使用液态玻璃材质",
                            items = listOf(VisualEffectMode.LiquidGlass.label, VisualEffectMode.ColorOsGlow.label),
                            selectedIndex = if (effects.usesNativeControls) 1 else 0,
                            backdrop = backdrop,
                            config = visualConfig,
                            onSelectedIndexChange = { index ->
                                val mode = if (index == 1) VisualEffectMode.ColorOsGlow else VisualEffectMode.LiquidGlass
                                if (mode == VisualEffectMode.ColorOsGlow && effects.selectedMode != mode) {
                                    onUpdateConfig(state.config.withGeneralSettingsFrom(draft).switchCourseCardGlassMode(true))
                                }
                                effects.selectMode(mode)
                            }
                        )
                    }
                    SettingsDivider()
                    SleepDownLiquidDropdownPreference(
                        title = "材质分级",
                        summary = when (com.xiaomanjun.sleepdownschedule.core.performance.AppMaterialPreferences.level) {
                            com.xiaomanjun.sleepdownschedule.core.performance.AppMaterialLevel.QUALITY -> "提供完整的液态玻璃和光感视效"
                            com.xiaomanjun.sleepdownschedule.core.performance.AppMaterialLevel.PERFORMANCE -> "提供部分液态玻璃和光感视效"
                            com.xiaomanjun.sleepdownschedule.core.performance.AppMaterialLevel.SUPER_PERFORMANCE -> "不提供任何液态玻璃和光感视效"
                        },
                        items = listOf("质感", "性能", "超级性能"),
                        selectedIndex = com.xiaomanjun.sleepdownschedule.core.performance.AppMaterialPreferences.level.ordinal,
                        backdrop = backdrop, config = visualConfig,
                        onSelectedIndexChange = {
                            com.xiaomanjun.sleepdownschedule.core.performance.AppMaterialPreferences.setLevel(
                                context, com.xiaomanjun.sleepdownschedule.core.performance.AppMaterialLevel.entries[it]
                            )
                        }
                    )
                    if (!effects.usesNativeControls &&
                        !com.xiaomanjun.sleepdownschedule.core.performance.AppMaterialPreferences.isSuperPerformance) {
                        SettingsDivider()
                        SettingsNavigationRow(
                            title = "视效设置",
                            subtitle = "调整液态玻璃效果",
                            onClick = onOpenLiquidGlass
                        )
                    }
                    SettingsDivider()
                    SettingsAppIconStyleRow(
                        selected = appIconStyle,
                        backdrop = backdrop,
                        config = visualConfig,
                        onSelected = { style ->
                            appIconStyle = style
                            AppIconManager.setStyle(context, style)
                        }
                    )
                    SettingsDivider()
                    SettingsAppIconModeRow(
                        selected = appIconMode,
                        backdrop = backdrop,
                        config = visualConfig,
                        onSelected = { mode ->
                            appIconMode = mode
                            AppIconManager.setMode(context, mode)
                        }
                    )
                }
            }
        }
        item(key = "general-navigation-layout") {
            GlassPreferenceSection("导航与布局") {
                SettingsGroup(backdrop = backdrop, config = visualConfig, modifier = Modifier.fillMaxWidth()) {
                    SleepDownLiquidDropdownPreference(
                        title = "导航模式",
                        summary = "手机竖屏下的底栏布局",
                        items = listOf("普通模式", "并列模式"),
                        selectedIndex = if (parallelNavigation) 1 else 0,
                        backdrop = backdrop, config = visualConfig,
                        onSelectedIndexChange = {
                            parallelNavigation = it == 1
                            com.xiaomanjun.sleepdownschedule.feature.home.HomeNavigationPreferences.setParallel(context, it == 1)
                        }
                    )
                    if (!parallelNavigation) {
                        SettingsDivider()
                        SettingsDockAlignmentRow(
                            selected = draft.dockAlignment,
                            backdrop = backdrop,
                            config = visualConfig,
                            onSelected = { applyChange(draft.copy(dockAlignment = it)) }
                        )
                    }
                    SettingsDivider()
                    SettingsHomeStartModeRow(
                        selected = draft.defaultHomeMode,
                        backdrop = backdrop,
                        config = visualConfig,
                        onSelected = { applyChange(draft.copy(defaultHomeMode = it)) }
                    )
                    SettingsDivider()
                    SettingsDayViewModeRow(
                        selected = dayViewMode,
                        backdrop = backdrop,
                        config = visualConfig,
                        onSelected = { mode ->
                            dayViewMode = mode
                            DayViewPreferences.setMode(context, mode)
                            if (draft.defaultHomeMode == HomeStartMode.TWO_DAY) {
                                applyChange(draft.copy(defaultHomeMode = HomeStartMode.DAY))
                            }
                        }
                    )
                    SettingsDivider()
                    SettingsWeekViewStyleRow(
                        selected = weekViewStyle,
                        backdrop = backdrop,
                        config = visualConfig,
                        onSelected = { style ->
                            weekViewStyle = style
                            WeekViewPreferences.setStyle(context, style)
                        }
                    )
                    SettingsDivider()
                    SettingsDefaultWallpaperRow(
                        selected = draft.defaultWallpaperStyle,
                        backdrop = backdrop,
                        config = visualConfig,
                        onSelected = { applyChange(draft.copy(defaultWallpaperStyle = it)) }
                    )
                }
            }
        }
        item(key = "general-system-behavior") {
            GlassPreferenceSection("系统行为") {
                SettingsGroup(backdrop = backdrop, config = visualConfig, modifier = Modifier.fillMaxWidth()) {
                SettingsToggleRow(
                    title = "隐藏后台卡片",
                    subtitle = "以任意方式离开应用后，都从最近任务列表中隐藏本应用",
                    checked = draft.hideFromRecents,
                    backdrop = backdrop,
                    onCheckedChange = { applyChange(draft.copy(hideFromRecents = it)) }
                )
                if (AppDistribution.supportsSelfUpdate) {
                    SettingsDivider()
                    SettingsToggleRow(
                        title = "自动检查更新",
                        subtitle = "每天首次打开应用时检查 Gitee 上的新版本",
                        checked = draft.autoCheckUpdates,
                        backdrop = backdrop,
                        onCheckedChange = { applyChange(draft.copy(autoCheckUpdates = it)) }
                    )
                    SettingsDivider()
                    SettingsToggleRow(
                        title = "接收 Beta 版更新",
                        subtitle = if (includeBetaUpdates) "更新渠道：正式版与 Beta 版；同版本正式版发布后也会提示"
                            else "更新渠道：仅正式版；开启后可提前体验 Beta 版",
                        checked = includeBetaUpdates,
                        backdrop = backdrop,
                        onCheckedChange = {
                            includeBetaUpdates = it
                            GiteeAppUpdater.setIncludesBeta(context, it)
                        }
                    )
                }
                }
            }
        }
    }
}
