package com.xiaomanjun.sleepdownschedule.feature.course.editor

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.kyant.backdrop.Backdrop
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle
import com.xiaomanjun.sleepdownschedule.PeriodEntity
import com.xiaomanjun.sleepdownschedule.R
import com.xiaomanjun.sleepdownschedule.ScheduleConfigEntity
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.CenteredDialogContentTransition
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.QuickSheetLiquidAction
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.SleepDownDesignTokens
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.SleepDownPickerDialog
import com.xiaomanjun.sleepdownschedule.glass.ui.appUsesDarkTheme
import java.time.LocalTime
import com.xiaomanjun.sleepdownschedule.domain.schedule.CoursePeriodTime
import com.xiaomanjun.sleepdownschedule.domain.schedule.parseCoursePeriodTimes
import com.xiaomanjun.sleepdownschedule.domain.schedule.matchingPeriodIndicesForTimeRange
import kotlin.math.max
import kotlinx.coroutines.flow.map
import top.yukonga.miuix.kmp.basic.BasicComponent as MiuixBasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults as MiuixBasicComponentDefaults
import top.yukonga.miuix.kmp.squircle.squircleSurface

internal sealed interface CourseEditorPickerRequest {
    val title: String

    data class Wheel(
        override val title: String,
        val labels: List<String>,
        val startIndex: Int,
        val endIndex: Int? = null,
        val onConfirm: (Int, Int?) -> Unit
    ) : CourseEditorPickerRequest

    data class Grid(
        override val title: String,
        val labels: List<String>,
        val selectedIndices: Set<Int>,
        val preferredColumns: Int,
        val onConfirm: (Set<Int>) -> Unit
    ) : CourseEditorPickerRequest

    data class Period(
        override val title: String,
        val labels: List<String>,
        val startIndex: Int,
        val endIndex: Int,
        val endIndexUpperBound: Int,
        val customStartTime: String?,
        val customEndTime: String?,
        val regularStartTime: String?,
        val regularEndTime: String?,
        val periodIndices: List<Int>,
        val customPeriodTimes: String?,
        val customTimeMatchesPeriods: Boolean = false,
        val onConfirmPeriods: (Int, Int) -> Unit,
        val onConfirmCustomTime: (String, String) -> Unit,
        val onConfirmLessonTimes: (List<CoursePeriodTime>) -> Unit
    ) : CourseEditorPickerRequest
}

private enum class CourseEditorTimeMode { Periods, Range, Lessons }

internal data class PeriodPickerSelectionBounds(
    val startIndex: Int,
    val endIndex: Int,
    val endIndexUpperBound: Int
)

internal fun boundedPeriodPickerSelection(
    startIndex: Int,
    endIndex: Int,
    requestedEndIndexUpperBound: Int,
    labelsLastIndex: Int
): PeriodPickerSelectionBounds {
    val safeLastIndex = labelsLastIndex.coerceAtLeast(0)
    val safeUpperBound = requestedEndIndexUpperBound.coerceIn(0, safeLastIndex)
    val safeStart = startIndex.coerceIn(0, safeUpperBound)
    return PeriodPickerSelectionBounds(
        startIndex = safeStart,
        endIndex = endIndex.coerceIn(safeStart, safeUpperBound),
        endIndexUpperBound = safeUpperBound
    )
}

@Composable
internal fun <T> DialogMultiGridSelector(
    title: String,
    values: List<T>,
    selected: Set<T>,
    displayValue: String,
    preferredColumns: Int,
    onSelected: (Set<T>) -> Unit,
    onOpenPicker: (CourseEditorPickerRequest) -> Unit,
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    label: (T) -> String
) {
    if (values.isEmpty()) return
    val selectedIndices = values.indices.filterTo(linkedSetOf()) { values[it] in selected }
    CourseEditorPickerValue(
        title = title,
        value = displayValue,
        backdrop = backdrop,
        config = config,
        onClick = {
            onOpenPicker(
                CourseEditorPickerRequest.Grid(
                    title = "选择$title",
                    labels = values.map(label),
                    selectedIndices = selectedIndices,
                    preferredColumns = preferredColumns,
                    onConfirm = { indices ->
                        onSelected(indices.sorted().mapTo(linkedSetOf()) { values[it] })
                    }
                )
            )
        }
    )
}

