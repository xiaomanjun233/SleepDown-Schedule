package com.xiaomanjun.sleepdownschedule.feature.home.overlay

import com.xiaomanjun.sleepdownschedule.app.ui.*
import com.xiaomanjun.sleepdownschedule.glass.ui.*
import com.xiaomanjun.sleepdownschedule.transition.legacy.*
import com.xiaomanjun.sleepdownschedule.feature.importing.history.*
import com.xiaomanjun.sleepdownschedule.*
import com.xiaomanjun.sleepdownschedule.feature.home.*
import com.xiaomanjun.sleepdownschedule.feature.home.day.*
import com.xiaomanjun.sleepdownschedule.feature.home.week.*

import com.xiaomanjun.sleepdownschedule.feature.course.editor.*
import com.xiaomanjun.sleepdownschedule.feature.importing.*
import com.xiaomanjun.sleepdownschedule.core.ui.settings.*

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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.catalog.components.LiquidPanel
import com.kyant.shapes.RoundedRectangle
import com.xiaomanjun.sleepdownschedule.glass.LiquidMorphController
import com.xiaomanjun.sleepdownschedule.glass.LiquidMorphControllerBridge
import com.xiaomanjun.sleepdownschedule.glass.LiquidMorphPhase
import com.xiaomanjun.sleepdownschedule.glass.GlassBackdropDomain
import com.xiaomanjun.sleepdownschedule.glass.GlassMaterialRole
import com.xiaomanjun.sleepdownschedule.glass.GlassRendererKind
import com.xiaomanjun.sleepdownschedule.glass.GlassSceneKeys
import com.xiaomanjun.sleepdownschedule.glass.GlassTransitionEnvelope
import com.xiaomanjun.sleepdownschedule.glass.GlassTransitionGeometry
import com.xiaomanjun.sleepdownschedule.glass.GlassTransitionLayer
import com.xiaomanjun.sleepdownschedule.glass.LocalGlassSceneState
import com.xiaomanjun.sleepdownschedule.glass.rememberGlassSurfaceDescriptor
import com.xiaomanjun.sleepdownschedule.glass.sampleGlassTransitionEnvelope
import com.xiaomanjun.sleepdownschedule.glass.stableContentOffsetInEnvelope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
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
    var kind by mutableStateOf<HomeMenuDestinationKind?>(null)
        internal set
    private val controllerBridge = LiquidMorphControllerBridge {
        "home-menu-destination:${kind?.name ?: "unknown"}"
    }
    val liquidMorphController: LiquidMorphController get() = controllerBridge.controller
    private var currentPhase by mutableStateOf(HomeAnchoredOverlayPhase.Idle)
    var phase: HomeAnchoredOverlayPhase
        get() = currentPhase
        internal set(value) {
            if (currentPhase == value) return
            currentPhase = value
            controllerBridge.synchronize(
                when (value) {
                    HomeAnchoredOverlayPhase.Idle -> LiquidMorphPhase.Idle
                    HomeAnchoredOverlayPhase.Preparing -> LiquidMorphPhase.Preparing
                    HomeAnchoredOverlayPhase.Opening -> LiquidMorphPhase.Opening
                    HomeAnchoredOverlayPhase.Open -> LiquidMorphPhase.Open
                    HomeAnchoredOverlayPhase.Closing -> LiquidMorphPhase.Closing
                    HomeAnchoredOverlayPhase.Disposing -> LiquidMorphPhase.Released
                }
            )
        }
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

