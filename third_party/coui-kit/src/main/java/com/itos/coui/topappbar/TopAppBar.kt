package com.itos.coui.topappbar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.dp
import com.itos.coui.backdrop.CouiBackdrop
import com.itos.coui.backdrop.LocalCouiBackdrop
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.rememberCouiTextStyle
import com.itos.coui.theme.CouiTheme
import com.itos.coui.theme.ProvideCouiContent
import com.itos.coui.theme.LocalCouiDarkTheme
import kotlin.math.roundToInt

/** 完整紧凑顶栏；内部处理安全区和槽位布局，点击行为由宿主提供。 */
@Composable
fun TopAppBar(
    modifier: Modifier = Modifier,
    title: @Composable () -> Unit,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    dark: Boolean = CouiTheme.isDark,
    colors: CouiTopAppBarColors = CouiTopAppBarDefaults.colors(dark = dark),
    windowInsets: WindowInsets = CouiTopAppBarDefaults.windowInsets,
    backdrop: CouiBackdrop = checkNotNull(LocalCouiBackdrop.current) {
        "顶栏需要显式 backdrop 或 ProvideCouiBackdrop 作用域"
    },
) {
    TopAppBarSurface(modifier.fillMaxWidth(), backdrop,
        gradientColor = colors.backgroundColor, dark = dark) {
        CompositionLocalProvider(LocalCouiDarkTheme provides dark) {
            AppBarLayout(title, navigationIcon, actions, colors, windowInsets,
                CouiTopAppBarDefaults.height, CouiTopAppBarDefaults.height, null)
        }
    }
}

/**
 * 大标题缩为固定小标题；省略 scrollBehavior 时保持展开。
 * 连接挂到正文祖先，正文通过 scrollBehavior.state.contentPadding 补偿逻辑滚动与可见弹簧的差值。
 * 高度不含 windowInsets；expandedHeight 必须大于正数 collapsedHeight。
 * 字号为 Unspecified 时沿用预设：展开跟随 HomeTitle，收起使用固定 dp。
 * 显式字号必须是有限正数 sp；title 槽位须继承组件提供的字号。
 */
@Composable
fun LargeTopAppBar(
    modifier: Modifier = Modifier,
    title: @Composable () -> Unit,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    scrollBehavior: CouiTopAppBarScrollBehavior? = null,
    dark: Boolean = CouiTheme.isDark,
    colors: CouiTopAppBarColors = CouiTopAppBarDefaults.colors(dark = dark),
    windowInsets: WindowInsets = CouiTopAppBarDefaults.windowInsets,
    expandedHeight: Dp = CouiTopAppBarDefaults.expandedHeight,
    collapsedHeight: Dp = CouiTopAppBarDefaults.collapsedHeight,
    backdrop: CouiBackdrop = checkNotNull(LocalCouiBackdrop.current) {
        "顶栏需要显式 backdrop 或 ProvideCouiBackdrop 作用域"
    },
    expandedTitleSize: TextUnit = TextUnit.Unspecified,
    collapsedTitleSize: TextUnit = TextUnit.Unspecified,
) {
    require(expandedHeight.value.isFinite() && collapsedHeight.value.isFinite() &&
        collapsedHeight > 0.dp && expandedHeight > collapsedHeight) {
        "展开高度必须大于收起高度，且高度必须是有限正数"
    }
    require(expandedTitleSize == TextUnit.Unspecified ||
        expandedTitleSize.isSp && expandedTitleSize.value.isFinite() && expandedTitleSize.value > 0f) {
        "展开字号必须为 Unspecified 或有限正数 sp"
    }
    require(collapsedTitleSize == TextUnit.Unspecified ||
        collapsedTitleSize.isSp && collapsedTitleSize.value.isFinite() && collapsedTitleSize.value > 0f) {
        "收起字号必须为 Unspecified 或有限正数 sp"
    }
    val density = LocalDensity.current
    val state = scrollBehavior?.state
    SideEffect {
        state?.updateRange(density, hasSearchBar = false, expandedHeight = expandedHeight, collapsedHeight = collapsedHeight)
        state?.updateSearch(expanded = false, active = false)
    }
    TopAppBarSurface(modifier.fillMaxWidth(), backdrop,
        referenceBlurRadius = with(density) { CouiCollapsingAppBarDefaults.referenceBlurRadiusPx.toDp() },
        bottomExtension = CouiCollapsingAppBarDefaults.titleOnlyBlurExtension,
        gradientSpeed = 3f, gradientColor = colors.backgroundColor, dark = dark,
        topExtension = {
            with(density) {
                val range = (expandedHeight.roundToPx() - collapsedHeight.roundToPx()).coerceAtLeast(0)
                (state?.offsetPx ?: 0f).coerceIn(0f, range.toFloat()).toDp()
            }
        }) {
        CompositionLocalProvider(LocalCouiDarkTheme provides dark) {
            AppBarLayout(title, navigationIcon, actions, colors, windowInsets, expandedHeight, collapsedHeight, state,
                expandedTitleSize, collapsedTitleSize)
        }
    }
}

