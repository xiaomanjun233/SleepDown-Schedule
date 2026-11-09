package com.xiaomanjun.sleepdownschedule.glass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import android.graphics.BlendMode
import com.itos.coui.floatingactionbutton.CouiFabDefaults
import com.itos.coui.topappbar.CouiTopAppBarButtonDefaults
import com.itos.coui.segmentbutton.CouiSegmentedControlDefaults
import com.itos.coui.effect.CouiFloatingMaterial
import com.itos.coui.effect.couiControlMaterialEffect
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
    val material = remember(dark) { CouiSegmentedControlDefaults.material(dark) }
    return couiControlMaterial(backdrop, CouiCapsuleShape, material)
}

/** AllThemeButton samples behind the shell with a 30px mirror blur and no color filters. */
@Composable
internal fun couiIlluminatedCapsuleMaterial(backdrop: Backdrop, shape: Shape): Modifier {
    val material = remember {
        CouiFloatingMaterial(
            firstColor = Color.Transparent, firstBlendMode = BlendMode.SRC_OVER,
            secondColor = Color.Transparent, secondBlendMode = BlendMode.SRC_OVER,
            edgeAlpha = 0f, innerGlowAlpha = 0f, blurRadius = 30f,
        )
    }
    return couiControlMaterial(backdrop, shape, material)
}

@Composable
private fun couiControlMaterial(backdrop: Backdrop?, shape: Shape, material: CouiFloatingMaterial): Modifier {
    val effect = remember(material) { couiControlMaterialEffect(material).asComposeRenderEffect() }
    return Modifier.drawBackdrop(checkNotNull(backdrop), shape = { shape }, effects = {
        padding = material.blurRadius
        renderEffect = effect
    }, highlight = null, shadow = null)
}
