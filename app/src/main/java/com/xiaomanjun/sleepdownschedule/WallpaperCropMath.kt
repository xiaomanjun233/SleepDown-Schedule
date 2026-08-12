package com.xiaomanjun.sleepdownschedule

import android.app.Activity
import android.content.Context
import android.os.Build
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlin.math.max
import kotlin.math.min

enum class WallpaperPreviewOrientation { Portrait, Landscape }

data class WallpaperCropState(
    val centerX: Float = 0.5f,
    val centerY: Float = 0.5f,
    val scale: Float = 1f
)

fun calculateWallpaperPreviewAspect(context: Context, orientation: WallpaperPreviewOrientation): Float {
    val bounds = runCatching {
        val activity = context as? Activity ?: return@runCatching null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            activity.windowManager.currentWindowMetrics.bounds
        } else {
            @Suppress("DEPRECATION")
            android.graphics.Rect().also { activity.windowManager.defaultDisplay.getRectSize(it) }
        }
    }.getOrNull()
    val width = bounds?.width()?.takeIf { it > 0 } ?: 0
    val height = bounds?.height()?.takeIf { it > 0 } ?: 0
    if (width <= 0 || height <= 0) {
        return if (orientation == WallpaperPreviewOrientation.Portrait) 9f / 20f else 20f / 9f
    }
    val shortSide = min(width, height).toFloat()
    val longSide = max(width, height).toFloat()
    return if (orientation == WallpaperPreviewOrientation.Portrait) shortSide / longSide else longSide / shortSide
}

fun calculateFocusCropRect(
    bitmapWidth: Int,
    bitmapHeight: Int,
    containerWidth: Float,
    containerHeight: Float,
    cropState: WallpaperCropState
): Rect {
    if (bitmapWidth <= 0 || bitmapHeight <= 0 || containerWidth <= 0f || containerHeight <= 0f) {
        return Rect.Zero
    }
    val baseScale = max(containerWidth / bitmapWidth.toFloat(), containerHeight / bitmapHeight.toFloat())
    val finalScale = baseScale * cropState.scale.coerceAtLeast(1f)
    val drawnWidth = bitmapWidth * finalScale
    val drawnHeight = bitmapHeight * finalScale
    val focusPixelX = bitmapWidth * cropState.centerX.coerceIn(0f, 1f) * finalScale
    val focusPixelY = bitmapHeight * cropState.centerY.coerceIn(0f, 1f) * finalScale
    val offsetX = clampWallpaperOffset(containerWidth / 2f - focusPixelX, containerWidth, drawnWidth)
    val offsetY = clampWallpaperOffset(containerHeight / 2f - focusPixelY, containerHeight, drawnHeight)
    return Rect(offsetX, offsetY, offsetX + drawnWidth, offsetY + drawnHeight)
}

fun clampCropState(
    state: WallpaperCropState,
    bitmapWidth: Int,
    bitmapHeight: Int,
    containerWidth: Int,
    containerHeight: Int
): WallpaperCropState {
    if (bitmapWidth <= 0 || bitmapHeight <= 0 || containerWidth <= 0 || containerHeight <= 0) {
        return WallpaperCropState(
            centerX = state.centerX.coerceIn(0f, 1f),
            centerY = state.centerY.coerceIn(0f, 1f),
            scale = state.scale.coerceAtLeast(1f)
        )
    }
    val userScale = state.scale.coerceIn(1f, 6f)
    val baseScale = max(containerWidth / bitmapWidth.toFloat(), containerHeight / bitmapHeight.toFloat())
    val finalScale = baseScale * userScale
    val minCenterX = if (bitmapWidth * finalScale <= containerWidth) 0.5f else containerWidth / (2f * bitmapWidth * finalScale)
    val maxCenterX = if (bitmapWidth * finalScale <= containerWidth) 0.5f else 1f - minCenterX
    val minCenterY = if (bitmapHeight * finalScale <= containerHeight) 0.5f else containerHeight / (2f * bitmapHeight * finalScale)
    val maxCenterY = if (bitmapHeight * finalScale <= containerHeight) 0.5f else 1f - minCenterY
    return WallpaperCropState(
        centerX = state.centerX.coerceIn(minCenterX, maxCenterX),
        centerY = state.centerY.coerceIn(minCenterY, maxCenterY),
        scale = userScale
    )
}

fun updateCropStateForGesture(
    state: WallpaperCropState,
    pan: Offset,
    zoom: Float,
    bitmapWidth: Int,
    bitmapHeight: Int,
    containerWidth: Int,
    containerHeight: Int
): WallpaperCropState {
    if (bitmapWidth <= 0 || bitmapHeight <= 0 || containerWidth <= 0 || containerHeight <= 0) return state
    val nextScale = (state.scale.coerceAtLeast(1f) * zoom).coerceIn(1f, 6f)
    val baseScale = max(containerWidth / bitmapWidth.toFloat(), containerHeight / bitmapHeight.toFloat())
    val finalScale = baseScale * nextScale
    return clampCropState(
        state.copy(
            centerX = state.centerX - pan.x / (bitmapWidth * finalScale),
            centerY = state.centerY - pan.y / (bitmapHeight * finalScale),
            scale = nextScale
        ),
        bitmapWidth,
        bitmapHeight,
        containerWidth,
        containerHeight
    )
}

fun ScheduleConfigEntity.wallpaperCropState(orientation: WallpaperPreviewOrientation): WallpaperCropState {
    return if (orientation == WallpaperPreviewOrientation.Portrait) {
        WallpaperCropState(
            centerX = wallpaperPortraitCenterX ?: 0.5f,
            centerY = wallpaperPortraitCenterY ?: 0.5f,
            scale = wallpaperPortraitScale ?: 1f
        )
    } else {
        WallpaperCropState(
            centerX = wallpaperLandscapeCenterX ?: 0.5f,
            centerY = wallpaperLandscapeCenterY ?: 0.5f,
            scale = wallpaperLandscapeScale ?: 1f
        )
    }
}

private fun clampWallpaperOffset(offset: Float, container: Float, drawn: Float): Float {
    if (drawn <= container) return (container - drawn) / 2f
    return offset.coerceIn(container - drawn, 0f)
}
