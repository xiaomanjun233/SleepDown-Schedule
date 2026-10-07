package com.xiaomanjun.sleepdownschedule.core.ui.text

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.abs
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** All labels share the course ink; local surface samples only control a diffuse shadow. */
@Composable
internal fun CourseCardText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color,
    coloredText: Boolean = false,
    style: TextStyle = LocalTextStyle.current,
    fontWeight: FontWeight? = null,
    fontSize: TextUnit = TextUnit.Unspecified,
    lineHeight: TextUnit = TextUnit.Unspecified,
    textAlign: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    adaptiveContrast: Boolean = true
) {
    // The parent resolves the course hue once. Metadata uses the same opaque ink as the
    // title, so background differences cannot turn individual labels into opposing colors.
    val displayedColor = if (coloredText) color.copy(alpha = 1f) else color
    val background = if (adaptiveContrast) LocalCourseTextBackground.current else null
    val flatShadowStrength = background?.flatLuminance?.let {
        softTextShadowStrength(floatArrayOf(it), displayedColor.luminance(), displayedColor.alpha)
    }
    var targetShadowStrength by remember(displayedColor) { mutableFloatStateOf(0f) }
    val animatedShadowStrength by animateFloatAsState(targetShadowStrength, tween(160), label = "course-text-soft-shadow")
    val shadowStrength = flatShadowStrength ?: animatedShadowStrength
    val coordinates = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val layout = remember { arrayOfNulls<TextLayoutResult>(1) }
    val lastBounds = remember(background, displayedColor) { arrayOfNulls<Rect>(1) }
    val lastSamples = remember(background, displayedColor) { arrayOfNulls<FloatArray>(1) }
    val scope = rememberCoroutineScope()
    val observedOrigin = remember { arrayOfNulls<Offset>(1) }
    val resolved = remember(background, displayedColor) { booleanArrayOf(false) }
    val lastMoveNanos = remember { longArrayOf(0L) }
    val settleJob = remember { arrayOfNulls<Job>(1) }
    fun updateShadow(): Boolean {
        if (background == null) {
            targetShadowStrength = 0f
            return true
        }
        if (background.frozen) return false
        val position = coordinates[0]?.takeIf { it.isAttached } ?: return false
        val measured = layout[0]?.takeIf { it.lineCount > 0 } ?: return false
        val bounds = Rect(
            (0 until measured.lineCount).minOf { measured.getLineLeft(it) }, measured.getLineTop(0),
            (0 until measured.lineCount).maxOf { measured.getLineRight(it) }, measured.getLineBottom(measured.lineCount - 1)
        ).translate(position.localToWindow(Offset.Zero))
        if (lastBounds[0] == bounds) return true
        lastBounds[0] = bounds
        val samples = background.sample(bounds)
        if (samples == null) {
            lastSamples[0] = null
            targetShadowStrength = 0f
            return true
        }
        val previousSamples = lastSamples[0]
        // Similar samples keep shadow strength stable without repeating the contrast calculation.
        if (previousSamples != null && previousSamples.size == samples.size &&
            samples.indices.all { abs(samples[it] - previousSamples[it]) < 0.012f }) return true
        lastSamples[0] = samples
        targetShadowStrength = softTextShadowStrength(
            samples, displayedColor.luminance(), displayedColor.alpha, targetShadowStrength
        )
        return true
    }
    fun updateAfterMotion() {
        // The page resumes sampling through LaunchedEffect(background) after settling.
        // Keep the coordinate reference current without mapping every label to the window
        // or starting settle jobs on each frame of a pager swipe or vertical scroll.
        if (background == null || background.frozen) return
        val position = coordinates[0]?.takeIf { it.isAttached } ?: return
        val origin = position.localToWindow(Offset.Zero)
        if (observedOrigin[0] == origin) {
            if (!resolved[0]) resolved[0] = updateShadow()
            return
        }
        observedOrigin[0] = origin
        if (!resolved[0]) {
            resolved[0] = updateShadow()
            return
        }
        // Keep shadows stable during a swipe or scroll, then sample the settled wallpaper.
        lastMoveNanos[0] = System.nanoTime()
        if (settleJob[0]?.isActive == true) return
        settleJob[0] = scope.launch {
            while ((System.nanoTime() - lastMoveNanos[0]) < 180_000_000L) delay(60)
            resolved[0] = updateShadow()
        }
    }
    LaunchedEffect(background, displayedColor) {
        settleJob[0]?.cancel()
        resolved[0] = updateShadow()
    }
    val density = LocalDensity.current
    val lightText = courseTextNeedsDarkShadow(displayedColor)
    val effectiveFontSize = when {
        fontSize != TextUnit.Unspecified -> fontSize
        style.fontSize != TextUnit.Unspecified -> style.fontSize
        else -> 14.sp
    }
    val shadowStyle = if (shadowStrength <= 0.001f) style else {
        val radius = with(density) {
            if (coloredText && flatShadowStrength != null) {
                (effectiveFontSize.toPx() * 0.42f).coerceIn(4.dp.toPx(), 8.dp.toPx())
            } else if (coloredText) {
                (effectiveFontSize.toPx() * 0.14f).coerceIn(1.5.dp.toPx(), 3.dp.toPx())
            } else {
                (effectiveFontSize.toPx() * 0.30f).coerceIn(3.dp.toPx(), 6.dp.toPx())
            }
        }
        // Fixed monochrome ink needs a faint, diffuse backing rather than a visible rim.
        val maximumShadowAlpha = when {
            coloredText && flatShadowStrength != null -> if (lightText) 0.54f else 0.30f
            coloredText -> if (lightText) 0.72f else 0.38f
            lightText -> 0.28f
            else -> 0.18f
        }
        style.copy(shadow = Shadow(
            color = (if (lightText) Color.Black else Color.White).copy(
                alpha = maximumShadowAlpha * shadowStrength
            ),
            offset = if (coloredText && flatShadowStrength == null)
                Offset(0f, with(density) { 0.5.dp.toPx() }) else Offset.Zero,
            blurRadius = radius
        ))
    }
    Text(
        text = text,
        modifier = if (background == null || flatShadowStrength != null) modifier else modifier.onGloballyPositioned {
            coordinates[0] = it
            updateAfterMotion()
        },
        color = displayedColor,
        style = shadowStyle,
        fontWeight = if (coloredText) maxOf(fontWeight ?: style.fontWeight ?: FontWeight.Normal, FontWeight.Bold) else fontWeight,
        fontSize = fontSize,
        lineHeight = lineHeight,
        textAlign = textAlign,
        maxLines = maxLines,
        overflow = overflow,
        onTextLayout = { layout[0] = it; if (settleJob[0]?.isActive != true) resolved[0] = updateShadow() }
    )
}
