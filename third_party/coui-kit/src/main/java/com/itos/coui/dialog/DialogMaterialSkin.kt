package com.itos.coui.dialog

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import com.itos.coui.backdrop.CouiBackdrop

/** Drawing/press feedback only. A skin cannot replace the window, layout or action callback. */
interface DialogMaterialSkin {
    @Composable
    fun surface(backdrop: CouiBackdrop?, shape: Shape, dark: Boolean, observeMotion: () -> Unit): Modifier

    @Composable
    fun button(backdrop: CouiBackdrop?, shape: Shape, dark: Boolean, enabled: Boolean,
        surface: Color, interaction: MutableInteractionSource): Modifier
}

val LocalDialogMaterialSkin = compositionLocalOf<DialogMaterialSkin?> { null }
internal val LocalDialogUnderlay = compositionLocalOf<CouiBackdrop?> { null }
