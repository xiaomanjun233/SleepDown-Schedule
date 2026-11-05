package com.itos.coui.radiobutton

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

@Immutable
data class CouiRadioButtonColors(
    val selected: Color,
    val unselected: Color,
    val disabledSelected: Color,
    val disabledUnselected: Color,
    val center: Color,
    val disabledCenter: Color,
    val animatedCenter: Color = center,
)

object CouiRadioButtonDefaults {
    fun colors(selected: Color, dark: Boolean): CouiRadioButtonColors = CouiRadioButtonColors(
        selected = selected,
        unselected = Color(if (dark) 0x4dffffff else 0x42000000),
        disabledSelected = selected.copy(alpha = .3f),
        disabledUnselected = Color(if (dark) 0x26ffffff else 0x14000000),
        center = Color.White,
        disabledCenter = Color(if (dark) 0x29ffffff else 0x8affffff),
        animatedCenter = Color(0xfffafafa),
    )
}
