package com.xiaomanjun.sleepdownschedule.core.ui.designsystem

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
    val preserveMaterialWhenDisabled: Boolean = false
)

internal val LocalNativeCapsuleButtonRenderer = compositionLocalOf<
    (@Composable (NativeCapsuleButtonRequest, @Composable RowScope.() -> Unit) -> Boolean)?
> { null }
