package com.itos.coui.button

import android.graphics.Paint
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.itos.coui.effect.CouiContour
import com.itos.coui.effect.CouiContourStyle
import com.itos.coui.effect.LocalCouiContourGlowEnabled
import com.itos.coui.effect.SpotlightPreset
import com.itos.coui.effect.couiButtonFeedback
import com.itos.coui.effect.rememberCouiSpotlight
import com.itos.coui.motion.couiPressMotion
import com.itos.coui.motion.couiTouchMotion
import com.itos.coui.motion.rememberCouiPressMotion
import com.itos.coui.motion.rememberCouiTouchMotion
import com.itos.coui.shape.CouiCapsuleShape
import com.itos.coui.shape.CouiShape
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text

enum class CouiButtonStyle { Secondary, Primary }

@Immutable
data class CouiButtonColors(
    val surface: Color,
    val content: Color,
    val disabledSurface: Color,
    val disabledContent: Color,
)

object CouiButtonDefaults {
    /** COUIButton 自动圆角：等宽高为圆，其他尺寸采用项目平滑胶囊。 */
    val shape: Shape = object : Shape {
        private val circle = CouiShape(999.dp, 2f)
        override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density) =
            (if (size.width == size.height) circle else CouiCapsuleShape).createOutline(size, layoutDirection, density)
    }
    val minHeight = 44.dp
    val horizontalPadding = 12.dp

    /** 普通页面使用 container8 over elevated；材质弹窗显式使用半透明白色。 */
    fun secondaryColors(dark: Boolean, material: Boolean = false) = CouiButtonColors(
        Color(
            if (material) {
                if (dark) 0x33FFFFFF else 0xA6FFFFFF
            } else {
                if (dark) 0xFF353535 else 0xFFEBEBEB
            }
        ),
        Color(if (dark) 0xE6FFFFFF else 0xE6000000),
        Color(if (dark) 0x1AFFFFFF else 0x14000000),
        Color(if (dark) 0x28FFFFFF else 0x42000000),
    )

    /** accent 由宿主主题提供；主按钮填色与文字色不读取宿主的 Typography。 */
    fun primaryColors(accent: Color) = CouiButtonColors(
        accent,
        Color.White,
        accent.copy(alpha = 77f / 255f),
        Color(0x4DFFFFFF),
    )
}

