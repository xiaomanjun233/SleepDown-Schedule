package com.xiaomanjun.sleepdownschedule.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import top.yukonga.miuix.kmp.basic.RadioButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.*
import com.xiaomanjun.sleepdownschedule.domain.schedule.*
import com.xiaomanjun.sleepdownschedule.model.*
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Detached preview; choosing a radio item never writes a course or creates a public record. */
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
    SleepDownPickerDialog(true, "切换作息", onDismiss, backdrop, config, scrollableContent = true,
        bottomActions = {
            LiquidAlertActions(listOf(
                LiquidAlertAction("取消", LiquidAlertActionStyle.Secondary, onClick = onDismiss),
                LiquidAlertAction("确认切换", LiquidAlertActionStyle.Primary,
                    enabled = impact != null && impact.conflicts.isEmpty(), onClick = { onConfirm(mode) })
            ), backdrop, config)
        }) {
        Text(name, style = MiuixTheme.textStyles.headline1)
        listOf(PeriodAlignmentMode.INDEX to ("按原节次对齐" to "使用相同编号的整节时间"),
            PeriodAlignmentMode.TIME to ("按实际时间对齐" to "使用与原始时间重叠的整节时间")).forEach { (value, labels) ->
            Row(Modifier.fillMaxWidth().selectable(mode == value, role = Role.RadioButton,
                onClick = { mode = value }).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(labels.first, style = MiuixTheme.textStyles.body1)
                    Text(labels.second, style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                }
                RadioButton(selected = mode == value, onClick = null)
            }
        }
        Text("自定义时间保持不变。", style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        Text(impact?.summary() ?: (preview.exceptionOrNull()?.message ?: "无法预览切换"),
            style = MiuixTheme.textStyles.body1)
    }
}
