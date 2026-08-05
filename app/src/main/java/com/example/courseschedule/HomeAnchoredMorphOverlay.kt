package com.example.courseschedule

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.catalog.components.LiquidPanel
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.drawPlainBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.RoundedRectangle
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin

internal const val HomeAnchoredMorphOpenDurationMillis = 330
internal const val HomeAnchoredMorphCloseDurationMillis = 320
internal const val HomeAnchoredMorphPinchFraction = 0.28f
internal const val HomeAnchoredMorphClosePinchFraction = 0.08f
internal const val HomeAnchoredMorphBackgroundDurationMillis = 460
internal const val HomeAnchoredMorphBackgroundDelayMillis = 20
internal const val HomeAnchoredMorphBackgroundScale = 1.08f

private val HomeAnchoredFallEasing = CubicBezierEasing(0.22f, 0.0f, 0.42f, 1.0f)
private val HomeAnchoredOpenPositionEasing = CubicBezierEasing(0.16f, 0.78f, 0.18f, 1.0f)
private val HomeAnchoredOpenSizeEasing = CubicBezierEasing(0.20f, 0.48f, 0.24f, 1.0f)
private val HomeAnchoredCloseEasing = CubicBezierEasing(0.28f, 0.06f, 0.20f, 1.0f)
internal val HomeAnchoredBackgroundEasing = CubicBezierEasing(0.30f, 0.0f, 0.20f, 1.0f)

internal enum class HomeAnchoredOverlayKind {
    Add,
    Personalize
}

internal enum class HomeAnchoredOverlayPhase {
    Idle,
    Preparing,
    Opening,
    Open,
    Closing,
    Disposing
}

internal data class HomeAnchoredOverlayRequest(
    val kind: HomeAnchoredOverlayKind,
    val sourceBoundsInRoot: Rect
)

@Stable
internal class HomeAnchoredMorphState {
    val progress = Animatable(0f)
    val backgroundZoom = Animatable(1f)
    var phase by mutableStateOf(HomeAnchoredOverlayPhase.Idle)
        internal set
    var renderedKind by mutableStateOf<HomeAnchoredOverlayKind?>(null)
        internal set
}

@Composable
internal fun rememberHomeAnchoredMorphState(): HomeAnchoredMorphState =
    remember { HomeAnchoredMorphState() }

internal data class HomeAnchoredMorphGeometry(
    val rect: Rect,
    val cornerRadiusPx: Float,
    val sourceScale: Float,
    val sourceAlpha: Float,
    val surfaceAlpha: Float,
    val contentAlpha: Float,
    val pathProgress: Float,
    val expansionProgress: Float
)

/**
 * Keeps the tablet personalization backdrop tied to both animation state and slider preview.
 * The preview progress is already animated by the caller, so this value can drive blur radius
 * and opacity without snapping when a drag starts or finishes.
 */
internal fun personalizeBackdropBlurLayerProgress(
    expansionProgress: Float,
    previewProgress: Float
): Float = (
    homeMorphSmoothStep(0.12f, 0.46f, expansionProgress.coerceIn(0f, 1f)) *
        (1f - previewProgress.coerceIn(0f, 1f))
    ).coerceIn(0f, 1f)

