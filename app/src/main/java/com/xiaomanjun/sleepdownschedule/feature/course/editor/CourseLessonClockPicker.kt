package com.xiaomanjun.sleepdownschedule.feature.course.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
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
import com.xiaomanjun.sleepdownschedule.ScheduleConfigEntity
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.QuickSheetLiquidAction
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.SleepDownDesignTokens
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.SleepDownPickerDialog
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsMinutePickerContent
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsPickerValueRow
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsTimePickerContent
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsToggleRow
import java.time.LocalTime
import top.yukonga.miuix.kmp.basic.NumberPicker
import top.yukonga.miuix.kmp.basic.NumberPickerDefaults

private enum class LessonClockField(val title: String) {
    START("开始时间"), COUNT("节数"), CLASS("单节时长"), BREAK("课间时长")
}

@Composable
internal fun CourseLessonClockPicker(
    draft: CourseLessonClockDraft,
    preferredIndices: List<Int>,
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    renderInRootScaffold: Boolean,
    onChange: (CourseLessonClockDraft) -> Unit
) {
    val preview = runCatching { draft.times(preferredIndices) }.getOrNull()
    var field by remember { mutableStateOf(LessonClockField.START) }
    var lessonIndex by remember { mutableStateOf<Int?>(null) }
    var showPicker by remember { mutableStateOf(false) }
    var pendingValue by remember { mutableIntStateOf(0) }

    fun open(target: LessonClockField, value: Int, index: Int? = null) {
        field = target
        lessonIndex = index
        pendingValue = value
        showPicker = true
    }
    fun startMinute(value: String, index: Int? = null): Int =
        (runCatching { LocalTime.parse(value) }.getOrNull()
            ?: index?.let { preview?.getOrNull(it)?.start }
            ?: runCatching { LocalTime.parse(draft.start) }.getOrNull()
            ?: LocalTime.of(8, 0)).let { it.hour * 60 + it.minute }

    Column(
        modifier = Modifier.fillMaxWidth()
            .heightIn(max = SleepDownDesignTokens.CenteredDialog.SelectionContentMaxHeight)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        SettingsPickerValueRow("开始时间", draft.start, {
            open(LessonClockField.START, startMinute(draft.start))
        }, compact = true)
        SettingsPickerValueRow("节数", "${draft.count} 节", {
            open(LessonClockField.COUNT, draft.count.toIntOrNull()?.coerceIn(1, 40) ?: 1)
        }, compact = true)
        SettingsToggleRow("逐节设置", "", draft.individually, backdrop, compact = true) {
            onChange(draft.copy(individually = it))
        }
        if (!draft.individually) {
            SettingsPickerValueRow("单节时长", "${draft.classMinutes} 分钟", {
                open(LessonClockField.CLASS, draft.classMinutes.toIntOrNull() ?: 45)
            }, compact = true)
            SettingsPickerValueRow("课间时长", "${draft.breakMinutes} 分钟", {
                open(LessonClockField.BREAK, draft.breakMinutes.toIntOrNull() ?: 10)
            }, compact = true)
        } else {
            Text(
                "各节默认自动衔接，也可单独指定开始时间。",
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = LocalContentColor.current.copy(alpha = 0.66f)
            )
            repeat(draft.count.toIntOrNull()?.takeIf { it in 1..40 } ?: 0) { index ->
                val row = draft.lesson(index)
                HorizontalDivider(Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
                    color = LocalContentColor.current.copy(alpha = 0.14f))
                Text("第 ${index + 1} 节",
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.titleSmall)
                SettingsPickerValueRow("开始时间", row.startOverride.ifBlank {
                    preview?.getOrNull(index)?.start?.let { "自动 · $it" } ?: "自动衔接"
                }, {
                    open(LessonClockField.START, startMinute(row.startOverride, index), index)
                }, compact = true)
                SettingsPickerValueRow("单节时长", "${row.classMinutes} 分钟", {
                    open(LessonClockField.CLASS, row.classMinutes.toIntOrNull() ?: 45, index)
                }, compact = true)
                if (index < (draft.count.toIntOrNull() ?: 0) - 1) {
                    SettingsPickerValueRow("课间时长", "${row.breakMinutes} 分钟", {
                        open(LessonClockField.BREAK, row.breakMinutes.toIntOrNull() ?: 10, index)
                    }, compact = true)
                }
            }
        }
        preview?.let {
            Text(
                "${it.first().start} – ${it.last().end}",
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = LocalContentColor.current.copy(alpha = 0.66f)
            )
        }
    }
    // One retained secondary picker for the whole form; cancel never writes a partial value.
    SleepDownPickerDialog(
        show = showPicker,
        title = lessonIndex?.let { "第 ${it + 1} 节 · ${field.title}" } ?: field.title,
        onDismissRequest = { showPicker = false },
        backdrop = backdrop,
        config = config,
        renderInRootScaffold = renderInRootScaffold
    ) {
        when (field) {
            LessonClockField.START -> SettingsTimePickerContent(pendingValue, 0..1439) { pendingValue = it }
            LessonClockField.CLASS, LessonClockField.BREAK -> SettingsMinutePickerContent(
                pendingValue, { pendingValue = it }, (if (field == LessonClockField.CLASS) 1 else 0)..1439,
                Modifier.fillMaxWidth()
            )
            LessonClockField.COUNT -> {
                val foreground = LocalContentColor.current
                NumberPicker(
                    value = pendingValue.coerceIn(1, 40),
                    onValueChange = { pendingValue = it },
                    range = 1..40,
                    visibleItemCount = 3,
                    label = { "$it 节" },
                    colors = NumberPickerDefaults.colors(
                        selectedTextColor = foreground,
                        unselectedTextColor = foreground.copy(alpha = 0.34f)
                    ),
                    textStyle = MaterialTheme.typography.titleLarge.copy(color = foreground),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        if (field == LessonClockField.START && lessonIndex != null) {
            QuickSheetLiquidAction("自动衔接", true, backdrop, config, modifier = Modifier.fillMaxWidth(),
                height = SleepDownDesignTokens.CenteredDialog.ActionHeight) {
                val index = requireNotNull(lessonIndex)
                onChange(draft.withLesson(index, draft.lesson(index).copy(startOverride = "")))
                showPicker = false
            }
        }
        Row(Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(SleepDownDesignTokens.Dialog.ActionSpacing)) {
            QuickSheetLiquidAction("取消", true, backdrop, config, modifier = Modifier.weight(1f),
                height = SleepDownDesignTokens.CenteredDialog.ActionHeight) { showPicker = false }
            QuickSheetLiquidAction("确定", true, backdrop, config, primary = true,
                modifier = Modifier.weight(1f), height = SleepDownDesignTokens.CenteredDialog.ActionHeight) {
                val index = lessonIndex
                val time = LocalTime.of(pendingValue.coerceIn(0, 1439) / 60, pendingValue.coerceIn(0, 1439) % 60).toString()
                val updated = when (field) {
                    LessonClockField.START -> if (index == null) draft.copy(start = time)
                        else draft.withLesson(index, draft.lesson(index).copy(startOverride = time))
                    LessonClockField.COUNT -> draft.copy(count = pendingValue.coerceIn(1, 40).toString())
                    LessonClockField.CLASS -> {
                        val minutes = pendingValue.coerceIn(1, 1439).toString()
                        if (index == null) draft.copy(classMinutes = minutes)
                        else draft.withLesson(index, draft.lesson(index).copy(classMinutes = minutes))
                    }
                    LessonClockField.BREAK -> {
                        val minutes = pendingValue.coerceIn(0, 1439).toString()
                        if (index == null) draft.copy(breakMinutes = minutes)
                        else draft.withLesson(index, draft.lesson(index).copy(breakMinutes = minutes))
                    }
                }
                onChange(updated)
                showPicker = false
            }
        }
    }
}
