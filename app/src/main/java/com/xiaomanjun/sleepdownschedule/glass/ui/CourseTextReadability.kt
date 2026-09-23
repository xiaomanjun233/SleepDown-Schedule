package com.xiaomanjun.sleepdownschedule.glass.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import com.xiaomanjun.sleepdownschedule.core.ui.text.CourseTextBackground
import com.xiaomanjun.sleepdownschedule.core.ui.text.LocalCourseTextMotionFrozen
import com.xiaomanjun.sleepdownschedule.feature.home.LocalHomeBackgroundFrozen
import com.xiaomanjun.sleepdownschedule.feature.home.LocalHomeTextContrastFrozen
import com.xiaomanjun.sleepdownschedule.feature.home.day.LocalHomeReadability
import com.xiaomanjun.sleepdownschedule.feature.home.day.sampleVisibleWallpaperColors

/** Estimate the tinted, blurred surface from the cached wallpaper; never read back the GPU. */
@Composable
internal fun rememberCourseTextBackground(
    base: Color,
    tintAlpha: Float,
    blurred: Boolean,
    blurPx: Float,
    outline: Boolean,
    expanded: Boolean,
    ready: Boolean,
    cardBounds: () -> Rect?
): CourseTextBackground {
    val wallpaper = LocalHomeReadability.current
    val frozen = LocalHomeBackgroundFrozen.current ||
        LocalCourseTextMotionFrozen.current || LocalHomeTextContrastFrozen.current
    return remember(wallpaper, frozen, base, tintAlpha, blurred, blurPx, outline, expanded, ready) {
        CourseTextBackground(frozen) { windowBounds ->
            if (!ready) return@CourseTextBackground null
            val bounds = windowBounds.translate(-wallpaper.rootOffsetInWindow)
            val colors = sampleVisibleWallpaperColors(wallpaper,
                if (blurred) bounds.inflate(blurPx * 0.5f) else bounds)
                ?: return@CourseTextBackground if (tintAlpha >= 0.85f) floatArrayOf(base.luminance()) else null
            val mean = Color(colors.map { it.red }.average().toFloat(),
                colors.map { it.green }.average().toFloat(), colors.map { it.blue }.average().toFloat())
            val card = cardBounds()
            val fraction = if (card != null && card.height > 0f)
                ((windowBounds.center.y - card.top) / card.height).coerceIn(0f, 1f) else 0.5f
            val tintWeight = if (outline) {
                val stops = if (expanded) listOf(0f to 0.2f, 0.48f to 0.2f, 0.68f to 0.48f,
                    0.82f to 0.84f, 0.92f to 0.88f, 1f to 1f)
                else listOf(0f to 0.2f, 0.62f to 0.2f, 0.82f to 0.48f,
                    0.90f to 0.84f, 0.96f to 0.88f, 1f to 1f)
                val end = stops.indexOfFirst { it.first >= fraction }.coerceAtLeast(1)
                val (a, av) = stops[end - 1]
                val (b, bv) = stops[end]
                av + (bv - av) * ((fraction - a) / (b - a))
            } else 1f
            val tint = base.copy(alpha = (tintAlpha * tintWeight).coerceIn(0f, 1f))
            colors.map { source ->
                val softened = if (blurred) Color(
                    source.red * 0.3f + mean.red * 0.7f,
                    source.green * 0.3f + mean.green * 0.7f,
                    source.blue * 0.3f + mean.blue * 0.7f
                ) else source
                tint.compositeOver(softened).luminance()
            }.toFloatArray()
        }
    }
}
