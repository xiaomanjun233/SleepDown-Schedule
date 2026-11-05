package com.itos.coui.dialog

import android.os.Build
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.itos.coui.shape.CouiShape
import com.itos.coui.effect.CouiMaterialBlendMode
import com.itos.coui.theme.CouiTheme

/** 普通警告窗口的位置；大屏策略由宿主决定，不探测厂商私有设备特征。 */
enum class CouiDialogPlacement { Bottom, Center }

@Immutable
data class CouiDialogColors(
    val surface: Color,
    val title: Color,
    val message: Color,
    val action: Color,
    val disabledAction: Color,
    val destructive: Color,
    val dimAmount: Float,
) {
    init { require(dimAmount.isFinite() && dimAmount in 0f..1f) { "窗口遮罩强度必须在 0 到 1 之间" } }
}

/**
 * 弹窗材质。
 *
 * 有宿主窗口源且 API 33 时，源先按项目 Fast Kawase 模糊，再执行亮度混色和 blendMode。
 * 缺少窗口源或平台不支持时明确拒绝，避免用不同的窗口高斯与 SrcOver 叠色冒充同一材质。
 */
@Immutable
data class CouiDialogMaterial(
    val mix: Color,
    val blend: Color,
    /** 原厂 integer/coui_dialog_blur_radius；是 Kawase 引擎等级，不是像素或 sigma。 */
    val blurRadius: Int = 200,
    val blendMode: CouiMaterialBlendMode = CouiMaterialBlendMode.Overlay,
) {
    init { require(blurRadius == 0 || blurRadius >= 10) { "模糊等级必须为 0 或至少 10" } }
}

/** Settings 17.0.0 普通、非 Tiny、无推荐按钮分支; 默认样式对应关闭背景模糊的 BottomWarning。 */
object CouiDialogDefaults {
    val cornerRadius = 45.dp
    val shape: Shape = CouiShape(cornerRadius, 3f)
    val maxWidth = 392.dp
    val maxHeight = 632.dp
    val landscapeMaxHeight = 306.dp
    val horizontalMargin = 16.dp
    val bottomMargin = 32.dp
    internal val bottomMarginWithNavigationBar = 60.dp

    internal fun bottomPaddingPx(navigationBarHeight: Int, gestureNavigation: Boolean, density: Density): Int = with(density) {
        val base = bottomMargin.roundToPx()
        val adjusted = if (gestureNavigation) {
            if (navigationBarHeight > 0) (base - navigationBarHeight).coerceAtLeast(0) else 0
        } else {
            if (navigationBarHeight > 0) bottomMarginWithNavigationBar.roundToPx() - navigationBarHeight else base
        }
        // getEffectiveBottomMargin 的非正调整量仍回到基础留白。
        if (adjusted > 0) adjusted else base
    }
    val contentPadding = 24.dp
    val titleTop = 24.dp
    val titleBottom = 6.dp
    internal fun titleMaxHeight(screenHeightDp: Int) = when {
        screenHeightDp >= 560 -> 160.dp
        screenHeightDp >= 300 -> 120.dp
        else -> 80.dp
    }
    val messageBottom = 16.dp
    val messageTopWithoutTitle = 22.dp
    val messageBottomWithoutTitle = 4.dp
    val buttonHeight = 44.dp
    val multilineButtonHeight = 56.dp
    val buttonHorizontalPadding = 12.dp
    val buttonEdgePadding = 16.dp
    val buttonInsidePadding = 4.dp
    val buttonTopPadding = 4.dp
    val buttonBottomPadding = 16.dp

    /** ShadowEdge 的 weight_16_1_large, 同时用于描边与内晕距离场。 */
    internal const val edgeWeight = 3f

    fun colors(dark: Boolean) = CouiDialogColors(
        surface = if (dark) Color(0xFF1E1E1E) else Color.White,
        title = if (dark) Color(0xE6FFFFFF) else Color(0xE6000000),
        message = if (dark) Color(0x8AFFFFFF) else Color(0x8A000000),
        action = if (dark) Color(0xE6FFFFFF) else Color(0xE6000000),
        disabledAction = if (dark) Color(0x28FFFFFF) else Color(0x42000000),
        destructive = if (dark) Color(0xFFFF6C61) else Color(0xFFDB382C),
        dimAmount = if (dark) 0.6f else 0.2f,
    )

    @Composable
    fun themeColors(dark: Boolean = CouiTheme.isDark): CouiDialogColors {
        val theme = CouiTheme.colorSchemeFor(dark)
        return colors(dark).copy(surface = theme.surface, title = theme.onSurface,
            message = theme.onSurfaceVariant, action = theme.onSurface)
    }

    /**
     * 原厂 blur 分支的材质叠色。mix/blend 取自 coui_dialog_list_mix_blur_* 与
     * coui_dialog_list_blend_blur_*, 模糊等级取自 integer/coui_dialog_blur_radius。
     */
    fun material(dark: Boolean) = CouiDialogMaterial(
        mix = if (dark) Color(0x8C777777) else Color(0xDC909090),
        blend = if (dark) Color(0xB2292929) else Color(0x509F9F9F),
        blurRadius = 200,
        blendMode = if (dark) CouiMaterialBlendMode.Overlay else CouiMaterialBlendMode.ColorDodge,
    )

    /** 主操作主题色由宿主提供，不读取宿主 MaterialTheme。 */
    fun primaryActionColors(accent: Color): CouiDialogActionColors = DialogButtonDefaults.primaryColors(accent)

    /** 项目材质的 API 门槛；调用方仍须提供窗口源。 */
    fun isMaterialSupported(): Boolean = Build.VERSION.SDK_INT >= 33
}
