package com.itos.coui.dialog

import com.itos.coui.theme.CouiTheme

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import com.itos.coui.backdrop.CouiBackdrop

/** 受控操作。确认后的关闭、状态变更及业务提交由 onClick 的宿主处理。 */
data class CouiDialogAction(val text: String, val onClick: () -> Unit, val enabled: Boolean = true,
    val style: CouiDialogActionStyle = CouiDialogActionStyle.Secondary, val colors: CouiDialogActionColors? = null) {
    init {
        require(text.isNotBlank()) { "操作文案不能为空" }
        require(style != CouiDialogActionStyle.Primary || colors != null) { "主操作必须由宿主提供主题配色" }
    }
}

/**
 * 普通警告弹窗。两短按钮横排，单个、三个或放不下的按钮竖排。
 * content 可装配列表、输入和选择器；不提供推荐/AI Payment 专用按钮及原厂高内容标题合并协议。
 * CouiShape 自动提供轮廓半径；其他 Shape 使用材质时须显式提供 cornerRadius。
 */
@Composable
fun AlertDialog(
    modifier: Modifier = Modifier,
    onDismissRequest: () -> Unit,
    confirmAction: CouiDialogAction,
    dark: Boolean = CouiTheme.isDark,
    title: String? = null,
    message: String? = null,
    dismissAction: CouiDialogAction? = null,
    neutralAction: CouiDialogAction? = null,
    placement: CouiDialogPlacement = CouiDialogPlacement.Bottom,
    colors: CouiDialogColors = CouiDialogDefaults.themeColors(dark),
    shape: Shape = CouiDialogDefaults.shape,
    cornerRadius: Dp = Dp.Unspecified,
    material: CouiDialogMaterial? = null,
    backdrop: CouiBackdrop? = null,
    properties: DialogProperties = DialogProperties(usePlatformDefaultWidth = false),
    content: @Composable ColumnScope.() -> Unit = {},
) {
    CouiDialogWindow(modifier, onDismissRequest, colors, dark, placement, shape, cornerRadius, properties, material, backdrop) {
        val configuration = LocalConfiguration.current
        var messageLines by remember(message) { mutableIntStateOf(1) }
        var stacked by remember { mutableIntStateOf(if (neutralAction != null) 1 else 0) }
        val tall = messageLines > 1 || (stacked == 1 && (dismissAction != null || neutralAction != null))
        Column(Modifier.fillMaxWidth()) {
            // 对应正文/标题的滚动区与独立 buttonPanel；操作不随长文滚出窗口。
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                if (!title.isNullOrEmpty()) Box(Modifier.fillMaxWidth().padding(
                    start = CouiDialogDefaults.contentPadding, end = CouiDialogDefaults.contentPadding,
                    top = CouiDialogDefaults.titleTop, bottom = CouiDialogDefaults.titleBottom)) {
                    DialogText(text = title, kind = DialogTextKind.Title, color = colors.title, modifier = Modifier.fillMaxWidth()
                        .heightIn(max = CouiDialogDefaults.titleMaxHeight(configuration.screenHeightDp))
                        .verticalScroll(rememberScrollState()))
                }
                if (!message.isNullOrEmpty()) DialogText(text = message, kind = DialogTextKind.Message, color = colors.message, modifier =
                    Modifier.fillMaxWidth().padding(horizontal = CouiDialogDefaults.contentPadding)
                        .padding(top = if (title.isNullOrEmpty() && !tall) CouiDialogDefaults.messageTopWithoutTitle else 0.dp,
                            bottom = if (title.isNullOrEmpty() && !tall) CouiDialogDefaults.messageBottomWithoutTitle
                                else CouiDialogDefaults.messageBottom),
                    onLineCount = { if (messageLines != it) messageLines = it })
                content()
            }
            DialogActions(confirmAction, dismissAction, neutralAction, colors, dark, material != null) {
                val next = if (it) 1 else 0
                if (stacked != next) stacked = next
            }
        }
    }
}

