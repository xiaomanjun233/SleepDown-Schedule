package com.xiaomanjun.sleepdownschedule.platform.coloros

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LocalControlDialogSurface
import com.itos.coui.button.CouiButtonDefaults
import com.itos.coui.button.CouiButtonStyle
import com.itos.coui.button.rememberCouiCapsuleMaterial
import androidx.compose.foundation.interaction.MutableInteractionSource
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.ButtonMaterialRequest
import com.xiaomanjun.sleepdownschedule.glass.couiIlluminatedCapsuleMaterial

/**
 * COUI-Kit 按钮兼容入口。普通胶囊按钮走 Kit 的填色、轮廓、按压和跟手光；
 * 受光按钮在相同下层源上采样，输入复合区仍由自身的手势 owner 处理。
 */
@Composable
internal fun rememberGlowButtonMaterial(
    request: ButtonMaterialRequest,
    interactionSource: MutableInteractionSource,
): Modifier? {
    // 输入框复合按钮有自定义命中区域；Kit 普通 clickable 不能接管其整块输入区。
    if (request.customGestureRegion) return null
    val primary = request.primaryColor.isSpecified
    val accent = if (primary) request.primaryColor.copy(alpha = 1f) else Color.Unspecified
    val shape = request.shape ?: CouiButtonDefaults.shape
    val sampled = (!primary || request.translucentAccent || request.lightBackdrop) &&
        (request.backdrop != null && !LocalControlDialogSurface.current) &&
        !com.xiaomanjun.sleepdownschedule.core.performance.AppMaterialPreferences.isSuperPerformance
    val colors = if (primary) CouiButtonDefaults.primaryColors(accent)
        else CouiButtonDefaults.secondaryColors(request.dark, material = true)
    return rememberCouiCapsuleMaterial(
        source = interactionSource,
        dark = request.dark,
        enabled = request.enabled,
        style = if (primary) CouiButtonStyle.Primary else CouiButtonStyle.Secondary,
        colors = if (sampled && (request.enabled || request.preserveMaterialWhenDisabled)) colors.copy(surface = Color.Transparent) else colors,
        shape = shape,
        backgroundModifier = if (sampled && (request.enabled || request.preserveMaterialWhenDisabled))
            if (request.lightBackdrop) couiIlluminatedCapsuleMaterial(checkNotNull(request.backdrop), shape)
            else com.xiaomanjun.sleepdownschedule.glass.couiCapsuleMaterial(
                request.backdrop, shape, request.dark, accent)
            else Modifier,
        preserveMaterialWhenDisabled = request.preserveMaterialWhenDisabled,
        surfaceSheen = request.surfaceSheen,
        underlayModifier = if (request.cardLightShadow) illuminatedButtonShadow() else Modifier,
    )
}

/** AllThemeButton 的独立底影按 208×44dp 原比例拟合，不依赖系统图片。 */
private fun illuminatedButtonShadow(): Modifier = Modifier.drawWithCache {
    val top = size.height * 0.97f
    val shadowSize = Size(size.width * 1.14f, size.height * 0.58f)
    val shadow = Brush.verticalGradient(
        0f to Color.Transparent,
        .08f to Color.Black.copy(alpha = .58f),
        .18f to Color.Black.copy(alpha = .17f),
        .44f to Color.Black.copy(alpha = .05f),
        .75f to Color.Black.copy(alpha = .025f),
        1f to Color.Transparent,
        startY = top,
        endY = top + shadowSize.height,
    )
    onDrawBehind {
        drawRoundRect(shadow, topLeft = Offset((size.width - shadowSize.width) / 2f, top),
            size = shadowSize, cornerRadius = CornerRadius(shadowSize.height / 2f))
    }
}
