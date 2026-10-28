package com.xiaomanjun.sleepdownschedule.feature.course.editor

import com.xiaomanjun.sleepdownschedule.domain.schedule.encodeCoursePeriodTimes
import com.xiaomanjun.sleepdownschedule.domain.schedule.parseCoursePeriodTimes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.kyant.backdrop.Backdrop
import com.xiaomanjun.sleepdownschedule.CourseEntity
import com.xiaomanjun.sleepdownschedule.PeriodEntity
import com.xiaomanjun.sleepdownschedule.ScheduleConfigEntity
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.DialogButtonRole
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.DialogCapsuleField
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.DialogLiquidButton
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LocalNativeDialogSurface
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LocalNativeMaterialForeground
import com.xiaomanjun.sleepdownschedule.courseAnchorPeriodsForTimeRange
import com.xiaomanjun.sleepdownschedule.courseTimeLabel
import com.xiaomanjun.sleepdownschedule.glass.GlassBackdropDomain
import com.xiaomanjun.sleepdownschedule.glass.glassBackdropProducer
import com.xiaomanjun.sleepdownschedule.glass.rememberGlassCombinedBackdrop
import com.xiaomanjun.sleepdownschedule.glass.rememberGlassLayerBackdrop
import com.xiaomanjun.sleepdownschedule.glass.ui.CourseGlassCard
import com.xiaomanjun.sleepdownschedule.glass.ui.LocalAdaptiveGlass
import com.xiaomanjun.sleepdownschedule.glass.ui.appPanelForegroundColor
import com.xiaomanjun.sleepdownschedule.core.ui.theme.appUsesDarkTheme
import com.xiaomanjun.sleepdownschedule.glass.ui.courseCardAllowsCustomOverrides
import com.xiaomanjun.sleepdownschedule.glass.ui.courseCardBaseColor
import com.xiaomanjun.sleepdownschedule.glass.ui.glassForegroundColor
import com.xiaomanjun.sleepdownschedule.hasCustomTime
import com.xiaomanjun.sleepdownschedule.weekdayLabel
import java.time.LocalTime
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.flow.map

