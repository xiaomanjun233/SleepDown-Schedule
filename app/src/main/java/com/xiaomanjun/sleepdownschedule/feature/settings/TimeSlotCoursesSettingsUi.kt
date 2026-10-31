package com.xiaomanjun.sleepdownschedule.feature.settings

import com.xiaomanjun.sleepdownschedule.core.ui.text.unclippedMarquee
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import com.kyant.shapes.Capsule

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items as staggeredItems
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.xiaomanjun.sleepdownschedule.AppState
import com.xiaomanjun.sleepdownschedule.CourseScheduleApp
import com.xiaomanjun.sleepdownschedule.PeriodEntity
import com.xiaomanjun.sleepdownschedule.R
import com.xiaomanjun.sleepdownschedule.ScheduleConfigEntity
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.*
import com.xiaomanjun.sleepdownschedule.core.ui.scaffold.detailContentTopPadding
import com.xiaomanjun.sleepdownschedule.core.ui.scaffold.settingsVisualConfig
import com.xiaomanjun.sleepdownschedule.core.ui.scaffold.settingsPageBackground
import com.xiaomanjun.sleepdownschedule.core.ui.settings.*
import com.xiaomanjun.sleepdownschedule.domain.schedule.*
import com.xiaomanjun.sleepdownschedule.feature.course.editor.CourseEditorPickerOverlay
import com.xiaomanjun.sleepdownschedule.feature.course.editor.CourseEditorPickerRequest
import com.xiaomanjun.sleepdownschedule.feature.home.homeSwitchGroup
import com.xiaomanjun.sleepdownschedule.transition.TransitionLaunchResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.util.UUID
import top.yukonga.miuix.kmp.basic.BasicComponent as MiuixBasicComponent
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** The schedule page contains only the same navigation row used by other settings. */
@Composable
fun TimeSlotCoursesSettings(config: ScheduleConfigEntity, backdrop: Backdrop?, onOpen: () -> Unit) {
    val decoded = remember(config.timeSlotCoursesJson) { runCatching { decodeTimeSlotCourses(config.timeSlotCoursesJson) } }
    GlassPreferenceSection("特殊课程") {
        SettingsGroup(backdrop, config, Modifier.fillMaxWidth().homeSwitchGroup()) {
            SettingsNavigationRow("特殊课程",
                decoded.fold(
                    onSuccess = { if (it.isEmpty()) "设置早读、午休、晚自修等时间段" else "${it.size} 个时间段 · 添加、编辑和删除" },
                    onFailure = { "数据无法读取，点此查看" }
                ), onClick = onOpen)
        }
    }
}

