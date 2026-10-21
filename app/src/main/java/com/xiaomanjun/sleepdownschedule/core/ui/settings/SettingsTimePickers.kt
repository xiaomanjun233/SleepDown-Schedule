package com.xiaomanjun.sleepdownschedule.core.ui.settings

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.kyant.backdrop.Backdrop
import com.xiaomanjun.sleepdownschedule.LastMinuteOfDay
import com.xiaomanjun.sleepdownschedule.PeriodTimePickerBounds
import com.xiaomanjun.sleepdownschedule.PeriodTimeSelection
import com.xiaomanjun.sleepdownschedule.PeriodTimeSelectionAnchor
import com.xiaomanjun.sleepdownschedule.ScheduleConfigEntity
import com.xiaomanjun.sleepdownschedule.constrainPeriodTimeSelection
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.QuickSheetLiquidAction
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.SleepDownDesignTokens
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.SleepDownPickerDialog
import com.xiaomanjun.sleepdownschedule.domain.schedule.constrainPeriodTimeSelection
import com.xiaomanjun.sleepdownschedule.domain.schedule.parseScheduleTime
import com.xiaomanjun.sleepdownschedule.formatScheduleDate
import com.xiaomanjun.sleepdownschedule.parseScheduleDate
import java.time.LocalDate
import java.time.LocalTime

fun showNativeDatePicker(context: Context, currentValue: String, onPicked: (String) -> Unit) {
    val date = parseScheduleDate(currentValue) ?: LocalDate.now()
    DatePickerDialog(
        context,
        { _, year, month, day ->
            onPicked(formatScheduleDate(LocalDate.of(year, month + 1, day)))
        },
        date.year,
        date.monthValue - 1,
        date.dayOfMonth
    ).show()
}

fun showNativeTimePicker(context: Context, currentValue: String, onPicked: (String) -> Unit) {
    val time = runCatching { parseScheduleTime(currentValue) }.getOrNull() ?: java.time.LocalTime.of(8, 0)
    TimePickerDialog(
        context,
        { _, hour, minute ->
            onPicked("%02d:%02d".format(hour, minute))
        },
        time.hour,
        time.minute,
        true
    ).show()
}

@Composable
fun SettingsDatePickerRow(
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    enabled: Boolean = true
) {
    val popupBackdrop = LocalSettingsPopupBackdrop.current ?: backdrop
    var showPicker by remember { mutableStateOf(false) }
    val initialDate = remember(value, showPicker) { parseScheduleDate(value) ?: LocalDate.now() }
    var pickerYear by remember(initialDate, showPicker) { mutableIntStateOf(initialDate.year) }
    var pickerMonth by remember(initialDate, showPicker) { mutableIntStateOf(initialDate.monthValue) }
    var pickerDay by remember(initialDate, showPicker) { mutableIntStateOf(initialDate.dayOfMonth) }
    SettingsPickerValueRow(
        title = title,
        value = value,
        enabled = enabled,
        onClick = { showPicker = true }
    )
    SleepDownPickerDialog(
        show = showPicker,
        title = "选择日期",
        onDismissRequest = { showPicker = false },
        backdrop = popupBackdrop,
        config = config,
        contentPadding = PaddingValues(SleepDownDesignTokens.QuickSheet.PickerContentPadding)
    ) {
        SettingsDatePickerContent(
            year = pickerYear, month = pickerMonth, day = pickerDay,
            onYearChange = { pickerYear = it }, onMonthChange = { pickerMonth = it },
            onDayChange = { pickerDay = it }
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(SleepDownDesignTokens.Dialog.ActionSpacing)) {
            QuickSheetLiquidAction(
                "取消", true, popupBackdrop, config,
                modifier = Modifier.weight(1f), height = SleepDownDesignTokens.CenteredDialog.ActionHeight
            ) { showPicker = false }
            QuickSheetLiquidAction(
                "确定", true, popupBackdrop, config, primary = true,
                modifier = Modifier.weight(1f), height = SleepDownDesignTokens.CenteredDialog.ActionHeight
            ) {
                val maxDay = java.time.YearMonth.of(pickerYear, pickerMonth).lengthOfMonth()
                onValueChange(formatScheduleDate(LocalDate.of(pickerYear, pickerMonth, pickerDay.coerceAtMost(maxDay))))
                showPicker = false
            }
        }
    }
}

