package com.itos.coui.topappbar

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.LayerOutsets
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/** 包住完整的返回或操作槽位, 放在槽位进出动画之外, 保留内部淡化及原位输入。 */
@Composable
fun TopAppBarButtonGroup(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val overlay = checkNotNull(LocalCouiToolbarOverlay.current) { "顶栏按钮组必须位于顶栏效果作用域内" }
    overlay.apply {
        Row(
            modifier = Modifier.renderInSharedTransitionScopeOverlay(renderInOverlay = { true }).then(modifier),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

/** 顶栏操作区淡化; 可关闭横向收放, 淡化层始终包含外围投影。 */
@Composable
fun TopAppBarActionVisibility(
    modifier: Modifier = Modifier,
    visible: Boolean,
    durationMillis: Int = 280,
    easing: Easing = FastOutSlowInEasing,
    animateSize: Boolean = true,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current.density
    // 沿用原厂祖先裁剪的像素留白, 只扩离屏缓冲, 不给收放宽度增加 padding。
    // shortcut: 留白固定为原厂边界, 若投影或拖动超出此范围, 需按实际绘制边界扩展。
    val layerOutsets = remember(density) { LayerOutsets(all = (120f / density).dp) }
    if (!animateSize) {
        val transition = updateTransition(visible, label = "TopAppBarActionVisibility")
        val opacity by transition.animateFloat(
            transitionSpec = { tween(durationMillis, easing = easing) },
            label = "TopAppBarActionAlpha",
        ) { if (it) 1f else 0f }
        // AnimatedVisibility 退场时的零尺寸预布局会驱动内部 animateBounds, 原位淡化需保留尺寸。
        if (transition.currentState || transition.targetState || transition.isRunning) {
            Box(
                modifier = modifier.graphicsLayer { alpha = opacity; outsets = layerOutsets },
                propagateMinConstraints = true,
            ) { content() }
        }
        return
    }
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = expandHorizontally(tween(durationMillis, easing = easing), expandFrom = Alignment.End, clip = false),
        exit = shrinkHorizontally(tween(durationMillis, easing = easing), shrinkTowards = Alignment.End, clip = false),
    ) {
        val opacity by transition.animateFloat(
            transitionSpec = {
                when {
                    EnterExitState.PreEnter isTransitioningTo EnterExitState.Visible -> tween(durationMillis, easing = easing)
                    EnterExitState.Visible isTransitioningTo EnterExitState.PostExit -> tween(durationMillis, easing = easing)
                    else -> spring(stiffness = Spring.StiffnessMediumLow)
                }
            },
            label = "TopAppBarActionAlpha",
        ) { if (it == EnterExitState.Visible) 1f else 0f }
        Box(
            modifier = Modifier.graphicsLayer {
                alpha = opacity
                outsets = layerOutsets
            },
            propagateMinConstraints = true,
        ) { content() }
    }
}