/** Only a confirmed entry is persisted; the parent schedule's other settings stay independent. */
@Composable
fun TimeSlotCourseManagementScreen(
    state: AppState,
    backdrop: Backdrop?,
    exitCommitRequest: Int = 0,
    onExitCommitFinished: (Boolean) -> Unit = {},
    onExitInterceptionChange: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val config = settingsVisualConfig(state.config)
    val activity = context.timeSlotTransitionActivity()
    val radius = with(LocalDensity.current) { (if (LocalGlassMiuixEnabled.current) 24.dp else 30.dp).toPx() }
    val backgroundColor = settingsPageBackground(config).toArgb()
    var launching by remember { mutableStateOf<String?>(null) }
    var hidden by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val decoded = remember(state.config.timeSlotCoursesJson) {
        runCatching { decodeTimeSlotCourses(state.config.timeSlotCoursesJson) }
    }
    val currentCourses by rememberUpdatedState(decoded.getOrDefault(emptyList()))
    val currentState by rememberUpdatedState(state)
    SideEffect { onExitInterceptionChange(launching != null) }
    DisposableEffect(Unit) { onDispose { onExitInterceptionChange(false) } }
    LaunchedEffect(exitCommitRequest) {
        if (exitCommitRequest > 0 && launching == null) onExitCommitFinished(true)
    }
    val padding = PaddingValues(start = 12.dp, end = 12.dp, top = detailContentTopPadding() + 8.dp,
        bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 32.dp)
    fun open(original: TimeSlotCourse?, bounds: Rect?, returnBounds: () -> Rect?) {
        val owner = activity ?: return
        if (launching != null) return
        val key = original?.id?.let { "slot:$it" } ?: "add"
        launching = key
        error = null
        scope.launch {
            try {
                val result = openTimeSlotCourseEditor(owner, currentState, original, bounds, radius, backgroundColor,
                    returnBounds = {
                        val stillPresent = if (original == null) currentCourses.size < MaxTimeSlotCourses
                            else currentCourses.any { it.id == original.id }
                        if (stillPresent) returnBounds() else null
                    }, onSourceHidden = { hidden = key }, onSourceVisible = { hidden = null },
                    onSourceReleased = { hidden = null; launching = null })
                if (result is TransitionLaunchResult.Failed) error = "暂时无法打开特殊课程，请稍后重试"
            } catch (failure: Exception) {
                hidden = null; launching = null
                if (failure is CancellationException) throw failure
                error = failure.message ?: "特殊课程页面打开失败"
            }
        }
    }
    LazyVerticalStaggeredGrid(columns = StaggeredGridCells.Fixed(2), modifier = Modifier.fillMaxSize(),
        contentPadding = padding, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalItemSpacing = 8.dp) {
            item(span = StaggeredGridItemSpan.FullLine) {
                Text("可独立显示、在课表中占位，或按整周空闲情况智能合并；不参与课程提醒。", Modifier.padding(horizontal = 6.dp),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            decoded.exceptionOrNull()?.let { failure -> item(span = StaggeredGridItemSpan.FullLine) {
                Text("特殊课程数据无法读取，原数据已保留：${failure.message}", color = MaterialTheme.colorScheme.error)
            } }
            error?.let { message -> item(span = StaggeredGridItemSpan.FullLine) { Text(message, color = MaterialTheme.colorScheme.error) } }
            staggeredItems(decoded.getOrDefault(emptyList()), key = { it.id }) { slot ->
                TimeSlotCourseNavigationCard(slot.name, "", resolvedTimeSlotCourse(slot, state.periods),
                    backdrop, config, enabled = true, hidden = hidden == "slot:${slot.id}",
                    onClick = { bounds, liveBounds -> open(slot, bounds, liveBounds) })
            }
            item {
                val count = decoded.getOrNull()?.size ?: 0
                TimeSlotCourseNavigationCard("添加特殊课程",
                        if (count < MaxTimeSlotCourses) "自定义名称、时间段和显示星期" else "最多添加 $MaxTimeSlotCourses 个时间段",
                    null, backdrop, config, enabled = decoded.isSuccess && count < MaxTimeSlotCourses,
                    hidden = hidden == "add", onClick = { bounds, liveBounds -> open(null, bounds, liveBounds) })
            }
        }
}

@Composable
private fun TimeSlotCourseNavigationCard(
    title: String, subtitle: String, slot: TimeSlotCourse?, backdrop: Backdrop?, config: ScheduleConfigEntity,
    enabled: Boolean, hidden: Boolean, onClick: (Rect?, () -> Rect?) -> Unit
) {
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    DisposableEffect(Unit) { onDispose { coordinates = null } }
    fun bounds(): Rect? = coordinates?.takeIf { it.isAttached }?.boundsInWindow()
    SettingsGroup(backdrop, config, Modifier.fillMaxWidth().homeSwitchGroup()
        .onGloballyPositioned { coordinates = it }.graphicsLayer { alpha = if (hidden) 0f else 1f }) {
        val foreground = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.38f)
        MiuixBasicComponent(enabled = enabled, role = Role.Button, onClick = { onClick(bounds(), ::bounds) },
            insideMargin = PaddingValues(horizontal = 14.dp, vertical = 16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (slot == null) Icon(Icons.Outlined.Add, contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 1f else 0.38f),
                        modifier = Modifier.size(18.dp))
                    Text(title, color = foreground, style = MiuixTheme.textStyles.main,
                        maxLines = 1, softWrap = false,
                        modifier = Modifier.weight(1f).basicMarquee(iterations = Int.MAX_VALUE))
                    if (slot != null) Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = null,
                        tint = foreground.copy(alpha = 0.62f), modifier = Modifier.size(16.dp).graphicsLayer { rotationZ = 180f })
                }
                if (slot != null) {
                    TimeSlotCourseCardInfo(Icons.Outlined.Schedule, "${slot.startTime}–${slot.endTime}", foreground)
                    TimeSlotCourseCardInfo(Icons.Outlined.CalendarToday, timeSlotWeekdaySummary(slot.weekdays), foreground)
                    TimeSlotCourseCardInfo(Icons.Outlined.ViewAgenda, timeSlotAnchorLabel(slot.afterPeriod), foreground)
                } else {
                    Text(subtitle, color = foreground.copy(alpha = if (enabled) 0.72f else foreground.alpha),
                        style = MiuixTheme.textStyles.footnote1)
                }
            }
        }
    }
}

