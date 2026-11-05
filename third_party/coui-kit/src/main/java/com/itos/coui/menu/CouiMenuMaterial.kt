package com.itos.coui.menu

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isFinite
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.itos.coui.backdrop.CouiBackdrop
import com.itos.coui.motion.CouiTouchMotion
import com.itos.coui.shape.CouiCornerWeight
import com.itos.coui.shape.CouiShape

/** 材质只处理背景, 菜单文字和状态层保持清晰。 */
internal data class CouiMenuMaterial(val drawing: Modifier = Modifier, val input: Modifier = Modifier)

@Composable
internal fun couiMenuMaterial(
    backdrop: CouiBackdrop,
    shape: Shape,
    interactive: Boolean,
    motion: CouiMenuMotion,
    touchMotion: CouiTouchMotion,
    themeTintEnabled: Boolean,
    inputTarget: ((Offset, Boolean) -> LayoutCoordinates?)? = null,
    transitionProgress: (() -> Float)? = null,
    parentTouchMotion: CouiTouchMotion? = null,
): CouiMenuMaterial {
    // 材质组合覆盖进入、退出和反向动画, 不按 expanded 提前释放背景。
    backdrop.RetainWhileComposed()
    val surface = LocalCouiMenuStyle.current.colors.surface
    val dark = LocalCouiMenuStyle.current.isDark
    // 固定使用 COUI RoundFrameLayout 的普通材质模式 4, 不按 HDR 状态切换。
    val mix = if (dark) Color(0x99262626) else Color(0x99CDCDCD)
    val blend = if (dark) Color(0x99262626) else Color(0xBFEEEEEE)
    val themeTint = LocalCouiMenuStyle.current.themeTint
    val runtimeShaderSupported = Build.VERSION.SDK_INT >= 33
    val blurredBackdrop = rememberCouiMenuBlur(backdrop, surface)
    val backgroundLayer = rememberGraphicsLayer()
    val density = LocalDensity.current
    val legacyBlurRadius = 12.dp.value * density.density
    val blurPadding = if (runtimeShaderSupported) 0 else legacyBlurRadius.toInt()
    val edgeRadiusPx = LocalCouiMenuStyle.current.resolvedRadius.value * density.density
    val edgeWeight = (LocalCouiMenuStyle.current.shape as? CouiShape)?.cornerWeight ?: CouiCornerWeight
    val edge = if (runtimeShaderSupported) {
        rememberCouiMenuEdge(dark, edgeRadiusPx, edgeWeight, mix, blend)
    } else null
    if (!runtimeShaderSupported) {
        // shortcut: API 33 以下保留旧 Gaussian/Canvas 混合, 不视为厂商逐像素等价实现。
        backgroundLayer.renderEffect = remember(legacyBlurRadius) {
            BlurEffect(null, legacyBlurRadius, edgeTreatment = TileMode.Decal)
        }
    }
    backgroundLayer.clip = true
    backgroundLayer.topLeft = IntOffset(-blurPadding, -blurPadding)
    val (spotlightInput, drawSpotlight) = couiMenuSpotlight(interactive, dark, inputTarget)
    var coordinates: LayoutCoordinates? by remember { mutableStateOf(null, neverEqualPolicy()) }
    val drawing = Modifier
        .graphicsLayer {
            this.shape = shape
            clip = true
            compositingStrategy = CompositingStrategy.Offscreen
        }
        .onGloballyPositioned { coordinates = it }
        .drawWithContent {
            transitionProgress?.invoke()
            // 订阅外层变换, graphicsLayer 单独更新时也重新映射背景, 避免采样跟着菜单移动。
            val frame = motion.frame
            val scaleX = if (parentTouchMotion == null) frame.scale * touchMotion.scaleX.value else
                frame.scale * parentTouchMotion.scaleX.value + touchMotion.scaleX.value - 1f
            val scaleY = if (parentTouchMotion == null) frame.scale * touchMotion.scaleY.value else
                frame.scale * parentTouchMotion.scaleY.value + touchMotion.scaleY.value - 1f
            val translation = frame.translation + Offset(
                touchMotion.translationX.value + (parentTouchMotion?.translationX?.value ?: 0f),
                touchMotion.translationY.value + (parentTouchMotion?.translationY?.value ?: 0f),
            )
            val target = coordinates
            drawRect(surface)
            if (target != null && target.isAttached && size.width > 0f && size.height > 0f &&
                scaleX > 0f && scaleY > 0f && translation.isFinite
            ) {
                if (Build.VERSION.SDK_INT >= 33) {
                    edge?.let { backgroundLayer.renderEffect = it.renderEffect(size, density.density) }
                }
                // 背景录制不调用 drawContent, 使用独立 Density 避免作用域密度自引用。
                backgroundLayer.record(
                    density = density,
                    layoutDirection = layoutDirection,
                    size = IntSize(size.width.toInt() + blurPadding * 2, size.height.toInt() + blurPadding * 2),
                ) {
                    if (Build.VERSION.SDK_INT >= 33 && blurredBackdrop != null) {
                        blurredBackdrop.draw(this, target)
                    } else {
                        drawRect(surface)
                        // Gaussian 的 Decal 采样保留边界外留白, 不把菜单边缘当成源图边缘。
                        translate(blurPadding.toFloat(), blurPadding.toFloat()) {
                            backdrop.draw(this, target)
                        }
                    }
                }
                drawLayer(backgroundLayer)
            }
            if (!runtimeShaderSupported) {
                drawRect(mix, blendMode = BlendMode.Luminosity)
                drawRect(blend, blendMode = BlendMode.Overlay)
            }
            if (themeTintEnabled) {
                // 只引入页面色相, 保留材质亮度, 不让前景参与混色。
                drawRect(themeTint, blendMode = BlendMode.Color)
            }
            drawContent()
        }
        // 跟手光仍沿用 COUI 菜单的透明前景合成, 不进入通知轮廓的背景混色链。
        .graphicsLayer {
            compositingStrategy = CompositingStrategy.Offscreen
        }
        .drawWithContent {
            drawSpotlight?.invoke(this)
            drawContent()
        }
    return CouiMenuMaterial(drawing, spotlightInput)
}
