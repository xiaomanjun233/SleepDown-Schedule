package com.example.courseschedule

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.net.Uri
import androidx.palette.graphics.Palette
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

data class WallpaperSourceSize(val width: Int, val height: Int)

fun persistWallpaperUriPermission(context: Context, uri: Uri) {
    runCatching {
        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}

fun persistWallpaperSource(context: Context, uri: Uri): Uri? {
    if (uri.scheme == "file") return uri
    persistWallpaperUriPermission(context, uri)
    val wallpaperDir = File(context.filesDir, "wallpaper")
    if (!wallpaperDir.exists()) wallpaperDir.mkdirs()
    val extension = runCatching {
        context.contentResolver.getType(uri)?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
    }.getOrNull() ?: "jpg"
    val output = File(wallpaperDir, "source_wallpaper_${System.currentTimeMillis()}.$extension")
    return runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
            output.outputStream().use { outputStream -> input.copyTo(outputStream) }
        } ?: return null
        wallpaperDir.listFiles()
            ?.filter { it.name.startsWith("source_wallpaper_") && it != output }
            ?.forEach { it.delete() }
        Uri.fromFile(output)
    }.getOrNull()
}

fun loadWallpaperBitmap(context: Context, config: ScheduleConfigEntity, useDarkDefaultWallpaper: Boolean): Bitmap? {
    val customUri = config.wallpaperUri
    if (!customUri.isNullOrBlank()) {
        loadSampledBitmap(context, Uri.parse(customUri), maxDimension = 2600)?.let { return it }
    }
    if (config.defaultWallpaperStyle == DefaultWallpaperStyle.KANBAN) {
        val defaultRes = if (useDarkDefaultWallpaper) R.drawable.default_wallpaper_dark else R.drawable.default_wallpaper_light
        return runCatching { BitmapFactory.decodeResource(context.resources, defaultRes) }.getOrNull()
    }
    return null
}

