package com.xiaomanjun.sleepdownschedule.feature.course.editor

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.geometry.Rect
import com.xiaomanjun.sleepdownschedule.glass.LiquidBackdropDepthFrame
import com.xiaomanjun.sleepdownschedule.glass.LiquidContentHandoffFrame
import com.xiaomanjun.sleepdownschedule.glass.LiquidLayerLifecycleFrame
import com.xiaomanjun.sleepdownschedule.glass.LiquidMorphDirection
import com.xiaomanjun.sleepdownschedule.glass.LiquidMorphFrame
import com.xiaomanjun.sleepdownschedule.glass.LiquidMorphInput
import com.xiaomanjun.sleepdownschedule.glass.LiquidMorphSpec
import com.xiaomanjun.sleepdownschedule.glass.LiquidMotionSample
import com.xiaomanjun.sleepdownschedule.glass.LiquidProgressKinematics
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sin

/**
 * Course-editor timings and pure frame calculations. The overlay owns the clock and cleanup.
 * This route is independent of the similar course-management Activity and Agent morphs.
 */
internal object CourseEditorMotion {
    val OpenPositionEasing = CubicBezierEasing(0.18f, 0.72f, 0.18f, 1.0f)
    val OpenSizeEasing = CubicBezierEasing(0.22f, 0.62f, 0.22f, 1.0f)
    val ClosePositionEasing = CubicBezierEasing(0.30f, 0.10f, 0.22f, 1.0f)
    const val OpenDurationMillis = 380
    const val CloseDurationMillis = 440
    // The background zoom runs on its own, slower timeline than the card morph so it keeps
    // settling after the card has finished expanding/collapsing — reading as inertial pull
    // on the home surface behind the editor rather than a motion locked to the card.
    const val BackgroundZoomOpenScale = 1.08f
    const val BackgroundZoomDelayMillis = 20
    const val BackgroundZoomOpenDurationMillis = 420
    const val BackgroundZoomCloseDurationMillis = CloseDurationMillis
    val BackgroundZoomInertialEasing = CubicBezierEasing(0.30f, 0.0f, 0.20f, 1.0f)
    // A short overlapping entrance follows the shell; offscreen rows mount only when scrolled in.
    const val FormRevealDurationMillis = 300
    val RowRevealEasing = CubicBezierEasing(0.22f, 0f, 0.30f, 1f)
    // 需要逐行飞入的行数：固定标题栏 + 表单行（课程名称/教师/地点/星期/节次/周次/单双周/颜色/备注/删除/错误）。
    const val FormRowCount = 12

    private val CenteredEasing = CubicBezierEasing(0.2f, 0.7f, 0.2f, 1f)
    fun centeredSpec(opening: Boolean) = tween<Float>(if (opening) 280 else 240, easing = CenteredEasing)

    fun formRowEntrance(rowIndex: Int, progress: Float): Float {
        val delayMillis = rowIndex.coerceIn(0, FormRowCount) * 8f
        val t = ((progress * FormRevealDurationMillis - delayMillis) / 200f)
            .coerceIn(0f, 1f)
        return RowRevealEasing.transform(t)
    }
}

internal fun courseEditorInterpolate(start: Float, stop: Float, fraction: Float): Float {
    val safe = fraction.coerceIn(0f, 1f)
    return start + (stop - start) * safe
}

private data class CourseEditorVisualProgress(
    val position: Float,
    val size: Float
)

private fun courseEditorVisualProgress(
    rawProgress: Float,
    phase: CourseEditorOverlayPhase
): CourseEditorVisualProgress {
    val raw = rawProgress.coerceIn(0f, 1f)
    return when (phase) {
        CourseEditorOverlayPhase.Closing,
        CourseEditorOverlayPhase.Disposing -> {
            // Keep the same reverse parabola, but let its center advance gently at first so the
            // close does not feel faster than the opening. Size contracts earlier and separately,
            // making the return arc visible instead of moving a nearly full-size dialog.
            val closingElapsed = 1f - raw
            val closingPosition = CourseEditorMotion.ClosePositionEasing.transform(closingElapsed)
            val advancedClosingSize = (closingElapsed / 0.78f).coerceIn(0f, 1f)
            val closingSize = CourseEditorMotion.OpenSizeEasing.transform(advancedClosingSize)
            CourseEditorVisualProgress(
                position = (1f - closingPosition).coerceIn(0f, 1f),
                size = (1f - closingSize).coerceIn(0f, 1f)
            )
        }
        CourseEditorOverlayPhase.Open -> CourseEditorVisualProgress(position = 1f, size = 1f)
        else -> {
            val delayedSizeProgress = ((raw - 0.10f) / 0.90f).coerceIn(0f, 1f)
            CourseEditorVisualProgress(
                position = CourseEditorMotion.OpenPositionEasing.transform(raw).coerceIn(0f, 1f),
                size = CourseEditorMotion.OpenSizeEasing.transform(delayedSizeProgress).coerceIn(0f, 1f)
            )
        }
    }
}

