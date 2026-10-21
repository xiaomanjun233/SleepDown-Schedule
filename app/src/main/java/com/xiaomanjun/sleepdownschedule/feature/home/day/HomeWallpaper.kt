package com.xiaomanjun.sleepdownschedule.feature.home.day

import android.graphics.Bitmap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.core.graphics.get
import com.xiaomanjun.sleepdownschedule.DefaultWallpaperStyle
import com.xiaomanjun.sleepdownschedule.ScheduleConfigEntity
import com.xiaomanjun.sleepdownschedule.core.performance.HomeWallpaperImages
import com.xiaomanjun.sleepdownschedule.core.performance.StartupPhase
import com.xiaomanjun.sleepdownschedule.core.wallpaper.FocusCroppedWallpaper
import com.xiaomanjun.sleepdownschedule.feature.home.PersonalizationPreviewState
import kotlinx.coroutines.delay

@Composable
internal fun HomeWallpaper(
    config: ScheduleConfigEntity,
    images: HomeWallpaperImages,
    phase: StartupPhase,
    reduceQuality: Boolean = false,
    previewState: PersonalizationPreviewState? = null,
    onRecordKeyChanged: ((Any?) -> Unit)? = null
) {
    val targetBitmap = images.source
    val targetBlurredBitmap = images.blurredSource
    val targetReducedBitmap = images.reducedSource
    var visibleBitmap by remember { mutableStateOf<Bitmap?>(targetBitmap) }
    var visibleBlurredBitmap by remember { mutableStateOf<Bitmap?>(targetBlurredBitmap) }
    var visibleReducedBitmap by remember { mutableStateOf<Bitmap?>(targetReducedBitmap) }
    var previousBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var previousBlurredBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var previousReducedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var crossfadeTarget by remember { mutableFloatStateOf(1f) }
    val crossfadeAlpha by animateFloatAsState(
        targetValue = crossfadeTarget,
        animationSpec = tween(durationMillis = 140),
        label = "wallpaper-crossfade"
    )
    // The producer may reuse only a settled image. In particular, a target image's identity
    // alone is insufficient during the 140 ms crossfade or before LaunchedEffect hands it off.
    val previewBlur = previewState?.wallpaperBlur
    val recordKey = if (
        previousBitmap == null && crossfadeAlpha == 1f &&
        visibleBitmap != null && visibleBitmap == targetBitmap &&
        visibleBlurredBitmap == targetBlurredBitmap && visibleReducedBitmap == targetReducedBitmap
    ) listOf(visibleBitmap, visibleBlurredBitmap, visibleReducedBitmap, config, reduceQuality, previewBlur)
    else null
    SideEffect { onRecordKeyChanged?.invoke(recordKey) }
    DisposableEffect(onRecordKeyChanged) {
        onDispose { onRecordKeyChanged?.invoke(null) }
    }
    LaunchedEffect(targetBitmap, targetBlurredBitmap, targetReducedBitmap) {
        if (
            targetBitmap != visibleBitmap ||
            targetBlurredBitmap != visibleBlurredBitmap ||
            targetReducedBitmap != visibleReducedBitmap
        ) {
            // The first ready wallpaper participates in the first-draw gate without a fade.
            if (visibleBitmap == null) {
                visibleBitmap = targetBitmap
                visibleBlurredBitmap = targetBlurredBitmap
                visibleReducedBitmap = targetReducedBitmap
                crossfadeTarget = 1f
                return@LaunchedEffect
            }
            previousBitmap = visibleBitmap
            previousBlurredBitmap = visibleBlurredBitmap
            previousReducedBitmap = visibleReducedBitmap
            visibleBitmap = targetBitmap
            visibleBlurredBitmap = targetBlurredBitmap
            visibleReducedBitmap = targetReducedBitmap
            crossfadeTarget = 0f
            withFrameNanos { }
            crossfadeTarget = 1f
            delay(160)
            previousBitmap = null
            previousBlurredBitmap = null
            previousReducedBitmap = null
        }
    }
    val uri = config.wallpaperUri
    if (!uri.isNullOrBlank()) {
        val bitmap = visibleBitmap ?: return
        bitmap.let {
            Box(modifier = Modifier.fillMaxSize()) {
                previousBitmap?.let { old ->
                    HomeWallpaperLayer(
                        bitmap = wallpaperBitmapForRender(
                            source = old,
                            reduced = previousReducedBitmap,
                            blurred = previousBlurredBitmap,
                            reduceQuality = reduceQuality,
                            transientBlurActive = previewState?.wallpaperBlur != null
                        ),
                        config = config,
                        alpha = 1f - crossfadeAlpha,
                        useSavedCrop = true,
                        previewBlurDp = previewState?.wallpaperBlur
                    )
                }
                HomeWallpaperLayer(
                    bitmap = wallpaperBitmapForRender(
                        source = it,
                        reduced = visibleReducedBitmap,
                        blurred = visibleBlurredBitmap,
                        reduceQuality = reduceQuality,
                        transientBlurActive = previewState?.wallpaperBlur != null
                    ),
                    config = config,
                    alpha = crossfadeAlpha,
                    useSavedCrop = true,
                    previewBlurDp = previewState?.wallpaperBlur
                )
            }
        }
        return
    }
    if (config.defaultWallpaperStyle == DefaultWallpaperStyle.KANBAN) {
        visibleBitmap?.let {
            Box(modifier = Modifier.fillMaxSize()) {
                previousBitmap?.let { old ->
                    HomeWallpaperLayer(
                        bitmap = wallpaperBitmapForRender(
                            source = old,
                            reduced = previousReducedBitmap,
                            blurred = previousBlurredBitmap,
                            reduceQuality = reduceQuality,
                            transientBlurActive = previewState?.wallpaperBlur != null
                        ),
                        config = config,
                        alpha = 1f - crossfadeAlpha,
                        useSavedCrop = false,
                        previewBlurDp = previewState?.wallpaperBlur
                    )
                }
                HomeWallpaperLayer(
                    bitmap = wallpaperBitmapForRender(
                        source = it,
                        reduced = visibleReducedBitmap,
                        blurred = visibleBlurredBitmap,
                        reduceQuality = reduceQuality,
                        transientBlurActive = previewState?.wallpaperBlur != null
                    ),
                    config = config,
                    alpha = crossfadeAlpha,
                    useSavedCrop = false,
                    previewBlurDp = previewState?.wallpaperBlur
                )
            }
        }
        return
    }
}

