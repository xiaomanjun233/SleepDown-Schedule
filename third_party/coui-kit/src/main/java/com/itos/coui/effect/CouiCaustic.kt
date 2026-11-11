package com.itos.coui.effect

import android.graphics.LinearGradient
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RenderEffect as ComposeRenderEffect
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import com.itos.coui.backdrop.CouiBackdrop
import com.itos.coui.backdrop.CouiSamplingCoordinates
import com.itos.coui.backdrop.drawCouiBackdrop
import kotlin.math.ceil

/** Standalone foreground caustic over an opaque SDR backdrop. Lengths are physical pixels. */
@RequiresApi(34)
@Composable
fun Modifier.drawCouiCaustic(
    backdrop: CouiBackdrop,
    cornerRadius: (Size) -> Float?,
    shadow: CouiFloatingShadow,
    sampleScale: Float = 1f,
): Modifier {
    val scale = sampleScale.coerceIn(0.125f, 1f)
    val effect = remember(shadow, scale) { CouiCausticEffect(shadow, scale) }
    return drawCouiBackdrop(backdrop, shape = null, effects = {
        padding = effect.padding
        this.sampleScale = scale
        renderEffect = effect.forBounds(size, cornerRadius(size))
    })
}

/** A uniform backing can feed the same optical shader without a sampled texture. */
@RequiresApi(34)
@Composable
fun Modifier.drawCouiCaustic(
    backdropColor: Color,
    cornerRadius: (Size) -> Float?,
    shadow: CouiFloatingShadow,
): Modifier {
    val origin = remember(backdropColor) {
        val argb = backdropColor.toArgb()
        LinearGradient(0f, 0f, 1f, 0f, argb, argb, Shader.TileMode.CLAMP)
    }
    val effect = remember(shadow) { CouiCausticEffect(shadow, 1f) }
    return drawWithCache {
        val radius = cornerRadius(size) ?: return@drawWithCache onDrawBehind {}
        val shader = effect.shaderForBounds(size, radius)
        shader.setInputShader("u_originShader", origin)
        val brush = ShaderBrush(shader)
        val padding = effect.padding
        val bounds = Size(size.width + 2 * padding, size.height + 2 * padding)
        onDrawBehind {
            translate(-padding, -padding) { drawRect(brush, size = bounds) }
        }
    }
}

/**
 * A prepared texture supplied by the scene. Padding is part of the sample coordinates;
 * projection and material body therefore share one mapping and one set of source pixels.
 */
@RequiresApi(34)
@Composable
fun Modifier.drawCouiCaustic(
    sample: (LayoutCoordinates, Float) -> Shader?,
    redraw: () -> Any?,
    cornerRadius: (Size) -> Float?,
    shadow: CouiFloatingShadow,
    programs: CouiCausticPrograms,
): Modifier {
    val coordinates = remember { CouiSamplingCoordinates() }
    val latestSample = rememberUpdatedState(sample)
    val latestRedraw = rememberUpdatedState(redraw)
    return onGloballyPositioned(coordinates::update).drawWithCache {
        val radius = cornerRadius(size) ?: return@drawWithCache onDrawBehind {}
        val effect = programs.forBounds(shadow, size, radius)
        val shader = effect.shaderForBounds(size, radius)
        val brush = ShaderBrush(shader)
        val padding = effect.padding
        val bounds = Size(size.width + 2 * padding, size.height + 2 * padding)
        onDrawBehind {
            latestRedraw.value()
            val target = coordinates.coordinates?.takeIf { it.isAttached } ?: return@onDrawBehind
            val texture = latestSample.value(target, padding) ?: return@onDrawBehind
            shader.setInputShader("u_originShader", texture)
            translate(-padding, -padding) { drawRect(brush, size = bounds) }
        }
    }
}

/** The owner is the sampled scene, not each card; the existing optical formula is unchanged. */
@RequiresApi(34)
class CouiCausticPrograms {
    private data class Key(val shadow: CouiFloatingShadow, val size: Size, val radius: Float)
    private val programs = linkedMapOf<Key, CouiCausticEffect>()
    internal fun forBounds(shadow: CouiFloatingShadow, size: Size, radius: Float): CouiCausticEffect =
        programs.getOrPut(Key(shadow, size, radius)) {
            if (programs.size == 64) programs.remove(programs.keys.first())
            CouiCausticEffect(shadow, 1f)
        }
}

@RequiresApi(34)
internal class CouiCausticEffect(
    shadow: CouiFloatingShadow,
    private val scale: Float,
    private val program: String = CouiCausticShader,
) {
    private val color = shadow.causticColor
    private val layout = shadow.layout
    val padding = ceil(maxOf(0f, -layout.left, -layout.top, layout.right, layout.bottom) * scale)
    private val optics = floatArrayOf(color.red, color.green, color.blue, color.alpha,
        layout.left * scale, layout.top * scale, layout.right * scale, layout.bottom * scale, shadow.sdf * scale,
        shadow.power, shadow.powerMix, shadow.speed, 0.45f, 0.6f)
    private val shader by lazy {
        RuntimeShader(program).apply {
            setIntUniform("u_foregroundComposition", 1)
            setFloatUniform("u_offset", -padding, -padding)
            setFloatUniform("u_invScale", 1f, 1f)
            setFloatUniform("u_pyrolysisArray", optics)
        }
    }
    private var lastSize = Size.Unspecified
    private var lastRadius = Float.NaN
    private var effect: ComposeRenderEffect? = null

    fun shaderForBounds(size: Size, radius: Float?): RuntimeShader {
        // An unrecognized outline has no caustic. Never pass an unfiltered rectangular sample.
        val radiusPx = radius ?: -1f
        if (size != lastSize || radiusPx != lastRadius) {
            lastSize = size
            lastRadius = radiusPx
            shader.setFloatUniform("u_commonArray", floatArrayOf(0f, 0f, 0f, 0f, size.width * scale,
                size.height * scale, radiusPx.coerceAtLeast(0f) * scale, 0f, 0f, 0f, 0f, 0f))
            shader.setFloatUniform("u_pyrolysisArray", optics.copyOf().also { if (radius == null) it[3] = 0f })
            effect = null
        }
        return shader
    }

    fun forBounds(size: Size, radius: Float?): ComposeRenderEffect {
        val runtimeShader = shaderForBounds(size, radius)
        return effect ?: RenderEffect.createRuntimeShaderEffect(runtimeShader, "u_originShader")
            .asComposeRenderEffect().also { effect = it }
    }
}
