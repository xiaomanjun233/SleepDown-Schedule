package com.xiaomanjun.sleepdownschedule.feature.agent

import android.graphics.BlendMode
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas

/**
 * COUIIntentSeekBar's conic-light progress effect, ported from the ColorOS seek bar renderer.
 * The native drawer draws a 450px-tall, 300px-padded light layer around the track and blends it
 * with SCREEN. The light is two additive cones whose height falls off behind the progress head.
 */
internal interface AgentConicLightPainter {
    fun draw(
        canvas: Canvas,
        trackWidth: Float,
        rowHeight: Float,
        progress: Float,
        accent: Color,
        alphaReveal: Float,
        shapeReveal: Float
    )
}

@Composable
internal fun rememberAgentConicLightPainter(): AgentConicLightPainter? = remember {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        // A malformed or unsupported shader must not take down the assistant card.
        runCatching { AgentConicLightPainterApi33() }.getOrNull()
    } else {
        null
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class AgentConicLightPainterApi33 : AgentConicLightPainter {
    private val runtimeShader = RuntimeShader(ConicLightShader)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = runtimeShader
        blendMode = BlendMode.SCREEN
    }

    override fun draw(
        canvas: Canvas,
        trackWidth: Float,
        rowHeight: Float,
        progress: Float,
        accent: Color,
        alphaReveal: Float,
        shapeReveal: Float
    ) {
        if (trackWidth <= 0f || alphaReveal <= 0f) return
        val safeProgress = progress.coerceIn(0f, 1f)
        val reveal = shapeReveal.coerceIn(0f, 1f)
        // ConicLightEffectRenderer modulates the cone below 20% progress with a
        // cubic-bezier whose control points collapse to a linear ramp.
        val modulated = if (safeProgress > 0.2f) 1f else (safeProgress / 0.21f).coerceIn(0f, 1f)
        val targetHeight = 0.6f * modulated
        val targetFalloff = 0.6f * modulated
        // The shape spring starts from the OFF values (height 0.1, no falloff, no cone).
        val height = 0.1f + (targetHeight - 0.1f) * reveal
        val heightFalloff = targetFalloff * reveal
        val coneK = 0.2f * reveal
        val w0 = 0.008f * reveal
        val lightWidth = trackWidth + 2f * ConicLightHorizontalPadding
        val lightHeight = ConicLightHeight

        runtimeShader.setFloatUniform("u_resolution", lightWidth, lightHeight)
        runtimeShader.setFloatUniform(
            "u_startColor",
            accent.red * 255f,
            accent.green * 255f,
            accent.blue * 255f
        )
        runtimeShader.setFloatUniform(
            "u_endColor",
            accent.red * 255f,
            accent.green * 255f,
            accent.blue * 255f
        )
        runtimeShader.setFloatUniform(
            "u_progressHeightParams",
            safeProgress,
            trackWidth,
            height,
            heightFalloff
        )
        runtimeShader.setFloatUniform("u_basicParams", 3f, 1f, 2f, 0.2f)
        runtimeShader.setFloatUniform(
            "u_alphaParams",
            alphaReveal.coerceIn(0f, 1f),
            0.6f * alphaReveal.coerceIn(0f, 1f),
            w0,
            ConicLightSpotRadius
        )
        runtimeShader.setFloatUniform("u_coneParams", coneK, coneK, 2f, 1f)
        runtimeShader.setFloatUniform("u_miscParams", 1f, 0f, 0f, 0f)

        val native = canvas.nativeCanvas
        native.save()
        native.translate(-ConicLightHorizontalPadding, rowHeight / 2f - lightHeight / 2f)
        native.drawRect(0f, 0f, lightWidth, lightHeight, paint)
        native.restore()
    }
}

private const val ConicLightHeight = 450f
private const val ConicLightHorizontalPadding = 300f
private const val ConicLightSpotRadius = 500f

