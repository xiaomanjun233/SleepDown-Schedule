package com.xiaomanjun.sleepdownschedule.core.ui.text

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.math.abs

internal class CourseTextBackground(
    val frozen: Boolean,
    val sample: (Rect) -> FloatArray?
)

internal val LocalCourseTextBackground = compositionLocalOf<CourseTextBackground?> { null }

/** The page keeps its current text contrast while cards move under the wallpaper. */
internal val LocalCourseTextMotionFrozen = compositionLocalOf { false }

/** Keep the course hue while adjusting lightness and saturation only when contrast needs help. */
internal fun courseTextColorForBackground(seed: Color, samples: FloatArray, previous: Color): Color {
    val backgrounds = samples.filter { it.isFinite() }.map { it.coerceIn(0f, 1f) }
    if (backgrounds.isEmpty()) return previous
    val high = maxOf(seed.red, seed.green, seed.blue)
    val low = minOf(seed.red, seed.green, seed.blue)
    val delta = high - low
    val lightness = (high + low) / 2f
    val saturation = if (delta < 0.0001f) 0f else delta / (1f - abs(2f * lightness - 1f))
    val hue = if (delta < 0.0001f) 0f else {
        val sector = when (high) {
            seed.red -> (seed.green - seed.blue) / delta
            seed.green -> (seed.blue - seed.red) / delta + 2f
            else -> (seed.red - seed.green) / delta + 4f
        }
        (sector * 60f + 360f) % 360f
    }
    fun contrast(color: Color): Float {
        val foreground = color.luminance()
        val ratios = backgrounds.map { (maxOf(it, foreground) + 0.05f) / (minOf(it, foreground) + 0.05f) }.sorted()
        // A card blurs fine texture. Protect the least readable fifth, without following single pixels.
        return ratios[((ratios.size - 1) * 0.2f).toInt()]
    }
    val original = seed.copy(alpha = 1f)
    if (contrast(original) >= 4.5f) return original
    val previousLight = previous.luminance() > 0.18f
    val candidates = (0..64).flatMap { index ->
        val level = 0.08f + index * (0.88f / 64f)
        listOf(1f, 0.85f, 0.7f, 0.55f).map { saturationScale ->
            val adjustedSaturation = (saturation * saturationScale).coerceIn(0f, 1f)
            val color = Color.hsl(hue, adjustedSaturation, level)
            val change = abs(level - lightness) +
                abs(adjustedSaturation - saturation) * 0.35f +
                if ((color.luminance() > 0.18f) != previousLight) 0.06f else 0f
            Triple(color, contrast(color), change)
        }
    }
    val chosen = candidates.filter { it.second >= 4.5f }.minByOrNull { it.third }
        ?: candidates.maxBy { it.second }
    val previousContrast = contrast(previous)
    // Keep the current polarity in the narrow band where neither choice has a real advantage.
    if ((chosen.first.luminance() > 0.18f) != previousLight &&
        previousContrast >= 4.2f && chosen.second < previousContrast + 0.6f) return previous
    return chosen.first
}
