package com.xiaomanjun.sleepdownschedule.feature.onboarding

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

private const val WelcomeOrbitMillis = 1_380f
internal const val WelcomeWhiteFrameMillis = 1_820f
internal const val WelcomePanelStartMillis = WelcomeWhiteFrameMillis + 96f
internal const val WelcomePanelSettleMillis = WelcomePanelStartMillis + 900f
private val BloomEasing = CubicBezierEasing(0.33f, 0f, 0.68f, 1f)

internal fun Modifier.versionWelcomeBackdrop(panelDrop: Dp, elapsedMillis: () -> Float) = drawWithCache {
    val paper = Path()
    val drop = panelDrop.toPx()
    val rebound = (drop - 10.dp.toPx()).coerceAtLeast(0f)
    val maxCorner = minOf(32.dp.toPx(), size.width / 2f)
    val orb = size.minDimension * 0.035f
    val origin = Offset(size.width / 2f, size.height / 2f)
    val fillRadius = hypot(origin.x, origin.y) + 1f
    fun light(color: Color) = Brush.radialGradient(
        0f to color, 0.32f to color.copy(alpha = 0.45f),
        0.68f to color.copy(alpha = 0.09f), 1f to color.copy(alpha = 0f),
        center = Offset.Zero, radius = 1f,
    )
    val warmLight = light(Color(0xFFFFD1A8))
    val coolLight = light(Color(0xFFB6D6FF))
    onDrawBehind {
        val millis = elapsedMillis()
        drawRect(Color.Black)
        if (millis < WelcomeWhiteFrameMillis) {
            val orbit = segment(millis, 0f, WelcomeOrbitMillis)
            val angle = 4.0 * PI * orbit - PI / 2.0
            val direction = Offset(cos(angle).toFloat(), sin(angle).toFloat())
            val reach = size.minDimension * 0.18f * (1f - smoothSegment(orbit, 0.28f, 1f))
            val center = origin + Offset(direction.x * reach, direction.y * reach * 0.72f)
            val breath = (0.5f + 0.5f * sin(angle - PI / 3).toFloat()) * (1f - smoothSegment(orbit, 0.88f, 1f))
            val charge = smoothSegment(millis, 920f, 1_550f)
            val expansion = BloomEasing.transform(segment(millis, 1_350f, WelcomeWhiteFrameMillis))
            val radius = lerp(orb * (0.79f + breath * 0.37f + charge * 0.24f), fillRadius, expansion)
            val visible = smoothSegment(millis, 0f, 160f)
            val lightStrength = visible * (0.40f + 0.23f * breath + 0.28f * charge)
            val spread = maxOf(radius * 1.3f, orb * (2.4f + 1.1f * breath + 2.8f * charge))
            drawOrbLight(warmLight, center + direction * radius * 0.5f, spread, lightStrength)
            drawOrbLight(coolLight, center - direction * radius * 0.6f, spread, lightStrength)
            drawCircle(Color.White, radius, center, alpha = visible)
        } else if (millis < WelcomePanelStartMillis) {
            drawRect(Color.White)
        } else {
            val time = millis - WelcomePanelStartMillis
            val top = when {
                time < 540f -> drop * curtainSegment(time, 0f, 540f)
                time < 720f -> lerp(drop, rebound, curtainSegment(time, 540f, 720f))
                else -> lerp(rebound, drop, curtainSegment(time, 720f, 900f))
            }
            val corner = CornerRadius(maxCorner * curtainSegment(time, 0f, 300f))
            paper.rewind()
            paper.addRoundRect(RoundRect(Rect(0f, top, size.width, size.height),
                topLeft = corner, topRight = corner))
            drawPath(paper, Color.White)
        }
    }
}

private fun DrawScope.drawOrbLight(brush: Brush, center: Offset, spread: Float, strength: Float) {
    withTransform({
        translate(center.x, center.y)
        scale(spread, spread, Offset.Zero)
    }) {
        drawCircle(brush, radius = 1f, center = Offset.Zero, alpha = strength, blendMode = BlendMode.Plus)
    }
}

private fun curtainSegment(millis: Float, start: Float, end: Float): Float =
    platformAnimatorFraction(segment(millis, start, end))

internal fun platformAnimatorFraction(progress: Float): Float =
    (0.5 - 0.5 * cos(PI * progress)).toFloat()

internal fun segment(millis: Float, start: Float, end: Float): Float =
    ((millis - start) / (end - start)).coerceIn(0f, 1f)

internal fun smoothSegment(millis: Float, start: Float, end: Float): Float {
    val progress = segment(millis, start, end)
    return progress * progress * (3f - 2f * progress)
}
