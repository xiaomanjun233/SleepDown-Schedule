package com.xiaomanjun.sleepdownschedule.feature.widget

import com.xiaomanjun.sleepdownschedule.feature.widget.providers.*
import com.xiaomanjun.sleepdownschedule.core.wallpaper.*

import com.xiaomanjun.sleepdownschedule.*

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
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.util.SizeF
import java.util.LinkedHashMap
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class WidgetRenderSize(val widthDp: Int, val heightDp: Int)

internal fun normalizedWidgetRenderSize(widthDp: Int, heightDp: Int): WidgetRenderSize =
    WidgetRenderSize(
        widthDp = widthDp.coerceAtLeast(80),
        heightDp = heightDp.coerceAtLeast(80)
    )

data class WidgetBackgroundResult(
    val bitmap: Bitmap,
    val header: Int,
    val headerSecondary: Int,
    val content: List<Int>,
    val contentSecondary: List<Int>,
    val accent: Int,
    val darkBackground: Boolean
)

internal data class TodayTomorrowWidgetLayoutMetrics(
    val horizontalPaddingDp: Int,
    val verticalPaddingDp: Int,
    val headerHeightDp: Int,
    val dayHeaderHeightDp: Int,
    val headerGapDp: Int,
    val columnGapDp: Int,
    val rowGapDp: Int,
    val rowHeightDp: Int,
    val maxCoursesPerDay: Int,
    val rowCornerRadiusDp: Int,
    val timeColumnWidthDp: Int,
    val textScale: Float
)

internal fun todayTomorrowWidgetLayoutMetrics(
    size: WidgetRenderSize,
    fontScale: Float = 1f
): TodayTomorrowWidgetLayoutMetrics {
    fun progress(value: Int, compact: Int, comfortable: Int): Float =
        ((value - compact).toFloat() / (comfortable - compact).toFloat()).coerceIn(0f, 1f)

    val widthProgress = progress(size.widthDp, 220, 336)
    val heightProgress = progress(size.heightDp, 110, 300)
    val horizontalPadding = (9f + 3f * widthProgress).roundToInt()
    val verticalPadding = (8f + 4f * heightProgress).roundToInt()
    val headerHeight = (22f + 4f * heightProgress).roundToInt()
    val dayHeaderHeight = (20f + 3f * heightProgress).roundToInt()
    val headerGap = 4
    val rowGap = 4
    val availableHeight = (
        size.heightDp - verticalPadding * 2 - headerHeight - dayHeaderHeight - headerGap * 2
    ).coerceAtLeast(30)
    val preferredRowHeight = (43f + 11f * heightProgress).roundToInt()
    val calculatedCapacity = floor(
        (availableHeight + rowGap).toFloat() / (preferredRowHeight + rowGap).toFloat()
    ).toInt()
    val minimumCapacity = if (size.heightDp >= 145) 2 else 1
    val capacity = maxOf(calculatedCapacity, minimumCapacity).coerceIn(1, 6)
    val rowHeight = floor(
        (availableHeight - rowGap * (capacity - 1)).toFloat() / capacity.toFloat()
    ).toInt().coerceIn(30, 64)
    val fontCompensation = (1f / (1f + (fontScale.coerceAtLeast(1f) - 1f) * 0.48f))
        .coerceIn(0.84f, 1f)
    val textScale = minOf(
        0.90f + 0.16f * progress(rowHeight, 30, 54),
        0.96f + 0.10f * widthProgress,
        fontCompensation
    )
    return TodayTomorrowWidgetLayoutMetrics(
        horizontalPaddingDp = horizontalPadding,
        verticalPaddingDp = verticalPadding,
        headerHeightDp = headerHeight,
        dayHeaderHeightDp = dayHeaderHeight,
        headerGapDp = headerGap,
        // Keeps the two MIUIX day panels visually separated at every host width.
        columnGapDp = 9,
        rowGapDp = rowGap,
        rowHeightDp = rowHeight,
        maxCoursesPerDay = capacity,
        rowCornerRadiusDp = (11f + 3f * progress(rowHeight, 30, 54)).roundToInt(),
        timeColumnWidthDp = (30f + 8f * widthProgress).roundToInt(),
        textScale = textScale
    )
}

