package com.itos.coui.effect

import android.graphics.*
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.toArgb

/** Apply the original two tint operations to sampled pixels within one draw. */
@RequiresApi(29)
class CouiControlMaterialShader(private val material: CouiFloatingMaterial) : (Shader) -> Shader {
    private val first = material.firstColor.toArgb().let {
        LinearGradient(0f, 0f, 1f, 0f, it, it, Shader.TileMode.CLAMP)
    }
    private val second = material.secondColor.toArgb().let {
        LinearGradient(0f, 0f, 1f, 0f, it, it, Shader.TileMode.CLAMP)
    }
    private var input: Shader? = null
    private var output: Shader? = null

    override fun invoke(source: Shader): Shader {
        if (input !== source) {
            input = source
            output = ComposeShader(ComposeShader(source, first, material.firstBlendMode),
                second, material.secondBlendMode)
        }
        return checkNotNull(output)
    }
}

/** 浮动表面和控制表面共用两层混色顺序；只作用于采样，不处理前景。 */
@RequiresApi(31)
fun couiControlMaterialEffect(material: CouiFloatingMaterial): RenderEffect =
    couiControlMaterialEffectOrNull(material) ?: RenderEffect.createOffsetEffect(0f, 0f)

/**
 * 构造控件材质的 [RenderEffect]；跳过 Android 12 Skia 会优化成空指针的透明混色层和零模糊。
 */
@RequiresApi(31)
fun couiControlMaterialEffectOrNull(material: CouiFloatingMaterial): RenderEffect? {
    val brightness = material.sampleBrightness.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 1f
    val exposure = if (brightness <= 0.999f) RenderEffect.createColorFilterEffect(
        ColorMatrixColorFilter(ColorMatrix().apply { setScale(brightness, brightness, brightness, 1f) })) else null
    val radius = material.blurRadius.takeIf { it.isFinite() && it > 0f } ?: 0f
    var current: RenderEffect? = when {
        radius == 0f -> exposure
        exposure != null -> RenderEffect.createBlurEffect(radius, radius, exposure, Shader.TileMode.MIRROR)
        else -> RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.MIRROR)
    }
    current = chainColorFilter(current, material.firstColor.toArgb(), material.firstBlendMode)
    return chainColorFilter(current, material.secondColor.toArgb(), material.secondBlendMode)
}

@RequiresApi(31)
private fun chainColorFilter(input: RenderEffect?, argb: Int, blendMode: BlendMode): RenderEffect? {
    if (isNoOpBlendFilter(argb, blendMode)) return input
    val filter = BlendModeColorFilter(argb, blendMode)
    return if (input != null) {
        RenderEffect.createColorFilterEffect(filter, input)
    } else {
        RenderEffect.createColorFilterEffect(filter)
    }
}

@RequiresApi(29)
internal fun isNoOpBlendFilter(argb: Int, blendMode: BlendMode): Boolean {
    if (blendMode == BlendMode.DST) return true
    if ((argb ushr 24) != 0) return false
    return when (blendMode) {
        BlendMode.CLEAR,
        BlendMode.SRC,
        BlendMode.SRC_IN,
        BlendMode.DST_IN,
        BlendMode.SRC_OUT,
        BlendMode.DST_ATOP,
        BlendMode.MODULATE -> false
        else -> true
    }
}
