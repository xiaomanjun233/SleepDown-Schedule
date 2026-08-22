package com.xiaomanjun.sleepdownschedule

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.util.Base64
import android.view.RoundedCorner
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.catalog.components.LiquidButton
import com.kyant.backdrop.catalog.components.LiquidPanel
import com.xiaomanjun.sleepdownschedule.transition.ActivityTransitionCoordinator
import com.xiaomanjun.sleepdownschedule.transition.StaticTransitionAnchorProvider
import com.xiaomanjun.sleepdownschedule.transition.TransitionAnchorFrame
import com.xiaomanjun.sleepdownschedule.transition.TransitionLaunchResult
import com.xiaomanjun.sleepdownschedule.transition.TransitionPayload
import com.xiaomanjun.sleepdownschedule.transition.TransitionRouteId
import com.xiaomanjun.sleepdownschedule.transition.attachOpeningSourceSnapshotHandoff
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt
import kotlin.math.abs
import top.yukonga.miuix.kmp.basic.BasicComponent as MiuixBasicComponent
import top.yukonga.miuix.kmp.squircle.squircleSurface

class AiEduImportProgressActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        @Suppress("DEPRECATION")
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
        val app = application as CourseScheduleApp
        setContent {
            val state by app.repository.state.collectAsStateWithLifecycle(AppState())
            CourseScheduleTheme(config = state.config) {
                AiEduImportProgressPage(
                    config = state.config,
                    onImportSubmitted = { returnToScheduleHome() },
                    onScreenModeRequested = {
                        finish()
                        AiEduImportProgressSession.useScreenMode()
                    },
                    onClose = {
                        if (AiEduImportProgressSession.progress.value?.awaitingConfirmation == true) {
                            AiEduImportProgressSession.cancel()
                        }
                        finish()
                    }
                )
            }
        }
    }
}

internal fun aiComposerBottomInsetPx(
    imeBottomPx: Int,
    navigationBottomPx: Int,
    baselineRootHeightPx: Int,
    currentRootHeightPx: Int,
    baselineRootTopOnScreenPx: Int,
    currentRootTopOnScreenPx: Int,
    tolerancePx: Int
): Int {
    if (imeBottomPx <= navigationBottomPx) return navigationBottomPx
    if (baselineRootHeightPx <= 0 || currentRootHeightPx <= 0) return imeBottomPx

    // OEMs can honor ADJUST_NOTHING in three different ways: leave the root untouched,
    // resize it, or pan the whole window. Only compensate the portion that has not already
    // been applied by the window manager, otherwise the composer moves by two IME heights.
    val resizedByPx = (baselineRootHeightPx - currentRootHeightPx).coerceAtLeast(0)
    val pannedByPx = (baselineRootTopOnScreenPx - currentRootTopOnScreenPx).coerceAtLeast(0)
    val alreadyAppliedPx = maxOf(resizedByPx, pannedByPx)
    val remainingPx = (imeBottomPx - alreadyAppliedPx).coerceAtLeast(0)
    return if (remainingPx <= tolerancePx) 0 else remainingPx
}

