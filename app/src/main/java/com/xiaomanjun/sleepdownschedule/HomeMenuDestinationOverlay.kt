package com.xiaomanjun.sleepdownschedule

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.catalog.components.LiquidPanel
import com.kyant.shapes.RoundedRectangle
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

internal enum class HomeMenuDestinationKind { AddCourse, ManualImport, EduImport }

internal data class HomeMenuDestinationRequest(
    val kind: HomeMenuDestinationKind,
    val sourceBoundsInRoot: Rect,
    val collapseBoundsInRoot: Rect
)

@Stable
internal class HomeMenuDestinationMotionState {
    val progress = Animatable(0f)
    val backgroundZoom = Animatable(1f)
    var phase by mutableStateOf(HomeAnchoredOverlayPhase.Idle)
        internal set
}

@Composable
internal fun rememberHomeMenuDestinationMotionState(): HomeMenuDestinationMotionState =
    remember { HomeMenuDestinationMotionState() }

private const val DestinationOpenDurationMillis = 330
internal const val HomeMenuDestinationCloseDurationMillis = 350
private const val DestinationBackgroundDurationMillis = 420
internal const val HomeMenuDestinationEduBackgroundScale = 1.08f
private const val SourceMenuUnloadDelayMillis = 52L
private const val EduSourceMenuUnloadDelayMillis = 32L
private val DestinationBackgroundEasing = CubicBezierEasing(0.30f, 0f, 0.20f, 1f)

internal fun homeMenuDestinationTargetRect(
    kind: HomeMenuDestinationKind,
    rootSize: IntSize,
    density: Float,
    adaptiveMetrics: HomeAdaptiveMetrics
): Rect {
    if (rootSize.width <= 0 || rootSize.height <= 0) return Rect.Zero
    if (kind == HomeMenuDestinationKind.EduImport) {
        return Rect(0f, 0f, rootSize.width.toFloat(), rootSize.height.toFloat())
    }

    val safeDensity = density.coerceAtLeast(0.001f)
    val content = adaptiveMetrics.contentRectPx(rootSize, safeDensity)
    val width = if (adaptiveMetrics.isLargeScreen) {
        val minimum = minOf(600f * safeDensity, content.width)
        minOf(content.width, 680f * safeDensity).coerceAtLeast(minimum)
    } else {
        minOf(content.width * 0.96f, 600f * safeDensity)
    }.coerceAtLeast(1f)
    val maximumHeight = when (kind) {
        HomeMenuDestinationKind.AddCourse -> 548f * safeDensity
        HomeMenuDestinationKind.ManualImport -> 500f * safeDensity
        HomeMenuDestinationKind.EduImport -> content.height
    }
    val height = if (adaptiveMetrics.isLargeScreen) {
        minOf(content.height * 0.82f, maximumHeight).coerceAtLeast(
            minOf(content.height, 360f * safeDensity)
        )
    } else {
        val availableRatio = if (kind == HomeMenuDestinationKind.AddCourse) 0.88f else 1f
        minOf(content.height * availableRatio, maximumHeight)
    }.coerceAtLeast(1f)
    val left = content.left + (content.width - width) / 2f
    val top = content.top + (content.height - height) / 2f
    return Rect(left, top, left + width, top + height)
}

