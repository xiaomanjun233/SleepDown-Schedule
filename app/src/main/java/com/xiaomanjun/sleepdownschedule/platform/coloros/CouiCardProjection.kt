package com.xiaomanjun.sleepdownschedule.platform.coloros

import android.content.res.Configuration
import android.graphics.BitmapShader
import android.graphics.Paint
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.NativeCardLightRequest
import kotlin.math.cos
import kotlin.math.sin

// WallpaperShadowView resolves the shape, then shadow_card.coz projects the card and blurs
// it in two quarter-resolution buffers. The runtime View overrides both kernel limits to 80.
private data class ProjectionLayout(
    val widthDp: Int,
    val cardWidth: Float,
    val angle: Double,
    val near: Float,
    val tailAlpha: Float,
    val ovalStartWidth: Float,
    val ovalWidth: Float,
    val ovalHeight: Float,
    val initialOvalEnd: Float,
    val speed: Float,
    val contrast: Float = 1f,
)

@RequiresApi(34)
@Composable
internal fun CouiCardProjection(request: NativeCardLightRequest): Boolean {
    val bitmap = request.bitmap
    if (bitmap == null || bitmap.isRecycled) return true
    val config = LocalConfiguration.current
    val layout = when {
        config.smallestScreenWidthDp >= 600 && config.orientation == Configuration.ORIENTATION_LANDSCAPE ->
            ProjectionLayout(820, 1085f, 85.0, .636f, .05f, 200f, 700f, 350f, 100f, 2f)
        config.smallestScreenWidthDp >= 600 ->
            ProjectionLayout(620, 616f, 96.0, .525f, .3f, 150f, 350f, 350f, 100f, 1.6f)
        config.screenWidthDp >= 600 ->
            ProjectionLayout(660, 614f, 96.0, .52f, .05f, 200f, 300f, 500f, 0f, 1.6f, 1.05f)
        else -> ProjectionLayout(600, 550f, 95.5, .523f, .05f, 200f, 300f, 400f, 0f, 2f)
    }
    val density = LocalDensity.current
    val bandWidth = with(density) { layout.widthDp.dp.toPx() }
    val bandHeight = with(density) { 136.dp.toPx() }
    val buffer = IntSize((bandWidth / 4f).toInt().coerceAtLeast(1), (bandHeight / 4f).toInt().coerceAtLeast(1))
    val source = remember(bitmap) {
        BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
            setFilterMode(BitmapShader.FILTER_MODE_LINEAR)
        }
    }
    val project = remember(source, layout, buffer) {
        RuntimeShader(CardProjectionShader).apply {
            setInputShader("card", source)
            setFloatUniform("textureSize", bitmap.width.toFloat(), bitmap.height.toFloat())
            setFloatUniform("bufferSize", buffer.width.toFloat(), buffer.height.toFloat())
            val angle = Math.toRadians(layout.angle)
            val sine = 10f / layout.near * (408f / 1080f) * sin(angle).toFloat()
            val slope = 4080f * cos(angle).toFloat() + 200f * sine
            setFloatUniform("perspective", sine, slope, layout.cardWidth, 408f)
            setFloatUniform("bandWidth", layout.widthDp * 3f)
            setFloatUniform("oval", layout.ovalStartWidth, layout.ovalWidth, layout.ovalHeight, layout.initialOvalEnd)
            setFloatUniform("tailAlpha", layout.tailAlpha)
        }
    }
    val paint = remember(project) { Paint().apply { shader = project } }
    val projectionLayer = rememberGraphicsLayer()
    val horizontalLayer = rememberGraphicsLayer()
    val verticalLayer = rememberGraphicsLayer()
    val horizontal = remember(layout, density, buffer) {
        projectionBlurEffect(buffer, layout.speed, density.density / 6f, 0f)
    }
    val vertical = remember(layout, density, buffer) {
        projectionBlurEffect(buffer, layout.speed, 0f, density.density / 6f)
    }
    val composite = remember(layout) { RuntimeShader(CardProjectionCompositeShader) }
    val current = rememberUpdatedState(request)
    Canvas(request.modifier) {
        val value = current.value
        val card = value.cardBounds()
        val opacity = value.strength().coerceIn(0f, 1f)
        if (card.isEmpty || opacity <= 0f || bitmap.isRecycled || !drawContext.canvas.nativeCanvas.isHardwareAccelerated) return@Canvas
        val depth = value.projectionDepth?.toPx() ?: bandHeight
        val bounds = if (value.fitToCardWidth) cardLightDrawingBounds(card, value.receiverBounds?.invoke(), depth,
            Rect(0f, 0f, size.width, size.height)) else null
        if (bounds?.isEmpty == true || depth <= 0f) return@Canvas
        val stockWidth = layout.cardWidth * this.density / 3f
        val scale = if (bounds != null) cardLightHorizontalScale(card.width, stockWidth, bandWidth, bounds.width) else 1f
        val width = bandWidth * scale
        val left = card.center.x - width / 2f
        val xScale = width / buffer.width
        val yScale = depth / buffer.height
        project.setFloatUniform("reveal", value.revealProgress().coerceIn(0f, 1f))
        project.setFloatUniform("opacity", opacity)
        project.setFloatUniform("corner", value.cornerRadius.toPx() / scale * 3f / this.density)
        projectionLayer.renderEffect = horizontal
        projectionLayer.record(size = buffer) {
            drawContext.canvas.nativeCanvas.drawRect(0f, 0f, size.width, size.height, paint)
        }
        horizontalLayer.renderEffect = vertical
        horizontalLayer.record(size = buffer) { drawLayer(projectionLayer) }
        composite.setFloatUniform("contrast", layout.contrast)
        composite.setFloatUniform("mask", if (bounds == null) -1f else (bounds.left - left) / xScale,
            if (bounds == null) -1f else (bounds.right - left) / xScale)
        verticalLayer.renderEffect = RenderEffect.createRuntimeShaderEffect(composite, "light").asComposeRenderEffect()
        verticalLayer.record(size = buffer) { drawLayer(horizontalLayer) }
        withTransform({
            translate(left, card.bottom)
            scale(xScale, yScale, Offset.Zero)
        }) { drawLayer(verticalLayer) }
    }
    return true
}

