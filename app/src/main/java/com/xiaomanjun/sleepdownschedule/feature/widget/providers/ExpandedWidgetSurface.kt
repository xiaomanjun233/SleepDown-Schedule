package com.xiaomanjun.sleepdownschedule.feature.widget.providers

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.graphics.withClip
import com.xiaomanjun.sleepdownschedule.AppState
import com.xiaomanjun.sleepdownschedule.core.wallpaper.createBlurredWallpaperBitmap
import com.xiaomanjun.sleepdownschedule.feature.home.day.hasAnyWallpaper
import com.xiaomanjun.sleepdownschedule.feature.widget.WidgetAppearanceEntity
import com.xiaomanjun.sleepdownschedule.feature.widget.WidgetBackgroundRenderer
import com.xiaomanjun.sleepdownschedule.feature.widget.WidgetBackgroundResult
import com.xiaomanjun.sleepdownschedule.feature.widget.WidgetRenderSize
import com.xiaomanjun.sleepdownschedule.glass.ui.courseGlassTintAlpha
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt
import kotlin.math.sqrt

private const val ExpandedWidgetMaxPixels = 384_000f
private const val ExpandedWidgetPreviewMaxPixels = 240_000f
internal val WidgetTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
internal val WidgetShortDateFormatter = DateTimeFormatter.ofPattern("M.d")

internal fun boundedWidgetBitmapSize(
    requestedWidth: Int,
    requestedHeight: Int,
    maxPixels: Float
): Pair<Int, Int> {
    var width = requestedWidth.coerceAtLeast(1)
    var height = requestedHeight.coerceAtLeast(1)
    val pixels = width.toDouble() * height.toDouble()
    if (pixels > maxPixels) {
        val factor = sqrt(maxPixels / pixels).toFloat()
        width = (width * factor).roundToInt().coerceAtLeast(1)
        height = (height * factor).roundToInt().coerceAtLeast(1)
    }
    return width to height
}

private fun expandedWidgetPixelSize(
    context: Context,
    size: WidgetRenderSize,
    transparentBackground: Boolean
): Pair<Int, Int> {
    val density = context.resources.displayMetrics.density.coerceAtLeast(1f)
    return boundedWidgetBitmapSize(
        requestedWidth = (size.widthDp * density).roundToInt(),
        requestedHeight = (size.heightDp * density).roundToInt(),
        maxPixels = if (transparentBackground) {
            ExpandedWidgetPreviewMaxPixels
        } else {
            ExpandedWidgetMaxPixels
        }
    )
}

internal data class ExpandedWidgetSurface(
    val output: Bitmap,
    val sample: Bitmap,
    val custom: WidgetBackgroundResult?,
    val dark: Boolean,
    val primary: Int,
    val secondary: Int,
    val accent: Int
)

internal fun createExpandedWidgetSurface(
    context: Context,
    appearance: WidgetAppearanceEntity,
    size: WidgetRenderSize,
    appDark: Boolean,
    transparentBackground: Boolean
): ExpandedWidgetSurface {
    val custom = WidgetBackgroundRenderer.render(
        context = context,
        appearance = appearance,
        size = size,
        darkMode = appDark,
        drawContentSurfaces = false,
        pixelLimit = if (transparentBackground) {
            ExpandedWidgetPreviewMaxPixels
        } else {
            ExpandedWidgetMaxPixels
        }
    )
    val dark = custom?.darkBackground ?: appDark
    val (width, height) = expandedWidgetPixelSize(context, size, transparentBackground)
    val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val sample = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val destination = RectF(0f, 0f, width.toFloat(), height.toFloat())
    val bitmapPaint = Paint(
        Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG
    )

    fun drawBase(canvas: Canvas) {
        if (custom != null) {
            canvas.drawBitmap(custom.bitmap, null, destination, bitmapPaint)
        } else {
            drawDefaultMiuixWidgetBackground(canvas, width, height, dark)
        }
    }

    drawBase(Canvas(sample))
    if (transparentBackground) {
        output.eraseColor(Color.TRANSPARENT)
    } else {
        drawBase(Canvas(output))
    }
    return ExpandedWidgetSurface(
        output = output,
        sample = sample,
        custom = custom,
        dark = dark,
        primary = custom?.header ?: if (dark) Color.WHITE else Color.rgb(25, 26, 31),
        secondary = custom?.headerSecondary ?: if (dark) {
            Color.argb(178, 255, 255, 255)
        } else {
            Color.argb(148, 25, 26, 31)
        },
        accent = custom?.accent ?: if (dark) Color.rgb(73, 158, 255) else Color.rgb(0, 112, 226)
    )
}

