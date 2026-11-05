package com.itos.coui.topappbar

import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.union
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import com.itos.coui.theme.CouiTheme

@Immutable
data class CouiTopAppBarColors(
    val backgroundColor: Color,
    val titleColor: Color,
    val navigationIconColor: Color,
    val actionIconColor: Color,
)

object CouiTopAppBarDefaults {
    val height = 52.dp
    val expandedHeight = CouiCollapsingAppBarDefaults.titleOnlyHeight
    val collapsedHeight = CouiCollapsingAppBarDefaults.collapsedTitleHeight
    internal val expandedTitleStart = CouiCollapsingAppBarDefaults.titleStart
    internal val horizontalPadding = 12.dp
    internal val titleGap = 8.dp
    val windowInsets: WindowInsets
        @Composable get() = WindowInsets.systemBars.union(WindowInsets.displayCutout)
            .only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)

    @Composable
    fun colors(backgroundColor: Color = Color.Unspecified, titleColor: Color = Color.Unspecified,
        navigationIconColor: Color = Color.Unspecified, actionIconColor: Color = Color.Unspecified,
        dark: Boolean = CouiTheme.isDark): CouiTopAppBarColors {
        val scheme = CouiTheme.colorSchemeFor(dark)
        return CouiTopAppBarColors(backgroundColor.takeOrElse { scheme.background },
            titleColor.takeOrElse { scheme.onSurface }, navigationIconColor.takeOrElse { scheme.onSurface },
            actionIconColor.takeOrElse { scheme.onSurface })
    }

    /** 记住可恢复的折叠状态；canScroll 读取正文实际滚动范围。 */
    @Composable
    fun exitUntilCollapsedScrollBehavior(canScroll: () -> Boolean = { true }): CouiTopAppBarScrollBehavior {
        val state = rememberCouiCollapsingAppBarState(canScroll)
        return remember(state) { CouiTopAppBarScrollBehavior(state) }
    }

    val fadeHeight = 96.dp
    val referenceBlurRadius = 12.dp
    val bottomExtension = 12.dp
    internal val searchActionGap = 12.dp
    internal val searchActionMinWidth = 40.dp
    internal val searchActionMaxWidth = 152.dp
    internal val searchActionPadding = 16.dp
}
