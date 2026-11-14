package com.xiaomanjun.sleepdownschedule.core.ui.designsystem

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit

/** Typography and action dimensions of the shared dialog host. */
internal data class ControlPickerStyle(
    val titleSize: TextUnit,
    val titleWeight: FontWeight,
    val buttonHeight: Dp,
    val buttonMinWidth: Dp,
    val buttonTextSize: TextUnit,
    val buttonWeight: FontWeight
)

internal val LocalControlPickerStyle = compositionLocalOf<ControlPickerStyle?> { null }
