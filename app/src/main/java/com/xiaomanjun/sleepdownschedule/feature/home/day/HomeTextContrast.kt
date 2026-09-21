package com.xiaomanjun.sleepdownschedule.feature.home.day

import kotlin.math.pow
import kotlin.math.roundToInt

/** Keep the chosen text color; protect the difficult parts of its background with a soft shadow. */
internal fun homeTextShadowStrength(
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
        // Conservative opacity penalty: translucent secondary labels need at least as much help.
        1f + (opaqueContrast - 1f) * textAlpha.coerceIn(0f, 1f)
    }.sorted()
    if (ratios.isEmpty()) return 0f
    // Ignore isolated pixels, but retain narrow stripes that the old average washed away.
    val difficultContrast = ratios[((ratios.size - 1) * 0.10f).toInt()]
    val exitThreshold = if (currentStrength > 0f) 5.4f else 4.8f
    if (difficultContrast >= exitThreshold) return 0f
    val strength = ((4.8f - difficultContrast) / 3.8f).coerceIn(0.125f, 1f)
    // Eight levels suppress subpixel sampling noise; the UI blends between these targets.
    return (strength * 8f).roundToInt() / 8f
}

/** The black wallpaper overlay multiplies sRGB channels before relative luminance is computed. */
internal fun visibleWallpaperLuminance(argb: Int, brightness: Float): Float {
    val dim = brightness.coerceIn(0.35f, 1f)
    fun channel(shift: Int): Float {
        val value = ((argb ushr shift) and 0xff) / 255f * dim
        return if (value <= 0.04045f) value / 12.92f else ((value + 0.055f) / 1.055f).pow(2.4f)
    }
    return channel(16) * 0.2126f + channel(8) * 0.7152f + channel(0) * 0.0722f
}
