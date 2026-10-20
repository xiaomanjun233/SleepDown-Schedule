package com.xiaomanjun.sleepdownschedule.core.wallpaper

import android.graphics.Bitmap
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.unit.IntSize
import androidx.core.graphics.get
import com.xiaomanjun.sleepdownschedule.model.ScheduleConfigEntity
import com.xiaomanjun.sleepdownschedule.model.DefaultWallpaperStyle
import kotlin.math.roundToInt

data class HomeReadabilityContext(
    val bitmap: Bitmap? = null,
    val config: ScheduleConfigEntity? = null,
    val rootSize: IntSize = IntSize.Zero,
    val rootOffsetInWindow: Offset = Offset.Zero
)

val LocalHomeReadability = compositionLocalOf { HomeReadabilityContext() }

internal fun sampleVisibleWallpaperColors(
    context: HomeReadabilityContext,
    bounds: Rect,
    columns: Int = 7,
    rows: Int = 5
): List<ComposeColor>? {
    if (bounds.width <= 0f || bounds.height <= 0f) return null
    val bitmap = context.bitmap ?: return null
    val config = context.config ?: return null
    val root = context.rootSize
    if (root.width <= 0 || root.height <= 0 || bitmap.width <= 0 || bitmap.height <= 0) {
        return null
    }
    val useSavedCrop = !config.wallpaperUri.isNullOrBlank()
    val cropState = if (useSavedCrop) {
        config.wallpaperCropState(
            if (root.width <= root.height) WallpaperPreviewOrientation.Portrait
            else WallpaperPreviewOrientation.Landscape
        )
    } else WallpaperCropState()
    val drawn = calculateFocusCropRect(
        bitmapWidth = bitmap.width,
        bitmapHeight = bitmap.height,
        containerWidth = root.width.toFloat(),
        containerHeight = root.height.toFloat(),
        cropState = cropState
    )
    if (drawn.width <= 0f || drawn.height <= 0f) return null
    val visibleBounds = bounds.intersect(Rect(0f, 0f, root.width.toFloat(), root.height.toFloat()))
    if (visibleBounds.width <= 0f || visibleBounds.height <= 0f) return null
    val samples = ArrayList<ComposeColor>(columns * rows)
    val brightness = config.wallpaperBrightness.coerceIn(0.35f, 1f)
    for (row in 0 until rows) {
        for (column in 0 until columns) {
            val rootX = visibleBounds.left + visibleBounds.width * ((column + 0.5f) / columns)
            val rootY = visibleBounds.top + visibleBounds.height * ((row + 0.5f) / rows)
            val x = (((rootX - drawn.left) / drawn.width) * bitmap.width)
                .roundToInt().coerceIn(0, bitmap.width - 1)
            val y = (((rootY - drawn.top) / drawn.height) * bitmap.height)
                .roundToInt().coerceIn(0, bitmap.height - 1)
            val pixel = bitmap[x, y]
            val source = ComposeColor(pixel)
            samples.add(ComposeColor(source.red * brightness, source.green * brightness, source.blue * brightness))
        }
    }
    return samples
}

internal fun ScheduleConfigEntity.hasAnyWallpaper(): Boolean {
    return !wallpaperUri.isNullOrBlank() || defaultWallpaperStyle == DefaultWallpaperStyle.KANBAN
}

internal fun homeFlatBackgroundColor(dark: Boolean): ComposeColor =
    if (dark) ComposeColor(0xFF18181C) else ComposeColor(0xFFF1F1F3)

