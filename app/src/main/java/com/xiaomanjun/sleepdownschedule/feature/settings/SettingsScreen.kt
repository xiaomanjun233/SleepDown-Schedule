package com.xiaomanjun.sleepdownschedule.feature.settings

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kyant.backdrop.Backdrop
import com.xiaomanjun.sleepdownschedule.AppState
import com.xiaomanjun.sleepdownschedule.PeriodEntity
import com.xiaomanjun.sleepdownschedule.R
import com.xiaomanjun.sleepdownschedule.ScheduleConfigEntity
import com.xiaomanjun.sleepdownschedule.core.ui.chrome.TopGlassIconButton
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.SleepDownDesignTokens
import com.xiaomanjun.sleepdownschedule.core.ui.scaffold.settingsPageBackground
import com.xiaomanjun.sleepdownschedule.core.ui.scaffold.settingsVisualConfig
import com.xiaomanjun.sleepdownschedule.core.ui.settings.GlassMiuixSettingsTheme
import com.xiaomanjun.sleepdownschedule.core.ui.settings.GlassMiuixTabletDetailPaneScaffold
import com.xiaomanjun.sleepdownschedule.core.ui.settings.LocalSettingsSharedGradient
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsSharedGradient
import com.xiaomanjun.sleepdownschedule.core.ui.settings.rememberSettingsPaneBackground
import com.xiaomanjun.sleepdownschedule.feature.backup.BackupRestorePreviewScreen
import com.xiaomanjun.sleepdownschedule.feature.backup.BackupRestoreSettingsScreen
import com.xiaomanjun.sleepdownschedule.feature.home.AdaptivePaneTransition
import com.xiaomanjun.sleepdownschedule.feature.home.rememberHomeAdaptiveMetrics
import com.xiaomanjun.sleepdownschedule.feature.importing.DonateSettingsScreen
import com.xiaomanjun.sleepdownschedule.feature.schedule.autorefresh.AutoRefreshScheduleStore
import com.xiaomanjun.sleepdownschedule.feature.schedule.manager.ScheduleManagerScreen
import com.xiaomanjun.sleepdownschedule.feature.settings.about.ChangelogSettingsScreen
import com.xiaomanjun.sleepdownschedule.feature.settings.about.aboutPageGradientColors
import com.xiaomanjun.sleepdownschedule.feature.widget.WidgetCustomizationScreen
import com.xiaomanjun.sleepdownschedule.glass.ui.appUsesDarkTheme

