package com.xiaomanjun.sleepdownschedule.feature.settings

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.updateTransition
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.xiaomanjun.sleepdownschedule.domain.schedule.decodeScheduleAdjustments
import com.xiaomanjun.sleepdownschedule.domain.schedule.encodeScheduleAdjustments
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
import com.xiaomanjun.sleepdownschedule.core.ui.settings.CouiSettingsTabletDetailPaneScaffold
import com.xiaomanjun.sleepdownschedule.feature.backup.BackupRestorePreviewScreen
import com.xiaomanjun.sleepdownschedule.feature.backup.BackupRestoreSettingsScreen
import com.xiaomanjun.sleepdownschedule.core.ui.interaction.blockPaneInputWhile
import com.xiaomanjun.sleepdownschedule.core.ui.interaction.landscapePageBlur
import com.xiaomanjun.sleepdownschedule.feature.home.rememberHomeAdaptiveMetrics
import com.xiaomanjun.sleepdownschedule.feature.importing.DonateSettingsScreen
import com.xiaomanjun.sleepdownschedule.feature.schedule.autorefresh.AutoRefreshScheduleStore
import com.xiaomanjun.sleepdownschedule.feature.schedule.manager.ScheduleManagerScreen
import com.xiaomanjun.sleepdownschedule.feature.settings.about.AutoUpdateSettingsScreen
import com.xiaomanjun.sleepdownschedule.feature.settings.about.ChangelogSettingsScreen
import com.xiaomanjun.sleepdownschedule.feature.feedback.FeedbackScreen
import com.xiaomanjun.sleepdownschedule.feature.settings.about.ColorOsAboutSettingsScreen
import com.xiaomanjun.sleepdownschedule.feature.settings.about.SoftwareUpdateMenu
import com.xiaomanjun.sleepdownschedule.feature.settings.about.WideSoftwareUpdateToolbarButton
import com.itos.coui.icons.CouiIcons
import com.xiaomanjun.sleepdownschedule.feature.settings.about.SoftwareUpdateSettingsScreen
import com.xiaomanjun.sleepdownschedule.feature.widget.WidgetCustomizationScreen