internal fun homeAnchoredMorphGeometry(
    source: Rect,
    target: Rect,
    rawProgress: Float,
    closing: Boolean,
    directClosing: Boolean = false,
    directSourceCornerRadiusPx: Float? = null,
    sourceCornerRadiusPx: Float? = null,
    pinchDiameterPx: Float,
    minimumDropPx: Float,
    maximumDropPx: Float,
    maximumArcPx: Float,
    targetCornerRadiusPx: Float
): HomeAnchoredMorphGeometry {
    val raw = rawProgress.coerceIn(0f, 1f)
    val pathProgress = if (closing) {
        1f - HomeAnchoredCloseEasing.transform(1f - raw)
    } else {
        raw
    }.coerceIn(0f, 1f)
    val pinchFraction = if (closing) {
        HomeAnchoredMorphClosePinchFraction
    } else {
        HomeAnchoredMorphPinchFraction
    }
    val sourceCenter = source.center
    val targetCenter = target.center
    val dropDistance = (abs(targetCenter.y - sourceCenter.y) * 0.18f)
        .coerceIn(minimumDropPx, maximumDropPx)
    val pinchCenterX = sourceCenter.x
    val pinchCenterY = sourceCenter.y + dropDistance

    val expansionProgress: Float
    val centerX: Float
    val centerY: Float
    val width: Float
    val height: Float
    val cornerRadius: Float
    val sourceScale: Float

    val sourceRadius = sourceCornerRadiusPx
        ?: directSourceCornerRadiusPx
        ?: (min(source.width, source.height) / 2f)

    if (directClosing) {
        val position = HomeAnchoredOpenPositionEasing.transform(pathProgress)
        val size = HomeAnchoredOpenSizeEasing.transform(pathProgress)
        centerX = lerpHomeMorph(sourceCenter.x, targetCenter.x, position)
        centerY = lerpHomeMorph(sourceCenter.y, targetCenter.y, position)
        width = lerpHomeMorph(source.width, target.width, size)
        height = lerpHomeMorph(source.height, target.height, size)
        cornerRadius = lerpHomeMorph(
            sourceRadius,
            targetCornerRadiusPx,
            size
        )
        sourceScale = (min(width, height) / min(source.width, source.height).coerceAtLeast(1f))
            .coerceIn(0f, 1f)
        expansionProgress = pathProgress
    } else if (pathProgress <= pinchFraction) {
        val local = (pathProgress / pinchFraction).coerceIn(0f, 1f)
        val fall = HomeAnchoredFallEasing.transform(local)
        val diameterProgress = HomeAnchoredOpenSizeEasing.transform(local)
        width = lerpHomeMorph(source.width, pinchDiameterPx, diameterProgress)
        height = lerpHomeMorph(source.height, pinchDiameterPx, diameterProgress)
        centerX = sourceCenter.x
        centerY = lerpHomeMorph(sourceCenter.y, pinchCenterY, fall)
        cornerRadius = min(
            lerpHomeMorph(sourceRadius, pinchDiameterPx / 2f, diameterProgress),
            min(width, height) / 2f
        )
        sourceScale = (min(width, height) / min(source.width, source.height).coerceAtLeast(1f))
            .coerceIn(0f, 1f)
        expansionProgress = 0f
    } else {
        val local = ((pathProgress - pinchFraction) /
            (1f - pinchFraction)).coerceIn(0f, 1f)
        val position = HomeAnchoredOpenPositionEasing.transform(local)
        val size = HomeAnchoredOpenSizeEasing.transform(local)
        val deltaY = targetCenter.y - pinchCenterY
        val arc = min(maximumArcPx, abs(deltaY) * 0.22f)
        val controlX = (pinchCenterX + targetCenter.x) / 2f
        val controlY = (pinchCenterY + targetCenter.y) / 2f + sign(deltaY) * arc
        val inverse = 1f - position
        centerX = inverse * inverse * pinchCenterX +
            2f * inverse * position * controlX +
            position * position * targetCenter.x
        centerY = inverse * inverse * pinchCenterY +
            2f * inverse * position * controlY +
            position * position * targetCenter.y
        val pulseWindow = ((local - 0.82f) / 0.18f).coerceIn(0f, 1f)
        val pulseScale = 1f + sin(PI.toFloat() * pulseWindow) * 0.008f
        width = lerpHomeMorph(pinchDiameterPx, target.width, size) * pulseScale
        height = lerpHomeMorph(pinchDiameterPx, target.height, size) * pulseScale
        cornerRadius = lerpHomeMorph(pinchDiameterPx / 2f, targetCornerRadiusPx, size)
        sourceScale = (pinchDiameterPx / min(source.width, source.height).coerceAtLeast(1f))
            .coerceIn(0f, 1f)
        expansionProgress = local
    }

    val handoff = if (closing) {
        homeMorphSmoothStep(0.015f, 0.12f, expansionProgress)
    } else {
        homeMorphSmoothStep(0.05f, 0.34f, expansionProgress)
    }
    val contentAlpha = if (closing) {
        homeMorphSmoothStep(0.04f, 0.20f, expansionProgress)
    } else {
        homeMorphSmoothStep(0.18f, 0.55f, expansionProgress)
    }
    return HomeAnchoredMorphGeometry(
        rect = Rect(
            left = centerX - width / 2f,
            top = centerY - height / 2f,
            right = centerX + width / 2f,
            bottom = centerY + height / 2f
        ),
        cornerRadiusPx = cornerRadius,
        sourceScale = sourceScale,
        sourceAlpha = 1f - handoff,
        surfaceAlpha = handoff,
        contentAlpha = contentAlpha,
        pathProgress = pathProgress,
        expansionProgress = expansionProgress
    )
}

