package com.xiaomanjun.sleepdownschedule.feature.onboarding

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.xiaomanjun.sleepdownschedule.feature.home.LocalHomePaneVisible

internal const val WelcomeRevealMillis = 860f
internal const val LiquidSettleMillis = 4_570f
private val RevealEasing = CubicBezierEasing(0.28f, 0f, 0.20f, 1f)
private val CardLandingEasing = CubicBezierEasing(0.17f, 0.82f, 0.22f, 1f)
private val LiquidWhite = Color.White
private val LiquidEdge = Color(0xFFBAC9E1)

/** Draw the opening calendar and the Continue surface as a single moving shape. */
internal fun Modifier.versionWelcomeLiquid(
    elapsedMillis: () -> Float,
    buttonBounds: () -> Rect?,
) = drawWithCache {
    val outline = Path().apply { fillType = PathFillType.EvenOdd }
    val calendarSize = (size.minDimension * 0.27f).coerceAtMost(108.dp.toPx())
    val travelStart = Offset(size.width * 0.5f, size.height * 0.42f)
    val edgeWidth = 1.dp.toPx()
    onDrawBehind {
        val button = buttonBounds() ?: return@onDrawBehind
        if (button.isEmpty) return@onDrawBehind
        val elapsed = elapsedMillis()
        val appearing = smoothSegment(elapsed, 70f, 460f)
        val assembling = smoothSegment(elapsed, 360f, 1_070f)
        val falling = smoothSegment(elapsed, 1_440f, 4_020f)
        val spreading = smoothSegment(elapsed, 3_450f, LiquidSettleMillis)
        val gathering = smoothSegment(elapsed, 1_400f, 1_950f)
        val stretch = smoothSegment(elapsed, 1_660f, 2_240f) *
            (1f - smoothSegment(elapsed, 2_430f, 3_160f))
        val rebound = smoothSegment(elapsed, 3_830f, 4_170f) *
            (1f - smoothSegment(elapsed, 4_170f, LiquidSettleMillis))
        val position = Offset(
            travelStart.x + (button.center.x - travelStart.x) * falling -
                calendarSize * 0.22f * stretch,
            lerp(travelStart.y, button.center.y, falling) + 6.dp.toPx() * rebound,
        )
        val body = calendarSize * lerp(0.34f, 1f, assembling) * lerp(0.10f, 1f, appearing)
        val width = lerp(body * (1f - stretch * 0.33f), button.width, spreading)
        val height = lerp(body * (0.94f + stretch * 0.48f), button.height, spreading) -
            5.dp.toPx() * rebound
        val radius = minOf(width, height) * lerp(lerp(0.50f, 0.23f, assembling), 0.50f, maxOf(gathering, spreading))
        val rect = Rect(position.x - width / 2, position.y - height / 2,
            position.x + width / 2, position.y + height / 2)
        val cuts = assembling * (1f - gathering)

        outline.rewind()
        outline.addRoundRect(RoundRect(rect, CornerRadius(radius)))
        if (cuts > 0f) {
            val divider = Rect(position.x - width * 0.35f, rect.top + height * 0.27f,
                position.x + width * 0.35f, rect.top + height * (0.27f + 0.065f * cuts))
            outline.addRoundRect(RoundRect(divider, CornerRadius(divider.height / 2)))
            val cellSize = minOf(width, height) * 0.18f * cuts
            for (column in 0..1) {
                for (row in 0..1) {
                    val cellCenter = Offset(
                        position.x + width * (column - 0.5f) * 0.32f,
                        rect.top + height * (0.53f + row * 0.23f),
                    )
                    outline.addRoundRect(RoundRect(Rect(cellCenter, cellSize * 0.5f), CornerRadius(cellSize * 0.27f)))
                }
            }
            val tabWidth = width * 0.12f
            for (direction in -1..1 step 2) {
                drawRoundRect(
                    LiquidWhite,
                    topLeft = Offset(position.x + direction * width * 0.23f - tabWidth / 2,
                        rect.top - height * 0.17f * cuts),
                    size = Size(tabWidth, height * (0.10f + 0.17f * cuts)),
                    cornerRadius = CornerRadius(tabWidth * 0.5f),
                    alpha = appearing,
                )
            }
        }
        drawPath(outline, LiquidWhite, alpha = appearing)
        val onPaper = smoothSegment(elapsed, 2_140f, 2_530f)
        if (onPaper > 0f) {
            drawRoundRect(LiquidEdge, rect.topLeft, rect.size, CornerRadius(radius),
                alpha = onPaper * 0.45f, style = Stroke(edgeWidth))
        }
    }
}

/** The inverse clip leaves the already laid-out timetable visible through the expanding button. */
internal fun Modifier.versionWelcomeAperture(
    reveal: State<Float>,
    buttonBounds: () -> Rect?,
) = drawWithCache {
    val cutout = Path()
    val rimWidth = 1.dp.toPx()
    val extraRadius = 23.dp.toPx()
    onDrawWithContent {
        val progress = reveal.value
        if (progress >= 1f) return@onDrawWithContent
        val button = buttonBounds()
        if (progress <= 0f || button == null) {
            drawContent()
            return@onDrawWithContent
        }
        val expand = RevealEasing.transform(progress)
        val bounds = Rect(lerp(button.left, 0f, expand), lerp(button.top, 0f, expand),
            lerp(button.right, size.width, expand), lerp(button.bottom, size.height, expand))
        val radius = (button.height / 2 + extraRadius * smoothSegment(progress, 0f, 0.43f)) *
            (1f - smoothSegment(progress, 0.54f, 1f))
        val corner = CornerRadius(radius.coerceAtMost(minOf(bounds.width, bounds.height) / 2))
        cutout.rewind()
        cutout.addRoundRect(RoundRect(bounds, corner))
        clipPath(cutout, ClipOp.Difference) { this@onDrawWithContent.drawContent() }
        drawRoundRect(LiquidWhite, bounds.topLeft, bounds.size, corner,
            alpha = 1f - smoothSegment(progress, 0f, 0.32f))
        drawRoundRect(LiquidWhite, bounds.topLeft, bounds.size, corner,
            alpha = 0.65f * (1f - smoothSegment(progress, 0.20f, 0.88f)), style = Stroke(rimWidth))
    }
}

internal val LocalWelcomeCourseFlight = compositionLocalOf<State<Float>?> { null }

@Composable
internal fun Modifier.welcomeCourseFlight(orderFraction: Float): Modifier {
    val clock = LocalWelcomeCourseFlight.current ?: return this
    val paneVisible = LocalHomePaneVisible.current
    return graphicsLayer {
        // The lower part of the grid is uncovered first as the bottom button grows upward.
        val delay = 0.29f + (1f - orderFraction.coerceIn(0f, 1f)) * 0.24f
        val elapsed = if (paneVisible.value) clock.value else 1f
        val progress = ((elapsed - delay) / 0.46f).coerceIn(0f, 1f)
        val remaining = 1f - CardLandingEasing.transform(progress)
        translationY = 96.dp.toPx() * remaining
        scaleX = 1f - 0.09f * remaining
        scaleY = scaleX
        alpha = smoothSegment(progress, 0f, 0.48f)
        compositingStrategy = CompositingStrategy.ModulateAlpha
    }
}
