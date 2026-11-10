package com.itos.coui.floatingactionbutton

import androidx.annotation.RequiresApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import com.itos.coui.theme.CouiTheme
import com.itos.coui.theme.ProvideCouiContent
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.itos.coui.backdrop.CouiBackdrop
import com.itos.coui.backdrop.LocalCouiBackdrop
import com.itos.coui.effect.CouiFloatingMaterial
import com.itos.coui.effect.CouiFloatingRenderer
import com.itos.coui.effect.CouiFloatingShadow
import com.itos.coui.effect.CouiFloatingSurface
import com.itos.coui.effect.LocalCouiContourGlowEnabled
import com.itos.coui.effect.SpotlightPreset
import com.itos.coui.effect.couiFabFeedback
import com.itos.coui.effect.couiFloatingSurface
import com.itos.coui.effect.rememberCouiSpotlight
import com.itos.coui.motion.couiPressMotion
import com.itos.coui.motion.couiTouchMotion
import com.itos.coui.motion.rememberCouiPressMotion
import com.itos.coui.motion.rememberCouiTouchMotion
import com.itos.coui.shape.CouiShape

/**
 * 使用 CouiShape 时, 裁剪与材质共用半径和权重, 普通投影使用相同半径的 caster。
 * 相邻 FAB 可启用 foregroundComposition, 在不透明 SDR 背景上只叠加自身前景, 避免背景回填擦除兄弟节点。
 * 省略 [renderer] 时创建独立的单主体绘制器。宿主可先创建绘制器并处理失败，再显式传入。
 */
@RequiresApi(34)
@Composable
fun Fab(
    modifier: Modifier = Modifier,
    backdrop: CouiBackdrop = checkNotNull(LocalCouiBackdrop.current) {
        "FAB 需要显式 backdrop 或 COUI Scaffold 的独立栏位"
    },
    renderer: CouiFloatingRenderer = remember { CouiFloatingRenderer.standalone() },
    onClick: () -> Unit,
    dark: Boolean = CouiTheme.isDark,
    shape: Shape = CouiFabDefaults.circleShape,
    material: CouiFloatingMaterial? = null,
    shadow: CouiFloatingShadow? = null,
    foregroundComposition: Boolean = false,
    contentColor: Color = CouiFabDefaults.contentColor,
    content: @Composable () -> Unit,
) {
    val contour = LocalCouiContourGlowEnabled.current
    val density = LocalDensity.current.density
    val couiShape = shape as? CouiShape
    val colors = CouiTheme.colorSchemeFor(dark)
    val resolvedMaterial = material ?: CouiFabDefaults.material(colors.accent, dark)
    val resolvedShadow = shadow ?: CouiFabDefaults.shadow(resolvedMaterial.firstColor, density, contour)
    val contentLayer = rememberGraphicsLayer()
    val motion = rememberCouiTouchMotion(true)
    val press = rememberCouiPressMotion()
    val interactionSource = remember { MutableInteractionSource() }
    val (lightInput, drawLight) = rememberCouiSpotlight(
        interactive = true,
        preset = SpotlightPreset.OpaqueSmall1,
        dark = dark,
    )
    var boundsSize by remember { mutableStateOf(IntSize.Zero) }
    Box(
        modifier.size(56.dp).onSizeChanged { boundsSize = it }
            .couiFloatingSurface(backdrop, renderer, resolvedMaterial, resolvedShadow, shape,
                contentLayer, contour, foregroundComposition = foregroundComposition) {
                CouiFloatingSurface(
                    Rect(Offset(motion.translationX.value, motion.translationY.value),
                        Size(boundsSize.width.toFloat(), boundsSize.height.toFloat())),
                    motion.scaleX.value + press.scale.value - 1f, motion.scaleY.value + press.scale.value - 1f,
                    cornerWeight = couiShape?.cornerWeight ?: 2f,
                    cornerRadius = couiShape?.radius?.value?.times(density),
                )
            },
    ) {
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                translationX = motion.translationX.value
                translationY = motion.translationY.value
                scaleX = motion.scaleX.value + press.scale.value - 1f
                scaleY = motion.scaleY.value + press.scale.value - 1f
            }.couiTouchMotion(motion, true, cancelOnConsumedMove = false, consumeDrag = true)
                .couiPressMotion(press).then(lightInput)
                .drawWithContent { contentLayer.record { this@drawWithContent.drawContent() } }
                .clip(shape).drawBehind { drawLight?.invoke(this) }
                .couiFabFeedback(dark, interactionSource)
                .clickable(
                    interactionSource = interactionSource, indication = null, role = Role.Button, onClick = onClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            ProvideCouiContent(color = contentColor, content = content)
        }
    }
}