@Composable
internal fun DialogPeriodSelector(
    title: String,
    values: List<Int>,
    periods: List<PeriodEntity>,
    start: Int,
    end: Int,
    customStartTime: String?,
    customEndTime: String?,
    customPeriodTimes: String?,
    selectedPeriodIndices: List<Int>,
    onRangeSelected: (Int, Int) -> Unit,
    onCustomTimeSelected: (String, String) -> Unit,
    onLessonTimesSelected: (List<CoursePeriodTime>) -> Unit,
    onOpenPicker: (CourseEditorPickerRequest) -> Unit,
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    label: (Int) -> String
) {
    val safeValues = remember(values) { values.distinct().sorted().ifEmpty { listOf(1) } }
    val startIndex = safeValues.indexOf(start).takeIf { it >= 0 } ?: 0
    val endIndex = (safeValues.indexOf(end).takeIf { it >= 0 } ?: startIndex).coerceAtLeast(startIndex)
    val customRange = remember(customStartTime, customEndTime) {
        val parsedStart = customStartTime?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
        val parsedEnd = customEndTime?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
        if (parsedStart != null && parsedEnd != null && parsedEnd.isAfter(parsedStart)) {
            parsedStart to parsedEnd
        } else {
            null
        }
    }
    // Imported exact clocks remain the saved source. Use the same display classification
    // as the timetable for every provider, including rows saved before this upgrade.
    val matchingIndices = remember(customRange, customPeriodTimes, periods, safeValues) {
        customRange?.takeIf { customPeriodTimes.isNullOrBlank() }?.let { (from, to) ->
            matchingPeriodIndicesForTimeRange(from, to, periods).takeIf { indices ->
                indices.isNotEmpty() && indices.all { it in safeValues }
            }
        }.orEmpty()
    }
    val displayStartIndex = matchingIndices.firstOrNull()?.let(safeValues::indexOf) ?: startIndex
    val displayEndIndex = matchingIndices.lastOrNull()?.let(safeValues::indexOf) ?: endIndex
    CourseEditorPickerValue(
        title = title,
        value = customRange?.takeIf { matchingIndices.isEmpty() }?.let { (customStart, customEnd) -> "$customStart - $customEnd" }
            ?: if (start !in safeValues || end !in safeValues) "请选择时间"
            else "${label(safeValues[displayStartIndex])} - ${label(safeValues[displayEndIndex])}",
        backdrop = backdrop,
        config = config,
        onClick = {
            onOpenPicker(
                CourseEditorPickerRequest.Period(
                    title = "选择节次",
                    labels = safeValues.map(label),
                    startIndex = displayStartIndex,
                    endIndex = displayEndIndex,
                    endIndexUpperBound = safeValues.lastIndex,
                    customStartTime = customRange?.first?.toString(),
                    customEndTime = customRange?.second?.toString(),
                    regularStartTime = periods.firstOrNull { it.periodIndex == safeValues[displayStartIndex] }?.startTime,
                    regularEndTime = periods.firstOrNull { it.periodIndex == safeValues[displayEndIndex] }?.endTime,
                    periodIndices = selectedPeriodIndices.distinct().sorted(),
                    customPeriodTimes = customPeriodTimes,
                    customTimeMatchesPeriods = matchingIndices.isNotEmpty(),
                    onConfirmPeriods = { selectedStart, selectedEnd ->
                        onRangeSelected(safeValues[selectedStart], safeValues[selectedEnd])
                    },
                    onConfirmCustomTime = onCustomTimeSelected,
                    onConfirmLessonTimes = onLessonTimesSelected
                )
            )
        }
    )
}

