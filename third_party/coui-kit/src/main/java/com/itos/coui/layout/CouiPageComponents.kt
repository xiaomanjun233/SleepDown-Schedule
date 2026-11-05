package com.itos.coui.layout

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.itos.coui.effect.couiButtonFeedback
import com.itos.coui.shape.CouiShape
import com.itos.coui.theme.CouiTheme
import com.itos.coui.theme.CouiColorScheme
import com.itos.coui.theme.CouiPresets
import com.itos.coui.text.Text
import com.itos.coui.text.CouiTextKind

/** 独立配色值；不创建页面主题作用域，也不改变内容槽内其他组件的主题。 */
@Immutable
data class CouiPageColors(val background: Color, val card: Color, val title: Color, val summary: Color,
    val disabled: Color, val accent: Color, val divider: Color = title.copy(alpha = .12f))

object CouiPageDefaults {
    val horizontalMargin = 16.dp
    val rowPadding = 16.dp
    val rowMinHeight = 48.dp
    val iconSize = 24.dp
    val iconSpacing = 16.dp
    val contentPadding = 10.dp
    val summarySpacing = 2.dp
    val groupSpacing = 16.dp
    val categoryTopSpacing = 12.dp
    val shape = CouiShape(24.dp, 3f)
    val toolbarHeight = 52.dp
    val dividerHeight = 0.33.dp
    /** 消费当前原生/可选宿主策略；显式 colors 仍优先。 */
    @Composable
    fun themeColors(dark: Boolean = CouiTheme.isDark): CouiPageColors =
        CouiTheme.colorSchemeFor(dark).pageColors()

    fun colors(accent: Color, dark: Boolean): CouiPageColors =
        (if (dark) CouiPresets.Dark else CouiPresets.Light).pageColors().copy(accent = accent)
}

private fun CouiColorScheme.pageColors() = CouiPageColors(background, container, onSurface,
    onSurfaceVariant, onSurface.copy(alpha = .3f), accent, outlineVariant)

/**
 * 一组相邻设置项共享项目平滑圆角；条件隐藏的条目不保留空白或分组端点。
 * 内部顶部留白按标题选择 Defaults；modifier.padding 在默认留白之外累加。
 * colors 只控制本组卡片、标题和说明；任意内容槽继续使用各组件自己的主题或显式参数。
 * headerActions 仅在 title 非空时渲染，位于标题行末端。
 */
@Composable
fun Group(
    modifier: Modifier = Modifier,
    title: String? = null,
    footer: String? = null,
    headerActions: @Composable RowScope.() -> Unit = {},
    colors: CouiPageColors = CouiPageDefaults.themeColors(),
    horizontalMargin: androidx.compose.ui.unit.Dp = CouiPageDefaults.horizontalMargin,
    topSpacing: androidx.compose.ui.unit.Dp? = null,
    shape: Shape = CouiPageDefaults.shape,
    content: @Composable ColumnScope.() -> Unit,
) {
    val groupTopSpacing = topSpacing ?: if (title.isNullOrEmpty()) CouiPageDefaults.groupSpacing else CouiPageDefaults.categoryTopSpacing
    Column(modifier.fillMaxWidth().padding(top = groupTopSpacing)) {
        if (!title.isNullOrEmpty()) Row(Modifier.padding(horizontal = 32.dp, vertical = 8.dp).heightIn(min = 16.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(text = title, kind = CouiTextKind.GroupTitle, color = colors.summary, modifier =
                Modifier.weight(1f).semantics { heading() }, textAlign = TextAlign.Start)
            headerActions()
        }
        Column(Modifier.padding(horizontal = horizontalMargin).fillMaxWidth()
            .clip(shape).background(colors.card), content = content)
        if (!footer.isNullOrEmpty()) Footer(text = footer, color = colors.summary)
    }
}

/** 卡片内部的细分隔。默认至少一个物理像素，实际高度服从宿主约束。 */
@Composable
fun Divider(modifier: Modifier = Modifier,
    startIndent: androidx.compose.ui.unit.Dp = CouiPageDefaults.rowPadding,
    endIndent: androidx.compose.ui.unit.Dp = CouiPageDefaults.rowPadding,
    color: Color = CouiTheme.colorScheme.outlineVariant) {
    val thickness = with(LocalDensity.current) {
        CouiPageDefaults.dividerHeight.roundToPx().coerceAtLeast(1).toDp()
    }
    Box(modifier.fillMaxWidth().padding(start = startIndent, end = endIndent)
        .height(thickness)
        .background(color))
}

@Composable
fun Footer(modifier: Modifier = Modifier, text: String,
    color: Color = CouiTheme.colorScheme.onSurfaceVariant) {
    Text(text = text, kind = CouiTextKind.Description, color = color, modifier =
        modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 8.dp), textAlign = TextAlign.Start)
}

/**
 * 通用行内容槽；onClick 非空时安装唯一的点击与反馈，行内留白也参与点击。
 * 无 onClick 时不提供点击、按钮语义、语义合并或反馈；宿主可自行组合 toggleable/selectable 与反馈。
 * enabled 仅控制本组件的点击与反馈，任意内容槽的交互与禁用状态由宿主负责。
 */
@Composable
fun Item(
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = null,
    iconSize: androidx.compose.ui.unit.Dp = CouiPageDefaults.iconSize,
    summary: (@Composable () -> Unit)? = null,
    trailing: @Composable () -> Unit = {},
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    dark: Boolean = CouiTheme.isDark,
    contentPadding: androidx.compose.foundation.layout.PaddingValues = androidx.compose.foundation.layout.PaddingValues(
        horizontal = CouiPageDefaults.rowPadding, vertical = CouiPageDefaults.contentPadding),
    title: @Composable () -> Unit,
) {
    val interaction = if (onClick != null) {
        val source = remember { MutableInteractionSource() }
        Modifier.couiButtonFeedback(dark, source, enabled, list = true)
            .clickable(source, null, enabled, role = Role.Button, onClick = onClick)
    } else Modifier
    Row(modifier.fillMaxWidth().defaultMinSize(minHeight = CouiPageDefaults.rowMinHeight)
        .then(interaction)
        .padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) Box(Modifier.padding(end = CouiPageDefaults.iconSpacing).size(iconSize), contentAlignment = Alignment.Center) { icon() }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(CouiPageDefaults.summarySpacing)) {
            title()
            summary?.invoke()
        }
        trailing()
    }
}
