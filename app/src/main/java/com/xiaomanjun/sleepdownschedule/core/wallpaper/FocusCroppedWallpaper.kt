package com.xiaomanjun.sleepdownschedule.core.wallpaper

import com.xiaomanjun.sleepdownschedule.*

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt

@Composable
fun FocusCroppedBitmap(
    bitmap: Bitmap,
    cropState: WallpaperCropState,
    modifier: Modifier = Modifier,
    alpha: Float = 1f
) {
    val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
    Canvas(modifier = modifier.graphicsLayer { this.alpha = alpha }) {
        drawFocusCroppedBitmap(bitmap, imageBitmap, cropState)
    }
}

@Composable
fun FocusCroppedWallpaper(
    bitmap: Bitmap,
    config: ScheduleConfigEntity,
    modifier: Modifier = Modifier,
    alpha: Float = 1f,
    useSavedCrop: Boolean = true
) {
    BoxWithConstraints(modifier = modifier) {
        val orientation = if (maxHeight >= maxWidth) {
            WallpaperPreviewOrientation.Portrait
        } else {
            WallpaperPreviewOrientation.Landscape
        }
        val cropState = if (useSavedCrop) {
            config.wallpaperCropState(orientation)
        } else {
            WallpaperCropState()
        }
        FocusCroppedBitmap(
            bitmap = bitmap,
            cropState = cropState,
            modifier = Modifier.fillMaxSize(),
            alpha = alpha
        )
    }
}

private fun DrawScope.drawFocusCroppedBitmap(
    bitmap: Bitmap,
    imageBitmap: androidx.compose.ui.graphics.ImageBitmap,
    cropState: WallpaperCropState
) {
    val rect = calculateFocusCropRect(
        bitmap.width,
        bitmap.height,
        size.width,
        size.height,
        cropState
    )
    if (rect == androidx.compose.ui.geometry.Rect.Zero) return
    drawImage(
        image = imageBitmap,
        dstOffset = IntOffset(rect.left.roundToInt(), rect.top.roundToInt()),
        dstSize = IntSize(
            rect.width.roundToInt().coerceAtLeast(1),
            rect.height.roundToInt().coerceAtLeast(1)
        )
    )
}
