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
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.catalog.components.LiquidPanel
import com.kyant.shapes.RoundedRectangle
import kotlinx.coroutines.coroutineScope
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
    var kind by mutableStateOf<HomeMenuDestinationKind?>(null)
        internal set
}

@Composable
internal fun rememberHomeMenuDestinationMotionState(): HomeMenuDestinationMotionState =
    remember { HomeMenuDestinationMotionState() }

private data class HomeMenuDestinationFrame(
    val geometry: HomeAnchoredMorphGeometry,
    val destinationClosing: Boolean,
    val fullScreenOpenEndpoint: Boolean,
    val renderedCornerRadiusPx: Float,
    val sourceCloneAlpha: Float,
    val destinationSurfaceAlpha: Float,
    val sourceContentBlurPx: Float,
    val destinationContentBlurPx: Float,
    val destinationContentAlpha: Float,
    val destinationBlurMix: Float
)

private class DeferredDestinationShape(
    private val frame: State<HomeMenuDestinationFrame>,
    private val density: Density,
    topStart: CornerSize = CornerSize(0f),
    topEnd: CornerSize = topStart,
    bottomEnd: CornerSize = topStart,
    bottomStart: CornerSize = topStart
) : CornerBasedShape(topStart, topEnd, bottomEnd, bottomStart) {
    override fun createOutline(
        size: Size,
        topStart: Float,
        topEnd: Float,
        bottomEnd: Float,
        bottomStart: Float,
        layoutDirection: LayoutDirection
    ): Outline {
        val corner = (
            frame.value.renderedCornerRadiusPx / density.density.coerceAtLeast(0.001f)
            ).coerceAtLeast(0f).dp
        return RoundedRectangle(corner).createOutline(size, layoutDirection, density)
    }

    override fun copy(
        topStart: CornerSize,
        topEnd: CornerSize,
        bottomEnd: CornerSize,
        bottomStart: CornerSize
    ): CornerBasedShape = DeferredDestinationShape(
        frame = frame,
        density = density,
        topStart = topStart,
        topEnd = topEnd,
        bottomEnd = bottomEnd,
        bottomStart = bottomStart
    )
}

