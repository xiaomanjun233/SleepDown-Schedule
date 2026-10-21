package com.xiaomanjun.sleepdownschedule.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.effects.blur
import com.xiaomanjun.sleepdownschedule.AppState
import com.xiaomanjun.sleepdownschedule.R
import com.xiaomanjun.sleepdownschedule.core.ui.chrome.HomeIconButton
import com.xiaomanjun.sleepdownschedule.core.ui.chrome.TopBackButton
import com.xiaomanjun.sleepdownschedule.core.ui.interaction.excludeParentPullGesture
import com.xiaomanjun.sleepdownschedule.core.ui.scaffold.detailTopOverlayHeight
import com.xiaomanjun.sleepdownschedule.effectiveCurrentWeek
import com.xiaomanjun.sleepdownschedule.feature.home.boundlessHomeTopGradientHeight
import com.xiaomanjun.sleepdownschedule.feature.home.day.HomeDateTitle
import com.xiaomanjun.sleepdownschedule.feature.home.overlay.HomeAnchoredOverlayKind
import com.xiaomanjun.sleepdownschedule.feature.home.rememberHomeAdaptiveMetrics
import com.xiaomanjun.sleepdownschedule.feature.settings.SettingsPage
import com.xiaomanjun.sleepdownschedule.feature.settings.title
import com.xiaomanjun.sleepdownschedule.glass.ui.LocalAdaptiveGlass
import com.xiaomanjun.sleepdownschedule.model.HomeMode
import java.time.LocalDate