private fun drawDefaultMiuixWidgetBackground(
    canvas: Canvas,
    width: Int,
    height: Int,
    dark: Boolean
) {
    val base = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply {
        shader = LinearGradient(
            0f,
            0f,
            width.toFloat(),
            height.toFloat(),
            if (dark) {
                intArrayOf(Color.rgb(33, 34, 40), Color.rgb(22, 23, 28), Color.rgb(16, 17, 21))
            } else {
                intArrayOf(Color.rgb(252, 252, 255), Color.rgb(243, 246, 252), Color.rgb(235, 239, 248))
            },
            floatArrayOf(0f, 0.54f, 1f),
            Shader.TileMode.CLAMP
        )
    }
    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), base)

    val radius = maxOf(width, height) * 0.78f
    val blueGlow = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply {
        shader = RadialGradient(
            width * 0.12f,
            height * 0.04f,
            radius,
            if (dark) Color.argb(48, 34, 126, 255) else Color.argb(56, 116, 181, 255),
            Color.TRANSPARENT,
            Shader.TileMode.CLAMP
        )
    }
    canvas.drawCircle(width * 0.12f, height * 0.04f, radius, blueGlow)
    val violetGlow = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply {
        shader = RadialGradient(
            width * 0.92f,
            height * 0.96f,
            radius * 0.72f,
            if (dark) Color.argb(35, 126, 78, 255) else Color.argb(38, 173, 143, 255),
            Color.TRANSPARENT,
            Shader.TileMode.CLAMP
        )
    }
    canvas.drawCircle(width * 0.92f, height * 0.96f, radius * 0.72f, violetGlow)
}

internal fun createCardBlur(surface: ExpandedWidgetSurface, state: AppState): Bitmap? {
    if (!state.config.courseCardGlassEnabled) return null
    val radius = state.config.courseCardBlur.roundToInt().coerceIn(0, 24)
    return if (radius > 0) {
        createBlurredWallpaperBitmap(surface.sample, radius) ?: surface.sample
    } else {
        surface.sample
    }
}

internal fun recycleSurface(surface: ExpandedWidgetSurface, blurred: Bitmap?) {
    if (blurred != null && blurred !== surface.sample) blurred.recycle()
    surface.sample.recycle()
}

internal fun finishExpandedWidgetBitmap(
    surface: ExpandedWidgetSurface,
    transparentBackground: Boolean
): Bitmap {
    if (transparentBackground) {
        surface.output.setHasAlpha(true)
        return surface.output
    }
    val compact = surface.output.copy(Bitmap.Config.RGB_565, false)
    surface.output.recycle()
    compact.setHasAlpha(false)
    return compact
}

internal fun withAlpha(color: Int, alpha: Int): Int = Color.argb(
    alpha.coerceIn(0, 255),
    Color.red(color),
    Color.green(color),
    Color.blue(color)
)

internal fun centeredTextBaseline(rect: RectF, paint: Paint): Float =
    rect.centerY() - (paint.fontMetrics.ascent + paint.fontMetrics.descent) / 2f

internal fun continuousRoundedRectPath(rect: RectF, radius: Float): Path {
    return com.xiaomanjun.sleepdownschedule.core.ui.designsystem.continuousRoundedRectPath(
        rect, radius, radius
    )
}

internal fun ellipsizedWidgetText(text: String, paint: TextPaint, width: Float): String =
    TextUtils.ellipsize(
        text,
        paint,
        width.coerceAtLeast(1f),
        TextUtils.TruncateAt.END
    ).toString()

internal fun drawNeutralGlassSurface(
    canvas: Canvas,
    blurred: Bitmap?,
    rect: RectF,
    radius: Float,
    sx: Float,
    sy: Float,
    dark: Boolean,
    strong: Boolean = false,
    showOutline: Boolean = true,
) {
    val path = continuousRoundedRectPath(rect, radius)
    canvas.withClip(path) {
        blurred?.let { drawBitmap(it, 0f, 0f, null) }
        drawColor(
            if (dark) {
                Color.argb(if (strong) 92 else 62, 7, 8, 12)
            } else {
                Color.argb(if (strong) 176 else 130, 255, 255, 255)
            }
        )
    }
    if (showOutline) {
        WidgetBackgroundRenderer.drawPresetGlassHighlight(canvas, path, rect, sx, sy, dark)
    }
}

internal fun drawCourseGlassSurface(
    canvas: Canvas,
    blurred: Bitmap?,
    rect: RectF,
    radius: Float,
    sx: Float,
    sy: Float,
    dark: Boolean,
    state: AppState,
    courseColor: Int,
    showOutline: Boolean = true,
    forceOpaque: Boolean = false
) {
    val path = continuousRoundedRectPath(rect, radius)
    if (forceOpaque) {
        canvas.drawPath(
            path,
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply { color = courseColor }
        )
    } else if (state.config.courseCardGlassEnabled) {
        canvas.withClip(path) {
            blurred?.let { drawBitmap(it, 0f, 0f, null) }
            val tintAlpha = (courseGlassTintAlpha(
                cardAlpha = state.config.cardAlpha,
                quality = 1f,
                hasWallpaper = state.config.hasAnyWallpaper()
            ) * 255f).roundToInt().coerceIn(0, 255)
            drawColor(withAlpha(courseColor, tintAlpha))
        }
    } else {
        canvas.drawPath(
            path,
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply {
                color = withAlpha(
                    courseColor,
                    (state.config.cardAlpha.coerceIn(0.86f, 1f) * 255f).roundToInt()
                )
            }
        )
    }
    if (showOutline) {
        WidgetBackgroundRenderer.drawPresetGlassHighlight(canvas, path, rect, sx, sy, dark)
    }
}

internal fun shortChineseWeekday(date: LocalDate): String = when (date.dayOfWeek.value) {
    1 -> "周一"
    2 -> "周二"
    3 -> "周三"
    4 -> "周四"
    5 -> "周五"
    6 -> "周六"
    else -> "周日"
}