@Composable
internal fun AiEduImportProgressPage(
    config: ScheduleConfigEntity,
    onClose: () -> Unit,
    onImportSubmitted: () -> Unit = onClose,
    onScreenModeRequested: () -> Unit = AiEduImportProgressSession::useScreenMode,
    historicalProgress: AiEduImportProgress? = null,
    historicalDraft: ImportDraft? = null,
    historicalEntryId: String? = null,
    showHistoryAction: Boolean = true,
    onImportRequested: ((ImportDraft, Boolean) -> Unit)? = null
) {
    val sessionProgress by AiEduImportProgressSession.progress.collectAsStateWithLifecycle()
    val sessionPreviewDraft by AiEduImportProgressSession.previewDraft.collectAsStateWithLifecycle()
    val historicalMode = historicalProgress != null && historicalDraft != null
    var localProgress by remember(historicalProgress) { mutableStateOf(historicalProgress) }
    var localPreviewDraft by remember(historicalDraft) { mutableStateOf(historicalDraft) }
    val current = if (historicalMode) {
        checkNotNull(localProgress ?: historicalProgress)
    } else {
        sessionProgress ?: AiEduImportProgress(steps = listOf("等待 AI 教务导入任务"))
    }
    val previewDraft = if (historicalMode) localPreviewDraft else sessionPreviewDraft
    fun updateProgress(next: AiEduImportProgress) {
        if (historicalMode) localProgress = next else AiEduImportProgressSession.update(next)
    }
    fun updatePreviewDraft(next: ImportDraft) {
        if (historicalMode) localPreviewDraft = next else AiEduImportProgressSession.setPreviewDraft(next)
    }
    fun requestImport(draft: ImportDraft, createNewSchedule: Boolean) {
        onImportRequested?.invoke(draft, createNewSchedule)
            ?: AiEduImportProgressSession.requestFinalImport(draft, createNewSchedule)
        onImportSubmitted()
    }
    val listState = rememberLazyListState()
    val textColor = glassForegroundColor(settingsVisualConfig(config))
    val pageTitle = current.routeLabel.takeIf { it.isNotBlank() } ?: "AI 教务导入"
    var executionExpanded by remember { mutableStateOf(!current.finished) }
    var previewAttachment by remember { mutableStateOf<AiEduAttachmentPreviewRequest?>(null) }
    var previewSourceHidden by remember { mutableStateOf(false) }
    var rootSize by remember { mutableStateOf(IntSize.Zero) }
    var rootPositionOnScreen by remember { mutableStateOf(Offset.Zero) }
    var rootPositionInWindow by remember { mutableStateOf(Offset.Zero) }
    var baselineRootHeightPx by remember { mutableIntStateOf(0) }
    var baselineRootTopOnScreenPx by remember { mutableIntStateOf(0) }
    val previewBackgroundZoom = remember { Animatable(1f) }
    val previewSceneBackdrop = rememberLayerBackdrop { drawContent() }
    val conversationPageColor = settingsPageBackground(settingsVisualConfig(config))
    val conversationContentBackdrop = rememberLayerBackdrop {
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
    var conversationSending by remember { mutableStateOf(false) }
    var composerHeightPx by remember { mutableStateOf(0) }
    val runtimePickerState = rememberAiRuntimePickerState()
    var historySourceHidden by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val density = LocalDensity.current
    val imeBottomPx = WindowInsets.ime.getBottom(density)
    val navigationBottomPx = WindowInsets.navigationBars.getBottom(density)
    val insetTolerancePx = with(density) { 24.dp.roundToPx() }
    LaunchedEffect(
        imeBottomPx,
        navigationBottomPx,
        rootSize.height,
        rootPositionOnScreen.y
    ) {
        if (imeBottomPx <= navigationBottomPx && rootSize.height > 0) {
            baselineRootHeightPx = rootSize.height
            baselineRootTopOnScreenPx = rootPositionOnScreen.y.roundToInt()
        }
    }
    val composerBottomInsetPx = aiComposerBottomInsetPx(
        imeBottomPx = imeBottomPx,
        navigationBottomPx = navigationBottomPx,
        baselineRootHeightPx = baselineRootHeightPx,
        currentRootHeightPx = rootSize.height,
        baselineRootTopOnScreenPx = baselineRootTopOnScreenPx,
        currentRootTopOnScreenPx = rootPositionOnScreen.y.roundToInt(),
        tolerancePx = insetTolerancePx
    )
    val conversationScope = rememberCoroutineScope()
    LaunchedEffect(current.finished) {
        if (current.finished) executionExpanded = false
    }
    BackHandler(enabled = current.awaitingConfirmation) {
        AiEduImportProgressSession.cancel()
        onClose()
    }

    Box(
        Modifier
            .fillMaxSize()
            .clipToBounds()
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
                    val depthProgress = (
                        (zoom - 1f) / (HomeAnchoredMorphBackgroundScale - 1f)
                        ).coerceIn(0f, 1f)
                    val blurPx = 12.dp.toPx() * depthProgress
                    renderEffect = if (blurPx > 0.01f) {
                        BlurEffect(blurPx, blurPx, TileMode.Clamp)
                    } else {
                        null
                    }
                }
        ) {
            Box(Modifier.fillMaxSize().layerBackdrop(previewSceneBackdrop)) {
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
                             onClick = { sourceBounds ->
                                 conversationScope.launch {
                                     // TODO(OPLUS_DEFERRED_20260823): Exact source-overlay handoff
                                     // remains unverified; PLJ110 still showed an AI-history OPEN
                                     // blank frame in the signed acceptance build.
                                     // Capture the real button before hiding it, then record a
                                     // second frame for the clean page background used by Morph.
                                     historySnapshotRequested.set(true)
                                     historySnapshotRequestVersion += 1
                                     withFrameNanos { }
                                     val sourceSnapshot = runCatching {
                                         historySnapshotLayer.toImageBitmap().asAndroidBitmap()
                                     }.getOrNull()?.cropToWindowBounds(
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
                                             onOpeningSourceHandoff = releaseOpeningSource,
                                             nativeSourceLeashAlphaOutOnOpen = true,
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
                        .layerBackdrop(conversationContentBackdrop)
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = detailContentTopPadding() + 10.dp,
                            bottom = with(LocalDensity.current) { composerHeightPx.toDp() } + 18.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                item {
                    AiEduUserMessage(
                        progress = current,
                        textColor = textColor,
                        config = config,
                        backdrop = backdrop,
                        showAttachment = !previewSourceHidden,
                        onPreview = { previewAttachment = it }
                    )
                }
                current.conversationTurns.forEachIndexed { index, turn ->
                    item(key = "conversation-turn-${index}-${turn.userPrompt.hashCode()}") {
                        AiEduConversationTurnSummary(
                            turn = turn,
                            index = index + 1,
                            textColor = textColor
                        )
                    }
                }
                val summary = current.liveSummary.ifBlank { current.reasoningOutput }
                if (summary.isNotBlank()) item {
                    AiEduModelSummary(summary = summary, textColor = textColor)
                }
                if (current.steps.isNotEmpty()) item {
                    AgentRunTrace(
                        statuses = aiEduAgentRunStatuses(current),
                        expanded = executionExpanded,
                        foreground = textColor,
                        active = !current.finished && current.error == null,
                        onToggle = { executionExpanded = !executionExpanded }
                    )
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
                previewDraft?.let { draft ->
                    item {
                        AiEduInlineImportPreview(
                            draft = draft,
                            textColor = textColor,
                            config = config,
                            backdrop = backdrop,
                            onCreateNew = {
                                requestImport(draft, true)
                            },
                            onOverwrite = {
                                requestImport(draft, false)
                            }
                        )
                    }
                }
                    }
                }
                AiEduConversationComposer(
                    value = conversationInput,
                    sending = conversationSending,
                    config = config,
                    backdrop = conversationContentBackdrop,
                    runtimePickerState = runtimePickerState,
                    textColor = textColor,
                    attachmentVisible = !current.requestSent &&
                        (current.pageText.isNotBlank() || current.screenshotPreviews.isNotEmpty()),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = with(density) { composerBottomInsetPx.toDp() })
                        .onSizeChanged { composerHeightPx = it.height },
                    onValueChange = { conversationInput = it.take(600) },
                    onSend = send@{
                        val prompt = conversationInput.trim().ifBlank {
                            if (current.awaitingConfirmation) current.userPrompt else return@send
                        }
                        if (conversationSending) return@send
                        conversationInput = ""
                        if (current.awaitingConfirmation) {
                            updateProgress(current.copy(userPrompt = prompt))
                            AiEduImportProgressSession.confirm()
                        } else {
                            val baseDraft = previewDraft ?: return@send
                            conversationSending = true
                            conversationScope.launch {
                                val settings = AiImportSettingsStore.loadForRuntime(context)
                                    ?: AiImportSettingsStore.load(context)
                                var workingProgress = current.copy(
                                    steps = current.steps + listOf(
                                        "正在理解你的新要求",
                                        "模型正在分析课程、周次和节次"
                                    ),
                                    userPrompt = prompt,
                                    liveSummary = "我正在理解你的修改要求，并核对现有课程、周次和节次信息。",
                                    finished = false,
                                    error = null
                                )
                                updateProgress(workingProgress)
                                val progressTicker = launch {
                                    listOf(
                                        "模型正在核对现有课表结构",
                                        "模型正在生成修改方案",
                                        "仍在等待模型完成，请保留此页面"
                                    ).forEach { summary ->
                                        delay(2_400)
                                        if (!workingProgress.finished) {
                                            workingProgress = workingProgress.copy(
                                                steps = workingProgress.steps + summary,
                                                liveSummary = summary
                                            )
                                            updateProgress(workingProgress)
                                        }
                                    }
                                }
                                AiScheduleImportService(context)
                                    .reviseSchedule(baseDraft, prompt, current, settings)
                                    .mapCatching { result ->
                                        val revised = ScheduleImportParser.parse(
                                            result.output.ifBlank { result.rawOutput },
                                            baseDraft.config
                                        ).getOrThrow().copy(source = ImportDraftSource.AI_EDU)
                                        revised to result
                                    }
                                    .onSuccess { (revised, result) ->
                                        progressTicker.cancel()
                                        val previousTurns = current.conversationTurns.ifEmpty {
                                            listOf(
                                                AiEduImportConversationTurn(
                                                    userPrompt = current.userPrompt,
                                                    reasoningOutput = current.reasoningOutput,
                                                    aiOutput = current.aiOutput
                                                )
                                            )
                                        }
                                        val nextProgress = workingProgress.copy(
                                            steps = workingProgress.steps + listOf("模型已给出修改摘要", "修改结果通过本地校验"),
                                            userPrompt = prompt,
                                            requestSent = true,
                                            reasoningOutput = result.reasoningOutput,
                                            aiOutput = result.rawOutput,
                                            liveSummary = result.reasoningOutput.ifBlank {
                                                "本轮已按你的要求更新课表，并通过本地校验。"
                                            },
                                            finished = true,
                                            error = null,
                                            conversationTurns = previousTurns + AiEduImportConversationTurn(
                                                userPrompt = prompt,
                                                reasoningOutput = result.reasoningOutput,
                                                aiOutput = result.rawOutput
                                            )
                                        )
                                        updatePreviewDraft(revised)
                                        updateProgress(nextProgress)
                                        if (historicalEntryId != null) {
                                            AiImportHistoryStore.update(context, historicalEntryId, revised, nextProgress)
                                        } else {
                                            AiImportHistoryStore.updateMatching(context, baseDraft, revised, nextProgress)
                                        }
                                    }
                                    .onFailure { error ->
                                        progressTicker.cancel()
                                        updateProgress(
                                            workingProgress.copy(
                                                steps = workingProgress.steps + "本次修改未完成",
                                                finished = true,
                                                error = error.message ?: "AI 没有完成这次修改"
                                            )
                                        )
                                    }
                                conversationSending = false
                            }
                        }
                    }
                )
            }
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
private fun AiEduConversationTurnSummary(
    turn: AiEduImportConversationTurn,
    index: Int,
    textColor: Color
) {
    val summary = turn.reasoningOutput.ifBlank { "模型已完成这一轮修改。" }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(textColor.copy(alpha = 0.055f))
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text("第 $index 轮修改", color = textColor.copy(alpha = 0.64f), style = MaterialTheme.typography.labelMedium)
        Text(turn.userPrompt, color = textColor, style = MaterialTheme.typography.bodyMedium, maxLines = 3)
        Text(summary, color = textColor.copy(alpha = 0.70f), style = MaterialTheme.typography.bodySmall, maxLines = 5)
    }
}

@Composable
private fun AiEduModelSummary(summary: String, textColor: Color) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text("模型摘要", color = textColor.copy(alpha = 0.58f), style = MaterialTheme.typography.labelMedium)
        Text(summary, color = textColor.copy(alpha = 0.86f), style = MaterialTheme.typography.bodyMedium)
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
            if (hasCapturedText || hasCapturedVisual) "确认发送课表资料" else "未能可靠读取课表文字",
            color = textColor,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            if (hasCapturedVisual) {
                "已准备高清视觉截取，请检查上方附件。截图会按页面顺序发送，重叠区域只用于校对，不会重复导入课程。"
            } else if (!hasCapturedText && hasVisualCaptureAction) {
                "当前课表可能由图片、Canvas 或跨域页面渲染。你可以进入高清识屏，应用会按页面位置截取多张清晰视口图，不会压成模糊长图；确认后才会发送给视觉模型。"
            } else if (hasCapturedText && hasVisualCaptureAction) {
                "已抓取到页面文字；如果页面包含图片、Canvas 或复杂排版，也可以改用高清视觉截取。应用会发送多张清晰视口图，不会压成模糊长图。"
            } else if (hasCapturedText) {
                "已抓取到页面文字。需要识别图片、Canvas 或复杂排版时，可切换到支持视觉输入的模型后使用高清视觉截取。"
            } else {
                "当前模型无法使用高清视觉截取。请切换到支持视觉输入的模型，或返回可复制文字的课表页面。"
            },
            color = textColor.copy(alpha = 0.66f),
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
                    LiquidAlertActionStyle.Secondary,
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
                    LiquidAlertActionStyle.Secondary,
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
    modifier: Modifier = Modifier
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
            .onGloballyPositioned { bounds = it.boundsInWindow() },
        surfaceColorOverride = if (appUsesDarkTheme(config)) {
            Color.White.copy(alpha = 0.10f)
        } else {
            Color.White.copy(alpha = 0.08f)
        }
    )
}

