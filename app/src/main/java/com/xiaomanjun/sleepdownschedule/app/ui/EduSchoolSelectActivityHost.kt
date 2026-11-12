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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xiaomanjun.sleepdownschedule.AiImportHistoryActivity
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
import com.xiaomanjun.sleepdownschedule.core.ui.scaffold.DetailActivityScaffold
import com.xiaomanjun.sleepdownschedule.core.ui.scaffold.detailContentTopPadding
import com.xiaomanjun.sleepdownschedule.core.ui.scaffold.settingsVisualConfig
import com.xiaomanjun.sleepdownschedule.core.ui.theme.appUsesDarkTheme
import com.xiaomanjun.sleepdownschedule.feature.course.management.HomeMenuActivitySourceFallback
import com.xiaomanjun.sleepdownschedule.feature.importing.EduSchoolPickerScreen
import com.xiaomanjun.sleepdownschedule.feature.importing.AiImportHistoryBackgroundCapture
import com.xiaomanjun.sleepdownschedule.feature.importing.ConfirmScheduleScreen
import com.xiaomanjun.sleepdownschedule.feature.importing.NormalizedAiManualImportScreen
import com.xiaomanjun.sleepdownschedule.feature.importing.PendingImportSetupStore
import com.xiaomanjun.sleepdownschedule.feature.importing.UnifiedImportTabs
import com.xiaomanjun.sleepdownschedule.feature.importing.cropToAiHistorySource
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
            var selectedTab by rememberSaveable { mutableIntStateOf(0) }
            val schoolSelected = !unifiedImport || selectedTab == 1
            val retainedTabs = rememberSaveableStateHolder()
            val focus = LocalFocusManager.current
            val keyboard = LocalSoftwareKeyboardController.current
            val selectTab by rememberUpdatedState<(Int) -> Unit> { index ->
                if (index != selectedTab) {
                    focus.clearFocus(force = true)
                    keyboard?.hide()
                    selectedTab = index
                }
            }
            val recordingDensity = LocalDensity.current
            val refreshScope = rememberCoroutineScope()
            var historyBounds by remember { mutableStateOf(Rect.Zero) }
            var historyOpening by remember { mutableStateOf(false) }
            var historyHidden by remember { mutableStateOf(false) }
            val historyScene = rememberGraphicsLayer()
            fun openHistory(sourceView: View?) {
                if (historyOpening || form.submitting) return
                historyOpening = true
                refreshScope.launch {
                    var removeCover: (() -> Unit)? = null
                    var sessionOwnsSource = false
                    try {
                        focus.clearFocus(force = true)
                        keyboard?.hide()
                        val bounds = sourceView?.liveTransitionBoundsInActivity(this@EduSchoolSelectActivityHost)
                            ?: historyBounds
                        val anchor = TransitionAnchorFrame(bounds, minOf(bounds.width, bounds.height) / 2f, bitmap = null)
                        fun returnAnchor(frame: TransitionAnchorFrame) = TransitionAnchorProvider {
                            val current = sourceView?.liveTransitionBoundsInActivity(
                                this@EduSchoolSelectActivityHost, coveredWindow = true
                            ) ?: historyBounds
                            frame.copy(boundsInWindow = current).takeIf { it.hasBounds }
                        }
                        val result = ActivityTransitionCoordinator.open(
                            this@EduSchoolSelectActivityHost,
                            TransitionRouteId.ManualImportToHistory,
                            Intent(this@EduSchoolSelectActivityHost, AiImportHistoryActivity::class.java),
                            TransitionPayload(
                                openingAnchor = anchor,
                                returnAnchorProvider = returnAnchor(anchor),
                                onOpeningSourceHandoff = { removeCover?.invoke() },
                                onSourceReleased = { historyHidden = false; historyOpening = false },
                                openingSourceView = sourceView
                            ),
                            prepareLegacy = { pending ->
                                withFrameNanos { }
                                val decor = window.decorView
                                val windowBounds = Rect(0f, 0f, decor.width.toFloat(), decor.height.toFloat())
                                val fullFrame = captureGlassWindowRegion(window, windowBounds)
                                val snapshot = fullFrame?.let {
                                    AiImportHistoryBackgroundCapture(it, 0f, 0f).cropToAiHistorySource(bounds)
                                }
                                val captured = anchor.copy(bitmap = snapshot)
                                removeCover = attachOpeningSourceSnapshotHandoff(captured)
                                if (removeCover != null) {
                                    historyHidden = true
                                    withFrameNanos { }
                                    withFrameNanos { }
                                }
                                val background = if (historyHidden) runCatching {
                                    historyScene.toImageBitmap().asAndroidBitmap()
                                }.getOrNull() else fullFrame
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
                    val beforeAdapters = if (manual) ShiguangWarehouse.loadAdapters(this@EduSchoolSelectActivityHost)
                        else emptyList()
                    runCatching {
                        if (manual) {
                            ShiguangWarehouseUpdater.refresh(this@EduSchoolSelectActivityHost)
                        } else {
                            ShiguangWarehouseUpdater.refreshIfStale(this@EduSchoolSelectActivityHost)
                        }
                    }.onSuccess { result ->
                        if (result == null) return@onSuccess
                        if (result.changed) warehouseGeneration += 1
                        if (manual) {
                            val afterAdapters = ShiguangWarehouse.loadAdapters(this@EduSchoolSelectActivityHost)
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
                if (schoolSelected && ShiguangWarehouseUpdater.isRefreshStale(this@EduSchoolSelectActivityHost)) {
                    refreshWarehouse(manual = false)
                }
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
                    BackHandler(enabled = form.submitting) { }
                    Box(Modifier.fillMaxSize().drawWithContent {
                        if (historyHidden) {
                            historyScene.record {
                                withRecordingDensity(recordingDensity) { this@drawWithContent.drawContent() }
                            }
                            drawLayer(historyScene)
                        } else drawContent()
                    }) {
                    DetailActivityScaffold(
                        title = if (unifiedImport) "导入课表" else "选择学校",
                        config = state.config,
                        onBack = { if (!form.submitting) requestClose() },
                        topBarActions = { topBackdrop ->
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
                                if (unifiedImport) LiveTransitionButton(
                                    modifier = Modifier.onGloballyPositioned { historyBounds = it.boundsInWindow() },
                                    show = !historyHidden,
                                ) { sourceView ->
                                    TopGlassIconButton(
                                        backdrop = topBackdrop,
                                        config = settingsVisualConfig(state.config),
                                        iconRes = R.drawable.ic_history,
                                        contentDescription = "导入历史",
                                        onClick = { openHistory(sourceView) },
                                        modifier = Modifier.size(SleepDownDesignTokens.SecondaryPage.BackButtonSize)
                                    )
                                }
                            }
                        }
                    ) { backdrop ->
                        val topPadding = detailContentTopPadding()
                        val tabBackdrop by rememberUpdatedState(backdrop)
                        val importTabs = remember {
                            movableContentOf {
                                Box(Modifier.fillMaxWidth().padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
                                    UnifiedImportTabs(
                                        selectedIndex = selectedTab,
                                        onSelected = selectTab,
                                        dark = appUsesDarkTheme(state.config),
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
                                    onSelect = { adapter ->
                                        ActivityTransitionCoordinator.openImmediate(
                                            this@EduSchoolSelectActivityHost,
                                            TransitionRouteId.SchoolSelectToEduImport,
                                            Intent(this@EduSchoolSelectActivityHost, EduImportActivity::class.java)
                                                .putExtra(EduAdapterExtra, adapter.toIntentKey())
                                        )
                                    }
                                )
                            } else Box(Modifier.fillMaxSize().padding(top = topPadding)) {
                                NormalizedAiManualImportScreen(
                                    state = state, backdrop = backdrop, onCancel = requestClose,
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
                                                        PendingImportSetupStore.put(this@EduSchoolSelectActivityHost, scheduleId)
                                                        requestClose()
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
                    }
                }
            }
        }
    }
}

internal class UnifiedImportFormState : ViewModel() {
    private var initialized = false
    var input by mutableStateOf("")
    var initialFiles by mutableStateOf<List<Uri>>(emptyList())
    var draft by mutableStateOf<ImportDraft?>(null)
    var submitting by mutableStateOf(false)
    var saveError by mutableStateOf<String?>(null)

    fun initialize(text: String?, files: List<Uri>) {
        if (initialized) return
        initialized = true
        input = text.orEmpty()
        initialFiles = files
    }
}