private fun courseEditorSettleScale(
    rawProgress: Float,
    phase: CourseEditorOverlayPhase
): Float {
    val raw = rawProgress.coerceIn(0f, 1f)
    return when (phase) {
        CourseEditorOverlayPhase.Opening -> {
            val pulse = ((raw - 0.78f) / 0.22f).coerceIn(0f, 1f)
            1f + sin(PI.toFloat() * pulse) * 0.008f
        }
        CourseEditorOverlayPhase.Closing -> {
            val closingElapsed = 1f - raw
            val pulse = ((closingElapsed - 0.78f) / 0.22f).coerceIn(0f, 1f)
            1f + sin(PI.toFloat() * pulse) * 0.008f
        }
        else -> 1f
    }
}

private fun curvedCourseEditorRect(
    source: Rect,
    target: Rect,
    positionProgress: Float,
    sizeProgress: Float,
    pulseScale: Float,
    maxArcPx: Float
): Rect {
    val positionT = positionProgress.coerceIn(0f, 1f)
    val sizeT = sizeProgress.coerceIn(0f, 1f)
    val sourceCenter = source.center
    val targetCenter = target.center
    val deltaY = targetCenter.y - sourceCenter.y
    val arcAmplitude = min(maxArcPx, abs(deltaY) * 0.22f)
    val controlX = (sourceCenter.x + targetCenter.x) / 2f
    val controlY = (sourceCenter.y + targetCenter.y) / 2f + sign(deltaY) * arcAmplitude
    val inverse = 1f - positionT
    val centerX =
        inverse * inverse * sourceCenter.x +
            2f * inverse * positionT * controlX +
            positionT * positionT * targetCenter.x
    val centerY =
        inverse * inverse * sourceCenter.y +
            2f * inverse * positionT * controlY +
            positionT * positionT * targetCenter.y
    val width = courseEditorInterpolate(source.width, target.width, sizeT) * pulseScale
    val height = courseEditorInterpolate(source.height, target.height, sizeT) * pulseScale
    return Rect(
        left = centerX - width / 2f,
        top = centerY - height / 2f,
        right = centerX + width / 2f,
        bottom = centerY + height / 2f
    )
}

internal fun courseEditorSmoothStep(edge0: Float, edge1: Float, value: Float): Float {
    val t = ((value - edge0) / (edge1 - edge0).coerceAtLeast(0.0001f)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

internal fun legacyCourseEditorMorphSpec(
    source: Rect,
    target: Rect,
    sourceCornerRadiusPx: Float,
    targetCornerRadiusPx: Float,
    maximumArcPx: Float,
    sourceBlurMaxPx: Float,
    destinationBlurMaxPx: Float,
    hasSourceTransform: Boolean,
    preloadedContent: Boolean = false
): LiquidMorphSpec = object : LiquidMorphSpec {
    override val routeKey: String = "course-editor"

    override fun frame(input: LiquidMorphInput): LiquidMorphFrame {
        val closing = input.direction == LiquidMorphDirection.Closing
        val phase = if (closing) CourseEditorOverlayPhase.Closing else CourseEditorOverlayPhase.Opening
        val visualProgress = courseEditorVisualProgress(input.rawProgress, phase)
        val pulseScale = courseEditorSettleScale(input.rawProgress, phase)
        val rect = curvedCourseEditorRect(
            source = source,
            target = target,
            positionProgress = visualProgress.position,
            sizeProgress = visualProgress.size,
            pulseScale = pulseScale,
            maxArcPx = maximumArcPx
        )
        val cornerProgress = courseEditorSmoothStep(0.04f, 0.90f, visualProgress.size)
        val corner = courseEditorInterpolate(
            sourceCornerRadiusPx,
            targetCornerRadiusPx,
            cornerProgress
        )
        val openingHandoff = courseEditorSmoothStep(0.12f, 0.50f, visualProgress.position)
        val closingHandoff = courseEditorSmoothStep(0.04f, 0.18f, visualProgress.position)
        val destinationAlpha = if (preloadedContent) {
            com.xiaomanjun.sleepdownschedule.glass.liquidPreloadedContentAlpha(input.rawProgress)
        } else if (closing) closingHandoff else openingHandoff
        val sourceAlpha = if (hasSourceTransform) 1f - destinationAlpha else 0f
        val sourceBlurProgress = if (closing) {
            courseEditorSmoothStep(0f, 0.12f, visualProgress.position)
        } else {
            courseEditorSmoothStep(0f, 0.24f, visualProgress.position)
        }
        val destinationBlur = if (closing) {
            destinationBlurMaxPx
        } else {
            destinationBlurMaxPx * (1f - courseEditorSmoothStep(0.90f, 1f, visualProgress.position))
        }
        val trajectory = LiquidProgressKinematics(visualProgress.position, 0f, 0f)
        val shape = LiquidProgressKinematics(visualProgress.size, 0f, 0f)
        val moving = closing || input.rawProgress < 0.999f
        return LiquidMorphFrame(
            rect = rect,
            cornerRadiusPx = corner,
            trajectoryProgress = visualProgress.position,
            shapeProgress = visualProgress.size,
            motion = LiquidMotionSample(trajectory = trajectory, shape = shape),
            content = LiquidContentHandoffFrame(
                sourceAlpha = sourceAlpha,
                destinationSurfaceAlpha = 1f,
                destinationContentAlpha = destinationAlpha,
                sourceBlurPx = sourceBlurMaxPx * sourceBlurProgress,
                destinationBlurPx = destinationBlur,
                destinationMounted = true,
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
                prewarmRequired = !closing && input.rawProgress <= 0f
            )
        )
    }
}