@Composable
private fun AppBarLayout(
    title: @Composable () -> Unit,
    navigationIcon: @Composable () -> Unit,
    actions: @Composable RowScope.() -> Unit,
    colors: CouiTopAppBarColors,
    windowInsets: WindowInsets,
    expandedHeight: Dp,
    collapsedHeight: Dp,
    state: CouiCollapsingAppBarState?,
    expandedTitleSize: TextUnit = TextUnit.Unspecified,
    collapsedTitleSize: TextUnit = TextUnit.Unspecified,
) {
    val large = expandedHeight > collapsedHeight
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val defaultTitleStyle = rememberCouiTextStyle(if (large) CouiTextKind.HomeTitle else CouiTextKind.Toolbar)
    val titleStyle = if (large && expandedTitleSize != TextUnit.Unspecified) {
        // 同步行高与字号的像素比例，保留原字体度量及系统非线性字体缩放。
        val lineHeight = with(density) {
            (defaultTitleStyle.lineHeight.toPx() * expandedTitleSize.toPx() /
                defaultTitleStyle.fontSize.toPx()).toSp()
        }
        defaultTitleStyle.copy(fontSize = expandedTitleSize, lineHeight = lineHeight)
    } else defaultTitleStyle
    val fraction = state?.collapsedFraction ?: 0f
    val progress = if (large) CouiCollapsingAppBarDefaults.titleEasing.transform(fraction) else 0f
    val scale = if (large) {
        val collapsedScale = with(density) {
            val collapsedPixels = if (collapsedTitleSize == TextUnit.Unspecified)
                CouiCollapsingAppBarDefaults.collapsedTitleSize.toPx() else collapsedTitleSize.toPx()
            collapsedPixels / titleStyle.fontSize.toPx()
        }
        1f + (collapsedScale - 1f) * progress
    } else 1f
    Layout(modifier = Modifier.windowInsetsPadding(windowInsets), content = {
        Box {
            CompositionLocalProvider(LocalCouiToolbarContentColor provides colors.navigationIconColor) {
                ProvideCouiContent(color = colors.navigationIconColor, content = navigationIcon)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            CompositionLocalProvider(LocalCouiToolbarContentColor provides colors.actionIconColor) {
                ProvideCouiContent(color = colors.actionIconColor) { actions() }
            }
        }
        Box(Modifier.semantics { heading() }.graphicsLayer {
            scaleX = scale
            scaleY = scale
            transformOrigin = TransformOrigin(if (direction == LayoutDirection.Rtl) 1f else 0f, 0f)
            clip = true
        }) {
            ProvideCouiContent(color = colors.titleColor, textStyle = titleStyle.copy(color = colors.titleColor),
                mergeTextStyle = false, content = title)
        }
    }) { nodes, constraints ->
        require(constraints.hasBoundedWidth) { "顶栏需要有界宽度" }
        val width = constraints.maxWidth
        val expanded = expandedHeight.roundToPx()
        val collapsed = collapsedHeight.roundToPx()
        val range = (expanded - collapsed).coerceAtLeast(0)
        val offset = (state?.offsetPx ?: 0f).coerceIn(0f, range.toFloat())
        val height = constraints.constrainHeight((expanded - offset).roundToInt())
        val toolbarHeight = if (large) minOf(collapsed, height) else height
        val padding = CouiTopAppBarDefaults.horizontalPadding.roundToPx().coerceAtMost(width / 2)
        val gap = CouiTopAppBarDefaults.titleGap.roundToPx()
        val slotWidth = (width - 2 * padding).coerceAtLeast(0)
        val navigation = nodes[0].measure(Constraints(maxWidth = slotWidth, maxHeight = toolbarHeight))
        val action = nodes[1].measure(Constraints(maxWidth = slotWidth - navigation.width, maxHeight = toolbarHeight))
        val compactStart = if (large && navigation.width == 0) CouiCollapsingAppBarDefaults.titleOnlyStart.toPx()
            else (padding + navigation.width + if (navigation.width > 0) gap else 0).toFloat()
        val compactEnd = padding + action.width + if (action.width > 0) gap else 0
        val expandedStart = CouiTopAppBarDefaults.expandedTitleStart.toPx()
        val curvedTitle = large && navigation.width > 0
        // 弧线偏移在两端的位置与变化率均为零，保留标题原有的起步和收尾节奏。
        val arcEnvelope = progress * (1f - progress)
        val arcOffset = if (curvedTitle) 0.8f * arcEnvelope * arcEnvelope else 0f
        val horizontalProgress = progress + arcOffset
        val start = if (large)
            expandedStart + (compactStart - expandedStart) * horizontalProgress else compactStart
        val end = if (large && action.width == 0) CouiCollapsingAppBarDefaults.titleOnlyStart.toPx()
            else compactEnd.toFloat()
        // 用缩放后的可见宽度限制标题，避免收起时覆盖导航或操作按钮。
        val titleWidth = ((width - start - end).coerceAtLeast(0f) / scale).roundToInt()
        val titleNode = nodes[2].measure(Constraints(maxWidth = titleWidth, maxHeight = (height / scale).toInt()))
        layout(width, height) {
            val navigationY = (toolbarHeight - navigation.height) / 2
            navigation.placeRelative(padding, navigationY, zIndex = 1f)
            action.placeRelative(width - padding - action.width, (toolbarHeight - action.height) / 2, zIndex = 1f)
            val titleY = if (large) {
                val expandedY = expanded - (CouiTopAppBarDefaults.expandedHeight -
                    CouiCollapsingAppBarDefaults.titleOnlyTop).toPx()
                val collapsedY = (collapsed - titleNode.height * scale) / 2f
                if (curvedTitle) {
                    val verticalProgress = progress - arcOffset
                    expandedY + (collapsedY - expandedY) * verticalProgress
                } else {
                    expandedY + (range + collapsedY - expandedY) * progress - offset
                }
            } else (height - titleNode.height) / 2f
            titleNode.placeRelative(start.roundToInt(),
                (if (curvedTitle) titleY.coerceAtLeast(0f) else
                    titleY.coerceIn(0f, (height - titleNode.height * scale).coerceAtLeast(0f))).roundToInt())
        }
    }
}
