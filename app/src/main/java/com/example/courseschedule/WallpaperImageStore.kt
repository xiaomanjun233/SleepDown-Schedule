package com.example.courseschedule

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import androidx.core.net.toUri
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.net.Uri
import android.os.Build
import androidx.palette.graphics.Palette
import java.io.File
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt

const val WallpaperBlurMaxDp = 12f
private const val WallpaperDirectoryName = "wallpaper"
private const val StoredWallpaperQuality = 88

fun wallpaperBlurPercent(blurDp: Float): Float =
    blurDp.coerceIn(0f, WallpaperBlurMaxDp) / WallpaperBlurMaxDp * 100f

fun wallpaperBlurDp(percent: Float): Float =
    percent.coerceIn(0f, 100f) / 100f * WallpaperBlurMaxDp

fun extractRepresentativeWallpaperColors(bitmap: Bitmap?): List<Long> {
    if (bitmap == null || bitmap.width <= 0 || bitmap.height <= 0) return DefaultCourseCardPalette
    val palette = runCatching {
        Palette.from(bitmap)
            .maximumColorCount(12)
            .resizeBitmapArea(96 * 96)
            .generate()
    }.getOrNull() ?: return DefaultCourseCardPalette
    val candidates = buildList {
        palette.vibrantSwatch?.rgb?.let(::add)
        palette.lightVibrantSwatch?.rgb?.let(::add)
        palette.darkVibrantSwatch?.rgb?.let(::add)
        palette.mutedSwatch?.rgb?.let(::add)
        palette.lightMutedSwatch?.rgb?.let(::add)
        palette.darkMutedSwatch?.rgb?.let(::add)
        palette.swatches.sortedByDescending { it.population }.forEach { add(it.rgb) }
    }
    val distinct = mutableListOf<Int>()
    candidates.forEach { color ->
        val sufficientlyDifferent = distinct.all { existing ->
            val dr = android.graphics.Color.red(color) - android.graphics.Color.red(existing)
            val dg = android.graphics.Color.green(color) - android.graphics.Color.green(existing)
            val db = android.graphics.Color.blue(color) - android.graphics.Color.blue(existing)
            dr * dr + dg * dg + db * db >= 42 * 42
        }
        if (sufficientlyDifferent) distinct += color
    }
    return distinct.take(8).map { it.toLong() and 0xFFFFFFFFL }
        .takeIf { it.size >= 3 }
        ?: DefaultCourseCardPalette
}

data class WallpaperSourceSize(val width: Int, val height: Int)

data class LoadedWallpaperSource(
    val bitmap: Bitmap?,
    val sourceSize: WallpaperSourceSize?
)

fun persistWallpaperUriPermission(context: Context, uri: Uri) {
    runCatching {
        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}

fun persistWallpaperSource(context: Context, uri: Uri): Uri? {
    if (uri.scheme == "file") return uri
    persistWallpaperUriPermission(context, uri)
    return persistManagedWallpaperImage(
        context = context,
        uri = uri,
        directoryName = WallpaperDirectoryName,
        filePrefix = "source_wallpaper",
        maxDimension = 2600
    )
}

internal fun persistManagedWallpaperImage(
    context: Context,
    uri: Uri,
    directoryName: String,
    filePrefix: String,
    maxDimension: Int
): Uri? {
    if (uri.scheme == "file") return uri
    val bitmap = loadSampledBitmap(context, uri, maxDimension) ?: return null
    val directory = File(context.filesDir, directoryName).apply { mkdirs() }
    val output = File(directory, "${filePrefix}_${UUID.randomUUID()}.webp")
    val temporary = File(directory, "${output.name}.tmp")
    return runCatching {
        temporary.outputStream().buffered().use { stream ->
            check(compressStoredWallpaper(bitmap, stream)) { "壁纸压缩失败" }
        }
        if (!temporary.renameTo(output)) {
            temporary.copyTo(output, overwrite = true)
            temporary.delete()
        }
        Uri.fromFile(output)
    }.getOrElse {
        temporary.delete()
        output.delete()
        null
    }.also {
        bitmap.recycle()
    }
}

@Suppress("DEPRECATION")
private fun compressStoredWallpaper(bitmap: Bitmap, output: java.io.OutputStream): Boolean {
    val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Bitmap.CompressFormat.WEBP_LOSSY
    } else {
        Bitmap.CompressFormat.WEBP
    }
    return bitmap.compress(format, StoredWallpaperQuality, output)
}

