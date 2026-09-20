package com.xiaomanjun.sleepdownschedule.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiaomanjun.sleepdownschedule.domain.schedule.*
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.*
import com.xiaomanjun.sleepdownschedule.model.ScheduleConfigEntity
import com.kyant.backdrop.Backdrop
import top.yukonga.miuix.kmp.basic.NumberPicker
import top.yukonga.miuix.kmp.basic.NumberPickerDefaults
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** The same fixed action row for every period picker and wizard step. */
@Composable
internal fun PeriodPickerActions(
    backdrop: Backdrop?, config: ScheduleConfigEntity,
    onCancel: () -> Unit, onConfirm: () -> Unit,
    cancelText: String = "取消", confirmText: String = "确定"
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(SleepDownDesignTokens.Dialog.ActionSpacing)) {
        QuickSheetLiquidAction(cancelText, true, backdrop, config, modifier = Modifier.weight(1f),
            height = SleepDownDesignTokens.CenteredDialog.ActionHeight, onClick = onCancel)
        QuickSheetLiquidAction(confirmText, true, backdrop, config, modifier = Modifier.weight(1f), primary = true,
            height = SleepDownDesignTokens.CenteredDialog.ActionHeight, onClick = onConfirm)
    }
}

@Composable
internal fun PeriodQuickNumberPicker(
    value: Int, onValueChange: (Int) -> Unit, range: IntRange,
    label: (Int) -> String, modifier: Modifier = Modifier, style: TextStyle? = null
) {
    val foreground = LocalContentColor.current
    BoxWithConstraints(modifier) {
        val fontScale = LocalDensity.current.fontScale
        val textStyle = style ?: MiuixTheme.textStyles.title1.copy(fontSize = when {
            maxWidth < 90.dp || fontScale > 1.3f -> 17.sp
            maxWidth < 140.dp || fontScale > 1.1f -> 20.sp
            else -> 25.sp
        })
        NumberPicker(value.coerceIn(range), onValueChange, modifier = Modifier.fillMaxWidth(), range = range,
            visibleItemCount = 3, label = label, textStyle = textStyle.copy(color = foreground),
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

/** Original quick allocation: total wheel above, parallel part wheels, last part gets the remainder. */
@Composable
internal fun PeriodQuickCountContent(
    parts: List<PeriodDayPart>, counts: Map<PeriodDayPart, Int>, onChange: (Map<PeriodDayPart, Int>) -> Unit
) {
    val total = parts.sumOf { counts.getValue(it) }
    val foreground = LocalContentColor.current
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("总节次", style = MaterialTheme.typography.labelMedium, color = foreground.copy(alpha = 0.66f))
        PeriodQuickNumberPicker(total, { onChange(allocateQuickPickerCounts(parts, counts, it)) },
            parts.size..maxOf(40, total), { "${it}节" }, Modifier.fillMaxWidth().height(112.dp))
        Text("时段分配", style = MaterialTheme.typography.labelMedium, color = foreground.copy(alpha = 0.66f))
        Row(Modifier.fillMaxWidth()) {
            parts.forEach { part ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(part.timelineLabel(), style = MaterialTheme.typography.labelMedium, color = foreground.copy(alpha = 0.66f))
                    if (part == parts.last()) {
                        Box(Modifier.fillMaxWidth().height(140.dp), contentAlignment = Alignment.Center) {
                            Text("${counts.getValue(part)}节", fontSize = if (parts.size > 2) 17.sp else 20.sp,
                                color = foreground, fontWeight = FontWeight.SemiBold)
                        }
                    } else {
                        val others = parts.dropLast(1).filterNot { it == part }.sumOf { counts.getValue(it) }
                        val maximum = (total - others - 1).coerceAtLeast(1)
                        PeriodQuickNumberPicker(counts.getValue(part), { value ->
                            val next = counts + (part to value)
                            onChange(next + (parts.last() to (total - parts.dropLast(1).sumOf { next.getValue(it) })))
                        }, 1..maximum, { "${it}节" }, Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

/** Original parallel HH:mm wheels, with earlier parts reserving space for later parts. */
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
    Row(Modifier.fillMaxWidth()) {
        parts.forEachIndexed { position, part ->
            val minimum = parts.getOrNull(position - 1)?.let { constrained.getValue(it) + spans.getValue(it) } ?: 0
            val maximum = LastMinuteOfDay - parts.drop(position).sumOf { spans.getValue(it) }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(part.timelineLabel(), style = MaterialTheme.typography.labelMedium, color = LocalContentColor.current.copy(alpha = 0.66f))
                PeriodQuickNumberPicker(constrained.getValue(part), { value ->
                    onChange(constrainAutomaticPartStarts(config, draft, constrained + (part to value)))
                }, minimum..maxOf(minimum, maximum), ::timelineMinuteText, Modifier.fillMaxWidth())
            }
        }
    }
}
