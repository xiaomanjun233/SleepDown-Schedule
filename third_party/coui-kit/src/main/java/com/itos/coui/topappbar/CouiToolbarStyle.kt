package com.itos.coui.topappbar

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import com.itos.coui.effect.CouiFloatingMaterial
import com.itos.coui.effect.CouiFloatingShadow

data class CouiToolbarStyle(
    val dark: Boolean,
    val circleShape: Shape,
    val capsuleShape: Shape,
    val material: (capsule: Boolean) -> CouiFloatingMaterial,
    val shadow: (capsule: Boolean) -> CouiFloatingShadow,
    val contentColor: Color,
    val textStyle: TextStyle,
    val cursorColor: Color,
    val contourGlowEnabled: Boolean,
)

internal val LocalCouiToolbarStyle = staticCompositionLocalOf<CouiToolbarStyle> {
    error("顶栏样式必须由组件入口提供")
}
