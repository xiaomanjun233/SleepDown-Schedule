package com.xiaomanjun.sleepdownschedule.core.ui.controls

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Accent palette for quota progress bars using the same light/dark visual balance as [DayAgentPresentation].
 */
fun conicQuotaAccentColor(
    isExhausted: Boolean,
    isLow: Boolean,
    darkSurface: Boolean
): Color = when {
    isExhausted -> if (darkSurface) Color(0xFFFF7474) else Color(0xFFD92D2D)
    isLow -> if (darkSurface) Color(0xFFFFB45C) else Color(0xFFD96A00)
    else -> if (darkSurface) Color(0xFF62B5FF) else Color(0xFF006EDC)
}

/**
 * Smooth quota ring color ramp: green at full quota -> yellow around half -> red when low/exhausted.
 */
fun conicQuotaRingColor(
    progress: Float,
    isExhausted: Boolean,
    isUnlimited: Boolean,
    darkSurface: Boolean
): Color {
    val fullGreen = if (darkSurface) Color(0xFF34C759) else Color(0xFF1F9D45)
    val midYellow = if (darkSurface) Color(0xFFFFD60A) else Color(0xFFE59500)
    val lowRed = if (darkSurface) Color(0xFFFF5A5F) else Color(0xFFD92D2D)
    if (isUnlimited) return fullGreen
    if (isExhausted) return lowRed
    val clamped = progress.coerceIn(0f, 1f)
    return if (clamped >= 0.5f) {
        lerp(midYellow, fullGreen, (clamped - 0.5f) / 0.5f)
    } else {
        lerp(lowRed, midYellow, clamped / 0.5f)
    }
}

/**
 * Circular quota progress ring: full circle when unused; consumed quota recedes clockwise from
 * 12 o'clock to reveal the underlying track. The highlight follows the remaining arc.
 */
@Composable
fun ConicGlowProgressRing(
    progress: Float,
    accent: Color,
    centerText: String,
    darkSurface: Boolean,
    modifier: Modifier = Modifier,
    ringSize: Dp = 42.dp,
    strokeWidth: Dp = 4.dp,
    glowEnabled: Boolean = true,
    centerTextColor: Color = accent
) {
    val lightPainter = rememberConicProgressLight()
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 360, easing = FastOutSlowInEasing),
        label = "conic-ring-progress"
    )
    val glowAlpha by animateFloatAsState(
        targetValue = if (glowEnabled && progress > 0.01f) 1f else 0f,
        animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
        label = "conic-ring-glow-alpha"
    )
    Box(
        modifier = modifier.size(ringSize),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawWithCache {
                    val strokePx = strokeWidth.toPx()
                    val inset = strokePx / 2f
                    val arcSize = Size(
                        (size.width - strokePx).coerceAtLeast(1f),
                        (size.height - strokePx).coerceAtLeast(1f)
                    )
                    val arcTopLeft = Offset(inset, inset)
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val railColor = if (darkSurface) Color(0x26FFFFFF) else Color(0x18000000)
                    onDrawBehind {
                        drawArc(
                            color = railColor,
                            startAngle = 0f,
                            sweepAngle = 360f,
                            useCenter = false,
                            topLeft = arcTopLeft,
                            size = arcSize,
                            style = Stroke(width = strokePx, cap = StrokeCap.Round)
                        )
                        val safeProgress = animatedProgress.coerceIn(0f, 1f)
                        val sweep = 360f * safeProgress
                        if (sweep <= 0.5f) return@onDrawBehind

                        val usedSweep = 360f - sweep
                        val startAngle = if (usedSweep <= 0.5f) -90f else -90f + usedSweep
                        val drawSweep = if (usedSweep <= 0.5f) 360f else sweep

                        drawArc(
                            color = accent,
                            startAngle = startAngle,
                            sweepAngle = drawSweep,
                            useCenter = false,
                            topLeft = arcTopLeft,
                            size = arcSize,
                            style = Stroke(width = strokePx, cap = StrokeCap.Round)
                        )

                        if (glowAlpha > 0.01f && usedSweep > 0.5f) {
                            val highlightSweep = 32f.coerceAtMost(drawSweep)
                            val fadeStop = highlightSweep / 360f
                            val tipAlpha = (if (darkSurface) 0.55f else 0.48f) * glowAlpha
                            val tipColor = Color.White.copy(alpha = tipAlpha)
                            val clearLight = Color.White.copy(alpha = 0f)
                            rotate(degrees = startAngle, pivot = center) {
                                drawArc(
                                    brush = Brush.sweepGradient(
                                        colorStops = arrayOf(
                                            0f to tipColor,
                                            fadeStop * 0.46f to Color.White.copy(alpha = tipAlpha * 0.35f),
                                            fadeStop to clearLight,
                                            0.95f to clearLight,
                                            1f to tipColor
                                        ),
                                        center = center
                                    ),
                                    startAngle = 0f,
                                    sweepAngle = highlightSweep,
                                    useCenter = false,
                                    topLeft = arcTopLeft,
                                    size = arcSize,
                                    style = Stroke(width = strokePx, cap = StrokeCap.Round)
                                )
                            }
                            lightPainter?.drawArc(
                                canvas = drawContext.canvas,
                                center = center,
                                radii = Size(arcSize.width / 2f, arcSize.height / 2f),
                                strokeWidth = strokePx,
                                headDegrees = startAngle,
                                trailDegrees = 44f.coerceAtMost(drawSweep),
                                accent = accent,
                                alphaReveal = glowAlpha
                            )
                        }
                    }
                }
        )
        val centerFontSize = if (centerText.length >= 3) 12.sp else 15.sp
        Text(
            text = centerText,
            color = centerTextColor,
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = centerFontSize,
                lineHeight = centerFontSize,
                fontWeight = FontWeight.Bold
            ),
            maxLines = 1,
            softWrap = false
        )
    }
}