internal fun homeAddMenuTargetRect(
    source: Rect,
    rootSize: IntSize,
    density: Float,
    actionCount: Int,
    adaptiveMetrics: HomeAdaptiveMetrics? = null
): Rect {
    val width = 202f * density
    val height = (16f + 48f * actionCount + 4f * (actionCount - 1).coerceAtLeast(0)) * density
    val proposed = Rect(
        left = source.center.x - width / 2f,
        top = source.bottom + 4f * density,
        right = source.center.x + width / 2f,
        bottom = source.bottom + 4f * density + height
    )
    return clampHomeMorphTarget(proposed, rootSize, 12f * density, adaptiveMetrics?.contentRectPx(rootSize, density))
}

internal fun homePersonalizeTargetRect(
    rootSize: IntSize,
    density: Float,
    adaptiveMetrics: HomeAdaptiveMetrics? = null
): Rect {
    if (adaptiveMetrics != null) {
        val content = adaptiveMetrics.contentRectPx(rootSize, density)
        if (!adaptiveMetrics.isLargeScreen) {
            val maxHeightDp = content.height / density.coerceAtLeast(0.001f)
            val heightRatio = when {
                maxHeightDp < 520f -> 0.78f
                maxHeightDp < 700f -> 0.74f
                else -> 0.70f
            }
            val panelHeight = minOf(content.height * heightRatio, 680f * density)
                .coerceAtLeast(minOf(content.height, 280f * density))
            val panelWidth = (content.width * 0.95f).coerceAtLeast(1f)
            val proposed = Rect(
                left = content.left + (content.width - panelWidth) / 2f,
                top = content.top + (content.height - panelHeight) / 2f,
                right = content.left + (content.width + panelWidth) / 2f,
                bottom = content.top + (content.height + panelHeight) / 2f
            )
            return clampHomeMorphTarget(proposed, rootSize, 0f, content)
        }
        val landscape = adaptiveMetrics.isTabletLandscape
        val minWidth = minOf((if (landscape) 380f else 400f) * density, content.width)
        val panelWidth = minOf(
            content.width * if (landscape) 0.40f else 0.72f,
            (if (landscape) 448f else 480f) * density
        ).coerceAtLeast(minWidth)
        val panelHeight = minOf(
            content.height * if (landscape) 0.84f else 0.78f,
            (if (landscape) 760f else 720f) * density
        ).coerceAtLeast(minOf(content.height, 420f * density))
        val left = if (adaptiveMetrics.isTabletLandscape) {
            content.right - panelWidth
        } else {
            content.left + (content.width - panelWidth) / 2f
        }
        val top = content.top + (content.height - panelHeight) / 2f
        return Rect(
            left = left,
            top = top,
            right = left + panelWidth,
            bottom = top + panelHeight
        )
    }
    val rootWidth = rootSize.width.toFloat()
    val rootHeight = rootSize.height.toFloat()
    val maxHeightDp = rootHeight / density.coerceAtLeast(0.001f)
    val heightRatio = when {
        maxHeightDp < 520f -> 0.74f
        maxHeightDp < 700f -> 0.72f
        else -> 0.68f
    }
    val topClearance = (HomeInitialTopInset.value + 8f) * density
    val centeredSafeHeight = (rootHeight - topClearance * 2f).coerceAtLeast(280f * density)
    val panelHeight = minOf(rootHeight * heightRatio, centeredSafeHeight, 680f * density)
    val panelWidth = ((rootWidth - 32f * density).coerceAtLeast(1f) * 0.95f)
    val proposed = Rect(
        left = (rootWidth - panelWidth) / 2f,
        top = (rootHeight - panelHeight) / 2f,
        right = (rootWidth + panelWidth) / 2f,
        bottom = (rootHeight + panelHeight) / 2f
    )
    return clampHomeMorphTarget(proposed, rootSize, 12f * density)
}

