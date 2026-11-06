package com.itos.coui.dialog

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.itos.coui.button.*
import com.itos.coui.backdrop.LocalCouiSurfaceBackdrop
import com.itos.coui.effect.couiControlMaterial
import com.itos.coui.topappbar.CouiTopAppBarButtonDefaults
import com.itos.coui.floatingactionbutton.CouiFabDefaults

enum class CouiDialogActionStyle { Secondary, Primary }

@Immutable
data class CouiDialogActionColors(val surface: Color, val content: Color, val disabledSurface: Color, val disabledContent: Color)

internal object DialogButtonDefaults {
    val shape get() = CouiButtonDefaults.shape
    fun secondaryColors(dark: Boolean, material: Boolean = false): CouiDialogActionColors =
        CouiButtonDefaults.secondaryColors(dark, material).let {
            CouiDialogActionColors(it.surface, it.content, it.disabledSurface, it.disabledContent)
        }
    fun primaryColors(accent: Color): CouiDialogActionColors = CouiButtonDefaults.primaryColors(accent).let {
        CouiDialogActionColors(it.surface, it.content, it.disabledSurface, it.disabledContent)
    }
}

/** 兼容上游 Dialog 配色 API，完整输入与绘制统一委托已迁移的普通按钮。 */
@Composable
internal fun DialogButton(modifier: Modifier = Modifier, text: String, onClick: () -> Unit,
    dark: Boolean, enabled: Boolean = true, style: CouiDialogActionStyle = CouiDialogActionStyle.Secondary,
    material: Boolean = false, colors: CouiDialogActionColors? = null,
    interactionSource: MutableInteractionSource? = null) {
    val resolved = colors ?: if (style == CouiDialogActionStyle.Secondary)
        DialogButtonDefaults.secondaryColors(dark, material) else error("主操作必须提供配色")
    val backdrop = LocalCouiSurfaceBackdrop.current?.takeIf { enabled }
    val background = if (backdrop != null) {
        val buttonMaterial = (if (style == CouiDialogActionStyle.Primary)
            CouiFabDefaults.material(resolved.surface, dark) else CouiTopAppBarButtonDefaults.systemMaterial(dark, true)).copy(blurRadius = 7f)
        Modifier.couiControlMaterial(backdrop, CouiButtonDefaults.shape, buttonMaterial)
    } else Modifier
    CouiButton(modifier = modifier, text = text, onClick = onClick, dark = dark, enabled = enabled,
        style = if (style == CouiDialogActionStyle.Primary) CouiButtonStyle.Primary else CouiButtonStyle.Secondary,
        backgroundModifier = background,
        colors = CouiButtonColors(if (backdrop != null) Color.Transparent else resolved.surface,
            resolved.content, resolved.disabledSurface, resolved.disabledContent),
        interactionSource = interactionSource)
}
