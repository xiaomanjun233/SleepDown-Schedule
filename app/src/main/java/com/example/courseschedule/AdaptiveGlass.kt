package com.example.courseschedule

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sign

data class AdaptiveGlassState(
    val luminance: Float,
    val lightGlass: Boolean,
    val contentColor: Color,
    val surfaceColor: Color,
    val brightness: Float,
    val contrast: Float,
    val saturation: Float,
    val blurMultiplier: Float,
    val tintAlpha: Float
)

val LocalAdaptiveGlass = compositionLocalOf {
    adaptiveGlassStateFromLuminance(1f, preferLightGlass = true)
}

data class AdaptiveBackdropSampler(
    val layer: GraphicsLayer,
    val luminance: State<Float>,
    val contentColor: State<Color>,
    val enabled: Boolean,
    val recordBackdrop: DrawScope.(DrawScope.() -> Unit) -> Unit
)

@Composable
fun rememberFallbackAdaptiveGlassState(config: ScheduleConfigEntity): AdaptiveGlassState {
    val fallbackLightGlass = glassUsesLightStyle(config)
    val fallbackLuminance = when {
        fallbackLightGlass -> config.wallpaperBrightness.coerceIn(0.56f, 1f)
        else -> config.wallpaperBrightness.coerceIn(0f, 0.46f)
    }
    return adaptiveGlassStateFromLuminance(
        luminance = fallbackLuminance,
        preferLightGlass = fallbackLightGlass
    )
}

fun adaptiveGlassStateFromLuminance(
    luminance: Float,
    preferLightGlass: Boolean? = null,
    quality: Float = 1f
): AdaptiveGlassState {
    val clamped = luminance.coerceIn(0f, 1f)
    val lightGlass = when (preferLightGlass) {
        true -> clamped >= 0.46f
        false -> clamped > 0.56f
        null -> clamped > 0.52f
    }
    val signed = clamped * 2f - 1f
    val l = sign(signed) * signed * signed
    val brightness: Float
    val contrast: Float
    val blurMultiplier: Float
    val tintAlpha: Float
    if (l > 0f) {
        brightness = lerp(0.08f, 0.32f, l)
        contrast = lerp(1.0f, 0.78f, l)
        blurMultiplier = lerp(1.0f, 1.18f, l)
        tintAlpha = lerp(0.14f, 0.22f, l)
    } else {
        brightness = lerp(0.06f, -0.14f, -l)
        contrast = lerp(1.0f, 1.08f, -l)
        blurMultiplier = lerp(1.0f, 0.82f, -l)
        tintAlpha = lerp(0.18f, 0.30f, -l)
    }
    val scaledQuality = quality.coerceIn(0.45f, 1f)
    val contentColor = if (clamped > 0.5f) Color.Black else Color.White
    val surfaceColor =
        if (lightGlass) Color.White.copy(alpha = tintAlpha * scaledQuality)
        else Color(0xFF050505).copy(alpha = tintAlpha * scaledQuality)
    return AdaptiveGlassState(
        luminance = clamped,
        lightGlass = lightGlass,
        contentColor = contentColor,
        surfaceColor = surfaceColor,
        brightness = brightness,
        contrast = contrast,
        saturation = 1.5f,
        blurMultiplier = blurMultiplier,
        tintAlpha = tintAlpha
    )
}

@Composable
fun rememberAdaptiveBackdropLuminanceSampler(
    enabled: Boolean,
    sampleIntervalMillis: Long = 500L,
    sampleSize: Int = 5,
    animationMillis: Int = 650
): AdaptiveBackdropSampler {
    val layer = rememberGraphicsLayer()
    var targetLuminance by remember { mutableFloatStateOf(0.5f) }
    val luminance = animateFloatAsState(
        targetValue = targetLuminance,
        animationSpec = tween(animationMillis),
        label = "adaptive-glass-luminance"
    )
    val contentColor = animateColorAsState(
        targetValue = if (luminance.value > 0.5f) Color.Black else Color.White,
        animationSpec = tween(animationMillis),
        label = "adaptive-glass-content-color"
    )
    val clampedSampleSize = sampleSize.coerceIn(1, 8)
    LaunchedEffect(enabled, layer, sampleIntervalMillis, clampedSampleSize, animationMillis) {
        if (!enabled) return@LaunchedEffect
        val interval = max(500L, sampleIntervalMillis)
        while (isActive) {
            runCatching {
                targetLuminance = layer.sampleAverageLuminance(clampedSampleSize)
            }
            delay(interval)
        }
    }
    val record: DrawScope.(DrawScope.() -> Unit) -> Unit = remember(enabled, layer) {
        { drawBackdrop ->
            drawBackdrop()
            if (enabled) {
                layer.record(
                    density = this,
                    layoutDirection = layoutDirection,
                    size = IntSize(size.width.roundToInt(), size.height.roundToInt())
                ) {
                    drawBackdrop()
                }
            }
        }
    }
    return AdaptiveBackdropSampler(
        layer = layer,
        luminance = luminance,
        contentColor = contentColor,
        enabled = enabled,
        recordBackdrop = record
    )
}

@Composable
fun AdaptiveBackdropLuminanceProbe(
    backdrop: Backdrop?,
    sampler: AdaptiveBackdropSampler,
    modifier: Modifier = Modifier
) {
    if (!sampler.enabled || backdrop == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    Box(
        modifier = modifier
            .graphicsLayer(alpha = 0f)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { RectangleShape },
                effects = {},
                onDrawBackdrop = { drawBackdrop ->
                    sampler.recordBackdrop.invoke(this) {
                        drawBackdrop()
                    }
                }
            )
    )
}

private suspend fun GraphicsLayer.sampleAverageLuminance(sampleSize: Int): Float {
    val imageBitmap = toImageBitmap()
    val width = imageBitmap.width
    val height = imageBitmap.height
    if (width <= 0 || height <= 0) return 0.5f
    val pixel = IntArray(1)
    var luminance = 0f
    var count = 0
    val maxIndex = (sampleSize - 1).coerceAtLeast(1)
    repeat(sampleSize) { yIndex ->
        repeat(sampleSize) { xIndex ->
            val x = ((width - 1) * xIndex / maxIndex).coerceIn(0, width - 1)
            val y = ((height - 1) * yIndex / maxIndex).coerceIn(0, height - 1)
            imageBitmap.readPixels(pixel, startX = x, startY = y, width = 1, height = 1)
            luminance += pixel[0].weightedLuminance()
            count++
        }
    }
    return if (count == 0) 0.5f else luminance / count
}

private fun Int.weightedLuminance(): Float {
    val r = (this shr 16 and 0xFF) / 255f
    val g = (this shr 8 and 0xFF) / 255f
    val b = (this and 0xFF) / 255f
    return 0.2126f * r + 0.7152f * g + 0.0722f * b
}
