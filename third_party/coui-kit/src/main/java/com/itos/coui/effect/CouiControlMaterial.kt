package com.itos.coui.effect

import android.graphics.*
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.toArgb

/** 浮动表面和控制表面共用两层混色顺序；只作用于采样，不处理前景。 */
@RequiresApi(31)
fun couiControlMaterialEffect(material: CouiFloatingMaterial): RenderEffect {
    val brightness = material.sampleBrightness.coerceIn(0f, 1f)
    val exposure = if (brightness < 1f) RenderEffect.createColorFilterEffect(
        ColorMatrixColorFilter(ColorMatrix().apply { setScale(brightness, brightness, brightness, 1f) })) else null
    val sampled = when {
        material.blurRadius == 0f -> exposure ?: RenderEffect.createOffsetEffect(0f, 0f)
        exposure != null -> RenderEffect.createBlurEffect(material.blurRadius, material.blurRadius, exposure, Shader.TileMode.MIRROR)
        else -> RenderEffect.createBlurEffect(material.blurRadius, material.blurRadius, Shader.TileMode.MIRROR)
    }
    val first = RenderEffect.createColorFilterEffect(
        BlendModeColorFilter(material.firstColor.toArgb(), material.firstBlendMode), sampled)
    return RenderEffect.createColorFilterEffect(
        BlendModeColorFilter(material.secondColor.toArgb(), material.secondBlendMode), first)
}
