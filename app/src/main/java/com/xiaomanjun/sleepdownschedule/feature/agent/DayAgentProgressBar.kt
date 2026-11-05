package com.xiaomanjun.sleepdownschedule.feature.agent

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.xiaomanjun.sleepdownschedule.core.ui.material.LocalVisualEffects
import com.xiaomanjun.sleepdownschedule.platform.coloros.rememberColorOsIntentSeekBar

/**
 * The divider doubles as the countdown read bar. During class and break it expands into the
 * ColorOS playback seek bar; the native conic-light view is used in 凝光 mode, while liquid
 * glass draws the same bar and conical light in Compose. Leaving the phase collapses it back
 * into the divider.
 */
@Composable
internal fun DayAgentProgressRow(
    progress: Float?,
    accent: Color,
    foreground: Color,
    dark: Boolean,
    modifier: Modifier = Modifier
) {
    val nativeMode = LocalVisualEffects.current.usesNativeControls
    val active = progress != null
    val nativeSeekBar = if (nativeMode) rememberColorOsIntentSeekBar(dark) else null
    val useNative = nativeMode && nativeSeekBar != null
    val lightPainter = if (useNative) null else rememberAgentConicLightPainter()
    val layoutDirection = LocalLayoutDirection.current
    val reveal by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (active) 340 else 240,
            easing = FastOutSlowInEasing
        ),
        label = "agent-read-bar-reveal"
    )
    val alphaReveal by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (active) 300 else 200,
            easing = LinearOutSlowInEasing
        ),
        label = "agent-read-bar-light-alpha"
    )
    val shapeReveal by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (active) 900 else 400,
            easing = FastOutSlowInEasing
        ),
        label = "agent-read-bar-light-shape"
    )
    val value by animateFloatAsState(
        targetValue = progress ?: 0f,
        animationSpec = tween(
            durationMillis = if (active) 1_000 else 220,
            easing = if (active) LinearEasing else FastOutSlowInEasing
        ),
        label = "agent-read-bar-value"
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
            if (!useNative && reveal > 0.01f) {
                drawConicReadBar(
                    progress = value,
                    accent = accent,
                    foreground = foreground,
                    reveal = reveal,
                    alphaReveal = alphaReveal,
                    shapeReveal = shapeReveal,
                    painter = lightPainter,
                    rtl = layoutDirection == LayoutDirection.Rtl
                )
            }
        },
        contentAlignment = Alignment.Center
    ) {
        if (useNative) {
            key(nativeSeekBar.view) {
                AndroidView(
                    factory = { nativeSeekBar.view },
                    modifier = Modifier.fillMaxWidth().requiredHeight(24.dp)
                        .nativeSeekBarExtend()
                        .graphicsLayer { alpha = reveal },
                    update = {
                        nativeSeekBar.sync(
                            progress = if (active) value else null,
                            accent = accent.toArgb(),
                            trackColor = foreground.copy(alpha = 0.15f).toArgb()
                        )
                    }
                )
            }
            DisposableEffect(nativeSeekBar) { onDispose { nativeSeekBar.dispose() } }
        }
    }
}

private fun Modifier.nativeSeekBarExtend(horizontal: Dp = 12.dp): Modifier = layout { measurable, constraints ->
    // COUISeekBar reserves the thumb radius at both ends even when the thumb is hidden.
    // Measure the native view wider and place it centered so the track reaches the card edges.
    val extra = horizontal.roundToPx()
    val width = constraints.maxWidth + extra * 2
    val placeable = measurable.measure(
        androidx.compose.ui.unit.Constraints.fixed(width, constraints.maxHeight)
    )
    layout(constraints.maxWidth, placeable.height) {
        placeable.place(-extra, 0)
    }
}

private fun DrawScope.drawConicReadBar(
    progress: Float,
    accent: Color,
    foreground: Color,
    reveal: Float,
    alphaReveal: Float,
    shapeReveal: Float,
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
            if (safeProgress > 0.2f && alphaReveal > 0f) {
                // The native renderer makes the gradient one fifth of the whole track, not of
                // the filled portion, so the bright edge keeps a constant physical length.
                val capWidth = size.width * 0.2f
                val capLeft = fillWidth - capWidth
                drawRoundRect(
                    brush = Brush.horizontalGradient(
                        0f to Color.White.copy(alpha = 0f),
                        1f to Color.White.copy(alpha = alphaReveal),
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
            if (painter != null) {
                // The card's own rounded clip owns the outer boundary. Do not clip to the 4dp
                // read-bar row: the native conic light is allowed to diffuse above and below
                // the track, and a tight row clip makes the light tail end in a hard edge.
                painter.draw(
                    canvas = drawContext.canvas,
                    trackWidth = size.width,
                    rowHeight = size.height,
                    progress = safeProgress,
                    accent = accent,
                    alphaReveal = alphaReveal,
                    shapeReveal = shapeReveal
                )
            } else {
                drawFallbackConicGlow(safeProgress, accent, alphaReveal, shapeReveal)
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
