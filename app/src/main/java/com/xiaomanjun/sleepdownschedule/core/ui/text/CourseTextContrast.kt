package com.xiaomanjun.sleepdownschedule.core.ui.text

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.math.abs
import kotlin.math.roundToInt

internal class CourseTextBackground(
    val frozen: Boolean,
    val sample: (Rect) -> FloatArray?
)

internal val LocalCourseTextBackground = compositionLocalOf<CourseTextBackground?> { null }

/** The page keeps its current text contrast while cards move under the wallpaper. */
internal val LocalCourseTextMotionFrozen = compositionLocalOf { false }

/** Keep monochrome text polarity fixed; add a soft opposite-color shadow only where needed. */
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

private data class CourseHsl(val hue: Float, val saturation: Float, val lightness: Float)

private fun courseHsl(seed: Color): CourseHsl {
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
    return CourseHsl(hue, saturation, lightness)
}

/** Day cards share the page's light/dark direction while keeping each course hue visible. */
internal fun courseTextColorForPage(seed: Color, pageLuminance: Float, lightText: Boolean): Color {
    val (hue, saturation, lightness) = courseHsl(seed)
    val page = pageLuminance.coerceIn(0f, 1f)
    val darkAmount = ((0.55f - page) / 0.55f).coerceIn(0f, 1f)
    val brightAmount = ((page - 0.45f) / 0.55f).coerceIn(0f, 1f)
    val coloredSaturation = if (saturation < 0.01f) 0f else {
        (saturation * (if (lightText) 1f + darkAmount * 0.2f else 1f - brightAmount * 0.25f))
            .coerceIn(0.42f, 0.95f)
    }
    val coloredLightness = if (lightText) {
        (0.68f + (lightness - 0.5f) * 0.2f + darkAmount * 0.06f).coerceIn(0.62f, 0.82f)
    } else {
        (0.36f + (lightness - 0.5f) * 0.12f - brightAmount * 0.05f).coerceIn(0.26f, 0.40f)
    }
    return Color.hsl(hue, coloredSaturation, coloredLightness)
}

/** Keep the course hue while adjusting lightness and saturation for the sampled background. */
internal fun courseTextColorForBackground(seed: Color, samples: FloatArray, previous: Color): Color {
    val backgrounds = samples.filter { it.isFinite() }.map { it.coerceIn(0f, 1f) }
    if (backgrounds.isEmpty()) return previous
    val (hue, saturation, lightness) = courseHsl(seed)
    fun contrast(color: Color): Float {
        val foreground = color.luminance()
        val ratios = backgrounds.map { (maxOf(it, foreground) + 0.05f) / (minOf(it, foreground) + 0.05f) }.sorted()
        // A card blurs fine texture. Protect the least readable fifth, without following single pixels.
        return ratios[((ratios.size - 1) * 0.2f).toInt()]
    }
    val medianBackground = backgrounds.sorted()[backgrounds.size / 2]
    val darkAmount = ((0.25f - medianBackground) / 0.25f).coerceIn(0f, 1f)
    val brightAmount = ((medianBackground - 0.55f) / 0.45f).coerceIn(0f, 1f)
    // Dark glass can carry a brighter, richer hue. Bright glass needs a quieter, darker ink.
    val preferredSaturation = (saturation * (1f + darkAmount * 0.20f - brightAmount * 0.30f))
        .coerceIn(0f, 1f)
    val preferredLightness = (lightness + darkAmount * 0.10f - brightAmount * 0.12f)
        .coerceIn(0.08f, 0.96f)
    val original = seed.copy(alpha = 1f)
    if (darkAmount < 0.01f && brightAmount < 0.01f &&
        contrast(original) >= 4.5f) return original
    val previousLight = previous.luminance() > 0.18f
    val preferred = Color.hsl(hue, preferredSaturation, preferredLightness)
    val preferredContrast = contrast(preferred)
    if (preferredContrast >= 4.5f) {
        val previousContrast = contrast(previous)
        if ((preferred.luminance() > 0.18f) != previousLight &&
            previousContrast >= 4.2f && preferredContrast < previousContrast + 0.6f) return previous
        return preferred
    }
    val candidates = (0..64).flatMap { index ->
        val level = 0.08f + index * (0.88f / 64f)
        listOf(preferredSaturation, preferredSaturation * 0.85f,
            preferredSaturation * 0.7f, saturation).map { adjustedSaturation ->
            val color = Color.hsl(hue, adjustedSaturation, level)
            val change = abs(level - preferredLightness) +
                abs(adjustedSaturation - preferredSaturation) * 0.35f +
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
