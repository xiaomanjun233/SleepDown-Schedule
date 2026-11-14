package com.itos.coui.sheet

import android.graphics.Typeface
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import com.itos.coui.text.couiTextStyle
import kotlin.math.roundToInt

internal enum class SheetTextKind { Title, Button }

/** Panel 标题沿 HeadlineS；菜单文字沿 COUIMenuItemStyle，不能借用警告弹窗的按钮角色。 */
@Composable
internal fun rememberSheetTextStyle(kind: SheetTextKind): TextStyle {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    return remember(kind, configuration, density) {
        val title = kind == SheetTextKind.Title
        val weight = if (title) 550 else 500
        val typeface = Typeface.create(Typeface.create("sans-serif-medium", Typeface.NORMAL), weight, false)
        // 原厂标题使用 sp，菜单的 coui_action_text_menu_size 明确为 dp。
        val pixels = if (title) with(density) { 18.sp.toPx() } else 14f * density.density
        val spacing = if (title) {
            if (Build.VERSION.SDK_INT >= 35) 1.1429f else 1.15f
        } else if (Build.VERSION.SDK_INT >= 35) 1.143f else 1f
        couiTextStyle(typeface, weight, pixels.roundToInt().toFloat(), spacing,
            includeFontPadding = true, density = density).copy(letterSpacing = 0.sp)
    }
}