@RequiresApi(33)
private fun projectionBlurEffect(size: IntSize, speed: Float, dx: Float, dy: Float) =
    RuntimeShader(CardProjectionBlurShader).apply {
        setFloatUniform("bufferSize", size.width.toFloat(), size.height.toFloat())
        setFloatUniform("blurStep", dx, dy)
        setFloatUniform("speed", speed)
    }.let { RenderEffect.createRuntimeShaderEffect(it, "previousPass").asComposeRenderEffect() }

private const val CardProjectionShader = """
    uniform shader card;
    uniform float2 textureSize, bufferSize;
    uniform float4 perspective, oval;
    uniform float bandWidth, tailAlpha, reveal, opacity, corner;

    half4 main(float2 point) {
        float2 p = point / bufferSize;
        float y = p.y * perspective.w;
        // Invert the stock quad projection with the near plane and rotation already folded in.
        float v = y * 10.0 / (perspective.y + y * perspective.x);
        float u = 0.5 + (p.x - 0.5) * bandWidth * (10.0 - perspective.x * v) / (10.0 * perspective.z);
        if (u < 0.0 || u > 1.0 || v < 0.0 || v > 1.0) return half4(0);
        float2 uv = float2(u, v);
        float2 cardSize = perspective.zw;
        float2 pos = float2((u - 0.5) * cardSize.x, v * cardSize.y - mix(-150.0, 100.0, reveal));
        float2 axes = float2(mix(oval.x, oval.y, reveal), mix(100.0, oval.z, reveal));
        float k = length(pos / axes);
        float distance = k * (k - 1.0) / max(0.00001, length(pos / (axes * axes)));
        float alpha = opacity * mix(1.0, tailAlpha, v) *
            (1.0 - smoothstep(mix(-500.0, 0.0, reveal), mix(oval.w, 50.0, reveal), distance));
        float2 q = abs((uv - 0.5) * cardSize) - cardSize * 0.5 + corner;
        float rounded = min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - corner;
        if (corner > 0.001) alpha *= 1.0 - smoothstep(-1.0, 0.0, rounded);
        float4 pixel = card.eval(uv * textureSize);
        if (pixel.a <= 0.0 || alpha <= 0.001) return half4(0);
        float3 rgb = pixel.rgb / pixel.a;
        rgb += step(0.25, dot(rgb, float3(0.2126, 0.7152, 0.0722))) * 0.1;
        rgb = clamp((rgb - 0.5) * mix(2.0, 1.0, reveal) + 0.5, 0.0, 1.0);
        alpha *= pixel.a;
        return half4(rgb * alpha, alpha);
    }
"""

private const val CardProjectionBlurShader = """
    uniform shader previousPass;
    uniform float2 bufferSize, blurStep;
    uniform float speed;
    half4 samplePass(float2 p) {
        if (any(lessThanEqual(p, float2(0))) || any(greaterThanEqual(p, bufferSize))) return half4(0);
        return previousPass.eval(p);
    }
    half4 main(float2 point) {
        float strength = smoothstep(-0.147, 1.0, point.y / bufferSize.y);
        int radius = int(80.0 * (1.0 - pow(1.0 - strength, speed)));
        if (radius <= 1) return samplePass(point);
        float sigma = float(radius) / 3.0;
        float weight = 0.39894228 / sigma;
        float4 result = float4(samplePass(point)) * weight;
        for (int i = 1; i <= 80; ++i) {
            if (i > radius) break;
            float f = float(i) / sigma;
            float2 delta = blurStep * float(i);
            result += (float4(samplePass(point + delta)) + float4(samplePass(point - delta))) * weight * exp(-0.5 * f * f);
        }
        return half4(result);
    }
"""

private const val CardProjectionCompositeShader = """
    uniform shader light;
    uniform float contrast;
    uniform float2 mask;
    half4 main(float2 point) {
        float4 color = light.eval(point);
        float noise = fract(sin(dot(point.yx / 270.0, float2(79.1214, 78.233))) * 43760.5453);
        float n = (noise - 0.5) * 0.07 + 0.5;
        float3 rgb = clamp((color.rgb - 0.5) * contrast + 0.5, 0.0, 1.0);
        rgb = mix(rgb - (1.0 - 2.0 * n) * rgb * (1.0 - rgb),
            rgb + (2.0 * n - 1.0) * (sqrt(rgb) - rgb), step(0.5, n));
        float fade = 1.0;
        if (mask.y > mask.x) {
            float fraction = (point.x - mask.x) / (mask.y - mask.x);
            fade = min(clamp(fraction / 0.15, 0.0, 1.0), clamp((1.0 - fraction) / 0.15, 0.0, 1.0));
        }
        return half4(clamp(rgb, 0.0, color.a), color.a) * fade;
    }
"""