@Composable
internal fun rootTopBarLayoutHeight(
    screen: Screen,
    boundlessWeekHeader: Boolean = false
): Dp {
    return when (screen) {
        Screen.Home -> {
            val metrics = rememberHomeAdaptiveMetrics()
            if (boundlessWeekHeader) {
                // Measure the whole visual gradient envelope, not just the header: otherwise
                // the parent's maximum height truncates the blur before its fade-out tail.
                boundlessHomeTopGradientHeight(metrics)
            } else {
                metrics.topOverlayHeight
            }
        }
        Screen.Config -> detailTopOverlayHeight()
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun AppTopBar(
    screen: Screen,
    state: AppState,
    settingsPage: SettingsPage,
    backdrop: Backdrop?,
    homeMode: HomeMode,
    onHomeModeChange: (HomeMode) -> Unit,
    homeDisplayDate: LocalDate,
    homeDisplayWeek: Int,
    beforeScheduleTerm: Boolean,
    afterScheduleTerm: Boolean,
    homeShowingAnotherWeek: Boolean,
    onReturnHomeToCurrentWeek: () -> Unit,
    activeHomeOverlay: HomeAnchoredOverlayKind?,
    addButtonHidden: Boolean,
    onAddButtonPositioned: (androidx.compose.ui.geometry.Rect) -> Unit,
    onPersonalizeButtonPositioned: (androidx.compose.ui.geometry.Rect) -> Unit,
    onToggleAddMenu: (Float) -> Unit,
    onTogglePersonalize: (Float) -> Unit,
    onBackHome: () -> Unit
) {
    val adaptiveTopBarColor = LocalAdaptiveGlass.current.contentColor
    val topBarMetrics = rememberHomeAdaptiveMetrics()
    val landscape = topBarMetrics.isTabletLandscape
    val homeTextColor = adaptiveTopBarColor
    if (screen is Screen.Home) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility)
                .height(66.dp)
                .graphicsLayer { clip = false }
        ) {
            // Keep Material's bar shell, but render both the title and animated liquid controls as
            // siblings so neither is clipped by TopAppBar's internal single-line title layout.
            TopAppBar(
                modifier = Modifier.fillMaxSize(),
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = ComposeColor.Transparent,
                    scrolledContainerColor = ComposeColor.Transparent,
                    titleContentColor = homeTextColor,
                    actionIconContentColor = homeTextColor,
                    navigationIconContentColor = homeTextColor
                ),
                title = {}
            )
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .padding(start = if (landscape) topBarMetrics.tabletContentMargin else 16.dp,
                        end = if (landscape) 64.dp else 120.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                HomeDateTitle(
                    state = state,
                    displayDate = if (homeMode == HomeMode.Day) homeDisplayDate else LocalDate.now(),
                    displayWeek = if (homeMode == HomeMode.Day) effectiveCurrentWeek(state.config, homeDisplayDate) else homeDisplayWeek,
                    showTwoDays = false,
                    beforeScheduleTerm = beforeScheduleTerm,
                    afterScheduleTerm = afterScheduleTerm,
                    showReturnToCurrentWeekHint = homeMode == HomeMode.Week && homeDisplayWeek != effectiveCurrentWeek(state.config),
                    showWeather = homeMode == HomeMode.Week,
                    onReturnCurrent = onReturnHomeToCurrentWeek
                )
            }
            Row(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    // Match Material TopAppBar's 4dp action inset plus the existing row inset.
                    .padding(top = 2.dp, end = if (topBarMetrics.isLargeScreen)
                        (topBarMetrics.tabletContentMargin - 7.dp).coerceAtLeast(0.dp) else 8.dp)
                    .excludeParentPullGesture()
                    .graphicsLayer { clip = false },
                verticalAlignment = Alignment.CenterVertically
            ) {
                HomeIconButton(
                    backdrop = backdrop,
                    config = state.config,
                    iconRes = R.drawable.ic_edit,
                    contentDescription = "benchmark_personalize_button",
                    selected = false,
                    visible = activeHomeOverlay != HomeAnchoredOverlayKind.Personalize,
                    onClick = onTogglePersonalize,
                    onButtonPositioned = onPersonalizeButtonPositioned
                )
                if (!landscape) HomeIconButton(
                    backdrop = backdrop,
                    config = state.config,
                    iconRes = R.drawable.ic_more_horizontal,
                    contentDescription = "添加菜单",
                    selected = false,
                    visible = !addButtonHidden,
                    onClick = onToggleAddMenu,
                    onButtonPositioned = onAddButtonPositioned
                )
            }
        }
        return
    }
    TopAppBar(
        modifier = Modifier.windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility).height(66.dp),
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = ComposeColor.Transparent,
            scrolledContainerColor = ComposeColor.Transparent,
            titleContentColor = if (screen is Screen.Home) homeTextColor else MaterialTheme.colorScheme.onBackground,
            actionIconContentColor = if (screen is Screen.Home) homeTextColor else MaterialTheme.colorScheme.onBackground,
            navigationIconContentColor = if (screen is Screen.Home) homeTextColor else MaterialTheme.colorScheme.onBackground
        ),
        title = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    when (settingsPage) {
                        SettingsPage.Root -> "设置"
                        SettingsPage.General -> "通用设置"
                        SettingsPage.LiquidGlass -> "视效设置"
                        SettingsPage.Widgets -> "小组件设置"
                        SettingsPage.AiImport -> "AI 设置"
                        SettingsPage.DayAgent -> "AI 助理"
                        SettingsPage.Schedule -> "课表详细设置"
                        SettingsPage.PeriodSchemes -> "作息管理"
                        SettingsPage.AutoRefreshSchedule -> "自动刷新课表"
                        SettingsPage.Notifications -> "通知设置"
                        SettingsPage.ScheduleManager -> "课表设置"
                        SettingsPage.BackupRestore -> "备份与恢复"
                        SettingsPage.WebDav -> "WebDAV 备份"
                        SettingsPage.BackupPreview -> "恢复预览"
                        SettingsPage.About -> "关于应用"
                        SettingsPage.Changelog -> "关于应用"
                        SettingsPage.Donate -> "捐赠支持"
						SettingsPage.PrivacyPolicy -> "隐私政策"
                    },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 28.sp
                )
            }
        },
        navigationIcon = {
            if (screen is Screen.Config && settingsPage != SettingsPage.Root) {
                TopBackButton(
                    backdrop = backdrop,
                    config = state.config,
                    onClick = onBackHome,
                    modifier = Modifier.padding(start = 16.dp).size(42.dp)
                )
            }
        },
        actions = {}
    )
}
