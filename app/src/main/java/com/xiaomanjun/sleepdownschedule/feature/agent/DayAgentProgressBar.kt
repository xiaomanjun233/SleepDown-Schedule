package com.xiaomanjun.sleepdownschedule.feature.agent

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.PI

/**
 * The divider expands into a passive playback bar with the COUI conic light in either mode.
 */
@Composable
internal fun DayAgentProgressRow(
    progress: Float?,
    accent: Color,
    foreground: Color,
    modifier: Modifier = Modifier
) {
    val active = progress != null
    val lightPainter = rememberAgentConicLightPainter()
    val layoutDirection = LocalLayoutDirection.current
    val visibility = remember { MutableTransitionState(false) }.apply { targetState = active }
    val transition = rememberTransition(visibility, label = "agent-read-bar")
    val reveal by transition.animateFloat(
        transitionSpec = { tween(if (targetState) 340 else 240, easing = FastOutSlowInEasing) },
        label = "agent-read-bar-reveal"
    ) { if (it) 1f else 0f }
    val alphaReveal by transition.animateFloat(
        transitionSpec = { conicLightSpring(if (targetState) .3f else .4f) },
        label = "agent-read-bar-light-alpha"
    ) { if (it) 1f else 0f }
    val shapeReveal by transition.animateFloat(
        transitionSpec = { conicLightSpring(if (targetState) .9f else .4f) },
        label = "agent-read-bar-light-shape"
    ) { if (it) 1f else 0f }
    val animatedValue = animateFloatAsState(
        targetValue = progress ?: 0f,
        animationSpec = tween(
            durationMillis = if (active) 1_000 else 220,
            easing = if (active) LinearEasing else FastOutSlowInEasing
        ),
        label = "agent-read-bar-value"
    )
    val highlightAlpha by animateFloatAsState(
        if (active) 1f else 0f, conicLightSpring(.5f), label = "agent-read-bar-leading-edge"
    )
    Box(
        modifier.fillMaxWidth().height(4.dp).drawBehind {
            val centerY = size.height / 2f
            val hairline = 1.dp.toPx()
            if (reveal < 0.999f) {
                drawRect(
                    color = foreground.copy(alpha = 0.10f * (1f - reveal)),
                    topLeft = Offset(0f, centerY - hairline / 2f),
                    size = Size(size.width, hairline)
                )
            }
            if (reveal > 0.01f) {
                drawConicReadBar(
                    progress = animatedValue.value,
                    accent = accent,
                    foreground = foreground,
                    reveal = reveal,
                    alphaReveal = alphaReveal,
                    shapeReveal = shapeReveal,
                    highlightAlpha = highlightAlpha,
                    painter = lightPainter,
                    rtl = layoutDirection == LayoutDirection.Rtl
                )
            }
        }
    )
}

// COUISpringForce uses a critical spring when bounce is zero and ω = 2π / response.
private fun conicLightSpring(response: Float) = spring<Float>(
    dampingRatio = 1f,
    stiffness = (2f * PI.toFloat() / response).let { it * it },
    visibilityThreshold = .001f,
)

private fun DrawScope.drawConicReadBar(
    progress: Float,
    accent: Color,
    foreground: Color,
    reveal: Float,
    alphaReveal: Float,
    shapeReveal: Float,
    highlightAlpha: Float,
    painter: AgentConicLightPainter?,
    rtl: Boolean
) {
    val drawLtr: DrawScope.() -> Unit = {
        val trackHeight = 4.dp.toPx()
        val centerY = size.height / 2f
        val top = centerY - trackHeight / 2f
        val radius = CornerRadius(trackHeight / 2f)
        // COUIIntentSeekBar uses a 4dp track and progress; the passive read bar keeps the
        // track at the assistant card's 15% foreground tint.
        drawRoundRect(
            color = foreground.copy(alpha = foreground.alpha * 0.15f * reveal),
            topLeft = Offset(0f, top),
            size = Size(size.width, trackHeight),
            cornerRadius = radius
        )
        val safeProgress = progress.coerceIn(0f, 1f)
        val fillWidth = size.width * safeProgress
        if (fillWidth > 0f) {
            drawRoundRect(
                color = accent.copy(alpha = accent.alpha * reveal),
                topLeft = Offset(0f, top),
                size = Size(fillWidth, trackHeight),
                cornerRadius = radius
            )
            // IntentConicProgressRenderer overlays a transparent -> white gradient while
            // playing; it is the bright leading edge of the bar.
            if (highlightAlpha > 0f) {
                // The native renderer makes the gradient one fifth of the whole track, not of
                // the filled portion, so the bright edge keeps a constant physical length.
                val capWidth = (size.width * 0.2f).coerceAtMost(fillWidth)
                val capLeft = fillWidth - capWidth
                drawRoundRect(
                    brush = Brush.horizontalGradient(
                        0f to Color.White.copy(alpha = 0f),
                        1f to Color.White.copy(alpha = highlightAlpha),
                        startX = capLeft,
                        endX = fillWidth
                    ),
                    topLeft = Offset(capLeft, top),
                    size = Size(capWidth, trackHeight),
                    cornerRadius = radius
                )
            }
        }
        if (safeProgress > 0f) {
            val darkSurface = foreground.luminance() >= 0.5f
            val glowColor = if (darkSurface) accent else lerp(accent, foreground, 0.22f)
            if (painter != null) {
                // The card's own rounded clip owns the outer boundary. Do not clip to the 4dp
                // read-bar row: the native conic light is allowed to diffuse above and below
                // the track, and a tight row clip makes the light tail end in a hard edge.
                painter.draw(
                    canvas = drawContext.canvas,
                    trackWidth = size.width,
                    rowHeight = size.height,
                    progress = safeProgress,
                    accent = glowColor,
                    useScreenBlend = darkSurface,
                    alphaReveal = alphaReveal,
                    shapeReveal = shapeReveal
                )
            } else {
                drawFallbackConicGlow(safeProgress, glowColor, alphaReveal, shapeReveal)
            }
        }
    }
    if (rtl) {
        scale(-1f, 1f, pivot = Offset(size.width / 2f, size.height / 2f)) { drawLtr() }
    } else {
        drawLtr()
    }
}

/** API < 33 keeps the same leading edge with a compact radial approximation of the cone. */
private fun DrawScope.drawFallbackConicGlow(
    progress: Float,
    accent: Color,
    alphaReveal: Float,
    shapeReveal: Float
) {
    if (alphaReveal <= 0f) return
    val centerY = size.height / 2f
    val center = Offset(size.width * progress.coerceIn(0f, 1f), centerY)
    val radius = (8f + 6f * shapeReveal.coerceIn(0f, 1f)).dp.toPx()
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                accent.copy(alpha = 0.55f * alphaReveal),
                accent.copy(alpha = 0f)
            ),
            center = center,
            radius = radius
        ),
        radius = radius,
        center = center
    )
}
