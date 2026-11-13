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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.xiaomanjun.sleepdownschedule.core.ui.controls.ConicProgressLight
import com.xiaomanjun.sleepdownschedule.core.ui.controls.drawFallbackConicGlow
import com.xiaomanjun.sleepdownschedule.core.ui.controls.rememberConicProgressLight
import kotlin.math.PI

/**
 * The divider expands into a passive playback bar with the COUI conic light in either mode.
 */
@Composable
internal fun DayAgentProgressRow(
    progress: Float?,
    accent: Color,
    foreground: Color,
    darkSurface: Boolean,
    modifier: Modifier = Modifier
) {
    val active = progress != null
    val lightPainter = rememberConicProgressLight()
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
    val highlightAlpha by transition.animateFloat(
        transitionSpec = { conicLightSpring(.5f) }, label = "agent-read-bar-leading-edge"
    ) { if (it) 1f else 0f }
    Box(
        modifier.fillMaxWidth().height(4.dp).drawWithCache {
            val radius = CornerRadius(size.height / 2f)
            val railClip = Path().apply {
                addRoundRect(RoundRect(Rect(0f, 0f, size.width, size.height), radius))
            }
            val hairline = 1.dp.toPx()
            onDrawBehind {
                if (reveal < 0.999f) {
                    drawRect(
                        color = foreground.copy(alpha = 0.10f * (1f - reveal)),
                        topLeft = Offset(0f, (size.height - hairline) / 2f),
                        size = Size(size.width, hairline)
                    )
                }
                if (reveal > 0.01f) {
                    drawConicReadBar(
                        progress = animatedValue.value,
                        accent = accent,
                        darkSurface = darkSurface,
                        reveal = reveal,
                        alphaReveal = alphaReveal,
                        shapeReveal = shapeReveal,
                        highlightAlpha = highlightAlpha,
                        painter = lightPainter,
                        railClip = railClip,
                        rtl = layoutDirection == LayoutDirection.Rtl
                    )
                }
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
    darkSurface: Boolean,
    reveal: Float,
    alphaReveal: Float,
    shapeReveal: Float,
    highlightAlpha: Float,
    painter: ConicProgressLight?,
    railClip: Path,
    rtl: Boolean
) {
    val drawLtr: DrawScope.() -> Unit = bar@{
        val trackHeight = size.height
        val halfHeight = trackHeight / 2f
        val radius = CornerRadius(halfHeight)
        val railColor = if (darkSurface) Color(0x26FFFFFF) else Color(0x14000000)
        drawRoundRect(color = railColor.copy(alpha = railColor.alpha * reveal), cornerRadius = radius)
        val safeProgress = progress.coerceIn(0f, 1f)
        val fillWidth = size.width * safeProgress
        if (fillWidth <= 0f) return@bar
        val capEnd = fillWidth + halfHeight
        // The player clips the stroke to the rail and rounds it around the light's center.
        clipPath(railClip) {
            drawRoundRect(
                color = accent.copy(alpha = accent.alpha * reveal),
                topLeft = Offset(-halfHeight, 0f),
                size = Size(fillWidth + trackHeight, trackHeight),
                cornerRadius = radius
            )
            if (highlightAlpha > 0f) {
                val highlightWidth = (size.width * 0.2f).coerceAtMost(fillWidth)
                val capLeft = fillWidth - highlightWidth - halfHeight
                drawRoundRect(
                    brush = Brush.horizontalGradient(
                        0f to Color.White.copy(alpha = 0f),
                        1f to Color.White.copy(alpha = highlightAlpha),
                        startX = capLeft,
                        endX = capEnd
                    ),
                    topLeft = Offset(capLeft, 0f),
                    size = Size(capEnd - capLeft, trackHeight),
                    cornerRadius = radius
                )
            }
        }
        if (painter != null) {
            // Let the cone feather outside the track, within the card's rounded clip.
            painter.draw(
                canvas = drawContext.canvas,
                trackWidth = size.width,
                rowHeight = size.height,
                progress = safeProgress,
                accent = accent,
                darkSurface = darkSurface,
                alphaReveal = alphaReveal,
                shapeReveal = shapeReveal
            )
        } else {
            drawFallbackConicGlow(fillWidth, accent, darkSurface, alphaReveal, shapeReveal)
        }
    }
    if (rtl) {
        scale(-1f, 1f, pivot = Offset(size.width / 2f, size.height / 2f)) { drawLtr() }
    } else {
        drawLtr()
    }
}
