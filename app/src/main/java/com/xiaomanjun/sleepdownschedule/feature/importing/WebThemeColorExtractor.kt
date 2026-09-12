package com.xiaomanjun.sleepdownschedule.feature.importing

import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Reusable palette accumulator for the small, multi-row WebView header sample. */
internal class WebThemeColorExtractor {
    private val weights = DoubleArray(512)
    private val reds = DoubleArray(512)
    private val greens = DoubleArray(512)
    private val blues = DoubleArray(512)

    fun extract(pixels: IntArray): Int? {
        weights.fill(0.0)
        reds.fill(0.0)
        greens.fill(0.0)
        blues.fill(0.0)
        for (pixel in pixels) {
            val alpha = (pixel ushr 24) / 255.0
            if (alpha == 0.0) continue
            val red = (pixel ushr 16) and 255
            val green = (pixel ushr 8) and 255
            val blue = pixel and 255
            val bin = ((red shr 5) shl 6) or ((green shr 5) shl 3) or (blue shr 5)
            weights[bin] += alpha
            reds[bin] += linearChannels[red] * alpha
            greens[bin] += linearChannels[green] * alpha
            blues[bin] += linearChannels[blue] * alpha
        }
        var weight = 0.0
        var red = 0.0
        var green = 0.0
        var blue = 0.0
        for (bin in weights.indices) {
            // Area^1.5 suppresses sparse text/lines without discarding equally large colors.
            val areaBoost = sqrt(weights[bin])
            weight += weights[bin] * areaBoost
            red += reds[bin] * areaBoost
            green += greens[bin] * areaBoost
            blue += blues[bin] * areaBoost
        }
        if (weight == 0.0) return null
        return (255 shl 24) or (toSrgb(red / weight) shl 16) or
            (toSrgb(green / weight) shl 8) or toSrgb(blue / weight)
    }

    private companion object {
        val linearChannels = DoubleArray(256) {
            val channel = it / 255.0
            if (channel <= 0.04045) channel / 12.92 else ((channel + 0.055) / 1.055).pow(2.4)
        }

        fun toSrgb(linear: Double): Int {
            val value = if (linear <= 0.0031308) linear * 12.92 else 1.055 * linear.pow(1.0 / 2.4) - 0.055
            return (value * 255).roundToInt().coerceIn(0, 255)
        }
    }
}
