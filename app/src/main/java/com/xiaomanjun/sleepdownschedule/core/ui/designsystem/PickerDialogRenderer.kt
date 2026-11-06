package com.xiaomanjun.sleepdownschedule.core.ui.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf

internal data class PickerDialogRequest(
    val show: Boolean, val title: String, val dark: Boolean,
    val onDismiss: () -> Unit, val onFinished: (() -> Unit)?,
    val titleAction: (@Composable () -> Unit)?,
    val actions: List<LiquidAlertAction>?,
    val bottomActions: (@Composable () -> Unit)?,
    val scrollable: Boolean,
    val content: @Composable () -> Unit
)

internal val LocalPickerDialogRenderer = compositionLocalOf<(@Composable (PickerDialogRequest) -> Boolean)?> { null }
