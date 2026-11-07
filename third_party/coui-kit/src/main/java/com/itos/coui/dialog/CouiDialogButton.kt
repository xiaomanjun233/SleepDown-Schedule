package com.itos.coui.dialog

import android.graphics.Paint
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Density
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

enum class CouiDialogActionStyle { Secondary, Primary }

@Immutable
data class CouiDialogActionColors(val surface: Color, val content: Color, val disabledSurface: Color, val disabledContent: Color)

internal object DialogButtonDefaults {
    /** COUIButton 自动圆角：等宽高为圆，其他尺寸采用项目平滑胶囊。 */
    val shape: Shape = object : Shape {
        private val circle = CouiShape(999.dp, 2f)
        override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density) =
            (if (size.width == size.height) circle else CouiCapsuleShape).createOutline(size, layoutDirection, density)
    }
    /** 关闭弹窗材质时使用中性底色，材质分支使用半透明白色。 */
    fun secondaryColors(dark: Boolean, material: Boolean = false) = CouiDialogActionColors(
        Color(if (material) { if (dark) 0x33FFFFFF else 0xA6FFFFFF } else { if (dark) 0xFF353535 else 0xFFEBEBEB }), Color(if (dark) 0xE6FFFFFF else 0xE6000000),
        Color(if (dark) 0x1AFFFFFF else 0x14000000), Color(if (dark) 0x28FFFFFF else 0x42000000))
    /** accent 由宿主主题提供；主按钮填色与文字色不读取宿主的 Typography。 */
    fun primaryColors(accent: Color) = CouiDialogActionColors(accent, Color.White,
        accent.copy(alpha = 77f / 255f), Color(0x4DFFFFFF))
}

/** 弹窗内部操作按钮，复用项目形状、运动、轮廓和跟手光。 */
@Composable
internal fun DialogButton(
    modifier: Modifier = Modifier,
    text: String,
    onClick: () -> Unit,
    dark: Boolean,
    enabled: Boolean = true,
    style: CouiDialogActionStyle = CouiDialogActionStyle.Secondary,
    material: Boolean = false,
    colors: CouiDialogActionColors? = null,
    interactionSource: MutableInteractionSource? = null,
) {
    check(!material || Build.VERSION.SDK_INT >= 33) { "按钮材质表面要求 Android 13 或更高版本" }
    val resolvedColors = colors ?: if (style == CouiDialogActionStyle.Secondary) DialogButtonDefaults.secondaryColors(dark, material)
        else error("主操作必须由宿主显式提供配色")
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val motion = rememberCouiTouchMotion(enabled)
    val density = LocalDensity.current.density
    val press = rememberCouiPressMotion()
    val (lightInput, drawLight) = rememberCouiSpotlight(interactive = enabled, dark = dark,
        preset = if (style == CouiDialogActionStyle.Primary) SpotlightPreset.OpaqueMedium1 else SpotlightPreset.OpaqueMedium2,
        cancelOnConsumedMove = true)
    Box(modifier.heightIn(min = CouiDialogDefaults.buttonHeight).graphicsLayer {
        scaleX = motion.scaleX.value + press.scale.value - 1f
        scaleY = motion.scaleY.value + press.scale.value - 1f
        translationX = motion.translationX.value
        translationY = motion.translationY.value
    }.couiTouchMotion(motion, enabled, cancelOnConsumedMove = true)
        .couiPressMotion(press, enabled, cancelOnConsumedMove = true).then(lightInput)
        .clip(DialogButtonDefaults.shape).drawWithContent {
            drawContent()
            drawLight?.invoke(this)
        }.dialogButtonSurface(dark, enabled, if (enabled) resolvedColors.surface else resolvedColors.disabledSurface, style)
        .couiButtonFeedback(dark, source, enabled, pressFeedback = !material)
        .clickable(source, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
        .padding(horizontal = CouiDialogDefaults.buttonHorizontalPadding, vertical = 4.dp), contentAlignment = Alignment.Center) {
        DialogText(text = text, kind = DialogTextKind.Button, color = if (enabled) resolvedColors.content else resolvedColors.disabledContent)
    }
}

/** 弹窗操作复用公共轮廓后端。 */
@Composable
private fun Modifier.dialogButtonSurface(dark: Boolean, enabled: Boolean, surfaceColor: Color,
    style: CouiDialogActionStyle = CouiDialogActionStyle.Secondary): Modifier {
    val glow = LocalCouiContourGlowEnabled.current
    val contour = remember(glow) {
        if (glow && Build.VERSION.SDK_INT >= 33) CouiContour(style = CouiContourStyle.Button) else null
    }
    val paint = remember { Paint(Paint.ANTI_ALIAS_FLAG) }
    return drawWithContent {
        drawRect(surfaceColor)
        if (Build.VERSION.SDK_INT >= 33 && contour != null) {
            val primary = style == CouiDialogActionStyle.Primary
            val alpha = if (primary) {
                if (dark) { if (enabled) .25f else .05f } else .3f
            } else if (dark) { if (enabled) .1f else .03f } else { if (enabled) .8f else .24f }
            val inner = if (primary) {
                if (enabled) .1f else if (dark) .06f else .04f
            } else if (dark) .01f else .05f
            paint.shader = contour.shader(size, density, size.minDimension / 2f,
                if (size.width == size.height) 2f else 1f, Color.Transparent, inner, alpha, 1f,
                (if (primary) 2.5f else 3f) * density)
            drawIntoCanvas { it.nativeCanvas.drawRect(0f, 0f, size.width, size.height, paint) }
        }
        drawContent()
    }
}