internal fun clampHomeMorphTarget(
    target: Rect,
    rootSize: IntSize,
    marginPx: Float,
    bounds: Rect? = null
): Rect {
    if (rootSize.width <= 0 || rootSize.height <= 0) return Rect.Zero
    val safeBounds = bounds ?: Rect(0f, 0f, rootSize.width.toFloat(), rootSize.height.toFloat())
    val minLeft = safeBounds.left + marginPx
    val minTop = safeBounds.top + marginPx
    val maxRight = safeBounds.right - marginPx
    val maxBottom = safeBounds.bottom - marginPx
    val availableWidth = (maxRight - minLeft).coerceAtLeast(1f)
    val availableHeight = (maxBottom - minTop).coerceAtLeast(1f)
    val width = min(target.width, availableWidth)
    val height = min(target.height, availableHeight)
    val left = target.left.coerceIn(minLeft, maxRight - width)
    val top = target.top.coerceIn(minTop, maxBottom - height)
    return Rect(left, top, left + width, top + height)
}

@Composable
internal fun HomeAnchoredMorphOverlayHost(
    request: HomeAnchoredOverlayRequest?,
    motionState: HomeAnchoredMorphState,
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    addActions: List<AddMenuAction>,
    adaptiveMetrics: HomeAdaptiveMetrics,
    modifier: Modifier = Modifier,
    onDismissRequest: () -> Unit,
    onAddMenuBoundsChanged: (Rect) -> Unit = {},
    personalizePreviewProgress: Float = 0f,
    sourceContent: @Composable BoxScope.(HomeAnchoredOverlayKind, Modifier) -> Unit,
    personalizeContent: @Composable (Modifier) -> Unit
) {
    var renderedRequest by remember { mutableStateOf<HomeAnchoredOverlayRequest?>(null) }
    var panelContentPrepared by remember { mutableStateOf(false) }
    var rootSize by remember { mutableStateOf(IntSize.Zero) }
    val latestOnDismissRequest by rememberUpdatedState(onDismissRequest)
    val latestOnAddMenuBoundsChanged by rememberUpdatedState(onAddMenuBoundsChanged)

    LaunchedEffect(request, adaptiveMetrics.profile) {
        if (request != null) {
            motionState.phase = HomeAnchoredOverlayPhase.Preparing
            renderedRequest = request
            panelContentPrepared = request.kind != HomeAnchoredOverlayKind.Personalize
            motionState.renderedKind = request.kind
            motionState.progress.snapTo(0f)
            motionState.backgroundZoom.snapTo(1f)
            var waitedFrames = 0
            while (waitedFrames < 12 && (rootSize.width <= 0 || rootSize.height <= 0)) {
                withFrameNanos { }
                waitedFrames++
            }
            if (request.kind == HomeAnchoredOverlayKind.Personalize) {
                // Personalization contains several sliders and glass sections. Compose and measure
                // them before the panel starts moving so the first expansion frame stays cheap.
                panelContentPrepared = true
                withFrameNanos { }
            }
            motionState.phase = HomeAnchoredOverlayPhase.Opening
            coroutineScope {
                launch {
                    motionState.progress.animateTo(
                        1f,
                        tween(HomeAnchoredMorphOpenDurationMillis, easing = LinearEasing)
                    )
                }
                if (request.kind == HomeAnchoredOverlayKind.Personalize && !adaptiveMetrics.isLargeScreen) {
                    launch {
                        motionState.backgroundZoom.animateTo(
                            HomeAnchoredMorphBackgroundScale,
                            tween(
                                durationMillis = HomeAnchoredMorphBackgroundDurationMillis,
                                delayMillis = HomeAnchoredMorphBackgroundDelayMillis,
                                easing = HomeAnchoredBackgroundEasing
                            )
                        )
                    }
                }
            }
            motionState.phase = HomeAnchoredOverlayPhase.Open
        } else if (renderedRequest != null) {
            motionState.phase = HomeAnchoredOverlayPhase.Closing
            coroutineScope {
                launch {
                    motionState.progress.animateTo(
                        0f,
                        tween(HomeAnchoredMorphCloseDurationMillis, easing = LinearEasing)
                    )
                }
                if (motionState.backgroundZoom.value > 1.0001f) {
                    launch {
                        motionState.backgroundZoom.animateTo(
                            1f,
                            tween(
                                durationMillis = HomeAnchoredMorphBackgroundDurationMillis,
                                delayMillis = HomeAnchoredMorphBackgroundDelayMillis,
                                easing = HomeAnchoredBackgroundEasing
                            )
                        )
                    }
                }
            }
            motionState.phase = HomeAnchoredOverlayPhase.Disposing
            panelContentPrepared = false
            renderedRequest = null
            motionState.renderedKind = null
            motionState.phase = HomeAnchoredOverlayPhase.Idle
        } else {
            panelContentPrepared = false
            motionState.progress.snapTo(0f)
            motionState.backgroundZoom.snapTo(1f)
            motionState.renderedKind = null
            motionState.phase = HomeAnchoredOverlayPhase.Idle
        }
    }

    val shownRequest = request ?: renderedRequest
    BackHandler(enabled = shownRequest != null) {
        latestOnDismissRequest()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { rootSize = it }
    ) {
        val shown = shownRequest ?: return@Box
        if (rootSize.width <= 0 || rootSize.height <= 0) return@Box

        val density = androidx.compose.ui.platform.LocalDensity.current
        val targetRect = remember(
            shown.kind,
            shown.sourceBoundsInRoot,
            rootSize,
            density.density,
            addActions.size,
            adaptiveMetrics
        ) {
            when (shown.kind) {
                HomeAnchoredOverlayKind.Add -> homeAddMenuTargetRect(
                    source = shown.sourceBoundsInRoot,
                    rootSize = rootSize,
                    density = density.density,
                    actionCount = addActions.size,
                    adaptiveMetrics = adaptiveMetrics
                )
                HomeAnchoredOverlayKind.Personalize -> homePersonalizeTargetRect(
                    rootSize = rootSize,
                    density = density.density,
                    adaptiveMetrics = adaptiveMetrics
                )
            }
        }
        LaunchedEffect(shown.kind, targetRect) {
            if (shown.kind == HomeAnchoredOverlayKind.Add) {
                latestOnAddMenuBoundsChanged(targetRect)
            }
        }
        val targetCornerPx = with(density) {
            if (shown.kind == HomeAnchoredOverlayKind.Add) 26.dp.toPx() else 28.dp.toPx()
        }
        val geometry = homeAnchoredMorphGeometry(
            source = shown.sourceBoundsInRoot,
            target = targetRect,
            rawProgress = motionState.progress.value,
            closing = motionState.phase == HomeAnchoredOverlayPhase.Closing ||
                motionState.phase == HomeAnchoredOverlayPhase.Disposing,
            pinchDiameterPx = with(density) { 18.dp.toPx() },
            minimumDropPx = with(density) { 36.dp.toPx() },
            maximumDropPx = with(density) { 72.dp.toPx() },
            maximumArcPx = with(density) { adaptiveMetrics.animationArc.toPx() },
            targetCornerRadiusPx = targetCornerPx
        )
        val animatedRect = geometry.rect
        val animatedWidth = with(density) { animatedRect.width.toDp() }
        val animatedHeight = with(density) { animatedRect.height.toDp() }
        val targetWidth = with(density) { targetRect.width.toDp() }
        val targetHeight = with(density) { targetRect.height.toDp() }
        val corner = with(density) { geometry.cornerRadiusPx.toDp() }
        val maxContentBlurPx = with(density) { 5.dp.toPx() }
        val morphContentBlurPx = maxContentBlurPx * (
            1f - homeMorphSmoothStep(0.42f, 0.98f, geometry.expansionProgress)
        )
        val sourceContentBlurPx = maxContentBlurPx * homeMorphSmoothStep(
            0f,
            0.34f,
            geometry.pathProgress
        )
        val shape = if (shown.kind == HomeAnchoredOverlayKind.Personalize) {
            RoundedRectangle(corner)
        } else {
            RoundedCornerShape(corner)
        }
        val personalizeBlurLayerProgress = personalizeBackdropBlurLayerProgress(
            expansionProgress = geometry.expansionProgress,
            previewProgress = personalizePreviewProgress
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null
                ) { latestOnDismissRequest() }
        )

        if (
            shown.kind == HomeAnchoredOverlayKind.Personalize &&
            adaptiveMetrics.isLargeScreen &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            backdrop != null &&
            personalizeBlurLayerProgress > 0.001f
        ) {
            val auraProgress = geometry.expansionProgress.coerceIn(0f, 1f)
            val leftFeather = 104.dp * auraProgress
            val leftFeatherPx = with(density) { leftFeather.toPx() }
            val auraLeftPx = (animatedRect.left - leftFeatherPx).coerceAtLeast(0f)
            PersonalizeBackdropAura(
                backdrop = backdrop,
                leftFeather = leftFeather,
                blurProgress = personalizeBlurLayerProgress,
                modifier = Modifier
                    .offset {
                        IntOffset(
                            auraLeftPx.roundToInt(),
                            0
                        )
                    }
                    .size(
                        with(density) { (rootSize.width - auraLeftPx).toDp() },
                        with(density) { rootSize.height.toDp() }
                    )
                    .graphicsLayer {
                        alpha = geometry.surfaceAlpha * personalizeBlurLayerProgress
                    }
            )
        }

        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        animatedRect.left.roundToInt(),
                        animatedRect.top.roundToInt()
                    )
                }
                .size(animatedWidth, animatedHeight)
                .graphicsLayer {
                    clip = true
                    this.shape = shape
                }
                .then(
                    if (shown.kind == HomeAnchoredOverlayKind.Personalize) {
                        Modifier.clickable(
                            interactionSource = remember {
                                androidx.compose.foundation.interaction.MutableInteractionSource()
                            },
                            indication = null,
                            onClick = {}
                        )
                    } else {
                        Modifier
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            when (shown.kind) {
                HomeAnchoredOverlayKind.Add -> HomeAddMenuMorphPanel(
                    backdrop = backdrop,
                    config = config,
                    actions = addActions,
                    targetSize = IntSize(targetRect.width.roundToInt(), targetRect.height.roundToInt()),
                    surfaceAlpha = geometry.surfaceAlpha,
                    contentAlpha = geometry.contentAlpha,
                    contentBlurRadiusPx = morphContentBlurPx,
                    interactive = motionState.phase == HomeAnchoredOverlayPhase.Open,
                    corner = corner,
                    modifier = Modifier.fillMaxSize()
                )
                HomeAnchoredOverlayKind.Personalize -> HomePersonalizeMorphPanel(
                    backdrop = backdrop,
                    config = config,
                    targetWidth = targetWidth,
                    targetHeight = targetHeight,
                    surfaceAlpha = geometry.surfaceAlpha * if (adaptiveMetrics.isLargeScreen) {
                        1f
                    } else {
                        1f - personalizePreviewProgress.coerceIn(0f, 1f)
                    },
                    contentAlpha = geometry.contentAlpha,
                    contentBlurRadiusPx = morphContentBlurPx,
                    corner = corner,
                    progressiveBlur = adaptiveMetrics.isLargeScreen,
                    backdropBlurProgress = personalizeBlurLayerProgress,
                    modifier = Modifier.fillMaxSize(),
                    content = { contentModifier ->
                        if (panelContentPrepared) {
                            personalizeContent(contentModifier)
                        }
                    }
                )
            }

            Box(
                modifier = Modifier
                    .requiredSize(42.dp)
                    .graphicsLayer {
                        alpha = geometry.sourceAlpha
                        scaleX = geometry.sourceScale
                        scaleY = geometry.sourceScale
                        compositingStrategy = CompositingStrategy.Offscreen
                        renderEffect = if (sourceContentBlurPx > 0.01f) {
                            BlurEffect(sourceContentBlurPx, sourceContentBlurPx, TileMode.Clamp)
                        } else null
                    }
                    .clearAndSetSemantics { }
            ) {
                sourceContent(shown.kind, Modifier.fillMaxSize())
            }

            if (motionState.phase != HomeAnchoredOverlayPhase.Open) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(motionState.phase) {
                            awaitPointerEventScope {
                                while (true) {
                                    awaitPointerEvent().changes.forEach { it.consume() }
                                }
                            }
                        }
                )
            }
        }
    }
}

