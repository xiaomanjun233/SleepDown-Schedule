package com.itos.coui.picker

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 在整组滚轮内容后绘制选择线；不改变测量、裁剪或输入。
 * 线位于当前绘制区域的中心带，按 Clock / Calendar 的整数像素规则对齐。
 * 对称的垂直 padding 放在此 Modifier 内侧；非对称留白放外侧，避免偏离滚轮中心。
 */
fun Modifier.couiPickerSelectionLines(color: Color, horizontalInset: Dp = 16.dp): Modifier {
    require(horizontalInset.value.isFinite() && horizontalInset >= 0.dp) { "选择线内缩必须是有限非负值" }
    return drawBehind {
        val inset = horizontalInset.toPx().toInt().toFloat()
        val half = 24.dp.toPx().toInt().toFloat()
        val thickness = .33000004.dp.toPx().toInt().coerceAtLeast(1).toFloat()
        repeat(2) { index ->
            val y = (size.height / 2 + if (index == 0) -half else half).toInt().toFloat()
            drawRect(color, Offset(inset, y), Size((size.width - inset * 2).coerceAtLeast(0f), thickness))
        }
    }
}
