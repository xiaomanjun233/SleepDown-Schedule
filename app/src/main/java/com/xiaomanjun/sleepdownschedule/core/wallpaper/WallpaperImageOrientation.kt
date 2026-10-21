package com.xiaomanjun.sleepdownschedule.core.wallpaper

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.InputStream

internal data class WallpaperImageOrientation(val rotation: Int = 0, val flipped: Boolean = false) {
    fun sourceSize(width: Int, height: Int): WallpaperSourceSize =
        if (rotation == 90 || rotation == 270) WallpaperSourceSize(height, width)
        else WallpaperSourceSize(width, height)

    fun applyTo(bitmap: Bitmap): Bitmap {
        if (rotation == 0 && !flipped) return bitmap
        val transform = Matrix().apply {
            if (flipped) postScale(-1f, 1f)
            postRotate(rotation.toFloat())
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, transform, true).also {
            if (it !== bitmap) bitmap.recycle()
        }
    }
}

internal fun readWallpaperImageOrientation(open: () -> InputStream?): WallpaperImageOrientation =
    runCatching {
        open()?.use { stream ->
            val exif = ExifInterface(stream)
            WallpaperImageOrientation(exif.rotationDegrees, exif.isFlipped)
        }
    }.getOrNull() ?: WallpaperImageOrientation()