internal fun unreferencedScheduleWallpaperUris(
    referencedUris: Collection<String>,
    candidateUris: Collection<String>
): Set<String> {
    val referenced = referencedUris.filterTo(linkedSetOf()) { it.isNotBlank() }
    return candidateUris.filterTo(linkedSetOf()) { it !in referenced }
}

fun cleanupUnreferencedScheduleWallpapers(context: Context, referencedUris: Collection<String>) {
    val wallpaperDir = wallpaperDirectory(context)
    val filesByUri = wallpaperDir.listFiles()
        ?.asSequence()
        ?.filter { it.isFile }
        ?.associateBy { Uri.fromFile(it).toString() }
        .orEmpty()
    unreferencedScheduleWallpaperUris(referencedUris, filesByUri.keys).forEach { uri ->
        runCatching { filesByUri[uri]?.delete() }
    }
}

private fun wallpaperDirectory(context: Context): File = File(context.filesDir, WallpaperDirectoryName)

fun loadWallpaperBitmap(context: Context, config: ScheduleConfigEntity, useDarkDefaultWallpaper: Boolean): Bitmap? {
    val customUri = config.wallpaperUri
    if (!customUri.isNullOrBlank()) {
        loadSampledBitmap(context, customUri.toUri(), maxDimension = 2600)?.let { return it }
    }
    if (config.defaultWallpaperStyle == DefaultWallpaperStyle.KANBAN) {
        val defaultRes = if (useDarkDefaultWallpaper) R.drawable.default_wallpaper_dark else R.drawable.default_wallpaper_light
        return runCatching { BitmapFactory.decodeResource(context.resources, defaultRes) }.getOrNull()
    }
    return null
}

fun loadSampledBitmap(context: Context, uri: Uri, maxDimension: Int = 2200): Bitmap? {
    return loadWallpaperSource(context, uri, maxDimension).bitmap
}

fun loadWallpaperSource(
    context: Context,
    uri: Uri,
    maxDimension: Int = 2200
): LoadedWallpaperSource {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    runCatching {
        openWallpaperInputStream(context, uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
        return LoadedWallpaperSource(bitmap = null, sourceSize = null)
    }
    val sourceSize = WallpaperSourceSize(bounds.outWidth, bounds.outHeight)
    val sampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxDimension)
    val options = BitmapFactory.Options().apply {
        inSampleSize = sampleSize
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    val bitmap = runCatching {
        openWallpaperInputStream(context, uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    }.getOrNull()
    return LoadedWallpaperSource(bitmap = bitmap, sourceSize = sourceSize)
}

fun createReducedWallpaperBitmap(source: Bitmap?): Bitmap? {
    if (source == null || source.width <= 0 || source.height <= 0) return null
    val largest = max(source.width, source.height).coerceAtLeast(1)
    val maxReducedDimension = 900
    val scale = (maxReducedDimension.toFloat() / largest).coerceAtMost(1f)
    if (scale >= 0.999f) return source
    val width = (source.width * scale).roundToInt().coerceAtLeast(1)
    val height = (source.height * scale).roundToInt().coerceAtLeast(1)
    return source.scale(width, height)
}

/**
 * Small, always software-backed copy used only for regional text contrast checks.
 * Keeping this separate from the render bitmap avoids reading hardware bitmaps on
 * the main thread and makes the handful of per-label samples effectively free.
 */
fun createWallpaperReadabilityBitmap(source: Bitmap?): Bitmap? {
    if (source == null || source.width <= 0 || source.height <= 0) return null
    val largest = max(source.width, source.height).coerceAtLeast(1)
    val scale = (128f / largest).coerceAtMost(1f)
    val width = (source.width * scale).roundToInt().coerceAtLeast(1)
    val height = (source.height * scale).roundToInt().coerceAtLeast(1)
    return createBitmap(width, height).also { target ->
        Canvas(target).drawBitmap(source, null, android.graphics.Rect(0, 0, width, height), null)
    }
}

fun createBlurredWallpaperBitmap(source: Bitmap?, blurRadius: Int): Bitmap? {
    if (source == null || blurRadius <= 0 || source.width <= 0 || source.height <= 0) return null
    val largest = max(source.width, source.height).coerceAtLeast(1)
    val maxBlurDimension = 900
    val scale = (maxBlurDimension.toFloat() / largest).coerceAtMost(1f)
    val width = (source.width * scale).roundToInt().coerceAtLeast(1)
    val height = (source.height * scale).roundToInt().coerceAtLeast(1)
    val working = createBitmap(width, height)
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

fun wallpaperPrefersLightText(context: Context, uri: String?): Boolean {
    if (uri.isNullOrBlank()) return false
    val bitmap = loadSampledBitmap(context, uri.toUri(), maxDimension = 720) ?: return false
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