@Composable
private fun DialogRangeWheelSelector(
    title: String,
    values: List<Int>,
    start: Int,
    end: Int,
    displayValue: String? = null,
    onRangeSelected: (Int, Int) -> Unit,
    onOpenPicker: (CourseEditorPickerRequest) -> Unit,
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    label: (Int) -> String
) {
    val safeValues = remember(values) { values.distinct().sorted().ifEmpty { listOf(1) } }
    val startIndex = safeValues.indexOf(start).takeIf { it >= 0 } ?: 0
    val endIndex = (safeValues.indexOf(end).takeIf { it >= 0 } ?: startIndex).coerceAtLeast(startIndex)
    CourseEditorPickerValue(
        title = title,
        value = displayValue ?: "${label(safeValues[startIndex])} - ${label(safeValues[endIndex])}",
        backdrop = backdrop,
        config = config,
        onClick = {
            onOpenPicker(
                CourseEditorPickerRequest.Wheel(
                    title = "选择$title",
                    labels = safeValues.map(label),
                    startIndex = startIndex,
                    endIndex = endIndex,
                    onConfirm = { selectedStart, selectedEnd ->
                        onRangeSelected(safeValues[selectedStart], safeValues[selectedEnd ?: selectedStart])
                    }
                )
            )
        }
    )
}

@Composable
internal fun <T> DialogSingleWheelSelector(
    title: String,
    values: List<T>,
    selected: T,
    onSelected: (T) -> Unit,
    onOpenPicker: (CourseEditorPickerRequest) -> Unit,
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    label: (T) -> String
) {
    val safeValues = values.ifEmpty { return }
    val selectedIndex = safeValues.indexOf(selected).coerceAtLeast(0)
    CourseEditorPickerValue(
        title = title,
        value = label(safeValues[selectedIndex]),
        backdrop = backdrop,
        config = config,
        onClick = {
            onOpenPicker(
                CourseEditorPickerRequest.Wheel(
                    title = "选择$title",
                    labels = safeValues.map(label),
                    startIndex = selectedIndex,
                    onConfirm = { index, _ -> onSelected(safeValues[index]) }
                )
            )
        }
    )
}

@Composable
private fun CourseEditorPickerValue(
    title: String,
    value: String,
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    onClick: () -> Unit
) {
    val buttonTextColor = LocalContentColor.current
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            // Match the original selector capsule's 42dp height. The surrounding LazyColumn keeps
            // its original 12dp spacing, so replacing four selectors does not grow the dialog.
            .height(42.dp)
            // Only round the pressed/selection indication emitted by BasicComponent. This row has
            // no persistent capsule background in its resting state.
            .clip(RoundedRectangle(18.dp))
    ) {
        MiuixBasicComponent(
            title = title,
            titleColor = MiuixBasicComponentDefaults.titleColor(
                color = buttonTextColor,
                disabledColor = buttonTextColor.copy(alpha = 0.38f)
            ),
            modifier = Modifier.fillMaxSize(),
            insideMargin = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
            endActions = {
                Text(
                    value,
                    color = buttonTextColor.copy(alpha = 0.72f),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.End,
                    modifier = Modifier.widthIn(max = 132.dp)
                )
                Spacer(Modifier.width(8.dp))
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_back),
                    contentDescription = "打开${title}选择器",
                    tint = buttonTextColor.copy(alpha = 0.62f),
                    modifier = Modifier.size(18.dp).graphicsLayer { rotationZ = 180f }
                )
            },
            onClick = onClick
        )
    }
}

@Composable
internal fun CourseEditorColorValue(
    colorArgb: Long?,
    automaticColorArgb: Long,
    onClick: () -> Unit
) {
    val contentColor = LocalContentColor.current
    val previewColor = colorArgb ?: automaticColorArgb
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(42.dp)
            .clip(RoundedRectangle(18.dp))
    ) {
        MiuixBasicComponent(
            title = "课程颜色",
            titleColor = MiuixBasicComponentDefaults.titleColor(
                color = contentColor,
                disabledColor = contentColor.copy(alpha = 0.38f)
            ),
            modifier = Modifier.fillMaxSize(),
            insideMargin = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
            endActions = {
                Surface(
                    modifier = Modifier.size(20.dp),
                    shape = Capsule(),
                    color = ComposeColor(previewColor.toInt()),
                    border = BorderStroke(1.dp, contentColor.copy(alpha = 0.28f))
                ) {}
                Spacer(Modifier.width(8.dp))
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_back),
                    contentDescription = "打开课程颜色选择器",
                    tint = contentColor.copy(alpha = 0.62f),
                    modifier = Modifier.size(18.dp).graphicsLayer { rotationZ = 180f }
                )
            },
            onClick = onClick
        )
    }
}