/** AGSL from com.coui.appcompat.seekbar.shader.ConicLightShaderParams. */
private const val ConicLightShader = """
         uniform float2 u_resolution;
         uniform float3 u_startColor;
         uniform float3 u_endColor;
         uniform float4 u_progressHeightParams;
         uniform float4 u_basicParams;
         uniform float4 u_alphaParams;
         uniform float4 u_coneParams;
         uniform float4 u_miscParams;

         const float TO_POINT = 0.003921;

         float3 blendPlusLighter(float3 base, float3 blend) {
             return min(float3(1.0), base + blend);
         }
         float4 getBloom(float2 q, float coneK, float alpha1, float2 fragCoord) {
             float u_height = u_progressHeightParams.z;
             float u_heightFalloff = u_progressHeightParams.w;
             float u_alpha = u_basicParams.y;
             float w0 = u_alphaParams.z;
             float u_spotRadius = u_alphaParams.w;
             float radialFalloff = u_coneParams.w;
             float axialFalloff = u_coneParams.z;
             float u_rectWidth = u_progressHeightParams.y;
             float u_process = u_progressHeightParams.x;

             float xp = max(-q.x, 0.0);
             float feather = u_spotRadius;
             float w0_px = w0 * feather;

             float wCone = coneK * xp + w0_px;
             float w = mix(w0_px, wCone, step(0.0, xp));

             float abs_y = abs(q.y);
             float d = abs_y - w;
             float edgeMask = 1.0 - smoothstep(0.0, feather * 2.0, d);

             float rNorm = abs_y / (w + 1e-4);
             float radial = exp(-radialFalloff * rNorm * rNorm);

             float xpNorm = xp / feather;
             float axial = exp(-axialFalloff * xpNorm);

             float maxHeight = feather * u_height;
             float heightFactor = clamp(xp / (maxHeight + 1e-4), 0.0, 1.0);
             float heightAttenuation = 1.0 - pow(heightFactor, u_heightFalloff);
             float cond = step(maxHeight, xp);
             float overDistance = (xp - maxHeight) / feather * cond;
             heightAttenuation *= exp(-overDistance * 3.0);

             float intensity = edgeMask * radial * heightAttenuation * axial;
             intensity *= smoothstep(0.0, 0.03 * feather, xp);

             float3 glowColor = mix(u_startColor, u_endColor, heightFactor) * TO_POINT;
             return half4(glowColor * intensity, intensity * alpha1 * u_alpha);
         }

         float max3(float3 v) {
             return max(max(v.r, v.g), v.b);
         }

         float4 premulForAdditiveBlend(float4 c) {
             c.rgb = clamp(c.rgb, 0.0, 1.0);
             c.a = clamp(max(c.a, max3(c.rgb)), 0.0, 1.0);
             return c;
         }

         half4 main(float2 fragCoord) {
             float u_process = u_progressHeightParams.x;
             float u_rectWidth = u_progressHeightParams.y;

             float u_alpha1 = u_alphaParams.x;
             float u_alpha2 = u_alphaParams.y;

             float coneK = u_coneParams.x;
             float second_coneK = u_coneParams.y;

             float u_lightBlendMode = u_miscParams.x;
             float is_RTL = u_miscParams.y;

             float2 pixelPos = fragCoord - u_resolution * 0.5;
             float spotX = (u_process - 0.5) * u_rectWidth;
             float2 q = pixelPos - float2(spotX, 0.0);

             q.x *= 1.0 - 2.0 * is_RTL;

             float4 blend_color = float4(0.0);
             float4 bloom_color = getBloom(q, coneK, u_alpha1, fragCoord);
             float4 bloom_color2 = getBloom(q, second_coneK, u_alpha2, fragCoord);

             float4 base = bloom_color;
             float4 blend = bloom_color2;
             blend_color.rgb = blendPlusLighter(base.rgb, blend.rgb);
             blend_color.a = base.a * (1.0 - blend.a) + blend.a;

             float percent = abs(blend.a - base.a) / blend_color.a;
             float mix_bg = step(blend.a, base.a) * percent;
             float mix_fg = step(base.a, blend.a) * percent;
             blend_color.rgb = mix(blend_color.rgb, base.rgb, mix_bg);
             blend_color.rgb = mix(blend_color.rgb, blend.rgb, mix_fg);

             float4 final_color = mix(blend_color, float4(0.0), step(0.0, q.x));

             return premulForAdditiveBlend(final_color);
         }
"""
