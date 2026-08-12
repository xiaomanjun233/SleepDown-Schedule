package com.xiaomanjun.sleepdownschedule

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlin.math.abs
import kotlin.math.pow
import top.yukonga.miuix.kmp.squircle.squircleClip

private const val HomeChromeBlurSliderAnchor = 0.5f
private const val HomeChromeBlurSliderSnapThreshold = 0.025f

private fun anchoredHomeChromeBlurScale(value: Float): Float {
    val safe = normalizedHomeChromeBlurScale(value)
    return if (abs(safe - DefaultHomeChromeBlurScale) <= 0.04f) {
        DefaultHomeChromeBlurScale
    } else {
        safe
    }
}

/**
 * Keeps every component's original blur at the visual midpoint while giving both sides a wider
 * perceptual range: the left half reaches 0x more quickly and the right half reaches 8x.
 */
private fun homeChromeBlurScaleFromSlider(position: Float): Float {
    val safe = position.coerceIn(0f, 1f)
    return if (safe <= HomeChromeBlurSliderAnchor) {
        (safe / HomeChromeBlurSliderAnchor).pow(2.4f)
    } else {
        val progress = (safe - HomeChromeBlurSliderAnchor) / (1f - HomeChromeBlurSliderAnchor)
        DefaultHomeChromeBlurScale +
            (MaxHomeChromeBlurScale - DefaultHomeChromeBlurScale) * progress
    }
}

private fun homeChromeBlurSliderFromScale(scale: Float): Float {
    val safe = normalizedHomeChromeBlurScale(scale)
    return if (safe <= DefaultHomeChromeBlurScale) {
        HomeChromeBlurSliderAnchor * safe.pow(1f / 2.4f)
    } else {
        val progress = ((safe - DefaultHomeChromeBlurScale) /
            (MaxHomeChromeBlurScale - DefaultHomeChromeBlurScale)).coerceIn(0f, 1f)
        HomeChromeBlurSliderAnchor +
            (1f - HomeChromeBlurSliderAnchor) * progress
    }
}

@Composable
fun LiquidGlassSettingsScreen(
    state: AppState,
    backdrop: Backdrop?,
    onUpdateBlurScale: (Float) -> Unit
) {
    var previewBlurScale by remember(state.config.id) {
        mutableFloatStateOf(normalizedHomeChromeBlurScale(state.config.homeChromeBlurScale))
    }
    LaunchedEffect(state.config.homeChromeBlurScale) {
        previewBlurScale = normalizedHomeChromeBlurScale(state.config.homeChromeBlurScale)
    }

    val topPadding = detailContentTopPadding()
    val darkTheme = appUsesDarkTheme(state.config)
    val previewConfig = state.config.copy(
        wallpaperUri = null,
        defaultWallpaperStyle = DefaultWallpaperStyle.KANBAN,
        dockAlignment = DockAlignment.CENTER,
        homeChromeBlurScale = previewBlurScale
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = topPadding + 32.dp,
            bottom = DockScrollPadding
        ),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        item(key = "liquid-glass-preview") {
            LiquidGlassDockPreview(
                config = previewConfig,
                darkTheme = darkTheme
            )
        }
        item(key = "liquid-glass-blur") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GlassBlurEndpointIcon(
                        filled = false,
                        modifier = Modifier.semantics { contentDescription = "更通透" }
                    )
                    LiquidControlSlider(
                        value = homeChromeBlurSliderFromScale(previewBlurScale),
                        onValueChange = { position ->
                            val next = anchoredHomeChromeBlurScale(
                                homeChromeBlurScaleFromSlider(position)
                            )
                            previewBlurScale = next
                            onUpdateBlurScale(next)
                        },
                        onLiveValueChange = { position ->
                            previewBlurScale = anchoredHomeChromeBlurScale(
                                homeChromeBlurScaleFromSlider(position)
                            )
                        },
                        valueRange = 0f..1f,
                        snapValue = HomeChromeBlurSliderAnchor,
                        visibilityThreshold = HomeChromeBlurSliderSnapThreshold,
                        backdrop = backdrop,
                        modifier = Modifier.weight(1f)
                    )
                    GlassBlurEndpointIcon(
                        filled = true,
                        modifier = Modifier.semantics { contentDescription = "更模糊" }
                    )
                }
                Text(
                    text = "清透会更加透明；着色会提高不透明度，为内容和控件增加对比度。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun GlassBlurEndpointIcon(
    filled: Boolean,
    modifier: Modifier = Modifier
) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(modifier = modifier.size(width = 30.dp, height = 24.dp)) {
        val strokeWidth = 1.6.dp.toPx()
        val radius = 5.dp.toPx()
        val panelSize = Size(width = size.width * 0.66f, height = size.height * 0.62f)
        val backOffset = Offset(x = 1.dp.toPx(), y = 2.dp.toPx())
        val frontOffset = Offset(x = size.width * 0.30f, y = size.height * 0.30f)
        if (filled) {
            drawRoundRect(
                color = color.copy(alpha = 0.42f),
                topLeft = backOffset,
                size = panelSize,
                cornerRadius = CornerRadius(radius)
            )
            drawRoundRect(
                color = color.copy(alpha = 0.82f),
                topLeft = frontOffset,
                size = panelSize,
                cornerRadius = CornerRadius(radius)
            )
        } else {
            drawRoundRect(
                color = color.copy(alpha = 0.60f),
                topLeft = backOffset,
                size = panelSize,
                cornerRadius = CornerRadius(radius),
                style = Stroke(strokeWidth)
            )
            drawRoundRect(
                color = color.copy(alpha = 0.86f),
                topLeft = frontOffset,
                size = panelSize,
                cornerRadius = CornerRadius(radius),
                style = Stroke(strokeWidth)
            )
        }
    }
}

@Composable
private fun LiquidGlassDockPreview(
    config: ScheduleConfigEntity,
    darkTheme: Boolean
) {
    val previewBackdrop = rememberLayerBackdrop()
    val adaptiveGlass = remember(darkTheme) {
        adaptiveGlassStateFromLuminance(
            luminance = if (darkTheme) 0.28f else 0.72f,
            preferLightGlass = !darkTheme
        )
    }
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val previewWidth = minOf(maxWidth, 560.dp)
        val previewHeight = (previewWidth * 0.905f).coerceIn(250.dp, 500.dp)
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .width(previewWidth)
                .height(previewHeight)
                .squircleClip(32.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .layerBackdrop(previewBackdrop)
            ) {
                Image(
                    painter = painterResource(
                        if (darkTheme) R.drawable.default_wallpaper_dark
                        else R.drawable.default_wallpaper_light
                    ),
                    contentDescription = "液态玻璃预览壁纸",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            CompositionLocalProvider(LocalAdaptiveGlass provides adaptiveGlass) {
                FloatingDock(
                    selected = Screen.Home,
                    backdrop = previewBackdrop,
                    config = config,
                    modifier = Modifier.align(Alignment.BottomCenter),
                    previewMode = true,
                    onHome = {},
                    onConfig = {}
                )
            }
        }
    }
}
