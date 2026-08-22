package com.xiaomanjun.sleepdownschedule

import android.content.Intent
import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.catalog.components.LiquidPanel
import com.kyant.shapes.RoundedRectangle
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin
import androidx.compose.ui.graphics.rememberGraphicsLayer

private const val AnchoredMorphSnapshotTokenExtra = "anchored_morph_snapshot_token"
private const val CourseManagementDetailOpenDurationMillis = 380
private const val CourseManagementDetailCloseDurationMillis = 440
private val CourseManagementDetailOpenPositionEasing =
    CubicBezierEasing(0.18f, 0.72f, 0.18f, 1.0f)
private val CourseManagementDetailOpenSizeEasing =
    CubicBezierEasing(0.22f, 0.62f, 0.22f, 1.0f)
private val CourseManagementDetailClosePositionEasing =
    CubicBezierEasing(0.30f, 0.10f, 0.22f, 1.0f)

internal enum class AnchoredDetailMotionStyle {
    Liquid,
    DetailSettings,
    Parabolic,
    CourseManagementDetail,
    HomeMenuDestination
}

internal data class AnchoredMorphSnapshots(
    val background: Bitmap,
    val source: Bitmap? = null,
    val collapse: Bitmap? = null
)

internal fun detailMorphUsesTransientClip(progress: Float, closing: Boolean): Boolean =
    closing || progress < 0.999f

internal object AnchoredMorphSnapshotStore {
    private val nextToken = AtomicLong(1L)
    private val snapshots = ConcurrentHashMap<Long, AnchoredMorphSnapshots>()

    fun put(value: AnchoredMorphSnapshots): Long {
        val token = nextToken.getAndIncrement()
        snapshots[token] = value
        if (snapshots.size > 6) {
            snapshots.keys.sorted().dropLast(6).forEach(snapshots::remove)
        }
        return token
    }

    fun get(token: Long?): AnchoredMorphSnapshots? = token?.let(snapshots::get)

    fun remove(token: Long?) {
        token?.let(snapshots::remove)
    }
}

internal fun Intent.putAnchoredMorphSnapshots(value: AnchoredMorphSnapshots): Intent = apply {
    putExtra(AnchoredMorphSnapshotTokenExtra, AnchoredMorphSnapshotStore.put(value))
}

internal fun Intent.anchoredMorphSnapshotTokenOrNull(): Long? =
    getLongExtra(AnchoredMorphSnapshotTokenExtra, 0L).takeIf { it > 0L }

private data class AnchoredDetailMorphValues(
    val backgroundAlpha: Float,
    val sourceAlpha: Float,
    val contentAlpha: Float,
    val translationX: Float,
    val translationY: Float,
    val scale: Float,
    val clipBottom: Float,
    val progress: Float
)

/**
 * The history/detail Morph uses one moving layer whose outline follows the same values as its
 * scale and translation. Keeping the outline on that layer preserves the source footprint at
 * progress zero and avoids the one-frame rectangular flash caused by a separately drawn path.
 */
private class AnchoredDetailClipShape(
    private val screenWidth: Float,
    private val screenCornerRadiusPx: Float,
    private val sourceCornerRadiusPx: Float,
    private val values: State<AnchoredDetailMorphValues>
) : Shape {
    override fun createOutline(
        size: androidx.compose.ui.geometry.Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val current = values.value
        val radiusPx = sourceCornerRadiusPx +
            (screenCornerRadiusPx - sourceCornerRadiusPx) * current.progress
        val compensatedRadius =
            (radiusPx / current.scale.coerceAtLeast(0.001f) / density.density).dp
        return RoundedCornerShape(compensatedRadius).createOutline(
            size = androidx.compose.ui.geometry.Size(
                screenWidth,
                current.clipBottom.coerceAtLeast(1f)
            ),
            layoutDirection = layoutDirection,
            density = density
        )
    }
}

internal fun anchoredStableContentOffsetPx(
    positionInRootPx: Float,
    shellStartPx: Float,
    rootExtentPx: Float,
    shellExtentPx: Float
): Float = positionInRootPx - shellStartPx + (rootExtentPx - shellExtentPx) / 2f

/** Controls only ownership of the opening; the existing renderer and all motion values stay intact. */
internal enum class AnchoredDetailOpeningMode {
    AnimateLegacy,
    HoldSourceFrame,
    ShowDestination
}

