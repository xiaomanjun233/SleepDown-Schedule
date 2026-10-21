package com.xiaomanjun.sleepdownschedule.feature.home.week

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.kyant.backdrop.Backdrop
import com.kyant.shapes.RoundedRectangle
import com.xiaomanjun.sleepdownschedule.CourseEntity
import com.xiaomanjun.sleepdownschedule.PeriodEntity
import com.xiaomanjun.sleepdownschedule.ScheduleConfigEntity
import com.xiaomanjun.sleepdownschedule.domain.course.WeekConflictGroup
import com.xiaomanjun.sleepdownschedule.domain.course.WeekCourseSegment
import com.xiaomanjun.sleepdownschedule.domain.course.buildWeekConflictGroups
import com.xiaomanjun.sleepdownschedule.domain.schedule.courseAlignmentFragments
import com.xiaomanjun.sleepdownschedule.domain.schedule.courseNeedsSupplementaryWeekRow
import com.xiaomanjun.sleepdownschedule.exactTimeWeekPlacement
import com.xiaomanjun.sleepdownschedule.feature.course.editor.CourseEditorWeekGrid
import com.xiaomanjun.sleepdownschedule.feature.course.editor.LocalCourseEditorFlightRegistry
import com.xiaomanjun.sleepdownschedule.feature.home.adaptiveWeekCardCornerRadius
import com.xiaomanjun.sleepdownschedule.feature.home.currentWindowSizeDp
import com.xiaomanjun.sleepdownschedule.feature.home.day.HomeReadableText
import com.xiaomanjun.sleepdownschedule.feature.home.day.homeForegroundColor
import com.xiaomanjun.sleepdownschedule.feature.home.day.occurrenceOverrideKey
import com.xiaomanjun.sleepdownschedule.feature.home.homeSwitchGroup
import com.xiaomanjun.sleepdownschedule.feature.home.overlay.CourseCopyPhase
import com.xiaomanjun.sleepdownschedule.feature.home.overlay.CourseCopyPlaceholder
import com.xiaomanjun.sleepdownschedule.feature.home.overlay.CourseCopyTap
import com.xiaomanjun.sleepdownschedule.feature.home.overlay.LocalCourseCopy
import com.xiaomanjun.sleepdownschedule.feature.home.overlay.LocalCourseRemoval
import com.xiaomanjun.sleepdownschedule.feature.home.overlay.courseCopyGridTap
import com.xiaomanjun.sleepdownschedule.feature.home.overlay.courseShortcutPivot
import com.xiaomanjun.sleepdownschedule.glass.CourseGlassRestoreGroup
import com.xiaomanjun.sleepdownschedule.glass.LocalCourseGlassOcclusionPhase
import com.xiaomanjun.sleepdownschedule.glass.LocalCourseGlassRestorePlan
import com.xiaomanjun.sleepdownschedule.glass.LocalCourseGlassRestoreRegistry
import com.xiaomanjun.sleepdownschedule.glass.courseGlassRestoreGroupKey
import com.xiaomanjun.sleepdownschedule.glass.ui.CourseGlassCard
import com.xiaomanjun.sleepdownschedule.glass.ui.MutedCourseLightColor
import com.xiaomanjun.sleepdownschedule.glass.ui.glassForegroundColor
import com.xiaomanjun.sleepdownschedule.hasCustomTime
import com.xiaomanjun.sleepdownschedule.scheduleWeekStartDate
import kotlinx.coroutines.flow.map

internal data class WeekRenderedSegment(
    val groupIndex: Int,
    val group: WeekConflictGroup,
    val segment: WeekCourseSegment
)

internal data class WeekCustomTimeLabels(
    val showStart: Boolean,
    val showEnd: Boolean,
    val showRangeBelow: Boolean
)

internal fun weekCustomTimeLabels(
    top: Float,
    bottom: Float,
    occupied: List<Pair<Float, Float>>,
    gridBottom: Float,
    textHeight: Float
): WeekCustomTimeLabels {
    fun hasSpace(labelTop: Float, labelBottom: Float): Boolean =
        labelTop >= 0f && labelBottom <= gridBottom && occupied.none { (cardTop, cardBottom) ->
            cardBottom > labelTop - 1f && cardTop < labelBottom + 1f
        }

    val startFits = hasSpace(top - 10f, top - 10f + textHeight)
    val endFits = hasSpace(bottom + 10f - textHeight, bottom + 10f)
    return when {
        startFits && endFits -> WeekCustomTimeLabels(true, true, false)
        endFits -> WeekCustomTimeLabels(false, false, true)
        else -> WeekCustomTimeLabels(startFits, false, false)
    }
}

