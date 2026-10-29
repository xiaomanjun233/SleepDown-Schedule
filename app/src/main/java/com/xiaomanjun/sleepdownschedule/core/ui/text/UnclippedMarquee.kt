package com.xiaomanjun.sleepdownschedule.core.ui.text

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.roundToInt

/** The containing capsule owns clipping; the text adds no rectangular side cuts. */
@Composable
fun Modifier.unclippedMarquee(centered: Boolean = false, enabled: Boolean = true): Modifier {
    val density = LocalDensity.current
    val frozen = LocalHomeTextContrastFrozen.current
    val offset = remember { Animatable(0f) }
    var contentWidth by remember { mutableIntStateOf(0) }
    var viewportWidth by remember { mutableIntStateOf(0) }
    val gap = with(density) { 24.dp.toPx() }
    val velocity = with(density) { 30.dp.toPx() }
    val scroll = enabled && !frozen.value && contentWidth > viewportWidth && viewportWidth > 0
    LaunchedEffect(scroll, contentWidth, viewportWidth, gap, velocity) {
        offset.snapTo(0f)
        if (scroll) {
            delay(1200)
            while (isActive) {
                val distance = contentWidth + gap
                offset.animateTo(distance, tween((distance / velocity * 1000).roundToInt().coerceAtLeast(1),
                    easing = LinearEasing))
                offset.snapTo(0f)
                delay(1200)
            }
        }
    }
    return this.layout { measurable, constraints ->
        val child = measurable.measure(constraints.copy(minWidth = 0, maxWidth = Constraints.Infinity))
        val width = constraints.constrainWidth(child.width)
        contentWidth = child.width
        viewportWidth = width
        layout(width, child.height) {
            child.placeRelative(if (centered && child.width <= width) (width - child.width) / 2 else 0, 0)
        }
    }.drawWithContent {
        translate(left = -offset.value) { this@drawWithContent.drawContent() }
        if (scroll) translate(left = contentWidth + gap - offset.value) { this@drawWithContent.drawContent() }
    }
}
