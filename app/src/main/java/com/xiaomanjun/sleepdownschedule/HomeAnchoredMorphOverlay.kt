package com.xiaomanjun.sleepdownschedule

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.catalog.components.LiquidPanel
import com.kyant.backdrop.catalog.components.LiquidButton
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.shapes.RoundedRectangle
import com.xiaomanjun.sleepdownschedule.glass.GlassBackdropDomain
import com.xiaomanjun.sleepdownschedule.glass.GlassMaterialRole
import com.xiaomanjun.sleepdownschedule.glass.GlassMaterialSpec
import com.xiaomanjun.sleepdownschedule.glass.LiquidMorphController
import com.xiaomanjun.sleepdownschedule.glass.LiquidMorphControllerBridge
import com.xiaomanjun.sleepdownschedule.glass.LiquidMorphPhase
import com.xiaomanjun.sleepdownschedule.glass.rememberGlassSurfaceDescriptor
import com.xiaomanjun.sleepdownschedule.glass.sleepDownPlainGlassSurface
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin

internal const val HomeAnchoredMorphOpenDurationMillis = 430
internal const val HomePersonalizeMorphOpenDurationMillis = ThreeDotMenuMotion.OpenDurationMillis
internal const val HomeAnchoredMorphCloseDurationMillis = 360
internal const val HomePersonalizeMorphCloseDurationMillis = ThreeDotMenuMotion.CloseDurationMillis
internal const val HomeAnchoredOpenSettleStartFraction = 0.82f
internal const val HomeAnchoredMorphPinchFraction = 0.28f
internal const val HomeAnchoredMorphClosePinchFraction = 0.08f
internal const val HomeAnchoredMorphBackgroundDurationMillis = 460
internal const val HomeAnchoredMorphBackgroundDelayMillis = 20
internal const val HomeAnchoredMorphBackgroundScale = 1.08f

/** Motion parameters for the three-dot first-level menu's shared-object trajectory. */
internal object ThreeDotMenuMotion {
    const val OpenDurationMillis = 440
    const val CloseDurationMillis = 285

    const val OpenPinchFraction = 0.28f
    const val OpenPinchDiameterDp = 18f
    const val OpenMinimumDropDp = 36f
    const val OpenMaximumDropDp = 72f
    const val OpenReboundPeakFraction = 0.40f
    const val OpenReboundAmplitudeDp = 12f

    const val CloseSinkEndFraction = 0.13f
    const val CloseReturnStartFraction = 0.08f
    const val CloseReturnBlendEndFraction = 0.15f
    const val CloseControlXFraction = 0.40f
    const val CloseSinkOffsetDp = 6f
    const val CloseControlDropDp = 14f
    const val CloseSinkCompression = 0.012f

    val CloseReturnEasing = CubicBezierEasing(0.45f, 0.0f, 0.20f, 1.0f)
}

internal const val HomeAddMenuMorphOpenDurationMillis = ThreeDotMenuMotion.OpenDurationMillis
internal const val HomeAddMenuMorphCloseDurationMillis = ThreeDotMenuMotion.CloseDurationMillis

internal const val HomeAddMenuTargetCornerDp = 30f
internal const val HomeAddMenuSelectionCornerDp = 19f
internal const val HomeAddMenuConcentricInsetDp =
    HomeAddMenuTargetCornerDp - HomeAddMenuSelectionCornerDp
private const val HomeAddMenuActionContentWidthDp = 172f
private const val HomeAddMenuActionColumnInsetDp = 2f
private const val HomeAddMenuSelectionVerticalInsetDp = 1f
private const val HomeAddMenuContentTopPaddingDp = 6f
private const val HomeAddMenuModeHeightDp = 52f
private const val HomeAddMenuSectionGapDp = 4f
private const val HomeAddMenuDividerHeightDp = 1f
private const val HomeAddMenuActionItemHeightDp = 40f
private const val HomeAddMenuActionGapDp = 0f

// The visible trajectory is cubic-bezier for its complete duration. LinearEasing below is only
// used as the phase clock, so absolute handoff/rebound timings stay stable.
// Neither curve ends with y2=1: both retain visible terminal velocity instead of crawling through
// a long zero-speed tail. The source/content handoff hides the final endpoint stop.
private val HomeAnchoredOpenMotionEasing = CubicBezierEasing(0.10f, 0.60f, 0.88f, 0.88f)
// Closing is deliberately independent: it responds immediately, settles cleanly, and runs over
// a shorter duration instead of replaying the opening rebound backwards.
private val HomeAnchoredCloseMotionEasing = CubicBezierEasing(0.22f, 0.75f, 0.60f, 0.90f)
internal val HomeAnchoredBackgroundEasing = CubicBezierEasing(0.30f, 0.0f, 0.20f, 1.0f)

// The recovered 02:38 opening stack is shared by every Home popup trajectory. The generic
// anchored/detail transitions below still retain their older easing where they are used elsewhere.
private val HomeAnchoredFallEasing = CubicBezierEasing(0.22f, 0.0f, 0.42f, 1.0f)
private val HomeAnchoredOpenPositionEasing = CubicBezierEasing(0.16f, 0.78f, 0.18f, 1.0f)
private val HomeAnchoredOpenSizeEasing = CubicBezierEasing(0.20f, 0.48f, 0.24f, 1.0f)
private val HomeAnchoredCloseEasing = CubicBezierEasing(0.28f, 0.06f, 0.20f, 1.0f)

internal fun homeAnchoredOpenMotionProgress(progress: Float): Float {
    return HomeAnchoredOpenMotionEasing.transform(progress.coerceIn(0f, 1f))
}

internal fun homeAnchoredCloseMotionProgress(progress: Float): Float {
    val value = progress.coerceIn(0f, 1f)
    return 1f - HomeAnchoredCloseMotionEasing.transform(1f - value)
}

/**
 * Selects the visible trajectory used by [homeAnchoredMorphGeometry].
 *
 * [Directional] is the generic anchored transition style with a linear phase clock and
 * direction-specific cubics. [Legacy] restores the previous version's easing stack (a close-eased
 * phase clock plus fall/position/size cubics and closing handoff windows) for the independently
 * owned personalization and Home menu destination domains.
 */
internal enum class HomeMorphEasingStyle {
    Directional,
    Legacy
}

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

internal val HomeAnchoredOverlayPhase.isMovingTransition: Boolean
    get() = this == HomeAnchoredOverlayPhase.Preparing ||
        this == HomeAnchoredOverlayPhase.Opening ||
        this == HomeAnchoredOverlayPhase.Closing ||
        this == HomeAnchoredOverlayPhase.Disposing

internal data class HomeAnchoredOverlayRequest(
    val kind: HomeAnchoredOverlayKind,
    val sourceBoundsInRoot: Rect,
    val sourcePressedScale: Float = 1f
)