@Composable
internal fun AnchoredDetailActivityMorph(
    sourceBounds: Rect?,
    collapseBounds: Rect? = null,
    sourceCornerRadius: Dp,
    collapseCornerRadius: Dp = sourceCornerRadius,
    onFinished: () -> Unit,
    onSourceHandoff: () -> Unit = {},
    sourceContent: @Composable BoxScope.() -> Unit,
    backgroundSnapshot: Bitmap? = null,
    sourceSnapshot: Bitmap? = null,
    collapseSnapshot: Bitmap? = null,
    motionStyle: AnchoredDetailMotionStyle = AnchoredDetailMotionStyle.Liquid,
    destinationFirstOpening: Boolean = false,
    suppressOpening: Boolean = false,
    openingMode: AnchoredDetailOpeningMode = if (suppressOpening) {
        AnchoredDetailOpeningMode.ShowDestination
    } else {
        AnchoredDetailOpeningMode.AnimateLegacy
    },
    onOpened: () -> Unit = {},
    onCloseRequested: (() -> Boolean)? = null,
    content: @Composable (requestClose: () -> Unit) -> Unit
) {
    var rootSize by remember { mutableStateOf(IntSize.Zero) }
    val progress = remember(sourceBounds) {
        Animatable(
            if (sourceBounds == null || openingMode == AnchoredDetailOpeningMode.ShowDestination) {
                1f
            } else {
                0f
            }
        )
    }
    val backgroundScale = remember(backgroundSnapshot) { Animatable(1f) }
    var closing by remember(sourceBounds) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val usesCourseEditorMotion = motionStyle == AnchoredDetailMotionStyle.CourseManagementDetail
    val usesPageMotion = motionStyle == AnchoredDetailMotionStyle.DetailSettings ||
        motionStyle == AnchoredDetailMotionStyle.Parabolic ||
        usesCourseEditorMotion
    val usesHomeMenuDestinationMotion = motionStyle == AnchoredDetailMotionStyle.HomeMenuDestination
    val usesDestinationFirstOpening = usesHomeMenuDestinationMotion && destinationFirstOpening
    val snapshotBackdrop = rememberLayerBackdrop()
    val bypassLegacyOpening = openingMode == AnchoredDetailOpeningMode.ShowDestination
    val renderedProgress = if (bypassLegacyOpening) 1f else progress.value

    fun close() {
        if (closing) return
        if (onCloseRequested?.invoke() == true) return
        if (sourceBounds == null) {
            onFinished()
            return
        }
        closing = true
        scope.launch {
            if (openingMode == AnchoredDetailOpeningMode.ShowDestination && progress.value < 1f) {
                progress.snapTo(1f)
            }
            coroutineScope {
                launch {
                    backgroundScale.animateTo(
                        1f,
                        if (usesHomeMenuDestinationMotion) {
                            tween(
                                HomeMenuDestinationCloseDurationMillis,
                                easing = HomeAnchoredBackgroundEasing
                            )
                        } else {
                            tween(BACKGROUND_EXIT_DURATION, easing = BackgroundExitEasing)
                        }
                    )
                }
                launch {
                    progress.animateTo(
                        0f,
                        if (usesHomeMenuDestinationMotion) {
                            tween(HomeMenuDestinationCloseDurationMillis, easing = LinearEasing)
                        } else if (usesPageMotion) {
                            tween(DETAIL_SYSTEM_BACK_DURATION, easing = DetailExitEasing)
                        } else {
                            tween(HomeAnchoredMorphCloseDurationMillis, easing = LinearEasing)
                        }
                    )
                }
            }
            onFinished()
        }
    }

    BackHandler(onBack = ::close)
    LaunchedEffect(sourceBounds, rootSize, motionStyle, openingMode) {
        if (openingMode == AnchoredDetailOpeningMode.ShowDestination) {
            if (progress.value < 1f) progress.snapTo(1f)
        } else if (
            openingMode == AnchoredDetailOpeningMode.AnimateLegacy &&
            sourceBounds != null && rootSize.width > 0 && rootSize.height > 0 &&
            progress.value < 1f
        ) {
            // Precompose the destination before motion starts. This matches the detailed-settings
            // transition and avoids a backdrop-heavy page entering composition mid-animation.
            withFrameNanos { }
            withFrameNanos { }
            onSourceHandoff()
            coroutineScope {
                launch {
                    backgroundScale.animateTo(
                        if (usesHomeMenuDestinationMotion) {
                            HomeMenuDestinationEduBackgroundScale
                        } else {
                            0.92f
                        },
                        if (usesHomeMenuDestinationMotion) {
                            tween(
                                durationMillis = 420,
                                delayMillis = HomeAnchoredMorphBackgroundDelayMillis,
                                easing = HomeAnchoredBackgroundEasing
                            )
                        } else {
                            tween(BACKGROUND_OPEN_DURATION, easing = BackgroundOpenEasing)
                        }
                    )
                }
                launch {
                    progress.animateTo(
                        1f,
                        if (usesHomeMenuDestinationMotion) {
                            tween(
                                HomeMenuDestinationLegacyMotion.OpenDurationMillis,
                                easing = LinearEasing
                            )
                        } else if (usesPageMotion) {
                            tween(DETAIL_OPEN_DURATION, easing = DetailOpenEasing)
                        } else {
                            tween(HomeAnchoredMorphOpenDurationMillis, easing = LinearEasing)
                        }
                    )
                }
            }
            onOpened()
        } else if (sourceBounds == null) {
            onOpened()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { rootSize = it }
    ) {
        backgroundSnapshot?.takeIf { !bypassLegacyOpening || closing }?.let { bitmap ->
            Box(Modifier.fillMaxSize().layerBackdrop(snapshotBackdrop)) {
                MorphSnapshotBackground(
                    bitmap = bitmap,
                    backgroundScaleProvider = { backgroundScale.value },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        if (rootSize.width <= 0 || rootSize.height <= 0) {
            return@Box
        }
        if (usesHomeMenuDestinationMotion) {
            AnchoredHomeMenuDestinationStyleMorph(
                sourceBounds = sourceBounds,
                collapseBounds = collapseBounds ?: sourceBounds,
                sourceCornerRadius = sourceCornerRadius,
                collapseCornerRadius = collapseCornerRadius,
                rootSize = rootSize,
                sourceSnapshot = sourceSnapshot.takeIf { !bypassLegacyOpening || closing },
                collapseSnapshot = collapseSnapshot.takeIf { !bypassLegacyOpening || closing },
                backdrop = snapshotBackdrop.takeIf {
                    backgroundSnapshot != null && (!bypassLegacyOpening || closing)
                },
                progress = renderedProgress,
                closing = closing,
                destinationFirstOpening = usesDestinationFirstOpening,
                onClose = ::close,
                sourceContent = sourceContent,
                content = content
            )
        } else if (usesPageMotion) {
            AnchoredSettingsStyleMorph(
                sourceBounds = if (closing && collapseBounds != null) collapseBounds else sourceBounds,
                sourceCornerRadius = if (closing && collapseBounds != null) collapseCornerRadius else sourceCornerRadius,
                sourceSnapshot = if (closing && collapseBounds != null) {
                    collapseSnapshot ?: sourceSnapshot
                } else {
                    sourceSnapshot
                }.takeIf { !bypassLegacyOpening || closing },
                progress = renderedProgress,
                closing = closing,
                parabolic = motionStyle == AnchoredDetailMotionStyle.Parabolic || usesCourseEditorMotion,
                openingDownward = false,
                homeCourseParabola = usesCourseEditorMotion,
                onClose = ::close,
                sourceContent = sourceContent,
                content = content
            )
        } else {
            AnchoredLiquidStyleMorph(
                sourceBounds = sourceBounds,
                rootSize = rootSize,
                sourceCornerRadius = sourceCornerRadius,
                sourceSnapshot = sourceSnapshot.takeIf { !bypassLegacyOpening || closing },
                progress = renderedProgress,
                closing = closing,
                onClose = ::close,
                sourceContent = sourceContent,
                content = content
            )
        }
    }
}

/**
 * Activity counterpart of HomeMenuDestinationOverlayHost's full-screen Edu-import motion.
 * Geometry, timing, source handoff and close anchor intentionally call the same helpers instead of
 * approximating that transition with the generic settings-page scale/clip animation.
 */
@Composable
private fun BoxScope.AnchoredHomeMenuDestinationStyleMorph(
    sourceBounds: Rect?,
    collapseBounds: Rect?,
    sourceCornerRadius: Dp,
    collapseCornerRadius: Dp,
    rootSize: IntSize,
    sourceSnapshot: Bitmap?,
    collapseSnapshot: Bitmap?,
    backdrop: Backdrop?,
    progress: Float,
    closing: Boolean,
    destinationFirstOpening: Boolean,
    onClose: () -> Unit,
    sourceContent: @Composable BoxScope.() -> Unit,
    content: @Composable (requestClose: () -> Unit) -> Unit
) {
    val density = LocalDensity.current
    val adaptiveMetrics = rememberHomeAdaptiveMetrics()
    val full = Rect(0f, 0f, rootSize.width.toFloat(), rootSize.height.toFloat())
    val source = sourceBounds ?: full
    val collapse = collapseBounds ?: source
    val p = progress.coerceIn(0f, 1f)
    val activityDarkSurface =
        androidx.compose.material3.MaterialTheme.colorScheme.background.luminance() < 0.5f
    val geometry = homeMenuDestinationTrajectoryGeometry(
        sourceBoundsInRoot = source,
        collapseBoundsInRoot = collapse,
        target = full,
        rawProgress = p,
        closing = closing,
        // The Activity receives the real bounds from the source window. Use the exact corner
        // radii supplied by that source instead of the in-process menu defaults; otherwise the
        // first handoff clips the source high-light edge before the destination takes ownership.
        menuCornerRadiusPx = with(density) { sourceCornerRadius.toPx() },
        buttonCornerRadiusPx = with(density) { collapseCornerRadius.toPx() },
        pinchDiameterPx = with(density) { 18.dp.toPx() },
        minimumDropPx = with(density) { 12.dp.toPx() },
        maximumDropPx = with(density) { adaptiveMetrics.animationArc.toPx() },
        maximumArcPx = with(density) { adaptiveMetrics.animationArc.toPx() + 16.dp.toPx() },
        targetCornerRadiusPx = 0f
    )
    val fullOpenEndpoint = !detailMorphUsesTransientClip(p, closing)
    val renderedCornerRadiusPx = homeMenuDestinationRenderedCornerRadiusPx(
        geometry = geometry,
        rawProgress = p,
        isFullScreen = true,
        closing = closing,
        sourceCornerRadiusPx = with(density) { sourceCornerRadius.toPx() },
        collapseCornerRadiusPx = with(density) { collapseCornerRadius.toPx() },
        middleCornerRadiusPx = with(density) { 46.dp.toPx() }
    )
    val sourceAlpha = when {
        closing || destinationFirstOpening -> 0f
        else -> 1f - anchoredDestinationSmoothStep(0.035f, 0.20f, p)
    }
    val collapseAlpha = if (closing && collapseSnapshot != null) {
        1f - anchoredDestinationSmoothStep(0.06f, 0.18f, p)
    } else {
        0f
    }
    val destinationAlpha = if (destinationFirstOpening && !closing) {
        1f
    } else {
        homeMenuDestinationContentAlpha(
            rawProgress = p,
            isFullScreen = true,
            closing = closing
        )
    }
    // Course management uses the same destination geometry as Edu import, but its entry begins
    // with the already-composed destination clipped inside the source shell. The original menu is
    // hidden before the Activity starts, so replaying it here causes the visible button/menu blink.
    val destinationSurfaceAlpha = if (destinationFirstOpening && !closing) 1f else 1f - sourceAlpha
    val maxContentBlurPx = with(density) { 5.dp.toPx() }
    val destinationBlurMix = if (destinationFirstOpening && !closing) {
        0f
    } else if (closing) {
        val closeElapsed = 1f - p
        anchoredDestinationSmoothStep(0.48f, 0.84f, closeElapsed)
    } else {
        homeMenuDestinationOpeningContentBlurMix(
            rawProgress = p,
            isFullScreen = true
        )
    }
    val destinationContentLayer = rememberGraphicsLayer()
    val destinationContentRecorded = remember { AtomicBoolean(false) }
    val destinationClosingRecorded = remember { AtomicBoolean(false) }
    // Keep the Activity handoff on the same continuous rounded-rectangle family as the
    // in-process HomeMenuDestination shell. A platform RoundedCornerShape has a different
    // curvature and visibly crops the source highlight at the handoff even with the same radius.
    val shellShape = RoundedRectangle(with(density) { renderedCornerRadiusPx.toDp() })

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(closing) {
                awaitPointerEventScope {
                    while (true) awaitPointerEvent().changes.forEach { it.consume() }
                }
            }
    )
    Box(
        modifier = Modifier
            .offset {
                IntOffset(geometry.rect.left.roundToInt(), geometry.rect.top.roundToInt())
            }
            .graphicsLayer {
                // Releasing clip/offscreen composition at the full-screen endpoint is essential:
                // keeping the transient shell clip is what made the real course page look cropped.
                clip = !fullOpenEndpoint
                shape = shellShape
                compositingStrategy = if (fullOpenEndpoint) {
                    CompositingStrategy.Auto
                } else {
                    CompositingStrategy.Offscreen
                }
            }
            .layout { measurable, _ ->
                val stableWidth = rootSize.width.coerceAtLeast(1)
                val stableHeight = rootSize.height.coerceAtLeast(1)
                val placeable = measurable.measure(Constraints.fixed(stableWidth, stableHeight))
                val width = geometry.rect.width.roundToInt().coerceAtLeast(1)
                val height = geometry.rect.height.roundToInt().coerceAtLeast(1)
                layout(width, height) {
                    placeable.place((width - stableWidth) / 2, (height - stableHeight) / 2)
                }
            }
    ) {
        // Keep the same surface/source/content ordering as the in-process Home menu destination.
        // The Activity has a clean background snapshot instead of the original Backdrop producer.
        // Preserve the in-process LiquidPanel tint alpha over that snapshot; using the opaque
        // theme background here made the otherwise identical geometry flash as a black rectangle.
        val destinationSurfaceColor = if (activityDarkSurface) {
            Color(0xFF121212).copy(alpha = 0.30f)
        } else {
            Color.White.copy(alpha = 0.18f)
        }
        if (backdrop != null) {
            LiquidPanel(
                backdrop = backdrop,
                modifier = Modifier.fillMaxSize().graphicsLayer { alpha = destinationSurfaceAlpha },
                shape = shellShape,
                surfaceColor = destinationSurfaceColor,
                blurRadius = 10.dp,
                lensHeight = 12.dp,
                lensAmount = 16.dp
            ) { }
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = destinationSurfaceAlpha }
                    .background(destinationSurfaceColor)
            )
        }
        if (sourceAlpha > 0.001f) {
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            ((rootSize.width - geometry.rect.width) / 2f).roundToInt(),
                            ((rootSize.height - geometry.rect.height) / 2f).roundToInt()
                        )
                    }
                    .size(
                        with(density) { geometry.rect.width.toDp() },
                        with(density) { geometry.rect.height.toDp() }
                    )
                    .graphicsLayer {
                        alpha = sourceAlpha
                        renderEffect = if (p < 0.999f) {
                            platformBlurRenderEffect(
                                anchoredDestinationSmoothStep(0f, 0.035f, p) *
                                    maxContentBlurPx
                            )
                        } else {
                            null
                        }
                    }
            ) {
                if (sourceSnapshot != null) {
                    Image(
                        bitmap = sourceSnapshot.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.FillBounds
                    )
                } else {
                    sourceContent()
                }
            }
        }
        if (collapseAlpha > 0.001f && collapseSnapshot != null) {
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            anchoredStableContentOffsetPx(
                                positionInRootPx = collapse.left,
                                shellStartPx = geometry.rect.left,
                                rootExtentPx = rootSize.width.toFloat(),
                                shellExtentPx = geometry.rect.width
                            ).roundToInt(),
                            anchoredStableContentOffsetPx(
                                positionInRootPx = collapse.top,
                                shellStartPx = geometry.rect.top,
                                rootExtentPx = rootSize.height.toFloat(),
                                shellExtentPx = geometry.rect.height
                            ).roundToInt()
                        )
                    }
                    .size(
                        with(density) { collapse.width.toDp() },
                        with(density) { collapse.height.toDp() }
                    )
                    .graphicsLayer {
                        alpha = collapseAlpha
                    }
            ) {
                Image(
                    bitmap = collapseSnapshot.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillBounds
                )
            }
        }
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .drawWithContent {
                        val moving = !fullOpenEndpoint
                        if (!moving) {
                            drawContent()
                        } else {
                            val shouldRecord = if (closing) {
                                destinationClosingRecorded.compareAndSet(false, true)
                            } else {
                                destinationContentRecorded.compareAndSet(false, true)
                            }
                            if (shouldRecord) {
                                destinationContentLayer.record {
                                    this@drawWithContent.drawContent()
                                }
                            }
                            drawLayer(destinationContentLayer)
                        }
                    }
                    .graphicsLayer {
                        alpha = destinationAlpha * (1f - destinationBlurMix)
                    }
            ) {
                content(onClose)
            }
            if (!fullOpenEndpoint && destinationBlurMix > 0.001f) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .drawWithContent { drawLayer(destinationContentLayer) }
                        .graphicsLayer {
                            alpha = destinationAlpha * destinationBlurMix
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
    }
}