@Composable
private fun TimeSlotCourseCardInfo(icon: ImageVector, label: String, foreground: androidx.compose.ui.graphics.Color) {
    Row(modifier = Modifier.fillMaxWidth().clip(Capsule())
        .background(foreground.copy(alpha = 0.06f)).padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = foreground.copy(alpha = 0.52f), modifier = Modifier.size(14.dp))
        Text(label, color = foreground.copy(alpha = 0.78f), style = MiuixTheme.textStyles.footnote1,
            maxLines = 1, softWrap = false,
            modifier = Modifier.weight(1f).unclippedMarquee())
    }
}

@Composable
fun TimeSlotCourseEditorScreen(
    state: AppState, backdrop: Backdrop?, originalJson: String, handoffToken: Long,
    exitCommitRequest: Int, onExitCommitFinished: (Boolean) -> Unit,
    onExitInterceptionChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val repository = remember(context) { (context.applicationContext as CourseScheduleApp).repository }
    val scope = rememberCoroutineScope()
    val original = remember(originalJson) { originalJson.takeIf(String::isNotEmpty)?.let { decodeTimeSlotCourses(it).single() } }
    val config = settingsVisualConfig(state.config)
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    SideEffect { onExitInterceptionChange(true) }
    DisposableEffect(Unit) { onDispose { onExitInterceptionChange(false) } }
    val padding = PaddingValues(start = 16.dp, end = 16.dp, top = detailContentTopPadding() + 12.dp,
        bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 32.dp)
    MaterialTheme(typography = MaterialTheme.typography.copy(
        titleMedium = MiuixTheme.textStyles.main,
        bodyMedium = MiuixTheme.textStyles.body1,
        bodySmall = MiuixTheme.textStyles.footnote1,
        labelLarge = MiuixTheme.textStyles.button,
        labelMedium = MiuixTheme.textStyles.footnote1
    )) {
    TimeSlotCourseEditor(original, state.periods, config, backdrop, LocalSettingsPopupBackdrop.current ?: backdrop,
        padding, saving, error, exitCommitRequest, onClose = { if (!saving) onExitCommitFinished(true) },
        onSave = { updated ->
            if (!saving) {
                saving = true; error = null
                scope.launch {
                    try {
                        repository.saveTimeSlotCourse(state.config.id, original, updated)
                        if (updated == null) TimeSlotCourseStateHandoff.get(handoffToken)?.sourceRemoved = true
                        onExitCommitFinished(true)
                    } catch (failure: Exception) {
                        if (failure is CancellationException) throw failure
                        error = failure.message ?: "特殊课程保存失败"
                    } finally { saving = false }
                }
            }
        })
    }
}