@Composable
internal fun AiEduHistorySwipeRow(
    entry: AiImportHistoryEntry,
    textColor: Color = Color.White,
    showSource: Boolean = true,
    onDelete: () -> Unit,
    onOpen: (Rect) -> Unit
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
                        .clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.42f))
                        .onGloballyPositioned { cardBounds = it.boundsInWindow() }
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
        if (hasAttachment) {
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
                    shape = RoundedCornerShape(20.dp)
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
                .clip(RoundedCornerShape(22.dp, 22.dp, 6.dp, 22.dp))
                .background(Color(0xFF0A84FF))
        ) {
            Text(
                progress.userPrompt,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 8,
                overflow = TextOverflow.Ellipsis
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
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                if (attachment.images.isNotEmpty()) "${attachment.images.size} 页 · 点击预览" else "${attachment.text.length} 字 · 点击预览",
                color = textColor.copy(alpha = 0.58f),
                style = MaterialTheme.typography.bodySmall,
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
            .clip(RoundedCornerShape(12.dp))
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
    val geometry = homeAnchoredMorphGeometry(
        source = request.sourceBounds,
        target = target,
        rawProgress = progress.value,
        closing = closing,
        sourceCornerRadiusPx = with(density) { 20.dp.toPx() },
        pinchDiameterPx = with(density) { 44.dp.toPx() },
        minimumDropPx = with(density) { 10.dp.toPx() },
        maximumDropPx = with(density) { 54.dp.toPx() },
        maximumArcPx = with(density) { 46.dp.toPx() },
        targetCornerRadiusPx = screenCornerRadiusPx
    )
    val fullyOpen = !closing && progress.value >= 0.999f
    val renderedCornerRadiusPx = if (fullyOpen) 0f else geometry.cornerRadiusPx
    val sourceBlurPx = with(density) { 5.dp.toPx() } * homeMorphSmoothStep(
        0f,
        0.34f,
        geometry.pathProgress
    )
    val contentBlurPx = with(density) { 5.dp.toPx() } * (
        1f - homeMorphSmoothStep(0.42f, 0.98f, geometry.expansionProgress)
    )
    fun dismiss() {
        if (!closing) {
            closing = true
            scope.launch {
                coroutineScope {
                    launch {
                        progress.animateTo(
                            0f,
                            tween(HomeAnchoredMorphCloseDurationMillis, easing = LinearEasing)
                        )
                    }
                    launch {
                        backgroundZoom.animateTo(
                            1f,
                            tween(
                                HomeAnchoredMorphBackgroundDurationMillis,
                                easing = HomeAnchoredBackgroundEasing
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
                    tween(HomeAnchoredMorphOpenDurationMillis, easing = LinearEasing)
                )
            }
            launch {
                backgroundZoom.animateTo(
                    HomeAnchoredMorphBackgroundScale,
                    tween(
                        HomeAnchoredMorphBackgroundDurationMillis,
                        delayMillis = HomeAnchoredMorphBackgroundDelayMillis,
                        easing = HomeAnchoredBackgroundEasing
                    )
                )
            }
        }
    }
    BackHandler(onBack = ::dismiss)
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.34f * geometry.expansionProgress))
            .clickable(onClick = ::dismiss)
    )
    Box(
        Modifier
            .offset { IntOffset(geometry.rect.left.roundToInt(), geometry.rect.top.roundToInt()) }
            .size(
                with(density) { geometry.rect.width.toDp() },
                with(density) { geometry.rect.height.toDp() }
            )
             .graphicsLayer {
                 clip = !fullyOpen
                 shape = RoundedCornerShape(with(density) { renderedCornerRadiusPx.toDp() })
                 compositingStrategy = if (fullyOpen) {
                     CompositingStrategy.Auto
                 } else {
                     CompositingStrategy.Offscreen
                 }
             }
            .clickable(enabled = false) {}
    ) {
        AiEduLiquidPanel(
            backdrop = backdrop,
            config = config,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = geometry.surfaceAlpha.coerceAtLeast(0.08f) },
            accent = Color(0xFF8E8E93),
            shape = RoundedCornerShape(with(density) { renderedCornerRadiusPx.toDp() })
        ) { }
        if (geometry.sourceAlpha > 0.01f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = geometry.sourceAlpha
                        scaleX = geometry.sourceScale
                        scaleY = geometry.sourceScale
                        compositingStrategy = CompositingStrategy.Offscreen
                        renderEffect = if (sourceBlurPx > 0.01f) {
                            BlurEffect(sourceBlurPx, sourceBlurPx, TileMode.Clamp)
                        } else null
                    }
            ) {
                AiEduAttachmentCardContent(
                    attachment = request.attachment,
                    textColor = textColor,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        if (geometry.contentAlpha > 0.01f) {
            Column(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = geometry.contentAlpha
                        compositingStrategy = CompositingStrategy.Offscreen
                        renderEffect = if (contentBlurPx > 0.01f) {
                            BlurEffect(contentBlurPx, contentBlurPx, TileMode.Clamp)
                        } else null
                    }
                    .padding(top = 34.dp, start = 16.dp, end = 16.dp, bottom = 18.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(request.attachment.title, modifier = Modifier.weight(1f), color = textColor, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text("完成", modifier = Modifier.clickable(onClick = ::dismiss).padding(12.dp), color = Color(0xFF0A84FF), fontWeight = FontWeight.SemiBold)
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (request.attachment.text.isNotBlank()) item {
                        Text(request.attachment.text, color = textColor.copy(alpha = 0.88f), style = MaterialTheme.typography.bodyMedium, lineHeight = 21.sp)
                    }
                    itemsIndexed(request.attachment.images) { index, image ->
                        AiEduPreviewImage(image, "第 ${index + 1} 页")
                    }
                }
            }
        }
    }
}

@Composable
internal fun deviceScreenCornerRadiusPx(): Float {
    val view = LocalView.current
    val density = LocalDensity.current
    val fallback = with(density) { 32.dp.toPx() }
    return remember(view, density.density) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(
                RoundedCorner.POSITION_TOP_LEFT,
                RoundedCorner.POSITION_TOP_RIGHT,
                RoundedCorner.POSITION_BOTTOM_LEFT,
                RoundedCorner.POSITION_BOTTOM_RIGHT
            ).mapNotNull { position -> view.rootWindowInsets?.getRoundedCorner(position)?.radius }
                .maxOrNull()
                ?.toFloat()
                ?.takeIf { it > 0f }
                ?: fallback
        } else {
            fallback
        }
    }
}

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
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)),
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
    onCreateNew: () -> Unit,
    onOverwrite: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "导入预览",
            color = textColor,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            "${draft.courses.size} 门课程 · ${draft.config.totalWeeks} 周 · ${draft.periods.size} 个节次",
            color = textColor.copy(alpha = 0.58f),
            style = MaterialTheme.typography.bodySmall
        )
        draft.courses.forEach { course ->
            ImportPreviewCourseCard(course, draft.periods, draft.config)
        }
        Text(
            "如果内容不对，直接在下方告诉 AI 怎么修改。确认无误后再选择导入方式。",
            color = textColor.copy(alpha = 0.62f),
            style = MaterialTheme.typography.bodySmall
        )
        LiquidAlertActions(
            actions = listOf(
                LiquidAlertAction("创建新课表", LiquidAlertActionStyle.Primary, onClick = onCreateNew),
                LiquidAlertAction("覆盖当前课表", LiquidAlertActionStyle.Destructive, onClick = onOverwrite)
            ),
            backdrop = backdrop,
            config = config
        )
    }
}

