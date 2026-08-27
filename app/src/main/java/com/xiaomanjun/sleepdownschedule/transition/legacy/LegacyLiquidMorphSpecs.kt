package com.xiaomanjun.sleepdownschedule.transition.legacy

import com.xiaomanjun.sleepdownschedule.*

import com.xiaomanjun.sleepdownschedule.feature.home.overlay.*

import androidx.compose.ui.geometry.Rect
import com.xiaomanjun.sleepdownschedule.glass.GlassSceneKeys
import com.xiaomanjun.sleepdownschedule.glass.LiquidBackdropDepthFrame
import com.xiaomanjun.sleepdownschedule.glass.LiquidContentHandoffFrame
import com.xiaomanjun.sleepdownschedule.glass.LiquidLayerLifecycleFrame
import com.xiaomanjun.sleepdownschedule.glass.LiquidMorphDirection
import com.xiaomanjun.sleepdownschedule.glass.LiquidMorphFrame
import com.xiaomanjun.sleepdownschedule.glass.LiquidMorphInput
import com.xiaomanjun.sleepdownschedule.glass.LiquidMorphSpec
import com.xiaomanjun.sleepdownschedule.glass.LiquidMotionSample
import com.xiaomanjun.sleepdownschedule.glass.LiquidProgressKinematics

/**
 * First-stage adapter: it separates route ownership from rendering while delegating every pixel
 * to the already accepted geometry. Future motion specs can replace this behind an allowlist.
 */
internal class LegacyHomeLiquidMorphSpec(
    override val routeKey: String,
    private val geometryProvider: (LiquidMorphInput) -> HomeAnchoredMorphGeometry
) : LiquidMorphSpec {
    override fun frame(input: LiquidMorphInput): LiquidMorphFrame {
        val geometry = geometryProvider(input)
        val trajectory = LiquidProgressKinematics(
            progress = geometry.pathProgress,
            velocity = 0f,
            acceleration = 0f
        )
        val shape = LiquidProgressKinematics(
            progress = geometry.expansionProgress,
            velocity = 0f,
            acceleration = 0f
        )
        val moving = input.direction == LiquidMorphDirection.Closing || input.rawProgress < 0.999f
        return LiquidMorphFrame(
            rect = geometry.rect,
            cornerRadiusPx = geometry.cornerRadiusPx,
            trajectoryProgress = geometry.pathProgress,
            shapeProgress = geometry.expansionProgress,
            motion = LiquidMotionSample(trajectory = trajectory, shape = shape),
            content = LiquidContentHandoffFrame(
                sourceAlpha = geometry.sourceAlpha,
                destinationSurfaceAlpha = geometry.surfaceAlpha,
                destinationContentAlpha = geometry.contentAlpha,
                destinationInteractive = !moving
            ),
            backdropDepth = LiquidBackdropDepthFrame(
                scale = input.backdropScale,
                blurPx = input.backdropBlurPx,
                useCachedScene = input.useCachedBackdrop
            ),
            layerLifecycle = LiquidLayerLifecycleFrame(
                keepMorphClip = moving,
                keepOffscreenLayer = moving,
                prewarmRequired = input.rawProgress <= 0f &&
                    input.direction == LiquidMorphDirection.Opening
            ),
            sourceScale = geometry.sourceScale
        )
    }

    fun homeGeometry(
        source: Rect,
        target: Rect,
        rawProgress: Float,
        closing: Boolean
    ): HomeAnchoredMorphGeometry = frame(
        LiquidMorphInput(
            source = source,
            target = target,
            rawProgress = rawProgress,
            direction = if (closing) LiquidMorphDirection.Closing else LiquidMorphDirection.Opening
        )
    ).toHomeAnchoredGeometry()
}

internal fun LiquidMorphFrame.toHomeAnchoredGeometry(): HomeAnchoredMorphGeometry =
    HomeAnchoredMorphGeometry(
        rect = rect,
        cornerRadiusPx = cornerRadiusPx,
        sourceScale = sourceScale,
        sourceAlpha = content.sourceAlpha,
        surfaceAlpha = content.destinationSurfaceAlpha,
        contentAlpha = content.destinationContentAlpha,
        pathProgress = trajectoryProgress,
        expansionProgress = shapeProgress
    )

