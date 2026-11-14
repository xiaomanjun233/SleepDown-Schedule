package com.xiaomanjun.sleepdownschedule.core.ui.controls

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.shadow.Shadow

/** 教务与设置搜索共用表面参数；栏位定位由各页面保留。 */
internal object LiquidSearchStyle {
    val height = 44.dp
    val blur = 5.dp
    val lensHeight = 14.dp
    val lensAmount = 24.dp
    val pressExpansion = 1.5.dp
    val shadow = Shadow(radius = 8.dp, offset = DpOffset(0.dp, 2.dp), color = Color.Black.copy(alpha = .10f))
    fun surface(dark: Boolean) = if (dark) Color(0xFF16181D).copy(alpha = .78f) else Color.White.copy(alpha = .22f)
}
