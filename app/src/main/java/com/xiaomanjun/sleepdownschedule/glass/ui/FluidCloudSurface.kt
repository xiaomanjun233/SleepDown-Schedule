package com.xiaomanjun.sleepdownschedule.glass.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.xiaomanjun.sleepdownschedule.core.performance.AppMaterialPreferences

/**
 * ColorOS seedling card mask. The system composes a blurred background with a full-height
 * LinearGradient drawable (`card_background_gradient_top/middle/bottom_color`): the middle
 * color is opaque black at the top and the bottom color is #05000000, so the glass shows
 * through at the bottom. Light cards mirror the mask with white.
 */
internal fun Modifier.fluidCloudTop(
    dark: Boolean,
    intensity: Float = 1f
): Modifier {
    if (AppMaterialPreferences.isSuperPerformance) return this
    val strength = intensity.coerceIn(0f, 1.5f)
    val mask = if (dark) Color.Black else Color.White
    return drawWithCache {
        // Same three-color family as the seedling drawable, but compressed into the upper
        // half so the assistant card keeps a larger glass area than the taller system card.
        val scrim = Brush.verticalGradient(
            0f to mask.copy(alpha = strength),
            0.26f to mask.copy(alpha = 0.50f * strength),
            0.46f to mask.copy(alpha = 0.02f * strength),
            1f to mask.copy(alpha = 0.02f * strength)
        )
        onDrawBehind { drawRect(scrim) }
    }
}