private fun anchoredDestinationSmoothStep(edge0: Float, edge1: Float, value: Float): Float {
    val x = ((value - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}

private data class CourseManagementDetailGeometry(
    val rect: Rect,
    val positionProgress: Float,
    val sizeProgress: Float
)

private fun courseManagementDetailTrajectoryGeometry(
    source: Rect,
    target: Rect,
    rawProgress: Float,
    closing: Boolean,
    maxArcPx: Float
): CourseManagementDetailGeometry {
    val raw = rawProgress.coerceIn(0f, 1f)
    val positionProgress: Float
    val sizeProgress: Float
    val pulseScale: Float
    if (closing) {
        val closingElapsed = 1f - raw
        val closingPosition = CourseManagementDetailClosePositionEasing.transform(closingElapsed)
        val advancedClosingSize = (closingElapsed / 0.78f).coerceIn(0f, 1f)
        val closingSize = CourseManagementDetailOpenSizeEasing.transform(advancedClosingSize)
        positionProgress = (1f - closingPosition).coerceIn(0f, 1f)
        sizeProgress = (1f - closingSize).coerceIn(0f, 1f)
        val pulse = ((closingElapsed - 0.78f) / 0.22f).coerceIn(0f, 1f)
        pulseScale = 1f + sin(PI.toFloat() * pulse) * 0.008f
    } else {
        val delayedSizeProgress = ((raw - 0.10f) / 0.90f).coerceIn(0f, 1f)
        positionProgress =
            CourseManagementDetailOpenPositionEasing.transform(raw).coerceIn(0f, 1f)
        sizeProgress = CourseManagementDetailOpenSizeEasing
            .transform(delayedSizeProgress)
            .coerceIn(0f, 1f)
        val pulse = ((raw - 0.78f) / 0.22f).coerceIn(0f, 1f)
        pulseScale = 1f + sin(PI.toFloat() * pulse) * 0.008f
    }

    val sourceCenter = source.center
    val targetCenter = target.center
    val deltaY = targetCenter.y - sourceCenter.y
    val arcAmplitude = min(maxArcPx, abs(deltaY) * 0.22f)
    val controlX = (sourceCenter.x + targetCenter.x) / 2f
    val controlY = (sourceCenter.y + targetCenter.y) / 2f + sign(deltaY) * arcAmplitude
    val inverse = 1f - positionProgress
    val centerX = inverse * inverse * sourceCenter.x +
        2f * inverse * positionProgress * controlX +
        positionProgress * positionProgress * targetCenter.x
    val centerY = inverse * inverse * sourceCenter.y +
        2f * inverse * positionProgress * controlY +
        positionProgress * positionProgress * targetCenter.y
    val width = (source.width + (target.width - source.width) * sizeProgress) * pulseScale
    val height = (source.height + (target.height - source.height) * sizeProgress) * pulseScale
    return CourseManagementDetailGeometry(
        rect = Rect(
            left = centerX - width / 2f,
            top = centerY - height / 2f,
            right = centerX + width / 2f,
            bottom = centerY + height / 2f
        ),
        positionProgress = positionProgress,
        sizeProgress = sizeProgress
    )
}

/**
 * Course-management copy of the Home course-card editor Morph.
 *
 * This intentionally owns a separate implementation: a card above the destination center first
 * arcs down while growing and a card below it follows the opposite arc, without coupling future
 * Home editor tuning to this Activity transition.
 */
@Composable
private fun BoxScope.AnchoredCourseEditorStyleMorph(
    sourceBounds: Rect?,
    sourceCornerRadius: Dp,
    rootSize: IntSize,
    sourceSnapshot: Bitmap?,
    progress: Float,
    closing: Boolean,
    onClose: () -> Unit,
    sourceContent: @Composable BoxScope.() -> Unit,
    content: @Composable (requestClose: () -> Unit) -> Unit
) {
    val density = LocalDensity.current
    val adaptiveMetrics = rememberHomeAdaptiveMetrics()
    val target = Rect(0f, 0f, rootSize.width.toFloat(), rootSize.height.toFloat())
    val source = sourceBounds ?: target
    val p = progress.coerceIn(0f, 1f)
    val geometry = courseManagementDetailTrajectoryGeometry(
        source = source,
        target = target,
        rawProgress = p,
        closing = closing,
        maxArcPx = with(density) { adaptiveMetrics.animationArc.toPx() }
    )
    val cornerProgress = anchoredDestinationSmoothStep(0.04f, 0.90f, geometry.sizeProgress)
    val sourceCornerPx = with(density) { sourceCornerRadius.toPx() }
    val targetCornerPx = deviceScreenCornerRadiusPx()
    val cornerPx = sourceCornerPx + (targetCornerPx - sourceCornerPx) * cornerProgress
    val shellShape = RoundedRectangle(with(density) { cornerPx.toDp() })
    val fullOpenEndpoint = !detailMorphUsesTransientClip(p, closing)
    val contentAlpha = if (closing) {
        anchoredDestinationSmoothStep(0.04f, 0.18f, geometry.positionProgress)
    } else {
        anchoredDestinationSmoothStep(0.12f, 0.50f, geometry.positionProgress)
    }
    val sourceAlpha = 1f - contentAlpha
    val sourceBlurPx = with(density) {
        6.dp.toPx() * if (closing) {
            anchoredDestinationSmoothStep(0f, 0.12f, geometry.positionProgress)
        } else {
            anchoredDestinationSmoothStep(0f, 0.24f, geometry.positionProgress)
        }
    }
    val destinationBlurPx = if (fullOpenEndpoint) {
        0f
    } else {
        with(density) {
            5.dp.toPx() * if (closing) {
                1f
            } else {
                1f - anchoredDestinationSmoothStep(0.90f, 1f, geometry.positionProgress)
            }
        }
    }
    // The source snapshot is a cropped card bitmap. Keep it at its real footprint while the
    // surrounding glass shell grows and follows the trajectory; putting the bitmap in a
    // fillMaxSize child made its title and schedule rows stretch with the shell.
    val sourceOffsetInShell = IntOffset(
        (source.left - geometry.rect.left).roundToInt(),
        (source.top - geometry.rect.top).roundToInt()
    )
    val sourceWidthDp = with(density) { source.width.toDp() }
    val sourceHeightDp = with(density) { source.height.toDp() }

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(closing) {
                awaitPointerEventScope {
                    while (true) awaitPointerEvent().changes.forEach { it.consume() }
                }
            }
    )
    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    geometry.rect.left.roundToInt(),
                    geometry.rect.top.roundToInt()
                )
            }
            .graphicsLayer {
                clip = !fullOpenEndpoint
                shape = shellShape
                compositingStrategy = if (fullOpenEndpoint) {
                    CompositingStrategy.Auto
                } else {
                    CompositingStrategy.Offscreen
                }
            }
            .layout { measurable, _ ->
                val stableWidth = rootSize.width.coerceAtLeast(1)
                val stableHeight = rootSize.height.coerceAtLeast(1)
                val placeable = measurable.measure(Constraints.fixed(stableWidth, stableHeight))
                val width = geometry.rect.width.roundToInt().coerceAtLeast(1)
                val height = geometry.rect.height.roundToInt().coerceAtLeast(1)
                layout(width, height) {
                    placeable.place((width - stableWidth) / 2, (height - stableHeight) / 2)
                }
            }
    ) {
        if (sourceAlpha > 0.001f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = sourceAlpha
                        compositingStrategy = if (sourceBlurPx > 0.01f) {
                            CompositingStrategy.Offscreen
                        } else {
                            CompositingStrategy.Auto
                        }
                        renderEffect = platformBlurRenderEffect(sourceBlurPx)
                }
            ) {
                Box(
                    Modifier
                        .offset { sourceOffsetInShell }
                        .size(sourceWidthDp, sourceHeightDp)
                ) {
                    if (sourceSnapshot != null) {
                        Image(
                            bitmap = sourceSnapshot.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.FillBounds
                        )
                    } else {
                        sourceContent()
                    }
                }
            }
        }
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = contentAlpha
                    compositingStrategy = if (destinationBlurPx > 0.01f) {
                        CompositingStrategy.Offscreen
                    } else {
                        CompositingStrategy.Auto
                    }
                    renderEffect = platformBlurRenderEffect(destinationBlurPx)
                }
        ) {
            content(onClose)
        }
    }
}