internal fun widgetRenderSize(manager: AppWidgetManager, id: Int, type: WidgetAppearanceVariant): WidgetRenderSize {
    val options = manager.getAppWidgetOptions(id) ?: Bundle.EMPTY
    val fallback = when (type) {
        WidgetAppearanceVariant.COURSES_SQUARE -> WidgetRenderSize(160, 160)
        WidgetAppearanceVariant.WEEK_SCHEDULE -> WidgetRenderSize(320, 240)
        else -> WidgetRenderSize(320, 160)
    }
    return normalizedWidgetRenderSize(
        options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, fallback.widthDp),
        options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, fallback.heightDp)
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

internal object WidgetBackgroundRenderer {
    private const val MaxCacheEntries = 12
    // RemoteViews transports bitmaps through Binder. Keep a single background comfortably
    // below the transaction ceiling even on high-density launchers.
    private const val MaxBackgroundPixels = 160_000f
    private val cache = object : LinkedHashMap<String, WidgetBackgroundResult>(MaxCacheEntries, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, WidgetBackgroundResult>?): Boolean =
            size > MaxCacheEntries
    }

    fun render(
        context: Context,
        appearance: WidgetAppearanceEntity,
        size: WidgetRenderSize,
        courseCount: Int = 0,
        darkMode: Boolean = false,
        dayCourseCounts: List<Int> = emptyList(),
        coursesMetrics: CoursesWidgetLayoutMetrics? = null,
        todayTomorrowMetrics: TodayTomorrowWidgetLayoutMetrics? = null,
        drawContentSurfaces: Boolean = true,
        pixelLimit: Float = MaxBackgroundPixels
    ): WidgetBackgroundResult? {
        val uri = appearance.wallpaperUri?.takeIf { appearance.enabled } ?: return null
        val cacheKey = listOf(
            uri, appearance.updatedAt, appearance.centerX, appearance.centerY, appearance.scale,
            appearance.blurDp, appearance.brightness, appearance.variant, size.widthDp, size.heightDp,
            courseCount, darkMode, dayCourseCounts.joinToString(","), coursesMetrics,
            todayTomorrowMetrics, drawContentSurfaces, pixelLimit
        ).joinToString("|")
        synchronized(cache) { cache[cacheKey]?.let { return it } }
        val source = loadSampledBitmap(context, uri.toUri(), 1800) ?: return null
        val (width, height) = pixelSize(context, size, pixelLimit)
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
        val result = if (!drawContentSurfaces) {
            renderPlain(base)
        } else {
            when (appearance.type) {
                WidgetAppearanceVariant.COURSES_LARGE,
                WidgetAppearanceVariant.COURSES_SQUARE -> renderCourses(
                    base,
                    size,
                    courseCount,
                    coursesMetrics ?: coursesWidgetLayoutMetrics(
                        size,
                        if (appearance.type == WidgetAppearanceVariant.COURSES_SQUARE) {
                            TodayWidgetVariant.SQUARE
                        } else {
                            TodayWidgetVariant.LARGE
                        },
                        courseCount
                    )
                )
                WidgetAppearanceVariant.TODAY_TOMORROW -> renderTodayTomorrow(
                    base,
                    size,
                    dayCourseCounts,
                    todayTomorrowMetrics ?: todayTomorrowWidgetLayoutMetrics(size)
                )
                WidgetAppearanceVariant.WEEK_SCHEDULE,
                WidgetAppearanceVariant.TODAY_ASSISTANT -> renderPlain(base)
            }
        }
        result.bitmap.setHasAlpha(false)
        base.recycle()
        if (blurred !== crop) blurred.recycle()
        crop.recycle()
        source.recycle()
        synchronized(cache) { cache[cacheKey] = result }
        return result
    }

    internal fun pixelSize(
        context: Context,
        size: WidgetRenderSize,
        pixelLimit: Float = MaxBackgroundPixels
    ): Pair<Int, Int> {
        val density = context.resources.displayMetrics.density.coerceAtLeast(1f)
        var width = (size.widthDp * density).roundToInt().coerceAtLeast(1)
        var height = (size.heightDp * density).roundToInt().coerceAtLeast(1)
        val pixels = width.toFloat() * height
        if (pixels > pixelLimit) {
            val factor = sqrt(pixelLimit / pixels)
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

    private fun renderCourses(
        base: Bitmap,
        size: WidgetRenderSize,
        count: Int,
        metrics: CoursesWidgetLayoutMetrics
    ): WidgetBackgroundResult {
        val output = base.copy(Bitmap.Config.ARGB_8888, true)
        val extraBlur = createBlurredWallpaperBitmap(base, 10) ?: base
        val sx = output.width / size.widthDp.toFloat()
        val sy = output.height / size.heightDp.toFloat()
        fun rect(l: Float, t: Float, r: Float, b: Float) = RectF(l * sx, t * sy, r * sx, b * sy)
        val left = metrics.horizontalPaddingDp.toFloat()
        val right = size.widthDp - metrics.horizontalPaddingDp.toFloat()
        val top = (
            metrics.verticalPaddingDp + metrics.headerHeightDp + metrics.courseTopMarginDp
        ).toFloat()
        val regions = when {
            count <= 0 -> emptyList()
            metrics.useGrid -> {
                val columnGap = 4f
                val cellWidth = (right - left - columnGap) / 2f
                List(count.coerceAtMost(metrics.maxCourses)) { index ->
                    val row = index / 2
                    val column = index % 2
                    val cellLeft = if (column == 0) left else left + cellWidth + columnGap
                    val cellTop = top + row * (metrics.groupHeightDp + metrics.groupGapDp)
                    rect(cellLeft, cellTop, cellLeft + cellWidth, cellTop + metrics.groupHeightDp)
                }
            }
            else -> List(count.coerceAtMost(metrics.maxCourses)) { index ->
                val rowTop = top + index * (metrics.groupHeightDp + metrics.groupGapDp)
                rect(left, rowTop, right, rowTop + metrics.groupHeightDp)
            }
        }
        val primary = mutableListOf<Int>()
        val secondary = mutableListOf<Int>()
        val canvas = Canvas(output)
        val darkBackground = luminance(
            base,
            RectF(0f, 0f, base.width.toFloat(), base.height.toFloat())
        ) < 0.48
        regions.forEach { region ->
            val path = Path().apply {
                addRoundRect(
                    region,
                    metrics.groupCornerRadiusDp * sx,
                    metrics.groupCornerRadiusDp * sy,
                    Path.Direction.CW
                )
            }
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
            drawPresetGlassHighlight(canvas, path, region, sx, sy, darkBackground)
            primary += if (darkBackground) Color.WHITE else Color.rgb(17, 17, 17)
            secondary += if (darkBackground) {
                Color.argb(190, 255, 255, 255)
            } else {
                Color.argb(170, 0, 0, 0)
            }
        }
        if (extraBlur !== base) extraBlur.recycle()
        return WidgetBackgroundResult(
            output,
            if (darkBackground) Color.WHITE else Color.rgb(17, 17, 17),
            if (darkBackground) Color.argb(200, 255, 255, 255) else Color.argb(170, 0, 0, 0),
            primary, secondary,
            if (darkBackground) Color.rgb(98, 181, 255) else Color.rgb(0, 110, 220),
            darkBackground
        )
    }

    private fun renderTodayTomorrow(
        base: Bitmap,
        size: WidgetRenderSize,
        dayCourseCounts: List<Int>,
        metrics: TodayTomorrowWidgetLayoutMetrics
    ): WidgetBackgroundResult {
        val output = base.copy(Bitmap.Config.ARGB_8888, true)
        val extraBlur = createBlurredWallpaperBitmap(base, 10) ?: base
        val sx = output.width / size.widthDp.toFloat()
        val sy = output.height / size.heightDp.toFloat()
        val contentTopDp = (
            metrics.verticalPaddingDp + metrics.headerHeightDp + metrics.headerGapDp +
                metrics.dayHeaderHeightDp + metrics.headerGapDp
        ).toFloat()
        val contentWidthDp = size.widthDp - metrics.horizontalPaddingDp * 2f - metrics.columnGapDp
        val columnWidthDp = contentWidthDp / 2f
        val counts = List(2) { dayCourseCounts.getOrNull(it).orZero() }
        val regions = buildList {
            repeat(2) { dayIndex ->
                val columnLeftDp = metrics.horizontalPaddingDp +
                    dayIndex * (columnWidthDp + metrics.columnGapDp)
                repeat(counts[dayIndex].coerceAtMost(metrics.maxCoursesPerDay)) { rowIndex ->
                    val rowTopDp = contentTopDp +
                        rowIndex * (metrics.rowHeightDp + metrics.rowGapDp)
                    add(
                        RectF(
                            columnLeftDp * sx,
                            rowTopDp * sy,
                            (columnLeftDp + columnWidthDp) * sx,
                            (rowTopDp + metrics.rowHeightDp) * sy
                        )
                    )
                }
            }
        }
        val darkBackground = luminance(
            base,
            RectF(0f, 0f, base.width.toFloat(), base.height.toFloat())
        ) < 0.48
        val primaryColor = if (darkBackground) Color.WHITE else Color.rgb(17, 17, 17)
        val secondaryColor = if (darkBackground) {
            Color.argb(190, 255, 255, 255)
        } else {
            Color.argb(170, 0, 0, 0)
        }
        val canvas = Canvas(output)
        regions.forEach { region ->
            val path = Path().apply {
                addRoundRect(
                    region,
                    metrics.rowCornerRadiusDp * sx,
                    metrics.rowCornerRadiusDp * sy,
                    Path.Direction.CW
                )
            }
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
            drawPresetGlassHighlight(canvas, path, region, sx, sy, darkBackground)
        }
        if (extraBlur !== base) extraBlur.recycle()
        return WidgetBackgroundResult(
            bitmap = output,
            header = primaryColor,
            headerSecondary = if (darkBackground) {
                Color.argb(200, 255, 255, 255)
            } else {
                Color.argb(170, 0, 0, 0)
            },
            content = List(regions.size) { primaryColor },
            contentSecondary = List(regions.size) { secondaryColor },
            accent = if (darkBackground) Color.rgb(98, 181, 255) else Color.rgb(0, 110, 220),
            darkBackground = darkBackground
        )
    }

    private fun Int?.orZero(): Int = this ?: 0

    internal fun drawPresetGlassHighlight(
        canvas: Canvas,
        path: Path,
        region: RectF,
        sx: Float,
        sy: Float,
        darkBackground: Boolean
    ) {
        val strokeWidth = (1.1f * minOf(sx, sy)).coerceAtLeast(1f)
        val outlineAlpha = if (darkBackground) 62 else 46
        val outlinePaint = filteredPaint().apply {
            style = Paint.Style.STROKE
            this.strokeWidth = strokeWidth
            shader = LinearGradient(
                region.centerX(),
                region.top,
                region.centerX(),
                region.bottom,
                intArrayOf(
                    Color.argb(outlineAlpha, 255, 255, 255),
                    Color.argb(outlineAlpha / 3, 255, 255, 255),
                    Color.argb(outlineAlpha / 2, 255, 255, 255)
                ),
                floatArrayOf(0f, 0.52f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawPath(path, outlinePaint)

        fun drawCenteredEdgeHighlight(top: Boolean, alpha: Int) {
            val bandHeight = region.height() * 0.40f
            val saveCount = canvas.save()
            if (top) {
                canvas.clipRect(region.left, region.top - strokeWidth, region.right, region.top + bandHeight)
            } else {
                canvas.clipRect(region.left, region.bottom - bandHeight, region.right, region.bottom + strokeWidth)
            }
            val edgePaint = filteredPaint().apply {
                style = Paint.Style.STROKE
                this.strokeWidth = strokeWidth * 1.35f
                shader = LinearGradient(
                    region.left,
                    region.centerY(),
                    region.right,
                    region.centerY(),
                    intArrayOf(
                        Color.TRANSPARENT,
                        Color.argb(alpha, 255, 255, 255),
                        Color.TRANSPARENT
                    ),
                    floatArrayOf(0f, 0.5f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawPath(path, edgePaint)
            canvas.restoreToCount(saveCount)
        }

        drawCenteredEdgeHighlight(top = true, alpha = if (darkBackground) 150 else 126)
        drawCenteredEdgeHighlight(top = false, alpha = if (darkBackground) 92 else 72)
    }

    private fun renderPlain(base: Bitmap): WidgetBackgroundResult {
        val output = base.copy(Bitmap.Config.ARGB_8888, true)
        val darkBackground = luminance(
            base,
            RectF(0f, 0f, base.width.toFloat(), base.height.toFloat())
        ) < 0.48
        val primary = if (darkBackground) Color.WHITE else Color.rgb(17, 17, 17)
        val secondary = if (darkBackground) Color.argb(195, 255, 255, 255) else Color.argb(170, 0, 0, 0)
        return WidgetBackgroundResult(
            output,
            primary,
            secondary,
            listOf(primary),
            listOf(secondary),
            if (darkBackground) Color.rgb(98, 181, 255) else Color.rgb(0, 110, 220),
            darkBackground
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