@Composable
fun SettingsScreen(
    page: SettingsPage,
    state: AppState,
    backdrop: Backdrop?,
    onPageChange: (SettingsPage) -> Unit,
    onSave: (ScheduleConfigEntity, List<PeriodEntity>) -> Unit,
    onUpdateConfig: (ScheduleConfigEntity) -> Unit,
    onUpdateGeneralConfig: (ScheduleConfigEntity) -> Unit = onUpdateConfig,
    onUpdateHomeChromeBlurScale: (Float) -> Unit,
    onPreviewLiveUpdate: (ScheduleConfigEntity) -> Unit,
    onCreateSchedule: (String) -> Unit = {},
    onActivateSchedule: (Int, (() -> Unit)?) -> Unit = { _, _ -> },
    onRenameSchedule: (Int, String) -> Unit = { _, _ -> },
    onDeleteSchedule: (Int) -> Unit = {},
    exitCommitRequest: Int = 0,
    onExitCommitFinished: (Boolean) -> Unit = {},
    onExitInterceptionChange: (Boolean) -> Unit = {},
    onTabletAboutBackgroundChanged: (List<ComposeColor>?) -> Unit = {}
) {
    val context = LocalContext.current
    val pageConfig = settingsVisualConfig(state.config)
    val pageState = state.copy(config = pageConfig)
    val adaptiveMetrics = rememberHomeAdaptiveMetrics()
    val latestAboutBackgroundChanged by rememberUpdatedState(onTabletAboutBackgroundChanged)
    DisposableEffect(Unit) {
        onDispose { latestAboutBackgroundChanged(null) }
    }
    var backupPreviewUriString by rememberSaveable { mutableStateOf<String?>(null) }
    var autoRefreshWarehouseRequest by remember { mutableIntStateOf(0) }
    val autoRefreshProfile = AutoRefreshScheduleStore.observe(context).collectAsStateWithLifecycle().value
    GlassMiuixSettingsTheme(pageConfig) {
        if (page == SettingsPage.Root && adaptiveMetrics.isLargeScreen) {
            var tabletNavigation by rememberSaveable(
                stateSaver = TabletSettingsNavigationStateSaver
            ) {
                mutableStateOf(TabletSettingsNavigationState())
            }
            var detailNavigationDirection by remember { mutableIntStateOf(0) }
            var periodSchemeReturnRequest by remember { mutableIntStateOf(0) }
            var tabletWidgetEditorVisible by remember { mutableStateOf(false) }
            var pendingPageName by remember { mutableStateOf<String?>(null) }
            var scheduleExitInFlight by remember { mutableStateOf(false) }
            var scheduleExitRequest by remember { mutableIntStateOf(0) }
            var externalExitInFlight by remember { mutableStateOf(false) }
            var lastHandledExternalExitRequest by remember { mutableIntStateOf(exitCommitRequest) }
            var exitInterceptionByPage by remember {
                mutableStateOf<Map<SettingsPage, Boolean>>(emptyMap())
            }
            val selectedPage = tabletNavigation.rootPage
            val displayedPage = tabletNavigation.displayedPage
            val portrait = adaptiveMetrics.screenHeight > adaptiveMetrics.screenWidth
            val navigationWidth = if (portrait) {
                (adaptiveMetrics.contentWidth * 0.38f).coerceIn(280.dp, 328.dp)
            } else {
                (adaptiveMetrics.contentWidth * 0.31f).coerceIn(336.dp, 408.dp)
            }.coerceAtMost(adaptiveMetrics.contentWidth * 0.48f)
            LaunchedEffect(displayedPage) {
                if (displayedPage != SettingsPage.Widgets) tabletWidgetEditorVisible = false
            }
            val interceptRootExit = tabletNavigation.detailPages.isEmpty() &&
                exitInterceptionByPage[selectedPage] == true
            LaunchedEffect(interceptRootExit) {
                onExitInterceptionChange(interceptRootExit)
            }
            DisposableEffect(onExitInterceptionChange) {
                onDispose { onExitInterceptionChange(false) }
            }
            fun applyTabletRootPage(nextPage: SettingsPage) {
                detailNavigationDirection = 0
                tabletNavigation = tabletNavigation.selectRoot(nextPage)
                backupPreviewUriString = null
                pendingPageName = null
            }
            fun requestTabletRootPage(nextPage: SettingsPage) {
                if (
                    tabletNavigation.detailPages.isEmpty() &&
                    exitInterceptionByPage[selectedPage] == true
                ) {
                    pendingPageName = nextPage.name
                    if (!scheduleExitInFlight) {
                        scheduleExitInFlight = true
                        scheduleExitRequest++
                    }
                } else {
                    applyTabletRootPage(nextPage)
                }
            }
            fun pushTabletDetailPage(nextPage: SettingsPage) {
                val nextNavigation = tabletNavigation.pushDetail(nextPage)
                if (nextNavigation == tabletNavigation) return
                detailNavigationDirection = 1
                tabletNavigation = nextNavigation
            }
            fun popTabletDetailPage() {
                val nextNavigation = tabletNavigation.popDetail()
                if (nextNavigation == tabletNavigation) return
                if (displayedPage == SettingsPage.PeriodSchemes) periodSchemeReturnRequest++
                detailNavigationDirection = -1
                tabletNavigation = nextNavigation
            }
            LaunchedEffect(exitCommitRequest) {
                if (exitCommitRequest == lastHandledExternalExitRequest) return@LaunchedEffect
                lastHandledExternalExitRequest = exitCommitRequest
                if (
                    tabletNavigation.detailPages.isEmpty() &&
                    exitInterceptionByPage[selectedPage] == true
                ) {
                    externalExitInFlight = true
                    if (!scheduleExitInFlight) {
                        scheduleExitInFlight = true
                        scheduleExitRequest++
                    }
                } else {
                    onExitCommitFinished(true)
                }
            }
            BackHandler(enabled = tabletNavigation.detailPages.isNotEmpty()) {
                popTabletDetailPage()
            }
            val aboutColors = aboutPageGradientColors(appUsesDarkTheme(pageConfig))
            val aboutBackground = remember(aboutColors) { SettingsSharedGradient(aboutColors) }
            val showAboutBackground = displayedPage == SettingsPage.About || displayedPage == SettingsPage.Changelog
            LaunchedEffect(showAboutBackground, aboutColors) {
                latestAboutBackgroundChanged(aboutColors.takeIf { showAboutBackground })
            }
            val inheritedBackground = LocalSettingsSharedGradient.current
            val sharedBackground = (inheritedBackground ?: aboutBackground).takeIf { showAboutBackground }
            CompositionLocalProvider(LocalSettingsSharedGradient provides sharedBackground) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .onGloballyPositioned {
                        if (inheritedBackground == null) aboutBackground.boundsInRoot = it.boundsInRoot()
                    }
                    .then(rememberSettingsPaneBackground(settingsPageBackground(pageConfig)).modifier)
            ) {
                Box(Modifier.width(navigationWidth).fillMaxHeight()) {
                    SettingsRootScreen(
                        state = pageState,
                        backdrop = backdrop,
                        selectedPage = selectedPage,
                        onPageChange = ::requestTabletRootPage
                    )
                }
                Box(
                    Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.46f))
                )
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clipToBounds()
                ) {
                    AdaptivePaneTransition(
                        target = displayedPage, landscape = adaptiveMetrics.isTabletLandscape,
                        portraitTransitionSpec = {
                            when {
                                detailNavigationDirection > 0 -> {
                                    slideInHorizontally(
                                        tween(500, easing = MiuixSettingsNavigationMotion)
                                    ) { width -> width } togetherWith
                                        slideOutHorizontally(
                                            tween(500, easing = MiuixSettingsNavigationMotion)
                                        ) { width -> -width / 4 }
                                }
                                detailNavigationDirection < 0 -> {
                                    slideInHorizontally(
                                        tween(500, easing = MiuixSettingsNavigationMotion)
                                    ) { width -> -width / 4 } togetherWith
                                        slideOutHorizontally(
                                            tween(500, easing = MiuixSettingsNavigationMotion)
                                        ) { width -> width }
                                }
                                else -> EnterTransition.None togetherWith ExitTransition.None
                            }
                        },
                        label = "TabletSettingsDetailNavigation"
                    ) { targetPage ->
                        GlassMiuixTabletDetailPaneScaffold(
                            title = targetPage.title(),
                            config = pageConfig,
                            topBarVisible = !(targetPage == SettingsPage.Widgets && tabletWidgetEditorVisible),
                            showBackButton = targetPage != selectedPage,
                            useMiuixCollapsedTitleStyle =
                                targetPage.usesPersistentCenteredSettingsTitle(),
                            onBack = ::popTabletDetailPage,
                            topBarActions = { topBackdrop ->
                                if (targetPage == SettingsPage.AutoRefreshSchedule && autoRefreshProfile == null) {
                                    TopGlassIconButton(
                                        backdrop = topBackdrop,
                                        config = pageConfig,
                                        iconRes = R.drawable.ic_refresh,
                                        contentDescription = "更新自动刷新适配列表",
                                        onClick = { autoRefreshWarehouseRequest++ },
                                        modifier = Modifier.size(SleepDownDesignTokens.SecondaryPage.BackButtonSize),
                                        buttonHeight = 42.dp
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        ) { paneBackdrop ->
                            SettingsPageContent(
                                page = targetPage,
                                state = state,
                                pageState = pageState,
                                backdrop = paneBackdrop,
                                onPageChange = ::pushTabletDetailPage,
                                onSave = onSave,
                                onUpdateConfig = onUpdateConfig,
                                onUpdateGeneralConfig = onUpdateGeneralConfig,
                                onUpdateHomeChromeBlurScale = onUpdateHomeChromeBlurScale,
                                onPreviewLiveUpdate = onPreviewLiveUpdate,
                                onCreateSchedule = onCreateSchedule,
                                onActivateSchedule = onActivateSchedule,
                                onRenameSchedule = onRenameSchedule,
                                onDeleteSchedule = onDeleteSchedule,
                                onWidgetEditorVisibilityChange = { tabletWidgetEditorVisible = it },
                                backupPreviewUri = backupPreviewUriString?.toUri(),
                                onOpenBackupPreview = { source ->
                                    backupPreviewUriString = source.toString()
                                    pushTabletDetailPage(SettingsPage.BackupPreview)
                                },
                                exitCommitRequest = scheduleExitRequest,
                                onExitCommitFinished = { completed ->
                                    scheduleExitInFlight = false
                                    scheduleExitRequest = 0
                                    if (externalExitInFlight) {
                                        externalExitInFlight = false
                                        pendingPageName = null
                                        onExitCommitFinished(completed)
                                    } else {
                                        val destination = pendingPageName
                                        pendingPageName = null
                                        if (completed && destination != null) {
                                            applyTabletRootPage(
                                                runCatching { SettingsPage.valueOf(destination) }
                                                    .getOrDefault(SettingsPage.General)
                                            )
                                        }
                                    }
                                },
                                onExitInterceptionChange = { needsInterception ->
                                    exitInterceptionByPage = exitInterceptionByPage.toMutableMap().apply {
                                        if (needsInterception) put(targetPage, true) else remove(targetPage)
                                    }
                                },
                                autoRefreshWarehouseRequest = autoRefreshWarehouseRequest,
                                onOpenScheduleSettings = ::popTabletDetailPage,
                                periodSchemeReturnRequest = periodSchemeReturnRequest
                            )
                        }
                    }
                }
            }
            }
        } else {
            SettingsPageContent(
                page = page,
                state = state,
                pageState = pageState,
                backdrop = backdrop,
                onPageChange = onPageChange,
                onSave = onSave,
                onUpdateConfig = onUpdateConfig,
                onUpdateGeneralConfig = onUpdateGeneralConfig,
                onUpdateHomeChromeBlurScale = onUpdateHomeChromeBlurScale,
                onPreviewLiveUpdate = onPreviewLiveUpdate,
                onCreateSchedule = onCreateSchedule,
                onActivateSchedule = onActivateSchedule,
                onRenameSchedule = onRenameSchedule,
                onDeleteSchedule = onDeleteSchedule,
                backupPreviewUri = backupPreviewUriString?.toUri(),
                onOpenBackupPreview = { source ->
                    backupPreviewUriString = source.toString()
                    onPageChange(SettingsPage.BackupPreview)
                },
                exitCommitRequest = exitCommitRequest,
                onExitCommitFinished = onExitCommitFinished,
                onExitInterceptionChange = onExitInterceptionChange
            )
        }
    }
}