@Composable
private fun HomePersonalizeMorphPanel(
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    targetWidth: androidx.compose.ui.unit.Dp,
    targetHeight: androidx.compose.ui.unit.Dp,
    surfaceAlpha: Float,
    contentAlpha: Float,
    contentBlurRadiusPx: Float,
    corner: androidx.compose.ui.unit.Dp,
    progressiveBlur: Boolean,
    backdropBlurProgress: Float,
    modifier: Modifier,
    content: @Composable (Modifier) -> Unit
) {
    val lightGlass = glassUsesLightStyle(config)
    Box(modifier) {
        if (backdrop != null) {
            val surfaceColor = if (lightGlass) {
                HomeLightGlassSurfaceColor.copy(alpha = HomeLightGlassPanelTintAlpha)
            } else {
                Color(0xFF121212).copy(alpha = 0.30f)
            }
            if (progressiveBlur && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ProgressivePersonalizeSurface(
                    backdrop = backdrop,
                    corner = corner,
                    surfaceColor = surfaceColor,
                    blurProgress = backdropBlurProgress,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = surfaceAlpha }
                )
            } else {
                LiquidPanel(
                    backdrop = backdrop,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = surfaceAlpha },
                    shape = RoundedRectangle(corner),
                    surfaceColor = surfaceColor
                ) { }
            }
        } else {
            GlassDialogSurface(
                backdrop = null,
                config = config,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = surfaceAlpha },
                shape = RoundedCornerShape(corner)
            ) { }
        }
        val contentEffects = if (contentBlurRadiusPx > 0.01f) {
            Modifier.graphicsLayer {
                alpha = contentAlpha
                compositingStrategy = CompositingStrategy.Offscreen
                renderEffect = BlurEffect(contentBlurRadiusPx, contentBlurRadiusPx, TileMode.Clamp)
            }
        } else {
            Modifier.graphicsLayer { alpha = contentAlpha }
        }
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .requiredSize(targetWidth, targetHeight)
                .then(contentEffects)
        ) {
            content(Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun ProgressivePersonalizeSurface(
    backdrop: Backdrop,
    corner: androidx.compose.ui.unit.Dp,
    surfaceColor: Color,
    blurProgress: Float,
    modifier: Modifier = Modifier
) {
    val shape = RoundedRectangle(corner)
    val safeBlurProgress = blurProgress.coerceIn(0f, 1f)
    Box(
        modifier = modifier
            .graphicsLayer {
                this.shape = shape
                clip = true
            }
    ) {
        if (safeBlurProgress > 0.001f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = safeBlurProgress }
                    .drawPlainBackdrop(
                        backdrop = backdrop,
                        shape = { shape },
                        effects = { blur((7.dp * safeBlurProgress).toPx()) }
                    )
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                brush = Brush.horizontalGradient(
                    0f to surfaceColor.copy(
                        alpha = (surfaceColor.alpha + if (surfaceColor.red > 0.5f) 0.08f else 0.07f)
                            .coerceAtMost(0.42f)
                    ),
                    0.56f to surfaceColor,
                    1f to surfaceColor.copy(
                        alpha = (surfaceColor.alpha * 0.72f).coerceAtLeast(0.07f)
                    )
                ),
                shape = shape
            )
                .border(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        listOf(
                            Color.White.copy(alpha = 0.28f),
                            Color.White.copy(alpha = 0.08f)
                        )
                    ),
                    shape = shape
                )
        )
    }
}