@Composable
private fun HomeMenuDestinationTransitionShell(
    useStableEnvelope: Boolean,
    envelope: GlassTransitionEnvelope,
    geometry: () -> GlassTransitionGeometry,
    referenceRect: () -> Rect,
    targetSizePx: IntSize,
    targetWidth: Dp,
    targetHeight: Dp,
    temporaryClipActive: Boolean,
    clipStableEndpoint: Boolean,
    collapseHandedOff: Boolean,
    destinationShape: CornerBasedShape,
    destinationTestTag: String,
    content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit
) {
    if (!useStableEnvelope) {
        Box(
            modifier = Modifier
                .offset {
                    val rect = referenceRect()
                    IntOffset(rect.left.roundToInt(), rect.top.roundToInt())
                }
                .graphicsLayer {
                    alpha = if (collapseHandedOff) 0f else 1f
                    clip = temporaryClipActive || clipStableEndpoint
                    shape = destinationShape
                }
                .layout { measurable, _ ->
                    val placeable = measurable.measure(
                        Constraints.fixed(targetSizePx.width, targetSizePx.height)
                    )
                    val rect = referenceRect()
                    val width = rect.width.roundToInt().coerceAtLeast(1)
                    val height = rect.height.roundToInt().coerceAtLeast(1)
                    layout(width, height) {
                        placeable.place(
                            (width - targetSizePx.width) / 2,
                            (height - targetSizePx.height) / 2
                        )
                    }
                }
                .semantics { testTag = destinationTestTag },
            contentAlignment = Alignment.Center,
            content = content
        )
        return
    }

    GlassTransitionLayer(
        envelope = envelope,
        geometry = geometry,
        temporaryClipActive = temporaryClipActive,
        motionAlpha = { if (collapseHandedOff) 0f else 1f },
        modifier = Modifier.semantics { testTag = destinationTestTag }
    ) { stableEnvelope, currentGeometry ->
        val endpointClip = if (!temporaryClipActive && clipStableEndpoint) {
            Modifier.graphicsLayer {
                clip = true
                shape = destinationShape
            }
        } else {
            Modifier
        }
        Box(
            modifier = Modifier
                .offset {
                    stableContentOffsetInEnvelope(
                        envelope = stableEnvelope,
                        geometry = currentGeometry(),
                        stableContentSize = targetSizePx
                    )
                }
                .requiredSize(targetWidth, targetHeight)
                .then(endpointClip),
            contentAlignment = Alignment.Center,
            content = content
        )
    }
}