/**
 * Reusable COUI conic-light glowing progress bar matching [DayAgentProgressRow].
 */
@Composable
fun ConicGlowProgressBar(
    progress: Float,
    accent: Color,
    darkSurface: Boolean,
    modifier: Modifier = Modifier,
    trackHeight: Dp = 5.dp,
    glowEnabled: Boolean = true
) {
    val lightPainter = rememberConicProgressLight()
    val layoutDirection = LocalLayoutDirection.current
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 360, easing = FastOutSlowInEasing),
        label = "conic-glow-progress"
    )
    val glowAlpha by animateFloatAsState(
        targetValue = if (glowEnabled && progress > 0.01f) 1f else 0f,
        animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
        label = "conic-glow-alpha"
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(trackHeight)
            .drawWithCache {
                val radius = CornerRadius(size.height / 2f)
                val railClip = Path().apply {
                    addRoundRect(RoundRect(Rect(0f, 0f, size.width, size.height), radius))
                }
                onDrawBehind {
                    val drawBar: () -> Unit = bar@{
                        val heightPx = size.height
                        val halfHeight = heightPx / 2f
                        val railColor = if (darkSurface) Color(0x26FFFFFF) else Color(0x14000000)
                        drawRoundRect(color = railColor, cornerRadius = radius)
                        val safeProgress = animatedProgress.coerceIn(0f, 1f)
                        val fillWidth = size.width * safeProgress
                        if (fillWidth <= 0f) return@bar
                        val capEnd = fillWidth + halfHeight
                        clipPath(railClip) {
                            drawRoundRect(
                                color = accent,
                                topLeft = Offset(-halfHeight, 0f),
                                size = Size(fillWidth + heightPx, heightPx),
                                cornerRadius = radius
                            )
                            if (glowAlpha > 0f) {
                                val highlightWidth = (size.width * 0.2f).coerceAtMost(fillWidth)
                                val capLeft = fillWidth - highlightWidth - halfHeight
                                drawRoundRect(
                                    brush = Brush.horizontalGradient(
                                        0f to Color.White.copy(alpha = 0f),
                                        1f to Color.White.copy(alpha = 0.85f * glowAlpha),
                                        startX = capLeft,
                                        endX = capEnd
                                    ),
                                    topLeft = Offset(capLeft, 0f),
                                    size = Size(capEnd - capLeft, heightPx),
                                    cornerRadius = radius
                                )
                            }
                        }
                        if (glowAlpha > 0.01f) {
                            if (lightPainter != null) {
                                lightPainter.draw(
                                    canvas = drawContext.canvas,
                                    trackWidth = size.width,
                                    rowHeight = size.height,
                                    progress = safeProgress,
                                    accent = accent,
                                    darkSurface = darkSurface,
                                    alphaReveal = glowAlpha,
                                    shapeReveal = glowAlpha
                                )
                            } else {
                                drawFallbackConicGlow(
                                    fillWidth = fillWidth,
                                    accent = accent,
                                    darkSurface = darkSurface,
                                    alphaReveal = glowAlpha,
                                    shapeReveal = glowAlpha
                                )
                            }
                        }
                    }
                    if (layoutDirection == LayoutDirection.Rtl) {
                        scale(-1f, 1f, pivot = Offset(size.width / 2f, size.height / 2f)) { drawBar() }
                    } else {
                        drawBar()
                    }
                }
            }
    )
}