@Composable
fun SettingsScreen(
    page: SettingsPage,
    state: AppState,
    backdrop: Backdrop?,
    onPageChange: (SettingsPage) -> Unit,
    onSave: (ScheduleConfigEntity, List<PeriodEntity>) -> Unit,
    onSaveAdjustments: suspend (Int, String, String) -> Unit,
    onUpdateConfig: (ScheduleConfigEntity) -> Unit,
    onUpdateGeneralConfig: (ScheduleConfigEntity) -> Unit = onUpdateConfig,
    onUpdateHomeChromeBlurScale: (Float) -> Unit,
    onPreviewLiveUpdate: (ScheduleConfigEntity) -> Unit,
    onReplayWelcome: () -> Unit,
    onCreateSchedule: (String) -> Unit = {},
    onActivateSchedule: (Int, (() -> Unit)?) -> Unit = { _, _ -> },
    onRenameSchedule: (Int, String) -> Unit = { _, _ -> },
    onDeleteSchedule: (Int) -> Unit = {},
    exitCommitRequest: Int = 0,
    onExitCommitFinished: (Boolean) -> Unit = {},
    onExitInterceptionChange: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val pageConfig = settingsVisualConfig(state.config)
    val pageState = state.copy(config = pageConfig)
    val adaptiveMetrics = rememberHomeAdaptiveMetrics()
    var backupPreviewUriString by rememberSaveable { mutableStateOf<String?>(null) }
    var showingUpdateNotes by rememberSaveable { mutableStateOf(false) }
    var autoRefreshWarehouseRequest by remember { mutableIntStateOf(0) }
    val autoRefreshProfile = AutoRefreshScheduleStore.observe(context).collectAsStateWithLifecycle().value
    GlassMiuixSettingsTheme(pageConfig) {
        if (page == SettingsPage.Root && adaptiveMetrics.isLargeScreen) {
            var tabletNavigation by rememberSaveable(
                stateSaver = TabletSettingsNavigationStateSaver
            ) {
                mutableStateOf(TabletSettingsNavigationState())
            }
            var periodSchemeReturnRequest by remember { mutableIntStateOf(0) }
            var tabletWidgetEditorVisible by remember { mutableStateOf(false) }
            var scheduleExitRequest by remember { mutableIntStateOf(0) }
            var exitRequestPage by remember { mutableStateOf<SettingsPage?>(null) }
            var completeExitRequest by remember { mutableStateOf<((Boolean) -> Unit)?>(null) }
            var adjustmentCalendar by remember(state.config.id) { mutableStateOf<ScheduleConfigEntity?>(null) }
            var adjustmentsSaving by remember(state.config.id) { mutableStateOf(false) }
            var lastHandledExternalExitRequest by remember { mutableIntStateOf(exitCommitRequest) }
            var exitInterceptionByPage by remember {
                mutableStateOf<Map<SettingsPage, Boolean>>(emptyMap())
            }
            val selectedPage = tabletNavigation.rootPage
            val displayedPage = tabletNavigation.displayedPage
            val scheduleDraft = remember(state.config.id, selectedPage) {
                if (selectedPage == SettingsPage.Schedule) ScheduleSettingsDraft(state.config) else null
            }
            val currentScheduleDraft by rememberUpdatedState(scheduleDraft)
            val portrait = adaptiveMetrics.screenHeight > adaptiveMetrics.screenWidth
            val navigationWidth = if (portrait) {
                (adaptiveMetrics.contentWidth * 0.38f).coerceIn(280.dp, 328.dp)
            } else {
                (adaptiveMetrics.contentWidth * 0.31f).coerceIn(336.dp, 408.dp)
            }.coerceAtMost(adaptiveMetrics.contentWidth * 0.48f)
            LaunchedEffect(displayedPage) {
                if (displayedPage != SettingsPage.Widgets) tabletWidgetEditorVisible = false
            }
            val interceptExit = adjustmentsSaving || exitInterceptionByPage[displayedPage] == true ||
                scheduleDraft?.dirty == true
            LaunchedEffect(interceptExit) {
                onExitInterceptionChange(interceptExit)
            }
            DisposableEffect(onExitInterceptionChange) {
                onDispose { onExitInterceptionChange(false) }
            }
            fun applyTabletRootPage(nextPage: SettingsPage) {
                tabletNavigation = tabletNavigation.selectRoot(nextPage)
                showingUpdateNotes = false
                backupPreviewUriString = null
            }
            fun requestPaneExit(checkScheduleDraft: Boolean, onFinished: (Boolean) -> Unit) {
                val currentPage = tabletNavigation.displayedPage
                val editingRoutines = currentPage == SettingsPage.PeriodSchemes &&
                    exitInterceptionByPage[currentPage] == true
                if (adjustmentsSaving || editingRoutines || completeExitRequest != null) {
                    onFinished(false)
                    return
                }
                val pageToCommit = when {
                    exitInterceptionByPage[currentPage] == true -> currentPage
                    checkScheduleDraft && currentScheduleDraft?.dirty == true -> SettingsPage.Schedule
                    else -> null
                }
                if (pageToCommit == null) {
                    onFinished(true)
                    return
                }
                if (pageToCommit != currentPage) applyTabletRootPage(pageToCommit)
                completeExitRequest = onFinished
                exitRequestPage = pageToCommit
                scheduleExitRequest++
            }
            fun requestTabletRootPage(nextPage: SettingsPage) {
                val navigation = tabletNavigation
                if (navigation.rootPage == nextPage && navigation.detailPages.isEmpty()) return
                requestPaneExit(checkScheduleDraft = nextPage != navigation.rootPage) { completed ->
                    if (completed) applyTabletRootPage(nextPage)
                }
            }
            fun pushTabletDetailPage(nextPage: SettingsPage) {
                val nextNavigation = tabletNavigation.pushDetail(nextPage)
                if (nextNavigation == tabletNavigation) return
                tabletNavigation = nextNavigation
            }
            fun popTabletDetailPage() {
                val leavingPage = tabletNavigation.displayedPage
                if (leavingPage == SettingsPage.SoftwareUpdate && showingUpdateNotes) {
                    showingUpdateNotes = false
                    return
                }
                requestPaneExit(checkScheduleDraft = false) { completed ->
                    if (completed) {
                        if (leavingPage == SettingsPage.PeriodSchemes) periodSchemeReturnRequest++
                        tabletNavigation = tabletNavigation.popDetail()
                    }
                }
            }
            LaunchedEffect(exitCommitRequest) {
                if (exitCommitRequest == lastHandledExternalExitRequest) return@LaunchedEffect
                lastHandledExternalExitRequest = exitCommitRequest
                requestPaneExit(checkScheduleDraft = true, onFinished = onExitCommitFinished)
            }
            BackHandler(enabled = tabletNavigation.detailPages.isNotEmpty() ||
                (displayedPage == SettingsPage.SoftwareUpdate && showingUpdateNotes)) {
                popTabletDetailPage()
            }
            Row(Modifier.fillMaxSize().background(settingsPageBackground(pageConfig))) {
                Box(Modifier.width(navigationWidth).fillMaxHeight()) {
                    SettingsRootScreen(
                        state = pageState,
                        backdrop = backdrop,
                        selectedPage = selectedPage,
                        onPageChange = { requestTabletRootPage(it) }
                    )
                }
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clipToBounds()
                ) {
                    val detailTransition = updateTransition(tabletNavigation, label = "TabletSettingsDetailNavigation")
                    val moving = detailTransition.isRunning || detailTransition.currentState != detailTransition.targetState
                    val switchingCategory = detailTransition.segment.run { initialState.rootPage != targetState.rootPage }
                    detailTransition.AnimatedContent(
                        modifier = Modifier.fillMaxSize().blockPaneInputWhile(moving),
                        contentKey = { it.displayedPage },
                        transitionSpec = { settingsDetailTransition() }
                    ) { navigation ->
                        val targetPage = navigation.displayedPage
                        if (targetPage == SettingsPage.ScheduleAdjustments) {
                            val calendar = adjustmentCalendar ?: state.config
                            val entries = remember(state.config.scheduleAdjustmentsJson) {
                                decodeScheduleAdjustments(state.config.scheduleAdjustmentsJson)
                            }
                            ScheduleAdjustmentsScreen(
                                state = state.copy(config = state.config.copy(
                                    totalWeeks = calendar.totalWeeks,
                                    currentWeek = calendar.currentWeek,
                                    autoCurrentWeek = calendar.autoCurrentWeek,
                                    termStartDate = calendar.termStartDate
                                )),
                                entries = entries,
                                saving = adjustmentsSaving,
                                onDismiss = ::popTabletDetailPage,
                                onSave = { previous, updated ->
                                    check(!adjustmentsSaving) { "正在保存调休安排" }
                                    adjustmentsSaving = true
                                    try {
                                        onSaveAdjustments(state.config.id, encodeScheduleAdjustments(previous),
                                            encodeScheduleAdjustments(updated))
                                    } finally {
                                        adjustmentsSaving = false
                                    }
                                },
                                embedded = true
                            )
                            return@AnimatedContent
                        }
                        CouiSettingsTabletDetailPaneScaffold(
                            title = if (targetPage == SettingsPage.SoftwareUpdate && showingUpdateNotes) "更新说明" else targetPage.title(),
                            config = pageConfig,
                            topBarVisible = targetPage != SettingsPage.Feedback &&
                                !(targetPage == SettingsPage.Widgets && tabletWidgetEditorVisible),
                            showBackButton = navigation.detailPages.isNotEmpty() ||
                                (targetPage == SettingsPage.SoftwareUpdate && showingUpdateNotes),
                            onBack = ::popTabletDetailPage,
                            topBarNavigation = if (targetPage == SettingsPage.SoftwareUpdate &&
                                (navigation.detailPages.isNotEmpty() || showingUpdateNotes)) {
                                { WideSoftwareUpdateToolbarButton(CouiIcons.Back, "返回", onClick = ::popTabletDetailPage) }
                            } else null,
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
                                } else if (targetPage == SettingsPage.SoftwareUpdate) {
                                    SoftwareUpdateMenu(topBackdrop, onReplayWelcome, onOpenPage = ::pushTabletDetailPage)
                                }
                            },
                            modifier = Modifier.fillMaxSize().then(landscapePageBlur(switchingCategory))
                        ) { paneBackdrop ->
                            SettingsPageContent(
                                page = targetPage,
                                state = state,
                                pageState = pageState,
                                backdrop = paneBackdrop,
                                onPageChange = ::pushTabletDetailPage,
                                onFeedbackBack = if (navigation.detailPages.isNotEmpty()) ::popTabletDetailPage else null,
                                showUpdateNotes = showingUpdateNotes,
                                onOpenUpdateNotes = { showingUpdateNotes = true },
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
                                retainedScheduleDraft = scheduleDraft,
                                onOpenAdjustments = { config ->
                                    adjustmentCalendar = config
                                    pushTabletDetailPage(SettingsPage.ScheduleAdjustments)
                                },
                                exitCommitRequest = if (targetPage == exitRequestPage) scheduleExitRequest else 0,
                                onExitCommitFinished = { completed ->
                                    if (targetPage == exitRequestPage) {
                                        val action = completeExitRequest
                                        completeExitRequest = null
                                        exitRequestPage = null
                                        action?.invoke(completed)
                                    }
                                },
                                onExitInterceptionChange = { needsInterception ->
                                    exitInterceptionByPage = exitInterceptionByPage.toMutableMap().apply {
                                        if (needsInterception) put(targetPage, true) else remove(targetPage)
                                    }
                                },
                                autoRefreshWarehouseRequest = autoRefreshWarehouseRequest,
                                periodSchemeReturnRequest = periodSchemeReturnRequest
                            )
                        }
                    }
                }
            }
        } else {
            BackHandler(enabled = page == SettingsPage.SoftwareUpdate && showingUpdateNotes) { showingUpdateNotes = false }
            SettingsPageContent(
                page = page,
                state = state,
                pageState = pageState,
                backdrop = backdrop,
                onPageChange = onPageChange,
                showUpdateNotes = showingUpdateNotes,
                onOpenUpdateNotes = { showingUpdateNotes = true },
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
    showUpdateNotes: Boolean,
    onOpenUpdateNotes: () -> Unit,
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
    retainedScheduleDraft: ScheduleSettingsDraft? = null,
    onOpenAdjustments: ((ScheduleConfigEntity) -> Unit)? = null,
    exitCommitRequest: Int = 0,
    onExitCommitFinished: (Boolean) -> Unit = {},
    onExitInterceptionChange: (Boolean) -> Unit = {},
    autoRefreshWarehouseRequest: Int = 0,
    periodSchemeReturnRequest: Int = 0,
    onFeedbackBack: (() -> Unit)? = null,
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
            retainedDraft = retainedScheduleDraft,
            onOpenSchedulePage = if (retainedScheduleDraft != null) onPageChange else null,
            onOpenAdjustments = onOpenAdjustments
        ) }
        SettingsPage.ScheduleAdjustments -> Unit // Owns the scaffold in the tablet detail stack.
        SettingsPage.PeriodSchemes -> PeriodSchemeManagementScreen(
            state = state, backdrop = backdrop, exitCommitRequest = exitCommitRequest,
            onExitCommitFinished = onExitCommitFinished,
            onExitInterceptionChange = onExitInterceptionChange
        )
        SettingsPage.SpecialCourses -> TimeSlotCourseManagementScreen(
            state, exitCommitRequest, onExitCommitFinished, onExitInterceptionChange
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
        SettingsPage.About -> ColorOsAboutSettingsScreen(
            pageState,
            backdrop,
			onDonate = { onPageChange(SettingsPage.Donate) },
			onPrivacyPolicy = { onPageChange(SettingsPage.PrivacyPolicy) },
            onSoftwareUpdate = { onPageChange(SettingsPage.SoftwareUpdate) },
        )
        SettingsPage.Changelog -> ChangelogSettingsScreen(pageState, backdrop)
        SettingsPage.Feedback -> FeedbackScreen(pageState.config, onBack = onFeedbackBack)
        SettingsPage.SoftwareUpdate -> SoftwareUpdateSettingsScreen(pageState, backdrop, showUpdateNotes, onOpenUpdateNotes)
        SettingsPage.AutoUpdate -> AutoUpdateSettingsScreen(pageState.config, backdrop, onUpdateGeneralConfig)
		SettingsPage.Donate -> DonateSettingsScreen(pageState, backdrop)
		SettingsPage.PrivacyPolicy -> PrivacyPolicySettingsScreen(pageState, backdrop)
    }
}
