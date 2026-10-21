package com.xiaomanjun.sleepdownschedule.feature.agent

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.Dp
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sign

private val AgentMorphPositionEasing = CubicBezierEasing(0.18f, 0.72f, 0.18f, 1.0f)
private val AgentMorphSizeEasing = CubicBezierEasing(0.22f, 0.62f, 0.22f, 1.0f)
private val AgentMorphClosePositionEasing = CubicBezierEasing(0.30f, 0.10f, 0.22f, 1.0f)
internal const val AgentMorphOpenDurationMillis = 520
internal const val AgentMorphCloseDurationMillis = 520

/**
 * On a landscape tablet the conversation remains spatially attached to the Today Agent card:
 * its left, top and right edges stay fixed while only the bottom edge grows toward the centre
 * line of the home Dock. The floating input then straddles that same visual axis.
 */
internal fun tabletDayAgentConversationTargetRect(
    source: Rect,
    windowHeightPx: Float,
    density: Float,
    safeBottom: Dp
): Rect {
    val safeDensity = density.coerceAtLeast(0.001f)
    val dockCenterFromBottomDp = safeBottom.value + 8f + 27f
    val dockCenterY = windowHeightPx - dockCenterFromBottomDp * safeDensity
    val targetTop = source.bottom + 12f * safeDensity
    return Rect(
        left = source.left,
        top = targetTop,
        right = source.right,
        bottom = dockCenterY.coerceAtLeast(targetTop + safeDensity)
    )
}

internal fun tabletDayAgentConversationSourceRect(source: Rect, density: Float): Rect {
    val safeDensity = density.coerceAtLeast(0.001f)
    val halfWidth = 18f * safeDensity
    // Start inside the lower edge of the assistant card. The dialog shell remains transparent
    // for the first part of the morph, so it reads as flowing out from behind the card instead
    // of exposing a separate capsule below it.
    val top = source.bottom - 10f * safeDensity
    return Rect(
        left = source.center.x - halfWidth,
        top = top,
        right = source.center.x + halfWidth,
        bottom = source.bottom - 2f * safeDensity
    )
}

internal fun agentMorphPositionProgress(rawProgress: Float, closing: Boolean): Float {
    val raw = rawProgress.coerceIn(0f, 1f)
    if (!closing) {
        return AgentMorphPositionEasing.transform(raw).coerceIn(0f, 1f)
    }
    val closingElapsed = 1f - raw
    return (1f - AgentMorphClosePositionEasing.transform(closingElapsed)).coerceIn(0f, 1f)
}

internal fun agentMorphSizeProgress(rawProgress: Float, closing: Boolean): Float {
    val raw = rawProgress.coerceIn(0f, 1f)
    return if (!closing) {
        val delayed = ((raw - 0.10f) / 0.90f).coerceIn(0f, 1f)
        AgentMorphSizeEasing.transform(delayed).coerceIn(0f, 1f)
    } else {
        val closingElapsed = 1f - raw
        val advanced = (closingElapsed / 0.78f).coerceIn(0f, 1f)
        (1f - AgentMorphSizeEasing.transform(advanced)).coerceIn(0f, 1f)
    }
}

internal fun agentMorphGeometry(
    source: Rect,
    target: Rect,
    positionProgress: Float,
    sizeProgress: Float,
    maxArcPx: Float
): Rect {
    val position = positionProgress.coerceIn(0f, 1f)
    val size = sizeProgress.coerceIn(0f, 1f)
    val deltaY = target.center.y - source.center.y
    val arcAmplitude = min(maxArcPx, abs(deltaY) * 0.22f)
    val controlX = (source.center.x + target.center.x) / 2f
    val controlY = (source.center.y + target.center.y) / 2f + sign(deltaY) * arcAmplitude
    val inverse = 1f - position
    val centerX =
        inverse * inverse * source.center.x +
            2f * inverse * position * controlX +
            position * position * target.center.x
    val centerY =
        inverse * inverse * source.center.y +
            2f * inverse * position * controlY +
            position * position * target.center.y
    val width = source.width + (target.width - source.width) * size
    val height = source.height + (target.height - source.height) * size
    return Rect(
        left = centerX - width / 2f,
        top = centerY - height / 2f,
        right = centerX + width / 2f,
        bottom = centerY + height / 2f
    )
}

internal fun agentSmoothStep(edge0: Float, edge1: Float, value: Float): Float {
    val t = ((value - edge0) / (edge1 - edge0).coerceAtLeast(0.0001f)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}
