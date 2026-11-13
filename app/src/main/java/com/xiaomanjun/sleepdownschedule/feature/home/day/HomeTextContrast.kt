package com.xiaomanjun.sleepdownschedule.feature.home.day

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.edit
import com.xiaomanjun.sleepdownschedule.core.ui.text.softTextShadowStrength
import com.xiaomanjun.sleepdownschedule.core.wallpaper.HomeReadabilityContext
import com.xiaomanjun.sleepdownschedule.core.wallpaper.hasAnyWallpaper
import com.xiaomanjun.sleepdownschedule.core.wallpaper.sampleVisibleWallpaperColors
import com.xiaomanjun.sleepdownschedule.core.wallpaper.WallpaperPreviewOrientation
import com.xiaomanjun.sleepdownschedule.core.wallpaper.wallpaperCropState
import com.xiaomanjun.sleepdownschedule.feature.home.LocalHomePaneVisible
import com.xiaomanjun.sleepdownschedule.model.ScheduleConfigEntity
import kotlin.math.pow
import kotlinx.coroutines.flow.first

internal class HomeWallpaperTextAreas {
    val bounds = mutableStateMapOf<Any, Rect>()
    val moving = mutableStateListOf<() -> Boolean>()
}

internal val LocalHomeWallpaperTextAreas = compositionLocalOf<HomeWallpaperTextAreas?> { null }

@Composable
internal fun TrackHomeWallpaperTextMotion(isMoving: () -> Boolean) {
    val areas = LocalHomeWallpaperTextAreas.current ?: return
    val visible = LocalHomePaneVisible.current
    val currentMoving by rememberUpdatedState(isMoving)
    DisposableEffect(areas, visible) {
        val source = { visible.value && currentMoving() }
        areas.moving.add(source)
        onDispose { areas.moving.remove(source) }
    }
}

@Composable
internal fun rememberHomeWallpaperLightStyle(
    context: HomeReadabilityContext,
    areas: HomeWallpaperTextAreas,
    savedConfig: ScheduleConfigEntity,
    darkDefaultWallpaper: Boolean,
    frozen: Boolean
): Boolean? {
    val storage = LocalContext.current.applicationContext
    val preferences = remember(storage) {
        storage.getSharedPreferences("home_wallpaper_palette", Context.MODE_PRIVATE)
    }
    val wallpaperKey = remember(savedConfig, darkDefaultWallpaper) {
        wallpaperPaletteKey(savedConfig, darkDefaultWallpaper)
    }
    val storageKey = "schedule_${savedConfig.id}"
    var savedPalette by remember(storageKey) {
        val cached = preferences.getString(storageKey, null)
        mutableStateOf(cached?.substringBefore('|')?.toBooleanStrictOrNull()?.let { cached.substringAfter('|') to it })
    }
    val currentContext by rememberUpdatedState(context)
    val matchingWallpaper = context.config?.let {
        wallpaperPaletteKey(it, darkDefaultWallpaper) == wallpaperKey
    } == true
    val ready = !frozen && matchingWallpaper && context.bitmap != null && savedConfig.hasAnyWallpaper()
    LaunchedEffect(storageKey, wallpaperKey, areas, ready) {
        if (!ready || savedPalette?.first == wallpaperKey) return@LaunchedEffect
        // Choose once from a settled layout for each saved wallpaper and crop.
        snapshotFlow { areas.bounds.isNotEmpty() && areas.moving.none { it() } }.first { it }
        withFrameNanos { }
        val bounds = snapshotFlow {
            if (areas.moving.any { it() } || areas.bounds.isEmpty()) null else areas.bounds.toMap().values
        }.first { it != null } ?: return@LaunchedEffect
        val decided = wallpaperLightStyleForTextAreas(currentContext, bounds) ?: if (savedConfig.wallpaperUri.isNullOrBlank()) {
            !darkDefaultWallpaper
        } else {
            !savedConfig.homeTextLight && savedConfig.wallpaperBrightness >= 0.72f
        }
        savedPalette = wallpaperKey to decided
        preferences.edit { putString(storageKey, "$decided|$wallpaperKey") }
    }
    return savedPalette?.second.takeIf { savedConfig.hasAnyWallpaper() }
}

internal fun wallpaperPaletteKey(config: ScheduleConfigEntity, darkDefaultWallpaper: Boolean): String =
    listOf(
        config.wallpaperUri.orEmpty(),
        config.defaultWallpaperStyle,
        config.wallpaperUri.isNullOrBlank() && darkDefaultWallpaper,
        config.wallpaperBrightness,
        config.wallpaperBlur,
        config.wallpaperCropState(WallpaperPreviewOrientation.Portrait),
        config.wallpaperCropState(WallpaperPreviewOrientation.Landscape),
        config.homeTextLight,
    ).joinToString("|")

/** Vote by visible text area, so a larger title carries more weight than a small time label. */
internal fun wallpaperLightStyleForTextAreas(
    context: HomeReadabilityContext,
    boundsInWindow: Collection<Rect>
): Boolean? {
    val root = Rect(0f, 0f, context.rootSize.width.toFloat(), context.rootSize.height.toFloat())
    var totalArea = 0f
    var brightArea = 0f
    for (bounds in boundsInWindow) {
        val visible = bounds.translate(-context.rootOffsetInWindow).intersect(root)
        val samples = sampleVisibleWallpaperColors(context, visible) ?: continue
        val area = visible.width * visible.height
        // Above this relative luminance black has more contrast than white.
        val brightSamples = samples.count { it.luminance() >= 0.18f }
        brightArea += area * brightSamples / samples.size
        totalArea += area
    }
    val darkArea = totalArea - brightArea
    return when {
        brightArea > darkArea -> true
        darkArea > brightArea -> false
        else -> null
    }
}

/** Keep the chosen text color; protect the difficult parts of its background with a soft shadow. */
internal fun homeTextShadowStrength(
    luminances: FloatArray,
    textLuminance: Float,
    textAlpha: Float = 1f,
    currentStrength: Float = 0f
): Float = softTextShadowStrength(luminances, textLuminance, textAlpha, currentStrength)

/** The black wallpaper overlay multiplies sRGB channels before relative luminance is computed. */
internal fun visibleWallpaperLuminance(argb: Int, brightness: Float): Float {
    val dim = brightness.coerceIn(0.35f, 1f)
    fun channel(shift: Int): Float {
        val value = ((argb ushr shift) and 0xff) / 255f * dim
        return if (value <= 0.04045f) value / 12.92f else ((value + 0.055f) / 1.055f).pow(2.4f)
    }
    return channel(16) * 0.2126f + channel(8) * 0.7152f + channel(0) * 0.0722f
}
