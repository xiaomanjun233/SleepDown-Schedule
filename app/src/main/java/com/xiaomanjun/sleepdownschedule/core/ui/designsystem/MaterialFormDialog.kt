package com.xiaomanjun.sleepdownschedule.core.ui.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.xiaomanjun.sleepdownschedule.ScheduleConfigEntity
import com.xiaomanjun.sleepdownschedule.glass.ui.appUsesDarkTheme

/** The host owns the window; the form keeps validation and save state. */
@Composable
internal fun MaterialFormDialog(
    config: ScheduleConfigEntity,
    onDismissRequest: () -> Unit,
    visible: Boolean = true,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val maxHeight = with(density) { LocalWindowInfo.current.containerSize.height.toDp() * 0.82f }
    if (LocalNativeAlertRenderer.current?.invoke(
            NativeAlertRequest(
                "", "", emptyList(), appUsesDarkTheme(config), onDismissRequest,
                maxHeight, false, content, visible, deferDismissUntilHost = true
            )
        ) == true
    ) return
    Dialog(onDismissRequest, properties = DialogProperties(usePlatformDefaultWidth = false), content = content)
}