@Composable
internal fun HomeMenuDestinationOverlayHost(
    request: HomeMenuDestinationRequest?,
    motionState: HomeMenuDestinationMotionState,
    state: AppState,
    backdrop: Backdrop?,
    adaptiveMetrics: HomeAdaptiveMetrics,
    modifier: Modifier = Modifier,
    onDismissRequest: () -> Unit,
    sourceActions: List<AddMenuAction>,
    onSourceHandoff: () -> Unit,
    onCollapseHandoff: (HomeMenuDestinationKind) -> Unit,
    onClosed: () -> Unit,
    onAddCourses: (List<CourseEntity>) -> Unit,
    onManualImportParsed: (ImportDraft) -> Unit,
    captureHistoryBackground: suspend () -> Bitmap?,
    onEduAdapterSelected: (EduAdapter) -> Unit
) {
    var renderedRequest by remember { mutableStateOf<HomeMenuDestinationRequest?>(null) }
    var destinationContentPrepared by remember { mutableStateOf(false) }
    var rootSize by remember { mutableStateOf(IntSize.Zero) }
    var historyDetailRequest by remember { mutableStateOf<AiImportHistoryDetailMorphRequest?>(null) }
    var hiddenHistoryEntryId by remember { mutableStateOf<String?>(null) }
    var historyImportRequested by remember { mutableStateOf(false) }
    val destinationContentLayer = rememberGraphicsLayer()
    val latestDismiss by rememberUpdatedState(onDismissRequest)
    val latestSourceHandoff by rememberUpdatedState(onSourceHandoff)
    val latestCollapseHandoff by rememberUpdatedState(onCollapseHandoff)
    val latestClosed by rememberUpdatedState(onClosed)

    LaunchedEffect(request) {
        if (request != null) {
            renderedRequest = request
            destinationContentPrepared = false
            motionState.phase = HomeAnchoredOverlayPhase.Preparing
            motionState.progress.snapTo(0f)
            motionState.backgroundZoom.snapTo(1f)
            var frames = 0
            while (frames < 12 && (rootSize.width <= 0 || rootSize.height <= 0)) {
                withFrameNanos { }
                frames++
            }
            // Build and measure the destination while the source menu is still stationary. The
            // editor/import screens are comparatively heavy; creating them during the moving
            // part of the morph is visible as a dropped frame on mid-range devices.
            destinationContentPrepared = true
            withFrameNanos { }
            // Give the newly composed destination one additional draw frame so its text, list and
            // backdrop layers are resident before the source menu starts changing geometry.
            withFrameNanos { }
            motionState.phase = HomeAnchoredOverlayPhase.Opening
            coroutineScope {
                launch {
                    motionState.progress.animateTo(
                        1f,
                        tween(DestinationOpenDurationMillis, easing = LinearEasing)
                    )
                }
                launch {
                    delay(
                        if (request.kind == HomeMenuDestinationKind.EduImport) {
                            EduSourceMenuUnloadDelayMillis
                        } else {
                            SourceMenuUnloadDelayMillis
                        }
                    )
                    latestSourceHandoff()
                }
                launch {
                    motionState.backgroundZoom.animateTo(
                        if (request.kind == HomeMenuDestinationKind.EduImport) { HomeMenuDestinationEduBackgroundScale } else { HomeAnchoredMorphBackgroundScale },
                        tween(
                            DestinationBackgroundDurationMillis,
                            delayMillis = HomeAnchoredMorphBackgroundDelayMillis,
                            easing = DestinationBackgroundEasing
                        )
                    )
                }
            }
            motionState.phase = HomeAnchoredOverlayPhase.Open
        } else if (renderedRequest != null) {
            motionState.phase = HomeAnchoredOverlayPhase.Closing
            coroutineScope {
                launch {
                    motionState.progress.animateTo(
                        0f,
                        tween(HomeMenuDestinationCloseDurationMillis, easing = LinearEasing)
                    )
                }
                launch {
                    motionState.backgroundZoom.animateTo(
                        1f,
                        tween(DestinationBackgroundDurationMillis, easing = DestinationBackgroundEasing)
                    )
                }
            }
            motionState.phase = HomeAnchoredOverlayPhase.Disposing
            destinationContentPrepared = false
            renderedRequest = null
            motionState.phase = HomeAnchoredOverlayPhase.Idle
            latestClosed()
        }
    }

    val shown = request ?: renderedRequest
    BackHandler(enabled = shown != null) { latestDismiss() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { rootSize = it }
    ) {
        shown ?: return@Box
        if (rootSize.width <= 0 || rootSize.height <= 0) return@Box
        val density = androidx.compose.ui.platform.LocalDensity.current
        val isFullScreen = shown.kind == HomeMenuDestinationKind.EduImport
        val target = remember(shown.kind, rootSize, density.density, adaptiveMetrics) {
            homeMenuDestinationTargetRect(
                kind = shown.kind,
                rootSize = rootSize,
                density = density.density,
                adaptiveMetrics = adaptiveMetrics
            )
        }
        val destinationClosing = motionState.phase == HomeAnchoredOverlayPhase.Closing ||
            motionState.phase == HomeAnchoredOverlayPhase.Disposing
        val morphSource = if (destinationClosing) {
            shown.collapseBoundsInRoot
        } else {
            shown.sourceBoundsInRoot
        }
        val geometry = homeAnchoredMorphGeometry(
            source = morphSource,
            target = target,
            rawProgress = motionState.progress.value,
            // Menu destinations morph directly between the open menu and their target. They do
            // not repeat the button-to-droplet pinch used when the menu itself first appears.
            closing = destinationClosing,
            directClosing = !destinationClosing,
            directSourceCornerRadiusPx = with(density) {
                if (destinationClosing) 21.dp.toPx() else 26.dp.toPx()
            },
            pinchDiameterPx = with(density) { 18.dp.toPx() },
            minimumDropPx = with(density) { 12.dp.toPx() },
            maximumDropPx = with(density) { adaptiveMetrics.animationArc.toPx() },
            maximumArcPx = with(density) { adaptiveMetrics.animationArc.toPx() + 16.dp.toPx() },
            targetCornerRadiusPx = with(density) { if (isFullScreen) 0.dp.toPx() else 32.dp.toPx() }
        )
        val collapseHandoffReached = destinationClosing &&
            geometry.pathProgress <= HomeAnchoredMorphClosePinchFraction
        LaunchedEffect(collapseHandoffReached) {
            if (collapseHandoffReached) latestCollapseHandoff(shown.kind)
        }
        val rect = geometry.rect
        val fullScreenOpenEndpoint = isFullScreen &&
            !destinationClosing &&
            motionState.progress.value >= 0.999f
        val renderedCornerRadiusPx = if (isFullScreen) {
            if (fullScreenOpenEndpoint) {
                0f
            } else {
                val progress = geometry.pathProgress
                val sourceCorner = with(density) { 26.dp.toPx() }
                val middleCorner = with(density) { 46.dp.toPx() }
                if (progress <= 0.35f) {
                    sourceCorner + (middleCorner - sourceCorner) * (progress / 0.35f)
                } else {
                    middleCorner
                }
            }
        } else {
            geometry.cornerRadiusPx
        }
        val corner = with(density) { renderedCornerRadiusPx.coerceAtLeast(0f).toDp() }
        val targetWidth = with(density) { target.width.toDp() }
        val targetHeight = with(density) { target.height.toDp() }

        fun smoothStep(edge0: Float, edge1: Float, value: Float): Float {
            val x = ((value - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
            return x * x * (3f - 2f * x)
        }

        val sourceHandoffStart = if (isFullScreen) 0.035f else 0.12f
        val sourceHandoffEnd = if (isFullScreen) 0.20f else 0.40f
        val destinationHandoffStart = if (isFullScreen) 0.055f else 0.14f
        val destinationHandoffEnd = if (isFullScreen) 0.24f else 0.34f
        val sourceCloneAlpha = if (destinationClosing) 0f else {
            1f - smoothStep(sourceHandoffStart, sourceHandoffEnd, motionState.progress.value)
        }
        val destinationSurfaceAlpha = 1f - sourceCloneAlpha
        val maxContentBlurPx = with(density) { 5.dp.toPx() }
        val sourceContentBlurPx = if (destinationClosing) {
            0f
        } else {
            maxContentBlurPx * smoothStep(
                0f,
                sourceHandoffStart,
                motionState.progress.value
            )
        }
        val destinationContentBlurPx = if (destinationClosing) {
            val closeElapsed = 1f - motionState.progress.value
            maxContentBlurPx * smoothStep(
                if (isFullScreen) 0.48f else 0f,
                if (isFullScreen) 0.84f else 0.34f,
                closeElapsed
            )
        } else {
            maxContentBlurPx * (
                1f - smoothStep(
                    destinationHandoffEnd,
                    if (isFullScreen) 0.82f else 0.88f,
                    motionState.progress.value
                )
            )
        }
        val destinationContentAlpha = if (destinationClosing) {
            if (isFullScreen) {
                smoothStep(0.16f, 0.42f, motionState.progress.value)
            } else {
                smoothStep(0.66f, 0.90f, motionState.progress.value)
            }
        } else {
            smoothStep(destinationHandoffStart, destinationHandoffEnd, motionState.progress.value)
        }

        Box(
            Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = !isFullScreen,
                    onClick = { latestDismiss() }
                )
        )
        Box(
            modifier = Modifier
                .offset { IntOffset(rect.left.roundToInt(), rect.top.roundToInt()) }
                .size(
                    with(density) { rect.width.toDp() },
                    with(density) { rect.height.toDp() }
                )
                .graphicsLayer {
                    alpha = if (collapseHandoffReached) 0f else 1f
                    clip = !fullScreenOpenEndpoint
                    shape = RoundedRectangle(corner)
                },
            contentAlignment = Alignment.Center
        ) {
            val lightGlass = glassUsesLightStyle(state.config)
            if (backdrop != null) {
                LiquidPanel(
                    backdrop = backdrop,
                    modifier = Modifier.fillMaxSize().graphicsLayer { alpha = destinationSurfaceAlpha },
                    shape = RoundedRectangle(corner),
                    surfaceColor = if (lightGlass) {
                        Color.White.copy(alpha = 0.18f)
                    } else {
                        Color(0xFF121212).copy(alpha = 0.30f)
                    },
                    blurRadius = 10.dp
                ) { }
            } else {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(if (appUsesDarkTheme(state.config)) Color(0xFF1C1C1E) else Color.White)
                )
            }
            if (sourceCloneAlpha > 0.001f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = sourceCloneAlpha }
                ) {
                    HomeAddMenuMorphPanel(
                        backdrop = backdrop,
                        config = state.config,
                        actions = sourceActions,
                        targetSize = IntSize(
                            rect.width.roundToInt(),
                            rect.height.roundToInt()
                        ),
                        surfaceAlpha = 1f,
                        contentAlpha = 1f,
                        contentBlurRadiusPx = sourceContentBlurPx,
                        interactive = false,
                        corner = 26.dp,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            if (isFullScreen && destinationContentPrepared) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = destinationContentAlpha }
                        .background(androidx.compose.material3.MaterialTheme.colorScheme.background)
                )
            }
            if (destinationContentPrepared) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .requiredSize(targetWidth, targetHeight)
                        .drawWithContent {
                            if (motionState.phase == HomeAnchoredOverlayPhase.Preparing ||
                                motionState.phase == HomeAnchoredOverlayPhase.Open
                            ) {
                                destinationContentLayer.record {
                                    this@drawWithContent.drawContent()
                                }
                            }
                            drawLayer(destinationContentLayer)
                        }
                        .graphicsLayer {
                            alpha = destinationContentAlpha
                            compositingStrategy = CompositingStrategy.Offscreen
                            renderEffect = if (destinationContentBlurPx > 0.01f) {
                                BlurEffect(
                                    destinationContentBlurPx,
                                    destinationContentBlurPx,
                                    TileMode.Clamp
                                )
                            } else null
                        }
                ) {
                    when (shown.kind) {
                        HomeMenuDestinationKind.AddCourse -> top.yukonga.miuix.kmp.basic.Scaffold(
                            modifier = Modifier.fillMaxSize(),
                            containerColor = Color.Transparent,
                            contentWindowInsets = WindowInsets(0, 0, 0, 0)
                        ) {
                            NormalizedCourseEditorScreen(
                                state = state,
                                initialCourse = null,
                                onCancel = { latestDismiss() },
                                onSave = {},
                                onSaveCourses = onAddCourses,
                                onDelete = {},
                                backdrop = backdrop,
                                pickerRenderInRootScaffold = false
                            )
                        }
                        HomeMenuDestinationKind.ManualImport -> NormalizedAiManualImportScreen(
                            state = state,
                            backdrop = backdrop,
                            onCancel = { latestDismiss() },
                            captureHistoryBackground = captureHistoryBackground,
                            hiddenHistoryEntryId = hiddenHistoryEntryId,
                            onOpenHistoryEntry = { entry, sourceBounds, sourceSnapshot ->
                                val draft = AiImportHistoryStore.restore(entry, state.config).getOrNull()
                                if (draft != null) {
                                    historyDetailRequest = AiImportHistoryDetailMorphRequest(
                                        entry = entry,
                                        draft = draft,
                                        sourceBounds = sourceBounds,
                                        sourceSnapshot = sourceSnapshot
                                    )
                                }
                            },
                            onParsed = onManualImportParsed
                        )
                        HomeMenuDestinationKind.EduImport -> DetailActivityScaffold(
                            title = "选择学校",
                            config = state.config,
                            onBack = { latestDismiss() }
                        ) {
                            EduSchoolPickerScreen(
                                state = state,
                                onSelect = onEduAdapterSelected
                            )
                        }
                    }
                }
            }
            if (motionState.phase != HomeAnchoredOverlayPhase.Open) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(motionState.phase) {
                            awaitPointerEventScope {
                                while (true) awaitPointerEvent().changes.forEach { it.consume() }
                            }
                        }
                )
            }
        }
        historyDetailRequest?.let { detailRequest ->
            Box(Modifier.fillMaxSize().zIndex(500f)) {
                AiImportHistoryDetailMorphOverlay(
                    request = detailRequest,
                    config = state.config,
                    onSourceHandoff = { hiddenHistoryEntryId = detailRequest.entry.id },
                    onClosed = {
                        hiddenHistoryEntryId = null
                        historyDetailRequest = null
                        if (historyImportRequested) {
                            historyImportRequested = false
                            latestDismiss()
                        }
                    },
                    onImportRequested = { draft, createNewSchedule ->
                        AiEduImportProgressSession.requestFinalImport(draft, createNewSchedule)
                        historyImportRequested = true
                    },
                    sourceContent = {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(
                                    glassForegroundColor(state.config).copy(alpha = 0.055f)
                                )
                        ) {
                            AiImportHistoryRowContent(
                                entry = detailRequest.entry,
                                modifier = Modifier.fillMaxSize(),
                                textColor = glassForegroundColor(state.config)
                            )
                        }
                    }
                )
            }
        }
    }
}
