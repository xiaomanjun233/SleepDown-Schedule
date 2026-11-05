package com.itos.coui.topappbar

import android.graphics.BlendMode
import android.graphics.ColorSpace
import android.graphics.LinearGradient
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.itos.coui.backdrop.CouiBackdrop
import com.itos.coui.backdrop.CouiBackdropEffectScope
import com.itos.coui.backdrop.couiBackdrop
import com.itos.coui.backdrop.drawCouiBackdrop
import com.itos.coui.backdrop.rememberCouiBackdrop
import com.itos.coui.backdrop.LocalCouiBackdrop
import com.itos.coui.effect.CouiCoverageBlurEffect
import com.itos.coui.effect.drawCouiGradientBackdrop
import com.itos.coui.theme.CouiTheme
import com.itos.coui.theme.LocalCouiMaterialEnabled

internal val LocalCouiToolbarBackdrop = staticCompositionLocalOf<CouiBackdrop?> { null }
internal val LocalCouiToolbarOverlay = staticCompositionLocalOf<SharedTransitionScope?> { null }

private const val FadeStopCount = 65
private val BlurLayers = listOf(
    Triple(3.dp, 0.68f, 1f),
    Triple(7.dp, 0.44f, 0.82f),
    Triple(12.dp, 0.28f, 0.68f),
).map { (radius, start, end) ->
    radius to FloatArray(FadeStopCount) { index ->
        start + (end - start) * index / (FadeStopCount - 1f)
    }
}

private val GradientColorSpace = ColorSpace.get(ColorSpace.Named.EXTENDED_SRGB)
// 三层遮罩共用透明度曲线; 颜色和各层节点位置不随栏高变化, 只在初始化时生成。
private val FadeColors = LongArray(FadeStopCount) { index ->
    val progress = index / (FadeStopCount - 1f)
    val smoothProgress = progress * progress * (3f - 2f * progress)
    // 直接打包高精度透明度, 避免先舍入到 8 位。
    android.graphics.Color.pack(0f, 0f, 0f, 1f - smoothProgress, GradientColorSpace)
}

/**
 * 背景从标题栏顶部开始并向下延伸，不改变布局或触摸高度。
 * gradientSpeed 为 null 时保留项目固定渐隐；正值选择全高原厂幂曲线。
 * gradientColor 非透明时叠加主题背景渐变，其 alpha 与预设透明度相乘；dark 默认跟随当前主题。
 * topExtension 在测量时读取，背景向上延伸并平移，不改变内容占位或触摸范围。
 */