private fun wallpaperBitmapForRender(
    source: Bitmap,
    reduced: Bitmap?,
    blurred: Bitmap?,
    reduceQuality: Boolean,
    transientBlurActive: Boolean
): Bitmap {
    if (transientBlurActive) return reduced ?: source
    return if (reduceQuality) {
        blurred ?: reduced ?: source
    } else {
        blurred ?: source
    }
}

@Composable
private fun HomeWallpaperLayer(
    bitmap: Bitmap,
    config: ScheduleConfigEntity,
    alpha: Float,
    useSavedCrop: Boolean,
    previewBlurDp: Float?
) {
    val density = LocalDensity.current
    val previewBlurPx = with(density) { (previewBlurDp ?: 0f).dp.toPx() }
    val previewBlurEffect = remember(previewBlurPx) {
        if (previewBlurPx > 0.1f) BlurEffect(previewBlurPx, previewBlurPx, TileMode.Decal) else null
    }
    FocusCroppedWallpaper(
        bitmap = bitmap,
        config = config,
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                this.alpha = alpha
                renderEffect = previewBlurEffect
            },
        useSavedCrop = useSavedCrop
    )
}

internal fun ScheduleConfigEntity.hasAnyWallpaper(): Boolean {
    return !wallpaperUri.isNullOrBlank() || defaultWallpaperStyle == DefaultWallpaperStyle.KANBAN
}

@Composable
internal fun WallpaperToneOverlay(
    config: ScheduleConfigEntity,
    previewState: PersonalizationPreviewState? = null
) {
    Box(
        Modifier
            .fillMaxSize()
            .drawBehind {
                val brightness = (previewState?.wallpaperBrightness ?: config.wallpaperBrightness)
                    .coerceIn(0.35f, 1f)
                val totalDim = (1f - brightness).coerceIn(0f, 0.65f)
                val samplingDim = wallpaperGlassSamplingDim(brightness)
                // The sampling tone is already inside backgroundBackdrop. Apply only the
                // mathematically exact remainder here so the visible wallpaper still reaches the
                // user's requested brightness instead of being darkened twice.
                val dim = if (samplingDim >= 0.999f) {
                    0f
                } else {
                    ((totalDim - samplingDim) / (1f - samplingDim)).coerceIn(0f, 0.65f)
                }
                if (dim > 0f) drawRect(ComposeColor.Black.copy(alpha = dim))
            }
    )
}

internal fun wallpaperGlassSamplingDim(brightness: Float): Float {
    val wallpaperDim = (1f - brightness.coerceIn(0.35f, 1f)).coerceIn(0f, 0.65f)
    // Quadratic onset keeps glass nearly unchanged for small wallpaper adjustments; the 0.72
    // gain then keeps even the darkest setting materially brighter than the wallpaper itself.
    return (wallpaperDim * wallpaperDim * 0.72f).coerceIn(0f, wallpaperDim)
}

