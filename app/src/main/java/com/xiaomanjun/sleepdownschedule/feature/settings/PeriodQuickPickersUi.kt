package com.xiaomanjun.sleepdownschedule.feature.settings

import androidx.compose.runtime.Composable
import com.kyant.backdrop.Backdrop
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.*
import com.xiaomanjun.sleepdownschedule.model.ScheduleConfigEntity

/** The same fixed action row for every period and adjustment pickers. */
@Composable
internal fun PeriodPickerActions(
    backdrop: Backdrop?, config: ScheduleConfigEntity,
    onCancel: () -> Unit, onConfirm: () -> Unit,
    cancelText: String = "取消", confirmText: String = "确定"
) {
    LiquidAlertActions(
        actions = listOf(
            LiquidAlertAction(cancelText, LiquidAlertActionStyle.Cancel, onClick = onCancel),
            LiquidAlertAction(confirmText, LiquidAlertActionStyle.Primary, onClick = onConfirm)
        ),
        backdrop = backdrop,
        config = config
    )
}
