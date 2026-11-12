package com.xiaomanjun.sleepdownschedule.feature.home.week

import android.os.SystemClock
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.kyant.backdrop.Backdrop
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle
import com.xiaomanjun.sleepdownschedule.CourseEntity
import com.xiaomanjun.sleepdownschedule.PeriodEntity
import com.xiaomanjun.sleepdownschedule.ScheduleConfigEntity
import com.xiaomanjun.sleepdownschedule.core.performance.LocalStartupPhase
import com.xiaomanjun.sleepdownschedule.core.performance.StartupPhase
import com.xiaomanjun.sleepdownschedule.core.ui.chrome.LocalEditingCourseId
import com.xiaomanjun.sleepdownschedule.core.ui.chrome.LocalSharedTransitionScope
import com.xiaomanjun.sleepdownschedule.core.ui.text.LocalCourseTextMotionFrozen
import com.xiaomanjun.sleepdownschedule.courseAllowsWeekPeriodDrag
import com.xiaomanjun.sleepdownschedule.domain.course.nearestAvailableCourseMove
import com.xiaomanjun.sleepdownschedule.feature.agent.homeCountdownShockwave
import com.xiaomanjun.sleepdownschedule.feature.home.CourseAdjustmentBadge
import com.xiaomanjun.sleepdownschedule.feature.home.LocalAdjustedCourseEditor
import com.xiaomanjun.sleepdownschedule.core.ui.text.LocalHomeBackgroundFrozen
import com.xiaomanjun.sleepdownschedule.core.ui.material.LocalPersonalizationPreview
import com.xiaomanjun.sleepdownschedule.feature.home.adaptiveWeekCardCornerRadius
import com.xiaomanjun.sleepdownschedule.feature.home.courseAdjustmentBadgeHeight
import com.xiaomanjun.sleepdownschedule.feature.home.courseBadgeContentInset
import com.xiaomanjun.sleepdownschedule.feature.home.courseBadgeCornerAnchor
import com.xiaomanjun.sleepdownschedule.feature.home.currentWindowSizeDp
import com.xiaomanjun.sleepdownschedule.feature.home.day.homeCourseTextColor
import com.xiaomanjun.sleepdownschedule.feature.home.day.homeForegroundColor
import com.xiaomanjun.sleepdownschedule.feature.home.day.occurrenceOverrideKey
import com.xiaomanjun.sleepdownschedule.feature.home.overlay.CourseBoundsSource
import com.xiaomanjun.sleepdownschedule.feature.home.overlay.CourseShortcutRequest
import com.xiaomanjun.sleepdownschedule.feature.home.overlay.LocalCourseCopy
import com.xiaomanjun.sleepdownschedule.feature.home.overlay.LocalCourseShortcuts
import com.xiaomanjun.sleepdownschedule.feature.home.overlay.courseRemovalMotion
import com.xiaomanjun.sleepdownschedule.glass.ui.CourseGlassCard
import com.xiaomanjun.sleepdownschedule.glass.ui.GlassSurface
import com.xiaomanjun.sleepdownschedule.glass.ui.GlassTokens
import com.xiaomanjun.sleepdownschedule.glass.ui.MutedCourseLightColor
import com.xiaomanjun.sleepdownschedule.glass.ui.courseCardBaseColor
import com.xiaomanjun.sleepdownschedule.glass.ui.courseCardUsesAssignments
import com.xiaomanjun.sleepdownschedule.glass.ui.glassUsesLightStyle
import com.xiaomanjun.sleepdownschedule.hasCustomTime
import com.xiaomanjun.sleepdownschedule.scheduleWeekStartDate
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@Composable
internal fun WeekResizeCornerHandle(
    config: ScheduleConfigEntity,
    backdrop: Backdrop?,
    cardSize: androidx.compose.ui.unit.DpSize,
    cardCorner: Dp,
    selected: Boolean,
    modifier: Modifier = Modifier
) {
    val handleLight = glassUsesLightStyle(config)
    val handleColor = if (handleLight) {
        ComposeColor.Black.copy(alpha = if (selected) 0.74f else 0.58f)
    } else {
        ComposeColor.White.copy(alpha = if (selected) 0.88f else 0.72f)
    }
    val badgeSize = (cardCorner * 1.35f + 8.dp).coerceIn(16.dp, 34.dp)
        .coerceAtMost(minOf(cardSize.width, cardSize.height) / 2f + 4.dp)
    val handleShape = remember(cardSize, cardCorner, badgeSize) {
        WeekResizeCornerShape(cardSize, cardCorner, badgeSize)
    }
    Box(
        modifier = modifier,
        contentAlignment = Alignment.BottomEnd
    ) {
        GlassSurface(
            nativeMaterial = true,
            backdrop = backdrop,
            config = config,
            modifier = Modifier.size(badgeSize),
            shape = handleShape,
            tokens = GlassTokens.pill(intensity = 0.82f).copy(
                surfaceAlpha = 0.36f,
                shadowAlpha = 0.18f,
                innerShadowAlpha = 0.14f
            ),
            selected = true,
            onClick = null
        ) {
            Box(Modifier.fillMaxSize().background(handleColor.copy(alpha = if (selected) 0.28f else 0.16f), handleShape))
        }
    }
}

