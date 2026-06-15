package com.example.courseschedule

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawPlainBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.runtimeShaderEffect

enum class ProgressiveBlurDirection {
    TopToBottom,
    BottomToTop
}

@Composable
fun ProgressiveBackdropBlur(
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
    tintColor: Color,
    height: Dp,
    blurRadius: Dp = 16.dp,
    tintIntensity: Float = 0.18f,
    direction: ProgressiveBlurDirection = ProgressiveBlurDirection.TopToBottom,
    fallbackTintStops: List<Pair<Float, Color>>
) {
    val baseModifier = modifier.fillMaxWidth().height(height)
    val fallbackBrush = Brush.verticalGradient(*fallbackTintStops.toTypedArray())
    Box(
        modifier = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && backdrop != null) {
            baseModifier.drawPlainBackdrop(
                backdrop = backdrop,
                shape = { RectangleShape },
                effects = {
                    blur(blurRadius.toPx())
                    runtimeShaderEffect(
                        "ProgressiveBackdropBlur_${direction.name}",
                        progressiveBlurShaderSource(direction),
                        "content"
                    ) {
                        setFloatUniform("size", size.width, size.height)
                        setColorUniform("tint", tintColor)
                        setFloatUniform("tintIntensity", tintIntensity.coerceIn(0f, 1f))
                    }
                }
            )
        } else {
            baseModifier.background(fallbackBrush)
        }
    )
}

private fun progressiveBlurShaderSource(direction: ProgressiveBlurDirection): String {
    val maskExpression = when (direction) {
        ProgressiveBlurDirection.TopToBottom -> """
            float mask = 1.0 - smoothstep(0.45, 1.0, y);
            float tintMask = 1.0 - smoothstep(0.35, 1.0, y);
        """.trimIndent()

        ProgressiveBlurDirection.BottomToTop -> """
            float mask = smoothstep(0.0, 0.55, y);
            float tintMask = smoothstep(0.0, 0.65, y);
        """.trimIndent()
    }
    return """
        uniform shader content;
        uniform float2 size;
        layout(color) uniform half4 tint;
        uniform float tintIntensity;

        half4 main(float2 coord) {
            float y = coord.y / size.y;
            $maskExpression
            return mix(content.eval(coord) * mask, tint * tintMask, tintIntensity);
        }
    """.trimIndent()
}