internal fun weekCardVerticalBounds(
    segment: WeekCourseSegment,
    periods: List<PeriodEntity>,
    cardHeight: Dp
): Pair<Float, Float> {
    val exact = exactTimeWeekPlacement(segment.course, periods)
    val top = cardHeight.value * (exact?.topRows ?: segment.startPosition.toFloat()) +
        if (exact == null) 2f else 0f
    val height = if (exact != null) {
        (cardHeight.value * exact.heightRows).coerceAtLeast(1f)
    } else {
        (cardHeight.value * segment.span - 4f).coerceAtLeast(18f)
    }
    return top to top + height
}

private const val WeekPageGlassMinimumFillRatio = 0.34f
private const val WeekPageGlassMaximumLayerAreaFraction = 0.58f

private fun weekGlassCandidateId(
    dayIndex: Int,
    segment: WeekCourseSegment,
    groupIndex: Int
): String = "$dayIndex:${segment.course.id}:${segment.startPosition}:${segment.endPosition}:$groupIndex"

private fun weekSupplementaryTailKey(dayIndex: Int, courseId: Long, index: Int): String =
    "$dayIndex:supplementary:$courseId:$index"

private fun weekReferenceTailKey(dayIndex: Int, card: NonCurrentWeekCard): String =
    "$dayIndex:reference:${card.course.id}:${card.top.toBits()}:${card.bottom.toBits()}"

private fun weekReferenceSupplementaryTailKey(dayIndex: Int, courseId: Long, index: Int): String =
    "$dayIndex:reference-supplementary:$courseId:$index"

private fun renderedWeekSegments(
    conflictGroups: List<WeekConflictGroup>,
    conflictFocusCourseId: Long?,
    conflictFocusCourseKey: String?
): List<WeekRenderedSegment> = buildList {
    conflictGroups.forEachIndexed { groupIndex, group ->
        val visibleCourse = group.courses.firstOrNull { it.id == conflictFocusCourseId }
            ?: group.courses.firstOrNull {
                conflictFocusCourseKey != null &&
                    it.occurrenceOverrideKey() == conflictFocusCourseKey
            }
            ?: group.courses.first()
        val visibleSegments = group.segments.filter { it.course.id == visibleCourse.id }
        val segments = if (visibleCourse.hasCustomTime() && visibleCourse.arrangementProjection == null) {
            visibleSegments.minByOrNull { it.startPosition }?.let(::listOf).orEmpty()
        } else {
            visibleSegments
        }
        segments.forEach { segment ->
            add(WeekRenderedSegment(groupIndex, group, segment))
        }
    }
}

