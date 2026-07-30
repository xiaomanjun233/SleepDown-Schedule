package com.example.courseschedule

import android.appwidget.AppWidgetManager
import android.content.Context
import android.graphics.Bitmap
import androidx.core.graphics.createBitmap
import androidx.core.graphics.get
import androidx.core.graphics.withClip
import androidx.core.net.toUri
import androidx.core.os.BundleCompat
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.util.SizeF
import java.util.LinkedHashMap
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class WidgetRenderSize(val widthDp: Int, val heightDp: Int)

data class WidgetBackgroundResult(
    val bitmap: Bitmap,
    val header: Int,
    val headerSecondary: Int,
    val content: List<Int>,
    val contentSecondary: List<Int>,
    val accent: Int
)

internal fun widgetRenderSize(manager: AppWidgetManager, id: Int, type: WidgetAppearanceVariant): WidgetRenderSize {
    val options = manager.getAppWidgetOptions(id) ?: Bundle.EMPTY
    val fallback = if (type == WidgetAppearanceVariant.COURSES_SQUARE) WidgetRenderSize(160, 160) else WidgetRenderSize(320, 160)
    return WidgetRenderSize(
        options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, fallback.widthDp).coerceAtLeast(110),
        options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, fallback.heightDp).coerceAtLeast(110)
    )
}

internal fun widgetRenderSizes(manager: AppWidgetManager, id: Int, type: WidgetAppearanceVariant): List<WidgetRenderSize> {
    val fallback = widgetRenderSize(manager, id, type)
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return listOf(fallback)
    val options = manager.getAppWidgetOptions(id) ?: return listOf(fallback)
    val sizes = BundleCompat.getParcelableArrayList(
        options,
        AppWidgetManager.OPTION_APPWIDGET_SIZES,
        SizeF::class.java
    )
        .orEmpty()
        .map { WidgetRenderSize(it.width.roundToInt(), it.height.roundToInt()) }
        .filter { it.widthDp >= 80 && it.heightDp >= 80 }
        .distinct()
    return sizes.ifEmpty { listOf(fallback) }
}