private fun destinationSmoothStep(edge0: Float, edge1: Float, value: Float): Float {
    val x = ((value - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}

private const val DestinationOpenDurationMillis = 330
private const val DestinationPanelOpenDurationMillis = 400
internal const val HomeMenuDestinationCloseDurationMillis = 430
private const val DestinationBackgroundDurationMillis = 420
internal const val HomeMenuDestinationEduBackgroundScale = 1.08f
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
    homeMode: HomeMode,
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
            motionState.kind = request.kind
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
                        tween(
                            durationMillis = if (request.kind == HomeMenuDestinationKind.EduImport) {
                                DestinationOpenDurationMillis
                            } else {
                                DestinationPanelOpenDurationMillis
                            },
                            easing = LinearEasing
                        )
                    )
                }
                launch {
                    // The destination owns an exact Add-menu clone while progress is still at its
                    // source endpoint. Let that clone draw once, then release the original menu on
                    // the next frame instead of overlapping two live backdrop consumers for a
                    // fixed 32/52 ms window.
                    withFrameNanos { }
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
            motionState.kind = null
            latestClosed()
        }
    }

    val shown = request ?: renderedRequest
    BackHandler(enabled = shown != null) { latestDismiss() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .semantics { testTag = "benchmark_home_menu_destination" }
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
        val maxContentBlurPx = with(density) { 5.dp.toPx() }
        val frame = remember(
            shown,
            target,
            isFullScreen,
            density.density,
            adaptiveMetrics,
            motionState
        ) {
            derivedStateOf {
                val destinationClosing =
                    motionState.phase == HomeAnchoredOverlayPhase.Closing ||
                        motionState.phase == HomeAnchoredOverlayPhase.Disposing
                val rawProgress = motionState.progress.value
                val morphSource = if (destinationClosing) {
                    shown.collapseBoundsInRoot
                } else {
                    shown.sourceBoundsInRoot
                }
                val baseGeometry = homeAnchoredMorphGeometry(
                    source = morphSource,
                    target = target,
                    rawProgress = rawProgress,
                    // Menu destinations morph directly between the open menu and their target.
                    closing = destinationClosing,
                    directClosing = !destinationClosing,
                    directSourceCornerRadiusPx = with(density) {
                        if (destinationClosing) 21.dp.toPx() else 32.dp.toPx()
                    },
                    pinchDiameterPx = with(density) { 18.dp.toPx() },
                    minimumDropPx = with(density) { 12.dp.toPx() },
                    maximumDropPx = with(density) { adaptiveMetrics.animationArc.toPx() },
                    maximumArcPx = with(density) { adaptiveMetrics.animationArc.toPx() + 16.dp.toPx() },
                    targetCornerRadiusPx = with(density) {
                        if (isFullScreen) 0.dp.toPx() else 32.dp.toPx()
                    }
                )
                val geometry = if (isFullScreen) {
                    baseGeometry
                } else {
                    homeMorphWithVerticalRebound(
                        geometry = baseGeometry,
                        closing = destinationClosing,
                        overshootPx = with(density) { 14.dp.toPx() },
                        peakProgress = 0.40f
                    )
                }
                val fullScreenOpenEndpoint = isFullScreen &&
                    !destinationClosing && rawProgress >= 0.999f
                val renderedCornerRadiusPx = if (isFullScreen) {
                    if (fullScreenOpenEndpoint) {
                        0f
                    } else {
                        val progress = geometry.pathProgress
                        val sourceCorner = with(density) { 32.dp.toPx() }
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
                val sourceHandoffStart = if (isFullScreen) 0.035f else 0.12f
                val sourceHandoffEnd = if (isFullScreen) 0.20f else 0.40f
                val destinationHandoffStart = if (isFullScreen) 0.055f else 0.14f
                val destinationHandoffEnd = if (isFullScreen) 0.24f else 0.34f
                val sourceCloneAlpha = if (destinationClosing) {
                    0f
                } else {
                    1f - destinationSmoothStep(sourceHandoffStart, sourceHandoffEnd, rawProgress)
                }
                val destinationSurfaceAlpha = 1f - sourceCloneAlpha
                val sourceContentBlurPx = if (destinationClosing) {
                    0f
                } else {
                    maxContentBlurPx * destinationSmoothStep(0f, sourceHandoffStart, rawProgress)
                }
                val destinationContentBlurPx = if (destinationClosing) {
                    val closeElapsed = 1f - rawProgress
                    maxContentBlurPx * destinationSmoothStep(
                        if (isFullScreen) 0.48f else 0f,
                        if (isFullScreen) 0.84f else 0.34f,
                        closeElapsed
                    )
                } else {
                    maxContentBlurPx * (
                        1f - destinationSmoothStep(
                            destinationHandoffEnd,
                            if (isFullScreen) 0.82f else 0.88f,
                            rawProgress
                        )
                        )
                }
                val destinationContentAlpha = if (destinationClosing) {
                    if (isFullScreen) {
                        destinationSmoothStep(0.16f, 0.42f, rawProgress)
                    } else {
                        destinationSmoothStep(0.66f, 0.90f, rawProgress)
                    }
                } else {
                    destinationSmoothStep(destinationHandoffStart, destinationHandoffEnd, rawProgress)
                }
                HomeMenuDestinationFrame(
                    geometry = geometry,
                    destinationClosing = destinationClosing,
                    fullScreenOpenEndpoint = fullScreenOpenEndpoint,
                    renderedCornerRadiusPx = renderedCornerRadiusPx,
                    sourceCloneAlpha = sourceCloneAlpha,
                    destinationSurfaceAlpha = destinationSurfaceAlpha,
                    sourceContentBlurPx = sourceContentBlurPx,
                    destinationContentBlurPx = destinationContentBlurPx,
                    destinationContentAlpha = destinationContentAlpha,
                    destinationBlurMix = (
                        destinationContentBlurPx / maxContentBlurPx.coerceAtLeast(0.001f)
                        ).coerceIn(0f, 1f)
                )
            }
        }
        val collapseHandoffReached by remember(frame) {
            derivedStateOf {
                frame.value.destinationClosing &&
                    frame.value.geometry.pathProgress <= HomeAnchoredMorphClosePinchFraction
            }
        }
        LaunchedEffect(collapseHandoffReached) {
            if (collapseHandoffReached) latestCollapseHandoff(shown.kind)
        }
        val destinationShape = remember(frame, density) { DeferredDestinationShape(frame, density) }
        val sourceMenuShape = remember { androidx.compose.foundation.shape.RoundedCornerShape(32.dp) }
        val targetWidth = with(density) { target.width.toDp() }
        val targetHeight = with(density) { target.height.toDp() }
        val destinationTestTag = when (shown.kind) {
            HomeMenuDestinationKind.AddCourse -> "benchmark_home_destination_add_course"
            HomeMenuDestinationKind.ManualImport -> "benchmark_home_destination_manual_import"
            HomeMenuDestinationKind.EduImport -> "benchmark_home_destination_edu_import"
        }

        val showDestinationSurface by remember(frame, backdrop) {
            derivedStateOf { backdrop == null || frame.value.destinationSurfaceAlpha > 0.005f }
        }
        val showSourceClone by remember(frame) {
            derivedStateOf { frame.value.sourceCloneAlpha > 0.005f }
        }
        val showBlurredDestinationContent by remember(frame) {
            derivedStateOf {
                frame.value.destinationContentAlpha > 0.01f &&
                    frame.value.destinationBlurMix > 0.005f
            }
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
                .offset {
                    val rect = frame.value.geometry.rect
                    IntOffset(rect.left.roundToInt(), rect.top.roundToInt())
                }
                .layout { measurable, _ ->
                    val rect = frame.value.geometry.rect
                    val width = rect.width.roundToInt().coerceAtLeast(1)
                    val height = rect.height.roundToInt().coerceAtLeast(1)
                    val placeable = measurable.measure(Constraints.fixed(width, height))
                    layout(width, height) { placeable.place(0, 0) }
                }
                .semantics { testTag = destinationTestTag }
                .graphicsLayer {
                    val current = frame.value
                    alpha = if (
                        current.destinationClosing &&
                        current.geometry.pathProgress <= HomeAnchoredMorphClosePinchFraction
                    ) 0f else 1f
                    clip = !current.fullScreenOpenEndpoint
                    shape = destinationShape
                },
            contentAlignment = Alignment.Center
        ) {
            val lightGlass = glassUsesLightStyle(state.config)
            if (showDestinationSurface) {
                if (backdrop != null) {
                    LiquidPanel(
                        backdrop = backdrop,
                        modifier = Modifier.fillMaxSize().graphicsLayer {
                            alpha = frame.value.destinationSurfaceAlpha
                        },
                        shape = destinationShape,
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
                            .graphicsLayer { alpha = frame.value.destinationSurfaceAlpha }
                            .background(
                                if (appUsesDarkTheme(state.config)) Color(0xFF1C1C1E) else Color.White
                            )
                    )
                }
            }
            if (showSourceClone) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = frame.value.sourceCloneAlpha }
                ) {
                    HomeAddMenuMorphPanel(
                        backdrop = backdrop,
                        config = state.config,
                        actions = sourceActions,
                        homeMode = homeMode,
                        onHomeModeChange = {},
                        targetSizeProvider = {
                            val rect = frame.value.geometry.rect
                            IntSize(rect.width.roundToInt(), rect.height.roundToInt())
                        },
                        surfaceAlphaProvider = { 1f },
                        contentAlphaProvider = { 1f },
                        contentBlurRadiusPxProvider = { frame.value.sourceContentBlurPx },
                        interactive = false,
                        shape = sourceMenuShape,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            if (isFullScreen && destinationContentPrepared) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = frame.value.destinationContentAlpha }
                        .background(androidx.compose.material3.MaterialTheme.colorScheme.background)
                )
            }
            if (destinationContentPrepared) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .requiredSize(targetWidth, targetHeight)
                        .drawWithContent {
                            val phase = motionState.phase
                            if (phase == HomeAnchoredOverlayPhase.Preparing ||
                                phase == HomeAnchoredOverlayPhase.Open
                            ) {
                                destinationContentLayer.record {
                                    this@drawWithContent.drawContent()
                                }
                            }
                            if (phase == HomeAnchoredOverlayPhase.Open) {
                                // At the opening endpoint draw the live form directly. Reading the
                                // same GraphicsLayer immediately after re-recording it can expose a
                                // one-frame empty/old texture on some Vulkan drivers. The recording
                                // above is still kept current for the later closing animation.
                                this@drawWithContent.drawContent()
                            } else {
                                drawLayer(destinationContentLayer)
                            }
                        }
                        .graphicsLayer {
                            val current = frame.value
                            alpha = current.destinationContentAlpha * (1f - current.destinationBlurMix)
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
                if (showBlurredDestinationContent) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .requiredSize(targetWidth, targetHeight)
                            .drawWithContent { drawLayer(destinationContentLayer) }
                            .graphicsLayer {
                                val current = frame.value
                                alpha = current.destinationContentAlpha * current.destinationBlurMix
                                compositingStrategy = CompositingStrategy.Offscreen
                                renderEffect = BlurEffect(
                                    maxContentBlurPx,
                                    maxContentBlurPx,
                                    TileMode.Clamp
                                )
                            }
                    )
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
