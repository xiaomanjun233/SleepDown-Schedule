package com.xiaomanjun.sleepdownschedule.feature.feedback

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.xiaomanjun.sleepdownschedule.core.ui.controls.drawFallbackConicGlow
import com.xiaomanjun.sleepdownschedule.core.ui.controls.rememberConicProgressLight

@Composable
internal fun FeedbackProgress(stage: FeedbackStatus, accent: Color, dark: Boolean, modifier: Modifier = Modifier) {
    val step = animateFloatAsState(stage.step.toFloat(), tween(260), label = "feedback-stage")
    val painter = rememberConicProgressLight()
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val track = if (dark) Color(0x26FFFFFF) else Color(0x14000000)
    Box(modifier.fillMaxWidth().height(4.dp).drawWithCache {
        val count = FeedbackStatus.StepCount
        val gap = 4.dp.toPx()
        val width = ((size.width - gap * (count - 1)) / count).coerceAtLeast(0f)
        val stride = width + gap
        val radius = CornerRadius(size.height / 2f)
        val clip = Path().apply {
            repeat(count) { index ->
                val x = stride * index
                addRoundRect(RoundRect(Rect(x, 0f, x + width, size.height), radius))
            }
        }
        onDrawBehind {
            if (width <= 0f) return@onDrawBehind
            scale(if (rtl) -1f else 1f, 1f, pivot = center) {
                clipPath(clip) {
                    drawRect(track)
                    val position = step.value.coerceIn(0f, count.toFloat())
                    if (position > 0f) {
                        val activeIndex = (kotlin.math.ceil(position).toInt() - 1).coerceAtLeast(0)
                        val fillWidth = activeIndex * stride + (position - activeIndex) * width
                        drawRect(accent, size = Size(fillWidth, size.height))
                        val edgeWidth = (width * 0.75f).coerceAtMost(fillWidth)
                        drawRect(
                            brush = Brush.horizontalGradient(
                                listOf(Color.Transparent, Color.White.copy(alpha = 0.78f)),
                                startX = fillWidth - edgeWidth,
                                endX = fillWidth,
                            ),
                            topLeft = Offset(fillWidth - edgeWidth, 0f),
                            size = Size(edgeWidth, size.height),
                        )
                        if (painter != null) {
                            painter.draw(drawContext.canvas, size.width, size.height, fillWidth / size.width,
                                accent, dark, alphaReveal = 0.88f, shapeReveal = 0.72f)
                        } else {
                            drawFallbackConicGlow(fillWidth, accent, dark, alphaReveal = 0.88f, shapeReveal = 0.72f)
                        }
                    }
                }
            }
        }
    })
}