@Composable
private fun AiEduConversationComposer(
    value: String,
    sending: Boolean,
    config: ScheduleConfigEntity,
    backdrop: Backdrop?,
    runtimePickerState: AiRuntimePickerState,
    textColor: Color,
    attachmentVisible: Boolean,
    modifier: Modifier = Modifier,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit
) {
    AiEduLiquidPanel(
        backdrop = backdrop,
        config = config,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        shape = if (attachmentVisible) RoundedCornerShape(26.dp) else RoundedCornerShape(50),
        accent = Color(0xFF8E8E93)
    ) {
        Column(
            Modifier
                .padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 8.dp)
        ) {
            if (attachmentVisible) {
                Text(
                    "已附加当前课表资料 · 发送前不会调用模型",
                    modifier = Modifier.padding(start = 12.dp, end = 8.dp, bottom = 6.dp),
                    color = textColor.copy(alpha = 0.62f),
                    style = MaterialTheme.typography.labelMedium
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = textColor),
                    cursorBrush = SolidColor(Color(0xFF0A84FF)),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { onSend() }),
                    maxLines = 4,
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (value.isBlank()) {
                                AutoFitSingleLineText(
                                    text = if (attachmentVisible) {
                                        "帮我按规则导入…"
                                    } else {
                                        "告诉 AI 哪里需要修改…"
                                    },
                                    color = textColor.copy(alpha = 0.48f),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                            inner()
                        }
                    }
                )
                AiRuntimePicker(
                    state = runtimePickerState,
                    config = config,
                    backdrop = backdrop
                )
                if (backdrop != null) {
                    LiquidButton(
                        onClick = onSend,
                        backdrop = backdrop,
                        modifier = Modifier.size(40.dp),
                        height = 40.dp,
                        surfaceColor = Color(0xFF0A84FF).copy(alpha = 0.88f),
                        tint = Color(0xFF0A84FF),
                        contentPadding = PaddingValues(0.dp),
                        blurRadius = 4.dp,
                        lensHeight = 14.dp,
                        lensAmount = 18.dp,
                        chromaticAberration = false
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(if (sending) "■" else "↑", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).background(Color(0xFF0A84FF)).clickable(onClick = onSend),
                        contentAlignment = Alignment.Center
                    ) { Text(if (sending) "■" else "↑", color = Color.White, fontWeight = FontWeight.Bold) }
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
    shape: Shape = RoundedCornerShape(28.dp),
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
