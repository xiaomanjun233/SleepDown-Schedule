package com.xiaomanjun.sleepdownschedule.core.ui.chrome

import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.shadow.Shadow
import com.xiaomanjun.sleepdownschedule.DefaultHomeChromeBlurScale
import com.xiaomanjun.sleepdownschedule.ScheduleConfigEntity
import com.xiaomanjun.sleepdownschedule.glass.ui.GlassTokens
import com.xiaomanjun.sleepdownschedule.normalizedHomeChromeBlurScale

internal val DayDockScrollPadding = 104.dp
internal val WeekDockScrollPadding = 132.dp
internal val DockScrollPadding = 132.dp
internal val HomeHeaderGlassBlur = 2.dp
internal val HomeHeaderGlassLensHeight = 12.dp
internal val HomeHeaderGlassLensAmount = 24.dp
internal val HomeLightGlassSurfaceColor = ComposeColor(0xFFF2F4F8)
internal val HomeLightGlassGradientColor = ComposeColor(0xFFF7F8FB)
internal val HomeLightGlassAccentColor = ComposeColor(0xFF0A84FF)
internal val HomeLightGlassSelectedAccentColor = ComposeColor(0xFF006FD6)
internal const val HomeLightGlassChromeTintAlpha = 0.54f
internal const val HomeLightGlassAccentTintAlpha = 0.24f
internal const val HomeLightGlassPanelTintAlpha = 0.15f
internal const val HomeLightGlassMenuTintAlpha = 0.14f
internal fun homeChromeGlassSurfaceAlpha(lightGlass: Boolean): Float =
    if (lightGlass) HomeLightGlassChromeTintAlpha else 0.45f
internal const val HomeHeaderGlassHighlightAlpha = 0.09f
internal const val HomeHeaderGlassShadowAlpha = 0.05f
internal const val HomeHeaderGlassOuterShadowAlpha = 0.018f
internal const val HomeHeaderGlassInnerShadowAlpha = 0.08f
internal fun homeChromeBlur(base: Dp, config: ScheduleConfigEntity): Dp =
    base * normalizedHomeChromeBlurScale(config.homeChromeBlurScale)
internal fun homeHeaderGlassTokens(lightGlass: Boolean, blurScale: Float = DefaultHomeChromeBlurScale): GlassTokens {
    val base = GlassTokens.pill(intensity = 0.95f).copy(
        surfaceAlpha = homeChromeGlassSurfaceAlpha(lightGlass)
    )
    return base.copy(blur = base.blur * normalizedHomeChromeBlurScale(blurScale))
}

internal val HomeTopOverlayHeight = 178.dp
internal val LightTopBarButtonShadow = Shadow(
    radius = 7.dp,
    offset = DpOffset(0.dp, 1.dp),
    color = ComposeColor.Black.copy(alpha = 0.10f)
)
internal val HomeInitialTopInset = 122.dp
