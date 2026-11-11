package com.xiaomanjun.sleepdownschedule.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.CouiPickerFormPadding
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LiquidAlertAction
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LiquidAlertActionStyle
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.SleepDownPickerDialog
import com.xiaomanjun.sleepdownschedule.core.ui.settings.LiquidOptionTabs
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsDatePickerContent
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsDivider
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsGroup
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsInfoRow
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsPickerValueRow
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsToggleRow
import com.xiaomanjun.sleepdownschedule.domain.schedule.parseScheduleDate
import com.xiaomanjun.sleepdownschedule.model.ScheduleConfigEntity
import java.time.LocalDate

internal data class AdjustmentDraft(
    val date: String,
    val rest: Boolean,
    val source: String,
    val originalDate: String? = null,
    val label: String = "",
    val allDayPlaceholder: Boolean = false,
)

private enum class AdjustmentPickerPage { Details, TargetDate, SourceDate }

@Composable
internal fun HolidayYearDialog(
    show: Boolean,
    initialYear: Int,
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    onDismiss: () -> Unit,
    onDismissFinished: () -> Unit,
    onFetch: (Int) -> Unit,
) {
    var year by remember { mutableIntStateOf(initialYear) }
    SleepDownPickerDialog(
        show = show,
        title = "获取节假日与调休",
        message = "选择要获取的年份。",
        onDismissRequest = onDismiss,
        onDismissFinished = onDismissFinished,
        config = config,
        actions = listOf(
            LiquidAlertAction("取消", LiquidAlertActionStyle.Cancel, onClick = onDismiss),
            LiquidAlertAction("获取", LiquidAlertActionStyle.Primary) { onFetch(year) },
        ),
    ) {
        PeriodQuickSingleWheel(year, { year = it }, 2000..2100, { "${it} 年" })
    }
}

@Composable
internal fun AdjustmentEditorDialog(
    show: Boolean,
    saving: Boolean,
    draft: AdjustmentDraft,
    error: String?,
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    onDraftChange: (AdjustmentDraft) -> Unit,
    coursePreview: (String) -> String,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onDismissFinished: () -> Unit,
) {
    val targetDate = LocalDate.parse(draft.date)
    var page by remember { mutableStateOf(AdjustmentPickerPage.Details) }
    var selectedDate by remember { mutableStateOf(targetDate) }

    fun openDate(destination: AdjustmentPickerPage) {
        selectedDate = if (destination == AdjustmentPickerPage.SourceDate)
            parseScheduleDate(draft.source) ?: targetDate else targetDate
        page = destination
    }
    fun backToForm() { page = AdjustmentPickerPage.Details }

    val form = page == AdjustmentPickerPage.Details
    SleepDownPickerDialog(
        show = show,
        title = when (page) {
            AdjustmentPickerPage.Details -> if (draft.originalDate == null) "新增调休日" else "编辑调休安排"
            AdjustmentPickerPage.TargetDate -> "调休日期"
            AdjustmentPickerPage.SourceDate -> "原课程日期"
        },
        onDismissRequest = { if (!saving) { if (form) onCancel() else backToForm() } },
        onDismissFinished = onDismissFinished,
        config = config,
        contentPadding = if (form) CouiPickerFormPadding else PaddingValues(0.dp),
        contentTransitionKey = page,
        scrollableContent = true,
        smoothContentResize = true,
        actions = listOf(
            LiquidAlertAction(if (form) "取消" else "返回", LiquidAlertActionStyle.Cancel, enabled = !saving) {
                if (form) onCancel() else backToForm()
            },
            LiquidAlertAction(if (form) "确定" else "选用日期", LiquidAlertActionStyle.Primary, enabled = !saving) {
                if (form) onSave() else {
                    val date = selectedDate.toString()
                    onDraftChange(if (page == AdjustmentPickerPage.TargetDate) draft.copy(date = date)
                        else draft.copy(source = date))
                    backToForm()
                }
            },
        ),
        contentForState = { visiblePage ->
            if (visiblePage == AdjustmentPickerPage.Details) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        LiquidOptionTabs(
                            selectedIndex = if (draft.rest) 0 else 1,
                            labels = listOf("停课", "补课"),
                            backdrop = backdrop,
                            config = config,
                            width = maxWidth,
                            followAppTheme = true,
                            onSelected = { if (!saving) onDraftChange(draft.copy(rest = it == 0)) },
                        )
                    }
                    SettingsGroup(backdrop = null, config = config, topSpacing = 0.dp) {
                        SettingsPickerValueRow("调休日期", adjustmentDateLabel(targetDate),
                            onClick = { openDate(AdjustmentPickerPage.TargetDate) }, enabled = !saving)
                        if (draft.rest) {
                            SettingsToggleRow("全天占位", "无课也显示全天假期卡",
                                draft.allDayPlaceholder, backdrop, enabled = !saving) {
                                onDraftChange(draft.copy(allDayPlaceholder = it))
                            }
                        } else {
                            val source = parseScheduleDate(draft.source)
                            SettingsPickerValueRow("原课程日期", source?.let(::adjustmentDateLabel) ?: "请选择",
                                onClick = { openDate(AdjustmentPickerPage.SourceDate) }, enabled = !saving)
                            if (source != null) {
                                SettingsDivider()
                                SettingsInfoRow("补课预览", coursePreview(draft.source))
                            }
                        }
                    }
                    error?.let {
                        Text(it, Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            } else {
                SettingsDatePickerContent(
                    year = selectedDate.year,
                    month = selectedDate.monthValue,
                    day = selectedDate.dayOfMonth,
                    onYearChange = { selectedDate = selectedDate.withYear(it) },
                    onMonthChange = { selectedDate = selectedDate.withMonth(it) },
                    onDayChange = { selectedDate = selectedDate.withDayOfMonth(it.coerceAtMost(selectedDate.lengthOfMonth())) },
                )
            }
        },
        content = {},
    )
}