@Composable
private fun DialogActions(confirm: CouiDialogAction, dismiss: CouiDialogAction?, neutral: CouiDialogAction?,
    colors: CouiDialogColors, dark: Boolean, material: Boolean, onStacked: (Boolean) -> Unit) {
    val horizontal = listOfNotNull(dismiss, neutral, confirm)
    val vertical = listOfNotNull(neutral, confirm, dismiss)
    val density = LocalDensity.current
    val textStyle = rememberDialogTextStyle(DialogTextKind.Button)
    val measurer = rememberTextMeasurer()
    val scroll = rememberScrollState()
    // 文字视口只裁剪自己的内容；按钮运动绘制在文字之上。
    BoxWithConstraints(Modifier.fillMaxWidth().zIndex(1f)) {
        val parentWidth = maxWidth
        // COUIButtonBarLayout.getHorizontalButtonContentWidth：先均分父宽，再减按钮和父容器留白。
        val cellWidth = with(density) { parentWidth.roundToPx() } / horizontal.size
        val available = cellWidth - with(density) {
            (CouiDialogDefaults.buttonHorizontalPadding * 2 + CouiDialogDefaults.buttonEdgePadding +
                CouiDialogDefaults.buttonInsidePadding).toPx()
        }
        val textWidths = horizontal.associateWith {
            measurer.measure(it.text, textStyle, softWrap = false).multiParagraph.maxIntrinsicWidth
        }
        val stacked = horizontal.size != 2 || textWidths.values.any { it > available }
        SideEffect { onStacked(stacked) }
        val verticalAvailable = with(density) {
            parentWidth.roundToPx() - CouiDialogDefaults.buttonEdgePadding.roundToPx() * 2 -
                CouiDialogDefaults.buttonHorizontalPadding.roundToPx() * 2
        }.coerceAtLeast(0)
        fun buttonHeight(action: CouiDialogAction, width: Int, multiline: Boolean): Int {
            val textHeight = measurer.measure(action.text, textStyle, maxLines = 2, overflow = TextOverflow.Ellipsis,
                constraints = Constraints(maxWidth = width.coerceAtLeast(0))).size.height
            return with(density) {
                maxOf(textHeight + 4.dp.roundToPx() * 2,
                    (if (multiline) CouiDialogDefaults.multilineButtonHeight else CouiDialogDefaults.buttonHeight).roundToPx())
            }
        }
        val naturalHeight = with(density) {
            if (stacked) vertical.sumOf { action ->
                buttonHeight(action, verticalAvailable, textWidths.getValue(action) > verticalAvailable) +
                    CouiDialogDefaults.buttonTopPadding.roundToPx() * 2
            } + CouiDialogDefaults.buttonBottomPadding.roundToPx() - CouiDialogDefaults.buttonTopPadding.roundToPx()
            else horizontal.maxOf { action -> buttonHeight(action,
                cellWidth - CouiDialogDefaults.buttonEdgePadding.roundToPx() - CouiDialogDefaults.buttonInsidePadding.roundToPx() -
                    CouiDialogDefaults.buttonHorizontalPadding.roundToPx() * 2, false)
            } + CouiDialogDefaults.buttonTopPadding.roundToPx() + CouiDialogDefaults.buttonBottomPadding.roundToPx()
        }
        // verticalScroll 即使无法滚动也会裁剪，只有实际超高时才需要这个视口。
        Box(if (naturalHeight > constraints.maxHeight) Modifier.verticalScroll(scroll) else Modifier) {
            if (stacked) {
                Column {
                    vertical.forEachIndexed { index, action ->
                        Box(Modifier.padding(start = CouiDialogDefaults.buttonEdgePadding,
                            end = CouiDialogDefaults.buttonEdgePadding, top = CouiDialogDefaults.buttonTopPadding,
                            bottom = if (index == vertical.lastIndex) CouiDialogDefaults.buttonBottomPadding
                                else CouiDialogDefaults.buttonTopPadding)) {
                            DialogActionButton(action, if (action === neutral) colors.copy(action = colors.destructive) else colors,
                                dark, material, Modifier.fillMaxWidth(), textWidths.getValue(action) > verticalAvailable)
                        }
                    }
                }
            } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                horizontal.forEachIndexed { index, action ->
                    Box(Modifier.weight(1f).padding(
                        start = if (index == 0) CouiDialogDefaults.buttonEdgePadding else CouiDialogDefaults.buttonInsidePadding,
                        end = if (index == horizontal.lastIndex) CouiDialogDefaults.buttonEdgePadding else CouiDialogDefaults.buttonInsidePadding,
                        top = CouiDialogDefaults.buttonTopPadding, bottom = CouiDialogDefaults.buttonBottomPadding)) {
                        DialogActionButton(action, if (action === neutral) colors.copy(action = colors.destructive) else colors,
                            dark, material, Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

@Composable
private fun DialogActionButton(action: CouiDialogAction, colors: CouiDialogColors, dark: Boolean, material: Boolean,
    modifier: Modifier, multiline: Boolean = false) {
    DialogButton(modifier = modifier.heightIn(min = if (multiline) CouiDialogDefaults.multilineButtonHeight
        else CouiDialogDefaults.buttonHeight), text = action.text, onClick = action.onClick,
        dark = dark, enabled = action.enabled, style = action.style, material = material,
        colors = action.colors ?: DialogButtonDefaults.secondaryColors(dark, material).copy(
            content = colors.action, disabledContent = colors.disabledAction))
}