@Composable
internal fun CourseEditorPickerOverlay(
    show: Boolean,
    request: CourseEditorPickerRequest,
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    renderInRootScaffold: Boolean,
    onDismiss: () -> Unit,
    onDismissFinished: () -> Unit
) {
    // This picker owns its theme-tinted sheet, so its foreground follows that surface instead of
    // the wallpaper sampled by the page below it.
    val dark = appUsesDarkTheme(config)
    val pickerContentColor = if (dark) ComposeColor.White else ComposeColor(0xFF111111)
    val wheelRequest = request as? CourseEditorPickerRequest.Wheel
    val gridRequest = request as? CourseEditorPickerRequest.Grid
    val periodRequest = request as? CourseEditorPickerRequest.Period
    val periodBounds = remember(periodRequest) {
        periodRequest?.let {
            boundedPeriodPickerSelection(
                startIndex = it.startIndex,
                endIndex = it.endIndex,
                requestedEndIndexUpperBound = it.endIndexUpperBound,
                labelsLastIndex = it.labels.lastIndex
            )
        }
    }
    var startIndex by remember(request) {
        mutableIntStateOf(
            wheelRequest?.startIndex?.coerceIn(wheelRequest.labels.indices)
                ?: periodBounds?.startIndex
                ?: 0
        )
    }
    var endIndex by remember(request) {
        mutableIntStateOf(
            wheelRequest?.let {
                (it.endIndex ?: it.startIndex).coerceIn(it.labels.indices).coerceAtLeast(startIndex)
            } ?: periodBounds?.endIndex
                ?: 0
        )
    }
    var selectedIndices by remember(request) {
        mutableStateOf(gridRequest?.selectedIndices.orEmpty())
    }
    var timeMode by remember(request) {
        mutableStateOf(when {
            !periodRequest?.customPeriodTimes.isNullOrBlank() &&
                parseCoursePeriodTimes(periodRequest.customPeriodTimes).map { it.index } == periodRequest.periodIndices -> CourseEditorTimeMode.Lessons
            periodRequest?.customTimeMatchesPeriods == true -> CourseEditorTimeMode.Periods
            periodRequest?.customStartTime != null && periodRequest.customEndTime != null -> CourseEditorTimeMode.Range
            else -> CourseEditorTimeMode.Periods
        })
    }
    val initialCustomStart = remember(request) {
        periodRequest?.customStartTime?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
            ?: periodRequest?.regularStartTime?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
            ?: LocalTime.of(8, 0)
    }
    val initialCustomEnd = remember(request) {
        periodRequest?.customEndTime?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
            ?: periodRequest?.regularEndTime?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
            ?: initialCustomStart.plusMinutes(45)
    }
    var startHour by remember(request) { mutableIntStateOf(initialCustomStart.hour) }
    var startMinute by remember(request) { mutableIntStateOf(initialCustomStart.minute) }
    var endHour by remember(request) { mutableIntStateOf(initialCustomEnd.hour) }
    var endMinute by remember(request) { mutableIntStateOf(initialCustomEnd.minute) }
    var lessonClock by remember(request) { mutableStateOf(initialCourseLessonClockDraft(
        initialCustomStart, periodRequest?.periodIndices.orEmpty(), periodRequest?.customPeriodTimes
    )) }
    var timeError by remember(request) { mutableStateOf<String?>(null) }
    SleepDownPickerDialog(
        show = show,
        title = request.title,
        onDismissRequest = onDismiss,
        onDismissFinished = onDismissFinished,
        backdrop = backdrop,
        config = config,
        renderInRootScaffold = renderInRootScaffold,
        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp),
        blurRadius = 14.dp
    ) {
        CompositionLocalProvider(LocalContentColor provides pickerContentColor) {
        Column(
            modifier = Modifier.wrapContentHeight().padding(horizontal = 2.dp, vertical = 2.dp),
            verticalArrangement = Arrangement.spacedBy(
                SleepDownDesignTokens.QuickSheet.PickerContentSpacing
            )
        ) {
            if (periodRequest != null) {
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    com.xiaomanjun.sleepdownschedule.core.ui.settings.LiquidOptionTabs(
                        selectedIndex = timeMode.ordinal,
                        labels = listOf("按节次", "自定义时间", "按课时"),
                        backdrop = backdrop, config = config, width = maxWidth,
                        followAppTheme = true
                    ) { index ->
                        timeMode = CourseEditorTimeMode.entries[index]
                        timeError = null
                    }
                }
            }
            CenteredDialogContentTransition(
                targetState = timeMode,
                modifier = Modifier.fillMaxWidth(),
                label = "course-editor-picker-style"
            ) { displayedTimeMode ->
            if (wheelRequest != null) {
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
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
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 12.dp)) {
                        top.yukonga.miuix.kmp.basic.NumberPicker(
                            value = startIndex,
                            onValueChange = {
                                startIndex = it
                                if (wheelRequest.endIndex != null && endIndex < it) endIndex = it
                            },
                            range = if (wheelRequest.endIndex != null) 0..endIndex else wheelRequest.labels.indices,
                            visibleItemCount = 3,
                            label = { wheelRequest.labels[it] },
                            colors = pickerColors,
                            textStyle = pickerTextStyle,
                            modifier = Modifier.weight(1f)
                        )
                        if (wheelRequest.endIndex != null) {
                            top.yukonga.miuix.kmp.basic.NumberPicker(
                                value = endIndex,
                                onValueChange = { endIndex = it },
                                range = startIndex..wheelRequest.labels.lastIndex,
                                visibleItemCount = 3,
                                label = { wheelRequest.labels[it] },
                                colors = pickerColors,
                                textStyle = pickerTextStyle,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            } else if (periodRequest != null) {
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val compact = maxWidth < 330.dp || LocalDensity.current.fontScale > 1.10f
                    val pickerTextStyle = top.yukonga.miuix.kmp.theme.MiuixTheme.textStyles.title1.copy(
                        color = pickerContentColor,
                        fontSize = if (compact) 19.sp else 24.sp
                    )
                    val pickerColors = top.yukonga.miuix.kmp.basic.NumberPickerDefaults.colors(
                        selectedTextColor = pickerContentColor,
                        unselectedTextColor = pickerContentColor.copy(alpha = 0.34f),
                        disabledSelectedTextColor = pickerContentColor.copy(alpha = 0.55f),
                        disabledUnselectedTextColor = pickerContentColor.copy(alpha = 0.22f)
                    )
                    if (displayedTimeMode == CourseEditorTimeMode.Lessons) {
                        CourseLessonClockPicker(lessonClock, periodRequest.periodIndices, backdrop,
                            config, renderInRootScaffold) {
                            lessonClock = it
                            timeError = null
                        }
                    } else if (displayedTimeMode == CourseEditorTimeMode.Range) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(Modifier.fillMaxWidth()) {
                                Text("开始", modifier = Modifier.weight(1f), textAlign = TextAlign.Center, color = pickerContentColor.copy(alpha = 0.62f))
                                Text("结束", modifier = Modifier.weight(1f), textAlign = TextAlign.Center, color = pickerContentColor.copy(alpha = 0.62f))
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 6.dp)
                            ) {
                            listOf(
                                Triple(startHour, 0..23, { value: Int -> startHour = value; timeError = null }),
                                Triple(startMinute, 0..59, { value: Int -> startMinute = value; timeError = null }),
                                Triple(endHour, 0..23, { value: Int -> endHour = value; timeError = null }),
                                Triple(endMinute, 0..59, { value: Int -> endMinute = value; timeError = null })
                            ).forEachIndexed { index, (value, range, onValueChange) ->
                                top.yukonga.miuix.kmp.basic.NumberPicker(
                                    value = value,
                                    onValueChange = onValueChange,
                                    range = range,
                                    visibleItemCount = 3,
                                    label = { it.toString().padStart(2, '0') + if (index % 2 == 0) "时" else "分" },
                                    wrapAround = index % 2 == 1,
                                    colors = pickerColors,
                                    textStyle = pickerTextStyle,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 12.dp)
                        ) {
                            top.yukonga.miuix.kmp.basic.NumberPicker(
                                value = startIndex,
                                onValueChange = {
                                    startIndex = it
                                    if (endIndex < it) endIndex = it
                                },
                                range = 0..endIndex,
                                visibleItemCount = 3,
                                label = { periodRequest.labels[it] },
                                colors = pickerColors,
                                textStyle = pickerTextStyle,
                                modifier = Modifier.weight(1f)
                            )
                            top.yukonga.miuix.kmp.basic.NumberPicker(
                                value = endIndex,
                                onValueChange = { endIndex = it },
                                range = startIndex..(periodBounds?.endIndexUpperBound
                                    ?: periodRequest.labels.lastIndex),
                                visibleItemCount = 3,
                                label = { periodRequest.labels[it] },
                                colors = pickerColors,
                                textStyle = pickerTextStyle,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
                timeError?.let { message ->
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }
            } else if (gridRequest != null) {
                CourseEditorSelectionGrid(
                    labels = gridRequest.labels,
                    selectedIndices = selectedIndices,
                    preferredColumns = gridRequest.preferredColumns,
                    contentColor = pickerContentColor,
                    dark = dark,
                    onSelectionChange = { selectedIndices = it }
                )
            }
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(SleepDownDesignTokens.Dialog.ActionSpacing)
            ) {
                QuickSheetLiquidAction("取消", true, backdrop, config, modifier = Modifier.weight(1f), height = SleepDownDesignTokens.CenteredDialog.ActionHeight) { onDismiss() }
                QuickSheetLiquidAction("确定", true, backdrop, config, primary = true, modifier = Modifier.weight(1f), height = SleepDownDesignTokens.CenteredDialog.ActionHeight) {
                    when (request) {
                        is CourseEditorPickerRequest.Wheel -> request.onConfirm(
                            startIndex,
                            endIndex.takeIf { request.endIndex != null }
                        )
                        is CourseEditorPickerRequest.Grid -> request.onConfirm(selectedIndices)
                        is CourseEditorPickerRequest.Period -> {
                            if (timeMode == CourseEditorTimeMode.Lessons) {
                                val times = try { lessonClock.times(request.periodIndices) }
                                catch (error: IllegalArgumentException) {
                                    timeError = error.message
                                    return@QuickSheetLiquidAction
                                }
                                request.onConfirmLessonTimes(times)
                            } else if (timeMode == CourseEditorTimeMode.Range) {
                                val start = LocalTime.of(startHour, startMinute)
                                val end = LocalTime.of(endHour, endMinute)
                                if (!end.isAfter(start)) {
                                    timeError = "结束时间必须晚于开始时间"
                                    return@QuickSheetLiquidAction
                                }
                                if (start != initialCustomStart || end != initialCustomEnd || request.customStartTime == null || !request.customPeriodTimes.isNullOrBlank()) {
                                    request.onConfirmCustomTime(start.toString(), end.toString())
                                }
                            } else {
                                // Merely opening/confirming a matching imported clock must not
                                // erase its original campus/building time or source numbering.
                                if (!request.customTimeMatchesPeriods || startIndex != request.startIndex || endIndex != request.endIndex) {
                                    request.onConfirmPeriods(startIndex, endIndex)
                                }
                            }
                        }
                    }
                    onDismiss()
                }
            }
        }
        }
    }
}

@Composable
private fun CourseEditorSelectionGrid(
    labels: List<String>,
    selectedIndices: Set<Int>,
    preferredColumns: Int,
    contentColor: ComposeColor,
    dark: Boolean,
    onSelectionChange: (Set<Int>) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val latestSelection by rememberUpdatedState(selectedIndices)
    val latestOnSelectionChange by rememberUpdatedState(onSelectionChange)
    val density = LocalDensity.current
    val gap = 8.dp
    val cellHeight = 48.dp
    BoxWithConstraints(Modifier.fillMaxWidth().wrapContentHeight()) {
        val minimumCellWidth = if (preferredColumns >= 7) 38.dp else 50.dp
        val columns = minOf(
            preferredColumns.coerceAtLeast(1),
            maxOf(3, ((maxWidth + gap) / (minimumCellWidth + gap)).toInt())
        )
        val rowCount = (labels.size + columns - 1) / columns
        val gridHeight = cellHeight * rowCount + gap * (rowCount - 1).coerceAtLeast(0)
        val gapPx = with(density) { gap.toPx() }
        val cellHeightPx = with(density) { cellHeight.toPx() }

        fun toggle(index: Int) {
            val next = latestSelection.toMutableSet()
            if (!next.add(index)) next.remove(index)
            latestOnSelectionChange(next)
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }

        Box(
            Modifier
                .fillMaxWidth()
                .height(gridHeight)
                .pointerInput(labels, columns, gapPx, cellHeightPx) {
                    var gestureSelection = mutableSetOf<Int>()
                    var selectMode = true
                    var lastIndex = -1

                    fun indexAt(position: Offset): Int? {
                        if (position.x < 0f || position.y < 0f) return null
                        val cellWidth = (size.width - gapPx * (columns - 1)) / columns
                        val columnStride = cellWidth + gapPx
                        val rowStride = cellHeightPx + gapPx
                        val column = (position.x / columnStride).toInt()
                        val row = (position.y / rowStride).toInt()
                        if (column !in 0 until columns || row !in 0 until rowCount) return null
                        if (position.x - column * columnStride > cellWidth) return null
                        if (position.y - row * rowStride > cellHeightPx) return null
                        return (row * columns + column).takeIf(labels.indices::contains)
                    }

                    fun applyDragIndex(index: Int) {
                        if (index == lastIndex) return
                        lastIndex = index
                        if (selectMode) gestureSelection.add(index) else gestureSelection.remove(index)
                        latestOnSelectionChange(gestureSelection.toSet())
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }

                    detectDragGestures(
                        onDragStart = { position ->
                            gestureSelection = latestSelection.toMutableSet()
                            lastIndex = -1
                            indexAt(position)?.let { index ->
                                selectMode = index !in gestureSelection
                                applyDragIndex(index)
                            }
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            indexAt(change.position)?.let(::applyDragIndex)
                        }
                    )
                }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                labels.chunked(columns).forEachIndexed { rowIndex, rowLabels ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(gap)
                    ) {
                        rowLabels.forEachIndexed { columnIndex, label ->
                            val index = rowIndex * columns + columnIndex
                            val selected = index in selectedIndices
                            CourseEditorSelectionCell(
                                label = label,
                                selected = selected,
                                contentColor = contentColor,
                                dark = dark,
                                modifier = Modifier.weight(1f).height(cellHeight),
                                onClick = { toggle(index) }
                            )
                        }
                        repeat(columns - rowLabels.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun CourseEditorSelectionCell(
    label: String,
    selected: Boolean,
    contentColor: ComposeColor,
    dark: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val baseColor = when {
        selected -> MaterialTheme.colorScheme.primary
        dark -> ComposeColor.White.copy(alpha = 0.12f)
        else -> ComposeColor.Black.copy(alpha = 0.07f)
    }
    val pressedColor = baseColor.copy(
        red = baseColor.red * 0.78f,
        green = baseColor.green * 0.78f,
        blue = baseColor.blue * 0.78f
    )
    val surfaceColor by animateColorAsState(
        targetValue = if (pressed) pressedColor else baseColor,
        animationSpec = tween(durationMillis = if (pressed) 70 else 150),
        label = "course-editor-cell-press"
    )
    Box(
        modifier = modifier
            .squircleSurface(color = surfaceColor, cornerRadius = 12.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else contentColor.copy(alpha = 0.78f),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1
        )
    }
}