internal fun legacyThreeDotMenuMorphSpec(
    source: Rect,
    target: Rect,
    sourceCornerRadiusPx: Float?,
    targetCornerRadiusPx: Float,
    sourcePressedScale: Float,
    openingPinchDiameterPx: Float,
    openingMinimumDropPx: Float,
    openingMaximumDropPx: Float,
    openingMaximumArcPx: Float,
    verticalReboundAmplitudePx: Float,
    closingSinkOffsetPx: Float,
    closingControlDropPx: Float
): LegacyHomeLiquidMorphSpec = LegacyHomeLiquidMorphSpec(GlassSceneKeys.HomeThreeDotMenuMotion) { input ->
    homeThreeDotMenuTrajectoryGeometry(
        source = source,
        target = target,
        rawProgress = input.rawProgress,
        closing = input.direction == LiquidMorphDirection.Closing,
        sourceCornerRadiusPx = sourceCornerRadiusPx,
        targetCornerRadiusPx = targetCornerRadiusPx,
        sourcePressedScale = sourcePressedScale,
        openingPinchDiameterPx = openingPinchDiameterPx,
        openingMinimumDropPx = openingMinimumDropPx,
        openingMaximumDropPx = openingMaximumDropPx,
        openingMaximumArcPx = openingMaximumArcPx,
        verticalReboundAmplitudePx = verticalReboundAmplitudePx,
        closingSinkOffsetPx = closingSinkOffsetPx,
        closingControlDropPx = closingControlDropPx
    )
}

internal fun legacyPersonalizationMorphSpec(
    source: Rect,
    target: Rect,
    pinchDiameterPx: Float,
    minimumDropPx: Float,
    maximumDropPx: Float,
    maximumArcPx: Float,
    targetCornerRadiusPx: Float,
    sourcePressedScale: Float,
    verticalReboundAmplitudePx: Float,
    closingSinkOffsetPx: Float,
    closingControlDropPx: Float
): LegacyHomeLiquidMorphSpec = LegacyHomeLiquidMorphSpec("home-personalization") { input ->
    homePersonalizationTrajectoryGeometry(
        source = source,
        target = target,
        rawProgress = input.rawProgress,
        pinchDiameterPx = pinchDiameterPx,
        minimumDropPx = minimumDropPx,
        maximumDropPx = maximumDropPx,
        maximumArcPx = maximumArcPx,
        targetCornerRadiusPx = targetCornerRadiusPx,
        sourcePressedScale = sourcePressedScale,
        closing = input.direction == LiquidMorphDirection.Closing,
        verticalReboundAmplitudePx = verticalReboundAmplitudePx,
        closingSinkOffsetPx = closingSinkOffsetPx,
        closingControlDropPx = closingControlDropPx
    )
}

internal fun legacyHomeMenuDestinationMorphSpec(
    sourceBoundsInRoot: Rect,
    collapseBoundsInRoot: Rect,
    target: Rect,
    menuCornerRadiusPx: Float,
    buttonCornerRadiusPx: Float,
    pinchDiameterPx: Float,
    minimumDropPx: Float,
    maximumDropPx: Float,
    maximumArcPx: Float,
    targetCornerRadiusPx: Float
): LegacyHomeLiquidMorphSpec = LegacyHomeLiquidMorphSpec("home-menu-destination") { input ->
    homeMenuDestinationTrajectoryGeometry(
        sourceBoundsInRoot = sourceBoundsInRoot,
        collapseBoundsInRoot = collapseBoundsInRoot,
        target = target,
        rawProgress = input.rawProgress,
        closing = input.direction == LiquidMorphDirection.Closing,
        menuCornerRadiusPx = menuCornerRadiusPx,
        buttonCornerRadiusPx = buttonCornerRadiusPx,
        pinchDiameterPx = pinchDiameterPx,
        minimumDropPx = minimumDropPx,
        maximumDropPx = maximumDropPx,
        maximumArcPx = maximumArcPx,
        targetCornerRadiusPx = targetCornerRadiusPx
    )
}
