package com.xiaomanjun.sleepdownschedule.app.ui

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kyant.backdrop.Backdrop
import com.xiaomanjun.sleepdownschedule.AiImportHistoryActivity
import com.xiaomanjun.sleepdownschedule.AppState
import com.xiaomanjun.sleepdownschedule.CourseScheduleApp
import com.xiaomanjun.sleepdownschedule.CourseScheduleTheme
import com.xiaomanjun.sleepdownschedule.EduImportActivity
import com.xiaomanjun.sleepdownschedule.ImportDraft
import com.xiaomanjun.sleepdownschedule.R
import com.xiaomanjun.sleepdownschedule.app.state.ScheduleViewModel
import com.xiaomanjun.sleepdownschedule.app.state.ScheduleViewModelFactory
import com.xiaomanjun.sleepdownschedule.core.ui.chrome.TopGlassIconButton
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LiquidAlertAction
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LiquidAlertActionStyle
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LiquidAlertDialog
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.SleepDownDesignTokens
import com.xiaomanjun.sleepdownschedule.core.ui.interaction.findActivity
import com.xiaomanjun.sleepdownschedule.core.ui.scaffold.DetailActivityScaffold
import com.xiaomanjun.sleepdownschedule.core.ui.scaffold.detailContentTopPadding
import com.xiaomanjun.sleepdownschedule.core.ui.scaffold.settingsVisualConfig
import com.xiaomanjun.sleepdownschedule.core.ui.settings.CouiSettingsTabletDetailPaneScaffold
import com.xiaomanjun.sleepdownschedule.core.ui.theme.appUsesDarkTheme
import com.xiaomanjun.sleepdownschedule.feature.course.management.HomeMenuActivitySourceFallback
import com.xiaomanjun.sleepdownschedule.feature.importing.EduSchoolPickerScreen
import com.xiaomanjun.sleepdownschedule.feature.importing.AiImportHistoryBackgroundCapture
import com.xiaomanjun.sleepdownschedule.feature.importing.ConfirmScheduleScreen
import com.xiaomanjun.sleepdownschedule.feature.importing.NormalizedAiManualImportScreen
import com.xiaomanjun.sleepdownschedule.feature.importing.PendingImportSetupStore
import com.xiaomanjun.sleepdownschedule.feature.importing.UnifiedImportTabs
import com.xiaomanjun.sleepdownschedule.feature.importing.cropToAiHistorySource
import com.xiaomanjun.sleepdownschedule.feature.importing.history.AiImportHistoryPage
import com.xiaomanjun.sleepdownschedule.feature.importing.history.AiImportHistoryRecordPane
import com.xiaomanjun.sleepdownschedule.feature.importing.ShiguangWarehouse
import com.xiaomanjun.sleepdownschedule.feature.importing.shiguang.ShiguangWarehouseUpdater
import com.xiaomanjun.sleepdownschedule.feature.importing.shiguang.describeAdapterRefresh
import com.xiaomanjun.sleepdownschedule.feature.importing.toIntentKey
import com.xiaomanjun.sleepdownschedule.glass.captureGlassWindowRegion
import com.xiaomanjun.sleepdownschedule.glass.withRecordingDensity
import com.xiaomanjun.sleepdownschedule.transition.ActivityTransitionCoordinator
import com.xiaomanjun.sleepdownschedule.transition.CrossActivityTransitionHost
import com.xiaomanjun.sleepdownschedule.transition.LiveTransitionButton
import com.xiaomanjun.sleepdownschedule.transition.liveTransitionBoundsInActivity
import com.xiaomanjun.sleepdownschedule.transition.TransitionAnchorFrame
import com.xiaomanjun.sleepdownschedule.transition.TransitionAnchorProvider
import com.xiaomanjun.sleepdownschedule.transition.TransitionLaunchResult
import com.xiaomanjun.sleepdownschedule.transition.TransitionPayload
import com.xiaomanjun.sleepdownschedule.transition.TransitionRouteId
import com.xiaomanjun.sleepdownschedule.transition.attachOpeningSourceSnapshotHandoff
import kotlinx.coroutines.launch

private const val InitialImportTextExtra = "initial_import_text"

