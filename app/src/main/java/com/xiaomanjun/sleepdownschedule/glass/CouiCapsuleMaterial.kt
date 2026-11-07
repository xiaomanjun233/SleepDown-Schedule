package com.xiaomanjun.sleepdownschedule.glass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import com.itos.coui.floatingactionbutton.CouiFabDefaults
import com.itos.coui.topappbar.CouiTopAppBarButtonDefaults
import com.itos.coui.effect.CouiFloatingMaterial
import com.itos.coui.effect.couiControlMaterialEffect
import android.graphics.BlendMode
import com.itos.coui.shape.CouiCapsuleShape
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop

/** 普通浮动按钮只消费调用方背景域；Dialog 内的操作沿用 Kit 原生填色，不追加采样。 */
@Composable
internal fun couiCapsuleMaterial(backdrop: Backdrop?, shape: Shape, dark: Boolean, accent: Color): Modifier {
    val material = remember(dark, accent) {
        if (accent != Color.Unspecified) CouiFabDefaults.translucentMaterial(accent, dark)
            else CouiTopAppBarButtonDefaults.systemMaterial(dark, capsule = true).copy(blurRadius = 7f)
    }
    return couiControlMaterial(backdrop, shape, material)
}

@Composable
internal fun couiSegmentMaterial(backdrop: Backdrop?, dark: Boolean): Modifier {
    val material = remember(dark) { CouiFloatingMaterial(
        firstColor = Color(if (dark) 0x7F7F7F7F else 0x7F333333),
        firstBlendMode = if (dark) BlendMode.OVERLAY else BlendMode.COLOR_DODGE,
        secondColor = Color(if (dark) 0x99262626 else 0x99EFEFEF), edgeAlpha = 0f, innerGlowAlpha = 0f,
        blurRadius = 25f) }
    return couiControlMaterial(backdrop, CouiCapsuleShape, material)
}

@Composable
private fun couiControlMaterial(backdrop: Backdrop?, shape: Shape, material: CouiFloatingMaterial): Modifier {
    val effect = remember(material) { couiControlMaterialEffect(material).asComposeRenderEffect() }
    return Modifier.drawBackdrop(checkNotNull(backdrop), shape = { shape }, effects = {
        padding = material.blurRadius
        renderEffect = effect
    }, highlight = null, shadow = null)
}