@Composable
internal fun WallpaperGlassSamplingToneOverlay(
    config: ScheduleConfigEntity,
    previewState: PersonalizationPreviewState? = null
) {
    Box(
        Modifier
            .fillMaxSize()
            .drawBehind {
                val brightness = previewState?.wallpaperBrightness ?: config.wallpaperBrightness
                val dim = wallpaperGlassSamplingDim(brightness)
                if (dim > 0f) drawRect(ComposeColor.Black.copy(alpha = dim))
            }
    )
}

internal fun homeFlatBackgroundColor(dark: Boolean): ComposeColor =
    if (dark) ComposeColor(0xFF18181C) else ComposeColor(0xFFF1F1F3)

@Composable
fun HomeBackdropFallback(noWallpaper: Boolean = true) {
    val colors = MaterialTheme.colorScheme
    val dark = colors.background.luminance() < 0.5f

    Canvas(Modifier.fillMaxSize()) {
        if (noWallpaper) {
            // 无壁纸时背景带一点点灰，深浅色模式都调
            drawRect(homeFlatBackgroundColor(dark))
            return@Canvas
        }
        drawRect(colors.background)
        drawRect(
            brush = Brush.verticalGradient(
                colorStops = arrayOf(
                    0f to colors.primaryContainer.copy(alpha = if (dark) 0.34f else 0.42f),
                    0.24f to colors.surfaceVariant.copy(alpha = if (dark) 0.18f else 0.24f),
                    0.52f to colors.background,
                    0.76f to colors.tertiaryContainer.copy(alpha = if (dark) 0.22f else 0.30f),
                    1f to colors.secondaryContainer.copy(alpha = if (dark) 0.18f else 0.26f)
                ),
                startY = 0f,
                endY = size.height
            )
        )
        drawRect(
            brush = Brush.linearGradient(
                colorStops = arrayOf(
                    0f to ComposeColor.Transparent,
                    0.18f to colors.primary.copy(alpha = if (dark) 0.18f else 0.15f),
                    0.42f to colors.secondary.copy(alpha = if (dark) 0.10f else 0.09f),
                    0.66f to ComposeColor.Transparent,
                    1f to ComposeColor.Transparent
                ),
                start = Offset(-size.width * 0.20f, size.height * 0.02f),
                end = Offset(size.width * 1.12f, size.height * 0.62f)
            )
        )
        drawRect(
            brush = Brush.linearGradient(
                colorStops = arrayOf(
                    0f to ComposeColor.Transparent,
                    0.30f to ComposeColor.Transparent,
                    0.54f to colors.tertiary.copy(alpha = if (dark) 0.14f else 0.12f),
                    0.76f to colors.primary.copy(alpha = if (dark) 0.10f else 0.08f),
                    1f to ComposeColor.Transparent
                ),
                start = Offset(size.width * 1.18f, size.height * 0.18f),
                end = Offset(-size.width * 0.12f, size.height * 0.96f)
            )
        )
        // A wallpaper normally provides the local edges that make refraction readable. These
        // narrow, low-contrast bands provide the same sampling structure without turning the
        // wallpaper-free state into a busy illustration.
        drawRect(
            brush = Brush.verticalGradient(
                colorStops = arrayOf(
                    0f to ComposeColor.Transparent,
                    0.16f to ComposeColor.Transparent,
                    0.23f to colors.onBackground.copy(alpha = if (dark) 0.045f else 0.026f),
                    0.29f to ComposeColor.Transparent,
                    0.47f to ComposeColor.Transparent,
                    0.54f to colors.primary.copy(alpha = if (dark) 0.075f else 0.050f),
                    0.61f to ComposeColor.Transparent,
                    0.79f to ComposeColor.Transparent,
                    0.86f to colors.onBackground.copy(alpha = if (dark) 0.038f else 0.022f),
                    0.92f to ComposeColor.Transparent,
                    1f to ComposeColor.Transparent
                ),
                startY = 0f,
                endY = size.height
            )
        )
        drawRect(
            brush = Brush.linearGradient(
                colorStops = arrayOf(
                    0f to ComposeColor.Transparent,
                    0.38f to ComposeColor.Transparent,
                    0.47f to colors.secondary.copy(alpha = if (dark) 0.065f else 0.042f),
                    0.54f to colors.onBackground.copy(alpha = if (dark) 0.032f else 0.018f),
                    0.62f to ComposeColor.Transparent,
                    1f to ComposeColor.Transparent
                ),
                start = Offset(-size.width * 0.08f, size.height * 0.70f),
                end = Offset(size.width * 1.08f, size.height * 0.28f)
            )
        )
    }
}