internal fun unifiedImportIntent(context: Context, text: String? = null, files: List<Uri> = emptyList()): Intent =
    Intent(context, com.xiaomanjun.sleepdownschedule.EduSchoolSelectActivity::class.java)
        .putExtra(EduSchoolSelectActivityHost.UnifiedImportExtra, true)
        .apply {
            if (text != null) putExtra(InitialImportTextExtra, text)
            val uris = files.distinct()
            if (uris.isNotEmpty()) {
                clipData = ClipData.newRawUri("Schedule import", uris.first()).also { clip ->
                    uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
                }
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }

open class EduSchoolSelectActivityHost : ComponentActivity() {
    companion object {
        const val UnifiedImportExtra = "unified_schedule_import"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ActivityTransitionCoordinator.prepareDestinationBeforeOnCreate(this)
        super.onCreate(savedInstanceState)
        ActivityTransitionCoordinator.installDestinationWindowBackground(this)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        @Suppress("DEPRECATION")
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
        val unifiedImport = intent.getBooleanExtra(UnifiedImportExtra, false)
        setContent {
            val app = application as CourseScheduleApp
            val viewModel: ScheduleViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                factory = ScheduleViewModelFactory(app, app.repository)
            )
            val state by viewModel.state.collectAsStateWithLifecycle()
            val form: UnifiedImportFormState = androidx.lifecycle.viewmodel.compose.viewModel()
            LaunchedEffect(form, unifiedImport) {
                if (unifiedImport) form.initialize(
                    intent.getStringExtra(InitialImportTextExtra),
                    intent.clipData?.let { clip ->
                        (0 until clip.itemCount).mapNotNull { clip.getItemAt(it).uri }
                    }.orEmpty()
                )
            }
            CourseScheduleTheme(config = state.config) {
                CrossActivityTransitionHost(
                    activity = this@EduSchoolSelectActivityHost,
                    handleSystemBack = !form.submitting,
                    sourceContent = {
                        if (!unifiedImport) HomeMenuActivitySourceFallback(
                            config = state.config,
                            highlightedRowIndex = 4
                        )
                    }
                ) { requestClose ->
                    UnifiedImportPage(
                        state = state,
                        viewModel = viewModel,
                        form = form,
                        unifiedImport = unifiedImport,
                        onBack = requestClose,
                        onImported = { scheduleId ->
                            PendingImportSetupStore.put(this@EduSchoolSelectActivityHost, scheduleId)
                            requestClose()
                        }
                    )
                }
            }
        }
    }
}

