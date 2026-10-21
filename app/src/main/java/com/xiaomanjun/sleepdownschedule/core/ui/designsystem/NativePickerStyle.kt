package com.xiaomanjun.sleepdownschedule.core.ui.designsystem

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit

/** Dimensions sampled from the installed platform dialog, in the current font configuration. */
internal data class NativePickerStyle(
    val titleSize: TextUnit,
    val titleWeight: FontWeight,
    val buttonHeight: Dp,
    val buttonMinWidth: Dp,
    val buttonTextSize: TextUnit,
    val buttonWeight: FontWeight
)

internal val LocalNativePickerStyle = compositionLocalOf<NativePickerStyle?> { null }