/**
 * Shared year/month/day wheels. Kept separate from the row so a centered dialog can host the same
 * control as one of its pages instead of stacking a second dialog on top of itself.
 */
@Composable
internal fun SettingsDatePickerContent(
    year: Int,
    month: Int,
    day: Int,
    onYearChange: (Int) -> Unit,
    onMonthChange: (Int) -> Unit,
    onDayChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val maxDay = java.time.YearMonth.of(year, month).lengthOfMonth()
    LaunchedEffect(maxDay) {
        if (day > maxDay) onDayChange(maxDay)
    }
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val fontScale = LocalDensity.current.fontScale
        // NumberPicker defaults to MIUIX title1. Three equal columns make a four digit year
        // ellipsize on narrow dialogs or when display/font scaling is raised. Keep the picker
        // readable without changing the dialog width: reserve more width for the year and cap
        // only this dense numeric control's effective size at the extreme DPI combinations.
        val compactPicker = maxWidth < 300.dp || fontScale > 1.12f
        val pickerTextStyle = top.yukonga.miuix.kmp.theme.MiuixTheme.textStyles.title1.copy(
            fontSize = when {
                maxWidth < 270.dp || fontScale > 1.32f -> 21.sp
                compactPicker -> 24.sp
                else -> 28.sp
            }
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(if (compactPicker) 4.dp else 8.dp)
        ) {
            top.yukonga.miuix.kmp.basic.NumberPicker(
                value = year,
                onValueChange = onYearChange,
                range = 2000..2100,
                visibleItemCount = 3,
                label = { "${it}年" },
                textStyle = pickerTextStyle,
                modifier = Modifier.weight(if (compactPicker) 1.65f else 1.5f)
            )
            top.yukonga.miuix.kmp.basic.NumberPicker(
                value = month,
                onValueChange = onMonthChange,
                range = 1..12,
                visibleItemCount = 3,
                label = { "${it}月" },
                textStyle = pickerTextStyle,
                modifier = Modifier.weight(1f)
            )
            top.yukonga.miuix.kmp.basic.NumberPicker(
                value = day.coerceAtMost(maxDay),
                onValueChange = onDayChange,
                range = 1..maxDay,
                visibleItemCount = 3,
                label = { "${it}日" },
                textStyle = pickerTextStyle,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun SettingsTimePickerRow(
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    enabled: Boolean = true,
    minimumMinute: Int = 0,
    maximumMinute: Int = LastMinuteOfDay
) {
    val popupBackdrop = LocalSettingsPopupBackdrop.current ?: backdrop
    var showPicker by remember { mutableStateOf(false) }
    val initialTime = remember(value, showPicker) {
        runCatching { parseScheduleTime(value) }.getOrNull()
            ?: java.time.LocalTime.of(8, 0)
    }
    var pickerHour by remember(initialTime, showPicker) { mutableIntStateOf(initialTime.hour) }
    var pickerMinute by remember(initialTime, showPicker) { mutableIntStateOf(initialTime.minute) }
    val safeMinimum = minimumMinute.coerceIn(0, LastMinuteOfDay)
    val safeMaximum = maximumMinute.coerceIn(safeMinimum, LastMinuteOfDay)
    val selectedMinute = (pickerHour * 60 + pickerMinute).coerceIn(safeMinimum, safeMaximum)
    val selectedHour = selectedMinute / 60
    val allowedMinuteRange = minuteRangeForHour(selectedHour, safeMinimum, safeMaximum)
    SettingsPickerValueRow(title = title, value = value, enabled = enabled, onClick = { showPicker = true })
    SleepDownPickerDialog(
        show = showPicker,
        title = "选择时间",
        onDismissRequest = { showPicker = false },
        backdrop = popupBackdrop,
        config = config,
        contentPadding = PaddingValues(SleepDownDesignTokens.QuickSheet.PickerContentPadding)
    ) {
            SettingsTimePickerContent(selectedMinute, safeMinimum..safeMaximum) {
                pickerHour = it / 60
                pickerMinute = it % 60
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(SleepDownDesignTokens.Dialog.ActionSpacing)
            ) {
                QuickSheetLiquidAction(
                    "取消", true, popupBackdrop, config,
                    modifier = Modifier.weight(1f), height = SleepDownDesignTokens.CenteredDialog.ActionHeight
                ) { showPicker = false }
                QuickSheetLiquidAction(
                    "确定", true, popupBackdrop, config, primary = true,
                    modifier = Modifier.weight(1f), height = SleepDownDesignTokens.CenteredDialog.ActionHeight
                ) {
                    onValueChange("%02d:%02d".format(selectedMinute / 60, selectedMinute % 60))
                    showPicker = false
                }
            }
    }
}

@Composable
internal fun SettingsMinutePickerRow(
    title: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    enabled: Boolean = true,
    range: IntRange = 0..180,
    pickerTitle: String = "选择提前时间"
) {
    val popupBackdrop = LocalSettingsPopupBackdrop.current ?: backdrop
    var showPicker by remember { mutableStateOf(false) }
    val safeValue = value.coerceIn(range)
    var pickerValue by remember(safeValue, showPicker) { mutableIntStateOf(safeValue) }
    SettingsPickerValueRow(
        title = title,
        value = "$safeValue 分钟",
        enabled = enabled,
        onClick = { showPicker = true }
    )
    SleepDownPickerDialog(
        show = showPicker,
        title = pickerTitle,
        onDismissRequest = { showPicker = false },
        backdrop = popupBackdrop,
        config = config,
        contentPadding = PaddingValues(SleepDownDesignTokens.QuickSheet.PickerContentPadding)
    ) {
        SettingsMinutePickerContent(pickerValue, { pickerValue = it }, range)
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(SleepDownDesignTokens.Dialog.ActionSpacing)
        ) {
            QuickSheetLiquidAction(
                "取消", true, popupBackdrop, config,
                modifier = Modifier.weight(1f), height = SleepDownDesignTokens.CenteredDialog.ActionHeight
            ) { showPicker = false }
            QuickSheetLiquidAction(
                "确定", true, popupBackdrop, config, primary = true,
                modifier = Modifier.weight(1f), height = SleepDownDesignTokens.CenteredDialog.ActionHeight
            ) {
                onValueChange(pickerValue)
                showPicker = false
            }
        }
    }
}

/** Shared by settings rows and the period editor; typography and wheel geometry stay identical. */
@Composable
internal fun SettingsTimePickerContent(
    value: Int, range: IntRange, modifier: Modifier = Modifier, onValueChange: (Int) -> Unit
) {
    val safeMinimum = range.first.coerceIn(0, LastMinuteOfDay)
    val safeMaximum = range.last.coerceIn(safeMinimum, LastMinuteOfDay)
    val selectedMinute = value.coerceIn(safeMinimum, safeMaximum)
    val selectedHour = selectedMinute / 60
    val allowedMinuteRange = minuteRangeForHour(selectedHour, safeMinimum, safeMaximum)
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
                val pickerContentColor = LocalContentColor.current
                val compact = maxWidth < 300.dp || LocalDensity.current.fontScale > 1.15f
                val pickerTextStyle = top.yukonga.miuix.kmp.theme.MiuixTheme.textStyles.title1.copy(
                    color = pickerContentColor,
                    fontSize = if (compact) 23.sp else 28.sp
                )
                val pickerColors = top.yukonga.miuix.kmp.basic.NumberPickerDefaults.colors(
                    selectedTextColor = pickerContentColor,
                    unselectedTextColor = pickerContentColor.copy(alpha = 0.34f),
                    disabledSelectedTextColor = pickerContentColor.copy(alpha = 0.55f),
                    disabledUnselectedTextColor = pickerContentColor.copy(alpha = 0.22f)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 12.dp)
                ) {
                    top.yukonga.miuix.kmp.basic.NumberPicker(
                        value = selectedHour,
                        onValueChange = { hour ->
                            val minute = (selectedMinute % 60).coerceIn(minuteRangeForHour(hour, safeMinimum, safeMaximum))
                            onValueChange(hour * 60 + minute)
                        },
                        range = (safeMinimum / 60)..(safeMaximum / 60),
                        visibleItemCount = 3,
                        label = { "%02d时".format(it) },
                        colors = pickerColors,
                        textStyle = pickerTextStyle,
                        modifier = Modifier.weight(1f)
                    )
                    top.yukonga.miuix.kmp.basic.NumberPicker(
                        value = selectedMinute % 60,
                        onValueChange = { onValueChange(selectedHour * 60 + it) },
                        range = allowedMinuteRange,
                        wrapAround = allowedMinuteRange == 0..59,
                        visibleItemCount = 3,
                        label = { "%02d分".format(it) },
                        colors = pickerColors,
                        textStyle = pickerTextStyle,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
}

@Composable
internal fun SettingsMinutePickerContent(
    value: Int, onValueChange: (Int) -> Unit, range: IntRange, modifier: Modifier = Modifier
) {
        val pickerContentColor = LocalContentColor.current
        top.yukonga.miuix.kmp.basic.NumberPicker(
            value = value.coerceIn(range),
            onValueChange = onValueChange,
            range = range,
            visibleItemCount = 3,
            label = { "${it}分钟" },
            colors = top.yukonga.miuix.kmp.basic.NumberPickerDefaults.colors(
                selectedTextColor = pickerContentColor,
                unselectedTextColor = pickerContentColor.copy(alpha = 0.34f),
                disabledSelectedTextColor = pickerContentColor.copy(alpha = 0.55f),
                disabledUnselectedTextColor = pickerContentColor.copy(alpha = 0.22f)
            ),
            textStyle = top.yukonga.miuix.kmp.theme.MiuixTheme.textStyles.title1.copy(
                color = pickerContentColor,
                fontSize = 28.sp
            ),
            modifier = modifier.fillMaxWidth()
        )
}

private fun minuteRangeForHour(hour: Int, minimumMinute: Int, maximumMinute: Int): IntRange {
    val lower = if (hour == minimumMinute / 60) minimumMinute % 60 else 0
    val upper = if (hour == maximumMinute / 60) maximumMinute % 60 else 59
    return lower.coerceAtMost(upper)..upper
}

@Composable
internal fun ConstrainedPeriodTimePickers(
    startMinute: Int,
    endMinute: Int,
    bounds: PeriodTimePickerBounds,
    onSelectionChange: (PeriodTimeSelection) -> Unit,
    textStyle: TextStyle,
    showSectionLabels: Boolean,
    modifier: Modifier = Modifier,
    minimumDurationMinutes: Int = 1
) {
    val foreground = LocalContentColor.current
    val pickerColors = top.yukonga.miuix.kmp.basic.NumberPickerDefaults.colors(
        selectedTextColor = foreground,
        unselectedTextColor = foreground.copy(alpha = 0.34f),
        disabledSelectedTextColor = foreground.copy(alpha = 0.55f),
        disabledUnselectedTextColor = foreground.copy(alpha = 0.22f)
    )
    val duration = minimumDurationMinutes.coerceIn(1, (bounds.maximumEndMinute - bounds.minimumStartMinute).coerceAtLeast(1))
    val selection = constrainPeriodTimeSelection(startMinute, endMinute, bounds, minimumDurationMinutes = duration)
    LaunchedEffect(selection, startMinute, endMinute) {
        if (selection.startMinute != startMinute || selection.endMinute != endMinute) {
            onSelectionChange(selection)
        }
    }
    val latestStart = selection.endMinute - duration
    val earliestEnd = selection.startMinute + duration
    val startHour = selection.startMinute / 60
    val endHour = selection.endMinute / 60
    val startMinuteRange = minuteRangeForHour(startHour, bounds.minimumStartMinute, latestStart)
    val endMinuteRange = minuteRangeForHour(endHour, earliestEnd, bounds.maximumEndMinute)

    fun updateStart(candidate: Int) {
        onSelectionChange(
            constrainPeriodTimeSelection(
                candidate,
                selection.endMinute,
                bounds,
                PeriodTimeSelectionAnchor.START,
                minimumDurationMinutes = duration
            )
        )
    }

    fun updateEnd(candidate: Int) {
        onSelectionChange(
            constrainPeriodTimeSelection(
                selection.startMinute,
                candidate,
                bounds,
                PeriodTimeSelectionAnchor.END,
                minimumDurationMinutes = duration
            )
        )
    }

    Row(modifier, horizontalArrangement = Arrangement.spacedBy(if (showSectionLabels) 14.dp else 4.dp)) {
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            if (showSectionLabels) {
                Text("开始时间", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(Modifier.fillMaxWidth()) {
                top.yukonga.miuix.kmp.basic.NumberPicker(
                    value = startHour,
                    onValueChange = { hour ->
                        val minute = (selection.startMinute % 60)
                            .coerceIn(minuteRangeForHour(hour, bounds.minimumStartMinute, latestStart))
                        updateStart(hour * 60 + minute)
                    },
                    range = (bounds.minimumStartMinute / 60)..(latestStart / 60),
                    visibleItemCount = 3,
                    label = { "%02d时".format(it) },
                    textStyle = textStyle.copy(color = foreground),
                    colors = pickerColors,
                    modifier = Modifier.weight(1f)
                )
                top.yukonga.miuix.kmp.basic.NumberPicker(
                    value = selection.startMinute % 60,
                    onValueChange = { updateStart(startHour * 60 + it) },
                    range = startMinuteRange,
                    wrapAround = startMinuteRange == 0..59,
                    visibleItemCount = 3,
                    label = { "%02d分".format(it) },
                    textStyle = textStyle.copy(color = foreground),
                    colors = pickerColors,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            if (showSectionLabels) {
                Text("结束时间", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(Modifier.fillMaxWidth()) {
                top.yukonga.miuix.kmp.basic.NumberPicker(
                    value = endHour,
                    onValueChange = { hour ->
                        val minute = (selection.endMinute % 60)
                            .coerceIn(minuteRangeForHour(hour, earliestEnd, bounds.maximumEndMinute))
                        updateEnd(hour * 60 + minute)
                    },
                    range = (earliestEnd / 60)..(bounds.maximumEndMinute / 60),
                    visibleItemCount = 3,
                    label = { "%02d时".format(it) },
                    textStyle = textStyle.copy(color = foreground),
                    colors = pickerColors,
                    modifier = Modifier.weight(1f)
                )
                top.yukonga.miuix.kmp.basic.NumberPicker(
                    value = selection.endMinute % 60,
                    onValueChange = { updateEnd(endHour * 60 + it) },
                    range = endMinuteRange,
                    wrapAround = endMinuteRange == 0..59,
                    visibleItemCount = 3,
                    label = { "%02d分".format(it) },
                    textStyle = textStyle.copy(color = foreground),
                    colors = pickerColors,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
