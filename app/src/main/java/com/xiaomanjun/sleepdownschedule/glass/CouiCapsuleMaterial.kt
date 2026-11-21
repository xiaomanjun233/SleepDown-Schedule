package com.xiaomanjun.sleepdownschedule.glass

import android.graphics.Paint
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.nativeCanvas
import android.graphics.BlendMode
import com.itos.coui.floatingactionbutton.CouiFabDefaults
import com.itos.coui.topappbar.CouiTopAppBarButtonDefaults
import com.itos.coui.segmentbutton.CouiSegmentedControlDefaults
import com.itos.coui.effect.CouiFloatingMaterial
import com.itos.coui.effect.CouiContour
import com.itos.coui.effect.LocalCouiContourGlowEnabled
import com.itos.coui.effect.couiControlMaterialEffectOrNull
import com.itos.coui.shape.CouiCapsuleShape
import com.itos.coui.shape.CouiShape
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.withTransformCompensation
import com.kyant.backdrop.drawBackdrop

/** 普通浮动按钮只消费调用方背景域；Dialog 内的操作沿用 Kit 原生填色，不追加采样。 */
@Composable
internal fun couiCapsuleMaterial(backdrop: Backdrop?, shape: Shape, dark: Boolean, accent: Color): Modifier {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || backdrop == null) return Modifier
    val material = remember(dark, accent) {
        if (accent != Color.Unspecified) CouiFabDefaults.translucentMaterial(accent, dark)
            else CouiTopAppBarButtonDefaults.systemMaterial(dark, capsule = true).copy(blurRadius = 7f)
    }
    return couiControlMaterial(backdrop, shape, material)
}

/** COUIButton 的 TYPE_CONTENT_TRANSPARENT_BUTTON，描边由按钮本身负责。 */
@RequiresApi(31)
@Composable
internal fun couiTransparentButtonMaterial(backdrop: Backdrop, shape: Shape): Modifier {
    retainGlassSources(backdrop)
    val material = remember {
        CouiFloatingMaterial(
            firstColor = Color(0x3F333333), firstBlendMode = BlendMode.OVERLAY,
            secondColor = Color(0x3FFFFFFF), secondBlendMode = BlendMode.LUMINOSITY,
            blurRadius = 25f, edgeAlpha = 0f, innerGlowAlpha = 0f,
        )
    }
    return couiControlMaterial(backdrop, shape, material)
}

/** OTA MaterialLoadingView 的底卡采样配方，外框与底色由宿主绘制。 */
@RequiresApi(31)
@Composable
internal fun couiUpdateCardMaterial(backdrop: Backdrop, shape: Shape): Modifier {
    retainGlassSources(backdrop)
    val material = remember {
        CouiFloatingMaterial(
            firstColor = Color(0x802F2F2F), firstBlendMode = BlendMode.LUMINOSITY,
            secondColor = Color(0xB32E2E2E), secondBlendMode = BlendMode.OVERLAY,
            blurRadius = 20f, edgeAlpha = 0f, innerGlowAlpha = 0f,
        )
    }
    return couiControlMaterial(backdrop, shape, material)
}

@Composable
internal fun couiUpdateCardOutline(shape: CouiShape): Modifier {
    if (Build.VERSION.SDK_INT < 33 || !LocalCouiContourGlowEnabled.current) return Modifier
    val contour = remember { CouiContour() }
    val paint = remember { Paint(Paint.ANTI_ALIAS_FLAG) }
    return Modifier.drawWithCache {
        if (size.width > 0f && size.height > 0f) {
            paint.shader = contour.shader(size, density, shape.radius.toPx(), shape.cornerWeight,
                Color.Transparent, innerGlowAlpha = .05f, lineAlpha = .5f, effectAlpha = 1f)
        }
        onDrawBehind {
            val canvas = drawContext.canvas.nativeCanvas
            if (canvas.isHardwareAccelerated && size.width > 0f && size.height > 0f) {
                canvas.drawRect(0f, 0f, size.width, size.height, paint)
            }
        }
    }
}

@Composable
internal fun couiSegmentMaterial(backdrop: Backdrop?, dark: Boolean): Modifier {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || backdrop == null) return Modifier
    val material = remember(dark) { CouiSegmentedControlDefaults.material(dark) }
    return couiControlMaterial(backdrop, CouiCapsuleShape, material)
}

/** AllThemeButton samples behind the shell with a 30px mirror blur and no color filters. */
@Composable
internal fun couiIlluminatedCapsuleMaterial(backdrop: Backdrop, shape: Shape): Modifier {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return Modifier
    val material = remember {
        CouiFloatingMaterial(
            firstColor = Color.Transparent, firstBlendMode = BlendMode.SRC_OVER,
            secondColor = Color.Transparent, secondBlendMode = BlendMode.SRC_OVER,
            edgeAlpha = 0f, innerGlowAlpha = 0f, blurRadius = 30f,
        )
    }
    return couiControlMaterial(backdrop, shape, material)
}

@RequiresApi(31)
@Composable
private fun couiControlMaterial(backdrop: Backdrop, shape: Shape, material: CouiFloatingMaterial): Modifier {
    val effect = remember(material) {
        couiControlMaterialEffectOrNull(material)?.asComposeRenderEffect()
    } ?: return Modifier
    val source = remember(backdrop) {
        if (backdrop is LayerBackdrop) backdrop.withTransformCompensation() else backdrop
    }
    return Modifier.drawBackdrop(source, shape = { shape }, effects = {
        padding = material.blurRadius
        renderEffect = effect
    }, highlight = null, shadow = null)
}
