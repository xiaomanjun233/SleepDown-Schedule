package com.itos.coui.effect

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.RenderEffect as ComposeRenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect
import com.itos.coui.backdrop.CouiBackdrop
import com.itos.coui.backdrop.drawCouiBackdrop
import kotlin.math.ceil

/** Standalone foreground caustic over an opaque SDR backdrop. Lengths are physical pixels. */
@RequiresApi(34)
@Composable
fun Modifier.drawCouiCaustic(
    backdrop: CouiBackdrop,
    cornerRadius: (Size) -> Float?,
    shadow: CouiFloatingShadow,
): Modifier {
    val effect = remember(shadow) { CouiCausticEffect(shadow) }
    return drawCouiBackdrop(backdrop, shape = null, effects = {
        padding = effect.padding
        renderEffect = effect.forBounds(size, cornerRadius(size))
    })
}

@RequiresApi(34)
private class CouiCausticEffect(shadow: CouiFloatingShadow) {
    private val color = shadow.causticColor
    private val layout = shadow.layout
    val padding = ceil(maxOf(0f, -layout.left, -layout.top, layout.right, layout.bottom))
    private val optics = floatArrayOf(color.red, color.green, color.blue, color.alpha,
        layout.left, layout.top, layout.right, layout.bottom, shadow.sdf,
        shadow.power, shadow.powerMix, shadow.speed, 0.45f, 0.6f)
    private val shader by lazy {
        RuntimeShader(CouiCausticShader).apply {
            setIntUniform("u_foregroundComposition", 1)
            setFloatUniform("u_offset", -padding, -padding)
            setFloatUniform("u_invScale", 1f, 1f)
            setFloatUniform("u_pyrolysisArray", optics)
        }
    }
    private var lastSize = Size.Unspecified
    private var lastRadius = Float.NaN
    private var effect: ComposeRenderEffect? = null

    fun forBounds(size: Size, radius: Float?): ComposeRenderEffect? {
        // An unrecognized outline has no caustic. Never pass an unfiltered rectangular sample.
        val radiusPx = radius ?: -1f
        if (size != lastSize || radiusPx != lastRadius) {
            lastSize = size
            lastRadius = radiusPx
            shader.setFloatUniform("u_commonArray", floatArrayOf(0f, 0f, 0f, 0f, size.width,
                size.height, radiusPx.coerceAtLeast(0f), 0f, 0f, 0f, 0f, 0f))
            shader.setFloatUniform("u_pyrolysisArray", optics.copyOf().also { if (radius == null) it[3] = 0f })
            effect = RenderEffect.createRuntimeShaderEffect(shader, "u_originShader").asComposeRenderEffect()
        }
        return effect
    }
}
