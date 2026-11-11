package com.xiaomanjun.sleepdownschedule.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.itos.coui.picker.CouiPickerDefaults
import com.itos.coui.shape.CouiShape
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.*
import com.xiaomanjun.sleepdownschedule.core.ui.material.LocalVisualEffects
import com.xiaomanjun.sleepdownschedule.domain.schedule.*
import com.xiaomanjun.sleepdownschedule.model.ScheduleConfigEntity
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsNumberPicker as NumberPicker
import top.yukonga.miuix.kmp.basic.NumberPickerDefaults

/** The same fixed action row for every period picker and wizard step. */
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

@Composable
private fun PeriodQuickNumberPicker(
    value: Int, onValueChange: (Int) -> Unit, range: IntRange,
    label: (Int) -> String, modifier: Modifier = Modifier, enabled: Boolean = true
) {
    val foreground = LocalContentColor.current
    BoxWithConstraints(modifier) {
        val fontScale = LocalDensity.current.fontScale
        val textStyle = MaterialTheme.typography.headlineSmall.copy(fontSize = when {
            maxWidth < 90.dp || fontScale > 1.3f -> 17.sp
            maxWidth < 140.dp || fontScale > 1.1f -> 20.sp
            else -> 25.sp
        })
        NumberPicker(value.coerceIn(range), onValueChange, modifier = Modifier.fillMaxWidth(), range = range,
            enabled = enabled, visibleItemCount = 3, label = label, textStyle = textStyle.copy(color = foreground),
            colors = NumberPickerDefaults.colors(selectedTextColor = foreground,
                unselectedTextColor = foreground.copy(alpha = 0.34f),
                disabledSelectedTextColor = foreground.copy(alpha = 0.55f),
                disabledUnselectedTextColor = foreground.copy(alpha = 0.22f)))
    }
}

internal fun allocateQuickPickerCounts(parts: List<PeriodDayPart>, counts: Map<PeriodDayPart, Int>, total: Int): Map<PeriodDayPart, Int> {
    var remaining = total.coerceAtLeast(parts.size)
    return parts.mapIndexed { position, part ->
        val after = parts.lastIndex - position
        val allocated = if (after == 0) remaining else (counts[part] ?: 1).coerceIn(1, remaining - after)
        remaining -= allocated
        part to allocated
    }.toMap()
}

@Composable
private fun PeriodWheelSelectionBand(modifier: Modifier = Modifier) {
    if (!LocalVisualEffects.current.usesCouiControls) return
    val ink = LocalContentColor.current
    Box(modifier.height(CouiPickerDefaults.height / 3).background(ink.copy(alpha = 0.075f), CouiShape(14.dp, 2.5f)))
}

@Composable
internal fun PeriodQuickSingleWheel(
    value: Int, onChange: (Int) -> Unit, range: IntRange, label: (Int) -> String,
) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        PeriodWheelSelectionBand(Modifier.width(120.dp))
        PeriodQuickNumberPicker(value, onChange, range, label, Modifier.fillMaxWidth())
    }
}

@Composable
private fun PeriodWheelColumns(parts: List<PeriodDayPart>, content: @Composable (PeriodDayPart) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth()) {
            parts.forEach { part ->
                Text(part.timelineLabel(), Modifier.weight(1f), style = MaterialTheme.typography.labelMedium,
                    color = LocalContentColor.current.copy(alpha = 0.66f), textAlign = TextAlign.Center)
            }
        }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            PeriodWheelSelectionBand(Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                parts.forEach { part ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { content(part) }
                }
            }
        }
    }
}

@Composable
internal fun PeriodQuickCountContent(
    parts: List<PeriodDayPart>, counts: Map<PeriodDayPart, Int>, onChange: (Map<PeriodDayPart, Int>) -> Unit
) {
    val total = parts.sumOf { counts.getValue(it) }
    val foreground = LocalContentColor.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("总节次", Modifier.fillMaxWidth(), style = MaterialTheme.typography.labelMedium,
            color = foreground.copy(alpha = 0.66f), textAlign = TextAlign.Center)
        PeriodQuickSingleWheel(total, { onChange(allocateQuickPickerCounts(parts, counts, it)) },
            parts.size..maxOf(40, total), { "${it}节" })
        if (parts.size > 1) {
            Text("时段分配", Modifier.fillMaxWidth(), style = MaterialTheme.typography.labelMedium,
                color = foreground.copy(alpha = 0.66f), textAlign = TextAlign.Center)
            PeriodWheelColumns(parts) { part ->
                val value = counts.getValue(part)
                val last = part == parts.last()
                val others = parts.dropLast(1).filterNot { it == part }.sumOf { counts.getValue(it) }
                val range = if (last) value..value else 1..(total - others - 1).coerceAtLeast(1)
                PeriodQuickNumberPicker(value, { nextValue ->
                    val next = counts + (part to nextValue)
                    onChange(next + (parts.last() to (total - parts.dropLast(1).sumOf { next.getValue(it) })))
                }, range, { "${it}节" }, Modifier.fillMaxWidth(), enabled = !last)
            }
        }
    }
}

@Composable
internal fun PeriodQuickStartsContent(
    config: ScheduleConfigEntity, draft: PeriodSchemeDraft, starts: Map<PeriodDayPart, Int>,
    onChange: (Map<PeriodDayPart, Int>) -> Unit
) {
    val parts = PeriodDayPart.entries.filter { config.periodCount(it) > 0 }
    val spans = parts.associateWith { automaticPartSpanMinutes(config, draft, it) }
    if (spans.values.sum() > LastMinuteOfDay) {
        Text("当前节数和时长已超出一天，请先减少节数或时长。", color = MaterialTheme.colorScheme.error)
        return
    }
    val constrained = constrainAutomaticPartStarts(config, draft, starts)
    PeriodWheelColumns(parts) { part ->
        val position = parts.indexOf(part)
        val minimum = parts.getOrNull(position - 1)?.let { constrained.getValue(it) + spans.getValue(it) } ?: 0
        val maximum = LastMinuteOfDay - parts.drop(position).sumOf { spans.getValue(it) }
        PeriodQuickNumberPicker(constrained.getValue(part), { value ->
            onChange(constrainAutomaticPartStarts(config, draft, constrained + (part to value)))
        }, minimum..maxOf(minimum, maximum), ::timelineMinuteText, Modifier.fillMaxWidth())
    }
}
