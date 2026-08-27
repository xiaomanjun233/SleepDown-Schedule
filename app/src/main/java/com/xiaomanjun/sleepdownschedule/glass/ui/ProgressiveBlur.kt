package com.xiaomanjun.sleepdownschedule.glass.ui

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.runtimeShaderEffect
import com.xiaomanjun.sleepdownschedule.glass.GlassBackdropDomain
import com.xiaomanjun.sleepdownschedule.glass.GlassMaterialRole
import com.xiaomanjun.sleepdownschedule.glass.GlassMaterialSpec
import com.xiaomanjun.sleepdownschedule.glass.rememberGlassSurfaceDescriptor
import com.xiaomanjun.sleepdownschedule.glass.sleepDownPlainGlassSurface

enum class ProgressiveBlurDirection {
    TopToBottom,
    BottomToTop,
    LeftToRight,
    RightToLeft
}

@Composable
fun ProgressiveBackdropBlur(
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
    tintColor: Color,
    height: Dp,
    blurRadius: Dp = 12.dp,
    tintIntensity: Float = 0.18f,
    direction: ProgressiveBlurDirection = ProgressiveBlurDirection.TopToBottom,
    topMaskFadeStart: Float = 0.45f,
    topMaskFadeEnd: Float = 1f,
    topTintFadeStart: Float = 0.35f,
    topTintFadeEnd: Float = 1f,
    fallbackTintStops: List<Pair<Float, Color>>
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .progressiveBackdropBlur(
                backdrop = backdrop,
                tintColor = tintColor,
                blurRadius = blurRadius,
                tintIntensity = tintIntensity,
                direction = direction,
                topMaskFadeStart = topMaskFadeStart,
                topMaskFadeEnd = topMaskFadeEnd,
                topTintFadeStart = topTintFadeStart,
                topTintFadeEnd = topTintFadeEnd,
                fallbackTintStops = fallbackTintStops
            )
    )
}

@Composable
fun Modifier.progressiveBackdropBlur(
    backdrop: Backdrop?,
    tintColor: Color,
    blurRadius: Dp = 12.dp,
    tintIntensity: Float = 0.18f,
    direction: ProgressiveBlurDirection = ProgressiveBlurDirection.TopToBottom,
    topMaskFadeStart: Float = 0.45f,
    topMaskFadeEnd: Float = 1f,
    topTintFadeStart: Float = 0.35f,
    topTintFadeEnd: Float = 1f,
    fallbackTintStops: List<Pair<Float, Color>>
): Modifier {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && backdrop != null) {
        val material = remember(blurRadius, tintIntensity) {
            GlassMaterialSpec.simpleBlur(blurRadius).copy(
                surfaceAlpha = tintIntensity.coerceIn(0f, 1f)
            )
        }
        val descriptor = rememberGlassSurfaceDescriptor(
            debugLabel = "ProgressiveBackdropBlur",
            domain = GlassBackdropDomain.Content,
            materialRole = GlassMaterialRole.SimpleBlur,
            sceneKey = "progressive-backdrop-blur"
        )
        sleepDownPlainGlassSurface(
            backdrop = backdrop,
            descriptor = descriptor,
            material = material,
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
                    setFloatUniform("maskFadeStart", topMaskFadeStart.coerceIn(0f, 1f))
                    setFloatUniform("maskFadeEnd", topMaskFadeEnd.coerceAtLeast(topMaskFadeStart + 0.01f))
                    setFloatUniform("tintFadeStart", topTintFadeStart.coerceIn(0f, 1f))
                    setFloatUniform("tintFadeEnd", topTintFadeEnd.coerceAtLeast(topTintFadeStart + 0.01f))
                }
            }
        )
    } else {
        val brush = when (direction) {
            ProgressiveBlurDirection.TopToBottom -> Brush.verticalGradient(*fallbackTintStops.toTypedArray())
            ProgressiveBlurDirection.BottomToTop -> Brush.verticalGradient(
                *fallbackTintStops.map { (stop, color) -> 1f - stop to color }.reversed().toTypedArray()
            )
            ProgressiveBlurDirection.LeftToRight -> Brush.horizontalGradient(*fallbackTintStops.toTypedArray())
            ProgressiveBlurDirection.RightToLeft -> Brush.horizontalGradient(
                *fallbackTintStops.map { (stop, color) -> 1f - stop to color }.reversed().toTypedArray()
            )
        }
        background(brush)
    }
}

internal fun progressiveBlurShaderSource(direction: ProgressiveBlurDirection): String {
    val maskExpression = when (direction) {
        ProgressiveBlurDirection.TopToBottom -> """
            float mask = 1.0 - smoothstep(maskFadeStart, maskFadeEnd, y);
            float tintMask = 1.0 - smoothstep(tintFadeStart, tintFadeEnd, y);
        """.trimIndent()

        ProgressiveBlurDirection.BottomToTop -> """
            float mask = smoothstep(0.0, 0.55, y);
            float tintMask = smoothstep(0.0, 0.65, y);
        """.trimIndent()

        ProgressiveBlurDirection.LeftToRight -> """
            float mask = smoothstep(maskFadeStart, maskFadeEnd, x);
            float tintMask = smoothstep(tintFadeStart, tintFadeEnd, x);
        """.trimIndent()

        ProgressiveBlurDirection.RightToLeft -> """
            float mask = 1.0 - smoothstep(maskFadeStart, maskFadeEnd, x);
            float tintMask = 1.0 - smoothstep(tintFadeStart, tintFadeEnd, x);
        """.trimIndent()
    }
    return """
        uniform shader content;
        uniform float2 size;
        layout(color) uniform half4 tint;
        uniform float tintIntensity;
        uniform float maskFadeStart;
        uniform float maskFadeEnd;
        uniform float tintFadeStart;
        uniform float tintFadeEnd;

        half4 main(float2 coord) {
            float x = coord.x / size.x;
            float y = coord.y / size.y;
            $maskExpression
            return mix(content.eval(coord) * mask, tint * tintMask, tintIntensity);
        }
    """.trimIndent()
}
