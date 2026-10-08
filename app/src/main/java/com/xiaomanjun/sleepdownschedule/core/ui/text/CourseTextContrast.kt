package com.xiaomanjun.sleepdownschedule.core.ui.text

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.State
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.math.roundToInt

internal class CourseTextBackground(
    private val isFrozen: () -> Boolean,
    val flatLuminance: Float? = null,
    val sample: (Rect) -> FloatArray?
) {
    val frozen: Boolean get() = isFrozen()
}

internal val LocalCourseTextBackground = compositionLocalOf<CourseTextBackground?> { null }

/** The page keeps its current text contrast while cards move under the wallpaper. */
private val CourseTextAtRest = object : State<Boolean> { override val value = false }
internal val LocalCourseTextMotionFrozen = compositionLocalOf<State<Boolean>> { CourseTextAtRest }

/** Keep the supplied text color fixed; add a soft opposite-color shadow only where needed. */
internal fun softTextShadowStrength(
    luminances: FloatArray,
    textLuminance: Float,
    textAlpha: Float = 1f,
    currentStrength: Float = 0f
): Float {
    if (!textLuminance.isFinite()) return 0f
    val foreground = textLuminance.coerceIn(0f, 1f)
    val ratios = luminances.filter { it.isFinite() }.map { value ->
        val background = value.coerceIn(0f, 1f)
        val opaqueContrast = (maxOf(foreground, background) + 0.05f) / (minOf(foreground, background) + 0.05f)
        1f + (opaqueContrast - 1f) * textAlpha.coerceIn(0f, 1f)
    }.sorted()
    if (ratios.isEmpty()) return 0f
    val difficultContrast = ratios[((ratios.size - 1) * 0.10f).toInt()]
    val exitThreshold = if (currentStrength > 0f) 6.0f else 5.2f
    if (difficultContrast >= exitThreshold) return 0f
    val strength = ((5.2f - difficultContrast) / 4.2f).coerceIn(0.125f, 1f)
    return (strength * 8f).roundToInt() / 8f
}

private fun courseHue(seed: Color): Float {
    val high = maxOf(seed.red, seed.green, seed.blue)
    val low = minOf(seed.red, seed.green, seed.blue)
    val delta = high - low
    return if (delta < 0.0001f) 0f else {
        val sector = when (high) {
            seed.red -> (seed.green - seed.blue) / delta
            seed.green -> (seed.blue - seed.red) / delta + 2f
            else -> (seed.red - seed.green) / delta + 4f
        }
        (sector * 60f + 360f) % 360f
    }
}

/** Keep the course hue vivid while lifting colored lettering over wallpaper glass. */
internal fun courseTextColorForPage(seed: Color, hasWallpaper: Boolean, lightText: Boolean): Color {
    val value = maxOf(seed.red, seed.green, seed.blue)
    val chroma = value - minOf(seed.red, seed.green, seed.blue)
    val saturation = if (value > 0f) chroma / value else 0f
    // Resolve a luminous version of the same hue first, including on flat cards. Returning
    // the seed on a flat card painted the glyph and its background with identical ink.
    // Lift value AND reduce saturation: every channel becomes lighter than the original.
    return Color.hsv(
        hue = courseHue(seed),
        saturation = saturation * if (hasWallpaper) {
            if (lightText) 0.62f else 0.68f
        } else 0.46f,
        value = 1f
    )
}

/** Choose the higher-contrast shadow for the actual colored glyph, not the page foreground. */
internal fun courseTextNeedsDarkShadow(color: Color): Boolean = color.luminance() > 0.17912878f
