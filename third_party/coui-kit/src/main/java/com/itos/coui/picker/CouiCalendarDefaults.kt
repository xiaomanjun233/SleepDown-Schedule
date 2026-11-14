package com.itos.coui.picker

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.itos.coui.theme.CouiTheme

/** 月历各绘制角色；accent 仍可在组件上单独覆盖。 */
@Immutable
data class CouiCalendarColors(val foreground: Color, val secondary: Color, val weekday: Color,
    val hint: Color, val highlight: Color, val divider: Color, val onAccent: Color)

object CouiCalendarDefaults {
    @Composable
    fun colors(dark: Boolean = CouiTheme.isDark): CouiCalendarColors {
        val theme = CouiTheme.colorSchemeFor(dark)
        return CouiCalendarColors(theme.onSurface, theme.onSurfaceVariant,
            theme.onSurface.copy(alpha = .4f), theme.onSurface.copy(alpha = if (dark) .3f else .26f),
            Color(if (dark) 0x7F707070 else 0x7FD0D0D0), theme.outlineVariant, theme.onAccent)
    }
}