@Composable
private fun SettingsPageContent(
    page: SettingsPage,
    state: AppState,
    pageState: AppState,
    backdrop: Backdrop?,
    onPageChange: (SettingsPage) -> Unit,
    onSave: (ScheduleConfigEntity, List<PeriodEntity>) -> Unit,
    onUpdateConfig: (ScheduleConfigEntity) -> Unit,
    onUpdateGeneralConfig: (ScheduleConfigEntity) -> Unit,
    onUpdateHomeChromeBlurScale: (Float) -> Unit,
    onPreviewLiveUpdate: (ScheduleConfigEntity) -> Unit,
    onCreateSchedule: (String) -> Unit,
    onActivateSchedule: (Int, (() -> Unit)?) -> Unit,
    onRenameSchedule: (Int, String) -> Unit,
    onDeleteSchedule: (Int) -> Unit,
    onWidgetEditorVisibilityChange: (Boolean) -> Unit = {},
    backupPreviewUri: Uri? = null,
    onOpenBackupPreview: (Uri) -> Unit = {},
    exitCommitRequest: Int = 0,
    onExitCommitFinished: (Boolean) -> Unit = {},
    onExitInterceptionChange: (Boolean) -> Unit = {},
    autoRefreshWarehouseRequest: Int = 0,
    onOpenScheduleSettings: (() -> Unit)? = null,
    periodSchemeReturnRequest: Int = 0
) {
    when (page) {
        SettingsPage.Root -> SettingsRootScreen(pageState, backdrop, onPageChange = onPageChange)
        SettingsPage.General -> GeneralSettingsScreen(
            state = state,
            backdrop = backdrop,
            onUpdateConfig = onUpdateGeneralConfig,
            onOpenLiquidGlass = { onPageChange(SettingsPage.LiquidGlass) },
            exitCommitRequest = exitCommitRequest,
            onExitCommitFinished = onExitCommitFinished
        )
        SettingsPage.LiquidGlass -> VisualEffectsSettingsScreen(
            state = state,
            backdrop = backdrop,
            onUpdateBlurScale = onUpdateHomeChromeBlurScale
        )
        SettingsPage.Widgets -> WidgetCustomizationScreen(
            state,
            backdrop,
            onEditorVisibilityChange = onWidgetEditorVisibilityChange
        )
        SettingsPage.AiImport -> AiImportSettingsScreen(
            state = state,
            backdrop = backdrop,
            exitCommitRequest = exitCommitRequest,
            onExitCommitFinished = onExitCommitFinished
        )
        SettingsPage.DayAgent -> DayAgentSettingsScreen(state, backdrop)
        SettingsPage.Schedule -> key(periodSchemeReturnRequest) { ScheduleConfigScreen(
            state = state,
            backdrop = backdrop,
            section = SettingsSection.Schedule,
            onSave = onSave,
            onPreviewLiveUpdate = onPreviewLiveUpdate,
            exitCommitRequest = exitCommitRequest,
            onExitCommitFinished = onExitCommitFinished,
            onExitInterceptionChange = onExitInterceptionChange,
            onOpenPeriodSchemes = { onPageChange(SettingsPage.PeriodSchemes) }
        ) }
        SettingsPage.PeriodSchemes -> PeriodSchemeManagementScreen(
            state = state, backdrop = backdrop, exitCommitRequest = exitCommitRequest,
            onExitCommitFinished = onExitCommitFinished,
            onExitInterceptionChange = onExitInterceptionChange,
            onOpenScheduleSettings = onOpenScheduleSettings ?: { onPageChange(SettingsPage.Schedule) }
        )
        SettingsPage.AutoRefreshSchedule -> AutoRefreshScheduleSettingsScreen(
            state = state,
            backdrop = backdrop,
            warehouseRefreshRequest = autoRefreshWarehouseRequest
        )
        SettingsPage.Notifications -> ScheduleConfigScreen(
            state = state,
            backdrop = backdrop,
            section = SettingsSection.Notifications,
            onSave = { config, _ -> onUpdateConfig(config) },
            onPreviewLiveUpdate = onPreviewLiveUpdate,
            exitCommitRequest = exitCommitRequest,
            onExitCommitFinished = onExitCommitFinished
        )
        SettingsPage.ScheduleManager -> ScheduleManagerScreen(
            state,
            backdrop,
            onCreateSchedule,
            onActivateSchedule,
            onRenameSchedule,
            onDeleteSchedule
        )
        SettingsPage.BackupRestore -> BackupRestoreSettingsScreen(
            state = pageState,
            backdrop = backdrop,
            onOpenPreview = onOpenBackupPreview,
            onOpenWebDav = { onPageChange(SettingsPage.WebDav) }
        )
        SettingsPage.WebDav -> com.xiaomanjun.sleepdownschedule.feature.backup.webdav.WebDavSettingsScreen(
            pageState, backdrop, onOpenBackupPreview)
        SettingsPage.BackupPreview -> if (backupPreviewUri != null) {
            BackupRestorePreviewScreen(pageState, backdrop, backupPreviewUri)
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "没有找到要预览的备份文件，请返回后重新选择。",
                    modifier = Modifier.padding(24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        SettingsPage.About -> ChangelogSettingsScreen(
            pageState,
            backdrop,
			onDonate = { onPageChange(SettingsPage.Donate) },
			onPrivacyPolicy = { onPageChange(SettingsPage.PrivacyPolicy) }
        )
        SettingsPage.Changelog -> ChangelogSettingsScreen(
            pageState,
            backdrop,
			onDonate = { onPageChange(SettingsPage.Donate) },
			onPrivacyPolicy = { onPageChange(SettingsPage.PrivacyPolicy) }
        )
		SettingsPage.Donate -> DonateSettingsScreen(pageState, backdrop)
		SettingsPage.PrivacyPolicy -> PrivacyPolicySettingsScreen(pageState, backdrop)
    }
}
