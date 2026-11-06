package com.xiaomanjun.sleepdownschedule.platform.coloros

import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.isSpecified
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
    if (request.lightBackdrop || request.cardLightShadow) return false
    val primary = request.primaryColor.isSpecified
    CouiCapsuleButton(
        modifier = request.modifier,
        onClick = request.onClick,
        dark = request.dark,
        enabled = request.enabled,
        style = if (primary) CouiButtonStyle.Primary else CouiButtonStyle.Secondary,
        colors = if (primary) CouiButtonDefaults.primaryColors(request.primaryColor)
        else CouiButtonDefaults.secondaryColors(request.dark, material = true),
        height = request.height,
        contentPadding = request.contentPadding,
        preserveMaterialWhenDisabled = request.preserveMaterialWhenDisabled,
        surfaceSheen = request.surfaceSheen,
        content = content,
    )
    return true
}
