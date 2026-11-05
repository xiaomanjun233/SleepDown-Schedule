package com.xiaomanjun.sleepdownschedule.core.ui.designsystem

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp

/** Shared button contract. Vendor loading/material/touch handling stays in the app framework. */
internal data class NativeCapsuleButtonRequest(
    val onClick: () -> Unit,
    val modifier: Modifier,
    val height: Dp,
    val contentPadding: PaddingValues,
    val dark: Boolean,
    val enabled: Boolean = true,
    val primaryColor: Color = Color.Unspecified,
    val acceptsGesture: (Size, Offset) -> Boolean = { _, _ -> true },
    val preserveMaterialWhenDisabled: Boolean = false,
    /** Sample card light using the native personalisation button's background RenderEffect. */
    val lightBackdrop: Boolean = false,
    val cardLightShadow: Boolean = false,
    /**
     * Wash drawn over the material and under the content, already clipped to the capsule.
     * Evaluated during draw so animated light spills do not recompose the button.
     */
    val surfaceSheen: (() -> Brush?)? = null
)

internal val LocalNativeCapsuleButtonRenderer = compositionLocalOf<
    (@Composable (NativeCapsuleButtonRequest, @Composable RowScope.() -> Unit) -> Boolean)?
> { null }
