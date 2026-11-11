package com.xiaomanjun.sleepdownschedule.core.ui.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp

/** One window contract for both materials; feature code supplies content and actions. */
internal data class AlertDialogRequest(
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
)

internal val LocalControlDialogSurface = compositionLocalOf { false }
/** 特殊选择器仍使用同一 Kit 操作区；业务只提供动作。 */
internal val LocalControlDialogActionBar = compositionLocalOf<(@Composable (List<LiquidAlertAction>, androidx.compose.ui.Modifier) -> Unit)?> { null }
internal val LocalControlDialogHeading = compositionLocalOf<(@Composable (String) -> Unit)?> { null }
internal val LocalControlDialogWindow = compositionLocalOf<android.view.Window?> { null }
/** Stable available page size; a wrap-content dialog must never size itself from its own height. */
internal val LocalControlDialogHostSize = compositionLocalOf<androidx.compose.ui.unit.IntSize?> { null }
