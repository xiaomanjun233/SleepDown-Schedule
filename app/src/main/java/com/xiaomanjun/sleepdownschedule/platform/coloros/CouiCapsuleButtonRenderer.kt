package com.xiaomanjun.sleepdownschedule.platform.coloros

import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LocalNativeDialogSurface
import com.itos.coui.button.CouiButtonDefaults
import com.itos.coui.button.CouiButtonStyle
import com.itos.coui.button.CouiCapsuleButton
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.NativeCapsuleButtonRequest

/**
 * COUI-Kit 按钮兼容入口。普通胶囊按钮走 Kit 的填色、轮廓、按压和跟手光；
 * 原生投光/卡片光按钮仍返回 false，由 ColorOS 原生或液态实现继续处理。
 */
@Composable
internal fun CouiCapsuleButtonRenderer(
    request: NativeCapsuleButtonRequest,
    content: @Composable RowScope.() -> Unit,
): Boolean {
    // 输入框复合按钮有自定义命中区域；Kit 普通 clickable 不能接管其整块输入区。
    if (request.lightBackdrop || request.cardLightShadow || request.customGestureRegion) return false
    val primary = request.primaryColor.isSpecified
    val accent = if (primary) request.primaryColor.copy(alpha = 1f) else Color.Unspecified
    val shape = request.shape ?: CouiButtonDefaults.shape
    val sampled = (request.backdrop != null && !LocalNativeDialogSurface.current) &&
        !com.xiaomanjun.sleepdownschedule.core.performance.AppMaterialPreferences.isSuperPerformance
    val colors = if (primary) CouiButtonDefaults.primaryColors(accent)
        else CouiButtonDefaults.secondaryColors(request.dark, material = true)
    CouiCapsuleButton(
        modifier = request.modifier,
        onClick = request.onClick,
        dark = request.dark,
        enabled = request.enabled,
        style = if (primary) CouiButtonStyle.Primary else CouiButtonStyle.Secondary,
        colors = if (sampled && (request.enabled || request.preserveMaterialWhenDisabled)) colors.copy(surface = Color.Transparent) else colors,
        shape = shape,
        backgroundModifier = if (sampled && (request.enabled || request.preserveMaterialWhenDisabled))
            com.xiaomanjun.sleepdownschedule.glass.couiCapsuleMaterial(
                request.backdrop, shape, request.dark, accent)
            else Modifier,
        height = request.height,
        contentPadding = request.contentPadding,
        preserveMaterialWhenDisabled = request.preserveMaterialWhenDisabled,
        surfaceSheen = request.surfaceSheen,
        content = content,
    )
    return true
}