@Stable
internal class HomeAnchoredMorphState {
    val progress = Animatable(0f)
    val backgroundZoom = Animatable(1f)
    var renderedKind by mutableStateOf<HomeAnchoredOverlayKind?>(null)
        internal set
    private val controllerBridge = LiquidMorphControllerBridge {
        when (renderedKind) {
            HomeAnchoredOverlayKind.Add -> "home-three-dot-menu"
            HomeAnchoredOverlayKind.Personalize -> "home-personalization"
            null -> "home-anchored"
        }
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
    targetCornerRadiusPx: Float,
    sourcePressedScale: Float = 1f,
    pinchFractionOverride: Float? = null,
    cornerMorphDuringPinchFraction: Float = 0f,
    handoffStartFraction: Float = 0.05f,
    handoffEndFraction: Float = 0.34f,
    contentStartFraction: Float = 0.18f,
    contentEndFraction: Float = 0.55f,
    motionStyle: HomeMorphEasingStyle = HomeMorphEasingStyle.Directional
): HomeAnchoredMorphGeometry {
    val raw = rawProgress.coerceIn(0f, 1f)
    val legacy = motionStyle == HomeMorphEasingStyle.Legacy
    // Phase progress stays linear in both directions. This keeps the droplet section, content
    // handoff thresholds and rebound peak at their existing absolute times; only the visual
    // trajectory inside each phase receives direction-specific easing.
    // The legacy path instead reverses the previous close easing onto the phase clock and keeps
    // the old open/close pinch split, matching the previous version frame for frame.
    val pathProgress = if (legacy && closing) {
        1f - HomeAnchoredCloseEasing.transform(1f - raw)
    } else {
        raw
    }.coerceIn(0f, 1f)
    val pinchFraction = pinchFractionOverride?.coerceIn(0.08f, 0.72f)
        ?: if (legacy && closing) {
            HomeAnchoredMorphClosePinchFraction
        } else {
            HomeAnchoredMorphPinchFraction
        }
    fun motionProgress(progress: Float): Float = if (closing) {
        homeAnchoredCloseMotionProgress(progress)
    } else {
        homeAnchoredOpenMotionProgress(progress)
    }
    fun positionProgress(progress: Float): Float =
        if (legacy) HomeAnchoredOpenPositionEasing.transform(progress) else motionProgress(progress)
    fun sizeProgress(progress: Float): Float =
        if (legacy) HomeAnchoredOpenSizeEasing.transform(progress) else motionProgress(progress)
    fun fallProgress(progress: Float): Float =
        if (legacy) HomeAnchoredFallEasing.transform(progress) else motionProgress(progress)
    val sourceCenter = source.center
    val pressedScale = sourcePressedScale.coerceIn(1f, 1.16f)
    val initialSourceWidth = source.width * pressedScale
    val initialSourceHeight = source.height * pressedScale
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

    val sourceRadius = (sourceCornerRadiusPx
        ?: directSourceCornerRadiusPx
        ?: (min(source.width, source.height) / 2f)) * pressedScale
    val pinchEndCornerRadius = lerpHomeMorph(
        sourceRadius,
        targetCornerRadiusPx,
        cornerMorphDuringPinchFraction
    )

    if (directClosing) {
        val position = positionProgress(pathProgress)
        val size = sizeProgress(pathProgress)
        centerX = lerpHomeMorph(sourceCenter.x, targetCenter.x, position)
        centerY = lerpHomeMorph(sourceCenter.y, targetCenter.y, position)
        width = lerpHomeMorph(initialSourceWidth, target.width, size)
        height = lerpHomeMorph(initialSourceHeight, target.height, size)
        cornerRadius = lerpHomeMorph(
            sourceRadius,
            targetCornerRadiusPx,
            size
        )
        sourceScale = (min(width, height) / min(source.width, source.height).coerceAtLeast(1f))
            .coerceIn(0f, pressedScale)
        expansionProgress = pathProgress
    } else if (pathProgress <= pinchFraction) {
        val local = (pathProgress / pinchFraction).coerceIn(0f, 1f)
        val fall = fallProgress(local)
        val diameterProgress = sizeProgress(local)
        width = lerpHomeMorph(initialSourceWidth, pinchDiameterPx, diameterProgress)
        height = lerpHomeMorph(initialSourceHeight, pinchDiameterPx, diameterProgress)
        centerX = sourceCenter.x
        centerY = lerpHomeMorph(sourceCenter.y, pinchCenterY, fall)
        cornerRadius = if (cornerMorphDuringPinchFraction > 0f) {
            // The drawable still clamps naturally while the droplet is tiny, but its requested
            // corner is already converging on the panel. As the shell expands there is therefore
            // no second, visibly separate circle-to-panel corner phase.
            lerpHomeMorph(sourceRadius, pinchEndCornerRadius, diameterProgress)
        } else {
            min(
                lerpHomeMorph(sourceRadius, pinchDiameterPx / 2f, diameterProgress),
                min(width, height) / 2f
            )
        }
        sourceScale = (min(width, height) / min(source.width, source.height).coerceAtLeast(1f))
            .coerceIn(0f, pressedScale)
        expansionProgress = 0f
    } else {
        val local = ((pathProgress - pinchFraction) /
            (1f - pinchFraction)).coerceIn(0f, 1f)
        val position = positionProgress(local)
        val size = sizeProgress(local)
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
        val pulseWindow = if (legacy) {
            ((local - 0.82f) / 0.18f).coerceIn(0f, 1f)
        } else {
            ((local - 0.64f) /
                (HomeAnchoredOpenSettleStartFraction - 0.64f)).coerceIn(0f, 1f)
        }
        val pulseScale = if (legacy) {
            1f + sin(PI.toFloat() * pulseWindow) * 0.008f
        } else {
            1f
        }
        width = lerpHomeMorph(pinchDiameterPx, target.width, size) * pulseScale
        height = lerpHomeMorph(pinchDiameterPx, target.height, size) * pulseScale
        cornerRadius = lerpHomeMorph(
            if (cornerMorphDuringPinchFraction > 0f) pinchEndCornerRadius else pinchDiameterPx / 2f,
            targetCornerRadiusPx,
            size
        )
        sourceScale = (pinchDiameterPx / min(source.width, source.height).coerceAtLeast(1f))
            .coerceIn(0f, 1f)
        expansionProgress = local
    }

    val handoff = if (legacy && closing) {
        homeMorphSmoothStep(0.015f, 0.12f, expansionProgress)
    } else {
        homeMorphSmoothStep(handoffStartFraction, handoffEndFraction, expansionProgress)
    }
    val contentAlpha = if (legacy && closing) {
        homeMorphSmoothStep(0.04f, 0.20f, expansionProgress)
    } else {
        homeMorphSmoothStep(contentStartFraction, contentEndFraction, expansionProgress)
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
    // R_outer - R_inner = 30dp - 19dp = 11dp. Derive the shell from the compact 172dp
    // action-content width so the selected capsule has identical left, right and bottom insets.
    val width = (HomeAddMenuActionContentWidthDp + HomeAddMenuConcentricInsetDp * 2f) * density
    val height = (
        HomeAddMenuContentTopPaddingDp +
            HomeAddMenuModeHeightDp +
            HomeAddMenuSectionGapDp * 2f +
            HomeAddMenuDividerHeightDp +
            HomeAddMenuActionItemHeightDp * actionCount +
            HomeAddMenuActionGapDp * (actionCount - 1).coerceAtLeast(0) +
            (HomeAddMenuConcentricInsetDp - HomeAddMenuSelectionVerticalInsetDp)
        ) * density
    val marginPx = 12f * density
    val sourceGapPx = 4f * density
    val safeBounds = adaptiveMetrics?.contentRectPx(rootSize, density)
        ?: Rect(0f, 0f, rootSize.width.toFloat(), rootSize.height.toFloat())
    val proposed = Rect(
        left = source.right - width,
        top = source.bottom + sourceGapPx,
        right = source.right,
        bottom = source.bottom + sourceGapPx + height
    )
    val clamped = clampHomeMorphTarget(proposed, rootSize, marginPx, safeBounds)
    // Preserve the capsule's exact trailing edge whenever the panel fits. The generic clamp keeps
    // the vertical/safe-area guarantees, while this final horizontal placement avoids reintroducing
    // a fixed 12dp inward shift when the capsule itself intentionally sits 8dp or 11dp from screen.
    val minimumRight = safeBounds.left + marginPx + clamped.width
    val alignedRight = source.right.coerceIn(minimumRight, safeBounds.right)
    return Rect(
        left = alignedRight - clamped.width,
        top = clamped.top,
        right = alignedRight,
        bottom = clamped.bottom
    )
}

internal fun homeAddMenuHitIndex(
    y: Float,
    modeHeight: Float,
    actionTop: Float,
    actionStep: Float,
    actionCount: Int
): Int {
    if (y <= modeHeight || actionCount <= 0 || actionStep <= 0f) return -1
    val actionY = (y - actionTop).coerceAtLeast(0f)
    return (actionY / actionStep).toInt().takeIf { it in 0 until actionCount } ?: -1
}

internal fun homeAddMenuShellClipEnabled(phase: HomeAnchoredOverlayPhase): Boolean =
    phase != HomeAnchoredOverlayPhase.Idle && phase != HomeAnchoredOverlayPhase.Open

internal fun threeDotMenuOpeningPositionProgress(expansionProgress: Float): Float =
    HomeAnchoredOpenPositionEasing.transform(expansionProgress.coerceIn(0f, 1f))

internal fun threeDotMenuOpeningSizeProgress(expansionProgress: Float): Float =
    HomeAnchoredOpenSizeEasing.transform(expansionProgress.coerceIn(0f, 1f))

private fun threeDotQuadraticBezier(
    start: Offset,
    control: Offset,
    end: Offset,
    progress: Float
): Offset {
    val value = progress.coerceIn(0f, 1f)
    val inverse = 1f - value
    return Offset(
        x = inverse * inverse * start.x +
            2f * inverse * value * control.x +
            value * value * end.x,
        y = inverse * inverse * start.y +
            2f * inverse * value * control.y +
            value * value * end.y
    )
}

/** Personalization uses the exact same liquid-object trajectory as the three-dot menu. */
internal fun homePersonalizationTrajectoryGeometry(
    source: Rect,
    target: Rect,
    rawProgress: Float,
    pinchDiameterPx: Float,
    minimumDropPx: Float,
    maximumDropPx: Float,
    maximumArcPx: Float,
    targetCornerRadiusPx: Float,
    sourcePressedScale: Float = 1f,
    closing: Boolean = false,
    verticalReboundAmplitudePx: Float = 0f,
    closingSinkOffsetPx: Float = 0f,
    closingControlDropPx: Float = 0f
): HomeAnchoredMorphGeometry = homeThreeDotMenuTrajectoryGeometry(
    source = source,
    target = target,
    rawProgress = rawProgress,
    closing = closing,
    sourceCornerRadiusPx = min(source.width, source.height) / 2f,
    targetCornerRadiusPx = targetCornerRadiusPx,
    sourcePressedScale = sourcePressedScale,
    openingPinchDiameterPx = pinchDiameterPx,
    openingMinimumDropPx = minimumDropPx,
    openingMaximumDropPx = maximumDropPx,
    openingMaximumArcPx = maximumArcPx,
    verticalReboundAmplitudePx = verticalReboundAmplitudePx,
    closingSinkOffsetPx = closingSinkOffsetPx,
    closingControlDropPx = closingControlDropPx
)

/**
 * Shared liquid-object trajectory used by the Home popups.
 *
 * Opening starts as a pressed source, drops into a small droplet, then grows while following a
 * quadratic Bezier. Closing is intentionally not the reverse: it first sinks a little, then
 * follows a separate Bezier back into the source. The first-level menu keeps its recovered
 * trailing-edge growth; larger panels use center growth so their complete shell follows the path.
 */
private fun homeLiquidSharedObjectTrajectoryGeometry(
    source: Rect,
    target: Rect,
    rawProgress: Float,
    closing: Boolean,
    sourceCornerRadiusPx: Float? = null,
    targetCornerRadiusPx: Float,
    sourcePressedScale: Float = 1f,
    openingPinchDiameterPx: Float = min(source.width, source.height),
    openingPinchFraction: Float,
    openingMinimumDropPx: Float = 0f,
    openingMaximumDropPx: Float = Float.MAX_VALUE,
    openingMaximumArcPx: Float = Float.MAX_VALUE,
    verticalReboundAmplitudePx: Float = 0f,
    closingSinkOffsetPx: Float = 0f,
    closingControlDropPx: Float = 0f,
    openingRightEdgeAnchored: Boolean
): HomeAnchoredMorphGeometry {
    val raw = rawProgress.coerceIn(0f, 1f)
    val motion = ThreeDotMenuMotion
    val pressedScale = sourcePressedScale.coerceIn(1f, 1.16f)
    val initialWidth = source.width * pressedScale
    val initialHeight = source.height * pressedScale
    val sourceRadius =
        (sourceCornerRadiusPx ?: (min(source.width, source.height) / 2f)) * pressedScale
    val center: Offset
    val width: Float
    val height: Float
    val cornerRadius: Float
    val openAmount: Float
    var sourceScale = 1f
    var anchoredRight: Float? = null

    if (!closing) {
        val pinchFraction = openingPinchFraction.coerceIn(0f, 0.90f)
        val hasOpeningPinch = pinchFraction > 0.0001f
        val pinchDiameter = openingPinchDiameterPx.coerceAtLeast(1f)
        val dropDistance = (abs(target.center.y - source.center.y) * 0.18f).coerceIn(
            openingMinimumDropPx.coerceAtLeast(0f),
            openingMaximumDropPx.coerceAtLeast(openingMinimumDropPx)
        )
        val pinchCenter = Offset(
            x = source.center.x,
            y = source.center.y + dropDistance
        )

        if (hasOpeningPinch && raw <= pinchFraction) {
            val pinchProgress = (raw / pinchFraction).coerceIn(0f, 1f)
            val fallProgress = HomeAnchoredFallEasing.transform(pinchProgress)
            val diameterProgress = threeDotMenuOpeningSizeProgress(pinchProgress)
            width = lerpHomeMorph(initialWidth, pinchDiameter, diameterProgress)
            height = lerpHomeMorph(initialHeight, pinchDiameter, diameterProgress)
            center = Offset(
                x = source.center.x,
                y = lerpHomeMorph(source.center.y, pinchCenter.y, fallProgress)
            )
            cornerRadius = min(
                lerpHomeMorph(sourceRadius, pinchDiameter / 2f, diameterProgress),
                min(width, height) / 2f
            )
            sourceScale = min(width, height) /
                min(initialWidth, initialHeight).coerceAtLeast(1f)
            openAmount = 0f
        } else {
            val expansion = if (hasOpeningPinch) {
                ((raw - pinchFraction) / (1f - pinchFraction)).coerceIn(0f, 1f)
            } else {
                raw
            }
            val positionProgress = threeDotMenuOpeningPositionProgress(expansion)
            val sizeProgress = threeDotMenuOpeningSizeProgress(expansion)
            val expansionStartCenter = if (hasOpeningPinch) pinchCenter else source.center
            val expansionStartWidth = if (hasOpeningPinch) pinchDiameter else initialWidth
            val expansionStartHeight = if (hasOpeningPinch) pinchDiameter else initialHeight
            val expansionStartCorner = if (hasOpeningPinch) pinchDiameter / 2f else sourceRadius
            val deltaY = target.center.y - expansionStartCenter.y
            val arc = min(openingMaximumArcPx.coerceAtLeast(0f), abs(deltaY) * 0.22f)
            val control = Offset(
                x = (expansionStartCenter.x + target.center.x) / 2f,
                y = (expansionStartCenter.y + target.center.y) / 2f + sign(deltaY) * arc
            )
            val trajectoryCenter = threeDotQuadraticBezier(
                start = expansionStartCenter,
                control = control,
                end = target.center,
                progress = positionProgress
            )
            val reboundPeak = motion.OpenReboundPeakFraction
            val reboundOffset = if (expansion <= reboundPeak) {
                verticalReboundAmplitudePx * homeMorphSmoothStep(
                    reboundPeak * 0.45f,
                    reboundPeak,
                    expansion
                )
            } else {
                verticalReboundAmplitudePx * (
                    1f - homeMorphSmoothStep(reboundPeak, 1f, expansion)
                    )
            }
            center = Offset(
                x = trajectoryCenter.x,
                y = trajectoryCenter.y + reboundOffset
            )
            val pulseWindow = ((expansion - 0.82f) / 0.18f).coerceIn(0f, 1f)
            val pulseScale = 1f + sin(PI.toFloat() * pulseWindow) * 0.008f
            width = lerpHomeMorph(expansionStartWidth, target.width, sizeProgress) * pulseScale
            height = lerpHomeMorph(expansionStartHeight, target.height, sizeProgress) * pulseScale
            cornerRadius = lerpHomeMorph(expansionStartCorner, targetCornerRadiusPx, sizeProgress)
            if (openingRightEdgeAnchored) {
                val expansionStartRight = expansionStartCenter.x + expansionStartWidth / 2f
                anchoredRight = lerpHomeMorph(expansionStartRight, target.right, sizeProgress)
            }
            sourceScale = if (hasOpeningPinch) {
                pinchDiameter / min(initialWidth, initialHeight).coerceAtLeast(1f)
            } else {
                1f
            }
            openAmount = expansion
        }
    } else {
        val closeProgress = 1f - raw
        val sinkProgress = homeMorphSmoothStep(0f, motion.CloseSinkEndFraction, closeProgress)
        val returnClock = ((closeProgress - motion.CloseReturnStartFraction) /
            (1f - motion.CloseReturnStartFraction)).coerceIn(0f, 1f)
        val returnProgress = motion.CloseReturnEasing.transform(returnClock)
        val returnBlend = homeMorphSmoothStep(
            motion.CloseReturnStartFraction,
            motion.CloseReturnBlendEndFraction,
            closeProgress
        )
        val sunkCenter = Offset(
            x = target.center.x,
            y = target.center.y + closingSinkOffsetPx * sinkProgress
        )
        val returnStart = Offset(
            x = target.center.x,
            y = target.center.y + closingSinkOffsetPx
        )
        val returnControl = Offset(
            x = lerpHomeMorph(
                returnStart.x,
                source.center.x,
                motion.CloseControlXFraction
            ),
            y = returnStart.y + closingControlDropPx
        )
        val returnCenter = threeDotQuadraticBezier(
            start = returnStart,
            control = returnControl,
            end = source.center,
            progress = returnProgress
        )
        center = Offset(
            x = lerpHomeMorph(sunkCenter.x, returnCenter.x, returnBlend),
            y = lerpHomeMorph(sunkCenter.y, returnCenter.y, returnBlend)
        )

        val shrinkProgress = 1f - (1f - returnProgress).pow(1.12f)
        val compressionProgress = when {
            closeProgress <= 0.10f -> homeMorphSmoothStep(0f, 0.10f, closeProgress)
            closeProgress <= 0.22f -> 1f - homeMorphSmoothStep(0.10f, 0.22f, closeProgress)
            else -> 0f
        }
        width = lerpHomeMorph(target.width, initialWidth, shrinkProgress)
        height = lerpHomeMorph(target.height, initialHeight, shrinkProgress) *
            (1f - motion.CloseSinkCompression * compressionProgress)
        cornerRadius = lerpHomeMorph(targetCornerRadiusPx, sourceRadius, shrinkProgress)
        openAmount = 1f - shrinkProgress
    }

    val handoff = if (closing) {
        homeMorphSmoothStep(0f, 0.18f, openAmount)
    } else {
        homeMorphSmoothStep(0.05f, 0.34f, openAmount)
    }
    val left = anchoredRight?.minus(width) ?: (center.x - width / 2f)
    val right = anchoredRight ?: (center.x + width / 2f)
    return HomeAnchoredMorphGeometry(
        rect = Rect(
            left = left,
            top = center.y - height / 2f,
            right = right,
            bottom = center.y + height / 2f
        ),
        cornerRadiusPx = cornerRadius,
        sourceScale = sourceScale,
        sourceAlpha = 1f - handoff,
        surfaceAlpha = handoff,
        contentAlpha = if (closing) {
            homeMorphSmoothStep(0.16f, 0.46f, openAmount)
        } else {
            homeMorphSmoothStep(0.18f, 0.55f, openAmount)
        },
        pathProgress = raw,
        expansionProgress = openAmount
    )
}

/** The recovered 02:38 menu motion grows from its trailing edge. */
internal fun homeThreeDotMenuTrajectoryGeometry(
    source: Rect,
    target: Rect,
    rawProgress: Float,
    closing: Boolean,
    sourceCornerRadiusPx: Float? = null,
    targetCornerRadiusPx: Float,
    sourcePressedScale: Float = 1f,
    openingPinchDiameterPx: Float = min(source.width, source.height),
    openingMinimumDropPx: Float = 0f,
    openingMaximumDropPx: Float = Float.MAX_VALUE,
    openingMaximumArcPx: Float = Float.MAX_VALUE,
    verticalReboundAmplitudePx: Float = 0f,
    closingSinkOffsetPx: Float = 0f,
    closingControlDropPx: Float = 0f
): HomeAnchoredMorphGeometry = homeLiquidSharedObjectTrajectoryGeometry(
    source = source,
    target = target,
    rawProgress = rawProgress,
    closing = closing,
    sourceCornerRadiusPx = sourceCornerRadiusPx,
    targetCornerRadiusPx = targetCornerRadiusPx,
    sourcePressedScale = sourcePressedScale,
    openingPinchDiameterPx = openingPinchDiameterPx,
    openingPinchFraction = ThreeDotMenuMotion.OpenPinchFraction,
    openingMinimumDropPx = openingMinimumDropPx,
    openingMaximumDropPx = openingMaximumDropPx,
    openingMaximumArcPx = openingMaximumArcPx,
    verticalReboundAmplitudePx = verticalReboundAmplitudePx,
    closingSinkOffsetPx = closingSinkOffsetPx,
    closingControlDropPx = closingControlDropPx,
    openingRightEdgeAnchored = true
)

/**
 * Center-growing variant for large Home panels. Its rect is always derived from the Bezier center
 * plus width/height, avoiding the first-level menu's intentional trailing-edge expansion.
 */
internal fun homeCenteredSharedObjectTrajectoryGeometry(
    source: Rect,
    target: Rect,
    rawProgress: Float,
    closing: Boolean,
    sourceCornerRadiusPx: Float? = null,
    targetCornerRadiusPx: Float,
    sourcePressedScale: Float = 1f,
    openingPinchDiameterPx: Float = min(source.width, source.height),
    openingPinchFraction: Float = ThreeDotMenuMotion.OpenPinchFraction,
    openingMinimumDropPx: Float = 0f,
    openingMaximumDropPx: Float = Float.MAX_VALUE,
    openingMaximumArcPx: Float = Float.MAX_VALUE,
    verticalReboundAmplitudePx: Float = 0f,
    closingSinkOffsetPx: Float = 0f,
    closingControlDropPx: Float = 0f
): HomeAnchoredMorphGeometry = homeLiquidSharedObjectTrajectoryGeometry(
    source = source,
    target = target,
    rawProgress = rawProgress,
    closing = closing,
    sourceCornerRadiusPx = sourceCornerRadiusPx,
    targetCornerRadiusPx = targetCornerRadiusPx,
    sourcePressedScale = sourcePressedScale,
    openingPinchDiameterPx = openingPinchDiameterPx,
    openingPinchFraction = openingPinchFraction,
    openingMinimumDropPx = openingMinimumDropPx,
    openingMaximumDropPx = openingMaximumDropPx,
    openingMaximumArcPx = openingMaximumArcPx,
    verticalReboundAmplitudePx = verticalReboundAmplitudePx,
    closingSinkOffsetPx = closingSinkOffsetPx,
    closingControlDropPx = closingControlDropPx,
    openingRightEdgeAnchored = false
)

internal fun homeMorphWithVerticalRebound(
    geometry: HomeAnchoredMorphGeometry,
    closing: Boolean,
    overshootPx: Float,
    peakProgress: Float,
    legacyClosingRebound: Boolean = false
): HomeAnchoredMorphGeometry {
    // Closing has its own direct return and must not replay the opening bounce backwards.
    if (!legacyClosingRebound && (closing || overshootPx <= 0f)) return geometry
        val reboundOffset = run {
            val expansion = geometry.expansionProgress
            val peak = peakProgress.coerceIn(0.20f, 0.80f)
            if (legacyClosingRebound) {
                if (expansion <= peak) {
                    overshootPx * homeMorphSmoothStep(peak * 0.45f, peak, expansion)
                } else {
                    overshootPx * (1f - homeMorphSmoothStep(peak, 1f, expansion))
                }
            } else if (expansion <= peak) {
                // The bounce starts earlier, during the expansion, so the shell dips down while
                // it is still growing instead of wobbling after arrival.
                overshootPx * homeMorphSmoothStep(peak * 0.20f, peak, expansion)
            } else if (expansion >= HomeAnchoredOpenSettleStartFraction) {
                0f
            } else {
                // One-shot bounce: after bottoming out at the overshoot limit the shell returns
                // to the target in a single smooth pass and settles — no oscillation.
                overshootPx * (
                    1f - homeMorphSmoothStep(
                        peak,
                        HomeAnchoredOpenSettleStartFraction,
                        expansion
                    )
                    )
            }
        }
    return geometry.copy(
        rect = Rect(
            left = geometry.rect.left,
            top = geometry.rect.top + reboundOffset,
            right = geometry.rect.right,
            bottom = geometry.rect.bottom + reboundOffset
        )
    )
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
    homeMode: HomeMode,
    onHomeModeChange: (HomeMode) -> Unit,
    adaptiveMetrics: HomeAdaptiveMetrics,
    modifier: Modifier = Modifier,
    onDismissRequest: () -> Unit,
    onAddMenuBoundsChanged: (Rect) -> Unit = {},
    onSourceFollowThrough: (Rect) -> Unit = {},
    suppressClose: Boolean = false,
    onSilentDisposed: () -> Unit = {},
    personalizePreviewProgress: Float = 0f,
    sourceContent: @Composable BoxScope.(HomeAnchoredOverlayKind, Modifier) -> Unit,
    personalizeContent: @Composable (Modifier) -> Unit
) {
    var renderedRequest by remember { mutableStateOf<HomeAnchoredOverlayRequest?>(null) }
    var panelContentPrepared by remember { mutableStateOf(false) }
    val personalizeContentRecorded = remember {
        java.util.concurrent.atomic.AtomicBoolean(false)
    }
    var rootSize by remember { mutableStateOf(IntSize.Zero) }
    val latestOnDismissRequest by rememberUpdatedState(onDismissRequest)
    val latestOnAddMenuBoundsChanged by rememberUpdatedState(onAddMenuBoundsChanged)
    val latestOnSourceFollowThrough by rememberUpdatedState(onSourceFollowThrough)
    val latestOnSilentDisposed by rememberUpdatedState(onSilentDisposed)

    LaunchedEffect(request, adaptiveMetrics.profile) {
        if (request != null) {
            renderedRequest = request
            panelContentPrepared = request.kind != HomeAnchoredOverlayKind.Personalize
            personalizeContentRecorded.set(false)
            motionState.renderedKind = request.kind
            motionState.phase = HomeAnchoredOverlayPhase.Preparing
            motionState.progress.snapTo(0f)
            motionState.backgroundZoom.snapTo(1f)
            var waitedFrames = 0
            while (waitedFrames < 12 && (rootSize.width <= 0 || rootSize.height <= 0)) {
                withFrameNanos { }
                waitedFrames++
            }
            if (request.kind == HomeAnchoredOverlayKind.Personalize) {
                // Give the week-view scene cache one complete preparation frame before mounting
                // the slider tree. Recording both the full week grid and every personalization
                // control in the same frame was the remaining first-open spike on dense schedules.
                withFrameNanos { }
                // Compose, measure and record the heavy slider tree before geometry starts moving.
                // Wait for the draw callback instead of assuming that two display frames are enough
                // on every device. This prevents Opening from racing the first GPU recording.
                panelContentPrepared = true
                var contentWaitFrames = 0
                while (!personalizeContentRecorded.get() && contentWaitFrames < 8) {
                    withFrameNanos { }
                    contentWaitFrames++
                }
                // Keep one handoff frame after the layer is recorded so Kyant's backdrop consumer
                // and the cached settings tree never enter the same moving frame.
                withFrameNanos { }
            }
            motionState.phase = HomeAnchoredOverlayPhase.Opening
            coroutineScope {
                launch {
                    motionState.progress.animateTo(
                        1f,
                        tween(
                            durationMillis = if (
                                request.kind == HomeAnchoredOverlayKind.Personalize
                            ) {
                                HomePersonalizeMorphOpenDurationMillis
                            } else {
                                HomeAddMenuMorphOpenDurationMillis
                            },
                            easing = LinearEasing
                        )
                    )
                }
                if (request.kind == HomeAnchoredOverlayKind.Personalize && !adaptiveMetrics.isLargeScreen) {
                    launch {
                        motionState.backgroundZoom.animateTo(
                            HomeAnchoredMorphBackgroundScale,
                            tween(
                                durationMillis = HomePersonalizeMorphOpenDurationMillis,
                                delayMillis = HomeAnchoredMorphBackgroundDelayMillis,
                                easing = HomeAnchoredBackgroundEasing
                            )
                        )
                    }
                }
            }
            motionState.phase = HomeAnchoredOverlayPhase.Open
        } else if (renderedRequest != null) {
            if (suppressClose) {
                // Silent cleanup: the destination owns the button return. Dispose this hidden
                // first-level menu without playing a second visible Close or follow-through.
                motionState.progress.snapTo(0f)
                motionState.backgroundZoom.snapTo(1f)
                motionState.phase = HomeAnchoredOverlayPhase.Disposing
                panelContentPrepared = false
                renderedRequest = null
                motionState.renderedKind = null
                motionState.phase = HomeAnchoredOverlayPhase.Idle
                latestOnSilentDisposed()
            } else {
                motionState.phase = HomeAnchoredOverlayPhase.Closing
                coroutineScope {
                    launch {
                        motionState.progress.animateTo(
                            0f,
                            tween(
                                durationMillis = if (
                                    renderedRequest?.kind == HomeAnchoredOverlayKind.Personalize
                                ) {
                                    HomePersonalizeMorphCloseDurationMillis
                                } else {
                                    HomeAddMenuMorphCloseDurationMillis
                                },
                                easing = LinearEasing
                            )
                        )
                    }
                    if (motionState.backgroundZoom.value > 1.0001f) {
                        launch {
                            motionState.backgroundZoom.animateTo(
                                1f,
                                tween(
                                    durationMillis = HomePersonalizeMorphCloseDurationMillis,
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
            }
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
        if (shown.kind == HomeAnchoredOverlayKind.Personalize) {
            HomePersonalizationAnimatedOverlay(
                sourceBounds = shown.sourceBoundsInRoot,
                sourcePressedScale = shown.sourcePressedScale,
                targetRect = targetRect,
                rootSize = rootSize,
                motionState = motionState,
                backdrop = backdrop,
                config = config,
                adaptiveMetrics = adaptiveMetrics,
                previewProgress = personalizePreviewProgress,
                contentMounted = panelContentPrepared,
                onContentLaidOut = {},
                onContentRecorded = { personalizeContentRecorded.set(true) },
                onDismissRequest = { latestOnDismissRequest() },
                sourceContent = { sourceModifier ->
                    sourceContent(HomeAnchoredOverlayKind.Personalize, sourceModifier)
                },
                content = personalizeContent
            )
            return@Box
        }
        // Personalize is handled above, so this branch is the Add menu. Keep all animation-tick
        // reads inside derived state and deferred modifier lambdas instead of recomposing the host.
        val morphSpec = remember(
            shown.sourceBoundsInRoot,
            shown.sourcePressedScale,
            targetRect,
            adaptiveMetrics,
            density.density
        ) {
            legacyThreeDotMenuMorphSpec(
                source = shown.sourceBoundsInRoot,
                target = targetRect,
                sourceCornerRadiusPx = with(density) { 21.dp.toPx() },
                targetCornerRadiusPx = with(density) { HomeAddMenuTargetCornerDp.dp.toPx() },
                sourcePressedScale = shown.sourcePressedScale,
                openingPinchDiameterPx = with(density) {
                    ThreeDotMenuMotion.OpenPinchDiameterDp.dp.toPx()
                },
                openingMinimumDropPx = with(density) {
                    ThreeDotMenuMotion.OpenMinimumDropDp.dp.toPx()
                },
                openingMaximumDropPx = with(density) {
                    ThreeDotMenuMotion.OpenMaximumDropDp.dp.toPx()
                },
                openingMaximumArcPx = with(density) {
                    adaptiveMetrics.animationArc.toPx()
                },
                verticalReboundAmplitudePx = with(density) {
                    ThreeDotMenuMotion.OpenReboundAmplitudeDp.dp.toPx()
                },
                closingSinkOffsetPx = with(density) {
                    ThreeDotMenuMotion.CloseSinkOffsetDp.dp.toPx()
                },
                closingControlDropPx = with(density) {
                    ThreeDotMenuMotion.CloseControlDropDp.dp.toPx()
                }
            )
        }
        val geometry = remember(morphSpec, motionState) {
            derivedStateOf {
                morphSpec.homeGeometry(
                    source = shown.sourceBoundsInRoot,
                    target = targetRect,
                    rawProgress = motionState.progress.value,
                    closing = motionState.phase == HomeAnchoredOverlayPhase.Closing
                )
            }
        }
        var sourceHandedOff by remember(shown.kind) { mutableStateOf(false) }
        LaunchedEffect(shown.kind, motionState.phase) {
            if (shown.kind != HomeAnchoredOverlayKind.Add) return@LaunchedEffect
            if (suppressClose) return@LaunchedEffect
            if (motionState.phase != HomeAnchoredOverlayPhase.Closing) return@LaunchedEffect
            // Hand the collapsing shell over to the follow-through button while the droplet is
            // still at the source anchor, so the real button never pops in at rest.
            snapshotFlow { motionState.progress.value }.first { it <= 0.02f }
            if (motionState.phase != HomeAnchoredOverlayPhase.Closing) return@LaunchedEffect
            sourceHandedOff = true
            latestOnSourceFollowThrough(geometry.value.rect)
        }
        val shape = remember(geometry, density) {
            DeferredHomeMorphShape(geometry, continuous = false, density = density)
        }
        val settledSurfaceShape = remember {
            RoundedCornerShape(HomeAddMenuTargetCornerDp.dp)
        }
        val sourcePressedScale = shown.sourcePressedScale.coerceIn(1f, 1.16f)
        val maxContentBlurPx = with(density) { 5.dp.toPx() }
        var outsideDragHighlightedIndex by remember(shown.kind) { mutableIntStateOf(-1) }
        val outsideDragHaptic = LocalHapticFeedback.current
        val menuContentTopPaddingPx = with(density) { HomeAddMenuContentTopPaddingDp.dp.toPx() }
        val menuContentHorizontalPaddingPx = with(density) {
            (HomeAddMenuConcentricInsetDp - HomeAddMenuActionColumnInsetDp).dp.toPx()
        }
        val menuModeHeightPx = with(density) { HomeAddMenuModeHeightDp.dp.toPx() }
        val menuActionTopPx = with(density) {
            (
                HomeAddMenuModeHeightDp + HomeAddMenuSectionGapDp * 2f +
                    HomeAddMenuDividerHeightDp
                ).dp.toPx()
        }
        val menuActionStepPx = with(density) {
            (HomeAddMenuActionItemHeightDp + HomeAddMenuActionGapDp).dp.toPx()
        }

        fun targetMenuPosition(rootPosition: Offset): Offset? {
            val rect = geometry.value.rect
            if (!rect.contains(rootPosition) || rect.width <= 1f || rect.height <= 1f) return null
            return Offset(
                x = (rootPosition.x - rect.left) / rect.width * targetRect.width,
                y = (rootPosition.y - rect.top) / rect.height * targetRect.height
            )
        }

        fun outsideDragActionIndex(rootPosition: Offset): Int {
            val targetPosition = targetMenuPosition(rootPosition) ?: return -1
            val innerY = targetPosition.y - menuContentTopPaddingPx
            // The divider/section spacing is visual only. Fold its hit area into the
            // nearest action so sliding through them never drops gesture ownership. In particular,
            // the full strip below the mode switch belongs to the first "添加单节课" action.
            return homeAddMenuHitIndex(
                y = innerY,
                modeHeight = menuModeHeightPx,
                actionTop = menuActionTopPx,
                actionStep = menuActionStepPx,
                actionCount = addActions.size
            )
        }

        val outsideToMenuGesture = Modifier.pointerInput(addActions, homeMode, shown.kind) {
            awaitEachGesture {
                val down = awaitFirstDown(
                    requireUnconsumed = false,
                    pass = PointerEventPass.Initial
                )
                val downPosition = down.position
                var lastPosition = downPosition
                var enteredMenu = false
                var completedNormally = false
                try {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        lastPosition = change.position
                        val inside = targetMenuPosition(lastPosition) != null
                        enteredMenu = enteredMenu || inside
                        val nextIndex = outsideDragActionIndex(lastPosition)
                        if (nextIndex != outsideDragHighlightedIndex) {
                            outsideDragHighlightedIndex = nextIndex
                            if (nextIndex in addActions.indices) {
                                outsideDragHaptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        }
                        if (!change.pressed) {
                            completedNormally = change.changedToUpIgnoreConsumed()
                            break
                        }
                    }
                } finally {
                    val targetPosition = targetMenuPosition(lastPosition)
                    val selectedIndex = outsideDragHighlightedIndex
                    outsideDragHighlightedIndex = -1
                    if (completedNormally && targetPosition != null) {
                        val innerX = targetPosition.x - menuContentHorizontalPaddingPx
                        val innerY = targetPosition.y - menuContentTopPaddingPx
                        if (innerY in 0f..menuModeHeightPx) {
                            val innerWidth = targetRect.width - menuContentHorizontalPaddingPx * 2f
                            val targetMode = if (innerX < innerWidth / 2f) HomeMode.Day else HomeMode.Week
                            if (targetMode != homeMode) {
                                outsideDragHaptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onHomeModeChange(targetMode)
                            }
                        } else if (selectedIndex in addActions.indices) {
                            addActions[selectedIndex].onClick()
                        }
                    } else if (completedNormally && !enteredMenu) {
                        val dx = lastPosition.x - downPosition.x
                        val dy = lastPosition.y - downPosition.y
                        val slop = viewConfiguration.touchSlop
                        if (dx * dx + dy * dy <= slop * slop) latestOnDismissRequest()
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(outsideToMenuGesture)
        )

        Box(
            modifier = Modifier
                .offset {
                    val current = geometry.value.rect
                    IntOffset(
                        current.left.roundToInt(),
                        current.top.roundToInt()
                    )
                }
                .graphicsLayer {
                    // The moving shell owns clipping only while geometry is changing. At Open the
                    // static Kyant surface below owns its real 34dp outline, so highlight and shadow
                    // remain free to render outside the fill without exposing a stale dynamic shape.
                    clip = homeAddMenuShellClipEnabled(motionState.phase)
                    this.shape = shape
                }
                .layout { measurable, _ ->
                    // Measure the heavy glass subtree once at its final target size. The animated
                    // shell changes its reported size and clips the child around the shell center.
                    val targetWidth = targetRect.width.roundToInt().coerceAtLeast(1)
                    val targetHeight = targetRect.height.roundToInt().coerceAtLeast(1)
                    val placeable = measurable.measure(Constraints.fixed(targetWidth, targetHeight))
                    val current = geometry.value.rect
                    val width = current.width.roundToInt().coerceAtLeast(1)
                    val height = current.height.roundToInt().coerceAtLeast(1)
                    layout(width, height) {
                        placeable.place((width - targetWidth) / 2, (height - targetHeight) / 2)
                    }
                }
                .clickable(
                    interactionSource = remember {
                        androidx.compose.foundation.interaction.MutableInteractionSource()
                    },
                    indication = null,
                    onClick = {}
                ),
            contentAlignment = Alignment.Center
        ) {
            HomeAddMenuMorphPanel(
                backdrop = backdrop,
                config = config,
                actions = addActions,
                homeMode = homeMode,
                onHomeModeChange = onHomeModeChange,
                targetSizeProvider = {
                    IntSize(targetRect.width.roundToInt(), targetRect.height.roundToInt())
                },
                surfaceAlphaProvider = { geometry.value.surfaceAlpha },
                contentAlphaProvider = { geometry.value.contentAlpha },
                contentBlurRadiusPxProvider = {
                    maxContentBlurPx * (
                        1f - homeMorphSmoothStep(0.42f, 0.98f, geometry.value.expansionProgress)
                        )
                },
                externalHighlightedIndex = outsideDragHighlightedIndex,
                interactive = motionState.phase == HomeAnchoredOverlayPhase.Opening ||
                    motionState.phase == HomeAnchoredOverlayPhase.Open,
                shape = settledSurfaceShape,
                modifier = Modifier.fillMaxSize()
            )

            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .requiredSize(
                        width = with(density) {
                            (shown.sourceBoundsInRoot.width * sourcePressedScale).toDp()
                        },
                        height = with(density) {
                            (shown.sourceBoundsInRoot.height * sourcePressedScale).toDp()
                        }
                    )
                    .graphicsLayer {
                        val current = geometry.value
                        alpha = if (sourceHandedOff) 0f else current.sourceAlpha
                        scaleX = current.sourceScale
                        scaleY = current.sourceScale
                        compositingStrategy = CompositingStrategy.Offscreen
                        val sourceContentBlurPx = maxContentBlurPx * homeMorphSmoothStep(
                            0f,
                            0.34f,
                            current.pathProgress
                        )
                        renderEffect = if (sourceContentBlurPx > 0.01f) {
                            BlurEffect(sourceContentBlurPx, sourceContentBlurPx, TileMode.Clamp)
                        } else null
                    }
                    .clearAndSetSemantics { }
            ) {
                sourceContent(shown.kind, Modifier.fillMaxSize())
            }

            if (motionState.phase != HomeAnchoredOverlayPhase.Opening &&
                motionState.phase != HomeAnchoredOverlayPhase.Open
            ) {
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

private class DeferredHomeMorphShape(
    private val geometry: State<HomeAnchoredMorphGeometry>,
    private val continuous: Boolean,
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
        val corner = (geometry.value.cornerRadiusPx / density.density.coerceAtLeast(0.001f)).dp
        val shape = if (continuous) RoundedRectangle(corner) else RoundedCornerShape(corner)
        return shape.createOutline(size, layoutDirection, density)
    }

    override fun copy(
        topStart: CornerSize,
        topEnd: CornerSize,
        bottomEnd: CornerSize,
        bottomStart: CornerSize
    ): CornerBasedShape = DeferredHomeMorphShape(
        geometry = geometry,
        continuous = continuous,
        density = density,
        topStart = topStart,
        topEnd = topEnd,
        bottomEnd = bottomEnd,
        bottomStart = bottomStart
    )
}

@Composable
private fun BoxScope.HomePersonalizationAnimatedOverlay(
    sourceBounds: Rect,
    sourcePressedScale: Float,
    targetRect: Rect,
    rootSize: IntSize,
    motionState: HomeAnchoredMorphState,
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    adaptiveMetrics: HomeAdaptiveMetrics,
    previewProgress: Float,
    contentMounted: Boolean,
    onContentLaidOut: () -> Unit,
    onContentRecorded: () -> Unit,
    onDismissRequest: () -> Unit,
    sourceContent: @Composable (Modifier) -> Unit,
    content: @Composable (Modifier) -> Unit
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val latestPreviewProgress = rememberUpdatedState(previewProgress)
    val morphSpec = remember(
        sourceBounds,
        sourcePressedScale,
        targetRect,
        adaptiveMetrics,
        density.density
    ) {
        legacyPersonalizationMorphSpec(
            source = sourceBounds,
            target = targetRect,
            pinchDiameterPx = with(density) { 18.dp.toPx() },
            minimumDropPx = with(density) { 36.dp.toPx() },
            maximumDropPx = with(density) { 72.dp.toPx() },
            maximumArcPx = with(density) { adaptiveMetrics.animationArc.toPx() },
            targetCornerRadiusPx = with(density) { 28.dp.toPx() },
            sourcePressedScale = sourcePressedScale,
            verticalReboundAmplitudePx = with(density) {
                ThreeDotMenuMotion.OpenReboundAmplitudeDp.dp.toPx()
            },
            closingSinkOffsetPx = with(density) {
                ThreeDotMenuMotion.CloseSinkOffsetDp.dp.toPx()
            },
            closingControlDropPx = with(density) {
                ThreeDotMenuMotion.CloseControlDropDp.dp.toPx()
            }
        )
    }
    val geometry = remember(morphSpec, motionState) {
        derivedStateOf {
            // Personalization and the three-dot menu are one liquid object choreography. The
            // performance work below only changes recording and backdrop sampling.
            morphSpec.homeGeometry(
                source = sourceBounds,
                target = targetRect,
                rawProgress = motionState.progress.value,
                closing = motionState.phase == HomeAnchoredOverlayPhase.Closing
            )
        }
    }
    val blurProgress = remember(geometry, latestPreviewProgress) {
        derivedStateOf {
            personalizeBackdropBlurLayerProgress(
                expansionProgress = geometry.value.expansionProgress,
                previewProgress = latestPreviewProgress.value
            )
        }
    }
    val warmupBackdropEffects = motionState.phase == HomeAnchoredOverlayPhase.Preparing
    val progressiveBackdropBlurProgress by remember(blurProgress, warmupBackdropEffects) {
        derivedStateOf {
            val quantized = quantizeHomeProgressiveBackdropBlurProgress(blurProgress.value)
            if (warmupBackdropEffects) {
                // A nearly invisible first bucket gives Android a complete target-sized effect
                // chain before the first visible Opening frame. It is not a replacement bitmap;
                // Open still samples the live backdrop and hosts the real form.
                max(quantized, 1f / HomeProgressiveBackdropBlurStepCount)
            } else {
                quantized
            }
        }
    }
    val showAura by remember(
        progressiveBackdropBlurProgress,
        backdrop,
        adaptiveMetrics.isLargeScreen,
        warmupBackdropEffects
    ) {
        derivedStateOf {
            adaptiveMetrics.isLargeScreen &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                backdrop != null &&
                (warmupBackdropEffects || progressiveBackdropBlurProgress > 0.005f)
        }
    }
    val shape = remember(geometry, density) {
        DeferredHomeMorphShape(geometry, continuous = true, density = density)
    }
    val maxContentBlurPx = with(density) { 5.dp.toPx() }
    val personalizeContentLayer = rememberGraphicsLayer()
    val personalizeBlurredContentLayer = rememberGraphicsLayer()
    val fixedContentBlurEffect = remember(maxContentBlurPx) {
        BlurEffect(maxContentBlurPx, maxContentBlurPx, TileMode.Clamp)
    }
    val preparingContentRecorded = remember { java.util.concurrent.atomic.AtomicBoolean(false) }
    val closingBlurRecorded = remember { java.util.concurrent.atomic.AtomicBoolean(false) }
    LaunchedEffect(contentMounted) {
        if (!contentMounted) {
            preparingContentRecorded.set(false)
            closingBlurRecorded.set(false)
        }
    }
    val targetWidth = with(density) { targetRect.width.toDp() }
    val targetHeight = with(density) { targetRect.height.toDp() }

    Box(
        Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null,
                onClick = onDismissRequest
            )
    )

    if (showAura && backdrop != null) {
        DeferredPersonalizeBackdropAura(
            backdrop = backdrop,
            leftFeatherPxProvider = {
                with(density) { 104.dp.toPx() } * geometry.value.expansionProgress.coerceIn(0f, 1f)
            },
            blurProgressProvider = { progressiveBackdropBlurProgress },
            alphaProvider = {
                val visibleAlpha = geometry.value.surfaceAlpha * blurProgress.value
                if (warmupBackdropEffects) max(visibleAlpha, 0.001f) else visibleAlpha
            },
            modifier = Modifier
                .offset {
                    val leftFeatherPx = with(density) { 104.dp.toPx() } *
                        geometry.value.expansionProgress.coerceIn(0f, 1f)
                    IntOffset(
                        (geometry.value.rect.left - leftFeatherPx).coerceAtLeast(0f).roundToInt(),
                        (geometry.value.rect.top - with(density) { 56.dp.toPx() })
                            .coerceAtLeast(0f).roundToInt()
                    )
                }
                .layout { measurable, _ ->
                    val leftFeatherPx = with(density) { 104.dp.toPx() } *
                        geometry.value.expansionProgress.coerceIn(0f, 1f)
                    val left = (geometry.value.rect.left - leftFeatherPx).coerceAtLeast(0f)
                    val rightFeatherPx = with(density) { 56.dp.toPx() }
                    val verticalFeatherPx = with(density) { 56.dp.toPx() }
                    val width = (geometry.value.rect.width + leftFeatherPx + rightFeatherPx)
                        .roundToInt().coerceAtLeast(1)
                    val height = (geometry.value.rect.height + verticalFeatherPx * 2f)
                        .roundToInt().coerceAtLeast(1)
                    val placeable = measurable.measure(Constraints.fixed(width, height))
                    layout(width, height) { placeable.place(0, 0) }
                }
        )
    }

    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    geometry.value.rect.left.roundToInt(),
                    geometry.value.rect.top.roundToInt()
                )
            }
            .layout { measurable, _ ->
                val current = geometry.value.rect
                val width = current.width.roundToInt().coerceAtLeast(1)
                val height = current.height.roundToInt().coerceAtLeast(1)
                // The shell and Kyant surface remain in the animated rect's coordinate space.
                // DeferredHomePersonalizeMorphPanel already measures only the heavy form content at
                // target size; moving this whole node into target coordinates offsets the Morph.
                val placeable = measurable.measure(Constraints.fixed(width, height))
                layout(width, height) { placeable.place(0, 0) }
            }
            .graphicsLayer {
                val fullOpenEndpoint = motionState.phase == HomeAnchoredOverlayPhase.Open &&
                    geometry.value.pathProgress >= 0.999f
                clip = !fullOpenEndpoint
                this.shape = shape
                compositingStrategy = if (fullOpenEndpoint) {
                    CompositingStrategy.Auto
                } else {
                    CompositingStrategy.Offscreen
                }
            }
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null,
                onClick = {}
            ),
        contentAlignment = Alignment.Center
    ) {
        DeferredHomePersonalizeMorphPanel(
            backdrop = backdrop,
            config = config,
            targetWidth = targetWidth,
            targetHeight = targetHeight,
            shape = shape,
            progressiveBlur = adaptiveMetrics.isLargeScreen,
            warmupBackdropEffects = warmupBackdropEffects,
            surfaceAlphaProvider = {
                geometry.value.surfaceAlpha * if (adaptiveMetrics.isLargeScreen) {
                    1f
                } else {
                    1f - latestPreviewProgress.value.coerceIn(0f, 1f)
                }
            },
            contentAlphaProvider = { geometry.value.contentAlpha },
            contentBlurRadiusPxProvider = {
                // Blur is now a crossfade between two pre-recorded GPU layers below. Leaving this
                // outer target-sized layer unblurred avoids recomputing a full form RenderEffect on
                // every animation tick.
                0f
            },
            backdropBlurProgressProvider = { progressiveBackdropBlurProgress },
            onContentLaidOut = onContentLaidOut,
            modifier = Modifier.fillMaxSize(),
            content = { contentModifier ->
                if (contentMounted) {
                    content(
                        contentModifier.drawWithContent {
                            val phase = motionState.phase
                            if (phase == HomeAnchoredOverlayPhase.Preparing &&
                                preparingContentRecorded.compareAndSet(false, true)
                            ) {
                                personalizeContentLayer.record {
                                    this@drawWithContent.drawContent()
                                }
                                personalizeBlurredContentLayer.record(
                                    size = IntSize(
                                        size.width.roundToInt().coerceAtLeast(1),
                                        size.height.roundToInt().coerceAtLeast(1)
                                    )
                                ) {
                                    drawLayer(personalizeContentLayer)
                                }
                                personalizeBlurredContentLayer.renderEffect = fixedContentBlurEffect
                                onContentRecorded()
                            }
                            if (phase == HomeAnchoredOverlayPhase.Open) {
                                preparingContentRecorded.set(true)
                                closingBlurRecorded.set(false)
                                // Open is a live, interactive state. Do not re-record the entire
                                // settings tree on every slider/preview frame; the Preparing layer
                                // is retained for the next close and the live tree is drawn once.
                                this@drawWithContent.drawContent()
                            } else {
                                if (phase == HomeAnchoredOverlayPhase.Closing &&
                                    closingBlurRecorded.compareAndSet(false, true)
                                ) {
                                    personalizeContentLayer.record {
                                        this@drawWithContent.drawContent()
                                    }
                                    personalizeBlurredContentLayer.record(
                                        size = IntSize(
                                            size.width.roundToInt().coerceAtLeast(1),
                                            size.height.roundToInt().coerceAtLeast(1)
                                        )
                                    ) {
                                        drawLayer(personalizeContentLayer)
                                    }
                                    personalizeBlurredContentLayer.renderEffect = fixedContentBlurEffect
                                }
                                val blurMix = (
                                    1f - homeMorphSmoothStep(
                                        0.42f,
                                        0.98f,
                                        geometry.value.expansionProgress
                                    )
                                    ).coerceIn(0f, 1f)
                                personalizeContentLayer.alpha = 1f - blurMix
                                personalizeBlurredContentLayer.alpha = blurMix
                                if (blurMix < 0.999f) drawLayer(personalizeContentLayer)
                                if (blurMix > 0.001f) drawLayer(personalizeBlurredContentLayer)
                            }
                        }
                    )
                }
            }
        )

        Box(
            modifier = Modifier
                .requiredSize(
                    width = with(density) { sourceBounds.width.toDp() },
                    height = with(density) { sourceBounds.height.toDp() }
                )
                .graphicsLayer {
                    val current = geometry.value
                    alpha = current.sourceAlpha
                    scaleX = current.sourceScale
                    scaleY = current.sourceScale
                    compositingStrategy = CompositingStrategy.Offscreen
                    val blurPx = maxContentBlurPx * homeMorphSmoothStep(
                        0f,
                        0.34f,
                        current.pathProgress
                    )
                    renderEffect = if (blurPx > 0.01f) {
                        BlurEffect(blurPx, blurPx, TileMode.Clamp)
                    } else null
                }
                .clearAndSetSemantics { }
        ) {
            sourceContent(Modifier.fillMaxSize())
        }

        if (motionState.phase != HomeAnchoredOverlayPhase.Opening &&
            motionState.phase != HomeAnchoredOverlayPhase.Open
        ) {
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

@Composable
private fun DeferredHomePersonalizeMorphPanel(
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    targetWidth: androidx.compose.ui.unit.Dp,
    targetHeight: androidx.compose.ui.unit.Dp,
    shape: Shape,
    progressiveBlur: Boolean,
    warmupBackdropEffects: Boolean,
    surfaceAlphaProvider: () -> Float,
    contentAlphaProvider: () -> Float,
    contentBlurRadiusPxProvider: () -> Float,
    backdropBlurProgressProvider: () -> Float,
    onContentLaidOut: () -> Unit,
    modifier: Modifier,
    content: @Composable (Modifier) -> Unit
) {
    val lightGlass = glassUsesLightStyle(config)
    val surfaceColor = if (lightGlass) {
        HomeLightGlassSurfaceColor.copy(alpha = HomeLightGlassPanelTintAlpha)
    } else {
        Color(0xFF121212).copy(alpha = 0.30f)
    }
    val showSurface by remember(surfaceAlphaProvider, backdrop, warmupBackdropEffects) {
        derivedStateOf {
            warmupBackdropEffects || backdrop == null || surfaceAlphaProvider() > 0.005f
        }
    }

    // Keep the live form and its surface inside the animated shell even after the
    // outer Kyant host releases its temporary clip at the stable Open endpoint.
    // The form can continue scrolling in Open; without this shared clip its
    // target-sized content layer exposes square corners beyond the glass shell.
    Box(
        modifier = modifier.graphicsLayer {
            this.shape = shape
            clip = true
        }
    ) {
        if (showSurface) {
            if (backdrop != null) {
                if (progressiveBlur && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    DeferredProgressivePersonalizeSurface(
                        backdrop = backdrop,
                        shape = shape,
                        surfaceColor = surfaceColor,
                        blurProgressProvider = backdropBlurProgressProvider,
                        warmupBackdropEffects = warmupBackdropEffects,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                val visibleAlpha = surfaceAlphaProvider()
                                alpha = if (warmupBackdropEffects) {
                                    max(visibleAlpha, 0.001f)
                                } else {
                                    visibleAlpha
                                }
                            }
                    )
                } else {
                    LiquidPanel(
                        backdrop = backdrop,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = surfaceAlphaProvider() },
                        shape = shape,
                        surfaceColor = surfaceColor,
                        lensHeight = 16.dp,
                        lensAmount = 24.dp
                    ) { }
                }
            } else {
                GlassDialogSurface(
                    backdrop = null,
                    config = config,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = surfaceAlphaProvider() },
                    shape = shape
                ) { }
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .requiredSize(targetWidth, targetHeight)
                .onSizeChanged {
                    if (it.width > 0 && it.height > 0) onContentLaidOut()
                }
                .graphicsLayer {
                    val contentAlpha = contentAlphaProvider()
                    alpha = contentAlpha
                    // Avoid allocating a target-sized offscreen blur layer while the content is
                    // effectively invisible. The original blur curve resumes at the handoff.
                    val blurPx = if (contentAlpha > 0.01f) {
                        contentBlurRadiusPxProvider()
                    } else {
                        0f
                    }
                    compositingStrategy = if (blurPx > 0.01f) {
                        CompositingStrategy.Offscreen
                    } else {
                        CompositingStrategy.Auto
                    }
                    renderEffect = if (blurPx > 0.01f) {
                        BlurEffect(blurPx, blurPx, TileMode.Clamp)
                    } else null
                }
        ) {
            content(Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun DeferredProgressivePersonalizeSurface(
    backdrop: Backdrop,
    shape: Shape,
    surfaceColor: Color,
    blurProgressProvider: () -> Float,
    warmupBackdropEffects: Boolean,
    modifier: Modifier = Modifier
) {
    val material = remember(surfaceColor) {
        GlassMaterialSpec(
            role = GlassMaterialRole.MorphShell,
            blur = 7.dp,
            lensHeight = 0.dp,
            lensAmount = 0.dp,
            surfaceAlpha = surfaceColor.alpha,
            borderAlpha = 0.28f,
            highlightAlpha = 0f,
            shadowAlpha = 0f,
            innerShadowAlpha = 0f,
            depthEffect = false,
            useVibrancy = false
        )
    }
    val descriptor = rememberGlassSurfaceDescriptor(
        debugLabel = "DeferredProgressivePersonalizeSurface",
        domain = GlassBackdropDomain.ChromeCombined,
        materialRole = GlassMaterialRole.MorphShell,
        sceneKey = "home-personalization-progressive-surface"
    )
    val showBackdropPass by remember(blurProgressProvider, warmupBackdropEffects) {
        derivedStateOf {
            warmupBackdropEffects || blurProgressProvider().coerceIn(0f, 1f) > 0.005f
        }
    }
    Box(
        modifier = modifier.graphicsLayer {
            this.shape = shape
            clip = true
        }
    ) {
        if (showBackdropPass) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val progress = blurProgressProvider().coerceIn(0f, 1f)
                        // Preparing compiles the real blur path without producing a visible
                        // pre-flash. The normal Opening alpha begins on the next frame.
                        alpha = if (warmupBackdropEffects) 0.001f else progress
                    }
                    .sleepDownPlainGlassSurface(
                        backdrop = backdrop,
                        descriptor = descriptor,
                        material = material,
                        shape = { shape },
                        effects = {
                            blur((7.dp * blurProgressProvider().coerceIn(0f, 1f)).toPx())
                        }
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
private fun DeferredPersonalizeBackdropAura(
    backdrop: Backdrop,
    leftFeatherPxProvider: () -> Float,
    blurProgressProvider: () -> Float,
    alphaProvider: () -> Float,
    modifier: Modifier = Modifier
) {
    val material = remember {
        GlassMaterialSpec(
            role = GlassMaterialRole.MorphShell,
            blur = 5.dp,
            lensHeight = 16.dp,
            lensAmount = 24.dp,
            surfaceAlpha = 0f,
            borderAlpha = 0f,
            highlightAlpha = 0f,
            shadowAlpha = 0f,
            innerShadowAlpha = 0f,
            chromaticAberration = false,
            depthEffect = false
        )
    }
    val descriptor = rememberGlassSurfaceDescriptor(
        debugLabel = "DeferredPersonalizeBackdropAura",
        domain = GlassBackdropDomain.ChromeCombined,
        materialRole = GlassMaterialRole.MorphShell,
        sceneKey = "home-personalization-backdrop-aura"
    )
    Box(
        modifier = modifier
            .graphicsLayer {
                compositingStrategy = CompositingStrategy.Offscreen
                alpha = alphaProvider()
            }
            .drawWithContent {
                drawContent()
                val leftStop = (
                    leftFeatherPxProvider() / size.width.coerceAtLeast(1f)
                    ).coerceIn(0f, 0.48f)
                drawRect(
                    brush = Brush.horizontalGradient(
                        0f to Color.Transparent,
                        leftStop to Color.White,
                        1f to Color.White
                    ),
                    blendMode = BlendMode.DstIn
                )
            }
            .sleepDownPlainGlassSurface(
                backdrop = backdrop,
                descriptor = descriptor,
                material = material,
                shape = { RoundedRectangle(0.dp) },
                effects = {
                    val progress = blurProgressProvider().coerceIn(0f, 1f)
                    vibrancy()
                    blur((5.dp * progress).toPx())
                    lens(16.dp.toPx(), 24.dp.toPx(), chromaticAberration = false)
                }
            )
    )
}

@Composable
internal fun HomeAddMenuMorphPanel(
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    actions: List<AddMenuAction>,
    homeMode: HomeMode,
    onHomeModeChange: (HomeMode) -> Unit,
    targetSizeProvider: () -> IntSize,
    surfaceAlphaProvider: () -> Float,
    contentAlphaProvider: () -> Float,
    contentBlurRadiusPxProvider: () -> Float = { 0f },
    externalHighlightedIndex: Int = -1,
    interactive: Boolean,
    shape: Shape,
    modifier: Modifier
) {
    var highlightedIndex by remember { mutableIntStateOf(-1) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val itemStepPx = with(density) {
        (HomeAddMenuActionItemHeightDp + HomeAddMenuActionGapDp).dp.toPx()
    }
    val contentTopPaddingPx = with(density) { HomeAddMenuContentTopPaddingDp.dp.toPx() }
    val modeHeightPx = with(density) { HomeAddMenuModeHeightDp.dp.toPx() }
    val actionTopPx = with(density) {
        (
            HomeAddMenuModeHeightDp + HomeAddMenuSectionGapDp * 2f +
                HomeAddMenuDividerHeightDp
            ).dp.toPx()
    }
    val lightGlass = glassUsesLightStyle(config)
    val textColor = glassForegroundColor(config)
    val showSurface by remember(surfaceAlphaProvider, backdrop) {
        derivedStateOf { backdrop == null || surfaceAlphaProvider() > 0.005f }
    }

    fun hitIndex(y: Float): Int {
        if (!interactive) return -1
        // Keep one continuous action hit region below the mode switch. The divider is decoration,
        // not a separate pointer target, and row spacing is assigned to the preceding row.
        return homeAddMenuHitIndex(
            y = y - contentTopPaddingPx,
            modeHeight = modeHeightPx,
            actionTop = actionTopPx,
            actionStep = itemStepPx,
            actionCount = actions.size
        )
    }

    val unifiedMenuGestureModifier = if (interactive) {
        Modifier.pointerInput(actions, homeMode) {
            awaitEachGesture {
                val down = awaitFirstDown(
                    requireUnconsumed = false,
                    pass = PointerEventPass.Initial
                )
                highlightedIndex = hitIndex(down.position.y)
                var lastPosition = down.position
                var completedNormally = false
                try {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id }
                            ?: break
                        lastPosition = change.position
                        if (!change.pressed) {
                            completedNormally = change.changedToUpIgnoreConsumed()
                            break
                        }
                        val nextIndex = hitIndex(change.position.y)
                        if (nextIndex != highlightedIndex) {
                            highlightedIndex = nextIndex
                            if (nextIndex in actions.indices) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        }
                    }
                } finally {
                    val index = highlightedIndex
                    highlightedIndex = -1
                    if (completedNormally) {
                        val innerY = lastPosition.y - contentTopPaddingPx
                        if (innerY in 0f..modeHeightPx) {
                            val targetMode = if (lastPosition.x < size.width / 2f) {
                                HomeMode.Day
                            } else {
                                HomeMode.Week
                            }
                            if (targetMode != homeMode) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onHomeModeChange(targetMode)
                            }
                        } else if (index in actions.indices) {
                            actions[index].onClick()
                        }
                    }
                }
            }
        }
    } else {
        Modifier
    }

    @Composable
    fun MenuContent() {
        @Composable
        fun RowScope.ModeTile(mode: HomeMode, iconRes: Int, label: String) {
            val selected = homeMode == mode
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .graphicsLayer { alpha = if (selected) 1f else 0.62f }
                    .clearAndSetSemantics { },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(painterResource(iconRes), contentDescription = null, modifier = Modifier.size(21.dp))
                    Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                }
            }
        }
        Column(
            modifier = Modifier
                .layout { measurable, _ ->
                    val targetSize = targetSizeProvider()
                    val width = targetSize.width.coerceAtLeast(1)
                    val height = targetSize.height.coerceAtLeast(1)
                    val placeable = measurable.measure(Constraints.fixed(width, height))
                    layout(width, height) { placeable.place(0, 0) }
                }
                .graphicsLayer {
                    val contentAlpha = contentAlphaProvider()
                    alpha = contentAlpha
                    val blurPx = if (contentAlpha > 0.01f) {
                        contentBlurRadiusPxProvider()
                    } else {
                        0f
                    }
                    compositingStrategy = if (blurPx > 0.01f) {
                        CompositingStrategy.Offscreen
                    } else {
                        CompositingStrategy.Auto
                    }
                    renderEffect = if (blurPx > 0.01f) {
                        BlurEffect(blurPx, blurPx, TileMode.Clamp)
                    } else {
                        null
                    }
                }
                .padding(
                    start = (HomeAddMenuConcentricInsetDp - HomeAddMenuActionColumnInsetDp).dp,
                    top = HomeAddMenuContentTopPaddingDp.dp,
                    end = (HomeAddMenuConcentricInsetDp - HomeAddMenuActionColumnInsetDp).dp,
                    bottom = (HomeAddMenuConcentricInsetDp - HomeAddMenuSelectionVerticalInsetDp).dp
                )
                .then(unifiedMenuGestureModifier)
        ) {
            CompositionLocalProvider(LocalContentColor provides textColor) {
                Row(
                    modifier = Modifier
                        .width(134.dp)
                        .align(Alignment.CenterHorizontally)
                        .height(HomeAddMenuModeHeightDp.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    ModeTile(HomeMode.Day, R.drawable.ic_day_view, "日视图")
                    ModeTile(HomeMode.Week, R.drawable.ic_week_view, "周视图")
                }
                Spacer(Modifier.height(HomeAddMenuSectionGapDp.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .height(HomeAddMenuDividerHeightDp.dp)
                        .background(textColor.copy(alpha = 0.14f))
                )
                Spacer(Modifier.height(HomeAddMenuSectionGapDp.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = HomeAddMenuActionColumnInsetDp.dp),
                    verticalArrangement = Arrangement.spacedBy(HomeAddMenuActionGapDp.dp)
                ) {
                    actions.forEachIndexed { index, action ->
                        AddMenuLiquidItem(
                            config = config,
                            action = action,
                            itemHeight = HomeAddMenuActionItemHeightDp.dp,
                            highlighted = externalHighlightedIndex == index ||
                                highlightedIndex == index
                        )
                    }
                }
            }
        }
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.TopEnd
    ) {
        if (showSurface) {
            val surfaceModifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = surfaceAlphaProvider() }
            if (backdrop != null) {
                LiquidButton(
                    onClick = {},
                    backdrop = backdrop,
                    modifier = surfaceModifier,
                    isInteractive = interactive,
                    clickTargetEnabled = false,
                    height = with(density) { targetSizeProvider().height.toDp() },
                    contentPadding = PaddingValues(0.dp),
                    blurRadius = 8.dp,
                    lensHeight = 12.dp,
                    lensAmount = 24.dp,
                    shadowEnabled = true,
                    pressExpansion = 3.dp,
                    highlightRadiusMultiplier = 0.65f,
                    shape = shape,
                    surfaceColor = (
                        if (lightGlass) HomeLightGlassSurfaceColor else Color(0xFF050505)
                        ).copy(alpha = if (lightGlass) 0.28f else 0.40f)
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        MenuContent()
                    }
                }
            } else {
                Box(
                    modifier = surfaceModifier.background(
                        color = if (appUsesDarkTheme(config)) Color(0xFF1C1C1E) else Color.White,
                        shape = shape
                    ),
                    contentAlignment = Alignment.Center
                ) {
                    MenuContent()
                }
            }
        }
    }
}

internal fun lerpHomeMorph(start: Float, stop: Float, fraction: Float): Float {
    val safe = fraction.coerceIn(0f, 1f)
    return start + (stop - start) * safe
}

internal fun homeMorphSmoothStep(edge0: Float, edge1: Float, value: Float): Float {
    val t = ((value - edge0) / (edge1 - edge0).coerceAtLeast(0.0001f)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}
