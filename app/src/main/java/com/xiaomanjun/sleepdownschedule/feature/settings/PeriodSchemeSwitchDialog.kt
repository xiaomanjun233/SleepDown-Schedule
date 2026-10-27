package com.xiaomanjun.sleepdownschedule.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.*
import com.xiaomanjun.sleepdownschedule.domain.schedule.*
import com.xiaomanjun.sleepdownschedule.model.*

/** Detached preview; changing the selected mode never writes a course or creates a public record. */
@Composable
internal fun PeriodSchemeSwitchDialog(
    name: String, courses: List<CourseEntity>, currentConfig: ScheduleConfigEntity,
    currentPeriods: List<PeriodEntity>, targetConfig: ScheduleConfigEntity, targetPeriods: List<PeriodEntity>,
    backdrop: Backdrop?, config: ScheduleConfigEntity, onDismiss: () -> Unit,
    onConfirm: (PeriodAlignmentMode) -> Unit,
    catalog: TimetableCatalog = TimetableCatalog()
) {
    val mode = PeriodAlignmentMode.INDEX
    val preview = remember(courses, currentConfig, currentPeriods, targetConfig, targetPeriods, mode, catalog) {
        runCatching { previewCourseAlignment(courses, currentConfig, currentPeriods,
            targetConfig.copy(periodAlignmentMode = mode,activeTimetableComboId=null), targetPeriods,catalog) }
    }
    val impact = preview.getOrNull()
    LiquidAlertDialog(
        title = "切换作息",
        message = "",
        actions = listOf(
            LiquidAlertAction("取消", LiquidAlertActionStyle.Cancel, onClick = onDismiss),
            LiquidAlertAction("确认切换", LiquidAlertActionStyle.Primary,
                enabled = impact != null, onClick = { onConfirm(mode) })
        ),
        backdrop = backdrop,
        config = config,
        onDismissRequest = onDismiss,
        messageMaxHeight = SleepDownDesignTokens.CenteredDialog.SelectionContentMaxHeight,
        scrollableMessageContent = true,
        isSelector = true,
        messageContent = {
            val foreground = sleepDownPanelForegroundColor(config)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(name, style = MaterialTheme.typography.bodySmall,
                    color = foreground.copy(alpha = 0.62f))
                Text("普通课程使用新作息的同编号节次。独立作息与自定义时间保持原安排，在固定网格中显示真实时间。", style = MaterialTheme.typography.bodySmall,
                    color = foreground.copy(alpha = 0.62f))
                Text(impact?.summary() ?: (preview.exceptionOrNull()?.message ?: "无法预览切换"),
                    style = MaterialTheme.typography.bodyMedium, color = foreground.copy(alpha = 0.68f))
            }
        }
    )
}
