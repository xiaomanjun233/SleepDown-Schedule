package com.xiaomanjun.sleepdownschedule.feature.home.day

import com.xiaomanjun.sleepdownschedule.core.ui.text.softTextShadowStrength
import kotlin.math.pow

/** Keep the chosen text color; protect the difficult parts of its background with a soft shadow. */
internal fun homeTextShadowStrength(
    luminances: FloatArray,
    textLuminance: Float,
    textAlpha: Float = 1f,
    currentStrength: Float = 0f
): Float = softTextShadowStrength(luminances, textLuminance, textAlpha, currentStrength)

/** The black wallpaper overlay multiplies sRGB channels before relative luminance is computed. */
internal fun visibleWallpaperLuminance(argb: Int, brightness: Float): Float {
    val dim = brightness.coerceIn(0.35f, 1f)
    fun channel(shift: Int): Float {
        val value = ((argb ushr shift) and 0xff) / 255f * dim
        return if (value <= 0.04045f) value / 12.92f else ((value + 0.055f) / 1.055f).pow(2.4f)
    }
    return channel(16) * 0.2126f + channel(8) * 0.7152f + channel(0) * 0.0722f
}