private fun destinationSmoothStep(edge0: Float, edge1: Float, value: Float): Float {
    val x = ((value - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}

internal object HomeMenuDestinationLegacyMotion {
    const val OpenDurationMillis = 330
    const val CloseDurationMillis = 350

    // The form handoff remains delayed so cached and live text fields never overlap visibly.
    // This is a rendering policy only; it does not alter the recovered 1.1.5 shell geometry.
    const val NonFullscreenContentRevealStart = 0.52f
    const val NonFullscreenContentRevealEnd = 0.72f
}

internal fun homeMenuDestinationContentAlpha(
    rawProgress: Float,
    isFullScreen: Boolean,
    closing: Boolean
): Float = when {
    closing && isFullScreen -> destinationSmoothStep(0.16f, 0.42f, rawProgress)
    closing -> destinationSmoothStep(0.66f, 0.90f, rawProgress)
    isFullScreen -> destinationSmoothStep(0.055f, 0.24f, rawProgress)
    else -> destinationSmoothStep(
        HomeMenuDestinationLegacyMotion.NonFullscreenContentRevealStart,
        HomeMenuDestinationLegacyMotion.NonFullscreenContentRevealEnd,
        rawProgress
    )
}

internal fun homeMenuDestinationOpeningContentBlurMix(
    rawProgress: Float,
    isFullScreen: Boolean
): Float = if (isFullScreen) {
    1f - destinationSmoothStep(0.24f, 0.82f, rawProgress)
} else {
    // The underlying form is a GraphicsLayer cache. Blending a blurred copy of that cache with
    // its clear copy is particularly visible on cursor/input-field chrome during a fast morph.
    0f
}

/**
 * Keeps the real source outline intact until the source clone has handed off. Increasing the
 * radius during that overlap clips Kyant's highlight at the exact frame where the Activity and
 * the source window exchange ownership. The middle radius is introduced only after the source is
 * visually gone, then the full-screen endpoint releases the transient shell altogether.
 */
internal fun homeMenuDestinationRenderedCornerRadiusPx(
    geometry: HomeAnchoredMorphGeometry,
    rawProgress: Float,
    isFullScreen: Boolean,
    closing: Boolean,
    sourceCornerRadiusPx: Float,
    collapseCornerRadiusPx: Float,
    middleCornerRadiusPx: Float
): Float {
    if (!isFullScreen) {
        return geometry.cornerRadiusPx
    }
    if (closing) {
        // A full-screen destination has no visible corner at rest. As soon as it starts closing,
        // restore the transient shell radius used by the 1.1.5 transition, then converge on the
        // real three-dot button radius before the source window resumes ownership.
        val path = geometry.pathProgress
        val closeRadiusEnd = 0.35f
        return if (path >= closeRadiusEnd) {
            middleCornerRadiusPx
        } else {
            collapseCornerRadiusPx + (middleCornerRadiusPx - collapseCornerRadiusPx) *
                (path / closeRadiusEnd).coerceIn(0f, 1f)
        }
    }
    if (rawProgress >= 0.999f) {
        return geometry.cornerRadiusPx
    }
    val sourceHandoffEnd = 0.20f
    val middleRadiusEnd = 0.35f
    val path = geometry.pathProgress
    return when {
        path <= sourceHandoffEnd -> sourceCornerRadiusPx
        path >= middleRadiusEnd -> middleCornerRadiusPx
        else -> sourceCornerRadiusPx + (middleCornerRadiusPx - sourceCornerRadiusPx) *
            ((path - sourceHandoffEnd) / (middleRadiusEnd - sourceHandoffEnd))
    }
}

private const val DestinationOpenDurationMillis = HomeMenuDestinationLegacyMotion.OpenDurationMillis
internal const val HomeMenuDestinationCloseDurationMillis =
    HomeMenuDestinationLegacyMotion.CloseDurationMillis
private const val DestinationBackgroundDurationMillis = 420
internal const val HomeMenuDestinationEduBackgroundScale = 1.08f
private val DestinationBackgroundEasing = CubicBezierEasing(0.30f, 0f, 0.20f, 1f)

/**
 * The recovered 1.1.5 shared-object trajectory for Home menu destinations.
 *
 * Opening morphs directly from the first-level menu bounds. Closing uses the real three-dot button
 * as the geometry source while progress runs back to zero, so the destination is absorbed directly
 * into that button without a menu waypoint or first-level-menu choreography.
 */
internal fun homeMenuDestinationTrajectoryGeometry(
    sourceBoundsInRoot: Rect,
    collapseBoundsInRoot: Rect,
    target: Rect,
    rawProgress: Float,
    closing: Boolean,
    menuCornerRadiusPx: Float,
    buttonCornerRadiusPx: Float,
    pinchDiameterPx: Float,
    minimumDropPx: Float,
    maximumDropPx: Float,
    maximumArcPx: Float,
    targetCornerRadiusPx: Float
): HomeAnchoredMorphGeometry {
    val morphSource = if (closing) collapseBoundsInRoot else sourceBoundsInRoot
    return homeAnchoredMorphGeometry(
        source = morphSource,
        target = target,
        rawProgress = rawProgress,
        closing = closing,
        directClosing = !closing,
        directSourceCornerRadiusPx = if (closing) buttonCornerRadiusPx else menuCornerRadiusPx,
        pinchDiameterPx = pinchDiameterPx,
        minimumDropPx = minimumDropPx,
        maximumDropPx = maximumDropPx,
        maximumArcPx = maximumArcPx,
        targetCornerRadiusPx = targetCornerRadiusPx,
        motionStyle = HomeMorphEasingStyle.Legacy
    )
}

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
    awaitOpeningGate: suspend () -> Unit = {},
    onDismissRequest: () -> Unit,
    sourceActions: List<AddMenuAction>,
    onSourceHandoff: () -> Unit,
    onCollapseHandoff: (HomeMenuDestinationKind) -> Unit,
    onClosed: () -> Unit,
    onAddCourses: (List<CourseEntity>) -> Unit,
    onManualImportParsed: (ImportDraft) -> Unit,
    captureHistoryBackground: suspend () -> AiImportHistoryBackgroundCapture?,
    onEduAdapterSelected: (EduAdapter) -> Unit
) {
    val context = LocalContext.current
    val floatingOverlayHost = remember { DetailActivityFloatingOverlayHost() }
    var renderedRequest by remember { mutableStateOf<HomeMenuDestinationRequest?>(null) }
    var destinationContentPrepared by remember { mutableStateOf(false) }
    var collapseHandedOff by remember { mutableStateOf(false) }
    var rootSize by remember { mutableStateOf(IntSize.Zero) }
    val destinationContentLayer = rememberGraphicsLayer()
    val destinationContentRecorded = remember { AtomicBoolean(false) }
    val destinationClosingRecorded = remember { AtomicBoolean(false) }
    val latestDismiss by rememberUpdatedState(onDismissRequest)
    val latestSourceHandoff by rememberUpdatedState(onSourceHandoff)
    val latestCollapseHandoff by rememberUpdatedState(onCollapseHandoff)
    val latestClosed by rememberUpdatedState(onClosed)
    val latestAwaitOpeningGate by rememberUpdatedState(awaitOpeningGate)

    LaunchedEffect(request) {
        if (request != null) {
            motionState.kind = request.kind
            renderedRequest = request
            collapseHandedOff = false
            destinationContentPrepared = false
            destinationContentRecorded.set(false)
            destinationClosingRecorded.set(false)
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
            latestAwaitOpeningGate()
            motionState.phase = HomeAnchoredOverlayPhase.Opening
            coroutineScope {
                launch {
                    motionState.progress.animateTo(
                        1f,
                        tween(
                            durationMillis = DestinationOpenDurationMillis,
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
                        if (request.kind == HomeMenuDestinationKind.EduImport) {
                            HomeMenuDestinationEduBackgroundScale
                        } else {
                            HomeAnchoredMorphBackgroundScale
                        },
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
                        tween(
                            durationMillis = HomeMenuDestinationCloseDurationMillis,
                            easing = LinearEasing
                        )
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
            destinationContentRecorded.set(false)
            destinationClosingRecorded.set(false)
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
            .graphicsLayer { clip = false }
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
        val morphSpec = remember(
            shown,
            target,
            isFullScreen,
            density.density,
            adaptiveMetrics
        ) {
            legacyHomeMenuDestinationMorphSpec(
                sourceBoundsInRoot = shown.sourceBoundsInRoot,
                collapseBoundsInRoot = shown.collapseBoundsInRoot,
                target = target,
                menuCornerRadiusPx = with(density) { HomeAddMenuTargetCornerDp.dp.toPx() },
                buttonCornerRadiusPx = with(density) { 21.dp.toPx() },
                pinchDiameterPx = with(density) { 18.dp.toPx() },
                minimumDropPx = with(density) { 12.dp.toPx() },
                maximumDropPx = with(density) { adaptiveMetrics.animationArc.toPx() },
                maximumArcPx = with(density) {
                    adaptiveMetrics.animationArc.toPx() + 16.dp.toPx()
                },
                targetCornerRadiusPx = with(density) {
                    if (isFullScreen) 0.dp.toPx() else 32.dp.toPx()
                }
            )
        }
        val frame = remember(
            morphSpec,
            isFullScreen,
            density.density,
            motionState
        ) {
            derivedStateOf {
                val destinationClosing =
                    motionState.phase == HomeAnchoredOverlayPhase.Closing ||
                        motionState.phase == HomeAnchoredOverlayPhase.Disposing
                val rawProgress = motionState.progress.value
                val geometry = morphSpec.homeGeometry(
                    source = shown.sourceBoundsInRoot,
                    target = target,
                    rawProgress = rawProgress,
                    closing = destinationClosing
                )
                val fullScreenOpenEndpoint = isFullScreen &&
                    !destinationClosing && rawProgress >= 0.999f
                val renderedCornerRadiusPx = homeMenuDestinationRenderedCornerRadiusPx(
                    geometry = geometry,
                    rawProgress = rawProgress,
                    isFullScreen = isFullScreen,
                    closing = destinationClosing,
                    sourceCornerRadiusPx = with(density) { HomeAddMenuTargetCornerDp.dp.toPx() },
                    collapseCornerRadiusPx = with(density) { 21.dp.toPx() },
                    middleCornerRadiusPx = with(density) { 46.dp.toPx() }
                )
                val sourceHandoffStart = if (isFullScreen) 0.035f else 0.12f
                val sourceHandoffEnd = if (isFullScreen) 0.20f else 0.40f
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
                    maxContentBlurPx * homeMenuDestinationOpeningContentBlurMix(
                        rawProgress = rawProgress,
                        isFullScreen = isFullScreen
                    )
                }
                val destinationContentAlpha = homeMenuDestinationContentAlpha(
                    rawProgress = rawProgress,
                    isFullScreen = isFullScreen,
                    closing = destinationClosing
                )
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
            if (collapseHandoffReached && !collapseHandedOff) {
                collapseHandedOff = true
                latestCollapseHandoff(shown.kind)
            }
        }
        val destinationShape = remember(frame, density) { DeferredDestinationShape(frame, density) }
        val sourceMenuShape = remember {
            androidx.compose.foundation.shape.RoundedCornerShape(HomeAddMenuTargetCornerDp.dp)
        }
        val targetWidth = with(density) { target.width.toDp() }
        val targetHeight = with(density) { target.height.toDp() }
        val destinationTestTag = when (shown.kind) {
            HomeMenuDestinationKind.AddCourse -> "benchmark_home_destination_add_course"
            HomeMenuDestinationKind.ManualImport -> "benchmark_home_destination_manual_import"
            HomeMenuDestinationKind.EduImport -> "benchmark_home_destination_edu_import"
        }
        val glassSceneKey = when (shown.kind) {
            HomeMenuDestinationKind.AddCourse -> GlassSceneKeys.HomeMenuDestinationAddCourse
            HomeMenuDestinationKind.ManualImport -> GlassSceneKeys.HomeMenuDestinationManualImport
            HomeMenuDestinationKind.EduImport -> GlassSceneKeys.HomeMenuDestinationEduImport
        }
        val envelopeDescriptor = rememberGlassSurfaceDescriptor(
            debugLabel = "home-menu-destination-envelope",
            domain = GlassBackdropDomain.ChromeCombined,
            materialRole = GlassMaterialRole.MorphShell,
            requestedRenderer = GlassRendererKind.StableEnvelopeExperimental,
            sceneKey = glassSceneKey
        )
        val glassSceneState = LocalGlassSceneState.current
        val useStableEnvelope = glassSceneState?.rendererFor(envelopeDescriptor) ==
            GlassRendererKind.StableEnvelopeExperimental
        val targetSizePx = remember(target) {
            IntSize(
                target.width.roundToInt().coerceAtLeast(1),
                target.height.roundToInt().coerceAtLeast(1)
            )
        }
        val transitionEnvelope = remember(
            useStableEnvelope,
            morphSpec,
            shown,
            target,
            density.density
        ) {
            if (useStableEnvelope) {
                sampleGlassTransitionEnvelope(
                    tracks = listOf(
                        { progress ->
                            GlassTransitionGeometry(
                                rectInRoot = morphSpec.homeGeometry(
                                    source = shown.sourceBoundsInRoot,
                                    target = target,
                                    rawProgress = progress,
                                    closing = false
                                ).rect,
                                cornerRadiusPx = 0f
                            )
                        },
                        { progress ->
                            GlassTransitionGeometry(
                                rectInRoot = morphSpec.homeGeometry(
                                    source = shown.sourceBoundsInRoot,
                                    target = target,
                                    rawProgress = progress,
                                    closing = true
                                ).rect,
                                cornerRadiusPx = 0f
                            )
                        }
                    ),
                    steps = 256,
                    // The full-screen target already bounds every legacy path sample. Keeping it
                    // exactly root-sized avoids requiredSize coercion and a larger-than-window
                    // RenderTarget; centered dialogs retain a two-pixel sampling guard.
                    effectPaddingPx = if (isFullScreen) 0f else 2f
                )
            } else {
                GlassTransitionEnvelope.covering(
                    listOf(GlassTransitionGeometry(target, 0f))
                )
            }
        }
        val transitionGeometry = remember(frame) {
            {
                val current = frame.value
                GlassTransitionGeometry(
                    rectInRoot = current.geometry.rect,
                    cornerRadiusPx = current.renderedCornerRadiusPx
                )
            }
        }
        val temporaryClipActive by remember(frame, isFullScreen) {
            derivedStateOf {
                if (isFullScreen) {
                    !frame.value.fullScreenOpenEndpoint
                } else {
                    motionState.phase != HomeAnchoredOverlayPhase.Open
                }
            }
        }
        val transitionResourceId = remember(glassSceneKey) { "stable-envelope:$glassSceneKey" }
        if (useStableEnvelope) {
            val activeGlassSceneState = checkNotNull(glassSceneState)
            DisposableEffect(activeGlassSceneState, temporaryClipActive, transitionResourceId) {
                if (temporaryClipActive) {
                    activeGlassSceneState.acquireTemporaryResource(transitionResourceId)
                }
                onDispose {
                    if (temporaryClipActive) {
                        activeGlassSceneState.releaseTemporaryResource(transitionResourceId)
                    }
                }
            }
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
        HomeMenuDestinationTransitionShell(
            useStableEnvelope = useStableEnvelope,
            envelope = transitionEnvelope,
            geometry = transitionGeometry,
            referenceRect = { frame.value.geometry.rect },
            targetSizePx = targetSizePx,
            targetWidth = targetWidth,
            targetHeight = targetHeight,
            temporaryClipActive = temporaryClipActive,
            clipStableEndpoint = !isFullScreen,
            collapseHandedOff = collapseHandedOff,
            destinationShape = destinationShape,
            destinationTestTag = destinationTestTag
        ) {
            CompositionLocalProvider(
                LocalDetailActivityFloatingOverlayHost provides floatingOverlayHost
            ) {
            val lightGlass = glassUsesLightStyle(state.config)
            // Keep the material composed throughout Preparing and Opening. Mounting drawBackdrop
            // at the same frame the cached form hands off can flash text fields on some GPUs.
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
                    blurRadius = 10.dp,
                    lensHeight = 12.dp,
                    lensAmount = 16.dp
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
                            if (phase == HomeAnchoredOverlayPhase.Preparing &&
                                destinationContentRecorded.compareAndSet(false, true)
                            ) {
                                destinationContentLayer.record {
                                    this@drawWithContent.drawContent()
                                }
                            }
                            if (phase == HomeAnchoredOverlayPhase.Open) {
                                // Open is interactive. Keep the real form live and avoid recording
                                // the full destination tree on every frame.
                                this@drawWithContent.drawContent()
                            } else {
                                if (phase == HomeAnchoredOverlayPhase.Opening &&
                                    destinationContentRecorded.compareAndSet(false, true)
                                ) {
                                    destinationContentLayer.record {
                                        this@drawWithContent.drawContent()
                                    }
                                }
                                if (phase == HomeAnchoredOverlayPhase.Closing &&
                                    destinationClosingRecorded.compareAndSet(false, true)
                                ) {
                                    destinationContentLayer.record {
                                        this@drawWithContent.drawContent()
                                    }
                                }
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
                            // This destination itself lives inside the root centered-dialog
                                // producer. Rendering its picker in this nested Scaffold would put
                                // the consumer back inside the producer it samples and create a
                                // RenderNode cycle. The home root host is the first sibling outside
                                // that producer and is therefore the authoritative picker host.
                                pickerRenderInRootScaffold = true
                            )
                        }
                        HomeMenuDestinationKind.ManualImport -> NormalizedAiManualImportScreen(
                            state = state,
                            backdrop = backdrop,
                            onCancel = { latestDismiss() },
                            captureHistoryBackground = captureHistoryBackground,
                            onParsed = onManualImportParsed
                        )
                        HomeMenuDestinationKind.EduImport -> DetailActivityScaffold(
                            title = "选择学校",
                            config = state.config,
                            onBack = { latestDismiss() }
                        ) { schoolBackdrop ->
                            EduSchoolPickerScreen(
                                state = state,
                                backdrop = schoolBackdrop,
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
        }
        if (motionState.phase != HomeAnchoredOverlayPhase.Idle &&
            motionState.phase != HomeAnchoredOverlayPhase.Disposing
        ) {
            floatingOverlayHost.content?.let { overlayContent ->
                // Keep the search dock outside the anchored destination shell so its press/IME
                // overscan cannot be clipped by the morph envelope or the destination card. Mount
                // it during Preparing/Opening as well; otherwise the host only receives a draw
                // slot after Open and the dock flashes in on the last transition frame.
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { clip = false }
                        .zIndex(1000f)
                ) {
                    val destinationAlpha = frame.value.destinationContentAlpha
                    Box(
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                // Full-screen EduImport content begins its handoff shortly after
                                // progress starts. Reuse that exact reveal curve so the floating
                                // dock becomes visible during the morph and reaches full opacity
                                // with the destination, instead of appearing only at Open.
                                alpha = if (isFullScreen &&
                                    motionState.phase != HomeAnchoredOverlayPhase.Closing
                                ) {
                                    destinationAlpha
                                } else {
                                    1f
                                }
                                clip = false
                            }
                    ) {
                        overlayContent()
                    }
                }
            }
        }
    }
}
