// Based on Kyant0/AndroidLiquidGlass catalog components, Apache-2.0.
// Modified for SleepDown-Schedule.
package com.kyant.backdrop.catalog.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.RoundedRectangle

@Composable
fun LiquidPanel(
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedRectangle(28.dp),
    surfaceColor: Color = Color.White.copy(alpha = 0.20f),
    blurRadius: Dp = 10.dp,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier.drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = {
                vibrancy()
                blur(blurRadius.toPx())
                lens(24f.dp.toPx(), 34f.dp.toPx(), chromaticAberration = false)
            },
            highlight = { Highlight.Default.copy(alpha = 0.16f) },
            shadow = { Shadow(alpha = 0.18f) },
            innerShadow = { InnerShadow(radius = 10.dp, alpha = 0.22f) },
            onDrawSurface = {
                drawRect(surfaceColor)
                drawRect(Color.White.copy(alpha = 0.035f), blendMode = BlendMode.Screen)
            }
        ),
        content = content
    )
}