@Composable
fun WeekCourseBlock(
    course: CourseEntity,
    periods: List<PeriodEntity>,
    height: Dp,
    topInGrid: Dp,
    cardColor: ComposeColor,
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    shortcutPivotX: Float,
    weekMotionDirection: Int = 0,
    weekMotionOutgoing: Boolean = false,
    dayIndex: Int = 1,
    periodIndex: Int = 1,
    gridColumnWidth: Dp = 0.dp,
    cardLayoutWidth: Dp = (gridColumnWidth - 4.dp).coerceAtLeast(1.dp),
    periodRowHeight: Dp = height,
    layerOffset: Animatable<Float, AnimationVector1D>? = null,
    layerTravel: Float = 1f,
    stackIndex: Int = 0,
    cardOrderFraction: Float? = null,
    columnOrderFraction: Float? = null,
    conflictWarning: Boolean = false,
    conflictUnderlyingCourse: CourseEntity? = null,
    conflictUnderlyingPeriodIndex: Int? = null,
    conflictUnderlyingSpan: Int = 0,
    onResolveConflict: (CourseEntity) -> Unit = {},
    editMode: Boolean = false,
    editActivation: State<Float>,
    editingAllowed: Boolean = true,
    /** A cancelled occurrence uses neutral light; clicking still edits its original course. */
    muted: Boolean = false,
    editWeek: Int = 1,
    allWeekCourses: List<CourseEntity> = emptyList(),
    weekdayCount: Int = 7,
    onEnterEditMode: () -> Unit = {},
    onUpdateSingleWeekCourse: (CourseEntity, CourseEntity) -> Unit = { _, _ -> },
    onDeleteSingleWeekCourse: (CourseEntity) -> Unit = {},
    onCourseClick: (CourseEntity, Rect?) -> Unit,
    onDragStateChanged: (dayIndex: Int?, courseId: Long?) -> Unit = { _, _ -> },
    activeOverlayCourseId: Long? = null,
    activeOverlayTargetKey: String? = null,
    activeOverlayTargetWeek: Int = 0,
    onStartWeekEditOverlay: (WeekEditOverlayRequest) -> Unit = {},
    onDragWeekEditOverlay: (Offset) -> Unit = {},
    onFinishMoveOverlay: (Velocity) -> Unit = {},
    onFinishResizeOverlay: (Velocity) -> Unit = {},
    onCancelWeekEditOverlay: () -> Unit = {},
) {
    val personalizationPreview = LocalPersonalizationPreview.current
    val locationText = course.location.orEmpty()
    val hasLocation = config.weekCardShowLocation && locationText.isNotBlank()
    val hasTeacher = config.weekCardShowTeacher && !course.teacher.isNullOrBlank()
    val cardTextAlign = config.weekCardTextAlign()
    val subdued = muted
    val resolvedCardColor = if (subdued) MutedCourseLightColor else if (courseCardUsesAssignments(config)) courseCardBaseColor(config, course) else cardColor
    val pageForeground = homeForegroundColor(config)
    val coloredText = !subdued && config.courseCardColoredTextEnabled
    val courseTextColor = homeCourseTextColor(config, course, pageForeground, subdued)
    val density = LocalDensity.current
    val tailDirection = if (weekMotionOutgoing) -weekMotionDirection else weekMotionDirection
    val startupPhase = LocalStartupPhase.current
    val editControlOrder = ((periodIndex - 1).coerceAtLeast(0) * 7 + (dayIndex - 1).coerceAtLeast(0)) * 2 + stackIndex
    // Keep mutable layout coordinates outside Snapshot state. Ordinary scrolling resolves
    // the anchor once after settling or on interaction; edit animations keep reporting it.
    // Only a real width change updates the measured-width state below.
    val ownBoundsRef = remember(course.id, dayIndex, periodIndex, editWeek) {
        arrayOfNulls<Rect>(1)
    }
    val ownCoordinatesRef = remember(course.id, dayIndex, periodIndex, editWeek) {
        arrayOfNulls<LayoutCoordinates>(1)
    }
    val cardMotionFrozen = LocalCourseTextMotionFrozen.current
    var measuredCardWidth by remember(course.id, dayIndex, periodIndex, editWeek) {
        mutableFloatStateOf(0f)
    }
    val haptic = LocalHapticFeedback.current
    val weekEditMotionState = LocalWeekEditMotionState.current
    val copyMotion = LocalCourseCopy.current
    SideEffect {
        // An existing occurrence can absorb the copied weeks without changing its layout.
        if (ownBoundsRef[0] != null) copyMotion?.reportTarget(course, editWeek)
    }
    val isOverlayTarget = activeOverlayTargetKey != null &&
        activeOverlayTargetWeek == editWeek &&
        activeOverlayTargetWeek in course.weeks &&
        course.occurrenceOverrideKey() == activeOverlayTargetKey
    val periodIndexes = remember(periods) { periods.map { it.periodIndex } }
    val timeGrid = LocalWeekTimeSlotGrid.current
    val gridDensity = LocalDensity.current
    val rowTopsPx = remember(timeGrid, gridDensity.density) { timeGrid?.rowTops.orEmpty().map { with(gridDensity) { it.dp.toPx() } } }
    val customTimeLocked = !courseAllowsWeekPeriodDrag(course)
    val currentSpan = remember(course.periods, periodIndex, periodIndexes) {
        continuousSpanFrom(course, periodIndex, periodIndexes)
            .takeIf { it > 0 }
            ?: course.periods.size.coerceAtLeast(1)
    }
    var bodyDragging by remember(course.id, editWeek) { mutableStateOf(false) }
    var handleDragging by remember(course.id, editWeek) { mutableStateOf(false) }
    var conflictActionResolving by remember(course.id, editWeek) { mutableStateOf(false) }
    var conflictFlightTarget by remember(course.id, editWeek) { mutableStateOf<CourseEntity?>(null) }
    val conflictPillDismiss = remember(course.id, editWeek) { Animatable(0f) }
    val conflictCardFlight = remember(course.id, editWeek) { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val screenHeightPx = with(density) { currentWindowSizeDp().height.toPx() }
    val screenWidthPx = with(density) { currentWindowSizeDp().width.toPx() }
    val edgeScrollThresholdPx = with(density) { 92.dp.toPx() }
    val measuredCardWidthPx = measuredCardWidth
        .takeIf { it > 1f }
        ?: with(density) { 48.dp.toPx() }
    val gridColumnWidthPx = with(density) { gridColumnWidth.toPx() }
        .takeIf { it > 1f }
        ?: measuredCardWidthPx
    val periodRowHeightPx = with(density) { periodRowHeight.toPx() }.coerceAtLeast(1f)
    /*
     * WeekCourseBlock always lives inside the stationary course recorder. Sampling the
     * lifted backdrop here would make the source card read the recorder that is currently
     * drawing the source card, creating a recursive backdrop loop on long-press. The real
     * lifted card is rendered by WeekEditOverlayHost outside that recorder.
     */
    val activeCardBackdrop = backdrop
    val pressScale by animateFloatAsState(
        targetValue = if (bodyDragging || handleDragging) WeekEditLiftedScale else 1f,
        animationSpec = spring(dampingRatio = 0.60f, stiffness = 470f),
        label = "week-edit-press-scale-${course.id}"
    )
    val displayedHeight = height
    val windowSize = currentWindowSizeDp()
    val cardWidthForCorner = gridColumnWidth
        .takeIf { it > 0.dp }
        ?: (windowSize.width / 8f)
    val cardCorner = adaptiveWeekCardCornerRadius(
        cardWidth = (cardWidthForCorner - 4.dp).coerceAtLeast(1.dp),
        cardHeight = displayedHeight,
        windowWidth = windowSize.width,
        windowHeight = windowSize.height,
        progress = personalizationPreview?.weekCardCornerProgress
            ?: config.weekCardCornerProgress
    )
    val cardShape = remember(cardCorner) { RoundedRectangle(cardCorner) }
    val resizeStartIndex = periodIndexes.indexOf(periodIndex).coerceAtLeast(0)
    val resizeMaxSpan = (periodIndexes.size - resizeStartIndex).coerceAtLeast(1)
    val baseHeightPx = with(density) { height.toPx() }
    fun refreshOwnBounds(): Rect? {
        val coordinates = ownCoordinatesRef[0]?.takeIf { it.isAttached }
            ?: return ownBoundsRef[0]
        val bounds = coordinates.boundsInRoot()
        ownBoundsRef[0] = bounds
        if (periodIndex == course.periods.minOrNull() || course.hasCustomTime()) {
            copyMotion?.reportSource(course, editWeek, bounds)
        }
        copyMotion?.reportTarget(course, editWeek)
        if (isOverlayTarget) {
            weekEditMotionState?.onRealCardPositioned(bounds.center)
        }
        return bounds
    }
    val refreshSettledBounds by rememberUpdatedState(::refreshOwnBounds)
    LaunchedEffect(cardMotionFrozen) {
        // Coordinate bookkeeping is not layout state. Keep the large card/gesture subtree
        // out of the freeze/resume invalidation set; refresh only after motion settles.
        snapshotFlow { cardMotionFrozen.value }.collectLatest { frozen ->
            if (!frozen) {
                withFrameNanos { }
                refreshSettledBounds()
            }
        }
    }
    fun buildWeekEditOverlayRequest(
        mode: WeekEditOverlayMode,
        pointerInSource: Offset
    ): WeekEditOverlayRequest? {
        if (!editingAllowed) return null
        val bounds = refreshOwnBounds() ?: return null
        return WeekEditOverlayRequest(
            mode = mode,
            course = course,
            sourceBounds = bounds,
            dayIndex = dayIndex,
            periodIndex = periodIndex,
            currentSpan = currentSpan,
            maxSpan = resizeMaxSpan,
            gridColumnWidthPx = gridColumnWidthPx,
            periodRowHeightPx = periodRowHeightPx,
            periodIndexes = periodIndexes,
            weekCourses = allWeekCourses,
            editWeek = editWeek,
            weekdayCount = weekdayCount,
            initialPointerPosition = bounds.topLeft + pointerInSource,
            rowTopsPx = rowTopsPx,
            weekdays = timeGrid?.weekdays.orEmpty()
        )
    }
    val liftedVisualActive = bodyDragging || handleDragging || conflictActionResolving
    val resizeHandleInputEnabled =
        editMode &&
            !customTimeLocked &&
            !bodyDragging &&
            !conflictActionResolving &&
            (
                activeOverlayCourseId == null ||
                    (handleDragging && activeOverlayCourseId == course.id)
                )
    LaunchedEffect(liftedVisualActive) {
        if (liftedVisualActive) onDragStateChanged(dayIndex, course.id) else onDragStateChanged(null, null)
    }
    LaunchedEffect(conflictWarning) {
        if (!conflictWarning) {
            conflictActionResolving = false
            conflictFlightTarget = null
            conflictPillDismiss.snapTo(0f)
            conflictCardFlight.snapTo(0f)
        }
    }
    val shortcuts = LocalCourseShortcuts.current
    val currentEditMode by rememberUpdatedState(editMode)
    val adjustedEditor = LocalAdjustedCourseEditor.current
    val adjustedOccurrenceDate = if (editingAllowed) null else
        scheduleWeekStartDate(config, editWeek).plusDays(com.xiaomanjun.sleepdownschedule.domain.schedule.scheduleWeekdayOffset(config, dayIndex))
    val openShortcut by rememberUpdatedState<() -> CourseShortcutRequest?> {
        if (!editingAllowed || shortcuts == null) null else refreshOwnBounds()?.let { bounds ->
            CourseShortcutRequest(course, editWeek, bounds, with(density) { cardCorner.toPx() },
                shortcutPivotX, onEnterEditMode).also(shortcuts::open)
        }
    }
    val startBodyDrag by rememberUpdatedState<(Offset, CourseShortcutRequest?) -> Boolean> { position, shortcut ->
        val request = buildWeekEditOverlayRequest(WeekEditOverlayMode.Move, position)
        if (request != null && !customTimeLocked) {
            if (shortcut != null) shortcuts?.cancel(shortcut)
            if (!currentEditMode) onEnterEditMode()
            bodyDragging = true
            onStartWeekEditOverlay(request)
            true
        } else false
    }
    val dragBody by rememberUpdatedState(onDragWeekEditOverlay)
    val finishBodyDrag by rememberUpdatedState(onFinishMoveOverlay)
    val cancelBodyDrag by rememberUpdatedState(onCancelWeekEditOverlay)
    val clickBody by rememberUpdatedState<() -> Unit> {
        if (copyMotion?.active != true) {
            val bounds = refreshOwnBounds()
            if (editingAllowed) onCourseClick(course, bounds)
            else adjustedOccurrenceDate?.let { adjustedEditor?.invoke(course.id, it, bounds) }
        }
    }
    val editorTapRegistry = com.xiaomanjun.sleepdownschedule.feature.course.editor.LocalCourseEditorFlightRegistry.current
    val editorTapOwner = remember(course.id, editWeek, periodIndex) { Any() }
    androidx.compose.runtime.DisposableEffect(editorTapRegistry, editorTapOwner) {
        editorTapRegistry?.registerTarget(editorTapOwner, editWeek, day = false,
            bounds = { ownCoordinatesRef[0]?.takeIf { it.isAttached }?.boundsInRoot() },
            open = { clickBody() })
        onDispose { editorTapRegistry?.removeTarget(editorTapOwner) }
    }
    // Editing is read through updated state, never a pointerInput key: switching into edit
    // mode must not cancel the finger that is about to move the course.
    val bodyGestureModifier = if (copyMotion?.active == true) Modifier else Modifier.pointerInput(customTimeLocked, course.id, editWeek, currentSpan) {
        awaitEachGesture {
            // Claim the card contact before the outer blank-area detector sees it. Pager
            // scrolling still observes this down and wins once it consumes movement/slop.
            val down = awaitFirstDown()
            down.consume()
            val longPress = awaitLongPressOrCancellation(down.id)
            if (longPress == null) {
                val up = currentEvent.changes.firstOrNull { it.id == down.id }
                if (up != null && up.changedToUpIgnoreConsumed() && !up.isConsumed) {
                    up.consume()
                    if (!currentEditMode || customTimeLocked) clickBody()
                }
                return@awaitEachGesture
            }
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            val shortcutGesture = !currentEditMode
            var heldShortcut: CourseShortcutRequest? = null
            // A held card gets immediate feedback. Give a moving finger a short chance
            // to claim the gesture before allocating the menu window and home blur.
            var shortcutDeadline = if (shortcutGesture) SystemClock.uptimeMillis() + 80L else 0L
            val shortcutDrift = minOf(viewConfiguration.touchSlop, 1.5.dp.toPx())
            val velocityTracker = VelocityTracker()
            velocityTracker.addPosition(longPress.uptimeMillis, longPress.position)
            var accumulated = Offset.Zero
            var dragging = !shortcutGesture && !customTimeLocked && startBodyDrag(longPress.position, null)
            var released = false
            try {
                while (true) {
                    val event = if (shortcutDeadline == 0L) {
                        awaitPointerEvent()
                    } else {
                        withTimeoutOrNull(shortcutDeadline - SystemClock.uptimeMillis()) { awaitPointerEvent() }
                    }
                    if (event == null) {
                        shortcutDeadline = 0L
                        heldShortcut = openShortcut()
                        continue
                    }
                    val change = event.changes.firstOrNull { it.id == longPress.id } ?: break
                    if (event.changes.any { it.id != longPress.id && it.pressed } || change.isConsumed) break
                    if (!change.pressed) {
                        released = change.changedToUpIgnoreConsumed()
                        change.consume()
                        break
                    }
                    val delta = change.positionChange()
                    accumulated += delta
                    if (shortcutDeadline != 0L && accumulated.getDistance() > shortcutDrift) shortcutDeadline = 0L
                    velocityTracker.addPosition(change.uptimeMillis, change.position)
                    change.consume()
                    if (!dragging && !customTimeLocked && accumulated.getDistance() > viewConfiguration.touchSlop) {
                        dragging = startBodyDrag(longPress.position, heldShortcut)
                        if (dragging) dragBody(accumulated)
                    } else if (dragging) {
                        dragBody(delta)
                    }
                }
            } finally {
                if (dragging) {
                    bodyDragging = false
                    if (weekEditMotionState?.rootOwnsMoveGesture == true) {
                        // The stable week root continues receiving this pointer after paging
                        // disposes the original card. It also owns release and cancellation.
                    } else if (released) {
                        finishBodyDrag(velocityTracker.calculateVelocity())
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    } else cancelBodyDrag()
                } else if (shortcutGesture) {
                    if (released && heldShortcut == null) openShortcut()
                    if (!released) heldShortcut?.let { shortcuts?.cancel(it) }
                }
            }
        }
    }
    val editingId = LocalEditingCourseId.current
    val sharedScope = if (startupPhase == StartupPhase.FullQuality && course.id > 0L) LocalSharedTransitionScope.current else null
    val baseModifier = Modifier
        .fillMaxWidth()
        .height(height)
    val landingRipple = weekEditMotionState?.landingRipple?.takeIf { it.week == editWeek }
    val tailTransformActive =
        layerOffset?.isRunning == true || (layerOffset?.value ?: 0f) != 0f ||
            landingRipple != null ||
            copyMotion?.active == true || copyMotion?.rippleCenter != null ||
            copyMotion?.landed == true
    val tailTransformModifier = if (tailTransformActive) Modifier.graphicsLayer {
            val tailX = layerOffset?.let { offset ->
                val progress = (kotlin.math.abs(offset.value) / layerTravel.coerceAtLeast(1f)).coerceIn(0f, 1f)
                val cardFraction = cardOrderFraction
                    ?: ((periodIndex - 1).coerceIn(0, 9) / 9f)
                val screenFraction = ownBoundsRef[0]?.center?.y
                    ?.div(screenHeightPx.coerceAtLeast(1f))
                    ?.coerceIn(0f, 1f) ?: cardFraction
                val tailGroup = ((cardFraction * 0.55f + screenFraction * 0.45f) * 5f)
                    .roundToInt().coerceIn(0, 5)
                val columnFraction = columnOrderFraction
                    ?: ((dayIndex - 1).coerceIn(0, 6) / 6f)
                val screenColumnFraction = ownBoundsRef[0]?.center?.x
                    ?.div(screenWidthPx.coerceAtLeast(1f))
                    ?.coerceIn(0f, 1f) ?: columnFraction
                val columnGroup = ((columnFraction * 0.6f + screenColumnFraction * 0.4f) * 5f)
                    .roundToInt().coerceIn(0, 5)
                val phaseDelay = tailGroup * 0.014f + columnGroup * 0.028f
                val delayedProgress = ((progress - phaseDelay) / (1f - phaseDelay)).coerceIn(0f, 1f)
                val tailBase = with(density) {
                    (30.dp + (tailGroup * 15f).dp + (columnGroup * 8f).dp + (stackIndex * 6f).dp).toPx()
                }
                tailBase * delayedProgress * tailDirection
            } ?: 0f
            val copyRippleCenter = copyMotion?.rippleCenter?.takeIf { copyMotion.target?.week == editWeek }
            val rippleRadius = if (copyRippleCenter != null) copyMotion.rippleRadius else landingRipple?.radiusPx ?: 0f
            val bounds = ownBoundsRef[0]
            val rippleScale = if (
                rippleRadius > 0f && bounds != null && !bounds.isEmpty &&
                copyMotion?.isTarget(course, editWeek) != true &&
                !isOverlayTarget &&
                activeOverlayCourseId != course.id
            ) {
                val distance = if (copyRippleCenter != null) (bounds.center - copyRippleCenter).getDistance()
                    else weekEditMotionState?.landingRippleDistance(bounds.center) ?: Float.POSITIVE_INFINITY
                if (distance < rippleRadius) {
                    weekEditNeighborRippleTransform(
                        distancePx = distance,
                        radiusPx = rippleRadius,
                        progress = if (copyRippleCenter != null) copyMotion.ripple.value
                            else weekEditMotionState?.landingRippleProgress ?: 1f
                    ).scale
                } else 1f
            } else 1f
            val landingImpact = if (copyMotion?.isTarget(course, editWeek) == true && copyMotion.landed) {
                weekEditLandingImpactTransform(copyMotion.impact.value)
            } else {
                WeekEditLandingImpactTransform(translationFactor = 0f, scaleX = 1f, scaleY = 1f)
            }
            translationX = tailX
            scaleX = rippleScale * landingImpact.scaleX
            scaleY = rippleScale * landingImpact.scaleY
        } else Modifier
    val tailModifier = tailTransformModifier.onGloballyPositioned { coordinates ->
            ownCoordinatesRef[0] = coordinates
            val layoutWidth = coordinates.size.width.toFloat()
            if (measuredCardWidth != layoutWidth) {
                measuredCardWidth = layoutWidth
            }
            // Ordinary scrolling only needs coordinates for a later tap/long-press. Keep
            // the initial anchor and edit/copy/landing animations live; glass sampling has
            // its own position node and continues to follow the wallpaper every frame.
            if (!cardMotionFrozen.value || ownBoundsRef[0] == null || editMode ||
                tailTransformActive || isOverlayTarget || activeOverlayCourseId != null ||
                shortcuts?.request != null) {
                refreshOwnBounds()
            }
        }
    val hiddenByShortcut by remember(shortcuts, course.id, editWeek, ownBoundsRef) {
        derivedStateOf {
            shortcuts?.request?.let {
                it.course.id == course.id && it.week == editWeek && it.bounds == ownBoundsRef[0]
            } == true
        }
    }
    val hiddenByOverlay = hiddenByShortcut || copyMotion?.hides(course, editWeek) == true
    val visibilityModifier = if (hiddenByOverlay) Modifier.graphicsLayer { alpha = 0f } else Modifier
    CourseBoundsSource(
        courseId = course.id,
        visible = editingId != course.id && course.id !in
            com.xiaomanjun.sleepdownschedule.core.ui.chrome.LocalEditingCourseIds.current,
        sharedScope = sharedScope,
        modifier = baseModifier,
        shape = cardShape
    ) { sharedModifier ->
        Box(
            modifier = sharedModifier
                .then(tailModifier)
                .courseRemovalMotion(course, editWeek, courseCardBaseColor(config, course))
                .then(bodyGestureModifier)
                .semantics {
                    if (copyMotion?.active != true) {
                        onClick(if (editingAllowed) "查看课程" else "查看调休课程") { clickBody(); true }
                        if (editingAllowed) onLongClick("课程快捷操作") { openShortcut() != null }
                    }
                }
                .then(visibilityModifier)
                .homeCountdownShockwave()
                .zIndex(if (liftedVisualActive) 3f else 0f)
        ) {
            conflictUnderlyingCourse
                ?.takeIf { conflictActionResolving && conflictUnderlyingSpan > 0 }
                ?.let { underlyingCourse ->
                    val sourcePosition = periodIndexes.indexOf(periodIndex).coerceAtLeast(0)
                    val underlyingPosition = conflictUnderlyingPeriodIndex
                        ?.let(periodIndexes::indexOf)
                        ?.takeIf { it >= 0 }
                        ?: sourcePosition
                    val underlyingHeight = (
                        (timeGrid?.let { it.bottom((underlyingPosition + conflictUnderlyingSpan).toFloat()) - it.top(underlyingPosition.toFloat()) }?.dp
                            ?: (periodRowHeight * conflictUnderlyingSpan.toFloat())) - 4.dp
                        ).coerceAtLeast(18.dp)
                    val ownerReveal =
                        (conflictCardFlight.value / 0.14f).coerceIn(0f, 1f)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .offset(
                                y = timeGrid?.let { it.top(underlyingPosition.toFloat()) - it.top(sourcePosition.toFloat()) }?.dp
                                    ?: (periodRowHeight * (underlyingPosition - sourcePosition).toFloat())
                            )
                            .height(underlyingHeight)
                            .graphicsLayer { alpha = ownerReveal }
                    ) {
                        CourseGlassCard(
                            backdrop = activeCardBackdrop,
                            config = config,
                            course = underlyingCourse,
                            followTouch = false,
                            muted = subdued,
                            modifier = Modifier.fillMaxSize(),
                            shape = cardShape,
                            onClick = null
                        ) {
                            WeekCourseOverlayCardContent(underlyingCourse, config)
                        }
                    }
                }
            conflictFlightTarget?.takeIf { conflictActionResolving }?.let { target ->
                val flightProgress = conflictCardFlight.value.coerceIn(0f, 1f)
                val sourcePosition = periodIndexes.indexOf(periodIndex).coerceAtLeast(0)
                val orderedSourcePeriods = course.periods
                    .distinct()
                    .sortedBy { periodIndexes.indexOf(it).takeIf { index -> index >= 0 } ?: Int.MAX_VALUE }
                val orderedTargetPeriods = target.periods
                    .distinct()
                    .sortedBy { periodIndexes.indexOf(it).takeIf { index -> index >= 0 } ?: Int.MAX_VALUE }
                val segmentPeriodOrdinal = orderedSourcePeriods.indexOf(periodIndex).coerceAtLeast(0)
                val targetPeriod = orderedTargetPeriods
                    .getOrNull(segmentPeriodOrdinal)
                    ?: orderedTargetPeriods.firstOrNull()
                    ?: periodIndex
                val targetPosition = periodIndexes.indexOf(targetPeriod)
                    .takeIf { it >= 0 }
                    ?: sourcePosition
                val visibleDays = timeGrid?.weekdays.orEmpty()
                val targetOffsetX = (if (visibleDays.isEmpty()) target.weekday - dayIndex else
                    visibleDays.indexOf(target.weekday) - visibleDays.indexOf(dayIndex)) * gridColumnWidthPx
                val targetOffsetY = (rowTopsPx.getOrNull(targetPosition) ?: targetPosition * periodRowHeightPx) -
                    (rowTopsPx.getOrNull(sourcePosition) ?: sourcePosition * periodRowHeightPx)
                val flightArcPx = with(density) { 10.dp.toPx() }
                val flightElevationPx = with(density) { 14.dp.toPx() }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(height)
                        .graphicsLayer {
                            translationX = targetOffsetX * flightProgress
                            translationY = targetOffsetY * flightProgress -
                                sin(Math.PI * flightProgress).toFloat() * flightArcPx
                            val flightScale = 0.985f + 0.015f * flightProgress
                            scaleX = flightScale
                            scaleY = flightScale
                            alpha = (flightProgress / 0.12f).coerceIn(0f, 1f)
                            shadowElevation =
                                sin(Math.PI * flightProgress).toFloat().coerceAtLeast(0f) *
                                    flightElevationPx
                            shape = cardShape
                        }
                ) {
                    CourseGlassCard(
                        backdrop = activeCardBackdrop,
                        config = config,
                        course = target,
                        followTouch = false,
                        modifier = Modifier.fillMaxSize(),
                        shape = cardShape,
                        onClick = null
                    ) {
                        WeekCourseOverlayCardContent(target, config)
                    }
                }
            }
            val showConflictBadge = conflictWarning && !editMode && !customTimeLocked
            // The page owns the entry spring. Cards observe its value only in their layer.
            val cardTransformActive by remember(bodyDragging, handleDragging, conflictActionResolving,
                isOverlayTarget, activeOverlayCourseId, course.id, editActivation, editingAllowed) {
                derivedStateOf {
                    bodyDragging || handleDragging || pressScale != 1f ||
                        (editingAllowed && editActivation.value != 0f) ||
                        conflictActionResolving || isOverlayTarget ||
                        activeOverlayCourseId == course.id
                }
            }
            val cardTransformModifier = if (cardTransformActive) Modifier.graphicsLayer {
                val activation = if (editingAllowed) editActivation.value.coerceIn(0f, 1f) else 0f
                val activeScale = if (bodyDragging) {
                    WeekEditLiftedScale
                } else {
                    pressScale * (1f + activation * 0.006f)
                }
                transformOrigin = if (handleDragging) {
                    TransformOrigin(0.5f, 0f)
                } else {
                    TransformOrigin.Center
                }
                if (!bodyDragging && !handleDragging && activation > 0f && activation < 1f) {
                    // Let the edit cue settle before the stationary grid is sampled during drag.
                    val envelope = 4f * activation * (1f - activation)
                    rotationZ = 0.35f * envelope * sin(4.0 * Math.PI * activation).toFloat()
                }
                scaleX = activeScale
                scaleY = activeScale
                val departureAlpha = if (conflictActionResolving) {
                    1f - (conflictCardFlight.value / 0.12f).coerceIn(0f, 1f)
                } else {
                    1f
                }
                alpha = when {
                    isOverlayTarget -> if (weekEditMotionState?.realCardVisible == true) 1f else 0f
                    // The overlay starts at the source bounds and owns the entire lift.
                    // Switching owners once also keeps the sampled grid stable under it.
                    activeOverlayCourseId == course.id -> 0f
                    else -> departureAlpha
                }
            } else Modifier
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(displayedHeight)
                    .then(cardTransformModifier)
            ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(displayedHeight)
            ) {
            CourseGlassCard(
                backdrop = activeCardBackdrop,
                config = config,
                course = course,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(displayedHeight),
                shape = cardShape,
                // Pager owns lifetime; scrolling never toggles a card's material nodes.
                viewportMaterialVisible = !hiddenByOverlay &&
                    ((activeOverlayCourseId != course.id && !isOverlayTarget) ||
                        weekEditMotionState?.realCardVisible == true),
                muted = subdued,
                followTouch = !editMode,
                onClick = null
            ) {
            // The day column already knows the measured width. Subcomposing every card again
            // made a single prefetched page spend 17–24ms in measureAndLayout on the 120Hz phone.
            val badgeInset = when {
                showConflictBadge -> courseBadgeContentInset(
                    maxOf(20.dp, with(density) { 10.sp.toDp() } + 4.dp), cardCorner
                )
                !editingAllowed -> courseBadgeContentInset(with(density) { courseAdjustmentBadgeHeight() }, cardCorner)
                else -> 0.dp
            }
            Box(Modifier.fillMaxWidth().height(displayedHeight).clip(cardShape)
                .padding(bottom = badgeInset)) {
            WeekCourseCardTextContent(course, config, cardLayoutWidth,
                (displayedHeight - badgeInset).coerceAtLeast(0.dp), gridColumnWidth, subdued)
            }
            }
            }
            if (!editingAllowed && !showConflictBadge) {
                CourseAdjustmentBadge(if (muted) "停" else "补", activeCardBackdrop, config,
                    Modifier.align(Alignment.BottomEnd).courseBadgeCornerAnchor(cardCorner).zIndex(7f))
            }
            if (showConflictBadge) {
                val pillDismissProgress = conflictPillDismiss.value.coerceIn(0f, 1f)
                val pillTextColor =
                    if (glassUsesLightStyle(config)) ComposeColor.Black else ComposeColor.White
                GlassSurface(
                    nativeMaterial = true,
                    backdrop = activeCardBackdrop,
                    config = config,
                    // The parent card owns pager/tail motion, as for adjustment badges.
                    placementLayer = false,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .courseBadgeCornerAnchor(cardCorner)
                        .width((if (editingAllowed) 34.dp else 48.dp).coerceAtMost(cardLayoutWidth))
                        .heightIn(min = 20.dp)
                        .zIndex(8f)
                        .graphicsLayer {
                            val dismissScale = 1f - 0.30f * pillDismissProgress
                            scaleX = dismissScale
                            scaleY = dismissScale
                            alpha = 1f - pillDismissProgress
                        },
                    shape = Capsule(),
                    tokens = GlassTokens.pill(intensity = 0.86f).copy(
                        surfaceAlpha = 0.32f,
                        shadowAlpha = 0.18f,
                        innerShadowAlpha = 0.12f
                    ),
                    selected = true,
                    onClick = {
                        if (!conflictActionResolving) {
                            val moved = nearestAvailableCourseMove(
                                course = course,
                                week = editWeek,
                                courses = allWeekCourses,
                                periodIndexes = periodIndexes,
                                weekdayCount = weekdayCount
                            )
                            if (moved != null) {
                                conflictFlightTarget = moved
                                conflictActionResolving = true
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                scope.launch {
                                    conflictPillDismiss.snapTo(0f)
                                    conflictCardFlight.snapTo(0f)
                                    conflictPillDismiss.animateTo(
                                        1f,
                                        tween(
                                            durationMillis = 165,
                                            easing = CubicBezierEasing(0.32f, 0f, 0.68f, 1f)
                                        )
                                    )
                                    delay(24)
                                    conflictCardFlight.animateTo(
                                        1f,
                                        tween(
                                            durationMillis = 420,
                                            easing = CubicBezierEasing(0.16f, 0.82f, 0.18f, 1f)
                                        )
                                    )
                                    onResolveConflict(moved)
                                }
                            } else {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        }
                    }
                ) {
                    Text(
                        text = if (editingAllowed) "冲突" else if (muted) "停·冲突" else "补·冲突",
                        modifier = Modifier.align(Alignment.Center).padding(horizontal = 2.dp, vertical = 2.dp),
                        fontSize = 8.sp,
                        lineHeight = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = pillTextColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            val floatingCardOwnsVisual = (isOverlayTarget || activeOverlayCourseId == course.id) &&
                weekEditMotionState?.realCardVisible != true
            val showEditControls = editMode && !floatingCardOwnsVisual
            AnimatedVisibility(
                visible = showEditControls,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    // The first ordinary course may begin right at the header when there is
                    // no special slot before it. Keep the badge and its entrance inside the grid.
                    .offset(x = (-5).dp, y = maxOf((-5).dp, 2.dp - topInGrid))
                    .size(22.dp)
                    .zIndex(7f),
                enter = fadeIn(tween(125, delayMillis = (editControlOrder % 7) * 9)) +
                    scaleIn(
                        animationSpec = spring(dampingRatio = 0.54f, stiffness = 520f),
                        initialScale = 0.28f,
                        transformOrigin = TransformOrigin(1f, 1f)
                    ),
                exit = fadeOut(tween(90)) +
                    scaleOut(tween(120), targetScale = 0.55f, transformOrigin = TransformOrigin(1f, 1f))
            ) {
                GlassSurface(
                    nativeMaterial = true,
                    backdrop = activeCardBackdrop,
                    config = config,
                    modifier = Modifier.fillMaxSize(),
                    shape = Capsule(),
                    tokens = GlassTokens.pill(intensity = 0.82f).copy(
                        surfaceAlpha = 0.36f,
                        shadowAlpha = 0.18f,
                        innerShadowAlpha = 0.14f
                    ),
                    selected = true,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onDeleteSingleWeekCourse(course)
                    }
                ) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .clip(Capsule())
                            .background(ComposeColor(0xFFFF1F2D).copy(alpha = 0.48f))
                    )
                    Box(
                        Modifier
                            .align(Alignment.Center)
                            .width(10.dp)
                            .height(2.dp)
                            .clip(Capsule())
                            .background(ComposeColor.White)
                    )
                }
            }
            val resizeHandleBounds = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 4.dp, y = 4.dp)
                .size(44.dp)
                .zIndex(6f)
            // Keep every resize control in the week grid's own layer. The edit-mode gutter above
            // already provides the four drawing pixels used by this handle; promoting only the
            // final-row handle to a window Popup made it render above the app's bottom dock.
            AnimatedVisibility(
                visible = showEditControls && !customTimeLocked,
                modifier = resizeHandleBounds,
                enter = fadeIn(tween(135, delayMillis = 45 + (editControlOrder % 7) * 8)) +
                    scaleIn(
                        animationSpec = spring(dampingRatio = 0.52f, stiffness = 470f),
                        initialScale = 0.32f,
                        transformOrigin = TransformOrigin(0f, 0f)
                    ),
                exit = fadeOut(tween(90)) +
                    scaleOut(tween(125), targetScale = 0.55f, transformOrigin = TransformOrigin(0f, 0f))
            ) {
                WeekResizeCornerHandle(
                    config = config,
                    backdrop = activeCardBackdrop,
                    cardSize = androidx.compose.ui.unit.DpSize(cardLayoutWidth, displayedHeight),
                    cardCorner = cardCorner,
                    selected = false,
                    modifier = Modifier.fillMaxSize()
                )
            }
            // The floating card takes over drawing the handle. Keep the hit target in the grid
            // until the same finger releases, even after the original handle has faded out.
            if (resizeHandleInputEnabled) {
                Box(resizeHandleBounds.pointerInput(course.id, editWeek, currentSpan) {
                    val velocityTracker = VelocityTracker()
                    detectDragGestures(
                        onDragStart = {
                            velocityTracker.resetTracking()
                            buildWeekEditOverlayRequest(
                                WeekEditOverlayMode.Resize,
                                Offset(measuredCardWidthPx, baseHeightPx)
                            )?.let { request ->
                                handleDragging = true
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onStartWeekEditOverlay(request)
                            }
                        },
                        onDrag = { change, dragAmount ->
                            if (handleDragging) {
                                change.consume()
                                velocityTracker.addPosition(change.uptimeMillis, change.position)
                                onDragWeekEditOverlay(dragAmount)
                            }
                        },
                        onDragEnd = {
                            if (handleDragging) {
                                onFinishResizeOverlay(velocityTracker.calculateVelocity())
                                handleDragging = false
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        },
                        onDragCancel = {
                            if (handleDragging) {
                                handleDragging = false
                                onCancelWeekEditOverlay()
                            }
                        }
                    )
                })
            }
            }
        }
    }
}
