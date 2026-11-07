package com.xiaomanjun.sleepdownschedule.platform.coloros

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.itos.coui.button.CouiButtonColors
import com.itos.coui.button.CouiCapsuleButton
import com.itos.coui.shape.CouiShape
import com.itos.coui.theme.CouiTheme
import com.xiaomanjun.sleepdownschedule.core.ui.chrome.homeActionIconPainter

/** Set only inside the home top bar, so ordinary page icon buttons keep their existing route. */
internal val LocalCouiHomeTopBarActive = staticCompositionLocalOf { false }

/**
 * COUI 顶栏图标按钮：使用胶囊按钮同一套轮廓、按压、跟手光和禁用材质，
 * 但不额外创建顶栏采样源；顶栏自身仍由既有 HomeTopGradientBlur 处理。
 */
@Composable
internal fun CouiHomeIconButton(
    iconRes: Int,
    contentDescription: String,
    dark: Boolean,
    interactive: Boolean,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
    enabled: Boolean,
) {
    val scheme = CouiTheme.colorSchemeFor(dark)
    val content = if (selected) MaterialTheme.colorScheme.primary else scheme.onSurface
    val surface = if (dark) Color(0xCC262626) else Color(0x99F5F5F5)
    val colors = CouiButtonColors(
        surface = surface,
        content = content,
        disabledSurface = surface.copy(alpha = surface.alpha * 0.5f),
        disabledContent = content.copy(alpha = 0.38f),
    )
    CouiCapsuleButton(
        modifier = modifier,
        onClick = { if (interactive) onClick() },
        dark = dark,
        enabled = enabled,
        colors = colors,
        height = 42.dp,
        contentPadding = PaddingValues(0.dp),
        shape = CouiShape(999.dp, 2f),
        preserveMaterialWhenDisabled = true,
    ) {
        Icon(
            homeActionIconPainter(iconRes),
            contentDescription = contentDescription,
            modifier = Modifier.size(20.dp),
            tint = if (enabled) content else colors.disabledContent,
        )
    }
}