@Composable
private fun WeekDayColumn(
    courses: List<CourseEntity>,
    referenceCards: List<NonCurrentWeekCard> = emptyList(),
    holidayLabel: String? = null,
    renderedSegments: List<WeekRenderedSegment>,
    tailCardOrder: Map<String, Float>,
    tailColumnFraction: Float?,
    periods: List<PeriodEntity>,
    cardHeight: Dp,
    cardColor: ComposeColor,
    emptyBackground: ComposeColor,
    backdrop: Backdrop?,
    floatingBackdrop: Backdrop? = backdrop,
    config: ScheduleConfigEntity,
    shortcutPivotX: Float,
    weekMotionDirection: Int = 0,
    weekMotionOutgoing: Boolean = false,
    dayIndex: Int = 1,
    gridColumnWidth: Dp = 0.dp,
    periodRowHeight: Dp = cardHeight,
    layerOffset: Animatable<Float, AnimationVector1D>? = null,
    layerTravel: Float = 1f,
    editMode: Boolean = false,
    editingAllowed: Boolean = true,
    muted: Boolean = false,
    editWeek: Int = 1,
    allWeekCourses: List<CourseEntity> = emptyList(),
    weekdayCount: Int = 7,
    editScrollState: ScrollState? = null,
    onEnterEditMode: () -> Unit = {},
    onUpdateSingleWeekCourse: (CourseEntity, CourseEntity) -> Unit = { _, _ -> },
    conflictFocusCourseId: Long? = null,
    conflictFocusCourseKey: String? = null,
    onResolveCourseConflict: (CourseEntity, CourseEntity) -> Unit = { _, _ -> },
    onDeleteSingleWeekCourse: (CourseEntity) -> Unit = {},
    onCourseClick: (CourseEntity, Rect?) -> Unit,
    onDragStateChanged: (dayIndex: Int?, courseId: Long?) -> Unit = { _, _ -> },
    occlusionMaterialMounted: Boolean = true,
    draggingCourseId: Long? = null,
    activeOverlayCourseId: Long? = null,
    activeOverlayTargetKey: String? = null,
    activeOverlayTargetWeek: Int = 0,
    onStartWeekEditOverlay: (WeekEditOverlayRequest) -> Unit = {},
    onDragWeekEditOverlay: (Offset) -> Unit = {},
    onFinishMoveOverlay: (Velocity) -> Unit = {},
    onFinishResizeOverlay: (Velocity) -> Unit = {},
    onCancelWeekEditOverlay: () -> Unit = {}
) {
    val density = LocalDensity.current
    val periodIndexes = remember(periods) { periods.map { it.periodIndex } }
    val courseGlassRestorePlan = LocalCourseGlassRestorePlan.current
    val visibleCardBounds = remember(renderedSegments, periods, cardHeight) {
        renderedSegments.map { it.segment.course.id to weekCardVerticalBounds(it.segment, periods, cardHeight) }
    }
    val timeLabelHeight = with(density) { 9.sp.toDp().value } + 2f

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(cardHeight * periods.size.toFloat())
    ) {
        val measuredCardLayoutWidth = (maxWidth - 4.dp).coerceAtLeast(1.dp)
        if (emptyBackground.alpha > 0f) {
            Column(Modifier.fillMaxSize()) {
                periods.forEach { EmptyWeekCell(cardHeight, emptyBackground) }
            }
        }
        if (holidayLabel != null) {
            Box(Modifier.fillMaxSize().padding(2.dp)
                .clip(RoundedRectangle(14.dp)).background(MutedCourseLightColor.copy(alpha = 0.18f)),
                contentAlignment = Alignment.TopCenter) {
                Text(holidayLabel, Modifier.padding(horizontal = 4.dp, vertical = 18.dp),
                    color = homeForegroundColor(config).copy(alpha = 0.6f),
                    textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium)
            }
        }
        referenceCards.forEach { card ->
            key(card.course.id, card.top, card.bottom) {
                val tailKey = weekReferenceTailKey(dayIndex, card)
                val height = (card.bottom - card.top).dp
                val corner = adaptiveWeekCardCornerRadius(measuredCardLayoutWidth, height,
                    currentWindowSizeDp().width, currentWindowSizeDp().height, config.weekCardCornerProgress)
                CourseGlassCard(backdrop = backdrop, config = config, course = card.course,
                    muted = true, shape = RoundedRectangle(corner), onClick = null,
                    modifier = Modifier.offset(y = card.top.dp).fillMaxWidth()
                        .padding(horizontal = 2.dp).height(height)
                        .weekPageTail(tailKey, tailCardOrder[tailKey], tailColumnFraction)
                        .homeSwitchGroup(tailCardOrder[tailKey])) {
                    WeekCourseOverlayCardContent(card.course, config, muted = true)
                }
            }
        }
        renderedSegments.forEach { rendered ->
            val groupIndex = rendered.groupIndex
            val group = rendered.group
            val segment = rendered.segment
            val glassCandidateId = weekGlassCandidateId(dayIndex, segment, groupIndex)
            val underlyingSegment = group.segments
                .asSequence()
                .filter { it.course.id != segment.course.id }
                .filter {
                    it.startPosition <= segment.endPosition &&
                        it.endPosition >= segment.startPosition
                }
                .sortedWith(
                    compareBy<WeekCourseSegment> { it.startPosition }
                        .thenBy { it.endPosition }
                        .thenBy { it.course.id }
                )
                .firstOrNull()
            val exactPlacement = exactTimeWeekPlacement(segment.course, periods)
            val segmentTopRows = exactPlacement?.topRows ?: segment.startPosition.toFloat()
            val segmentHeightRows = exactPlacement?.heightRows ?: segment.span.toFloat()
            val segmentHeight = if (exactPlacement != null) {
                (cardHeight * segmentHeightRows).coerceAtLeast(1.dp)
            } else {
                (cardHeight * segmentHeightRows - 4.dp).coerceAtLeast(18.dp)
            }
            val segmentTopInset = if (exactPlacement != null) 0.dp else 2.dp
            val timeLabels = exactPlacement?.let {
                weekCustomTimeLabels(
                    top = cardHeight.value * segmentTopRows,
                    bottom = cardHeight.value * segmentTopRows + segmentHeight.value,
                    occupied = visibleCardBounds.filter { it.first != segment.course.id }.map { it.second },
                    gridBottom = cardHeight.value * periods.size,
                    textHeight = timeLabelHeight
                )
            }
            Box(
                modifier = Modifier
                    .offset(y = cardHeight * segmentTopRows + segmentTopInset)
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp)
                    .height(segmentHeight)
                    .weekPageTail(
                        cardKey = glassCandidateId,
                        cardOrderFraction = tailCardOrder[glassCandidateId],
                        columnOrderFraction = tailColumnFraction
                    )
                    .zIndex(if (LocalCourseRemoval.current?.matches(segment.course, editWeek) == true) 20f else groupIndex.toFloat())
            ) {
                WeekCourseBlock(
                    course = segment.course,
                    periods = periods,
                    height = segmentHeight,
                    cardColor = cardColor,
                    backdrop = backdrop,
                    floatingBackdrop = floatingBackdrop,
                    config = config,
                    weekMotionDirection = weekMotionDirection,
                    weekMotionOutgoing = weekMotionOutgoing,
                    dayIndex = dayIndex,
                    periodIndex = periodIndexes[segment.startPosition],
                    shortcutPivotX = shortcutPivotX,
                    gridColumnWidth = gridColumnWidth,
                    cardLayoutWidth = measuredCardLayoutWidth,
                    periodRowHeight = periodRowHeight,
                    layerOffset = layerOffset,
                    layerTravel = layerTravel,
                    stackIndex = groupIndex,
                    cardOrderFraction = tailCardOrder[glassCandidateId],
                    columnOrderFraction = tailColumnFraction,
                    conflictWarning = group.hasConflict,
                    conflictUnderlyingCourse = underlyingSegment?.course,
                    conflictUnderlyingPeriodIndex = underlyingSegment
                        ?.let { periodIndexes[it.startPosition] },
                    conflictUnderlyingSpan = underlyingSegment?.span ?: 0,
                    onResolveConflict = { moved ->
                        onResolveCourseConflict(segment.course, moved)
                    },
                    editingAllowed = editingAllowed,
                    muted = muted,
                    editMode = editMode,
                    editWeek = editWeek,
                    allWeekCourses = allWeekCourses,
                    weekdayCount = weekdayCount,
                    editScrollState = editScrollState,
                    onEnterEditMode = onEnterEditMode,
                    onUpdateSingleWeekCourse = onUpdateSingleWeekCourse,
                    onDeleteSingleWeekCourse = onDeleteSingleWeekCourse,
                    onCourseClick = onCourseClick,
                    onDragStateChanged = onDragStateChanged,
                    activeOverlayCourseId = activeOverlayCourseId,
                    activeOverlayTargetKey = activeOverlayTargetKey,
                    activeOverlayTargetWeek = activeOverlayTargetWeek,
                    onStartWeekEditOverlay = onStartWeekEditOverlay,
                    onDragWeekEditOverlay = onDragWeekEditOverlay,
                    onFinishMoveOverlay = onFinishMoveOverlay,
                    onFinishResizeOverlay = onFinishResizeOverlay,
                    onCancelWeekEditOverlay = onCancelWeekEditOverlay,
                    occlusionMaterialMounted = occlusionMaterialMounted &&
                        courseGlassRestorePlan.mountsGroup(courseGlassRestoreGroupKey(editWeek, listOf(glassCandidateId)))
                )
                timeLabels?.let { labels ->
                    val labelColor = glassForegroundColor(config)
                    if (labels.showStart) WeekCourseTimeLabel(
                                text = segment.course.customStartTime.orEmpty(),
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .offset(y = (-10).dp)
                                    .zIndex(12f),
                                color = labelColor,
                                fontSize = 8.sp,
                                lineHeight = 9.sp,
                    )
                    if (labels.showEnd) WeekCourseTimeLabel(
                                text = segment.course.customEndTime.orEmpty(),
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .offset(y = 10.dp)
                                    .zIndex(12f),
                                color = labelColor,
                                fontSize = 8.sp,
                                lineHeight = 9.sp,
                    )
                    if (labels.showRangeBelow) WeekCourseTimeLabel(
                        text = "${segment.course.customStartTime.orEmpty()}–${segment.course.customEndTime.orEmpty()}",
                        modifier = Modifier.align(Alignment.BottomCenter).offset(y = 10.dp).zIndex(12f),
                        color = labelColor,
                        fontSize = 7.sp,
                        lineHeight = 9.sp,
                    )
                }
            }
        }
    }
}

