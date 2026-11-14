package com.itos.coui.sheet

import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.dp
import com.itos.coui.backdrop.CouiBackdrop
import com.itos.coui.effect.LocalCouiContourGlowEnabled
import com.itos.coui.shape.CouiCapsuleShape
import com.itos.coui.shape.CouiShape
import com.itos.coui.topappbar.CouiToolbarStyle
import com.itos.coui.topappbar.CouiTopAppBarButtonDefaults
import com.itos.coui.topappbar.LocalCouiToolbarBackdrop

internal val LocalCouiSheetSurfaceBackdrop = staticCompositionLocalOf<CouiBackdrop?> { null }

/** 当前面板已绘制的纯背景源，供同窗口浮动控件使用；独立弹窗仍消费窗口内容源。 */
@Composable
fun rememberCouiSheetSurfaceBackdrop(): CouiBackdrop =
    checkNotNull(LocalCouiSheetSurfaceBackdrop.current) { "面板背景源必须在 ModalBottomSheet 内容中读取" }

/** Panel 工具栏：按钮采样面板已经绘制的背景，不采样含按钮自身的窗口内容源。 */
@RequiresApi(34)
@Composable
fun SheetToolbar(
    modifier: Modifier = Modifier,
    title: String,
    dark: Boolean,
    navigation: @Composable (CouiToolbarStyle) -> Unit = {},
    actions: @Composable (CouiToolbarStyle) -> Unit = {},
) {
    val backdrop = rememberCouiSheetSurfaceBackdrop()
    val density = LocalDensity.current.density
    val contour = LocalCouiContourGlowEnabled.current
    val textStyle = rememberSheetTextStyle(SheetTextKind.Button)
    val titleStyle = rememberSheetTextStyle(SheetTextKind.Title)
    val style = remember(dark, density, contour, textStyle) {
        CouiToolbarStyle(dark, CouiShape(20.dp, 2f), CouiCapsuleShape,
            material = { CouiSheetDefaults.toolbarMaterial(dark, it) },
            shadow = { CouiTopAppBarButtonDefaults.shadow(dark, it, density, contour, alphaScale = 1f) },
            contentColor = Color(if (dark) 0xE6FFFFFF else 0xE6000000), textStyle = textStyle,
            cursorColor = Color(if (dark) 0xE6FFFFFF else 0xE6000000), contourGlowEnabled = contour)
    }
    CompositionLocalProvider(LocalCouiToolbarBackdrop provides backdrop) {
        Layout(modifier = modifier.fillMaxWidth().padding(top = CouiSheetDefaults.toolbarTopSpacing)
            .padding(horizontal = CouiSheetDefaults.toolbarHorizontalPadding), content = {
            Box { navigation(style) }
            Box { actions(style) }
            Box(Modifier.padding(vertical = CouiSheetDefaults.toolbarTitleVerticalPadding)
                .heightIn(min = CouiSheetDefaults.toolbarTitleMinHeight), contentAlignment = Alignment.Center) {
                BasicText(title, Modifier.semantics { heading() },
                    style = titleStyle.copy(color = style.contentColor, textAlign = TextAlign.Center),
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }) { measurables, constraints ->
            val width = constraints.maxWidth
            val gap = CouiSheetDefaults.toolbarTitleGap.roundToPx()
            val buttonConstraints = Constraints(maxWidth = (width / 2 - gap).coerceAtLeast(0),
                maxHeight = constraints.maxHeight)
            val navigationPlaceable = measurables[0].measure(buttonConstraints)
            val actionPlaceable = measurables[1].measure(buttonConstraints)
            // 两边按较宽操作区等量避让，标题保持窗口居中；长标题可自然增加高度。
            val inset = maxOf(navigationPlaceable.width, actionPlaceable.width) + gap
            val titlePlaceable = measurables[2].measure(Constraints(
                maxWidth = (width - 2 * inset).coerceAtLeast(0), maxHeight = constraints.maxHeight))
            val height = constraints.constrainHeight(maxOf(CouiSheetDefaults.toolbarHeight.roundToPx(),
                navigationPlaceable.height, actionPlaceable.height, titlePlaceable.height))
            layout(width, height) {
                navigationPlaceable.placeRelative(0, (height - navigationPlaceable.height) / 2)
                actionPlaceable.placeRelative(width - actionPlaceable.width, (height - actionPlaceable.height) / 2)
                titlePlaceable.placeRelative((width - titlePlaceable.width) / 2, (height - titlePlaceable.height) / 2)
            }
        }
    }
}
