package com.xiaomanjun.sleepdownschedule.feature.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.effects.blur
import com.xiaomanjun.sleepdownschedule.ScheduleConfigEntity
import com.xiaomanjun.sleepdownschedule.core.ui.chrome.HomeLightGlassGradientColor
import com.xiaomanjun.sleepdownschedule.core.ui.chrome.HomeTopOverlayHeight
import com.xiaomanjun.sleepdownschedule.feature.home.day.WallpaperToneOverlay
import com.xiaomanjun.sleepdownschedule.feature.home.day.wallpaperGlassSamplingDim
import com.xiaomanjun.sleepdownschedule.feature.home.week.BoundlessWeekHeaderRowHeight
import com.xiaomanjun.sleepdownschedule.glass.ui.ProgressiveBackdropBlur
import com.xiaomanjun.sleepdownschedule.glass.ui.ProgressiveBlurDirection
import com.xiaomanjun.sleepdownschedule.glass.ui.glassUsesLightStyle
import kotlinx.coroutines.flow.first

// Size the tail from the actual header, rather than keeping the old 230dp minimum that
// spreads full-strength header blur too far into the first courses on compact windows.
internal fun boundlessHomeTopGradientHeight(metrics: HomeAdaptiveMetrics): Dp =
    metrics.safeTop + 66.dp + BoundlessWeekHeaderRowHeight + 36.dp

@Composable
fun HomeTopGradientBlur(
    config: ScheduleConfigEntity,
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
    height: Dp = HomeTopOverlayHeight,
    fullBlurHeight: Dp = 0.dp
) {
    val lightGlass = glassUsesLightStyle(config)
    val tintColor = if (lightGlass) HomeLightGlassGradientColor else ComposeColor(0xFF111111)
    Box(modifier = modifier) {
        ProgressiveBackdropBlur(
            backdrop = backdrop,
            tintColor = tintColor,
            height = height,
            blurRadius = 7.dp,
            // Pure blur: no white/dark tint base. A faint tint remains only as the API<33 fallback
            // brush (the runtime shader path is driven by tintIntensity = 0).
            tintIntensity = 0f,
            direction = ProgressiveBlurDirection.TopToBottom,
            // The Nexio radius curve needs its own plateau; alpha-mask parameters alone do not
            // stop the blur radius from shrinking before the boundless weekday/date header.
            radiusFadeStart = (fullBlurHeight.value / height.value).coerceIn(0f, 0.95f),
            topMaskFadeStart = 0.85f,
            topMaskFadeEnd = 1f,
            fallbackTintStops = listOf(
                0f to tintColor.copy(alpha = if (lightGlass) 0.12f else 0.16f),
                0.6f to tintColor.copy(alpha = if (lightGlass) 0.05f else 0.07f),
                1f to ComposeColor.Transparent
            )
        )
        // Wallpaper brightness: the backdrop sampled above already carries the glass sampling dim,
        // but the remainder dim (WallpaperToneOverlay) is kept outside the backdrop producers so
        // course cards stay bright. Re-apply that exact remainder inside the gradient zone so the
        // chrome does not read brighter ("发白") than the dimmed wallpaper around it.
        val brightness = config.wallpaperBrightness.coerceIn(0.35f, 1f)
        val totalDim = (1f - brightness).coerceIn(0f, 0.65f)
        val samplingDim = wallpaperGlassSamplingDim(brightness)
        val remainderDim = if (samplingDim >= 0.999f) {
            0f
        } else {
            ((totalDim - samplingDim) / (1f - samplingDim)).coerceIn(0f, 0.65f)
        }
        if (remainderDim > 0.001f) {
            val dimColor = ComposeColor.Black.copy(alpha = remainderDim)
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(height)
                    .drawBehind {
                        drawRect(
                            brush = Brush.verticalGradient(
                                0f to dimColor,
                                0.85f to dimColor.copy(alpha = dimColor.alpha * 0.6f),
                                1f to ComposeColor.Transparent
                            )
                        )
                    }
            )
        }
    }
}
