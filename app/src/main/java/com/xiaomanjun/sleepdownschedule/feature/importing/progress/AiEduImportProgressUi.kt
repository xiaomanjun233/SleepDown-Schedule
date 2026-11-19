package com.xiaomanjun.sleepdownschedule.feature.importing.progress

import com.xiaomanjun.sleepdownschedule.domain.schedule.resolvedCoursesForPreview

import com.xiaomanjun.sleepdownschedule.core.ui.theme.appUsesDarkTheme

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.widthIn
import com.xiaomanjun.sleepdownschedule.core.ui.interaction.SleepDownLazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.xiaomanjun.sleepdownschedule.core.ui.layout.rememberFloatingInputInsets
import com.xiaomanjun.sleepdownschedule.core.ui.layout.floatingInputPadding
import com.xiaomanjun.sleepdownschedule.core.ui.interaction.softBlurEnter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.catalog.components.LiquidButton
import com.kyant.backdrop.catalog.components.LiquidPanel
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle
import com.kyant.shapes.UnevenRoundedRectangle
import com.xiaomanjun.sleepdownschedule.*
import com.xiaomanjun.sleepdownschedule.R
import com.xiaomanjun.sleepdownschedule.core.ui.chrome.TopGlassIconButton
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.*
import com.xiaomanjun.sleepdownschedule.core.ui.material.controlAccentColor
import com.xiaomanjun.sleepdownschedule.core.ui.scaffold.DetailActivityScaffold
import com.xiaomanjun.sleepdownschedule.core.ui.scaffold.detailContentTopPadding
import com.xiaomanjun.sleepdownschedule.core.ui.scaffold.settingsPageBackground
import com.xiaomanjun.sleepdownschedule.core.ui.scaffold.settingsVisualConfig
import com.xiaomanjun.sleepdownschedule.core.ui.text.*
import com.xiaomanjun.sleepdownschedule.data.ai.FreeAiQuotaKind
import com.xiaomanjun.sleepdownschedule.data.ai.FreeAiQuotaStore
import com.xiaomanjun.sleepdownschedule.feature.agent.*
import com.xiaomanjun.sleepdownschedule.feature.home.day.*
import com.xiaomanjun.sleepdownschedule.feature.importing.*
import com.xiaomanjun.sleepdownschedule.feature.importing.history.*
import com.xiaomanjun.sleepdownschedule.feature.settings.SettingsDetailPageExtra
import com.xiaomanjun.sleepdownschedule.feature.settings.SettingsPage
import com.xiaomanjun.sleepdownschedule.glass.GlassBackdropDomain
import com.xiaomanjun.sleepdownschedule.glass.captureGlassWindowRegion
import com.xiaomanjun.sleepdownschedule.glass.glassBackdropProducer
import com.xiaomanjun.sleepdownschedule.glass.rememberGlassLayerBackdrop
import com.xiaomanjun.sleepdownschedule.glass.ui.*
import com.xiaomanjun.sleepdownschedule.transition.ActivityTransitionCoordinator
import com.xiaomanjun.sleepdownschedule.transition.LiveTransitionButton
import com.xiaomanjun.sleepdownschedule.transition.liveTransitionBoundsInActivity
import com.xiaomanjun.sleepdownschedule.transition.TransitionAnchorProvider
import com.xiaomanjun.sleepdownschedule.transition.TransitionAnchorFrame
import com.xiaomanjun.sleepdownschedule.transition.TransitionLaunchResult
import com.xiaomanjun.sleepdownschedule.transition.TransitionPayload
import com.xiaomanjun.sleepdownschedule.transition.TransitionRouteId
import com.xiaomanjun.sleepdownschedule.transition.attachOpeningSourceSnapshotHandoff
import com.xiaomanjun.sleepdownschedule.transition.legacy.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.utils.MiuixPopupUtils.Companion.MiuixPopupHost

