package com.xiaomanjun.sleepdownschedule.app.ui

import android.app.Activity
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kyant.backdrop.effects.blur
import com.xiaomanjun.sleepdownschedule.AppState
import com.xiaomanjun.sleepdownschedule.CourseScheduleApp
import com.xiaomanjun.sleepdownschedule.CourseScheduleTheme
import com.xiaomanjun.sleepdownschedule.R
import com.xiaomanjun.sleepdownschedule.SettingsDetailActivity
import com.xiaomanjun.sleepdownschedule.app.state.ScheduleViewModel
import com.xiaomanjun.sleepdownschedule.app.state.ScheduleViewModelFactory
import com.xiaomanjun.sleepdownschedule.applyAppThemeSurface
import com.xiaomanjun.sleepdownschedule.core.ui.chrome.TopGlassIconButton
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.SleepDownDesignTokens
import com.xiaomanjun.sleepdownschedule.core.ui.scaffold.DetailActivityScaffold
import com.xiaomanjun.sleepdownschedule.core.ui.scaffold.settingsVisualConfig
import com.xiaomanjun.sleepdownschedule.defaultConfig
import com.xiaomanjun.sleepdownschedule.defaultPeriods
import com.xiaomanjun.sleepdownschedule.feature.backup.BackupRestorePreviewScreen
import com.xiaomanjun.sleepdownschedule.feature.backup.BackupRestoreSettingsScreen
import com.xiaomanjun.sleepdownschedule.feature.importing.DonateSettingsScreen
import com.xiaomanjun.sleepdownschedule.feature.schedule.ScheduleSnapshotStore
import com.xiaomanjun.sleepdownschedule.feature.schedule.autorefresh.AutoRefreshScheduleStore
import com.xiaomanjun.sleepdownschedule.feature.schedule.manager.ScheduleManagerScreen
import com.xiaomanjun.sleepdownschedule.feature.settings.AiImportSettingsScreen
import com.xiaomanjun.sleepdownschedule.feature.settings.AutoRefreshScheduleSettingsScreen
import com.xiaomanjun.sleepdownschedule.feature.settings.DayAgentSettingsScreen
import com.xiaomanjun.sleepdownschedule.feature.settings.GeneralSettingsScreen
import com.xiaomanjun.sleepdownschedule.feature.settings.PeriodSchemeManagementScreen
import com.xiaomanjun.sleepdownschedule.feature.settings.PrivacyPolicySettingsScreen
import com.xiaomanjun.sleepdownschedule.feature.settings.ScheduleConfigScreen
import com.xiaomanjun.sleepdownschedule.feature.settings.SettingsPage
import com.xiaomanjun.sleepdownschedule.feature.settings.SettingsRootScreen
import com.xiaomanjun.sleepdownschedule.feature.settings.SettingsSection
import com.xiaomanjun.sleepdownschedule.feature.settings.VisualEffectsSettingsScreen
import com.xiaomanjun.sleepdownschedule.feature.settings.about.ChangelogSettingsScreen
import com.xiaomanjun.sleepdownschedule.feature.settings.title
import com.xiaomanjun.sleepdownschedule.feature.settings.usesPersistentCenteredSettingsTitle
import com.xiaomanjun.sleepdownschedule.feature.widget.WidgetCustomizationScreen
import com.xiaomanjun.sleepdownschedule.glass.ui.appUsesDarkTheme
import com.xiaomanjun.sleepdownschedule.transition.ActivityTransitionCoordinator
import com.xiaomanjun.sleepdownschedule.transition.CrossActivityTransitionHost
import com.xiaomanjun.sleepdownschedule.transition.LegacyTransitionProfile
import com.xiaomanjun.sleepdownschedule.transition.TransitionRouteCatalog
import com.xiaomanjun.sleepdownschedule.transition.TransitionRouteId
import com.xiaomanjun.sleepdownschedule.transition.legacy.ScheduleCustomizeIdExtra
import com.xiaomanjun.sleepdownschedule.transition.legacy.ScheduleEntrySnapshotExtra
import com.xiaomanjun.sleepdownschedule.transition.transitionRouteIdOrNull
import kotlinx.coroutines.flow.first