@Composable
internal fun UnifiedImportPage(
    state: AppState,
    viewModel: ScheduleViewModel,
    form: UnifiedImportFormState,
    onBack: () -> Unit,
    onImported: (Int) -> Unit,
    unifiedImport: Boolean = true,
    embedded: Boolean = false,
) {
    val context = LocalContext.current
    BackHandler(enabled = form.submitting) { }
    val schoolSelected = !unifiedImport || form.selectedTab == 1
    val dark by rememberUpdatedState(appUsesDarkTheme(state.config))
    val retainedTabs = rememberSaveableStateHolder()
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val selectTab by rememberUpdatedState<(Int) -> Unit> { index ->
        if (index != form.selectedTab) {
            focus.clearFocus(force = true)
            keyboard?.hide()
            form.selectTab(index)
        }
    }
    val recordingDensity = LocalDensity.current
    val refreshScope = rememberCoroutineScope()
    var showHistoryPane by rememberSaveable(form.openRequestVersion) { mutableStateOf(false) }
    var historyEntryId by rememberSaveable(form.openRequestVersion) { mutableStateOf<String?>(null) }
    fun backFromHistory() {
        if (historyEntryId != null) historyEntryId = null
        else showHistoryPane = false
    }
    BackHandler(enabled = embedded && showHistoryPane, onBack = ::backFromHistory)
    var historyBounds by remember { mutableStateOf(Rect.Zero) }
    var historyOpening by remember { mutableStateOf(false) }
    var historyHidden by remember { mutableStateOf(false) }
    val historyScene = if (embedded) null else rememberGraphicsLayer()
    fun openHistory(sourceView: View?) {
        if (form.submitting || historyOpening || showHistoryPane) return
        if (embedded) {
            focus.clearFocus(force = true)
            keyboard?.hide()
            showHistoryPane = true
            return
        }
        val scene = historyScene ?: return
        val activity = context.findActivity() ?: return
        historyOpening = true
        refreshScope.launch {
            var removeCover: (() -> Unit)? = null
            var sessionOwnsSource = false
            try {
                focus.clearFocus(force = true)
                keyboard?.hide()
                val bounds = sourceView?.liveTransitionBoundsInActivity(activity)
                    ?: historyBounds
                val anchor = TransitionAnchorFrame(bounds, minOf(bounds.width, bounds.height) / 2f, bitmap = null)
                fun returnAnchor(frame: TransitionAnchorFrame) = TransitionAnchorProvider {
                    val current = sourceView?.liveTransitionBoundsInActivity(
                        activity, coveredWindow = true
                    ) ?: historyBounds
                    frame.copy(boundsInWindow = current).takeIf { it.hasBounds }
                }
                val result = ActivityTransitionCoordinator.open(
                    activity,
                    TransitionRouteId.ManualImportToHistory,
                    Intent(activity, AiImportHistoryActivity::class.java),
                    TransitionPayload(
                        openingAnchor = anchor,
                        returnAnchorProvider = returnAnchor(anchor),
                        onOpeningSourceHandoff = { removeCover?.invoke() },
                        onSourceReleased = { historyHidden = false; historyOpening = false },
                        openingSourceView = sourceView
                    ),
                    prepareLegacy = { pending ->
                        withFrameNanos { }
                        val window = activity.window
                        val decor = window.decorView
                        val windowBounds = Rect(0f, 0f, decor.width.toFloat(), decor.height.toFloat())
                        val fullFrame = captureGlassWindowRegion(window, windowBounds)
                        val snapshot = fullFrame?.let {
                            AiImportHistoryBackgroundCapture(it, 0f, 0f).cropToAiHistorySource(bounds)
                        }
                        val captured = anchor.copy(bitmap = snapshot)
                        removeCover = activity.attachOpeningSourceSnapshotHandoff(captured)
                        if (removeCover != null) {
                            historyHidden = true
                            withFrameNanos { }
                            withFrameNanos { }
                        }
                        val background = if (historyHidden) {
                            runCatching { scene.toImageBitmap().asAndroidBitmap() }.getOrNull()
                        } else fullFrame
                        pending.copy(openingAnchor = captured, returnAnchorProvider = returnAnchor(captured),
                            backgroundBitmap = background)
                    }
                )
                sessionOwnsSource = result !is TransitionLaunchResult.Failed
            } finally {
                if (!sessionOwnsSource) {
                    removeCover?.invoke()
                    historyHidden = false
                    historyOpening = false
                }
            }
        }
    }
    var warehouseGeneration by remember { mutableIntStateOf(0) }
    var warehouseRefreshing by remember { mutableStateOf(false) }
    var warehouseManualRefreshing by remember { mutableStateOf(false) }
    var warehouseRefreshResultMessage by remember { mutableStateOf<String?>(null) }
    fun refreshWarehouse(manual: Boolean) {
        if (warehouseRefreshing) return
        warehouseRefreshing = true
        warehouseManualRefreshing = manual
        refreshScope.launch {
            val beforeAdapters = if (manual) ShiguangWarehouse.loadAdapters(context)
                else emptyList()
            runCatching {
                if (manual) {
                    ShiguangWarehouseUpdater.refresh(context)
                } else {
                    ShiguangWarehouseUpdater.refreshIfStale(context)
                }
            }.onSuccess { result ->
                if (result == null) return@onSuccess
                if (result.changed) warehouseGeneration += 1
                if (manual) {
                    val afterAdapters = ShiguangWarehouse.loadAdapters(context)
                    warehouseRefreshResultMessage = describeAdapterRefresh(
                        beforeAdapters, afterAdapters, result.changed
                    )
                }
            }.onFailure { error ->
                if (manual) {
                    warehouseRefreshResultMessage =
                        "更新失败，继续使用当前适配列表：${error.message ?: "网络请求失败"}"
                }
            }
            warehouseRefreshing = false
            warehouseManualRefreshing = false
        }
    }
    LaunchedEffect(schoolSelected) {
        if (schoolSelected && ShiguangWarehouseUpdater.isRefreshStale(context)) {
            refreshWarehouse(manual = false)
        }
    }
    val topBarActions: @Composable (Backdrop?) -> Unit = { topBackdrop ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (schoolSelected) TopGlassIconButton(
                backdrop = topBackdrop,
                config = settingsVisualConfig(state.config),
                iconRes = R.drawable.ic_refresh,
                contentDescription = if (warehouseRefreshing) "正在更新适配器" else "更新适配器",
                onClick = { refreshWarehouse(manual = true) },
                modifier = Modifier.size(SleepDownDesignTokens.SecondaryPage.BackButtonSize),
                buttonHeight = 42.dp
            )
            if (unifiedImport) {
                val historyButton: @Composable (View?) -> Unit = { sourceView ->
                    TopGlassIconButton(
                        backdrop = topBackdrop,
                        config = settingsVisualConfig(state.config),
                        iconRes = R.drawable.ic_history,
                        contentDescription = "导入历史",
                        onClick = { openHistory(sourceView) },
                        modifier = Modifier.size(SleepDownDesignTokens.SecondaryPage.BackButtonSize)
                    )
                }
                if (embedded) historyButton(null)
                else LiveTransitionButton(
                    modifier = Modifier.onGloballyPositioned { historyBounds = it.boundsInWindow() },
                    show = !historyHidden,
                    content = historyButton
                )
            }
        }
    }
    val pageContent: @Composable (Backdrop?) -> Unit = { backdrop ->
        val topPadding = detailContentTopPadding()
        val tabBackdrop by rememberUpdatedState(backdrop)
        val importTabs = remember {
            movableContentOf {
                Box(Modifier.fillMaxWidth().padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
                    UnifiedImportTabs(
                        selectedIndex = form.selectedTab,
                        onSelected = selectTab,
                        dark = dark,
                        enabled = !form.submitting,
                        backdrop = tabBackdrop,
                    )
                }
            }
        }
        Box(Modifier.fillMaxSize()) {
            if (schoolSelected) retainedTabs.SaveableStateProvider("school-import") {
                EduSchoolPickerScreen(
                    state = state,
                    backdrop = backdrop,
                    warehouseGeneration = warehouseGeneration,
                    listHeader = importTabs.takeIf { unifiedImport },
                    onRequestRefresh = { refreshWarehouse(manual = true) },
                    repoSourceEnabled = !warehouseRefreshing,
                    onSelect = { adapter ->
                        context.findActivity()?.let { activity ->
                            ActivityTransitionCoordinator.openImmediate(
                                activity,
                                TransitionRouteId.SchoolSelectToEduImport,
                                Intent(context, EduImportActivity::class.java).putExtra(EduAdapterExtra, adapter.toIntentKey())
                            )
                        }
                    }
                )
            } else Box(Modifier.fillMaxSize().padding(top = topPadding)) {
                NormalizedAiManualImportScreen(
                    state = state, backdrop = backdrop, onCancel = onBack,
                    initialText = form.input,
                    initialFileUris = form.initialFiles,
                    onInitialFileConsumed = { form.initialFiles = emptyList() },
                    inlineHeader = { Box(Modifier.padding(bottom = 10.dp)) { importTabs() } },
                    inlineHasPreview = form.draft != null,
                    inlineSubmitting = form.submitting,
                    onInlineTextChange = { form.input = it; form.draft = null; form.saveError = null },
                    onParsed = { form.draft = it; form.saveError = null },
                    inlinePreview = { header ->
                        form.draft?.let { draft ->
                            ConfirmScheduleScreen(
                                draft = draft, backdrop = backdrop, submitting = form.submitting,
                                errorMessage = form.saveError,
                                onCancel = { if (!form.submitting) form.draft = null },
                                onDraftChanged = { form.draft = it },
                                onConfirm = confirm@{ createNew ->
                                    if (form.submitting) return@confirm
                                    form.submitting = true
                                    form.saveError = null
                                    viewModel.importDraft(draft, createNew, onFailure = {
                                        form.submitting = false
                                        form.saveError = it.message ?: "保存失败，请重试"
                                    }) { scheduleId ->
                                        form.submitting = false
                                        form.reset()
                                        onImported(scheduleId)
                                    }
                                },
                                listHeader = header
                            )
                        }
                    }
                )
            }
        }
        if (warehouseManualRefreshing) {
            LiquidAlertDialog(
                title = "正在更新适配器",
                message = "正在获取最新适配列表。现在可以返回或退到桌面，更新会在后台继续。",
                actions = listOf(
                    LiquidAlertAction(
                        "后台继续",
                        LiquidAlertActionStyle.Secondary,
                        onClick = { warehouseManualRefreshing = false }
                    )
                ),
                config = state.config,
                onDismissRequest = { warehouseManualRefreshing = false }
            )
        }
        warehouseRefreshResultMessage?.let { result ->
            LiquidAlertDialog(
                title = "适配器刷新结果",
                message = result,
                actions = listOf(LiquidAlertAction("知道了", LiquidAlertActionStyle.Primary) {
                    warehouseRefreshResultMessage = null
                }),
                config = state.config,
                onDismissRequest = { warehouseRefreshResultMessage = null }
            )
        }
    }
    val title = if (unifiedImport) "导入课表" else "选择学校"
    if (embedded) {
        Crossfade(
            targetState = showHistoryPane to historyEntryId,
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp).clipToBounds(),
            animationSpec = tween(220),
            label = "ImportPane"
        ) { (history, entryId) ->
            when {
                !history -> retainedTabs.SaveableStateProvider("import-page") {
                    CouiSettingsTabletDetailPaneScaffold(
                        title, state.config, topBarActions = topBarActions, content = pageContent
                    )
                }
                entryId == null -> retainedTabs.SaveableStateProvider("history-page") {
                    AiImportHistoryPage(
                        config = state.config, onBack = { if (historyEntryId == null) showHistoryPane = false },
                        onOpenInPane = {
                            if (showHistoryPane && historyEntryId == null) historyEntryId = it.id
                        }
                    )
                }
                else -> AiImportHistoryRecordPane(
                    config = state.config, entryId = entryId, onBack = { historyEntryId = null },
                    onImportSubmitted = {
                        historyEntryId = null
                        showHistoryPane = false
                        onBack()
                    }
                )
            }
        }
    } else Box(Modifier.fillMaxSize().drawWithContent {
        if (historyHidden && historyScene != null) {
            historyScene.record {
                withRecordingDensity(recordingDensity) { this@drawWithContent.drawContent() }
            }
            drawLayer(historyScene)
        } else {
            drawContent()
        }
    }) {
        DetailActivityScaffold(
            title = title, config = state.config, onBack = { if (!form.submitting) onBack() },
            topBarActions = topBarActions, content = pageContent
        )
    }
}

internal class UnifiedImportFormState(private val savedState: SavedStateHandle) : ViewModel() {
    private var initialized = false
    var openRequestVersion by mutableIntStateOf(0)
        private set
    var selectedTab by mutableIntStateOf(savedState.get<Int>("import_tab") ?: 0)
        private set
    var input by mutableStateOf("")
    var initialFiles by mutableStateOf<List<Uri>>(emptyList())
    var draft by mutableStateOf<ImportDraft?>(null)
    var submitting by mutableStateOf(false)
    var saveError by mutableStateOf<String?>(null)

    fun selectTab(index: Int) {
        selectedTab = index
        savedState["import_tab"] = index
    }

    fun initialize(text: String?, files: List<Uri>) {
        if (initialized) return
        initialized = true
        if (text != null || files.isNotEmpty()) openWith(text, files)
    }

    fun openWith(text: String?, files: List<Uri>) {
        reset()
        input = text.orEmpty()
        initialFiles = files.distinct()
        openRequestVersion++
    }

    fun reset() {
        selectTab(0)
        input = ""
        initialFiles = emptyList()
        draft = null
        saveError = null
    }
}