open class AiEduImportProgressActivityHost : ComponentActivity() {
    private var displayedTaskId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        @Suppress("DEPRECATION")
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
        val app = application as CourseScheduleApp
        displayedTaskId = intent.getStringExtra(AiImportTaskManager.EXTRA_TASK_ID)
        AiImportForegroundService.clearCompletion(this, displayedTaskId)
        setContent {
            val state by app.repository.state.collectAsStateWithLifecycle(AppState())
            val activeProgress by AiEduImportProgressSession.progress.collectAsStateWithLifecycle()
            val recoveredEntry by produceState<AiImportHistoryEntry?>(null, displayedTaskId, activeProgress?.taskId) {
                value = if (!displayedTaskId.isNullOrBlank() && activeProgress?.taskId != displayedTaskId) {
                    withContext(Dispatchers.IO) {
                        AiImportHistoryStore.load(this@AiEduImportProgressActivityHost)
                            .firstOrNull { it.context?.taskId == displayedTaskId }
                            ?.let { AiImportHistoryStore.loadEntry(this@AiEduImportProgressActivityHost, it.id) }
                    }
                } else null
            }
            val recoveredDraft = remember(recoveredEntry, state.config) {
                recoveredEntry?.let { AiImportHistoryStore.restore(it, state.config).getOrNull() }
            }
            CourseScheduleTheme(config = state.config) {
                androidx.compose.runtime.key(displayedTaskId) {
                AiEduImportProgressPage(
                    config = state.config,
                    taskId = displayedTaskId,
                    historicalProgress = recoveredDraft?.let { historyConversationProgress(checkNotNull(recoveredEntry), it) },
                    historicalDraft = recoveredDraft,
                    historicalEntryId = recoveredEntry?.id,
                    onImportSubmitted = { returnToScheduleHome() },
                    onScreenModeRequested = {
                        finish()
                        AiEduImportProgressSession.useScreenMode()
                    },
                    onClose = {
                        if (AiEduImportProgressSession.progress.value?.let {
                                it.awaitingConfirmation && (displayedTaskId.isNullOrBlank() || it.taskId == displayedTaskId)
                            } == true) {
                            AiEduImportProgressSession.cancel()
                        }
                        finish()
                    }
                )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        displayedTaskId = intent.getStringExtra(AiImportTaskManager.EXTRA_TASK_ID)
        AiImportForegroundService.clearCompletion(
            this,
            intent.getStringExtra(AiImportTaskManager.EXTRA_TASK_ID)
        )
    }
}

@Composable
internal fun AiEduImportConversationPane(
    config: ScheduleConfigEntity,
    onClose: () -> Unit,
    taskId: String? = null,
    onImportSubmitted: () -> Unit = onClose,
    onScreenModeRequested: () -> Unit = AiEduImportProgressSession::useScreenMode,
    historicalProgress: AiEduImportProgress? = null,
    historicalDraft: ImportDraft? = null,
    historicalEntryId: String? = null,
    showHistoryAction: Boolean = true,
    onImportRequested: ((ImportDraft, Boolean) -> Unit)? = null,
    embedded: Boolean = false,
) {
    AiImportConversationTheme(config) {
    val context = LocalContext.current
    val observedSessionProgress by AiEduImportProgressSession.progress.collectAsStateWithLifecycle()
    val historicalMode = historicalProgress != null && historicalDraft != null
    var localProgress by remember(historicalProgress, historicalDraft) {
        mutableStateOf(if (historicalProgress != null && historicalDraft != null)
            ensureImportCheckpoint(historicalProgress, historicalDraft) else historicalProgress)
    }
    var localPreviewDraft by remember(historicalDraft) { mutableStateOf(historicalDraft) }
    var ownedRevisionTaskId by androidx.compose.runtime.saveable.rememberSaveable(taskId, historicalEntryId) {
        mutableStateOf<String?>(null)
    }
    val sessionProgress = observedSessionProgress?.takeIf {
        taskId.isNullOrBlank() || it.taskId == taskId
    }
    val ownedRevisionProgress = observedSessionProgress?.takeIf {
        ownedRevisionTaskId != null && it.taskId == ownedRevisionTaskId
    }
    val current = ownedRevisionProgress ?: if (historicalMode) {
        checkNotNull(localProgress ?: historicalProgress)
    } else {
        sessionProgress ?: AiEduImportProgress(
            finished = true,
            liveSummary = "此任务已不在当前会话中。可从导入历史恢复已校验阶段，或重新选择材料开始。",
            steps = listOf("等待选择导入任务")
        )
    }
    val ownsLiveTask = ownedRevisionProgress != null || (!historicalMode && sessionProgress != null)
    val saveProposal by EduAdapterSaveProposals.current.collectAsStateWithLifecycle()
    val verifiedScript by EduVerifiedScripts.current.collectAsStateWithLifecycle()
    val freeAiQuotaState by FreeAiQuotaStore.state.collectAsStateWithLifecycle()
    val settingsChanges by AiImportSettingsStore.changes.collectAsStateWithLifecycle()
    val usingManagedFreeAi = remember(settingsChanges, current.taskId) {
        val active = AiImportSettingsStore.loadForRuntime(context) ?: AiImportSettingsStore.load(context)
        AiProviderPresets.isManagedFreeId(active.profile.id)
    }
    val scriptForCurrentPreview = verifiedScript?.takeIf {
        ownsLiveTask && it.proposal.taskId == current.taskId && current.finished && !current.awaitingUserInput && current.error == null
    }
    val scriptSaveState = scriptForCurrentPreview?.submission?.state?.collectAsStateWithLifecycle()?.value
    val showLiveStatus = ownsLiveTask && !current.finished && current.error == null && !current.awaitingConfirmation && current.taskId.isNotBlank()
    val selectedWorkspaceDraft = remember(current.checkpoints, current.selectedCheckpointId, current.taskId, config) {
        current.checkpoints.firstOrNull { it.id == current.selectedCheckpointId && it.taskId == current.taskId }
            ?.restore(config)?.getOrNull()
    }
    val previewDraft = if (ownedRevisionProgress != null) {
        selectedWorkspaceDraft
    } else if (historicalMode) {
        localPreviewDraft
    } else {
        selectedWorkspaceDraft.takeIf { sessionProgress != null }
    }
    // Keep each immutable result at its first appearance in the conversation. A revision
    // inherits the selected draft as input; that must not append the old preview again.
    val archivedPreviews = remember(current.conversationTurns) {
        val seen = HashSet<String>()
        current.conversationTurns.map { turn ->
            turn.artifactPayload.takeIf { it.isNotBlank() && seen.add(it) }
        }
    }
    // Continue from the latest validated result; older previews remain fixed to their own turns.
    val latestDraft = remember(current.checkpoints, historicalDraft, config) {
        current.checkpoints.lastOrNull()?.restore(config)?.getOrNull() ?: historicalDraft
    }
    val previewPayload = remember(latestDraft) { latestDraft?.let { draftToPayload(it).toString() } }
    val currentPreview = latestDraft?.takeIf { !current.awaitingConfirmation && previewPayload !in archivedPreviews }
    fun updateProgress(next: AiEduImportProgress) {
        if (historicalMode) localProgress = next else AiEduImportProgressSession.update(next)
    }
    var importSubmitting by remember(current.taskId) { mutableStateOf(false) }
    fun requestImport(draft: ImportDraft, createNewSchedule: Boolean) {
        // Stop the same attempt before handing the immutable selection to existing confirmation.
        // A notification for an old task must never import another task's preview.
        if (!historicalMode && ownedRevisionProgress == null && sessionProgress == null) return
        if (importSubmitting) return
        importSubmitting = true
        if (AiEduImportProgressSession.progress.value?.let { it.taskId == current.taskId && !it.finished } == true) {
            AiImportTaskManager.pauseImport(context, current.taskId)
        }
        onImportRequested?.invoke(draft, createNewSchedule)
            ?: AiEduImportProgressSession.requestFinalImport(
                draft, createNewSchedule, current.taskId.takeIf { ownsLiveTask && !historicalMode }
            )
        onImportSubmitted()
    }
    val listState = rememberLazyListState()
    val inputInsets = rememberFloatingInputInsets()
    val textColor = LocalAiImportTextColors.current.primary
    val pageTitle = current.routeLabel.takeIf { it.isNotBlank() } ?: "AI 教务导入"
    var previewAttachment by remember { mutableStateOf<AiEduAttachmentPreviewRequest?>(null) }
    var previewSourceHidden by remember { mutableStateOf(false) }
    var rootSize by remember { mutableStateOf(IntSize.Zero) }
    var rootPositionOnScreen by remember { mutableStateOf(Offset.Zero) }
    var rootPositionInWindow by remember { mutableStateOf(Offset.Zero) }
    val previewBackgroundZoom = remember { Animatable(1f) }
    val previewSceneBackdrop = rememberGlassLayerBackdrop(
        domain = GlassBackdropDomain.ActivityBackground,
        providerId = "ai-import-preview-scene"
    ) { drawContent() }
    val conversationPageColor = settingsPageBackground(settingsVisualConfig(config))
    val conversationContentBackdrop = rememberGlassLayerBackdrop(
        domain = GlassBackdropDomain.Content,
        providerId = "ai-import-conversation-content"
    ) {
        drawRect(conversationPageColor)
        drawContent()
    }
    val historySnapshotLayer = rememberGraphicsLayer()
    val historySnapshotRequested = remember { AtomicBoolean(false) }
    var historySnapshotRequestVersion by remember { mutableStateOf(0) }
    val previewBackdrop = rememberScreenScaledBackdrop(
        backdrop = previewSceneBackdrop,
        scale = { previewBackgroundZoom.value },
        rootPositionOnScreen = { rootPositionOnScreen },
        rootSize = { rootSize }
    )
    val conversationScope = rememberCoroutineScope()
    var conversationInput by remember { mutableStateOf("") }
    var supplementalFiles by remember { mutableStateOf<List<AiImportFile>>(emptyList()) }
    var attachmentReading by remember { mutableStateOf(false) }
    var attachmentError by remember { mutableStateOf<String?>(null) }
    val canAttach = ownsLiveTask && AiImportTaskManager.canSupplementFiles(current.taskId) &&
        !current.awaitingConfirmation && (!current.finished || selectedWorkspaceDraft == null)
    val attachmentPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty() || !canAttach || attachmentReading) return@rememberLauncherForActivityResult
        attachmentReading = true
        attachmentError = null
        conversationScope.launch {
            try {
                val selected = loadAiImportFiles(context, uris)
                val combined = (supplementalFiles + selected).distinctBy(AiImportFile::uri)
                require(combined.size <= MaxAiImportFiles && combined.sumOf { it.sizeBytes.toLong() } <= MaxAiImportBatchBytes) {
                    "这组附件最多 20 个、合计 40 MB，请移除部分文件再选择"
                }
                supplementalFiles = combined
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                attachmentError = error.message ?: "读取附件失败，请重新选择"
            } finally {
                attachmentReading = false
            }
        }
    }
    val queuedInputs by remember(current.taskId) { AiImportTaskManager.observeInputs(current.taskId) }.collectAsStateWithLifecycle()
    var editingInputId by remember(current.taskId) { mutableStateOf<String?>(null) }
    fun clearComposer() {
        conversationInput = ""
        supplementalFiles = emptyList()
        attachmentError = null
        editingInputId = null
    }
    fun submitPending(
        mode: com.xiaomanjun.sleepdownschedule.core.ai.AgentMailbox.Mode,
        prompt: String = conversationInput.trim()
    ) {
        if (prompt.isBlank() && supplementalFiles.isEmpty()) return
        val input = AgentTaskInput(prompt, files = supplementalFiles)
        val accepted = editingInputId?.let { AiImportTaskManager.replaceInput(current.taskId, it, input) }
            ?: if (supplementalFiles.isEmpty()) AiImportTaskManager.submitInput(current.taskId, input, mode)
            else AiImportTaskManager.submitSupplementalFiles(current.taskId, input)
        if (accepted) clearComposer()
        else {
            val message = if (editingInputId != null) "该候选已开始处理，修改保留在输入框中"
                else if (AiImportTaskManager.observeInputs(current.taskId).value.size >= 16) "最多保留 16 条候选，请先处理或删除，输入内容已保留"
                else "任务状态已变化，输入内容已保留，请重新发送"
            editingInputId = null
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    var conversationSending by remember { mutableStateOf(false) }
    var composerHeightPx by remember { mutableStateOf(0) }
    val runtimePickerState = rememberAiRuntimePickerState()
    var historySourceHidden by remember { mutableStateOf(false) }
    var historyButtonBounds by remember { mutableStateOf(Rect.Zero) }
    var historyOpening by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    LaunchedEffect(
        historicalMode,
        ownedRevisionTaskId,
        ownedRevisionProgress?.finished,
        selectedWorkspaceDraft
    ) {
        val completed = ownedRevisionProgress
        if (historicalMode && completed?.finished == true) {
            localProgress = completed
            selectedWorkspaceDraft?.let { localPreviewDraft = it }
            ownedRevisionTaskId = null
        }
    }
    LaunchedEffect(current.taskId, current.finished) {
        if (current.finished) {
            conversationSending = false
        }
    }
    BackHandler(enabled = current.awaitingConfirmation) {
        AiEduImportProgressSession.cancel()
        onClose()
    }
    LaunchedEffect(current.returnToBrowser) {
        if (!historicalMode && current.returnToBrowser) onClose()
    }

    Box(
        Modifier
            .fillMaxSize()
            .clipToBounds()
            .then(inputInsets.viewportModifier)
            .onGloballyPositioned {
                rootSize = it.size
                rootPositionOnScreen = it.positionOnScreen()
                rootPositionInWindow = it.boundsInWindow().topLeft
            }
    ) {
        Box(
            Modifier
                    .fillMaxSize()
                    .drawWithContent {
                    if (historySnapshotRequestVersion >= 0 && historySnapshotRequested.compareAndSet(true, false)) {
                        historySnapshotLayer.record { this@drawWithContent.drawContent() }
                    }
                    drawContent()
                }
                .graphicsLayer {
                    val zoom = previewBackgroundZoom.value
                    scaleX = zoom
                    scaleY = zoom
                }
        ) {
            Box(Modifier.fillMaxSize().glassBackdropProducer(previewSceneBackdrop)) {
        DetailActivityScaffold(
            title = pageTitle,
            config = config,
            onBack = onClose,
            compactTopBar = true,
            centerCompactTitle = true,
            avoidIme = false,
            avoidHomeTabBar = embedded,
            topBarActions = { topBarBackdrop ->
                if (showHistoryAction) {
                    AiEduHistoryButton(
                        config = config,
                        backdrop = topBarBackdrop,
                        show = !historySourceHidden,
                        onBoundsChanged = { historyButtonBounds = it },
                        onClick = { sourceBounds, sourceView ->
                            if (!historyOpening) {
                                historyOpening = true
                                conversationScope.launch {
                                    var releaseCover: (() -> Unit)? = null
                                    var sessionOwnsSource = false
                                    try {
                                        val activity = context as? ComponentActivity ?: return@launch
                                        val bounds = sourceView?.liveTransitionBoundsInActivity(activity)
                                            ?: historyButtonBounds.takeUnless { it.isEmpty } ?: sourceBounds
                                        val anchor = TransitionAnchorFrame(bounds, minOf(bounds.width, bounds.height) / 2f, bitmap = null)
                                        fun returnAnchor(frame: TransitionAnchorFrame) = TransitionAnchorProvider {
                                            val current = sourceView?.liveTransitionBoundsInActivity(activity, coveredWindow = true)
                                                ?: historyButtonBounds
                                            frame.copy(boundsInWindow = current).takeIf { it.hasBounds }
                                        }
                                        val result = ActivityTransitionCoordinator.open(
                                            activity, TransitionRouteId.AiProgressToHistory,
                                            Intent(context, AiImportHistoryActivity::class.java),
                                            TransitionPayload(
                                                openingAnchor = anchor,
                                                returnAnchorProvider = returnAnchor(anchor),
                                                backgroundColor = conversationPageColor.toArgb(),
                                                onOpeningSourceHandoff = { releaseCover?.invoke() },
                                                onSourceReleased = { historySourceHidden = false; historyOpening = false },
                                                openingSourceView = sourceView
                                            ),
                                            prepareLegacy = { pending ->
                                                withFrameNanos { }
                                                val captured = anchor.copy(bitmap = captureGlassWindowRegion(activity.window, bounds))
                                                releaseCover = activity.attachOpeningSourceSnapshotHandoff(captured)
                                                val background = if (releaseCover != null) {
                                                    historySourceHidden = true
                                                    historySnapshotRequested.set(true)
                                                    historySnapshotRequestVersion += 1
                                                    withFrameNanos { }
                                                    withFrameNanos { }
                                                    runCatching { historySnapshotLayer.toImageBitmap().asAndroidBitmap() }.getOrNull()
                                                } else null
                                                pending.copy(openingAnchor = captured, returnAnchorProvider = returnAnchor(captured),
                                                    backgroundBitmap = background)
                                            }
                                        )
                                        sessionOwnsSource = result !is TransitionLaunchResult.Failed
                                    } finally {
                                        if (!sessionOwnsSource) {
                                            releaseCover?.invoke()
                                            historySourceHidden = false
                                            historyOpening = false
                                        }
                                    }
                                }
                            }
                        }
                    )
                }
            }
        ) { backdrop ->
            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .glassBackdropProducer(conversationContentBackdrop)
                ) {
                    SleepDownLazyColumn(
                        state = listState,
                        modifier = Modifier.align(Alignment.TopCenter).widthIn(max = 840.dp).fillMaxSize()
                            .followAiImportBottom(listState),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = detailContentTopPadding() + 10.dp,
                            bottom = with(LocalDensity.current) { composerHeightPx.toDp() } + 18.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                current.conversationTurns.forEachIndexed { index, turn ->
                    item(key = "conversation-turn-$index") {
                        AiEduUserMessage(
                            progress = current.copy(userPrompt = turn.userPrompt),
                            textColor = textColor, config = config, backdrop = backdrop,
                            includeAttachment = index == 0,
                            showAttachment = !previewSourceHidden,
                            onPreview = { previewAttachment = it }
                        )
                    }
                    itemsIndexed(groupAiImportReports(turn.activityReports), key = { _, group -> "turn-$index-report-${group.first}" }) { _, group ->
                        if (group.second.first().kind == AiImportReportKind.EXECUTION) AiImportToolGroup(group.second, active = false)
                        else AiImportTranscriptEntry(group.second.first())
                    }
                    item(key = "conversation-turn-$index-reply") {
                        AiEduConversationTurnReply(turn, textColor)
                    }
                    archivedPreviews[index]?.let { payload ->
                        item(key = "conversation-turn-$index-preview") {
                            val restored = remember(payload, config) { parseCheckpointDraft(payload, config) }
                            restored.fold(onSuccess = { draft ->
                                AiEduInlineImportPreview(draft, textColor, config, backdrop,
                                    modifier = Modifier.semantics { contentDescription = "第${index + 1}轮导入预览" },
                                    onCreateNew = { requestImport(draft, true) },
                                    onOverwrite = { requestImport(draft, false) })
                            }, onFailure = {
                                Text("此轮课表无法恢复：${it.message.orEmpty()}", color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodyMedium)
                            })
                        }
                    }
                }
                item(key = "current-user-message") {
                    AiEduUserMessage(
                        progress = current,
                        textColor = textColor,
                        config = config,
                        backdrop = backdrop,
                        showAttachment = !previewSourceHidden,
                        includeAttachment = current.conversationTurns.isEmpty(),
                        onPreview = { previewAttachment = it }
                    )
                }
                val reportGroups = groupAiImportReports(current.activityReports)
                itemsIndexed(reportGroups, key = { _, group -> "current-${current.taskId}-report-${group.first}" }) { index, group ->
                    if (group.second.first().kind == AiImportReportKind.EXECUTION) AiImportToolGroup(group.second, active = !current.finished && current.error == null && index == reportGroups.lastIndex)
                    else AiImportTranscriptEntry(group.second.first())
                }
                if (!current.awaitingConfirmation) item(key = "process-details") {
                    androidx.compose.runtime.key(current.taskId) {
                    AiImportLegacyProgress(current.activityReports, current.reasoningOutput, current.steps,
                        active = !current.finished && current.error == null)
                    }
                }
                current.error?.let { message ->
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                message,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            val showKeyShortcut = usingManagedFreeAi && (
                                message.contains("免费") || message.contains("额度") ||
                                    message.contains("上限") || message.contains("API Key") ||
                                    freeAiQuotaState?.isExhausted(FreeAiQuotaKind.IMPORT) == true
                                )
                            if (showKeyShortcut) {
                                DialogLiquidButton(
                                    backdrop = backdrop,
                                    label = "配置自己的 API Key",
                                    onClick = {
                                        context.startActivity(
                                            Intent(context, SettingsDetailActivity::class.java)
                                                .putExtra(SettingsDetailPageExtra, SettingsPage.AiImport.name)
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
                if (current.awaitingConfirmation) {
                    item {
                        AiEduCaptureConfirmation(
                            progress = current,
                            config = config,
                            backdrop = backdrop,
                            textColor = textColor,
                            onScreenModeRequested = onScreenModeRequested
                        )
                    }
                }
                if (ownsLiveTask && !current.finished && current.error == null && !current.awaitingConfirmation && current.taskId.isNotBlank()) {
                    item(key = "live-model-reasoning") {
                        AiImportReasoningPanel(taskId = current.taskId, reports = current.activityReports)
                    }
                }
                if (current.awaitingUserInput) item(key = "model-questions") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        current.clarificationQuestions.forEachIndexed { index, question ->
                            Text("${index + 1}. $question", color = textColor, style = AiImportTextStyles.Body)
                        }
                    }
                }
                if (current.assistantMessage.isNotBlank()) item(key = "assistant-reply") {
                    AgentMarkdownText(current.assistantMessage, textColor, AiImportTextStyles.Body)
                }
                if (current.finished && !current.awaitingUserInput && current.error == null && latestDraft == null &&
                    current.assistantMessage.isBlank() && current.liveSummary.isNotBlank()) item(key = "recovery-notice") {
                    Text(current.liveSummary, color = LocalAiImportTextColors.current.secondary, style = AiImportTextStyles.Progress)
                }
                current.checkpointNotice?.let { notice -> item(key = "checkpoint-notice") {
                    Text(notice, color = MaterialTheme.colorScheme.error, style = AiImportTextStyles.Secondary)
                } }
                currentPreview?.let { draft ->
                    item(key = "current-preview") {
                        AiEduInlineImportPreview(
                            draft = draft,
                            textColor = textColor,
                            config = config,
                            backdrop = backdrop,
                            modifier = Modifier.semantics { contentDescription = "第${current.conversationTurns.size + 1}轮导入预览" },
                            onCreateNew = {
                                requestImport(draft, true)
                            },
                            onOverwrite = {
                                requestImport(draft, false)
                            },
                            scriptSaveLabel = scriptSaveState?.let {
                                when {
                                    it.uploaded -> "已保存并上传"
                                    it.saving -> "正在保存并上传"
                                    it.savedName != null -> "重试上传"
                                    else -> "保存并上传"
                                }
                            },
                            onSaveScript = scriptForCurrentPreview?.takeIf {
                                scriptSaveState?.let { state -> !state.uploaded && !state.saving } == true
                            }?.let { script ->
                                { EduAdapterSaveProposals.offer(script.proposal) }
                            }
                        )
                    }
                }
                    }
                }

                if (listState.canScrollForward) {
                    DialogLiquidButton(
                        backdrop = backdrop, label = "回到最新",
                        modifier = Modifier.align(Alignment.BottomCenter)
                            .padding(bottom = with(density) { composerHeightPx.toDp() } + 12.dp).size(44.dp).softBlurEnter(),
                        height = 44.dp, horizontalPadding = 0.dp, monochromeNeutral = true,
                        onClick = {
                            conversationScope.launch { listState.scrollToAiImportEnd() }
                        }) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "回到最新",
                            tint = textColor, modifier = Modifier.size(22.dp).rotate(-90f))
                    }
                }
            }
        }
        // A full-page sibling of the scaffold, like the school search dock. Keep it within the
        // page recorder so history and attachment transitions still include the input.
                Column(Modifier.align(Alignment.BottomCenter)
                    .widthIn(max = 840.dp)
                    .onSizeChanged { composerHeightPx = it.height }.floatingInputPadding(inputInsets)) {
                AnimatedVisibility(showLiveStatus, modifier = Modifier.align(Alignment.CenterHorizontally),
                    enter = expandVertically(spring(dampingRatio = 0.9f, stiffness = 420f), expandFrom = Alignment.Bottom, clip = false),
                    exit = shrinkVertically(spring(dampingRatio = 0.9f, stiffness = 420f), shrinkTowards = Alignment.Bottom, clip = false) + fadeOut(tween(160))) {
                    AiImportStatusCapsule(current.taskId, config, conversationContentBackdrop,
                        Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                }
                attachmentError?.let { message ->
                    Text(message, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                        color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                AgentInputCapsules(
                    inputs = queuedInputs,
                    attachment = when {
                        attachmentReading -> "正在读取附件…"
                        supplementalFiles.isNotEmpty() -> supplementalFiles.joinToString("、") { it.displayName }
                        !current.requestSent && (current.pageText.isNotBlank() || current.screenshotPreviews.isNotEmpty()) -> "已附加课表资料"
                        else -> null
                    },
                    config = config, backdrop = conversationContentBackdrop, foreground = textColor,
                    onRemoveAttachment = if (supplementalFiles.isNotEmpty() && !attachmentReading)
                        ({ supplementalFiles = emptyList(); attachmentError = null }) else null,
                    onEdit = { editingInputId = it.id; conversationInput = it.value.text; supplementalFiles = it.value.files },
                    onRemove = { AiImportTaskManager.removeInput(current.taskId, it); if (editingInputId == it) editingInputId = null },
                    onGuide = { next ->
                        if (!current.finished) AiImportTaskManager.steerInput(current.taskId, next.id)
                        else {
                            val hasFiles = next.value.files.isNotEmpty()
                            if (hasFiles && !AiImportTaskManager.steerInput(current.taskId, next.id)) return@AgentInputCapsules
                            val resumed = AiImportTaskManager.continueImport(context, current.taskId,
                                next.value.text.ifBlank { "核对补充的材料" })
                            if (resumed != null) {
                                if (!hasFiles) AiImportTaskManager.removeInput(current.taskId, next.id)
                                ownedRevisionTaskId = resumed
                            }
                        }
                    })
                AiEduConversationComposer(
                    value = conversationInput,
                    sending = conversationSending || (!current.finished && !current.awaitingConfirmation),
                    canInterrupt = ownsLiveTask && AiImportTaskManager.canInterrupt(current.taskId),
                    awaitingUserInput = current.awaitingUserInput,
                    onStop = { AiImportTaskManager.pauseImport(context, current.taskId) },
                    config = config,
                    backdrop = conversationContentBackdrop,
                    runtimePickerState = runtimePickerState,
                    textColor = textColor,
                    attachmentVisible = !current.requestSent &&
                        (current.pageText.isNotBlank() || current.screenshotPreviews.isNotEmpty()),
                    hasSupplementalFiles = supplementalFiles.isNotEmpty(),
                    onAddAttachment = if (canAttach && !attachmentReading)
                        ({ attachmentPicker.launch(arrayOf("*/*")) }) else null,
                    modifier = Modifier,
                    onValueChange = { conversationInput = it.take(6_000) },
                    onSend = send@{
                        if (attachmentReading) return@send
                        val prompt = conversationInput.trim().ifBlank {
                            when {
                                supplementalFiles.isNotEmpty() -> "请核对补充的材料。"
                                current.awaitingConfirmation -> current.userPrompt
                                else -> return@send
                            }
                        }
                        if (editingInputId != null) {
                            submitPending(com.xiaomanjun.sleepdownschedule.core.ai.AgentMailbox.Mode.QUEUE, prompt)
                            return@send
                        }
                        if (current.awaitingConfirmation) {
                            conversationInput = ""
                            updateProgress(current.copy(userPrompt = prompt))
                            AiEduImportProgressSession.confirm()
                        } else if (ownsLiveTask && !current.finished) {
                            submitPending(com.xiaomanjun.sleepdownschedule.core.ai.AgentMailbox.Mode.QUEUE, prompt)
                        } else if (ownsLiveTask && AiImportTaskManager.canInterrupt(current.taskId)) {
                            ownedRevisionTaskId = AiImportTaskManager.continueImport(context, current.taskId, prompt, supplementalFiles)
                            if (ownedRevisionTaskId != null) clearComposer()
                            else if (supplementalFiles.isNotEmpty()) attachmentError = "当前无法继续解析，已保留所选附件"
                        } else {
                            if (supplementalFiles.isNotEmpty()) {
                                attachmentError = "原教务任务已结束，请重新打开教务页面导入"
                                return@send
                            }
                            if (conversationSending || !current.finished) return@send
                            val baseDraft = latestDraft ?: previewDraft ?: return@send
                            conversationInput = ""
                            conversationSending = true
                            val settings = AiImportSettingsStore.loadForRuntime(context)
                                ?: AiImportSettingsStore.load(context)
                            ownedRevisionTaskId = AiImportTaskManager.startRevision(
                                context = context,
                                baseDraft = baseDraft,
                                instruction = prompt,
                                baseProgress = if (latestDraft != null) current.copy(
                                    selectedCheckpointId = current.checkpoints.lastOrNull()?.id,
                                    checkpointSelectionLocked = false) else current,
                                settings = settings,
                                historicalEntryId = historicalEntryId
                            )
                        }
                    }
                )
                }
                // The composer is a full-page sibling of the scaffold, so its cascading menus fall
                // back to the process-wide popup registry. Without a root host in this Activity the
                // model picker toggles `expanded` but nothing ever renders the popup.
                MiuixPopupHost()
            }
        }
        AiEduBrowserWindow(current.taskId.takeIf { ownsLiveTask }, config, conversationContentBackdrop,
            Modifier.fillMaxSize().padding(top = detailContentTopPadding() + 4.dp,
                bottom = with(density) { composerHeightPx.toDp() } + 8.dp))
        val verified = scriptForCurrentPreview
        saveProposal?.takeIf { verified?.proposal?.id == it.id && latestDraft != null }?.let { proposal ->
            if (verified != null && scriptSaveState != null && !scriptSaveState.saving && !scriptSaveState.uploaded) {
                androidx.compose.runtime.key(proposal.id) {
                    EduScriptSaveDialog(
                        initialName = scriptSaveState.savedName ?: proposal.name,
                        config = config,
                        onDismiss = { EduAdapterSaveProposals.clear(proposal.id) },
                        onConfirm = { school ->
                            EduAdapterSaveProposals.clear(proposal.id)
                            AiEduScriptUploader.saveAndUploadInBackground(context, verified, school)
                        }
                    )
                }
            }
        }
        previewAttachment?.let { request ->
            AiEduAttachmentMorphOverlay(
                request = request,
                rootSize = rootSize,
                textColor = textColor,
                config = config,
                backdrop = previewBackdrop,
                backgroundZoom = previewBackgroundZoom,
                onSourceHandoff = { previewSourceHidden = true },
                onClosed = {
                    previewAttachment = null
                    previewSourceHidden = false
                }
            )
        }
    }
}
}

@Composable
private fun AiEduConversationTurnReply(
    turn: AiEduImportConversationTurn,
    textColor: Color
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        AiImportLegacyProgress(turn.activityReports, turn.reasoningOutput)
        if (turn.status != "已完成") Text(turn.status,
            color = if (turn.status == "已停止，等待补充") LocalAiImportTextColors.current.secondary else MaterialTheme.colorScheme.error,
            style = AiImportTextStyles.Secondary)
        if (turn.assistantMessage.isNotBlank()) AgentMarkdownText(turn.assistantMessage, textColor, AiImportTextStyles.Body)
    }
}

@Composable
private fun AiEduCaptureConfirmation(
    progress: AiEduImportProgress,
    config: ScheduleConfigEntity,
    backdrop: Backdrop?,
    textColor: Color,
    onScreenModeRequested: () -> Unit
) {
    val hasVisualCaptureAction = progress.screenModeActionLabel.isNotBlank()
    val hasCapturedText = progress.hasReadablePageText ?: progress.pageText.isNotBlank()
    val hasCapturedVisual = progress.screenshotPreviews.isNotEmpty()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            progress.confirmationTitle.ifBlank {
                if (hasCapturedText || hasCapturedVisual) "确认发送课表资料" else "未能可靠读取课表文字"
            },
            color = textColor,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            progress.confirmationMessage.ifBlank { if (hasCapturedVisual) {
                "已准备高清视觉截取，请检查上方附件。截图会按页面顺序发送，重叠区域只用于校对，不会重复导入课程。"
            } else if (!hasCapturedText && hasVisualCaptureAction) {
                "当前课表可能由图片、Canvas 或跨域页面渲染。你可以进入高清识屏，应用会按页面位置截取多张清晰视口图，不会压成模糊长图；确认后才会发送给视觉模型。"
            } else if (hasCapturedText && hasVisualCaptureAction) {
                "已抓取到页面文字；如果页面包含图片、Canvas 或复杂排版，也可以改用高清视觉截取。应用会发送多张清晰视口图，不会压成模糊长图。"
            } else if (hasCapturedText) {
                "已抓取到页面文字。需要识别图片、Canvas 或复杂排版时，可切换到支持视觉输入的模型后使用高清视觉截取。"
            } else {
                "当前模型无法使用高清视觉截取。请切换到支持视觉输入的模型，或返回可复制文字的课表页面。"
            } },
            color = LocalAiImportTextColors.current.secondary,
            style = MaterialTheme.typography.bodyMedium
        )
        val actions = if (!hasCapturedText && !hasCapturedVisual && hasVisualCaptureAction) {
            listOfNotNull(
                LiquidAlertAction(
                    progress.screenModeActionLabel.ifBlank { "进入高清识屏" },
                    LiquidAlertActionStyle.Primary,
                    onClick = onScreenModeRequested
                ),
                progress.confirmActionLabel.takeIf { it.isNotBlank() }?.let { label ->
                    LiquidAlertAction(
                        label,
                        LiquidAlertActionStyle.Secondary,
                        onClick = AiEduImportProgressSession::confirm
                    )
                },
                LiquidAlertAction(
                    progress.cancelActionLabel.ifBlank { "返回重抓" },
                    LiquidAlertActionStyle.Cancel,
                    onClick = AiEduImportProgressSession::cancel
                )
            )
        } else {
            listOfNotNull(
                progress.confirmActionLabel.takeIf { it.isNotBlank() }?.let { label ->
                    LiquidAlertAction(
                        label,
                        LiquidAlertActionStyle.Primary,
                        onClick = AiEduImportProgressSession::confirm
                    )
                },
                progress.screenModeActionLabel.takeIf { it.isNotBlank() }?.let { label ->
                    LiquidAlertAction(
                        label,
                        LiquidAlertActionStyle.Secondary,
                        onClick = onScreenModeRequested
                    )
                },
                progress.secondaryConfirmActionLabel
                    .takeIf { progress.screenModeActionLabel.isBlank() && it.isNotBlank() }
                    ?.let { label ->
                        LiquidAlertAction(
                            label,
                            LiquidAlertActionStyle.Secondary,
                            onClick = AiEduImportProgressSession::secondaryConfirm
                        )
                    },
                LiquidAlertAction(
                    progress.cancelActionLabel.ifBlank { "返回重抓" },
                    LiquidAlertActionStyle.Cancel,
                    onClick = AiEduImportProgressSession::cancel
                )
            )
        }.take(3)
        if (actions.isNotEmpty()) {
            LiquidAlertActions(actions, backdrop, config)
        }
    }
}

private data class AiEduAttachmentPreview(
    val title: String,
    val text: String = "",
    val images: List<RenderedPageImage> = emptyList()
)

private data class AiEduAttachmentPreviewRequest(
    val attachment: AiEduAttachmentPreview,
    val sourceBounds: Rect
)

@Composable
private fun AiEduHistoryButton(
    config: ScheduleConfigEntity,
    backdrop: Backdrop?,
    show: Boolean,
    onClick: (Rect, View?) -> Unit,
    onBoundsChanged: (Rect) -> Unit,
) {
    var bounds by remember { mutableStateOf(Rect.Zero) }
    LiveTransitionButton(
        modifier = Modifier.size(42.dp).onGloballyPositioned {
            bounds = it.boundsInWindow()
            onBoundsChanged(bounds)
        },
        show = show,
    ) { sourceView ->
        TopGlassIconButton(
            backdrop = backdrop,
            config = config,
            iconRes = R.drawable.ic_history,
            contentDescription = "历史记录",
            onClick = { if (show && !bounds.isEmpty) onClick(bounds, sourceView) },
            modifier = Modifier.fillMaxSize(),
            darkOverride = appUsesDarkTheme(config),
            foregroundOverride = LocalAiImportTextColors.current.primary
        )
    }
}

@Composable
private fun AiEduUserMessage(
    progress: AiEduImportProgress,
    textColor: Color,
    config: ScheduleConfigEntity,
    backdrop: Backdrop?,
    showAttachment: Boolean,
    includeAttachment: Boolean = true,
    onPreview: (AiEduAttachmentPreviewRequest) -> Unit
) {
    val density = LocalDensity.current
    val attachment = remember(progress.attachmentTitle, progress.pageText, progress.screenshotPreviews) {
        AiEduAttachmentPreview(
            title = progress.attachmentTitle.ifBlank {
                if (progress.screenshotPreviews.isNotEmpty()) "课表页面截图" else "课表页面文字"
            },
            text = progress.pageText,
            images = progress.screenshotPreviews
        )
    }
    val hasAttachment = attachment.text.isNotBlank() || attachment.images.isNotEmpty()
    var attachmentSize by remember(attachment) { mutableStateOf(IntSize.Zero) }
    var attachmentBounds by remember(attachment) { mutableStateOf(Rect.Zero) }
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (includeAttachment && hasAttachment) {
            if (showAttachment || attachmentSize == IntSize.Zero) {
                AiEduLiquidPanel(
                    backdrop = backdrop,
                    config = config,
                    modifier = Modifier
                        .fillMaxWidth(0.72f)
                        .onGloballyPositioned {
                            attachmentSize = it.size
                            attachmentBounds = it.boundsInRoot()
                        }
                        .clickable {
                            if (attachmentBounds.width > 0f) {
                                onPreview(AiEduAttachmentPreviewRequest(attachment, attachmentBounds))
                            }
                        },
                    accent = Color(0xFF8E8E93),
                    shape = RoundedRectangle(20.dp)
                ) {
                    AiEduAttachmentCardContent(attachment = attachment, textColor = textColor)
                }
            } else {
                Spacer(
                    Modifier.size(
                        width = with(density) { attachmentSize.width.toDp() },
                        height = with(density) { attachmentSize.height.toDp() }
                    )
                )
            }
        }
        if (progress.userPrompt.isNotBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.86f)
                    .wrapContentWidth(Alignment.End)
                    .clip(UnevenRoundedRectangle(22.dp, 22.dp, 6.dp, 22.dp))
                    .background(if (appUsesDarkTheme(config)) Color(0xFF142D49) else Color(0xFFDCEBFF))
            ) {
                Text(
                    progress.userPrompt,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    color = textColor,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}

@Composable
private fun AiEduAttachmentCardContent(
    attachment: AiEduAttachmentPreview,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        AiEduAttachmentThumbnail(
            attachment = attachment,
            modifier = Modifier.size(width = 44.dp, height = 50.dp)
        )
        Column(Modifier.weight(1f)) {
            Text(
                attachment.title,
                color = textColor,
                style = AiImportTextStyles.SmallTitle,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                if (attachment.images.isNotEmpty()) "${attachment.images.size} 页 · 点击预览" else "${attachment.text.length} 字 · 点击预览",
                color = LocalAiImportTextColors.current.muted,
                style = AiImportTextStyles.Caption,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun AiEduAttachmentThumbnail(
    attachment: AiEduAttachmentPreview,
    modifier: Modifier = Modifier
) {
    val image = attachment.images.firstOrNull()
    val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, image?.base64) {
        value = image?.let {
            withContext(Dispatchers.Default) {
                runCatching {
                    val bytes = Base64.decode(it.base64, Base64.DEFAULT)
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                }.getOrNull()
            }
        }
    }
    Box(
        modifier = modifier
            .clip(RoundedRectangle(12.dp))
            .background(Color(0xFF0A84FF).copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!,
                contentDescription = "${attachment.title} 首页缩略图",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Icon(
                painter = painterResource(R.drawable.ic_image_attachment),
                contentDescription = null,
                tint = Color(0xFF0A84FF),
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun AiEduAttachmentMorphOverlay(
    request: AiEduAttachmentPreviewRequest,
    rootSize: IntSize,
    textColor: Color,
    config: ScheduleConfigEntity,
    backdrop: Backdrop?,
    backgroundZoom: Animatable<Float, AnimationVector1D>,
    onSourceHandoff: () -> Unit,
    onClosed: () -> Unit
) {
    if (rootSize == IntSize.Zero) return
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val progress = remember(request) { Animatable(0f) }
    var closing by remember(request) { mutableStateOf(false) }
    val target = Rect(0f, 0f, rootSize.width.toFloat(), rootSize.height.toFloat())
    val screenCornerRadiusPx = deviceScreenCornerRadiusPx()
    val morphProgress = progress.value.coerceIn(0f, 1f)
    val source = request.sourceBounds
    val rect = Rect(
        left = source.left + (target.left - source.left) * morphProgress,
        top = source.top + (target.top - source.top) * morphProgress,
        right = source.right + (target.right - source.right) * morphProgress,
        bottom = source.bottom + (target.bottom - source.bottom) * morphProgress
    )
    val sourceHandoff = aiAttachmentPreviewSmoothStep(0.06f, 0.32f, morphProgress)
    val sourceAlpha = 1f - sourceHandoff
    val sourceScale = 1f - 0.018f * sourceHandoff
    val contentReveal = aiAttachmentPreviewSmoothStep(0.14f, 0.82f, morphProgress)
    val contentMotionBlurPx =
        detailMotionBlurRadiusDp(morphProgress) * 0.65f * density.density
    val sourceCornerRadiusPx = with(density) { 20.dp.toPx() }
    val cornerRadiusPx = sourceCornerRadiusPx +
        (screenCornerRadiusPx - sourceCornerRadiusPx) * morphProgress
    val fullyOpen = morphProgress >= 0.999f
    val renderedCornerRadiusPx = if (fullyOpen) 0f else cornerRadiusPx
    val shellColor = if (appUsesDarkTheme(config)) Color(0xFF202124) else Color(0xFFF7F7F8)
    fun dismiss() {
        if (!closing) {
            closing = true
            scope.launch {
                coroutineScope {
                    launch {
                        progress.animateTo(
                            0f,
                            tween(
                                AiAttachmentPreviewCloseDurationMillis,
                                easing = AiAttachmentPreviewCloseEasing
                            )
                        )
                    }
                    launch {
                        backgroundZoom.animateTo(
                            1f,
                            tween(
                                AiAttachmentPreviewBackgroundDurationMillis,
                                easing = AiAttachmentPreviewCloseEasing
                            )
                        )
                    }
                }
                onClosed()
            }
        }
    }
    LaunchedEffect(request) {
        // The overlay first commits an exact copy of the source card. Only then is the
        // original card removed from the list, matching the established Agent handoff.
        withFrameNanos { }
        onSourceHandoff()
        withFrameNanos { }
        coroutineScope {
            launch {
                progress.animateTo(
                    1f,
                    tween(
                        AiAttachmentPreviewOpenDurationMillis,
                        easing = AiAttachmentPreviewOpenEasing
                    )
                )
            }
            launch {
                backgroundZoom.animateTo(
                    AiAttachmentPreviewBackgroundScale,
                    tween(
                        AiAttachmentPreviewBackgroundDurationMillis,
                        delayMillis = 24,
                        easing = AiAttachmentPreviewOpenEasing
                    )
                )
            }
        }
    }
    BackHandler(onBack = ::dismiss)
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.34f * morphProgress))
            .clickable(onClick = ::dismiss)
    )
    Box(
        Modifier
            .offset { IntOffset(rect.left.roundToInt(), rect.top.roundToInt()) }
            .size(
                with(density) { rect.width.toDp() },
                with(density) { rect.height.toDp() }
            )
            .graphicsLayer {
                clip = !fullyOpen
                shape = RoundedRectangle(with(density) { renderedCornerRadiusPx.toDp() })
            }
            .background(
                color = shellColor,
                shape = RoundedRectangle(with(density) { renderedCornerRadiusPx.toDp() })
            )
            .clickable(enabled = false) {}
    ) {
        if (sourceAlpha > 0.01f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = sourceAlpha
                        scaleX = sourceScale
                        scaleY = sourceScale
                    }
            ) {
                AiEduAttachmentCardContent(
                    attachment = request.attachment,
                    textColor = textColor,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        AiEduLiquidPanel(
            backdrop = backdrop,
            config = config,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = contentReveal
                    translationY = (1f - contentReveal) * 12.dp.toPx()
                    scaleX = 0.97f + 0.03f * contentReveal
                    scaleY = 0.97f + 0.03f * contentReveal
                    compositingStrategy = if (contentMotionBlurPx > 0.01f) {
                        CompositingStrategy.Offscreen
                    } else {
                        CompositingStrategy.Auto
                    }
                    renderEffect = platformMotionBlurRenderEffect(contentMotionBlurPx)
                },
            accent = Color(0xFF8E8E93),
            shape = RoundedRectangle(0.dp)
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(top = 34.dp, start = 16.dp, end = 16.dp, bottom = 18.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(request.attachment.title, modifier = Modifier.weight(1f), color = textColor, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text("完成", modifier = Modifier.clickable(onClick = ::dismiss).padding(12.dp), color = controlAccentColor(Color(0xFF0A84FF)), fontWeight = FontWeight.SemiBold)
                }
                SleepDownLazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (request.attachment.text.isNotBlank()) item(key = "attachment-text") {
                        Text(request.attachment.text, color = textColor, style = AiImportTextStyles.Progress)
                    }
                    itemsIndexed(
                        items = request.attachment.images,
                        key = { _, image -> image.pageIndex }
                    ) { index, image ->
                        AiEduPreviewImage(image, "第 ${index + 1} 页")
                    }
                }
            }
        }
    }

}

private const val AiAttachmentPreviewOpenDurationMillis = 430
private const val AiAttachmentPreviewCloseDurationMillis = 360
private const val AiAttachmentPreviewBackgroundDurationMillis = 280
private const val AiAttachmentPreviewBackgroundScale = 1.04f
private val AiAttachmentPreviewOpenEasing = CubicBezierEasing(0.20f, 0f, 0f, 1f)
private val AiAttachmentPreviewCloseEasing = CubicBezierEasing(0.40f, 0f, 0.20f, 1f)

private fun aiAttachmentPreviewSmoothStep(start: Float, end: Float, value: Float): Float {
    val normalized = ((value - start) / (end - start)).coerceIn(0f, 1f)
    return normalized * normalized * (3f - 2f * normalized)
}

@Composable
internal fun deviceScreenCornerRadiusPx(): Float =
    com.xiaomanjun.sleepdownschedule.core.ui.interaction.deviceScreenCornerRadiusPx()

@Composable
private fun AiEduPreviewImage(image: RenderedPageImage, description: String) {
    val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, image.base64) {
        value = withContext(Dispatchers.Default) {
            runCatching {
                val bytes = Base64.decode(image.base64, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            }.getOrNull()
        }
    }
    bitmap?.let {
        Image(
            bitmap = it,
            contentDescription = description,
            modifier = Modifier.fillMaxWidth().clip(RoundedRectangle(20.dp)),
            contentScale = ContentScale.FillWidth
        )
    }
}

@Composable
private fun AiEduInlineImportPreview(
    draft: ImportDraft,
    textColor: Color,
    config: ScheduleConfigEntity,
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
    onCreateNew: () -> Unit,
    onOverwrite: () -> Unit,
    scriptSaveLabel: String? = null,
    onSaveScript: (() -> Unit)? = null
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "导入预览",
            color = textColor,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            "${draft.courses.size} 门课程 · ${draft.config.totalWeeks} 周 · ${draft.periods.size} 个节次",
            color = LocalAiImportTextColors.current.muted,
            style = AiImportTextStyles.Caption
        )
        remember(draft) { draft.resolvedCoursesForPreview() }.forEach { course ->
            ImportPreviewCourseCard(course, draft.periods, draft.config)
        }
        remember(draft) { importConflictNotice(draft) }?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
        if (onSaveScript != null) {
            Text("导入成功后，脚本会保存到我的适配并上传。",
                color = LocalAiImportTextColors.current.muted, style = AiImportTextStyles.Caption)
        }
        LiquidAlertActions(
            actions = listOf(
                LiquidAlertAction("创建新课表", LiquidAlertActionStyle.Primary, onClick = onCreateNew),
                LiquidAlertAction("覆盖当前课表", LiquidAlertActionStyle.Destructive, onClick = onOverwrite)
            ),
            backdrop = backdrop,
            config = config
        )
        if (scriptSaveLabel != null) LiquidAlertActions(
            actions = listOf(LiquidAlertAction(scriptSaveLabel, LiquidAlertActionStyle.Secondary,
                enabled = onSaveScript != null, onClick = { onSaveScript?.invoke() })),
            backdrop = backdrop, config = config
        )
    }
}

@Composable
private fun AiEduConversationComposer(
    value: String,
    sending: Boolean,
    canInterrupt: Boolean,
    awaitingUserInput: Boolean,
    onStop: () -> Unit,
    config: ScheduleConfigEntity,
    backdrop: Backdrop?,
    runtimePickerState: AiRuntimePickerState,
    textColor: Color,
    attachmentVisible: Boolean,
    hasSupplementalFiles: Boolean,
    onAddAttachment: (() -> Unit)?,
    modifier: Modifier = Modifier,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit
) {
    val showStop = sending && canInterrupt && value.isBlank() && !hasSupplementalFiles
    AgentInputLiquidCapsule(
        backdrop = backdrop,
        config = config,
        foreground = textColor,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp).softBlurEnter(),
        shape = Capsule(),
        expanded = false,
        heightOverride = 56.dp,
        wrapContentHeight = true,
        tokens = GlassTokens.dialog().copy(surfaceAlpha = if (glassUsesLightStyle(config)) 0.14f else 0.08f),
        baseSurfaceColorOverride = Color.White,
        interactionEnabledAt = { _, _ -> true }
    ) {
        Column(
            Modifier
                .padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (onAddAttachment != null) {
                    Box(Modifier.size(40.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onAddAttachment),
                        contentAlignment = Alignment.Center) {
                        Icon(painterResource(R.drawable.ic_add_course), "添加附件", tint = textColor, modifier = Modifier.size(24.dp))
                    }
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
                    textStyle = AiImportTextStyles.Input.copy(color = textColor),
                    cursorBrush = SolidColor(Color(0xFF0A84FF)),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { onSend() }),
                    maxLines = 4,
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (value.isBlank()) {
                                AutoFitSingleLineText(
                                    text = if (awaitingUserInput) {
                                        "回答问题，或补充导入要求…"
                                    } else if (sending && canInterrupt) {
                                        "发送补充要求，加入待处理消息…"
                                    } else if (attachmentVisible) {
                                        "帮我按规则导入…"
                                    } else {
                                        "告诉 AI 哪里需要修改…"
                                    },
                                    color = LocalAiImportTextColors.current.muted,
                                    style = AiImportTextStyles.Input
                                )
                            }
                            inner()
                        }
                    }
                )
                AiRuntimePicker(
                    state = runtimePickerState,
                    config = config,
                    backdrop = backdrop,
                    foregroundOverride = textColor
                )
                if (backdrop != null) {
                    LiquidButton(
                        onClick = { if (showStop) onStop() else onSend() },
                        backdrop = backdrop,
                        modifier = Modifier.size(40.dp),
                        height = 40.dp,
                        surfaceColor = controlAccentColor(Color(0xFF0A84FF)).copy(alpha = 0.88f),
                        tint = controlAccentColor(Color(0xFF0A84FF)),
                        contentPadding = PaddingValues(0.dp),
                        blurRadius = 4.dp,
                        lensHeight = 14.dp,
                        lensAmount = 18.dp,
                        chromaticAberration = false
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(if (showStop) "■" else "↑", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).background(controlAccentColor(Color(0xFF0A84FF))).clickable {
                            if (showStop) onStop() else onSend()
                        },
                        contentAlignment = Alignment.Center
                    ) { Text(if (showStop) "■" else "↑", color = Color.White, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

@Composable
private fun AiEduLiquidPanel(
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    modifier: Modifier = Modifier,
    accent: Color,
    shape: Shape = RoundedRectangle(28.dp),
    content: @Composable BoxScope.() -> Unit
) {
    val lightStyle = glassUsesLightStyle(config)
    val surfaceColor = if (lightStyle) {
        Color.White.copy(alpha = 0.14f)
    } else {
        Color.White.copy(alpha = 0.08f)
    }
    if (backdrop != null) {
        LiquidPanel(
            backdrop = backdrop,
            modifier = modifier,
            shape = shape,
            surfaceColor = surfaceColor,
            blurRadius = 18.dp,
            content = content
        )
    } else {
        Box(
            modifier = Modifier
                .then(modifier)
                .clip(shape)
                .background(
                    if (appUsesDarkTheme(config)) Color(0xFF202124) else Color(0xFFF7F7F8)
                ),
            content = content
        )
    }
}
