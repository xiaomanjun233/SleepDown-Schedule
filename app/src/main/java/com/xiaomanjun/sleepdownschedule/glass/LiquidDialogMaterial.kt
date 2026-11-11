package com.xiaomanjun.sleepdownschedule.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.itos.coui.backdrop.CouiBackdrop
import com.itos.coui.dialog.DialogMaterialSkin
import com.kyant.backdrop.catalog.components.liquidButtonVisualTransform
import com.kyant.backdrop.catalog.utils.InteractiveHighlight

/** Liquid drawing in the shared dialog geometry; window, text and actions belong to the host. */
internal object LiquidDialogMaterial : DialogMaterialSkin {
    @Composable
    override fun surface(backdrop: CouiBackdrop?, shape: Shape, dark: Boolean,
        observeMotion: () -> Unit): Modifier {
        val tint = if (dark) Color(0xFF202127).copy(alpha = .72f)
            else Color(0xFFF2F4F8).copy(alpha = .72f)
        if (backdrop == null) return Modifier.background(tint, shape)
        val source = rememberGlassWindowBackdrop(backdrop)
        val rounded = rememberRoundedMaterialShape(shape,
            (shape as? com.itos.coui.shape.CouiShape)?.radius ?: com.itos.coui.dialog.CouiDialogDefaults.cornerRadius)
        val material = GlassMaterialSpec.dialog()
        val descriptor = rememberGlassSurfaceDescriptor("DialogSurface", GlassBackdropDomain.ChromeCombined,
            material.role)
        return Modifier.sleepDownGlassSurface(source, descriptor, material, { rounded },
            GlassEffectFrame(blur = 20.dp, lensHeight = material.lensHeight, lensAmount = material.lensAmount,
                useVibrancy = true, depthEffect = false,
                highlight = GlassHighlightFrame(GlassHighlightStyle.Default, .08f)),
            onDrawBackdrop = { sample -> observeMotion(); sample() },
            onDrawSurface = { drawRect(tint) }, fallbackColor = tint)
    }

    @Composable
    override fun button(backdrop: CouiBackdrop?, shape: Shape, dark: Boolean, enabled: Boolean,
        surface: Color, interaction: MutableInteractionSource): Modifier {
        val scope = rememberCoroutineScope()
        val press = remember(scope) { InteractiveHighlight(scope) }
        val base = if (enabled) Modifier.liquidButtonVisualTransform(press)
            .then(press.modifier).then(press.gestureModifier) else Modifier
        if (backdrop == null) return base.clip(shape).background(surface)
        val source = rememberGlassWindowBackdrop(backdrop)
        val rounded = rememberRoundedMaterialShape(shape, 999.dp)
        val material = GlassMaterialSpec.pill()
        val descriptor = rememberGlassSurfaceDescriptor("DialogButton", GlassBackdropDomain.ChromeCombined,
            material.role)
        return base.sleepDownGlassSurface(source, descriptor, material, { rounded },
            GlassEffectFrame(blur = 12.dp, lensHeight = 4.dp, lensAmount = 6.dp,
                useVibrancy = true, depthEffect = false),
            onDrawBackdrop = { sample -> press.pressProgress; press.offset; sample() },
            onDrawSurface = { drawRect(surface) }, fallbackColor = surface)
    }
}
