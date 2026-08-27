package com.xiaomanjun.sleepdownschedule.core.wallpaper

import com.xiaomanjun.sleepdownschedule.glass.ui.*

import com.xiaomanjun.sleepdownschedule.*

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
import java.net.URI
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
    return curateWallpaperCoursePalette(
        palette.swatches.map { swatch ->
            (swatch.rgb.toLong() and 0xFFFFFFFFL) to swatch.population
        }
    )
}

/**
 * Palette target swatches are useful for accents, but ordering vibrant/dark targets before real
 * population made a tiny shadow or accessory steer several course cards. Keep populous colours,
 * reject low-population hue outliers, and let the assignment stage create nearby variations.
 */
internal fun curateWallpaperCoursePalette(
    weightedColors: List<Pair<Long, Int>>
): List<Long> {
    if (weightedColors.isEmpty()) return DefaultCourseCardPalette
    val ordered = weightedColors
        .filter { it.second > 0 }
        .sortedByDescending { it.second }
    if (ordered.isEmpty()) return DefaultCourseCardPalette
    val maximumPopulation = ordered.first().second.coerceAtLeast(1)
    fun hsv(color: Long): FloatArray = FloatArray(3).also {
        android.graphics.Color.colorToHSV(color.toInt(), it)
    }
    fun hueDistance(first: Float, second: Float): Float {
        val raw = kotlin.math.abs(first - second) % 360f
        return minOf(raw, 360f - raw)
    }
    val dominantHsv = hsv(ordered.first().first)
    val ranked = ordered
        .asSequence()
        .map { (color, population) ->
            val colorHsv = hsv(color)
            val populationRatio = population.toFloat() / maximumPopulation
            val distance = hueDistance(dominantHsv[0], colorHsv[0])
            Triple(color, colorHsv, populationRatio to distance)
        }
        .filter { (_, colorHsv, score) ->
            val (populationRatio, distance) = score
            colorHsv[2] >= 0.22f &&
                populationRatio >= 0.035f &&
                (
                    dominantHsv[1] < 0.16f ||
                        colorHsv[1] < 0.14f ||
                        populationRatio >= 0.18f ||
                        distance <= 70f
                    )
        }
        .sortedByDescending { (_, colorHsv, score) ->
            val (populationRatio, distance) = score
            val familyScore = if (dominantHsv[1] < 0.16f || colorHsv[1] < 0.14f) {
                0.5f
            } else {
                1f - distance / 180f
            }
            populationRatio * 0.82f + familyScore * 0.18f
        }
        .map { it.first }
        .toList()
    val distinct = mutableListOf<Long>()
    ranked.forEach { color ->
        if (distinct.all { courseCardPerceptualDistance(it, color) >= 0.045 }) {
            distinct += color
        }
    }
    return distinct.take(6).ifEmpty { listOf(ordered.first().first) }
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
    return persistManagedWallpaperBitmap(context, bitmap, directoryName, filePrefix).also {
        bitmap.recycle()
    }
}

internal fun persistManagedWallpaperBitmap(
    context: Context,
    bitmap: Bitmap,
    directoryName: String,
    filePrefix: String
): Uri? {
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

internal fun unreferencedWallpaperFiles(
    referencedUris: Collection<String>,
    candidateFiles: Collection<File>,
    canonicalize: (File) -> String = { it.canonicalPath }
): Set<File> {
    val referencedPaths = referencedUris.mapNotNullTo(linkedSetOf()) { uriString ->
        val uri = runCatching { URI(uriString) }.getOrNull()
        if (uri?.scheme != "file") null else uri.path?.let { path ->
            runCatching { canonicalize(File(path)) }.getOrNull()
        }
    }
    return candidateFiles.filterTo(linkedSetOf()) { file ->
        runCatching { canonicalize(file) }.getOrNull() !in referencedPaths
    }
}

fun cleanupUnreferencedScheduleWallpapers(context: Context, referencedUris: Collection<String>) {
    val wallpaperDir = wallpaperDirectory(context)
    val files = wallpaperDir.listFiles().orEmpty().filter(File::isFile)
    unreferencedWallpaperFiles(referencedUris, files).forEach { file ->
        runCatching { file.delete() }
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
