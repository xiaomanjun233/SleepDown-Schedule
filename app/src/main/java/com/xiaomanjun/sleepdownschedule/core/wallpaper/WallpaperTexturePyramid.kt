package com.xiaomanjun.sleepdownschedule.core.wallpaper

import android.graphics.Bitmap
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.math.sqrt

/**
 * Immutable source pixels plus progressively softer mip levels. Built off the UI thread
 * once per bitmap; crop, brightness, material and card motion never rebuild these pixels.
 * The original stays at full resolution for zero-blur/refraction fidelity.
 */
internal class WallpaperTexturePyramid(
    val source: Bitmap,
    val levels: List<Bitmap>,
    val sigma: FloatArray,
) {
    fun levelPair(sigmaInSourcePixels: Float): TextureLevelPair {
        val requested = sigmaInSourcePixels.coerceAtLeast(0f)
        val upper = sigma.indexOfFirst { it >= requested }
        if (upper <= 0) return if (upper == 0) TextureLevelPair(0, 0, 0f)
            else TextureLevelPair(levels.lastIndex, levels.lastIndex, 0f)
        val lower = upper - 1
        val lowVariance = sigma[lower] * sigma[lower]
        val highVariance = sigma[upper] * sigma[upper]
        return TextureLevelPair(lower, upper,
            ((requested * requested - lowVariance) / (highVariance - lowVariance)).coerceIn(0f, 1f))
    }
}

internal data class TextureLevelPair(val first: Int, val second: Int, val mix: Float)

internal suspend fun buildWallpaperTexturePyramid(source: Bitmap): WallpaperTexturePyramid {
    val levels = mutableListOf(source)
    val sigmas = mutableListOf(0f)
    var variance = 0f
    while (levels.size < 7) {
        currentCoroutineContext().ensureActive()
        val previous = levels.last()
        if (previous.width == 1 && previous.height == 1) break
        val width = (previous.width / 2).coerceAtLeast(1)
        val height = (previous.height / 2).coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(previous, width, height, true)
        val next = checkNotNull(scaled.copy(Bitmap.Config.ARGB_8888, true))
        // Only the new, unpublished level is mutable. Never recycle the source or pixels
        // that a retained display list may still reference.
        blurWallpaperBitmapInPlace(next, 1)
        val priorScale = source.width.toFloat() / previous.width
        val scale = source.width.toFloat() / width
        // Bilinear half-size reduction plus three radius-one box passes.
        variance += 0.25f * priorScale * priorScale + 2f * scale * scale
        levels += next
        sigmas += sqrt(variance)
        next.prepareToDraw()
    }
    return WallpaperTexturePyramid(source, levels, sigmas.toFloatArray())
}