object WidgetBackgroundRenderer {
    private const val MaxCacheEntries = 8
    private val cache = object : LinkedHashMap<String, WidgetBackgroundResult>(MaxCacheEntries, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, WidgetBackgroundResult>?): Boolean =
            size > MaxCacheEntries
    }

    fun render(
        context: Context,
        appearance: WidgetAppearanceEntity,
        size: WidgetRenderSize,
        courseCount: Int = 0,
        darkMode: Boolean = false
    ): WidgetBackgroundResult? {
        val uri = appearance.wallpaperUri?.takeIf { appearance.enabled } ?: return null
        val cacheKey = listOf(
            uri, appearance.updatedAt, appearance.centerX, appearance.centerY, appearance.scale,
            appearance.blurDp, appearance.brightness, appearance.variant, size.widthDp, size.heightDp, courseCount, darkMode
        ).joinToString("|")
        synchronized(cache) { cache[cacheKey]?.let { return it } }
        val source = loadSampledBitmap(context, uri.toUri(), 1800) ?: return null
        val (width, height) = cappedPixels(context, size)
        val crop = createBitmap(width, height)
        val destination = calculateFocusCropRect(
            source.width, source.height, width.toFloat(), height.toFloat(),
            WallpaperCropState(appearance.centerX, appearance.centerY, appearance.scale)
        )
        Canvas(crop).drawBitmap(source, null, RectF(destination.left, destination.top, destination.right, destination.bottom), filteredPaint())
        val blurred = if (appearance.blurDp > 0.1f) {
            createBlurredWallpaperBitmap(crop, appearance.blurDp.roundToInt().coerceAtLeast(1)) ?: crop
        } else crop
        val base = applyBrightness(blurred, appearance.brightness)
        val result = when (appearance.type) {
            WidgetAppearanceVariant.COURSES_LARGE,
            WidgetAppearanceVariant.COURSES_SQUARE -> renderCourses(base, appearance.type, size, courseCount)
            WidgetAppearanceVariant.TODAY_ASSISTANT -> renderAssistant(base)
        }
        result.bitmap.setHasAlpha(false)
        synchronized(cache) { cache[cacheKey] = result }
        return result
    }

    private fun cappedPixels(context: Context, size: WidgetRenderSize): Pair<Int, Int> {
        val density = context.resources.displayMetrics.density.coerceAtLeast(1f)
        var width = (size.widthDp * density).roundToInt().coerceAtLeast(1)
        var height = (size.heightDp * density).roundToInt().coerceAtLeast(1)
        val pixels = width.toFloat() * height
        if (pixels > 340_000f) {
            val factor = sqrt(340_000f / pixels)
            width = (width * factor).roundToInt().coerceAtLeast(1)
            height = (height * factor).roundToInt().coerceAtLeast(1)
        }
        return width to height
    }

    private fun applyBrightness(source: Bitmap, brightness: Float): Bitmap {
        val output = createBitmap(source.width, source.height)
        val matrix = ColorMatrix().apply { setScale(brightness, brightness, brightness, 1f) }
        Canvas(output).drawBitmap(source, 0f, 0f, filteredPaint().apply { colorFilter = ColorMatrixColorFilter(matrix) })
        return output
    }

    private fun renderCourses(base: Bitmap, type: WidgetAppearanceVariant, size: WidgetRenderSize, count: Int): WidgetBackgroundResult {
        val output = base.copy(Bitmap.Config.ARGB_8888, true)
        val extraBlur = createBlurredWallpaperBitmap(base, 10) ?: base
        val sx = output.width / size.widthDp.toFloat()
        val sy = output.height / size.heightDp.toFloat()
        fun rect(l: Float, t: Float, r: Float, b: Float) = RectF(l * sx, t * sy, r * sx, b * sy)
        val padding = if (type == WidgetAppearanceVariant.COURSES_SQUARE) 11f else 14f
        // The square XML starts its first weighted row after 11dp top padding,
        // an ~18dp title line and a 5dp row margin. Keep the baked glass region
        // aligned with that real row instead of reusing the taller 4x2 header.
        val headerBottom = if (type == WidgetAppearanceVariant.COURSES_SQUARE) 28f else 42f
        val top = headerBottom + 6f
        val bottom = size.heightDp - padding
        val left = padding
        val right = size.widthDp - padding
        val regions = when {
            count <= 0 -> emptyList()
            count == 1 -> twoRows(
                ::rect,
                left,
                right,
                top,
                bottom,
                if (type == WidgetAppearanceVariant.COURSES_SQUARE) 5f else 6f
            ).take(1)
            type == WidgetAppearanceVariant.COURSES_SQUARE -> twoRows(::rect, left, right, top, bottom, 5f)
            count >= 3 -> fourCells(::rect, left, right, top, bottom)
            else -> twoRows(::rect, left, right, top, bottom, 6f)
        }.take(count.coerceAtMost(4))
        val primary = mutableListOf<Int>()
        val secondary = mutableListOf<Int>()
        val canvas = Canvas(output)
        val darkBackground = luminance(
            base,
            RectF(0f, 0f, base.width.toFloat(), base.height.toFloat())
        ) < 0.48
        regions.forEach { region ->
            val path = Path().apply { addRoundRect(region, 14f * sx, 14f * sy, Path.Direction.CW) }
            canvas.withClip(path) {
                drawBitmap(extraBlur, 0f, 0f, null)
                drawColor(
                    if (darkBackground) {
                        Color.argb(66, 0, 0, 0)
                    } else {
                        Color.argb(78, 255, 255, 255)
                    }
                )
            }
            primary += if (darkBackground) Color.WHITE else Color.rgb(17, 17, 17)
            secondary += if (darkBackground) {
                Color.argb(190, 255, 255, 255)
            } else {
                Color.argb(170, 0, 0, 0)
            }
        }
        return WidgetBackgroundResult(
            output,
            if (darkBackground) Color.WHITE else Color.rgb(17, 17, 17),
            if (darkBackground) Color.argb(200, 255, 255, 255) else Color.argb(170, 0, 0, 0),
            primary, secondary,
            if (darkBackground) Color.rgb(98, 181, 255) else Color.rgb(0, 110, 220)
        )
    }

    private fun renderAssistant(base: Bitmap): WidgetBackgroundResult {
        val output = base.copy(Bitmap.Config.ARGB_8888, true)
        val darkBackground = luminance(
            base,
            RectF(0f, 0f, base.width.toFloat(), base.height.toFloat())
        ) < 0.48
        val primary = if (darkBackground) Color.WHITE else Color.rgb(17, 17, 17)
        val secondary = if (darkBackground) Color.argb(195, 255, 255, 255) else Color.argb(170, 0, 0, 0)
        return WidgetBackgroundResult(output, primary, secondary, listOf(primary), listOf(secondary),
            if (darkBackground) Color.rgb(98, 181, 255) else Color.rgb(0, 110, 220))
    }

    private fun twoRows(factory: (Float, Float, Float, Float) -> RectF, l: Float, r: Float, t: Float, b: Float, gap: Float): List<RectF> {
        val height = (b - t - gap) / 2f
        return listOf(factory(l, t, r, t + height), factory(l, t + height + gap, r, b))
    }

    private fun fourCells(factory: (Float, Float, Float, Float) -> RectF, l: Float, r: Float, t: Float, b: Float): List<RectF> {
        val gap = 6f
        val width = (r - l - gap) / 2f
        val height = (b - t - gap) / 2f
        return listOf(
            factory(l, t, l + width, t + height), factory(l + width + gap, t, r, t + height),
            factory(l, t + height + gap, l + width, b), factory(l + width + gap, t + height + gap, r, b)
        )
    }

    private fun luminance(bitmap: Bitmap, region: RectF): Double {
        val left = region.left.roundToInt().coerceIn(0, bitmap.width - 1)
        val top = region.top.roundToInt().coerceIn(0, bitmap.height - 1)
        val right = region.right.roundToInt().coerceIn(left + 1, bitmap.width)
        val bottom = region.bottom.roundToInt().coerceIn(top + 1, bitmap.height)
        var total = 0.0
        var count = 0
        val dx = ((right - left) / 12).coerceAtLeast(1)
        val dy = ((bottom - top) / 8).coerceAtLeast(1)
        for (y in top until bottom step dy) for (x in left until right step dx) {
            total += relativeLuminance(bitmap[x, y]); count++
        }
        return if (count == 0) 0.5 else total / count
    }

    internal fun relativeLuminance(color: Int): Double {
        fun channel(raw: Int): Double {
            val value = raw / 255.0
            return if (value <= 0.04045) value / 12.92 else Math.pow((value + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * channel(Color.red(color)) + 0.7152 * channel(Color.green(color)) + 0.0722 * channel(Color.blue(color))
    }

    internal fun contrastRatio(first: Int, second: Int): Double {
        val a = relativeLuminance(first); val b = relativeLuminance(second)
        return (maxOf(a, b) + 0.05) / (minOf(a, b) + 0.05)
    }

    private fun filteredPaint() = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
}
