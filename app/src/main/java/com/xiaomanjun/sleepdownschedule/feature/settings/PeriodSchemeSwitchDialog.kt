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
    onConfirm: (PeriodAlignmentMode) -> Unit
) {
    var mode by remember(name, targetPeriods) { mutableStateOf(PeriodAlignmentMode.INDEX) }
    val preview = remember(courses, currentConfig, currentPeriods, targetConfig, targetPeriods, mode) {
        runCatching { previewCourseAlignment(courses, currentConfig, currentPeriods,
            targetConfig.copy(periodAlignmentMode = mode), targetPeriods) }
    }
    val impact = preview.getOrNull()
    LiquidAlertDialog(
        title = "切换作息",
        message = "",
        actions = listOf(
            LiquidAlertAction("取消", LiquidAlertActionStyle.Secondary, onClick = onDismiss),
            LiquidAlertAction("确认切换", LiquidAlertActionStyle.Primary,
                enabled = impact != null && impact.conflicts.isEmpty(), onClick = { onConfirm(mode) })
        ),
        backdrop = backdrop,
        config = config,
        onDismissRequest = onDismiss,
        messageMaxHeight = SleepDownDesignTokens.CenteredDialog.SelectionContentMaxHeight,
        scrollableMessageContent = true,
        messageContent = {
            val foreground = sleepDownPanelForegroundColor(config)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(name, style = MaterialTheme.typography.bodySmall,
                    color = foreground.copy(alpha = 0.62f))
                Column(modifier = Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(
                        PeriodAlignmentMode.INDEX to ("按原节次对齐" to "使用相同编号的整节时间"),
                        PeriodAlignmentMode.TIME to ("按实际时间对齐" to "使用与原始时间重叠的整节时间")
                    ).forEach { (value, labels) ->
                        LiquidDialogChoiceCard(
                            title = labels.first,
                            selected = mode == value,
                            config = config,
                            onClick = { mode = value },
                            description = labels.second
                        )
                    }
                }
                Text("自定义时间保持不变", style = MaterialTheme.typography.bodySmall,
                    color = foreground.copy(alpha = 0.62f))
                Text(impact?.summary() ?: (preview.exceptionOrNull()?.message ?: "无法预览切换"),
                    style = MaterialTheme.typography.bodyMedium, color = foreground.copy(alpha = 0.68f))
            }
        }
    )
}
