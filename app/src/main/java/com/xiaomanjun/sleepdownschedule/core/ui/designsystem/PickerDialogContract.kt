package com.xiaomanjun.sleepdownschedule.core.ui.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.PaddingValues

/** 自定义正文的 Kit 留白；Wheel/Calendar 已有自身几何，不使用这层。 */
internal val CouiPickerFormPadding = PaddingValues(
    start = com.itos.coui.dialog.CouiDialogDefaults.contentPadding,
    end = com.itos.coui.dialog.CouiDialogDefaults.contentPadding,
    top = com.itos.coui.picker.CouiPickerDefaults.padding,
    bottom = com.itos.coui.dialog.CouiDialogDefaults.messageBottom)

internal data class PickerDialogRequest(
    val show: Boolean, val title: String, val dark: Boolean,
    val onDismiss: () -> Unit, val onFinished: (() -> Unit)?,
    val titleAction: (@Composable () -> Unit)?,
    val actions: List<LiquidAlertAction>?,
    val bottomActions: (@Composable () -> Unit)?,
    val scrollable: Boolean,
    val adjustForIme: Boolean = false,
    val content: @Composable () -> Unit,
    val message: String? = null,
)
