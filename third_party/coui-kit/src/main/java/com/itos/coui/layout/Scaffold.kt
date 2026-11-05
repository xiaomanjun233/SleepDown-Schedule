package com.itos.coui.layout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.MutableWindowInsets
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.onConsumedWindowInsetsChanged
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.unit.LayoutDirection
import com.itos.coui.theme.CouiTheme
import com.itos.coui.theme.LocalCouiMaterialEnabled
import com.itos.coui.backdrop.ProvideCouiBackdrop
import com.itos.coui.backdrop.couiBackdrop
import com.itos.coui.backdrop.rememberCouiBackdrop

/**
 * 可选页面容器：录制完整正文视口，顶栏、底栏及浮动操作在采样层之外绘制。
 *
 * [windowInsets] 的横向安全区由容器应用并消费；纵向安全区由对应栏位处理。
 * 没有栏位或栏位高度为零时，将剩余纵向安全区交给 [content]。
 * 正文应应用返回的 PaddingValues 并消费它；滚动内容可将 padding 放在滚动节点内侧。
 * 容器需要有界宽高。IME 避让由宿主用 Modifier.imePadding() 缩小视口。
 * 栏位可直接使用默认正文背景源；正文内部的采样消费者须自行分离源与消费节点。
 * [floatingActionButton] 可组合多个 FAB，已避让栏位和系统安全区；位置、外边距与层级由宿主提供。
 * 使用第三方容器时仍可显式装配背景源；滚动和导航由宿主处理。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Scaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    containerColor: Color = CouiTheme.colorScheme.background,
    windowInsets: WindowInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout),
    floatingActionButton: (@Composable BoxScope.() -> Unit)? = null,
    content: @Composable (PaddingValues) -> Unit,
) {
    val backdrop = rememberCouiBackdrop()
    val materialEnabled = LocalCouiMaterialEnabled.current
    val remainingInsets = remember(windowInsets) { MutableWindowInsets(windowInsets) }
    val contentPadding = remember { ScaffoldPadding() }
    val body: @Composable () -> Unit = remember(content, backdrop, containerColor, materialEnabled) { {
        Box(Modifier.fillMaxSize().then(if (materialEnabled) Modifier.couiBackdrop(backdrop) else Modifier).background(containerColor)) {
            content(contentPadding)
        }
    } }
    SubcomposeLayout(
        modifier = modifier
            .onConsumedWindowInsetsChanged { remainingInsets.insets = windowInsets.exclude(it) }
            .drawBehind {
                // 正文底色在采样节点内绘制；侧边安全区单独补色，避免半透明底色重复叠加。
                val left = remainingInsets.getLeft(this, layoutDirection).toFloat().coerceAtMost(size.width)
                val right = remainingInsets.getRight(this, layoutDirection).toFloat().coerceAtMost(size.width - left)
                drawRect(containerColor, size = Size(left, size.height))
                drawRect(containerColor, topLeft = Offset(size.width - right, 0f), size = Size(right, size.height))
            }
            .windowInsetsPadding(windowInsets.only(WindowInsetsSides.Horizontal)),
    ) { constraints ->
        require(constraints.hasBoundedWidth && constraints.hasBoundedHeight) {
            "Scaffold 需要有界的宽度和高度"
        }
        val looseConstraints = constraints.copy(minWidth = 0, minHeight = 0)
        val top = subcompose(ScaffoldSlot.TopBar) {
            ProvideCouiBackdrop(backdrop, content = topBar)
        }.map { it.measure(looseConstraints) }
        val bottom = subcompose(ScaffoldSlot.BottomBar) {
            ProvideCouiBackdrop(backdrop, content = bottomBar)
        }.map { it.measure(looseConstraints) }
        val topHeight = top.maxOfOrNull { it.height } ?: 0
        val bottomHeight = bottom.maxOfOrNull { it.height } ?: 0
        // 在正文子组合与测量前更新同一个对象，折叠栏可持续读取本帧的实际占位。
        contentPadding.value = PaddingValues(
            top = (if (topHeight > 0) topHeight else remainingInsets.getTop(this)).toDp(),
            bottom = (if (bottomHeight > 0) bottomHeight else remainingInsets.getBottom(this)).toDp(),
        )
        val bodyPlaceables = subcompose(ScaffoldSlot.Content, body).map { it.measure(looseConstraints) }
        val floating = subcompose(ScaffoldSlot.FloatingAction) {
            if (floatingActionButton != null) ProvideCouiBackdrop(backdrop) {
                Box(Modifier.fillMaxSize().padding(contentPadding).consumeWindowInsets(contentPadding),
                    content = floatingActionButton)
            }
        }.map { it.measure(looseConstraints) }
        layout(constraints.maxWidth, constraints.maxHeight) {
            bodyPlaceables.forEach { it.placeRelative(0, 0) }
            top.forEach { it.placeRelative(0, 0) }
            bottom.forEach { it.placeRelative(0, constraints.maxHeight - bottomHeight) }
            floating.forEach { it.placeRelative(0, 0) }
        }
    }
}

private enum class ScaffoldSlot { TopBar, BottomBar, Content, FloatingAction }

@Stable
private class ScaffoldPadding : PaddingValues {
    var value by mutableStateOf<PaddingValues>(PaddingValues())

    override fun calculateLeftPadding(layoutDirection: LayoutDirection) = value.calculateLeftPadding(layoutDirection)
    override fun calculateTopPadding() = value.calculateTopPadding()
    override fun calculateRightPadding(layoutDirection: LayoutDirection) = value.calculateRightPadding(layoutDirection)
    override fun calculateBottomPadding() = value.calculateBottomPadding()
}