/** 完整普通按钮：受控启用态、语义、项目形状、材质轮廓、按压形变与跟手光。 */
@Composable
fun CouiButton(
    modifier: Modifier = Modifier,
    text: String,
    onClick: () -> Unit,
    dark: Boolean,
    enabled: Boolean = true,
    style: CouiButtonStyle = CouiButtonStyle.Secondary,
    colors: CouiButtonColors? = null,
    backgroundModifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource? = null,
) {
    val resolvedColors = colors ?: if (style == CouiButtonStyle.Secondary) CouiButtonDefaults.secondaryColors(dark)
    else error("主按钮必须由宿主显式提供 primaryColors")
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val motion = rememberCouiTouchMotion(enabled)
    val press = rememberCouiPressMotion()
    val (lightInput, drawLight) = rememberCouiSpotlight(
        interactive = enabled,
        dark = dark,
        preset = if (style == CouiButtonStyle.Primary) SpotlightPreset.OpaqueMedium1 else SpotlightPreset.OpaqueMedium2,
        cancelOnConsumedMove = true,
    )
    Box(modifier.heightIn(min = CouiButtonDefaults.minHeight).graphicsLayer {
        scaleX = motion.scaleX.value + press.scale.value - 1f
        scaleY = motion.scaleY.value + press.scale.value - 1f
        translationX = motion.translationX.value
        translationY = motion.translationY.value
    }.couiTouchMotion(motion, enabled, cancelOnConsumedMove = true)
        .couiPressMotion(press, enabled, cancelOnConsumedMove = true).then(lightInput)
        .clip(CouiButtonDefaults.shape).then(backgroundModifier).drawWithContent {
            drawContent()
            drawLight?.invoke(this)
        }.couiButtonSurface(
            dark = dark,
            enabled = enabled,
            surfaceColor = if (enabled) resolvedColors.surface else resolvedColors.disabledSurface,
            style = style,
        ).couiButtonFeedback(dark, source, enabled, pressFeedback = Build.VERSION.SDK_INT < 33)
        .clickable(source, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
        .padding(horizontal = CouiButtonDefaults.horizontalPadding, vertical = 4.dp), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            kind = CouiTextKind.Button,
            color = if (enabled) resolvedColors.content else resolvedColors.disabledContent,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * 内容槽版本：供现有胶囊按钮兼容入口复用同一 COUI 填色、轮廓、按压和跟手光。
 * 高度、内边距、形状和内容完全由宿主决定；不替宿主设置内容颜色。
 */
@Composable
fun CouiCapsuleButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    dark: Boolean,
    enabled: Boolean = true,
    style: CouiButtonStyle = CouiButtonStyle.Secondary,
    colors: CouiButtonColors,
    height: Dp = CouiButtonDefaults.minHeight,
    contentPadding: PaddingValues = PaddingValues(horizontal = CouiButtonDefaults.horizontalPadding, vertical = 4.dp),
    shape: Shape = CouiButtonDefaults.shape,
    preserveMaterialWhenDisabled: Boolean = false,
    surfaceSheen: (() -> Brush?)? = null,
    backgroundModifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource? = null,
    underlayModifier: Modifier = Modifier,
    pressFeedback: Boolean = Build.VERSION.SDK_INT < 33,
    content: @Composable RowScope.() -> Unit,
) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val surface = rememberCouiCapsuleMaterial(dark, enabled, style, colors, shape,
        preserveMaterialWhenDisabled, surfaceSheen, backgroundModifier, source, underlayModifier, pressFeedback)
    CapsuleButtonLayout(modifier, surface, height, contentPadding, source, enabled,
        onClick = onClick, content = content)
}

/** A skin only: no content, size, clickable or business callback. */
@Composable
fun rememberCouiCapsuleMaterial(
    dark: Boolean,
    enabled: Boolean,
    style: CouiButtonStyle,
    colors: CouiButtonColors,
    shape: Shape,
    preserveMaterialWhenDisabled: Boolean = false,
    surfaceSheen: (() -> Brush?)? = null,
    backgroundModifier: Modifier = Modifier,
    source: MutableInteractionSource,
    underlayModifier: Modifier = Modifier,
    pressFeedback: Boolean = Build.VERSION.SDK_INT < 33,
): Modifier {
    val motion = rememberCouiTouchMotion(enabled)
    val press = rememberCouiPressMotion()
    val (lightInput, drawLight) = rememberCouiSpotlight(
        interactive = enabled,
        dark = dark,
        preset = if (style == CouiButtonStyle.Primary) SpotlightPreset.OpaqueMedium1 else SpotlightPreset.OpaqueMedium2,
        cancelOnConsumedMove = true,
    )
    return Modifier.graphicsLayer {
            scaleX = motion.scaleX.value + press.scale.value - 1f
            scaleY = motion.scaleY.value + press.scale.value - 1f
            translationX = motion.translationX.value
            translationY = motion.translationY.value
        }.couiTouchMotion(motion, enabled, cancelOnConsumedMove = true)
            .couiPressMotion(press, enabled, cancelOnConsumedMove = true).then(lightInput)
            .then(underlayModifier).clip(shape).then(backgroundModifier).drawWithContent {
                if (backgroundModifier != Modifier) {
                    // A graphicsLayer-only animation otherwise stretches the cached backdrop.
                    motion.scaleX.value
                    motion.scaleY.value
                    motion.translationX.value
                    motion.translationY.value
                    press.scale.value
                }
                drawContent()
                drawLight?.invoke(this)
            }.couiButtonSurface(
                dark = dark,
                enabled = enabled,
                surfaceColor = if (enabled || preserveMaterialWhenDisabled) colors.surface else colors.disabledSurface,
                style = style,
                surfaceSheen = surfaceSheen,
            ).couiButtonFeedback(dark, source, enabled, pressFeedback = pressFeedback)
}

/** 同一填色/轮廓装配亦供既有弹窗装饰 API 使用；不复制描边算法。 */
@Composable
internal fun Modifier.couiButtonSurface(
    dark: Boolean,
    enabled: Boolean,
    surfaceColor: Color,
    style: CouiButtonStyle = CouiButtonStyle.Secondary,
    surfaceSheen: (() -> Brush?)? = null,
): Modifier {
    val glow = LocalCouiContourGlowEnabled.current
    val contour = remember(glow) {
        if (glow && Build.VERSION.SDK_INT >= 33) CouiContour(style = CouiContourStyle.Button) else null
    }
    val paint = remember { Paint(Paint.ANTI_ALIAS_FLAG) }
    return drawWithContent {
        drawRect(surfaceColor)
        surfaceSheen?.invoke()?.let { drawRect(it) }
        if (Build.VERSION.SDK_INT >= 33 && contour != null) {
            val primary = style == CouiButtonStyle.Primary
            val alpha = if (primary) {
                if (dark) {
                    if (enabled) .25f else .05f
                } else .3f
            } else if (dark) {
                if (enabled) .1f else .03f
            } else {
                if (enabled) .8f else .24f
            }
            val inner = if (primary) {
                if (enabled) .1f else if (dark) .06f else .04f
            } else if (dark) .01f else .05f
            paint.shader = contour.shader(
                size, density, size.minDimension / 2f,
                if (size.width == size.height) 2f else 1f, Color.Transparent, inner, alpha, 1f,
                (if (primary) 2.5f else 3f) * density,
            )
            drawIntoCanvas { it.nativeCanvas.drawRect(0f, 0f, size.width, size.height, paint) }
        }
        drawContent()
    }
}
