package com.xiaomanjun.sleepdownschedule.feature.home.day

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
import com.xiaomanjun.sleepdownschedule.core.ui.text.softTextShadowStrength
import com.xiaomanjun.sleepdownschedule.core.wallpaper.HomeReadabilityContext
import com.xiaomanjun.sleepdownschedule.core.wallpaper.hasAnyWallpaper
import com.xiaomanjun.sleepdownschedule.core.wallpaper.sampleVisibleWallpaperColors
import com.xiaomanjun.sleepdownschedule.feature.home.LocalHomePaneVisible
import kotlin.math.pow
import kotlinx.coroutines.flow.collectLatest

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
    frozen: Boolean
): Boolean? {
    val config = context.config
    var lightStyle by remember(config?.id, config?.wallpaperUri, config?.defaultWallpaperStyle) {
        mutableStateOf<Boolean?>(null)
    }
    LaunchedEffect(context, areas, frozen) {
        if (frozen || context.bitmap == null || config?.hasAnyWallpaper() != true) return@LaunchedEffect
        snapshotFlow {
            if (areas.moving.any { it() }) null else areas.bounds.toMap()
        }.collectLatest { bounds ->
            if (bounds == null) return@collectLatest
            // Coalesce label placement and disposal before choosing one palette for the page.
            withFrameNanos { }
            wallpaperLightStyleForTextAreas(context, bounds.values)?.let { lightStyle = it }
        }
    }
    return lightStyle
}

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