open class SettingsDetailActivityHost : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // The coordinator selects a translucent destination for snapshot-free anchored routes
        // before super.onCreate. The QuickSheet route also declares this policy in the manifest so
        // its captured underlay can be installed without an opaque preview frame.
        ActivityTransitionCoordinator.prepareDestinationBeforeOnCreate(this)
        super.onCreate(savedInstanceState)
        ActivityTransitionCoordinator.installDestinationWindowBackground(this)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val externalBackupPreviewSource = intent.data?.takeIf {
            intent.action == Intent.ACTION_VIEW
        }
        val customizeScheduleId = intent.getIntExtra(ScheduleCustomizeIdExtra, -1).takeIf { it > 0 }
        if (intent.getStringExtra(SettingsDetailPageExtra) == SettingsPage.AutoRefreshSchedule.name) {
            // Match the education picker before the first window layout, not after composition.
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
        }
        val useEntrySnapshot = intent.getBooleanExtra(ScheduleEntrySnapshotExtra, false)
        setContent {
            val app = application as CourseScheduleApp
            val viewModel: ScheduleViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                factory = ScheduleViewModelFactory(app, app.repository)
            )
            val section = if (externalBackupPreviewSource != null) {
                SettingsPage.BackupPreview
            } else {
                SettingsPage.valueOf(
                    intent.getStringExtra(SettingsDetailPageExtra) ?: SettingsPage.General.name
                )
            }
            val backupPreviewSource = externalBackupPreviewSource
                ?: intent.getStringExtra(BackupPreviewUriExtra)?.toUri()
            val stateFlow = if (customizeScheduleId != null || section == SettingsPage.ScheduleManager) {
                viewModel.allSchedulesState
            } else {
                viewModel.state
            }
            val state by stateFlow.collectAsStateWithLifecycle()
            // Anchored entries keep the destination window transparent during the Morph. The
            // special QuickSheet route supplies its root-composited underlay to the shared
            // snapshot blur chain instead of relying on OEM cross-window blur.
            val isAnchoredSettingsEntry = remember {
                intent.transitionRouteIdOrNull()
                    ?.let(TransitionRouteCatalog::get)
                    ?.legacyProfile is LegacyTransitionProfile.Anchored
            }
            val isQuickSheetSettingsEntry = remember {
                intent.transitionRouteIdOrNull() == TransitionRouteId.QuickSheetToSettingsDetail
            }
            CourseScheduleTheme(config = state.config) {
                val darkWindowBackground = appUsesDarkTheme(state.config)
                LaunchedEffect(darkWindowBackground, isAnchoredSettingsEntry) {
                    // Keep the Activity surface consistent with the Compose page while it is
                    // leaving the screen. Some Android 13/ColorOS back animations otherwise
                    // reveal the light theme's static window background for one frame.
                    if (!isAnchoredSettingsEntry) {
                        window.applyAppThemeSurface(darkWindowBackground)
                    }
                }
                val scheduleEditState = remember(state, customizeScheduleId) {
                    if (customizeScheduleId != null) scheduleConfigStateForEdit(state, customizeScheduleId) else state
                }
                val scheduleEditReady = customizeScheduleId == null ||
                    (
                        state.allConfigs.any { it.id == customizeScheduleId } &&
                            state.allPeriods.any { it.scheduleId == customizeScheduleId }
                    )
                val editEntrySnapshot = remember(customizeScheduleId, useEntrySnapshot) {
                    customizeScheduleId
                        ?.takeIf { useEntrySnapshot }
                        ?.let { scheduleId ->
                            BitmapFactory.decodeFile(
                                ScheduleSnapshotStore.file(this, scheduleId).absolutePath
                            )
                        }
                }
                var editEntrySnapshotVisible by remember(editEntrySnapshot) {
                    mutableStateOf(editEntrySnapshot != null)
                }
                LaunchedEffect(editEntrySnapshot) {
                    if (editEntrySnapshot != null) {
                        withFrameNanos { }
                        withFrameNanos { }
                        editEntrySnapshotVisible = false
                    }
                }
                var scheduleExitRequest by remember { mutableIntStateOf(0) }
                var autoRefreshWarehouseRequest by remember { mutableIntStateOf(0) }
                val autoRefreshProfile = if (section == SettingsPage.AutoRefreshSchedule) {
                    AutoRefreshScheduleStore.observe(this@SettingsDetailActivityHost)
                        .collectAsStateWithLifecycle().value
                } else null
                var widgetEditorVisible by remember { mutableStateOf(false) }
                var interceptSystemBack by remember(section) { mutableStateOf(false) }
                // Anchored entries share the same destination-side Morph host; ordinary settings
                // entries retain their platform/depth transition behavior.
                var transitionRequestClose by remember { mutableStateOf<(() -> Unit)?>(null) }
                val closeSettings: () -> Unit = { (transitionRequestClose ?: { finish() })() }
                val requestExit: () -> Unit = {
                    when (section) {
                        SettingsPage.Schedule, SettingsPage.PeriodSchemes -> scheduleExitRequest++
                        else -> closeSettings()
                    }
                }
                // Anchored schedule pages need the Activity host to route Back through the page
                // before asking the Morph to close. Ordinary clean pages keep the platform Back
                // path unregistered so predictive Back remains available.
                val anchoredScheduleBackRequired =
                    section == SettingsPage.Schedule && isAnchoredSettingsEntry
                BackHandler(
                    enabled = interceptSystemBack || anchoredScheduleBackRequired,
                    onBack = requestExit
                )
                Box(Modifier.fillMaxSize()) {
                val settingsDetailContent: @Composable () -> Unit = {
                DetailActivityScaffold(
                    title = section.title(),
                    config = state.config,
                    compactTopBar = section.usesPersistentCenteredSettingsTitle(),
                    centerCompactTitle = section.usesPersistentCenteredSettingsTitle(),
                    compactTitleMatchesSettings = section.usesPersistentCenteredSettingsTitle(),
                    topBarVisible = !widgetEditorVisible,
                    onBack = requestExit,
                    topBarActions = { topBackdrop ->
                        if (section == SettingsPage.AutoRefreshSchedule && autoRefreshProfile == null) {
                            TopGlassIconButton(
                                backdrop = topBackdrop,
                                config = settingsVisualConfig(state.config),
                                iconRes = R.drawable.ic_refresh,
                                contentDescription = "更新自动刷新适配列表",
                                onClick = { autoRefreshWarehouseRequest++ },
                                modifier = Modifier.size(SleepDownDesignTokens.SecondaryPage.BackButtonSize),
                                buttonHeight = 42.dp
                            )
                        }
                    }
                ) { backdrop ->
                    when (section) {
                        SettingsPage.General -> GeneralSettingsScreen(
                            state = state,
                            backdrop = backdrop,
                            onUpdateConfig = viewModel::saveGeneralSettings,
                            onOpenLiquidGlass = {
                                ActivityTransitionCoordinator.openImmediate(
                                    this@SettingsDetailActivityHost,
                                    TransitionRouteId.SettingsToSettingsDetail,
                                    Intent(this@SettingsDetailActivityHost, SettingsDetailActivity::class.java)
                                        .putExtra(SettingsDetailPageExtra, SettingsPage.LiquidGlass.name)
                                )
                            }
                        )
                        SettingsPage.LiquidGlass -> VisualEffectsSettingsScreen(
                            state = state,
                            backdrop = backdrop,
                            onUpdateBlurScale = viewModel::saveHomeChromeBlurScale
                        )
                        SettingsPage.Widgets -> WidgetCustomizationScreen(
                            state = state,
                            backdrop = backdrop,
                            onEditorVisibilityChange = { widgetEditorVisible = it }
                        )
                        SettingsPage.AiImport -> AiImportSettingsScreen(
                            state = state,
                            backdrop = backdrop
                        )
                        SettingsPage.DayAgent -> DayAgentSettingsScreen(state, backdrop)
                        SettingsPage.Schedule -> if (!scheduleEditReady) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                androidx.compose.material3.CircularProgressIndicator()
                            }
                        } else {
                            ScheduleConfigScreen(
                                scheduleEditState,
                                backdrop,
                                SettingsSection.Schedule,
                                onSave = { _, _ -> viewModel.refreshNotificationsAfterSave() },
                                onPreviewLiveUpdate = viewModel::previewLiveUpdate,
                                exitCommitRequest = scheduleExitRequest,
                                onExitCommitFinished = { saved -> if (saved) closeSettings() },
                                onExitInterceptionChange = { interceptSystemBack = it }
                            )
                        }
                        SettingsPage.PeriodSchemes -> if (!scheduleEditReady) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                androidx.compose.material3.CircularProgressIndicator()
                            }
                        } else {
                            PeriodSchemeManagementScreen(
                                state = scheduleEditState, backdrop = backdrop, exitCommitRequest = scheduleExitRequest,
                                onExitCommitFinished = { saved -> if (saved) closeSettings() },
                                onExitInterceptionChange = { interceptSystemBack = it },
                                onOpenScheduleSettings = if (customizeScheduleId != null) closeSettings else null
                            )
                        }
                        SettingsPage.AutoRefreshSchedule -> AutoRefreshScheduleSettingsScreen(
                            state = state,
                            backdrop = backdrop,
                            warehouseRefreshRequest = autoRefreshWarehouseRequest
                        )
                        SettingsPage.Notifications -> ScheduleConfigScreen(
                            state = state,
                            backdrop = backdrop,
                            section = SettingsSection.Notifications,
                            onSave = { config, _ -> viewModel.saveNotificationSettings(config) },
                            onPreviewLiveUpdate = viewModel::previewLiveUpdate
                        )
                        SettingsPage.About -> ChangelogSettingsScreen(
                            state = state,
                            backdrop = backdrop,
                            onDonate = {
                                ActivityTransitionCoordinator.openImmediate(
                                    this@SettingsDetailActivityHost,
                                    TransitionRouteId.SettingsToSettingsDetail,
                                    Intent(this@SettingsDetailActivityHost, SettingsDetailActivity::class.java)
                                        .putExtra(SettingsDetailPageExtra, SettingsPage.Donate.name)
                                )
							},
							onPrivacyPolicy = {
								ActivityTransitionCoordinator.openImmediate(
                                    this@SettingsDetailActivityHost,
                                    TransitionRouteId.SettingsToSettingsDetail,
                                    Intent(this@SettingsDetailActivityHost, SettingsDetailActivity::class.java)
                                        .putExtra(SettingsDetailPageExtra, SettingsPage.PrivacyPolicy.name)
                                )
							}
                        )
                        SettingsPage.BackupRestore -> BackupRestoreSettingsScreen(
                            state = state,
                            backdrop = backdrop,
                            onOpenWebDav = {
                                ActivityTransitionCoordinator.openImmediate(this@SettingsDetailActivityHost,
                                    TransitionRouteId.SettingsToSettingsDetail,
                                    Intent(this@SettingsDetailActivityHost, SettingsDetailActivity::class.java)
                                        .putExtra(SettingsDetailPageExtra, SettingsPage.WebDav.name))
                            },
                            onOpenPreview = { source ->
                                ActivityTransitionCoordinator.openImmediate(
                                    this@SettingsDetailActivityHost,
                                    TransitionRouteId.SettingsToSettingsDetail,
                                    Intent(this@SettingsDetailActivityHost, SettingsDetailActivity::class.java)
                                        .setData(source)
                                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        .putExtra(SettingsDetailPageExtra, SettingsPage.BackupPreview.name)
                                        .putExtra(BackupPreviewUriExtra, source.toString())
                                )
                            }
                        )
                        SettingsPage.WebDav -> com.xiaomanjun.sleepdownschedule.feature.backup.webdav.WebDavSettingsScreen(state, backdrop) { source ->
                            ActivityTransitionCoordinator.openImmediate(this@SettingsDetailActivityHost,
                                TransitionRouteId.SettingsToSettingsDetail,
                                Intent(this@SettingsDetailActivityHost, SettingsDetailActivity::class.java)
                                    .setData(source).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    .putExtra(SettingsDetailPageExtra, SettingsPage.BackupPreview.name)
                                    .putExtra(BackupPreviewUriExtra, source.toString()))
                        }
                        SettingsPage.BackupPreview -> if (backupPreviewSource != null) {
                            BackupRestorePreviewScreen(state, backdrop, backupPreviewSource)
                        } else {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    "没有找到要预览的备份文件，请返回后重新选择。",
                                    modifier = Modifier.padding(24.dp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        SettingsPage.Changelog -> ChangelogSettingsScreen(
                            state = state,
                            backdrop = backdrop,
                            onDonate = {
                                ActivityTransitionCoordinator.openImmediate(
                                    this@SettingsDetailActivityHost,
                                    TransitionRouteId.SettingsToSettingsDetail,
                                    Intent(this@SettingsDetailActivityHost, SettingsDetailActivity::class.java)
                                        .putExtra(SettingsDetailPageExtra, SettingsPage.Donate.name)
                                )
							},
							onPrivacyPolicy = {
								ActivityTransitionCoordinator.openImmediate(
                                    this@SettingsDetailActivityHost,
                                    TransitionRouteId.SettingsToSettingsDetail,
                                    Intent(this@SettingsDetailActivityHost, SettingsDetailActivity::class.java)
                                        .putExtra(SettingsDetailPageExtra, SettingsPage.PrivacyPolicy.name)
                                )
							}
                        )
						SettingsPage.Donate -> DonateSettingsScreen(state, backdrop)
						SettingsPage.PrivacyPolicy -> PrivacyPolicySettingsScreen(state, backdrop)
                        SettingsPage.ScheduleManager -> ScheduleManagerScreen(
                            state = state, backdrop = backdrop,
                            onCreateSchedule = { viewModel.createSchedule(it) },
                            onActivateSchedule = { id, finish -> viewModel.activateSchedule(id, finish) },
                            onRenameSchedule = { id, name -> viewModel.renameSchedule(id, name) },
                            onDeleteSchedule = { viewModel.deleteSchedule(it) }
                        )
                        SettingsPage.Root -> SettingsRootScreen(state, backdrop) {}
                    }
                }
                }
                if (isAnchoredSettingsEntry) {
                    CrossActivityTransitionHost(
                        activity = this@SettingsDetailActivityHost,
                        // A new Activity-local ViewModel starts from AppState(). Starting the
                        // Morph against that placeholder consumes the whole opening duration
                        // before the selected schedule arrives, which looks like a skipped
                        // transition. Precompose the real page, then release the existing motion.
                        openingReady = !isQuickSheetSettingsEntry || scheduleEditReady,
                        handleSystemBack = section != SettingsPage.Schedule,
                        sourceContent = {
                            // The QuickSheet route already supplies the exact composed button and
                            // full background frame. Painting a synthetic source card here would
                            // stretch a flat color across the expanding shell, so leave this layer
                            // transparent; other anchored settings entries retain their fallback.
                            if (!isQuickSheetSettingsEntry) {
                                val sourceDark = appUsesDarkTheme(state.config)
                                Box(
                                    Modifier
                                        .fillMaxSize()
                                        .background(
                                            if (sourceDark) {
                                                ComposeColor(0xFF25272D)
                                            } else {
                                                ComposeColor(0xFFE9ECF2)
                                            }
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "详细设置",
                                        color = if (sourceDark) ComposeColor.White else ComposeColor(0xFF111111),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    ) { requestClose ->
                        if (transitionRequestClose == null) transitionRequestClose = requestClose
                        settingsDetailContent()
                    }
                } else {
                    settingsDetailContent()
                }
                AnimatedVisibility(
                    visible = editEntrySnapshotVisible,
                    enter = EnterTransition.None,
                    exit = fadeOut(animationSpec = tween(220)),
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(200f)
                ) {
                    editEntrySnapshot?.let { snapshot ->
                        Image(
                            bitmap = snapshot.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.FillBounds,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                }
            }
        }
    }
}

private fun scheduleConfigStateForEdit(state: AppState, scheduleId: Int): AppState {
    val targetConfig = state.allConfigs.firstOrNull { it.id == scheduleId }
        ?: state.config.takeIf { it.id == scheduleId }
        ?: defaultConfig(scheduleId)
    val targetPeriods = state.allPeriods.filter { it.scheduleId == scheduleId }
        .ifEmpty { state.periods.takeIf { targetConfig.id == state.config.id } ?: defaultPeriods(scheduleId) }
    return state.copy(
        config = targetConfig.copy(id = scheduleId), periods = targetPeriods,
        courses = state.allCourses.filter { it.scheduleId == scheduleId }
    )
}