@Composable
private fun TimeSlotCourseEditor(
    original: TimeSlotCourse?, periods: List<PeriodEntity>, config: ScheduleConfigEntity,
    backdrop: Backdrop?, popupBackdrop: Backdrop?, padding: PaddingValues,
    saving: Boolean, saveError: String?, exitRequest: Int,
    onClose: () -> Unit, onSave: (TimeSlotCourse?) -> Unit
) {
    val defaultEnd = remember(periods) {
        ((periods.minByOrNull { it.periodIndex }?.startTime?.let(::parseTimeSlotTime)
            ?: LocalTime.of(8, 0)).toSecondOfDay() / 60 - 10).coerceAtLeast(1)
    }
    val resolvedOriginal = remember(original, periods) { original?.let { resolvedTimeSlotCourse(it, periods) } }
    val initialStart = resolvedOriginal?.startTime ?: formatTimeSlotTime(LocalTime.ofSecondOfDay(((defaultEnd - 20).coerceAtLeast(0) * 60).toLong()))
    val initialEnd = resolvedOriginal?.endTime ?: formatTimeSlotTime(LocalTime.ofSecondOfDay((defaultEnd * 60).toLong()))
    val initialIndices = original?.periodIndices.orEmpty().sorted().joinToString(",")
    val initialLessonTimes = original?.customPeriodTimes.orEmpty()
    val initialPositions = remember(initialStart, initialEnd, periods) { allowedTimeSlotAnchors(initialStart, initialEnd, periods) }
    val initialDays = (original?.weekdays ?: DefaultTimeSlotWeekdays).fold(0) { mask, day -> mask or (1 shl (day - 1)) }
    val id = rememberSaveable { original?.id ?: UUID.randomUUID().toString() }
    var name by rememberSaveable { mutableStateOf(original?.name.orEmpty()) }
    var location by rememberSaveable { mutableStateOf(original?.location.orEmpty()) }
    var note by rememberSaveable { mutableStateOf(original?.note.orEmpty()) }
    var modeName by rememberSaveable { mutableStateOf((original?.displayMode ?: TimeSlotDisplayMode.INDEPENDENT).name) }
    val displayMode = TimeSlotDisplayMode.valueOf(modeName)
    var startWeek by rememberSaveable { mutableIntStateOf(original?.startWeek ?: 1) }
    val initialEndWeek = original?.endWeek ?: config.totalWeeks
    var endWeek by rememberSaveable { mutableIntStateOf(initialEndWeek) }
    var start by rememberSaveable { mutableStateOf(initialStart) }
    var end by rememberSaveable { mutableStateOf(initialEnd) }
    var indicesText by rememberSaveable { mutableStateOf(initialIndices) }
    var lessonTimes by rememberSaveable { mutableStateOf(initialLessonTimes) }
    var anchor by rememberSaveable {
        mutableIntStateOf(original?.afterPeriod?.takeIf { it in initialPositions } ?: initialPositions.firstOrNull() ?: 0)
    }
    var daysMask by rememberSaveable { mutableIntStateOf(initialDays) }
    var error by remember { mutableStateOf<String?>(null) }
    var showExit by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var picker by remember { mutableStateOf<CourseEditorPickerRequest?>(null) }
    var pickerVisible by remember { mutableStateOf(false) }
    val weekdays = (1..7).filter { daysMask and (1 shl (it - 1)) != 0 }
    val selectedPeriods = remember(indicesText) { indicesText.split(',').filter(String::isNotBlank).map(String::toInt) }
    val availablePeriods = remember(periods) { periods.map { it.periodIndex }.distinct().sorted() }
    val positions = remember(start, end, periods) { allowedTimeSlotAnchors(start, end, periods) }
    val positionError = if (positions.isEmpty()) "所选时间段没有匹配的位置，请检查作息" else null
    LaunchedEffect(positions) {
        if (positions.isNotEmpty() && anchor !in positions) anchor = positions.first()
    }
    val dirty = name != original?.name.orEmpty() || start != initialStart || end != initialEnd ||
        anchor != (original?.afterPeriod ?: 0) || daysMask != initialDays ||
        indicesText != initialIndices || lessonTimes != initialLessonTimes ||
        location != original?.location.orEmpty() || note != original?.note.orEmpty() ||
        displayMode != (original?.displayMode ?: TimeSlotDisplayMode.INDEPENDENT) ||
        startWeek != (original?.startWeek ?: 1) || endWeek != initialEndWeek
    fun requestClose() { if (!saving) { if (dirty) showExit = true else onClose() } }
    // Do not replay an exit request that belonged to the list or previous editor.
    var handledExit by remember { mutableIntStateOf(exitRequest) }
    LaunchedEffect(exitRequest) {
        if (handledExit != exitRequest) { handledExit = exitRequest; requestClose() }
    }
    fun submit() {
        if (anchor !in positions) { error = positionError ?: "请重新选择显示位置"; return }
        val updated = TimeSlotCourse(id, name.trim(), start, end, anchor, weekdays, selectedPeriods,
            lessonTimes.takeIf(String::isNotBlank), displayMode, location.trim(), note.trim(), startWeek,
            endWeek.takeUnless { original?.endWeek == null && it == config.totalWeeks })
        val failure = runCatching { validateTimeSlotCourses(listOf(updated)) }.exceptionOrNull()
        if (failure != null) error = failure.message else { error = null; showExit = false; onSave(updated) }
    }
    fun updateClock(newStart: String, newEnd: String) {
        start = newStart; end = newEnd
        val nextPositions = allowedTimeSlotAnchors(newStart, newEnd, periods)
        anchor = anchor.takeIf { it in nextPositions } ?: nextPositions.firstOrNull() ?: 0
        error = null
    }
    val timeLabel = when {
        lessonTimes.isNotBlank() -> "${selectedPeriods.size} 课时 · $start–$end"
        selectedPeriods.isNotEmpty() -> (if (selectedPeriods.size == 1) "第${selectedPeriods.first()}节" else
            "第${selectedPeriods.first()}–${selectedPeriods.last()}节") + " · $start–$end"
        else -> "$start–$end"
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = padding, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            GlassPreferenceSection("基本信息") {
                SettingsGroup(backdrop, config, Modifier.fillMaxWidth()) {
                    SettingsTextFieldRow("名称", name, { name = it.take(MaxTimeSlotNameLength); error = null },
                        enabled = !saving, placeholder = "如晚自修")
                    SettingsDivider()
                    SettingsTextFieldRow("地点", location, { location = it.take(200) }, enabled = !saving, placeholder = "可选")
                    SettingsDivider()
                    SettingsTextFieldRow("备注", note, { note = it.take(2000) }, enabled = !saving, placeholder = "可选")
                    SettingsDivider()
                    SettingsPickerValueRow("时间段", timeLabel, enabled = !saving && availablePeriods.isNotEmpty(),
                        valueBelowTitle = true, onClick = {
                        val suggested = selectedPeriods.ifEmpty {
                            courseAnchorPeriodsForTimeRange(LocalTime.parse(start), LocalTime.parse(end), periods)
                        }
                        val first = suggested.firstOrNull()?.let(availablePeriods::indexOf)?.takeIf { it >= 0 } ?: 0
                        val last = (suggested.lastOrNull()?.let(availablePeriods::indexOf) ?: first).coerceAtLeast(first)
                        val usesPeriods = selectedPeriods.isNotEmpty() && lessonTimes.isBlank()
                        picker = CourseEditorPickerRequest.Period(
                            title = "选择时间段", labels = availablePeriods.map { "第${it}节" },
                            startIndex = first, endIndex = last, endIndexUpperBound = availablePeriods.lastIndex,
                            customStartTime = start.takeUnless { usesPeriods }, customEndTime = end.takeUnless { usesPeriods },
                            regularStartTime = start, regularEndTime = end,
                            periodIndices = selectedPeriods.ifEmpty { availablePeriods.subList(first, last + 1) },
                            availablePeriodIndices = availablePeriods,
                            customPeriodTimes = lessonTimes.takeIf(String::isNotBlank),
                            customTimeMatchesPeriods = usesPeriods && availablePeriods.containsAll(selectedPeriods),
                            onConfirmPeriods = { from, to ->
                                val selected = availablePeriods.subList(from, to + 1)
                                val firstPeriod = periods.first { it.periodIndex == selected.first() }
                                val lastPeriod = periods.first { it.periodIndex == selected.last() }
                                indicesText = selected.joinToString(","); lessonTimes = ""
                                updateClock(firstPeriod.startTime, lastPeriod.endTime)
                            },
                            onConfirmCustomTime = { newStart, newEnd ->
                                indicesText = ""; lessonTimes = ""; updateClock(newStart, newEnd)
                            },
                            onConfirmLessonTimes = { times ->
                                indicesText = times.joinToString(",") { it.index.toString() }
                                lessonTimes = encodeCoursePeriodTimes(times).orEmpty()
                                updateClock(formatTimeSlotTime(times.first().start), formatTimeSlotTime(times.last().end))
                            })
                        pickerVisible = true
                    })
                }
            }
        }
        item {
            GlassPreferenceSection("显示方式") {
                SettingsGroup(backdrop, config, Modifier.fillMaxWidth()) {
                    val selectableModes = com.xiaomanjun.sleepdownschedule.domain.schedule.SelectableTimeSlotDisplayModes
                    val modeLabels = listOf("独立显示", "智能显示")
                    SleepDownLiquidDropdownPreference(items = modeLabels,
                        selectedIndex = selectableModes.indexOf(displayMode).takeIf { it >= 0 } ?: 1,
                        title = "显示模式", backdrop = popupBackdrop, config = config, enabled = !saving,
                        onSelectedIndexChange = { modeName = selectableModes[it].name })
                    SettingsInfoRow("显示说明", when (displayMode) {
                        TimeSlotDisplayMode.INDEPENDENT -> "沿用原方案，在节次边界独立显示。"
                        else -> "像普通课程一样占用所选节次，相邻显示日合成一张卡片；有正式课程的当天优先显示正式课程。"
                    })
                    SettingsDivider()
                    SettingsPickerValueRow("显示星期", timeSlotWeekdaySummary(weekdays), enabled = !saving, onClick = {
                        val days = if (config.weekStartsOnSunday) listOf(7, 1, 2, 3, 4, 5, 6) else (1..7).toList()
                        picker = CourseEditorPickerRequest.Grid("选择显示星期", days.map { "周${WeekdayLabels[it - 1]}" },
                            days.indices.filterTo(linkedSetOf()) { days[it] in weekdays }, 7) { selected ->
                            daysMask = selected.fold(0) { mask, index -> mask or (1 shl (days[index] - 1)) }; error = null
                        }
                        pickerVisible = true
                    })
                    SettingsDivider()
                    if (displayMode == TimeSlotDisplayMode.INDEPENDENT) SettingsPickerValueRow("显示位置", if (positions.isEmpty()) "请调整时间段" else timeSlotAnchorLabel(anchor),
                        enabled = !saving && positions.isNotEmpty(), onClick = {
                        picker = CourseEditorPickerRequest.Wheel("选择显示位置", positions.map(::timeSlotAnchorLabel),
                            positions.indexOf(anchor).coerceAtLeast(0)) { index, _ -> anchor = positions[index] }
                        pickerVisible = true
                    })
                    SettingsDivider()
                    SettingsPickerValueRow("有效周次", "第${startWeek}周至第${endWeek}周", enabled = !saving, onClick = {
                        val lastWeek = maxOf(config.totalWeeks, startWeek, endWeek)
                        picker = CourseEditorPickerRequest.Wheel("选择有效周次", (1..lastWeek).map { "第${it}周" },
                            startIndex = startWeek - 1, endIndex = endWeek - 1) { from, to ->
                            startWeek = from + 1
                            endWeek = (to ?: from) + 1
                        }
                        pickerVisible = true
                    })
                }
            }
        }
        (error ?: positionError ?: saveError)?.let { message -> item { Text(message, Modifier.padding(horizontal = 6.dp), color = MaterialTheme.colorScheme.error) } }
        item(key = "save") {
            SettingsActionButton(if (saving) "处理中…" else "保存", backdrop, { submit() },
                modifier = Modifier.fillMaxWidth(), glowing = true, enabled = !saving && positions.isNotEmpty())
        }
        if (original != null) item(key = "delete") {
            SettingsActionButton("删除特殊课程", backdrop, { showDelete = true },
                modifier = Modifier.fillMaxWidth(), destructive = true, glowing = true, enabled = !saving)
        }
    }
    picker?.let { request ->
        CourseEditorPickerOverlay(pickerVisible, request, popupBackdrop, config,
            renderInRootScaffold = LocalCenteredDialogRenderInRootScaffold.current,
            onDismiss = { pickerVisible = false }, onDismissFinished = { picker = null })
    }
    if (showExit) LiquidAlertDialog("保存特殊课程？", "保存后立即更新当前课表的占位。",
        listOf(
            LiquidAlertAction("保存", LiquidAlertActionStyle.Primary, enabled = !saving && positions.isNotEmpty()) { submit() },
            LiquidAlertAction("放弃修改", LiquidAlertActionStyle.Destructive, enabled = !saving) { onClose() },
            LiquidAlertAction("继续编辑", LiquidAlertActionStyle.Cancel) { showExit = false }
        ), popupBackdrop, config, onDismissRequest = { showExit = false })
    if (showDelete) LiquidAlertDialog("删除特殊课程？", "“${original?.name}”的占位将被移除。",
        listOf(
            LiquidAlertAction("取消", LiquidAlertActionStyle.Cancel) { showDelete = false },
            LiquidAlertAction("删除", LiquidAlertActionStyle.Destructive, enabled = !saving) { showDelete = false; onSave(null) }
        ), popupBackdrop, config, onDismissRequest = { showDelete = false })
}

private val WeekdayLabels = listOf("一", "二", "三", "四", "五", "六", "日")