@Composable
private fun BoxScope.AnchoredLiquidStyleMorph(
    sourceBounds: Rect?,
    rootSize: IntSize,
    sourceCornerRadius: Dp,
    sourceSnapshot: Bitmap?,
    progress: Float,
    closing: Boolean,
    onClose: () -> Unit,
    sourceContent: @Composable BoxScope.() -> Unit,
    content: @Composable (requestClose: () -> Unit) -> Unit
) {
    val density = LocalDensity.current
    val full = Rect(0f, 0f, rootSize.width.toFloat(), rootSize.height.toFloat())
    val source = sourceBounds ?: full
    val targetCornerRadiusPx = deviceScreenCornerRadiusPx()
    val geometry = homeAnchoredMorphGeometry(
        source = source,
        target = full,
        rawProgress = progress,
        closing = closing,
        sourceCornerRadiusPx = with(density) { sourceCornerRadius.toPx() },
        pinchDiameterPx = with(density) { 22.dp.toPx() },
        minimumDropPx = with(density) { 10.dp.toPx() },
        maximumDropPx = with(density) { 58.dp.toPx() },
        maximumArcPx = with(density) { 48.dp.toPx() },
        targetCornerRadiusPx = targetCornerRadiusPx,
        motionStyle = HomeMorphEasingStyle.Legacy
    )
    val fullOpenEndpoint = !detailMorphUsesTransientClip(progress, closing)
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.24f * geometry.expansionProgress))
            .pointerInput(closing) {
                awaitPointerEventScope {
                    while (true) awaitPointerEvent().changes.forEach { it.consume() }
                }
            }
    )
    Box(
        Modifier
            .offset {
                IntOffset(geometry.rect.left.roundToInt(), geometry.rect.top.roundToInt())
            }
            .size(
                with(density) { geometry.rect.width.toDp() },
                with(density) { geometry.rect.height.toDp() }
            )
            .graphicsLayer {
                clip = !fullOpenEndpoint
                shape = RoundedCornerShape(with(density) { geometry.cornerRadiusPx.toDp() })
                compositingStrategy = if (fullOpenEndpoint) {
                    CompositingStrategy.Auto
                } else {
                    CompositingStrategy.Offscreen
                }
                renderEffect = if (fullOpenEndpoint) {
                    null
                } else {
                    platformBlurRenderEffect(
                        detailMotionBlurRadiusDp(geometry.expansionProgress) * density.density
                    )
                }
            }
    ) {
        if (geometry.sourceAlpha > 0.001f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = geometry.sourceAlpha
                        scaleX = geometry.sourceScale
                        scaleY = geometry.sourceScale
                        shape = RoundedCornerShape(sourceCornerRadius)
                        clip = true
                    }
            ) {
                if (sourceSnapshot != null) {
                    Image(
                        bitmap = sourceSnapshot.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.FillBounds
                    )
                } else {
                    sourceContent()
                }
            }
        }
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = geometry.contentAlpha }) {
            content(onClose)
        }
    }
}