fun loadSampledBitmap(context: Context, uri: Uri, maxDimension: Int = 2200): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    runCatching {
        openWallpaperInputStream(context, uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    val sampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxDimension)
    val options = BitmapFactory.Options().apply {
        inSampleSize = sampleSize
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    return runCatching {
        openWallpaperInputStream(context, uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    }.getOrNull()
}

fun createReducedWallpaperBitmap(source: Bitmap?): Bitmap? {
    if (source == null || source.width <= 0 || source.height <= 0) return null
    val largest = max(source.width, source.height).coerceAtLeast(1)
    val maxReducedDimension = 900
    val scale = (maxReducedDimension.toFloat() / largest).coerceAtMost(1f)
    if (scale >= 0.999f) return source
    val width = (source.width * scale).roundToInt().coerceAtLeast(1)
    val height = (source.height * scale).roundToInt().coerceAtLeast(1)
    return Bitmap.createScaledBitmap(source, width, height, true)
}

fun createBlurredWallpaperBitmap(source: Bitmap?, blurRadius: Int): Bitmap? {
    if (source == null || blurRadius <= 0 || source.width <= 0 || source.height <= 0) return null
    val largest = max(source.width, source.height).coerceAtLeast(1)
    val maxBlurDimension = 900
    val scale = (maxBlurDimension.toFloat() / largest).coerceAtMost(1f)
    val width = (source.width * scale).roundToInt().coerceAtLeast(1)
    val height = (source.height * scale).roundToInt().coerceAtLeast(1)
    val working = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    Canvas(working).drawBitmap(source, null, android.graphics.Rect(0, 0, width, height), null)
    return runCatching {
        boxBlurBitmap(working, (blurRadius * scale).roundToInt().coerceIn(2, 18))
    }.getOrElse {
        working
    }
}

private fun boxBlurBitmap(bitmap: Bitmap, radius: Int): Bitmap {
    val width = bitmap.width
    val height = bitmap.height
    if (width <= 1 || height <= 1 || radius <= 0) return bitmap
    var pixels = IntArray(width * height)
    var scratch = IntArray(width * height)
    bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
    repeat(3) {
        boxBlurHorizontal(pixels, scratch, width, height, radius)
        boxBlurVertical(scratch, pixels, width, height, radius)
    }
    bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
    return bitmap
}

private fun boxBlurHorizontal(input: IntArray, output: IntArray, width: Int, height: Int, radius: Int) {
    val window = radius * 2 + 1
    for (y in 0 until height) {
        val row = y * width
        var a = 0
        var r = 0
        var g = 0
        var b = 0
        for (i in -radius..radius) {
            val color = input[row + i.coerceIn(0, width - 1)]
            a += color ushr 24
            r += color shr 16 and 0xFF
            g += color shr 8 and 0xFF
            b += color and 0xFF
        }
        for (x in 0 until width) {
            output[row + x] =
                (a / window shl 24) or
                    (r / window shl 16) or
                    (g / window shl 8) or
                    (b / window)
            val removeX = (x - radius).coerceIn(0, width - 1)
            val addX = (x + radius + 1).coerceIn(0, width - 1)
            val remove = input[row + removeX]
            val add = input[row + addX]
            a += (add ushr 24) - (remove ushr 24)
            r += (add shr 16 and 0xFF) - (remove shr 16 and 0xFF)
            g += (add shr 8 and 0xFF) - (remove shr 8 and 0xFF)
            b += (add and 0xFF) - (remove and 0xFF)
        }
    }
}

private fun boxBlurVertical(input: IntArray, output: IntArray, width: Int, height: Int, radius: Int) {
    val window = radius * 2 + 1
    for (x in 0 until width) {
        var a = 0
        var r = 0
        var g = 0
        var b = 0
        for (i in -radius..radius) {
            val color = input[i.coerceIn(0, height - 1) * width + x]
            a += color ushr 24
            r += color shr 16 and 0xFF
            g += color shr 8 and 0xFF
            b += color and 0xFF
        }
        for (y in 0 until height) {
            output[y * width + x] =
                (a / window shl 24) or
                    (r / window shl 16) or
                    (g / window shl 8) or
                    (b / window)
            val removeY = (y - radius).coerceIn(0, height - 1)
            val addY = (y + radius + 1).coerceIn(0, height - 1)
            val remove = input[removeY * width + x]
            val add = input[addY * width + x]
            a += (add ushr 24) - (remove ushr 24)
            r += (add shr 16 and 0xFF) - (remove shr 16 and 0xFF)
            g += (add shr 8 and 0xFF) - (remove shr 8 and 0xFF)
            b += (add and 0xFF) - (remove and 0xFF)
        }
    }
}

fun readWallpaperSourceSize(context: Context, uri: Uri): WallpaperSourceSize? {
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    runCatching {
        openWallpaperInputStream(context, uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    }
    return if (options.outWidth > 0 && options.outHeight > 0) {
        WallpaperSourceSize(options.outWidth, options.outHeight)
    } else {
        null
    }
}

private fun openWallpaperInputStream(context: Context, uri: Uri) =
    if (uri.scheme == "file") {
        File(uri.path.orEmpty()).inputStream()
    } else {
        context.contentResolver.openInputStream(uri)
    }

fun extractWallpaperColor(context: Context, uri: String?): Long? {
    if (uri.isNullOrBlank()) return null
    val bitmap = loadSampledBitmap(context, Uri.parse(uri), maxDimension = 720) ?: return null
    val color = runCatching {
        val palette = Palette.from(bitmap).generate()
        palette.getVibrantColor(palette.getMutedColor(android.graphics.Color.rgb(233, 221, 255)))
    }.getOrDefault(android.graphics.Color.rgb(233, 221, 255))
    return (color.toLong() and 0xFFFFFFFFL)
}

fun wallpaperPrefersLightText(context: Context, uri: String?): Boolean {
    if (uri.isNullOrBlank()) return false
    val bitmap = loadSampledBitmap(context, Uri.parse(uri), maxDimension = 720) ?: return false
    val palette = runCatching { Palette.from(bitmap).generate() }.getOrNull()
    val color = palette?.getDominantColor(android.graphics.Color.WHITE) ?: android.graphics.Color.WHITE
    val red = android.graphics.Color.red(color) / 255.0
    val green = android.graphics.Color.green(color) / 255.0
    val blue = android.graphics.Color.blue(color) / 255.0
    val luminance = 0.2126 * red + 0.7152 * green + 0.0722 * blue
    return luminance < 0.56
}

private fun calculateInSampleSize(width: Int, height: Int, maxDimension: Int): Int {
    var sampleSize = 1
    val largest = max(width, height)
    while (largest / sampleSize > maxDimension.coerceAtLeast(256)) {
        sampleSize *= 2
    }
    return sampleSize.coerceAtLeast(1)
}
