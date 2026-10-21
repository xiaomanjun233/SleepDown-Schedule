package com.xiaomanjun.sleepdownschedule.core.ui.designsystem

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** App assembly supplies vendor UI; the shared design system does not depend on a feature. */
internal data class NativeAlertRequest(
    val title: String,
    val message: String,
    val actions: List<LiquidAlertAction>,
    val dark: Boolean,
    val onDismissRequest: () -> Unit,
    val messageMaxHeight: Dp,
    val scrollableMessageContent: Boolean,
    val messageContent: (@Composable () -> Unit)?,
    val visible: Boolean = true,
    val deferDismissUntilHost: Boolean = false,
    val onDismissFinished: (() -> Unit)? = null,
    val contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    val windowWidth: Dp? = null
)

internal val LocalNativeAlertRenderer = compositionLocalOf<(@Composable (NativeAlertRequest) -> Boolean)?> { null }
internal val LocalNativeDialogSurface = compositionLocalOf { false }
internal val LocalNativeDialogWindow = compositionLocalOf<android.view.Window?> { null }
/** Stable available page size; a wrap-content dialog must never size itself from its own height. */
internal val LocalNativeDialogHostSize = compositionLocalOf<androidx.compose.ui.unit.IntSize?> { null }
