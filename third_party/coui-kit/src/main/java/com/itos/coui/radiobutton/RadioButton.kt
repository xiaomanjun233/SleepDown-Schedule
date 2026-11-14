package com.itos.coui.radiobutton

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.itos.coui.theme.CouiTheme

private val RadioSelectEasing = CubicBezierEasing(.3f, 0f, .1f, 1f)
private val RadioDeselectEasing = CubicBezierEasing(.33f, 0f, .67f, 1f)

/**
 * COUI coui_btn_radio 的圆形几何和双向切换动画，基准尺寸为 24dp。
 * 只负责绘制；宿主在外层提供 selectableGroup、selectable 与 RadioButton 语义。
 */
@Composable
fun RadioButton(
    modifier: Modifier = Modifier,
    selected: Boolean,
    enabled: Boolean = true,
    colors: CouiRadioButtonColors = CouiRadioButtonDefaults.colors(CouiTheme.colorScheme.accent, CouiTheme.isDark),
) {
    val timeline = remember { Animatable(300f) }
    var previousSelected by remember { mutableStateOf(selected) }
    var previousEnabled by remember { mutableStateOf(enabled) }
    var animated by remember { mutableStateOf(false) }
    LaunchedEffect(selected, enabled) {
        val changed = enabled && previousEnabled && previousSelected != selected
        previousSelected = selected
        previousEnabled = enabled
        animated = changed
        if (changed) {
            // selector 的两个方向不可逆；中断后从旧目标对应的另一条时间线重播。
            timeline.snapTo(0f)
            timeline.animateTo(300f, tween(300, easing = LinearEasing))
        } else timeline.snapTo(300f)
    }
    Spacer(modifier.size(24.dp).drawWithCache {
        val unit = size.minDimension / 24f
        onDrawBehind {
            val transitioning = enabled && previousEnabled && (animated || previousSelected != selected)
            val time = if (!transitioning) 300f else if (previousSelected != selected) 0f else timeline.value
            val easing = if (selected) RadioSelectEasing else RadioDeselectEasing
            val pulse = if (!transitioning) 1f else if (time < 150f) {
                1f + .08f * easing.transform(time / 150f)
            } else 1.08f - .08f * easing.transform(((time - 150f) / 150f).coerceIn(0f, 1f))
            val outlineAlpha = if (!transitioning) { if (selected) 0f else 1f }
                else if (selected) 1f - easing.transform((time / 300f).coerceIn(0f, 1f))
                else easing.transform((time / 267f).coerceIn(0f, 1f))
            val fillAlpha = if (!transitioning) { if (selected) 1f else 0f }
                else if (selected) easing.transform((time / 300f).coerceIn(0f, 1f))
                else 1f - easing.transform((time / 200f).coerceIn(0f, 1f))
            val centerScale = if (!transitioning) { if (selected) 1f else 0f }
                else if (selected) easing.transform((time / 300f).coerceIn(0f, 1f))
                else 1f - easing.transform((time / 267f).coerceIn(0f, 1f))
            drawCircle(if (enabled) colors.unselected else colors.disabledUnselected,
                radius = 9.2f * unit * pulse, alpha = outlineAlpha, style = Stroke(1.3f * unit * pulse))
            drawCircle(if (enabled) colors.selected else colors.disabledSelected,
                radius = 10f * unit * pulse, alpha = fillAlpha)
            val centerColor = if (!enabled) colors.disabledCenter else if (transitioning) colors.animatedCenter else colors.center
            drawCircle(centerColor, radius = 6f * unit * centerScale, alpha = fillAlpha)
        }
    })
}
