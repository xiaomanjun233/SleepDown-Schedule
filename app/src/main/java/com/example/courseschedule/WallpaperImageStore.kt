package com.example.courseschedule

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.palette.graphics.Palette
import java.io.File
import kotlin.math.max

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
