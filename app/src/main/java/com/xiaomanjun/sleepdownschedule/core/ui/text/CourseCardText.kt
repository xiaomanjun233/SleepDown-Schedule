package com.xiaomanjun.sleepdownschedule.core.ui.text

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import kotlin.math.abs

/** Resolve colored text against its local card surface, resampling only when its bounds change. */
@Composable
internal fun CourseCardText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color,
    themeColor: Color?,
    style: TextStyle = LocalTextStyle.current,
    fontWeight: FontWeight? = null,
    fontSize: TextUnit = TextUnit.Unspecified,
    lineHeight: TextUnit = TextUnit.Unspecified,
    textAlign: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip
) {
    val fallback = remember(themeColor, color) {
        themeColor?.let {
            val useLightText = color.luminance() > 0.5f
            val contrastColor = if (useLightText) Color.White else Color.Black
            val base = it.copy(alpha = 1f)
            // Add a white component on dark backgrounds; on light backgrounds move toward ink.
            // Keep as much theme color as possible within the readable foreground brightness band.
            var minimumMix = if (useLightText) 0.28f else 0.18f
            var maximumMix = 1f
            repeat(8) {
                val mix = (minimumMix + maximumMix) / 2f
                val luminance = lerp(base, contrastColor, mix).luminance()
                if (if (useLightText) luminance >= 0.72f else luminance <= 0.08f) maximumMix = mix
                else minimumMix = mix
            }
            lerp(base, contrastColor, maximumMix)
        } ?: color
    }
    val background = LocalCourseTextBackground.current
    var target by remember(themeColor, fallback) { mutableStateOf(fallback) }
    val foreground by animateColorAsState(target, tween(180), label = "course-text-lightness")
    val coordinates = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val layout = remember { arrayOfNulls<TextLayoutResult>(1) }
    val lastBounds = remember(background, themeColor, fallback) { arrayOfNulls<Rect>(1) }
    val lastSamples = remember(background, themeColor, fallback) { arrayOfNulls<FloatArray>(1) }
    fun updateForeground() {
        if (themeColor == null || background == null) { target = fallback; return }
        if (background.frozen) return
        val position = coordinates[0]?.takeIf { it.isAttached } ?: return
        val measured = layout[0]?.takeIf { it.lineCount > 0 } ?: return
        val bounds = Rect(
            (0 until measured.lineCount).minOf { measured.getLineLeft(it) }, measured.getLineTop(0),
            (0 until measured.lineCount).maxOf { measured.getLineRight(it) }, measured.getLineBottom(measured.lineCount - 1)
        ).translate(position.localToWindow(Offset.Zero))
        if (lastBounds[0] == bounds) return
        lastBounds[0] = bounds
        val samples = background.sample(bounds)
        if (samples == null) { lastSamples[0] = null; target = fallback; return }
        val previousSamples = lastSamples[0]
        // Scrolling over a flat/blurred area should not solve the same color palette every frame.
        if (previousSamples != null && previousSamples.size == samples.size &&
            samples.indices.all { abs(samples[it] - previousSamples[it]) < 0.012f }) return
        lastSamples[0] = samples
        target = courseTextColorForBackground(themeColor, samples, target)
    }
    LaunchedEffect(background, themeColor, fallback) { updateForeground() }
    Text(
        text = text,
        modifier = modifier.onGloballyPositioned { coordinates[0] = it; updateForeground() },
        color = if (themeColor == null) color else foreground,
        style = style,
        fontWeight = if (themeColor != null) maxOf(fontWeight ?: style.fontWeight ?: FontWeight.Normal, FontWeight.Bold) else fontWeight,
        fontSize = fontSize,
        lineHeight = lineHeight,
        textAlign = textAlign,
        maxLines = maxLines,
        overflow = overflow,
        onTextLayout = { layout[0] = it; updateForeground() }
    )
}