@Composable
private fun BoxScope.AnchoredSettingsStyleMorph(
    sourceBounds: Rect?,
    sourceCornerRadius: Dp,
    sourceSnapshot: Bitmap?,
    progress: Float,
    closing: Boolean,
    parabolic: Boolean,
    openingDownward: Boolean,
    homeCourseParabola: Boolean = false,
    onClose: () -> Unit,
    sourceContent: @Composable BoxScope.() -> Unit,
    content: @Composable (requestClose: () -> Unit) -> Unit
) {
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val screenWidth = with(density) { maxWidth.toPx() }
        val screenHeight = with(density) { maxHeight.toPx() }
        val source = sourceBounds ?: Rect(0f, 0f, screenWidth, screenHeight)
        val initialScale = (source.width / screenWidth).coerceAtLeast(0.001f)
        val p = progress.coerceIn(0f, 1f)
        val scale = initialScale + (1f - initialScale) * p
        val sourceCenterX = source.left + source.width / 2f
        val targetCornerRadiusPx = deviceScreenCornerRadiusPx()
        val arcHeight = if (parabolic) {
            minOf(screenHeight * 0.10f, with(density) { 96.dp.toPx() })
        } else {
            0f
        }
        val initialClipBottom = source.height / initialScale
        val clipBottom = initialClipBottom + (screenHeight - initialClipBottom) * p
        val shellHeight = clipBottom * scale
        val homePathCenter = if (homeCourseParabola) {
            val sourceCenter = source.center
            val targetCenter = androidx.compose.ui.geometry.Offset(screenWidth / 2f, screenHeight / 2f)
            val deltaY = targetCenter.y - sourceCenter.y
            val arcAmplitude = min(
                with(density) { 96.dp.toPx() },
                abs(deltaY) * 0.22f
            )
            val control = androidx.compose.ui.geometry.Offset(
                x = (sourceCenter.x + targetCenter.x) / 2f,
                y = (sourceCenter.y + targetCenter.y) / 2f + sign(deltaY) * arcAmplitude
            )
            val inverse = 1f - p
            androidx.compose.ui.geometry.Offset(
                x = inverse * inverse * sourceCenter.x +
                    2f * inverse * p * control.x + p * p * targetCenter.x,
                y = inverse * inverse * sourceCenter.y +
                    2f * inverse * p * control.y + p * p * targetCenter.y
            )
        } else {
            null
        }
        val values = AnchoredDetailMorphValues(
            backgroundAlpha = (p * 0.22f).coerceIn(0f, 0.22f),
            sourceAlpha = (1f - p * 3f).coerceIn(0f, 1f),
            contentAlpha = ((p - 0.1f) / 0.5f).coerceIn(0f, 1f),
            translationX = homePathCenter?.let { it.x - screenWidth / 2f }
                ?: ((sourceCenterX - screenWidth / 2f) * (1f - p)),
            // A quadratic arc keeps both endpoints exact while lifting the page through the
            // middle of the transition. This is intentionally page motion, without liquid pinch.
            translationY = homePathCenter?.let { it.y - shellHeight / 2f }
                ?: (source.top * (1f - p) +
                    if (openingDownward) {
                        4f * arcHeight * p * (1f - p) * if (closing) -1f else 1f
                    } else {
                        -4f * arcHeight * p * (1f - p)
                    }),
            scale = scale,
            clipBottom = clipBottom,
            progress = p
        )
        val valuesState = rememberUpdatedState(values)
        val clipShape = remember(
            source,
            screenWidth,
            sourceCornerRadius,
            targetCornerRadiusPx,
            density.density
        ) {
            AnchoredDetailClipShape(
                screenWidth = screenWidth,
                screenCornerRadiusPx = targetCornerRadiusPx,
                sourceCornerRadiusPx = with(density) { sourceCornerRadius.toPx() },
                values = valuesState
            )
        }
        val fullOpenEndpoint = !detailMorphUsesTransientClip(p, closing)
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = values.backgroundAlpha))
                .pointerInput(closing) {
                    awaitPointerEventScope {
                        while (true) awaitPointerEvent().changes.forEach { it.consume() }
                    }
                }
        )
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    transformOrigin = TransformOrigin(0.5f, 0f)
                    scaleX = values.scale
                    scaleY = values.scale
                    translationX = values.translationX
                    translationY = values.translationY
                    shape = clipShape
                    clip = !fullOpenEndpoint
                    compositingStrategy = if (fullOpenEndpoint) {
                        CompositingStrategy.Auto
                    } else {
                        CompositingStrategy.Offscreen
                    }
                    renderEffect = if (fullOpenEndpoint) {
                        null
                    } else {
                        platformBlurRenderEffect(
                            detailMotionBlurRadiusDp(values.progress) * density.density
                        )
                    }
                }
        ) {
            if (values.sourceAlpha > 0.001f) {
                if (sourceSnapshot != null) {
                    Image(
                        bitmap = sourceSnapshot.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(with(density) { initialClipBottom.toDp() })
                            .graphicsLayer {
                                alpha = values.sourceAlpha
                                shape = RoundedCornerShape(
                                    sourceCornerRadius / initialScale
                                )
                                clip = true
                            },
                        contentScale = ContentScale.FillBounds
                    )
                } else {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(with(density) { (source.height / initialScale).toDp() })
                            .graphicsLayer { alpha = values.sourceAlpha },
                        content = sourceContent
                    )
                }
            }
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = values.contentAlpha }) {
                content(onClose)
            }
        }
    }
}
