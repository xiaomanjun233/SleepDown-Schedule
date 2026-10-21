package com.xiaomanjun.sleepdownschedule.platform.coloros

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.ColorUtils
import androidx.core.net.toUri
import androidx.palette.graphics.Palette
import com.xiaomanjun.sleepdownschedule.R
import com.xiaomanjun.sleepdownschedule.core.wallpaper.loadSampledBitmap
import com.xiaomanjun.sleepdownschedule.model.DefaultWallpaperStyle
import com.xiaomanjun.sleepdownschedule.model.ScheduleConfigEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private val themeAccentCache = android.util.LruCache<String, Int>(6)
private val themeAccentMutex = Mutex()
private const val DefaultThemeAccent = 0xFF007AFF.toInt()

/** One small source decode per wallpaper; all Activities and native dialogs share the result. */
@Composable
internal fun rememberColorOsThemeAccent(config: ScheduleConfigEntity, dark: Boolean): Int {
    val host = LocalContext.current.applicationContext
    if (rememberColorOsSource() == null) return DefaultThemeAccent
    val uri = config.wallpaperUri?.takeIf { it.isNotBlank() }
    val resource = if (config.defaultWallpaperStyle == DefaultWallpaperStyle.KANBAN)
        if (dark) R.drawable.default_wallpaper_dark else R.drawable.default_wallpaper_light else 0
    val key = uri ?: "resource:$resource"
    val accent by produceState(themeAccentCache.get(key) ?: DefaultThemeAccent, key) {
        value = withContext(Dispatchers.IO) {
            themeAccentMutex.withLock {
                themeAccentCache.get(key) ?: run {
                    val bitmap = uri?.let { loadSampledBitmap(host, it.toUri(), maxDimension = 256) }
                        ?: resource.takeIf { it != 0 }?.let {
                            BitmapFactory.decodeResource(host.resources, it,
                                BitmapFactory.Options().apply { inSampleSize = 8 })
                        }
                    val extracted = try { colorOsWallpaperAccent(bitmap) ?: DefaultThemeAccent }
                        finally { bitmap?.recycle() }
                    themeAccentCache.put(key, extracted)
                    extracted
                }
            }
        }
    }
    return accent
}

@Immutable
internal data class ColorOsHomeAccents(val button: Int, val dock: Int)

internal fun dockAccent(source: ColorOsNativeUi.Source, context: Context, wallpaperAccent: Int?): Int =
    wallpaperAccent ?: source.color(context, "couiColorContainerTheme").defaultColor

@Composable
internal fun rememberColorOsHomeAccents(config: ScheduleConfigEntity, dark: Boolean, background: Int): ColorOsHomeAccents? {
    val host = LocalContext.current
    if (rememberColorOsSource() == null) return null
    val wallpaperAccent = rememberColorOsThemeAccent(config, dark)
    return remember(wallpaperAccent, background) {
        val button = wallpaperAccent
        ColorOsHomeAccents(button, colorOsContrastingAccent(button, background))
    }
}

/** Course palettes discard dark swatches; chrome must extract its own hue before brightening it. */
internal fun colorOsWallpaperAccent(bitmap: Bitmap?): Int? {
    if (bitmap == null || bitmap.isRecycled) return null
    val hsv = FloatArray(3)
    val seed = Palette.from(bitmap).clearFilters().maximumColorCount(12).resizeBitmapArea(96 * 96)
        .generate().swatches.sortedByDescending { it.population }.firstOrNull {
            Color.colorToHSV(it.rgb, hsv)
            hsv[1] >= 0.16f && hsv[2] >= 0.04f
        } ?: return null
    Color.colorToHSV(seed.rgb, hsv)
    hsv[1] = hsv[1].coerceIn(0.50f, 0.85f)
    hsv[2] = hsv[2].coerceIn(0.60f, 0.80f)
    return Color.HSVToColor(hsv)
}

/** Preserve wallpaper hue; adjust lightness against the visible, brightness-adjusted backdrop. */
internal fun colorOsContrastingAccent(seed: Int, background: Int): Int {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(seed, hsl)
    val brighten = ColorUtils.calculateLuminance(background) < 0.18
    val preferred = if (brighten) maxOf(hsl[2], 0.66f) else minOf(hsl[2], 0.42f)
    hsl[2] = preferred
    val candidate = ColorUtils.HSLToColor(hsl)
    if (ColorUtils.calculateContrast(candidate, background) >= 4.5) return candidate
    var low = 0f
    var high = 1f
    repeat(18) {
        val amount = (low + high) / 2f
        hsl[2] = preferred + ((if (brighten) 1f else 0f) - preferred) * amount
        if (ColorUtils.calculateContrast(ColorUtils.HSLToColor(hsl), background) >= 4.5) high = amount
        else low = amount
    }
    hsl[2] = preferred + ((if (brighten) 1f else 0f) - preferred) * high
    return ColorUtils.HSLToColor(hsl)
}
