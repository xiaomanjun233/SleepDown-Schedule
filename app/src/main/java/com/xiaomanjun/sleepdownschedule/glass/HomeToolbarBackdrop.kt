package com.xiaomanjun.sleepdownschedule.glass

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import com.itos.coui.backdrop.CouiBackdrop
import com.itos.coui.backdrop.couiBackdrop
import com.itos.coui.backdrop.rememberCouiBackdrop
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop

internal val LocalHomeToolbarBackdrop = staticCompositionLocalOf<CouiBackdrop?> { null }

@Composable
internal fun rememberHomeToolbarBackdrop(): CouiBackdrop =
    rememberCouiBackdrop(diagnosticName = "home-toolbar-background")

/**
 * 只录制顶栏这一条背景：页面前缀 → 壁纸亮度层 → 正文 → 渐进模糊。
 * 工具栏文字和按钮必须是后绘制的同级消费者，不能采样包含顶栏的 Dock 整页源。
 */
@Composable
internal fun HomeToolbarBackdrop(
    source: CouiBackdrop,
    background: Backdrop,
    foreground: Backdrop,
    modifier: Modifier = Modifier,
    wallpaperOverlay: @Composable BoxScope.() -> Unit = {},
    content: @Composable BoxScope.() -> Unit,
) {
    retainGlassSources(background)
    retainGlassSources(foreground)
    Box(modifier.couiBackdrop(source)) {
        Spacer(Modifier.matchParentSize().drawBackdrop(background, shape = { RectangleShape },
            effects = {}, highlight = null, shadow = null))
        wallpaperOverlay()
        Spacer(Modifier.matchParentSize().drawBackdrop(foreground, shape = { RectangleShape },
            effects = {}, highlight = null, shadow = null))
        content()
    }
}