@Composable
fun TopAppBarSurface(
    modifier: Modifier = Modifier,
    backdrop: CouiBackdrop = checkNotNull(LocalCouiBackdrop.current) {
        "顶栏需要显式 backdrop 或 ProvideCouiBackdrop 作用域"
    },
    fadeHeight: androidx.compose.ui.unit.Dp = CouiTopAppBarDefaults.fadeHeight,
    referenceBlurRadius: androidx.compose.ui.unit.Dp = CouiTopAppBarDefaults.referenceBlurRadius,
    bottomExtension: androidx.compose.ui.unit.Dp = CouiTopAppBarDefaults.bottomExtension,
    backgroundAlpha: Float = 1f,
    gradientSpeed: Float? = null,
    gradientColor: Color = Color.Transparent,
    dark: Boolean = CouiTheme.isDark,
    topExtension: () -> androidx.compose.ui.unit.Dp = { 0.dp },
    content: @Composable () -> Unit,
) {
    if (!LocalCouiMaterialEnabled.current || Build.VERSION.SDK_INT < 31) {
        Box(modifier, content = { content() })
        return
    }
    val density = LocalDensity.current.density
    val destination = rememberCouiBackdrop(parent = backdrop)
    val backgroundModifier = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Modifier.drawCouiGradientBackdrop(
            backdrop = backdrop,
            fadeHeightPx = fadeHeight.value * density,
            referenceBlurRadiusPx = referenceBlurRadius.value * density,
            gradientSpeed = gradientSpeed,
        )
    } else {
        val blurEffects = remember(density) {
            BlurLayers.map { (radius, _) -> CouiCoverageBlurEffect(radius.value * density) }
        }
        Modifier.drawCouiBackdrop(
            backdrop = backdrop,
            shape = { RectangleShape },
            effects = { progressiveBlur(blurEffects, fadeHeight) },
        )
    }
    val gradient = remember(gradientColor, dark) {
        // GradientBackgroundDrawable 从下到上：浅色 0/.7/1，深色 0/.4。
        fun color(alpha: Float) = gradientColor.copy(alpha = gradientColor.alpha * ((alpha * 255).toInt() / 255f))
        if (dark) Brush.verticalGradient(0f to color(.4f), 1f to color(0f))
        else Brush.verticalGradient(0f to color(1f), .6f to color(.7f), 1f to color(0f))
    }
    SharedTransitionScope { overlayModifier ->
        Layout(
            modifier = modifier.then(overlayModifier),
            content = {
                Box(Modifier.couiBackdrop(destination)) {
                    Box(Modifier.matchParentSize().graphicsLayer { alpha = backgroundAlpha.coerceIn(0f, 1f) }) {
                        Spacer(Modifier.matchParentSize().then(backgroundModifier))
                        if (gradientColor.alpha > 0f) Spacer(Modifier.matchParentSize().background(gradient))
                    }
                }
                Box {
                    CompositionLocalProvider(
                        LocalCouiToolbarBackdrop provides destination,
                        LocalCouiToolbarOverlay provides this@SharedTransitionScope,
                        content = content,
                    )
                }
            },
        ) { measurables, constraints ->
            val topBar = measurables[1].measure(constraints.copy(minHeight = 0))
            val topExtensionPx = topExtension().roundToPx()
            val background = measurables[0].measure(
                Constraints.fixed(
                    width = topBar.width,
                    height = topBar.height + topExtensionPx + bottomExtension.roundToPx(),
                )
            )
            // 折叠时固定高度的背景随顶栏上移，渐进曲线不随当前栏高重新拉伸。
            layout(topBar.width, topBar.height) {
                background.placeRelative(0, -topExtensionPx)
                topBar.placeRelative(0, 0)
            }
        }
    }
}

private fun CouiBackdropEffectScope.progressiveBlur(blurEffects: List<CouiCoverageBlurEffect>, fadeHeight: androidx.compose.ui.unit.Dp) {
    if (!size.isSpecified || size.height <= 0f) return

    // CouiBackdrop 会平移采样原点; 遮罩使用相同偏移并保留平台模糊的采样外沿。
    padding = blurEffects.maxOf { it.padding }
    var combined = RenderEffect.createOffsetEffect(0f, 0f)
    for ((index, layer) in BlurLayers.withIndex()) {
        val positions = layer.second
        val blurred = blurEffects[index].effect
        val mask = fadeEffect(positions, fadeHeight)
        val maskedBlur = RenderEffect.createBlendModeEffect(blurred, mask, BlendMode.DST_IN)
        // 在滤镜内叠到不透明采样底图上, 不再由 Compose 分别输出三张半透明图层。
        combined = RenderEffect.createBlendModeEffect(combined, maskedBlur, BlendMode.SRC_OVER)
    }
    // 保留采样内容本身的颜色, 避免主题底色覆盖不同表面时形成额外的明暗渐变。
    renderEffect = combined.asComposeRenderEffect()
}

private fun CouiBackdropEffectScope.fadeEffect(positions: FloatArray, fadeHeight: androidx.compose.ui.unit.Dp): RenderEffect {
    // 渐变沿固定长度展开并锚定背景下沿, 折叠时只平移, 不随栏高压缩。
    // 起点允许超出顶部; 由渐变自身延续端点颜色, 不裁短坐标区间。
    val gradientBottom = padding + size.height
    val gradientTop = gradientBottom - fadeHeight.toPx()
    return RenderEffect.createShaderEffect(
        LinearGradient(
            0f,
            gradientTop,
            0f,
            gradientBottom,
            FadeColors,
            positions,
            Shader.TileMode.CLAMP,
        )
    )
}