@Composable
fun WeekCourseColumnsLayer(
    modifier: Modifier = Modifier,
    courses: List<CourseEntity>,
    referenceCourses: List<CourseEntity> = emptyList(),
    onRenderedCardCountChanged: ((Int) -> Unit)? = null,
    showSupplementaryRows: Boolean = false,
    weekdays: List<Int>,
    periods: List<PeriodEntity>,
    cardHeight: Dp,
    cardColor: ComposeColor,
    backdrop: Backdrop?,
    floatingBackdrop: Backdrop? = backdrop,
    config: ScheduleConfigEntity,
    weekMotionDirection: Int,
    outgoing: Boolean,
    layerOffset: Animatable<Float, AnimationVector1D>,
    gestureOffset: () -> Float = { 0f },
    editMode: Boolean = false,
    editWeek: Int = 1,
    allWeekCourses: List<CourseEntity> = emptyList(),
    editScrollState: ScrollState? = null,
    onEnterEditMode: () -> Unit = {},
    onUpdateSingleWeekCourse: (CourseEntity, CourseEntity) -> Unit = { _, _ -> },
    conflictFocusCourseId: Long? = null,
    conflictFocusCourseKey: String? = null,
    onResolveCourseConflict: (CourseEntity, CourseEntity) -> Unit = { _, _ -> },
    onDeleteSingleWeekCourse: (CourseEntity) -> Unit = {},
    activeOverlayCourseId: Long? = null,
    activeOverlayTargetKey: String? = null,
    activeOverlayTargetWeek: Int = 0,
    onStartWeekEditOverlay: (WeekEditOverlayRequest) -> Unit = {},
    onDragWeekEditOverlay: (Offset) -> Unit = {},
    onFinishMoveOverlay: (Velocity) -> Unit = {},
    onFinishResizeOverlay: (Velocity) -> Unit = {},
    onCancelWeekEditOverlay: () -> Unit = {},
    onCourseClick: (CourseEntity, Rect?) -> Unit
) {
    val density = LocalDensity.current
    val flightRegistry = LocalCourseEditorFlightRegistry.current
    val courseCopy = LocalCourseCopy.current
    val copyHaptic = LocalHapticFeedback.current
    val copyPicking = courseCopy?.phase == CourseCopyPhase.Selecting && !outgoing
    val selectCopyPosition: (Offset) -> Unit = { point ->
        val grid = flightRegistry?.grid(editWeek)
        if (courseCopy != null && grid != null && copyPicking) {
            val physicalColumn = if (grid.width > 0f && point.x >= 0f && point.x < grid.width)
                (point.x * weekdays.size / grid.width).toInt() else -1
            val day = weekdays.getOrNull(if (grid.rightToLeft) weekdays.lastIndex - physicalColumn else physicalColumn)
            if (day != null && com.xiaomanjun.sleepdownschedule.domain.schedule.scheduleAdjustmentForDate(config,
                    scheduleWeekStartDate(config, editWeek).plusDays((day - 1).toLong())) != null) {
                courseCopy.reject("调休日请在原课程日期复制课程")
            } else {
                when (courseCopyGridTap(courseCopy, editWeek, grid, weekdays, periods, point)) {
                    CourseCopyTap.Selected -> copyHaptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    CourseCopyTap.Confirmed -> copyHaptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    CourseCopyTap.Rejected -> Unit
                }
            }
        }
    }
    val latestSelectCopyPosition by rememberUpdatedState(selectCopyPosition)
    val flightLayoutDirection = androidx.compose.ui.platform.LocalLayoutDirection.current
    DisposableEffect(flightRegistry, editWeek) {
        onDispose { flightRegistry?.remove(editWeek) }
    }
    val supplementaryCoursesByDay = remember(courses, periods) {
        courses.flatMap { courseAlignmentFragments(it, periods) }.filter { courseNeedsSupplementaryWeekRow(it, periods) }
            .sortedBy { it.customStartTime }.groupBy { it.weekday }
    }
    val coursesByWeekday = remember(courses, periods) {
        courses.flatMap { courseAlignmentFragments(it, periods) }.filterNot { courseNeedsSupplementaryWeekRow(it, periods) }.groupBy { it.weekday }
    }
    val periodIndexes = remember(periods) { periods.map { it.periodIndex } }
    val renderedSegmentsByDay = remember(
        coursesByWeekday,
        weekdays,
        periodIndexes,
        periods,
        conflictFocusCourseId,
        conflictFocusCourseKey
    ) {
        weekdays.associateWith { day ->
            renderedWeekSegments(
                conflictGroups = buildWeekConflictGroups(
                    coursesByWeekday[day].orEmpty(),
                    periodIndexes,
                    periods
                ),
                conflictFocusCourseId = conflictFocusCourseId,
                conflictFocusCourseKey = conflictFocusCourseKey
            )
        }
    }
    val adjustmentsByDay = remember(config, editWeek, weekdays) {
        val start = scheduleWeekStartDate(config, editWeek)
        weekdays.associateWith { day -> com.xiaomanjun.sleepdownschedule.domain.schedule.scheduleAdjustmentForDate(config, start.plusDays((day - 1).toLong())) }
    }
    val referencesByDay = remember(referenceCourses, courses, config.showNonCurrentWeekCourses,
        editWeek, weekdays, adjustmentsByDay, periods, cardHeight, renderedSegmentsByDay) {
        weekdays.associateWith { day ->
            if (config.showNonCurrentWeekCourses && adjustmentsByDay[day]?.let { it.sourceDate == null } != true)
                nonCurrentWeekCards(referenceCourses, courses, editWeek, day, periods, cardHeight.value,
                    renderedSegmentsByDay[day].orEmpty().map { weekCardVerticalBounds(it.segment, periods, cardHeight) })
            else emptyList()
        }
    }
    val supplementaryReferencesByDay = remember(referenceCourses, courses, config.showNonCurrentWeekCourses,
        editWeek, weekdays, adjustmentsByDay, periods) {
        weekdays.associateWith { day ->
            if (config.showNonCurrentWeekCourses && adjustmentsByDay[day]?.let { it.sourceDate == null } != true)
                nonCurrentSupplementaryCourses(referenceCourses, courses, editWeek, day, periods)
            else emptyList()
        }
    }
    // Count presentation cards only for animation, never for reminders or course statistics.
    // Adjacent retained weeks must not overwrite the current page's animation policy.
    if (onRenderedCardCountChanged != null) SideEffect {
        onRenderedCardCountChanged(weekdays.sumOf { day ->
            val adjustment = adjustmentsByDay[day]
            if (adjustment?.allDayPlaceholder == true && adjustment.sourceDate == null) 1 else
                renderedSegmentsByDay[day].orEmpty().size + referencesByDay[day].orEmpty().size +
                    if (showSupplementaryRows) supplementaryCoursesByDay[day].orEmpty().size +
                        supplementaryReferencesByDay[day].orEmpty().size else 0
        })
    }
    val tailCardOrder = remember(renderedSegmentsByDay, supplementaryCoursesByDay,
        referencesByDay, supplementaryReferencesByDay, showSupplementaryRows, weekdays, periods, cardHeight) {
        val cards = weekdays.flatMapIndexed { column, day ->
            val scheduled = renderedSegmentsByDay[day].orEmpty().map { rendered ->
                val segment = rendered.segment
                Triple(
                    weekGlassCandidateId(day, segment, rendered.groupIndex),
                    exactTimeWeekPlacement(segment.course, periods)?.topRows
                        ?: segment.startPosition.toFloat(),
                    column
                )
            }
            val supplementary = supplementaryCoursesByDay[day].orEmpty().mapIndexed { index, course ->
                Triple(
                    weekSupplementaryTailKey(day, course.id, index),
                    periods.size.toFloat() + index + 1f,
                    column
                )
            }
            val references = referencesByDay[day].orEmpty().map { card ->
                Triple(weekReferenceTailKey(day, card), card.top / cardHeight.value, column)
            }
            val supplementaryReferences = if (showSupplementaryRows)
                supplementaryReferencesByDay[day].orEmpty().mapIndexed { index, course ->
                    Triple(weekReferenceSupplementaryTailKey(day, course.id, index),
                        periods.size.toFloat() + supplementary.size + index + 1f, column)
                } else emptyList()
            scheduled + supplementary + references + supplementaryReferences
        }.sortedWith(compareBy<Triple<String, Float, Int>> { it.second }
            .thenBy { it.third }.thenBy { it.first })
        cards.mapIndexed { index, card ->
            card.first to if (cards.size > 1) index.toFloat() / (cards.size - 1) else 0.5f
        }.toMap()
    }
    val tailColumnOrder = remember(renderedSegmentsByDay, supplementaryCoursesByDay,
        referencesByDay, supplementaryReferencesByDay, showSupplementaryRows, weekdays) {
        val occupied = weekdays.filter { day ->
            renderedSegmentsByDay[day].orEmpty().isNotEmpty() ||
                supplementaryCoursesByDay[day].orEmpty().isNotEmpty() ||
                referencesByDay[day].orEmpty().isNotEmpty() ||
                (showSupplementaryRows && supplementaryReferencesByDay[day].orEmpty().isNotEmpty())
        }
        occupied.mapIndexed { index, day ->
            day to if (occupied.size > 1) index.toFloat() / (occupied.size - 1) else 0.5f
        }.toMap()
    }
    val courseGlassRestoreRegistry = LocalCourseGlassRestoreRegistry.current
    val courseGlassOcclusionPhase = LocalCourseGlassOcclusionPhase.current
    var draggingDayIndex by remember { mutableStateOf<Int?>(null) }
    var draggingCourseId by remember { mutableStateOf<Long?>(null) }
    // The restore coordinator now owns one material unit per card, never a sampled group.
    val restoreCards = remember(renderedSegmentsByDay, weekdays, editWeek) {
        weekdays.flatMapIndexed { column, day ->
            renderedSegmentsByDay[day].orEmpty().map { rendered ->
                CourseGlassRestoreGroup(
                    key = courseGlassRestoreGroupKey(editWeek, listOf(
                        weekGlassCandidateId(day, rendered.segment, rendered.groupIndex)
                    )),
                    pageWeek = editWeek,
                    normalizedCenterX = (column + 0.5f) / weekdays.size.coerceAtLeast(1)
                )
            }
        }
    }
    SideEffect {
        courseGlassRestoreRegistry?.replacePage(editWeek, restoreCards, courseGlassOcclusionPhase.usesFrozenGroupPlan)
    }
    DisposableEffect(courseGlassRestoreRegistry, editWeek) {
        onDispose { courseGlassRestoreRegistry?.removePage(editWeek) }
    }
    // The pager path supplies a zero offset. Keeping a full-grid layer at rest clips the
    // outermost columns' glass differently from the cards in the middle.
    val gridMoving = layerOffset.isRunning || layerOffset.value != 0f || gestureOffset() != 0f
    val gridMotionModifier = if (gridMoving) Modifier.graphicsLayer {
        clip = false
        translationX = layerOffset.value + gestureOffset()
    } else Modifier
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(copyPicking) {
                if (copyPicking) detectTapGestures { latestSelectCopyPosition(it) }
            }
            .onGloballyPositioned { coordinates ->
                if (!outgoing && flightRegistry?.frozen == false) {
                    flightRegistry.record(editWeek, CourseEditorWeekGrid(
                        coordinates.localToRoot(Offset.Zero), coordinates.size.width.toFloat(),
                        with(density) { cardHeight.toPx() }, with(density) { 4.dp.toPx() },
                        periodIndexes, editScrollState, editScrollState?.value ?: 0,
                        flightLayoutDirection == androidx.compose.ui.unit.LayoutDirection.Rtl
                    ))
                }
            }
            .then(gridMotionModifier)
    ) {
        val dayColumnWidth = maxWidth / weekdays.size.coerceAtLeast(1)
        val travel = with(density) { (maxWidth + 96.dp).toPx() }
        val layoutDirection = androidx.compose.ui.platform.LocalLayoutDirection.current
        Row(modifier = Modifier.fillMaxWidth()) {
            weekdays.forEachIndexed { columnIndex, day ->
                val shortcutPivotX = courseShortcutPivot(columnIndex, weekdays.size, layoutDirection)
                val dayAdjustment = adjustmentsByDay[day]
                val adjustedDay = dayAdjustment != null
                // Days off keep their regular cards on screen, greyed and read-only.
                val cancelledDay = adjustedDay && dayAdjustment.sourceDate == null
                val enterDayEditMode = { if (!adjustedDay) onEnterEditMode() }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .zIndex(
                            if (coursesByWeekday[day].orEmpty().any {
                                    LocalCourseRemoval.current?.matches(it, editWeek) == true
                                }) 2f else if (draggingDayIndex == day) 1f else 0f
                        )
                ) {
                    val fullDayPlaceholder = cancelledDay && dayAdjustment?.allDayPlaceholder == true
                    WeekDayColumn(
                        courses = coursesByWeekday[day].orEmpty(),
                        renderedSegments = if (fullDayPlaceholder) emptyList() else renderedSegmentsByDay[day].orEmpty(),
                        referenceCards = referencesByDay[day].orEmpty(),
                        holidayLabel = if (fullDayPlaceholder) dayAdjustment?.label.orEmpty().ifBlank { "假期" } else null,
                        tailCardOrder = tailCardOrder,
                        tailColumnFraction = tailColumnOrder[day],
                        periods = periods,
                        cardHeight = cardHeight,
                        cardColor = cardColor,
                        emptyBackground = ComposeColor.Transparent,
                        backdrop = backdrop,
                        floatingBackdrop = floatingBackdrop,
                        config = config,
                        weekMotionDirection = weekMotionDirection,
                        weekMotionOutgoing = outgoing,
                        dayIndex = day,
                        shortcutPivotX = shortcutPivotX,
                        gridColumnWidth = dayColumnWidth,
                        periodRowHeight = cardHeight,
                        layerOffset = layerOffset,
                        layerTravel = travel,
                        editMode = editMode && !adjustedDay,
                        editingAllowed = !adjustedDay,
                        muted = cancelledDay,
                        editWeek = editWeek,
                        allWeekCourses = allWeekCourses,
                        weekdayCount = weekdays.size,
                        editScrollState = editScrollState,
                        onEnterEditMode = enterDayEditMode,
                        onUpdateSingleWeekCourse = onUpdateSingleWeekCourse,
                        conflictFocusCourseId = conflictFocusCourseId,
                        conflictFocusCourseKey = conflictFocusCourseKey,
                        onResolveCourseConflict = onResolveCourseConflict,
                        onDeleteSingleWeekCourse = onDeleteSingleWeekCourse,
                        onCourseClick = onCourseClick,
                        onDragStateChanged = { dayIndex, courseId ->
                            draggingDayIndex = dayIndex
                            draggingCourseId = courseId
                        },
                        occlusionMaterialMounted = true,
                        draggingCourseId = draggingCourseId,
                        activeOverlayCourseId = activeOverlayCourseId,
                        activeOverlayTargetKey = activeOverlayTargetKey,
                        activeOverlayTargetWeek = activeOverlayTargetWeek,
                        onStartWeekEditOverlay = onStartWeekEditOverlay,
                        onDragWeekEditOverlay = onDragWeekEditOverlay,
                        onFinishMoveOverlay = onFinishMoveOverlay,
                        onFinishResizeOverlay = onFinishResizeOverlay,
                        onCancelWeekEditOverlay = onCancelWeekEditOverlay
                    )
                    if (showSupplementaryRows) {
                        Text(
                            text = if (columnIndex == 0) "其他时间" else "",
                            modifier = Modifier.height(24.dp).padding(top = 5.dp),
                            color = glassForegroundColor(config),
                            fontSize = 9.sp,
                            maxLines = 1
                        )
                        (if (fullDayPlaceholder) emptyList() else supplementaryCoursesByDay[day].orEmpty()).forEachIndexed { index, course ->
                            Column(
                                modifier = Modifier.fillMaxWidth().height(88.dp).padding(horizontal = 2.dp)
                                    .weekPageTail(
                                        cardKey = weekSupplementaryTailKey(day, course.id, index),
                                        cardOrderFraction = tailCardOrder[weekSupplementaryTailKey(day, course.id, index)],
                                        columnOrderFraction = tailColumnOrder[day]
                                    ),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                WeekCourseTimeLabel(course.customStartTime.orEmpty(), fontSize = 8.sp, lineHeight = 10.sp,
                                    color = glassForegroundColor(config), modifier = Modifier.height(12.dp))
                                WeekCourseBlock(
                                    course = course, periods = periods, height = 64.dp,
                                    cardColor = cardColor, backdrop = backdrop, floatingBackdrop = floatingBackdrop,
                                    config = config, dayIndex = day, gridColumnWidth = dayColumnWidth,
                                    shortcutPivotX = shortcutPivotX,
                                    stackIndex = index,
                                    cardOrderFraction = tailCardOrder[weekSupplementaryTailKey(day, course.id, index)],
                                    columnOrderFraction = tailColumnOrder[day],
                                    editMode = editMode && !adjustedDay, editWeek = editWeek,
                                    editingAllowed = !adjustedDay,
                                    muted = cancelledDay,
                                    allWeekCourses = allWeekCourses, editScrollState = editScrollState,
                                    onEnterEditMode = enterDayEditMode,
                                    onDeleteSingleWeekCourse = onDeleteSingleWeekCourse,
                                    onCourseClick = onCourseClick
                                )
                                WeekCourseTimeLabel(course.customEndTime.orEmpty(), fontSize = 8.sp, lineHeight = 10.sp,
                                    color = glassForegroundColor(config), modifier = Modifier.height(12.dp))
                            }
                        }
                        val supplementaryReferences = supplementaryReferencesByDay[day].orEmpty()
                        supplementaryReferences.forEachIndexed { index, course ->
                            val tailKey = weekReferenceSupplementaryTailKey(day, course.id, index)
                            key(tailKey) {
                                Column(Modifier.fillMaxWidth().height(88.dp).padding(horizontal = 2.dp)
                                    .weekPageTail(tailKey, tailCardOrder[tailKey], tailColumnOrder[day])
                                    .homeSwitchGroup(tailCardOrder[tailKey]),
                                    horizontalAlignment = Alignment.CenterHorizontally) {
                                    WeekCourseTimeLabel(course.customStartTime.orEmpty(), fontSize = 8.sp, lineHeight = 10.sp,
                                        color = glassForegroundColor(config), modifier = Modifier.height(12.dp))
                                    CourseGlassCard(backdrop, config, Modifier.fillMaxWidth().height(64.dp),
                                        course = course, muted = true) {
                                        WeekCourseOverlayCardContent(course, config, muted = true)
                                    }
                                    WeekCourseTimeLabel(course.customEndTime.orEmpty(), fontSize = 8.sp, lineHeight = 10.sp,
                                        color = glassForegroundColor(config), modifier = Modifier.height(12.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
        CourseCopyPlaceholder(
            controller = courseCopy, week = editWeek,
            grid = CourseEditorWeekGrid(
                origin = Offset.Zero, width = with(density) { maxWidth.toPx() },
                rowHeight = with(density) { cardHeight.toPx() }, gap = with(density) { 4.dp.toPx() },
                periodIndexes = periodIndexes, scroll = null, scrollAtCapture = 0,
                rightToLeft = flightLayoutDirection == androidx.compose.ui.unit.LayoutDirection.Rtl
            ),
            weekdays = weekdays, periods = periods, config = config, onSelect = selectCopyPosition
        )
    }
}

@Composable
fun EmptyWeekCell(height: Dp, background: ComposeColor) {
    Box(
        modifier = Modifier
            .height(height)
            .fillMaxWidth()
            .padding(1.dp)
            .background(background)
    )
}
