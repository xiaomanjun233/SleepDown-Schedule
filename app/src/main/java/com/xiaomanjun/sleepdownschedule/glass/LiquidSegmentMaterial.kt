package com.xiaomanjun.sleepdownschedule.glass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.itos.coui.shape.CouiCapsuleShape
import com.kyant.backdrop.Backdrop

/** The shared segment rail supplies bounds and press deformation; this is drawing only. */
@Composable
internal fun liquidSegmentMaterial(backdrop: Backdrop, dark: Boolean,
    indicator: Boolean, highContrast: Boolean): Modifier {
    val shape = rememberRoundedMaterialShape(CouiCapsuleShape, 999.dp)
    val material = remember(indicator, highContrast) {
        GlassMaterialSpec(role = GlassMaterialRole.Control,
            blur = if (indicator) 0.dp else if (highContrast) 7.dp else 4.dp,
            lensHeight = if (indicator) 10.dp else 24.dp,
            lensAmount = if (indicator) 14.dp else 24.dp,
            surfaceAlpha = if (indicator) .1f else .4f, borderAlpha = 0f,
            highlightAlpha = .07f, shadowAlpha = if (indicator) .05f else 0f,
            innerShadowAlpha = if (indicator) .08f else 0f,
            chromaticAberration = !highContrast, useVibrancy = !indicator)
    }
    val descriptor = rememberGlassSurfaceDescriptor(if (indicator) "SegmentIndicator" else "SegmentShell",
        GlassBackdropDomain.ChromeCombined, material.role)
    val tint = (if (dark) Color(0xFF26272D) else Color.White).copy(alpha =
        if (indicator) .1f else if (highContrast) .62f else .4f)
    return Modifier.sleepDownGlassSurface(backdrop, descriptor, material, { shape },
        GlassEffectFrame(blur = material.blur, lensHeight = material.lensHeight, lensAmount = material.lensAmount,
            useVibrancy = material.useVibrancy, chromaticAberration = material.chromaticAberration),
        onDrawSurface = { drawRect(tint) }, fallbackColor = tint)
}