@Composable
private fun PersonalizeBackdropAura(
    backdrop: Backdrop,
    leftFeather: androidx.compose.ui.unit.Dp,
    blurProgress: Float,
    modifier: Modifier = Modifier
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val safeBlurProgress = blurProgress.coerceIn(0f, 1f)
    Box(
        modifier = modifier
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                val left = with(density) { leftFeather.toPx() }
                val leftStop = (left / size.width.coerceAtLeast(1f)).coerceIn(0f, 0.48f)
                drawRect(
                    brush = Brush.horizontalGradient(
                        0f to Color.Transparent,
                        leftStop to Color.White,
                        1f to Color.White
                    ),
                    blendMode = BlendMode.DstIn
                )
            }
            .drawPlainBackdrop(
                backdrop = backdrop,
                // Lens requires a rounded/corner-based SDF shape. A zero-radius continuous
                // rectangle remains visually square while satisfying that contract.
                shape = { RoundedRectangle(0.dp) },
                effects = {
                    vibrancy()
                    blur((5.dp * safeBlurProgress).toPx())
                    lens(24.dp.toPx(), 34.dp.toPx(), chromaticAberration = false)
                }
            )
    )
}

@Composable
internal fun HomeAddMenuMorphPanel(
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    actions: List<AddMenuAction>,
    targetSize: IntSize,
    surfaceAlpha: Float,
    contentAlpha: Float,
    contentBlurRadiusPx: Float = 0f,
    interactive: Boolean,
    corner: androidx.compose.ui.unit.Dp,
    modifier: Modifier
) {
    var highlightedIndex by remember { mutableIntStateOf(-1) }
    var touching by remember { mutableStateOf(false) }
    val pressProgress by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (touching || highlightedIndex >= 0) 1f else 0f,
        animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.72f, stiffness = 360f),
        label = "home-add-menu-press"
    )
    val density = androidx.compose.ui.platform.LocalDensity.current
    val targetWidth = with(density) { targetSize.width.toDp() }
    val targetHeight = with(density) { targetSize.height.toDp() }
    val itemHeightPx = with(density) { 48.dp.toPx() }
    val itemStepPx = with(density) { 52.dp.toPx() }
    val menuPaddingPx = with(density) { 8.dp.toPx() }
    val lightGlass = glassUsesLightStyle(config)
    val textColor = glassForegroundColor(config)
    val menuShape = RoundedCornerShape(corner)

    fun hitIndex(y: Float): Int {
        val localY = y - menuPaddingPx
        if (localY < 0f || !interactive) return -1
        val index = (localY / itemStepPx).toInt()
        val inItem = localY - index * itemStepPx <= itemHeightPx
        return index.takeIf { it in actions.indices && inItem } ?: -1
    }

    val dragModifier = if (interactive) {
        Modifier.pointerInput(actions) {
            awaitPointerEventScope {
                while (true) {
                    val down = awaitPointerEvent().changes.firstOrNull { it.pressed } ?: continue
                    touching = true
                    highlightedIndex = hitIndex(down.position.y)
                    down.consume()
                    var released = false
                    while (!released) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: continue
                        highlightedIndex = hitIndex(change.position.y)
                        if (change.changedToUpIgnoreConsumed()) {
                            val index = highlightedIndex
                            touching = false
                            highlightedIndex = -1
                            if (index in actions.indices) actions[index].onClick()
                            released = true
                        }
                        change.consume()
                    }
                }
            }
        }
    } else {
        Modifier
    }

    Box(
        modifier = modifier.then(dragModifier),
        contentAlignment = Alignment.Center
    ) {
        val surfaceModifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = surfaceAlpha }
        Box(
            modifier = if (backdrop != null) {
                surfaceModifier.drawBackdrop(
                    backdrop = backdrop,
                    shape = { menuShape },
                    effects = {
                        vibrancy()
                        blur((14.dp * 0.65f + 3.dp * pressProgress).toPx())
                        lens(
                            (18.dp + 4.dp * pressProgress).toPx(),
                            (HomeHeaderGlassLensAmount + 6.dp + 6.dp * pressProgress).toPx(),
                            depthEffect = true,
                            chromaticAberration = false
                        )
                    },
                    highlight = {
                        Highlight.Default.copy(alpha = 0.04f + 0.08f * pressProgress)
                    },
                    shadow = {
                        Shadow(alpha = (if (lightGlass) 0.10f else 0.18f) + 0.08f * pressProgress)
                    },
                    innerShadow = {
                        InnerShadow(radius = 11.dp, alpha = 0.10f + 0.08f * pressProgress)
                    },
                    layerBlock = {
                        val scale = 1f + 0.016f * pressProgress
                        scaleX = scale
                        scaleY = scale
                    },
                    onDrawSurface = {
                        drawRect(
                            (if (lightGlass) HomeLightGlassSurfaceColor else Color(0xFF050505))
                                .copy(alpha = if (lightGlass) HomeLightGlassMenuTintAlpha else 0.26f)
                        )
                        drawRect(Color.Black.copy(alpha = if (lightGlass) 0.018f else 0.055f))
                    }
                )
            } else {
                surfaceModifier.background(
                    if (appUsesDarkTheme(config)) Color(0xFF1C1C1E) else Color.White
                )
            }
        )

        val contentEffects = if (contentBlurRadiusPx > 0.01f) {
            Modifier.graphicsLayer {
                alpha = contentAlpha
                compositingStrategy = CompositingStrategy.Offscreen
                renderEffect = BlurEffect(contentBlurRadiusPx, contentBlurRadiusPx, TileMode.Clamp)
            }
        } else {
            Modifier.graphicsLayer { alpha = contentAlpha }
        }
        Column(
            modifier = Modifier
                .requiredSize(targetWidth, targetHeight)
                .then(contentEffects)
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            CompositionLocalProvider(LocalContentColor provides textColor) {
                actions.forEachIndexed { index, action ->
                    AddMenuLiquidItem(
                        config = config,
                        action = action,
                        highlighted = highlightedIndex == index,
                        itemHeight = 48.dp
                    )
                }
            }
        }
    }
}

private fun lerpHomeMorph(start: Float, stop: Float, fraction: Float): Float {
    val safe = fraction.coerceIn(0f, 1f)
    return start + (stop - start) * safe
}

internal fun homeMorphSmoothStep(edge0: Float, edge1: Float, value: Float): Float {
    val t = ((value - edge0) / (edge1 - edge0).coerceAtLeast(0.0001f)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}
