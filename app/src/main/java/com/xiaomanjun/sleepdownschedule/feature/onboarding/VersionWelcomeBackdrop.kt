package com.xiaomanjun.sleepdownschedule.feature.onboarding

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp

internal const val WelcomePanelStartMillis = 2_440f
internal const val WelcomePanelSettleMillis = WelcomePanelStartMillis + 900f
private val WelcomePaper = Color(0xFFEEEEEF)
private val PanelDropEasing = CubicBezierEasing(0.22f, 0f, 0.24f, 1f)

internal fun Modifier.versionWelcomeBackdrop(panelDrop: Dp, elapsedMillis: () -> Float) = drawWithCache {
    val outline = Path()
    val maxDrop = panelDrop.toPx()
    val bounce = minOf(10.dp.toPx(), maxDrop)
    val maxCorner = 32.dp.toPx()
    val openingWidth = size.minDimension.coerceAtMost(420.dp.toPx())
    val warmLight = radialLight(Color(0xFFFFD69D))
    val coolLight = radialLight(Color(0xFFB4DBFF))
    val arch = Brush.radialGradient(
        0.38f to Color.Transparent,
        0.54f to Color(0x28C2D9EB),
        0.63f to Color(0x49E9C4C9),
        0.74f to Color(0x58F3D9AC),
        0.88f to Color(0x54BDE3F0),
        1f to Color.Transparent,
        center = Offset.Zero,
        radius = 1f,
    )
    val violet = radialLight(Color(0xFFCE90DA))
    val apricot = radialLight(Color(0xFFF9C39C))
    val blue = radialLight(Color(0xFF8BBEFA))

    onDrawBehind {
        drawRect(Color.Black)
        val millis = elapsedMillis()
        if (millis < WelcomePanelStartMillis) {
            val glow = smoothSegment(millis, 1_520f, 2_150f)
            val bloom = openingWidth * lerp(0.18f, 0.70f, glow)
            val origin = Offset(center.x, size.height * 0.52f)
            drawLight(warmLight, origin - Offset(bloom * 0.35f, bloom * 0.15f), bloom, bloom * 0.8f, 0.80f * glow)
            drawLight(coolLight, origin + Offset(bloom * 0.35f, bloom * 0.15f), bloom, bloom * 0.8f, 0.85f * glow)
            drawRect(WelcomePaper, alpha = smoothSegment(millis, 2_140f, WelcomePanelStartMillis))
        } else {
            val slideMillis = millis - WelcomePanelStartMillis
            val top = when {
                slideMillis < 540f -> maxDrop * PanelDropEasing.transform(segment(slideMillis, 0f, 540f))
                slideMillis < 720f -> lerp(maxDrop, maxDrop - bounce, smoothSegment(slideMillis, 540f, 720f))
                else -> lerp(maxDrop - bounce, maxDrop, smoothSegment(slideMillis, 720f, 900f))
            }
            val corner = maxCorner * smoothSegment(slideMillis, 0f, 300f)
            val panelHeight = size.height - top
            outline.rewind()
            outline.addRoundRect(RoundRect(Rect(0f, top, size.width, size.height + corner), CornerRadius(corner)))
            clipPath(outline) {
                drawRect(WelcomePaper)
                val archRise = smoothSegment(slideMillis, 50f, 950f)
                val archCenter = Offset(center.x, top + panelHeight * lerp(1.06f, 0.37f, archRise))
                clipRect(bottom = archCenter.y.coerceAtMost(size.height)) {
                    drawLight(arch, archCenter, size.width * 1.15f, panelHeight * 0.38f, archRise)
                }
                val bloom = smoothSegment(slideMillis, 660f, 2_150f)
                val drift = smoothSegment(slideMillis, 1_200f, 4_500f)
                val bottom = size.height + panelHeight * lerp(0.16f, 0.02f, bloom)
                drawLight(violet, Offset(size.width * lerp(0.10f, 0.02f, drift), bottom),
                    size.width * 0.62f, panelHeight * 0.27f, 0.76f * bloom)
                drawLight(apricot, Offset(size.width * lerp(0.38f, 0.49f, drift), bottom),
                    size.width * 0.68f, panelHeight * 0.27f, 0.76f * bloom)
                drawLight(blue, Offset(size.width * 0.98f, bottom),
                    size.width * 0.53f, panelHeight * 0.25f, 0.72f * bloom)
            }
        }
    }
}

private fun radialLight(color: Color) = Brush.radialGradient(
    0f to color,
    0.42f to color.copy(alpha = 0.70f),
    0.73f to color.copy(alpha = 0.22f),
    1f to color.copy(alpha = 0f),
    center = Offset.Zero,
    radius = 1f,
)

private fun DrawScope.drawLight(brush: Brush, position: Offset, radiusX: Float, radiusY: Float, opacity: Float) {
    if (opacity <= 0f) return
    withTransform({
        translate(position.x, position.y)
        scale(radiusX, radiusY, Offset.Zero)
    }) {
        drawCircle(brush, radius = 1f, center = Offset.Zero, alpha = opacity)
    }
}

private fun segment(millis: Float, start: Float, end: Float): Float =
    ((millis - start) / (end - start)).coerceIn(0f, 1f)

internal fun smoothSegment(millis: Float, start: Float, end: Float): Float {
    val progress = segment(millis, start, end)
    return progress * progress * (3f - 2f * progress)
}
