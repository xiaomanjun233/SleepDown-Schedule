package com.xiaomanjun.sleepdownschedule.feature.importing.progress

import com.xiaomanjun.sleepdownschedule.domain.schedule.resolvedCoursesForPreview

import com.xiaomanjun.sleepdownschedule.core.ui.theme.appUsesDarkTheme

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.xiaomanjun.sleepdownschedule.feature.agent.*
import com.xiaomanjun.sleepdownschedule.feature.home.day.*
import com.xiaomanjun.sleepdownschedule.feature.importing.*
import com.xiaomanjun.sleepdownschedule.feature.importing.history.*
import com.xiaomanjun.sleepdownschedule.glass.GlassBackdropDomain
import com.xiaomanjun.sleepdownschedule.glass.glassBackdropProducer
import com.xiaomanjun.sleepdownschedule.glass.rememberGlassLayerBackdrop
import com.xiaomanjun.sleepdownschedule.glass.ui.*
import com.xiaomanjun.sleepdownschedule.glass.ui.glassForegroundColor
import com.xiaomanjun.sleepdownschedule.transition.ActivityTransitionCoordinator
import com.xiaomanjun.sleepdownschedule.transition.StaticTransitionAnchorProvider
import com.xiaomanjun.sleepdownschedule.transition.TransitionAnchorFrame
import com.xiaomanjun.sleepdownschedule.transition.TransitionLaunchResult
import com.xiaomanjun.sleepdownschedule.transition.TransitionPayload
import com.xiaomanjun.sleepdownschedule.transition.TransitionRouteId
import com.xiaomanjun.sleepdownschedule.transition.attachOpeningSourceSnapshotHandoff
import com.xiaomanjun.sleepdownschedule.transition.legacy.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.squircle.squircleSurface
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
    onImportRequested: ((ImportDraft, Boolean) -> Unit)? = null
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
    val eduInteraction by EduAiInteractions.current.collectAsStateWithLifecycle()
    var saveAdapterError by remember(saveProposal?.id) { mutableStateOf<String?>(null) }
    var saveAdapterBusy by remember(saveProposal?.id) { mutableStateOf(false) }
    var saveAdapterName by remember(saveProposal?.id) { mutableStateOf(saveProposal?.name.orEmpty()) }
    var saveAdapterUrl by remember(saveProposal?.id) { mutableStateOf(saveProposal?.url.orEmpty()) }
    var showScriptSource by remember(saveProposal?.id) { mutableStateOf(false) }
    val scriptForCurrentPreview = verifiedScript?.takeIf {
        ownsLiveTask && it.proposal.taskId == current.taskId && current.finished && !current.awaitingUserInput && current.error == null
    }
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
            ?: AiEduImportProgressSession.requestFinalImport(draft, createNewSchedule)
        onImportSubmitted()
    }
    val listState = rememberLazyListState()
    var followingLatest by rememberAiImportFollowingLatest(listState, current.taskId)
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
    var conversationInput by remember { mutableStateOf("") }
    val queuedInputs by remember(current.taskId) { AiImportTaskManager.observeInputs(current.taskId) }.collectAsStateWithLifecycle()
    var editingInputId by remember(current.taskId) { mutableStateOf<String?>(null) }
    fun submitPending(mode: com.xiaomanjun.sleepdownschedule.core.ai.AgentMailbox.Mode) {
        val prompt = conversationInput.trim()
        if (prompt.isBlank()) return
        val input = com.xiaomanjun.sleepdownschedule.feature.agent.AgentTaskInput(prompt)
        val accepted = editingInputId?.let { AiImportTaskManager.replaceInput(current.taskId, it, input) }
            ?: AiImportTaskManager.submitInput(current.taskId, input, mode)
        if (accepted) { conversationInput = ""; editingInputId = null }
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
    // Cache the top-bar frame while the history button is idle; onClick captures would include
    // the native press/deformation state and produce a warped source for the system animation.
    var cleanHistoryFrame by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(historyButtonBounds, historySourceHidden, textColor, conversationPageColor) {
        cleanHistoryFrame = null
        if (!historySourceHidden &&
            historyButtonBounds.width > 1f && historyButtonBounds.height > 1f
        ) {
            historySnapshotRequested.set(true)
            historySnapshotRequestVersion += 1
            withFrameNanos { }
            withFrameNanos { }
            cleanHistoryFrame = runCatching {
                historySnapshotLayer.toImageBitmap().asAndroidBitmap()
            }.getOrNull()
        }
    }
    val density = LocalDensity.current
    val inputInsets = rememberFloatingInputInsets()
    val conversationScope = rememberCoroutineScope()
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
            topBarActions = { topBarBackdrop ->
                if (showHistoryAction) {
                    if (historySourceHidden) {
                        Spacer(Modifier.size(42.dp))
                    } else {
                        AiEduHistoryButton(
                            config = config,
                            backdrop = topBarBackdrop,
                            onBoundsChanged = { historyButtonBounds = it },
                             onClick = { sourceBounds ->
                                 conversationScope.launch {
                                     // Use the idle frame cached before press-down; capturing here
                                     // would bake the native button's press/deformation into the
                                     // system source bitmap.
                                     val sourceFrame = cleanHistoryFrame ?: run {
                                         historySnapshotRequested.set(true)
                                         historySnapshotRequestVersion += 1
                                         withFrameNanos { }
                                         withFrameNanos { }
                                         runCatching {
                                             historySnapshotLayer.toImageBitmap().asAndroidBitmap()
                                         }.getOrNull()
                                     }
                                     val sourceSnapshot = sourceFrame?.cropToWindowBounds(
                                         sourceBounds,
                                         rootPositionInWindow
                                     ) ?: return@launch
                                     val activity = context as? ComponentActivity
                                         ?: return@launch
                                     val anchor = TransitionAnchorFrame(
                                         boundsInWindow = sourceBounds,
                                         cornerRadiusPx = with(density) { 21.dp.toPx() },
                                         bitmap = sourceSnapshot
                                     )
                                     val releaseOpeningSource =
                                         activity.attachOpeningSourceSnapshotHandoff(anchor)
                                             ?: return@launch
                                     historySourceHidden = true
                                     historySnapshotRequested.set(true)
                                     historySnapshotRequestVersion += 1
                                     withFrameNanos { }
                                     withFrameNanos { }
                                     val backgroundSnapshot = runCatching {
                                         historySnapshotLayer.toImageBitmap().asAndroidBitmap()
                                     }.getOrNull() ?: run {
                                         releaseOpeningSource()
                                         historySourceHidden = false
                                         return@launch
                                     }
                                     val intent = Intent(context, AiImportHistoryActivity::class.java)
                                     val launchResult = ActivityTransitionCoordinator.open(
                                         activity = activity,
                                         routeId = TransitionRouteId.AiProgressToHistory,
                                         intent = intent,
                                         payload = TransitionPayload(
                                             openingAnchor = anchor,
                                             returnAnchorProvider =
                                                 StaticTransitionAnchorProvider(anchor),
                                             backgroundBitmap = backgroundSnapshot,
                                             backgroundColor = conversationPageColor.toArgb(),
                                             onOpeningSourceHandoff = releaseOpeningSource,
                                             onSourceReleased = {
                                                 historySourceHidden = false
                                             }
                                         )
                                     )
                                     if (launchResult is TransitionLaunchResult.Failed) {
                                         releaseOpeningSource()
                                         historySourceHidden = false
                                     }
                                }
                            }
                        )
                    }
                }
            }
        ) { backdrop ->
            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .glassBackdropProducer(conversationContentBackdrop)
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.align(Alignment.TopCenter).widthIn(max = 840.dp).fillMaxSize(),
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
                        Text(
                            message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium
                        )
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
                            scriptSaveLabel = scriptForCurrentPreview?.let { if (it.saved) "已保存到我的适配" else "保存当前脚本" },
                            onSaveScript = scriptForCurrentPreview?.takeUnless { it.saved }?.let { script ->
                                { EduAdapterSaveProposals.offer(script.proposal) }
                            }
                        )
                    }
                }
                    }
                }

                if (!followingLatest) {
                    DialogLiquidButton(
                        backdrop = backdrop, label = "回到最新",
                        modifier = Modifier.align(Alignment.BottomCenter)
                            .padding(bottom = with(density) { composerHeightPx.toDp() } + 12.dp).size(44.dp).softBlurEnter(),
                        height = 44.dp, horizontalPadding = 0.dp, monochromeNeutral = true,
                        onClick = {
                            followingLatest = true
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
                com.xiaomanjun.sleepdownschedule.feature.agent.AgentInputCapsules(
                    inputs = queuedInputs,
                    attachment = if (!current.requestSent && (current.pageText.isNotBlank() || current.screenshotPreviews.isNotEmpty())) "已附加课表资料" else null,
                    config = config, backdrop = conversationContentBackdrop, foreground = textColor,
                    onEdit = { editingInputId = it.id; conversationInput = it.value.text },
                    onRemove = { AiImportTaskManager.removeInput(current.taskId, it); if (editingInputId == it) editingInputId = null },
                    onGuide = { next ->
                        if (!current.finished) AiImportTaskManager.steerInput(current.taskId, next.id)
                        else {
                            val resumed = AiImportTaskManager.continueImport(context, current.taskId, next.value.text)
                            if (resumed != null) {
                                AiImportTaskManager.removeInput(current.taskId, next.id)
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
                    modifier = Modifier,
                    onValueChange = { conversationInput = it.take(6_000) },
                    onSend = send@{
                        val prompt = conversationInput.trim().ifBlank {
                            if (current.awaitingConfirmation) current.userPrompt else return@send
                        }
                        if (editingInputId != null) {
                            submitPending(com.xiaomanjun.sleepdownschedule.core.ai.AgentMailbox.Mode.QUEUE)
                            return@send
                        }
                        if (current.awaitingConfirmation) {
                            conversationInput = ""
                            updateProgress(current.copy(userPrompt = prompt))
                            AiEduImportProgressSession.confirm()
                        } else if (ownsLiveTask && !current.finished) {
                            submitPending(com.xiaomanjun.sleepdownschedule.core.ai.AgentMailbox.Mode.QUEUE)
                        } else if (ownsLiveTask && AiImportTaskManager.canInterrupt(current.taskId)) {
                            ownedRevisionTaskId = AiImportTaskManager.continueImport(context, current.taskId, prompt)
                            if (ownedRevisionTaskId != null) conversationInput = ""
                        } else {
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
        eduInteraction?.takeIf { ownsLiveTask && it.taskId == current.taskId }?.let { pending ->
            EduBridgeInteractionDialog(pending.request, pending.bridge, config, conversationContentBackdrop,
                onFinished = { EduAiInteractions.clear(pending.bridge) })
        }
        saveProposal?.takeIf { scriptForCurrentPreview?.proposal?.id == it.id && latestDraft != null }?.let { proposal ->
            com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LiquidAlertDialog(
                title = "保存为我的适配？",
                message = "此脚本已在当前网页运行成功并通过格式校验。确认课程、学校名称与登录入口后，可按拾光格式保存在本机，下次直接导入。" +
                    saveAdapterError?.let { "\n$it" }.orEmpty(),
                config = config, backdrop = conversationContentBackdrop,
                onDismissRequest = { if (!saveAdapterBusy) EduAdapterSaveProposals.clear(proposal.id) },
                messageContent = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        DialogCapsuleField(saveAdapterName, { saveAdapterName = it.take(80) }, "学校或入口名称", config, enabled = !saveAdapterBusy)
                        DialogCapsuleField(saveAdapterUrl, { saveAdapterUrl = it.take(4096) }, "教务登录地址", config,
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Uri, enabled = !saveAdapterBusy)
                        Text(if (showScriptSource) "收起脚本" else "查看脚本", style = AiImportTextStyles.Action,
                            color = textColor, modifier = Modifier.clip(Capsule()).clickable { showScriptSource = !showScriptSource }.padding(10.dp))
                        if (showScriptSource) SelectionContainer {
                            Text(proposal.source, color = textColor, style = AiImportTextStyles.Operation,
                                modifier = Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState()))
                        }
                    }
                },
                scrollableMessageContent = true,
                actions = listOf(
                    LiquidAlertAction("暂不保存", LiquidAlertActionStyle.Cancel, enabled = !saveAdapterBusy, onClick = { EduAdapterSaveProposals.clear(proposal.id) }),
                    LiquidAlertAction(if (saveAdapterBusy) "保存中…" else "保存", LiquidAlertActionStyle.Primary,
                        enabled = !saveAdapterBusy, dismissOnClick = false, onClick = saveScript@{
                        if (saveAdapterBusy) return@saveScript
                        saveAdapterBusy = true
                        val name = saveAdapterName
                        val loginUrl = saveAdapterUrl
                        conversationScope.launch {
                            runCatching { withContext(Dispatchers.IO) { LocalEduAdapterStore.save(context, name, loginUrl, proposal.source) } }
                                .onSuccess { EduVerifiedScripts.markSaved(proposal.id); EduAdapterSaveProposals.clear(proposal.id) }
                                .onFailure { saveAdapterError = it.message ?: "保存失败，请重试" }
                            saveAdapterBusy = false
                        }
                    }))
            )
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

private fun Bitmap.cropToWindowBounds(
    bounds: androidx.compose.ui.geometry.Rect,
    rootPosition: Offset
): Bitmap? = runCatching {
    val left = (bounds.left - rootPosition.x).roundToInt().coerceIn(0, width - 1)
    val top = (bounds.top - rootPosition.y).roundToInt().coerceIn(0, height - 1)
    val cropWidth = bounds.width.roundToInt().coerceIn(1, width - left)
    val cropHeight = bounds.height.roundToInt().coerceIn(1, height - top)
    Bitmap.createBitmap(this, left, top, cropWidth, cropHeight)
}.getOrNull()

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
    onClick: (Rect) -> Unit,
    modifier: Modifier = Modifier,
    onBoundsChanged: (Rect) -> Unit = {}
) {
    var bounds by remember { mutableStateOf(Rect.Zero) }
    TopGlassIconButton(
        backdrop = backdrop,
        config = config,
        iconRes = R.drawable.ic_history,
        contentDescription = "历史记录",
        onClick = { if (bounds.width > 0f) onClick(bounds) },
        modifier = modifier
            .size(42.dp)
            .onGloballyPositioned {
                val next = it.boundsInWindow()
                if (next != bounds) {
                    bounds = next
                    onBoundsChanged(next)
                }
            },
        darkOverride = appUsesDarkTheme(config),
        foregroundOverride = LocalAiImportTextColors.current.primary
    )
}

@Composable
internal fun AiEduHistorySwipeRow(
    entry: AiImportHistoryEntry,
    textColor: Color = Color.White,
    showSource: Boolean = true,
    onDelete: () -> Unit,
    onOpen: (Rect) -> Unit,
    onBoundsChanged: (Rect) -> Unit = {}
) {
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val offset = remember(entry.id) { Animatable(0f) }
    val removal = remember(entry.id) { Animatable(0f) }
    var revealThresholdCrossed by remember(entry.id) { mutableStateOf(false) }
    var deleteThresholdCrossed by remember(entry.id) { mutableStateOf(false) }
    var deletionStarted by remember(entry.id) { mutableStateOf(false) }
    var widthPx by remember { mutableStateOf(1f) }
    var cardBounds by remember(entry.id) { mutableStateOf(Rect.Zero) }
    val actionWidthPx = with(density) { 64.dp.toPx() }
    val actionGapPx = with(density) { 14.dp.toPx() }
    val revealPx = actionWidthPx + actionGapPx
    val deleteTriggerPx = maxOf(
        revealPx + with(density) { 156.dp.toPx() },
        widthPx * 0.78f
    ).coerceAtMost(widthPx * 0.88f)
    val maximumDragPx = (widthPx - with(density) { 16.dp.toPx() }).coerceAtLeast(revealPx)
    val settleSpring = spring<Float>(dampingRatio = 0.52f, stiffness = 420f)
    fun deleteWithMorph(performHaptic: Boolean = true) {
        if (deletionStarted) return
        deletionStarted = true
        scope.launch {
            if (performHaptic) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            if (abs(offset.value) < revealPx * 0.9f) {
                offset.animateTo(-revealPx, settleSpring)
            }
            if (abs(offset.value) < deleteTriggerPx) {
                offset.animateTo(-deleteTriggerPx, tween(180, easing = DetailExitEasing))
            }
            kotlinx.coroutines.coroutineScope {
                launch { offset.animateTo(-widthPx * 1.08f, tween(300, easing = DetailExitEasing)) }
                launch { removal.animateTo(1f, tween(360, easing = DetailExitEasing)) }
            }
            onDelete()
        }
    }
    val collapseProgress = ((removal.value - 0.82f) / 0.18f).coerceIn(0f, 1f)
    val dragDistance = (-offset.value).coerceAtLeast(0f)
    val gestureStretch = (
        (dragDistance - revealPx) / (deleteTriggerPx - revealPx).coerceAtLeast(1f)
        ).coerceIn(0f, 1f)
    val removalStretch = (removal.value / 0.42f).coerceIn(0f, 1f)
    val actionExpansion = maxOf(gestureStretch, removalStretch)
    val renderedActionWidthPx = actionWidthPx +
        (widthPx - with(density) { 32.dp.toPx() } - actionWidthPx)
        .coerceAtLeast(0f) * actionExpansion
    val overlapSafeActionWidthPx = minOf(
        renderedActionWidthPx,
        (dragDistance - actionGapPx).coerceAtLeast(actionWidthPx)
    )
    val revealProgress = (dragDistance / revealPx).coerceIn(0f, 1f)
    Box(
        Modifier
            .fillMaxWidth()
            .height((78 * (1f - collapseProgress)).coerceAtLeast(1f).dp)
            .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
    ) {
        val deleteModifier = Modifier
             .align(Alignment.CenterEnd)
             .padding(end = 16.dp)
             .fillMaxHeight()
             .width(with(density) { overlapSafeActionWidthPx.toDp() })
             .graphicsLayer {
                  val elasticStretch = (removal.value / 0.28f).coerceIn(0f, 1f)
                  val verticalSquash = ((removal.value - 0.16f) / 0.46f).coerceIn(0f, 1f)
                  val vanish = ((removal.value - 0.66f) / 0.30f).coerceIn(0f, 1f)
                  alpha = maxOf(revealProgress, removalStretch) * (1f - vanish)
                  scaleX = (1f + 0.09f * elasticStretch) * (1f - 0.27f * vanish)
                  scaleY = (1f - 0.26f * verticalSquash) * (1f - 0.20f * vanish)
                  transformOrigin = TransformOrigin(1f, 0.5f)
                  compositingStrategy = CompositingStrategy.Offscreen
                  renderEffect = platformBlurRenderEffect(10.dp.toPx() * vanish)
              }
         Box(
             deleteModifier
                 .squircleSurface(color = Color(0xFFFF3B30), cornerRadius = 15.dp)
                 .clickable(onClick = ::deleteWithMorph),
             contentAlignment = Alignment.Center
         ) {
             val iconVanish = ((removal.value - 0.72f) / 0.24f).coerceIn(0f, 1f)
             Image(
                 painter = painterResource(R.drawable.ic_delete_history),
                 contentDescription = "删除",
                 modifier = Modifier
                     .size(26.dp)
                     .graphicsLayer {
                         alpha = 1f - iconVanish
                         scaleX = 1f - 0.22f * iconVanish
                         scaleY = 1f - 0.22f * iconVanish
                     }
             )
         }
        Box(
            Modifier
                .fillMaxSize()
                .offset { IntOffset(offset.value.roundToInt(), 0) }
                .pointerInput(entry.id, widthPx) {
                    detectHorizontalDragGestures(
                        onHorizontalDrag = drag@{ change, dragAmount ->
                            change.consume()
                            if (deletionStarted) return@drag
                            val next = (offset.value + dragAmount).coerceIn(
                                -maximumDragPx,
                                with(density) { 8.dp.toPx() }
                            )
                            val revealCrossed = abs(next) >= revealPx * 0.48f
                            if (revealCrossed != revealThresholdCrossed) {
                                revealThresholdCrossed = revealCrossed
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                            scope.launch { offset.snapTo(next) }
                            val deleteCrossed = abs(next) >= deleteTriggerPx
                            if (deleteCrossed && !deleteThresholdCrossed) {
                                deleteThresholdCrossed = true
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                deleteWithMorph(performHaptic = false)
                            }
                        },
                        onDragEnd = dragEnd@{
                            if (deletionStarted) return@dragEnd
                            scope.launch {
                                 val target = if (abs(offset.value) >= revealPx * 0.48f) -revealPx else 0f
                                 revealThresholdCrossed = target < 0f
                                 deleteThresholdCrossed = false
                                 offset.animateTo(target, settleSpring)
                             }
                         },
                         onDragCancel = dragCancel@{
                             if (deletionStarted) return@dragCancel
                             revealThresholdCrossed = false
                             deleteThresholdCrossed = false
                             scope.launch { offset.animateTo(0f, settleSpring) }
                         }
                    )
                }
        ) {
            if (showSource) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                        .clip(RoundedRectangle(18.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.42f))
                        .onGloballyPositioned {
                            val bounds = it.boundsInWindow()
                            if (bounds != cardBounds) {
                                cardBounds = bounds
                                onBoundsChanged(bounds)
                            }
                        }
                ) {
                    AiImportHistoryRowContent(
                         entry = entry,
                         textColor = textColor,
                         modifier = Modifier.fillMaxSize().clickable(
                             interactionSource = remember { MutableInteractionSource() },
                             indication = null
                         ) {
                             if (abs(offset.value) > with(density) { 2.dp.toPx() }) {
                                 scope.launch { offset.animateTo(0f, settleSpring) }
                             } else if (cardBounds.width > 0f) {
                                 onOpen(cardBounds)
                             }
                         }
                    )
                }
            } else {
                Spacer(
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                )
            }
        }
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
                LazyColumn(
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
    modifier: Modifier = Modifier,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit
) {
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
                        onClick = { if (sending && canInterrupt && value.isBlank()) onStop() else onSend() },
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
                            Text(if (sending && canInterrupt && value.isBlank()) "■" else "↑", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).background(controlAccentColor(Color(0xFF0A84FF))).clickable {
                            if (sending && canInterrupt && value.isBlank()) onStop() else onSend()
                        },
                        contentAlignment = Alignment.Center
                    ) { Text(if (sending && canInterrupt && value.isBlank()) "■" else "↑", color = Color.White, fontWeight = FontWeight.Bold) }
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