@Composable
internal fun CourseEditorFormPage(
    course: CourseEntity?,
    title: String,
    groupedCourses: List<CourseEntity>,
    contextMessage: String?,
    draft: CourseEditorDraft,
    onDraftChange: (CourseEditorDraft) -> Unit,
    periodValues: List<Int>,
    periods: List<PeriodEntity>,
    totalWeeks: Int,
    config: ScheduleConfigEntity,
    backdrop: Backdrop?,
    error: String?,
    onCancel: () -> Unit,
    onSave: () -> Unit,
    onDelete: (() -> Unit)?,
    onOpenPicker: (CourseEditorPickerRequest) -> Unit,
    onOpenColorPicker: () -> Unit,
    pageCount: Int,
    rowEntrance: (Int) -> Float = { _ -> 1f },
    timetableCatalog: com.xiaomanjun.sleepdownschedule.model.TimetableCatalog = com.xiaomanjun.sleepdownschedule.model.TimetableCatalog()
) {
    val selectedBells = draft.independentPeriodSchemeId?.let { id -> timetableCatalog.times.filter { it.schemeId == id }
        .sortedBy { it.periodIndex }.map { PeriodEntity(it.periodIndex,it.startTime,it.endTime,config.id,it.alias) } } ?: periods
    val selectedValues = selectedBells.map { it.periodIndex }
    // This form lives on the wallpaper-sampling CourseGlassCard. Its complete foreground domain
    // must follow the glass contrast decision; appPanelForegroundColor follows the app theme and
    // produced black row labels on a dark sampled card while values/icons stayed light.
    val materialForeground = com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LocalNativeMaterialForeground.current
    val editorContentColor = if (materialForeground != ComposeColor.Unspecified) {
        materialForeground
    } else if (com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LocalNativeDialogSurface.current) {
        appPanelForegroundColor(config)
    } else if (backdrop != null) {
        LocalAdaptiveGlass.current.contentColor
    } else {
        glassForegroundColor(config)
    }
    // The field resolves its fill from this same foreground, never from the unrelated app theme.
    val editorFieldTextColor = editorContentColor
    val formBodyBackdrop = rememberGlassLayerBackdrop(
        domain = GlassBackdropDomain.Content,
        providerId = "course-editor-form-body"
    )
    val headerBackdrop = backdrop?.let {
        com.xiaomanjun.sleepdownschedule.glass.rememberGlassCombinedBackdrop(it, formBodyBackdrop)
    } ?: formBodyBackdrop
    // Only the scrolling fields produce this layer. The fixed header consumes it as a
    // later sibling, composited over the actual dialog shell, never the home timetable.
    // Row timing/easing is owned by the overlay; apply it once as an upward popup.
    // Other callers default to 1f and remain stationary.
    val rowEntranceDensity = LocalDensity.current
    fun Modifier.courseEditorFormRowEntrance(index: Int): Modifier = graphicsLayer {
        val p = rowEntrance(index).coerceIn(0f, 1f)
        alpha = p
        scaleX = 0.86f + 0.14f * p
        scaleY = 0.86f + 0.14f * p
        translationY = (1f - p) * with(rowEntranceDensity) { 32.dp.toPx() }
    }
    CompositionLocalProvider(LocalContentColor provides editorContentColor) {
    Box(modifier = Modifier.fillMaxSize()) {
    LazyColumn(
        // Compose only visible fields. Hidden input/selector rows and adjacent full forms must
        // not compete with the first visible editor frame. Drafts remain owned above the list.
        modifier = Modifier
            .fillMaxSize()
            .glassBackdropProducer(formBodyBackdrop),
        contentPadding = PaddingValues(
                // The title/actions live above the scrolling form. Reserve their full glass band so
                // the first field never slides under a moving header.
                top = 76.dp,
                bottom = when {
                    pageCount > 1 -> 52.dp
                    course == null -> 32.dp
                    else -> 16.dp
                }
            ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (contextMessage != null) {
            item(key = "context", contentType = "context") {
                Text(
                    text = contextMessage,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).courseEditorFormRowEntrance(0),
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalContentColor.current.copy(alpha = 0.82f)
                )
            }
        }
        if (course != null) {
            item(key = "summary", contentType = "summary") {
                Text(
                        listOfNotNull(
                            course.teacher,
                            course.location,
                            compactWeekdaySelectionLabel(groupedCourses.map(CourseEntity::weekday)),
                            if (course.hasCustomTime()) courseTimeLabel(course, periods)
                            else course.periods.takeIf { it.isNotEmpty() }?.let { "第${it.min()}-${it.max()}节" }
                        ).joinToString(" · "),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).courseEditorFormRowEntrance(1),
                        style = MaterialTheme.typography.labelMedium,
                        color = LocalContentColor.current.copy(alpha = 0.68f),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
            }
        }
        item(key = "name", contentType = "text-field") {
            DialogCapsuleField(
                value = draft.name,
                onValueChange = { onDraftChange(draft.copy(name = it)) },
                placeholder = "课程名称",
                config = config,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).courseEditorFormRowEntrance(2),
                fieldTextColor = editorFieldTextColor
            )
        }
        item(key = "teacher", contentType = "text-field") {
            DialogCapsuleField(
                value = draft.teacher,
                onValueChange = { onDraftChange(draft.copy(teacher = it)) },
                placeholder = "教师",
                config = config,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).courseEditorFormRowEntrance(3),
                fieldTextColor = editorFieldTextColor
            )
        }
        item(key = "location", contentType = "text-field") {
            DialogCapsuleField(
                value = draft.location,
                onValueChange = { onDraftChange(draft.copy(location = it)) },
                placeholder = "地点",
                config = config,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).courseEditorFormRowEntrance(4),
                fieldTextColor = editorFieldTextColor
            )
        }
        item(key = "credit", contentType = "text-field") {
            DialogCapsuleField(value = draft.credit, onValueChange = { onDraftChange(draft.copy(credit = it)) },
                placeholder = "学分（可选）", config = config,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), fieldTextColor = editorFieldTextColor)
        }
        item(key = "timing-mode", contentType = "selector") {
            val templates = timetableCatalog.schemes.filter { scheme -> timetableCatalog.times.any { it.schemeId == scheme.id } }
            val labels = listOf("跟随课表作息") + templates.map { "指定：${it.name}" } + "自定义时间"
            val selected = if (draft.customStartTime != null) labels.lastIndex
                else draft.independentPeriodSchemeId?.let { id -> templates.indexOfFirst { it.id == id }.takeIf { it >= 0 }?.plus(1) } ?: 0
            CourseEditorPickerValue("时间方式", labels[selected], backdrop, config) {
                onOpenPicker(CourseEditorPickerRequest.Wheel("时间方式", labels, selected, onConfirm = { index, _ ->
                    val custom = index == labels.lastIndex
                    onDraftChange(draft.copy(independentPeriodSchemeId = templates.getOrNull(index-1)?.id.takeUnless { custom },
                        customStartTime = selectedBells.firstOrNull()?.startTime?.takeIf { custom },
                        customEndTime = selectedBells.firstOrNull()?.endTime?.takeIf { custom },
                        customPeriodTimes = null, customLessonTimesEdited = false, timingSelectionEdited = true))
                }))
            }
        }
        if (course?.timingReviewReason != null || course?.arrangementProjection?.reviewReason != null) {
            item(key = "timing-review") { Text(course.timingReviewReason ?: course.arrangementProjection!!.reviewReason.orEmpty(),
                modifier = Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.bodySmall) }
        }
        item(key = "weekday", contentType = "selector") {
            Box(Modifier.fillMaxWidth().courseEditorFormRowEntrance(5)) {
            DialogMultiGridSelector(
                title = "星期",
                values = (1..7).toList(),
                selected = draft.weekdays,
                displayValue = draft.weekdays.sorted()
                    .joinToString("、") { "周${weekdayLabel(it)}" }.ifEmpty { "请选择星期" },
                preferredColumns = 7,
                onSelected = { onDraftChange(draft.copy(weekdays = it)) },
                onOpenPicker = onOpenPicker,
                backdrop = backdrop,
                config = config
            ) { "周${weekdayLabel(it)}" }
            }
        }
        item(key = "period", contentType = "selector") {
            Box(Modifier.fillMaxWidth().courseEditorFormRowEntrance(6)) {
            DialogPeriodSelector(
                title = "节次",
                    values = selectedValues.ifEmpty { periodValues },
                    periods = selectedBells,
                    start = draft.periodStart,
                    end = draft.periodEnd,
                    customStartTime = draft.customStartTime,
                    customEndTime = draft.customEndTime,
                    customPeriodTimes = draft.customPeriodTimes,
                    selectedPeriodIndices = if (draft.customLessonTimesEdited) {
                        parseCoursePeriodTimes(draft.customPeriodTimes).map { it.index }
                    } else course?.periods?.takeIf {
                        !draft.periodRangeEdited &&
                        it.minOrNull() == draft.periodStart && it.maxOrNull() == draft.periodEnd
                    } ?: periodValues.filter { it in draft.periodStart..draft.periodEnd },
                    onRangeSelected = { start, end ->
                        onDraftChange(
                            draft.copy(
                                periodStart = start,
                                periodEnd = end,
                                customStartTime = null,
                                customEndTime = null,
                                customPeriodTimes = null,
                                customLessonTimesEdited = false,
                                periodRangeEdited = true,
                                timingSelectionEdited = true
                            )
                        )
                    },
                    onCustomTimeSelected = { startTime, endTime ->
                        onDraftChange(
                            draft.copy(
                                customStartTime = startTime,
                                customEndTime = endTime,
                                customPeriodTimes = null,
                                customLessonTimesEdited = false,
                                independentPeriodSchemeId = null,
                                timingSelectionEdited = true
                            )
                        )
                    },
                    onLessonTimesSelected = { times ->
                        onDraftChange(draft.copy(
                            periodStart = times.first().index,
                            periodEnd = times.last().index,
                            customStartTime = times.first().start.toString(),
                            customEndTime = times.last().end.toString(),
                            customPeriodTimes = encodeCoursePeriodTimes(times),
                            customLessonTimesEdited = true,
                            independentPeriodSchemeId = null,
                            timingSelectionEdited = true
                        ))
                    },
                    onOpenPicker = onOpenPicker,
                    backdrop = backdrop,
                    config = config
                ) { "第${it}节" }
            }
        }
        item(key = "weeks", contentType = "selector") {
            Box(Modifier.fillMaxWidth().courseEditorFormRowEntrance(7)) {
            DialogMultiGridSelector(
                title = "周次",
                    values = ((1..totalWeeks).toList() + draft.weeks).distinct().sorted(),
                    selected = draft.weeks,
                    displayValue = compactWeekSelectionLabel(draft.weeks.toList()),
                    preferredColumns = 5,
                    onSelected = {
                        val mode = inferCourseWeekSelectionMode(it)
                        onDraftChange(draft.copy(weeks = it, parity = mode.toWeekParity()))
                    },
                    onOpenPicker = onOpenPicker,
                    backdrop = backdrop,
                    config = config
                ) { it.toString() }
            }
        }
        if (draft.weeks.any { it > totalWeeks }) {
            item(key = "weeks-outside-term", contentType = "context") {
                Text(
                    text = "学期共 $totalWeeks 周，原排课的${compactWeekSelectionLabel(draft.weeks.filter { it > totalWeeks })}超出学期。修改其他内容会保留这些周次；需要调整时请重新选择周次。",
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalContentColor.current.copy(alpha = 0.82f)
                )
            }
        }
        item(key = "parity", contentType = "selector") {
            Box(Modifier.fillMaxWidth().courseEditorFormRowEntrance(8)) {
            val selectedMode = inferCourseWeekSelectionMode(draft.weeks)
                DialogSingleWheelSelector(
                    title = "单双周",
                    values = CourseWeekSelectionMode.entries,
                    selected = selectedMode,
                    onSelected = { mode ->
                        val weeks = weeksForCourseWeekSelectionMode(mode, draft.weeks, totalWeeks)
                        onDraftChange(draft.copy(weeks = weeks, parity = mode.toWeekParity()))
                    },
                    onOpenPicker = onOpenPicker,
                    backdrop = backdrop,
                    config = config
                ) { courseWeekSelectionModeLabel(it) }
            }
        }
        if (courseCardAllowsCustomOverrides(config)) {
            item(key = "color", contentType = "selector") {
                Box(Modifier.fillMaxWidth().courseEditorFormRowEntrance(9)) {
                CourseEditorColorValue(
                    colorArgb = draft.customColorArgb,
                    automaticColorArgb = courseCardBaseColor(
                        config,
                        course?.copy(customColorArgb = null)
                    ).toArgb().toLong() and 0xFFFFFFFFL,
                    onClick = onOpenColorPicker
                )
                }
            }
        }
        item(key = "note", contentType = "text-field") {
            DialogCapsuleField(
                value = draft.note,
                onValueChange = { onDraftChange(draft.copy(note = it)) },
                placeholder = "备注",
                config = config,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).courseEditorFormRowEntrance(10),
                fieldTextColor = editorFieldTextColor
            )
        }
        if (onDelete != null) {
            item(key = "delete", contentType = "action") {
                Box(Modifier.padding(horizontal = 16.dp).courseEditorFormRowEntrance(11)) {
                    DialogLiquidButton(
                        backdrop = backdrop,
                        label = "删除课程",
                        onClick = onDelete,
                        modifier = Modifier.fillMaxWidth(),
                        role = DialogButtonRole.Cancel,
                        destructiveFilled = true,
                        blurRadius = 8.dp
                    )
                }
            }
        }
        error?.let { message ->
            item(key = "error", contentType = "error") {
                Text(message, modifier = Modifier.padding(horizontal = 16.dp).courseEditorFormRowEntrance(12), color = MaterialTheme.colorScheme.error)
            }
        }
    }
    CourseEditorFixedHeader(
        title = title,
        backdrop = backdrop,
        gradientBackdrop = headerBackdrop,
        config = config,
        onCancel = onCancel,
        onSave = onSave,
        modifier = Modifier
            .align(Alignment.TopCenter)
            .zIndex(8f),
        contentModifier = Modifier.courseEditorFormRowEntrance(0)
    )
    }
    }
}
