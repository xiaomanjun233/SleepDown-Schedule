package com.xiaomanjun.sleepdownschedule.feature.home.week

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.Velocity
import com.xiaomanjun.sleepdownschedule.CourseEntity
import com.xiaomanjun.sleepdownschedule.WeekParity
import com.xiaomanjun.sleepdownschedule.domain.course.buildWeekConflictGroups
import com.xiaomanjun.sleepdownschedule.domain.schedule.courseWeekDisplayFragments
import com.xiaomanjun.sleepdownschedule.domain.schedule.parityMatches
import com.xiaomanjun.sleepdownschedule.exactTimeWeekPlacement
import com.xiaomanjun.sleepdownschedule.feature.home.day.coursesVisibleInWeek
import com.xiaomanjun.sleepdownschedule.feature.home.day.occurrenceOverrideKey
import com.xiaomanjun.sleepdownschedule.feature.home.overlay.ScheduleOverlapSlot
import com.xiaomanjun.sleepdownschedule.feature.home.overlay.layoutScheduleOverlapSlots
import com.xiaomanjun.sleepdownschedule.feature.home.overlay.overlapEntryKey
import com.xiaomanjun.sleepdownschedule.hasCustomTime
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

internal fun continuousSpanFrom(course: CourseEntity, start: Int, periodIndexes: List<Int>): Int {
    var span = 0
    var expected = start
    for (periodIndex in periodIndexes.dropWhile { it != start }) {
        if (periodIndex != expected || periodIndex !in course.periods) break
        span += 1
        expected += 1
    }
    return span
}

internal data class WeekCourseEditTarget(
    val weekday: Int,
    val periods: List<Int>,
    val valid: Boolean
)

enum class WeekEditOverlayMode {
    Move,
    Resize
}

data class WeekEditOverlayRequest(
    val mode: WeekEditOverlayMode,
    val course: CourseEntity,
    val sourceBounds: Rect,
    val dayIndex: Int,
    val periodIndex: Int,
    val currentSpan: Int,
    val maxSpan: Int,
    val gridColumnWidthPx: Float,
    val periodRowHeightPx: Float,
    val periodIndexes: List<Int>,
    val weekCourses: List<CourseEntity>,
    val editWeek: Int,
    val weekdayCount: Int,
    val initialPointerPosition: Offset,
    val destinationWeek: Int = editWeek,
    val rowTopsPx: List<Float> = emptyList(),
    val weekdays: List<Int> = emptyList()
)

internal fun WeekEditOverlayRequest.rowTopFor(period: Int): Float {
    val index = periodIndexes.indexOf(period).coerceAtLeast(0)
    return rowTopsPx.getOrNull(index) ?: index * periodRowHeightPx
}

internal fun WeekEditOverlayRequest.columnFor(day: Int): Int =
    if (weekdays.isEmpty()) day - 1 else weekdays.indexOf(day).coerceAtLeast(0)

internal fun WeekEditOverlayRequest.weekdayAfterMove(deltaX: Float): Int {
    val days = weekdays.ifEmpty { (1..weekdayCount).toList() }
    return days[(columnFor(dayIndex) + (deltaX / gridColumnWidthPx).roundToInt()).coerceIn(days.indices)]
}

internal fun WeekEditOverlayRequest.columnDeltaTo(day: Int): Int = columnFor(day) - columnFor(dayIndex)

internal fun WeekEditOverlayRequest.startPeriodAfterMove(deltaY: Float): Int =
    periodIndexes.minByOrNull { kotlin.math.abs(rowTopFor(it) - rowTopFor(periodIndex) - deltaY) } ?: periodIndex

internal fun WeekEditOverlayRequest.heightForSpan(span: Int, startPeriod: Int = periodIndex, gap: Float = 0f): Float {
    val first = periodIndexes.indexOf(startPeriod).coerceAtLeast(0)
    val last = (first + span.coerceAtLeast(1) - 1).coerceAtMost(periodIndexes.lastIndex)
    if (last < first) return periodRowHeightPx
    return (rowTopFor(periodIndexes[last]) + periodRowHeightPx - rowTopFor(startPeriod) - gap).coerceAtLeast(1f)
}

internal fun WeekEditOverlayRequest.spanForHeight(height: Float): Int =
    (1..maxSpan.coerceAtLeast(1)).minByOrNull { kotlin.math.abs(heightForSpan(it) - height) } ?: 1

internal const val WeekEditLiftedScale = 1.12f
private const val WeekEditProjectionSeconds = 0.015f
// The bottom handle moves with the resized card. A conservative gain prevents tiny local pointer
// changes from turning into several timetable rows while retaining continuous visual feedback.
private const val WeekEditResizeDragGain = 0.52f

internal fun weekEditResizeHeightAfterDrag(
    currentHeightPx: Float,
    dragDeltaYPx: Float,
    periodRowHeightPx: Float,
    maxSpan: Int,
    resizePaddingPx: Float
): Float {
    val safeRowHeight = periodRowHeightPx.coerceAtLeast(1f)
    val safeMaxSpan = maxSpan.coerceAtLeast(1)
    val minHeight = (safeRowHeight - resizePaddingPx).coerceAtLeast(1f)
    val maxHeight = (safeRowHeight * safeMaxSpan - resizePaddingPx).coerceAtLeast(minHeight)
    return (currentHeightPx + dragDeltaYPx * WeekEditResizeDragGain)
        .coerceIn(minHeight, maxHeight)
}

internal fun weekEditResizeTargetSpan(
    heightPx: Float,
    periodRowHeightPx: Float,
    maxSpan: Int
): Int = (heightPx / periodRowHeightPx.coerceAtLeast(1f))
    .roundToInt()
    .coerceIn(1, maxSpan.coerceAtLeast(1))

internal data class WeekEditNeighborRippleTransform(
    val translationFactor: Float,
    val scale: Float,
    val rotationFactor: Float
)

internal data class WeekEditLandingRipple(val centerInScrollContent: Offset, val radiusPx: Float, val week: Int)

internal data class WeekEditLandingImpactTransform(
    val translationFactor: Float,
    val scaleX: Float,
    val scaleY: Float
)

internal fun weekEditLandingImpactTransform(progress: Float): WeekEditLandingImpactTransform {
    val safeProgress = progress.coerceIn(0f, 1f)
    fun smooth(value: Float): Float {
        val clamped = value.coerceIn(0f, 1f)
        return clamped * clamped * (3f - 2f * clamped)
    }
    val zScale = when {
        safeProgress <= 0.18f -> 1f - 0.095f * smooth(safeProgress / 0.18f)
        safeProgress <= 0.46f -> 0.905f + 0.155f * smooth((safeProgress - 0.18f) / 0.28f)
        safeProgress <= 0.72f -> 1.060f - 0.080f * smooth((safeProgress - 0.46f) / 0.26f)
        else -> 0.980f + 0.020f * smooth((safeProgress - 0.72f) / 0.28f)
    }
    return WeekEditLandingImpactTransform(
        translationFactor = 0f,
        scaleX = zScale,
        scaleY = zScale
    )
}

internal fun weekEditProjectedOffset(
    position: Float,
    velocity: Float,
    maximumProjection: Float
): Float = position + (velocity * WeekEditProjectionSeconds)
    .coerceIn(-maximumProjection.coerceAtLeast(0f), maximumProjection.coerceAtLeast(0f))

internal fun weekEditNeighborRippleTransform(
    distancePx: Float,
    radiusPx: Float,
    progress: Float,
    horizontalDirection: Float = 0f
): WeekEditNeighborRippleTransform {
    val safeRadius = radiusPx.coerceAtLeast(1f)
    val distanceRatio = (distancePx / safeRadius).coerceIn(0f, 1f)
    // Adjacent cards respond at contact; only the outer rings wait for wave propagation.
    val delayedStart = ((distanceRatio - 0.30f) / 0.70f).coerceAtLeast(0f) * 0.28f
    val arrival = (progress.coerceIn(0f, 1f) - delayedStart) / (1f - delayedStart)
    if (distanceRatio >= 1f || arrival <= 0f || arrival >= 1f) {
        return WeekEditNeighborRippleTransform(0f, 1f, 0f)
    }
    val safeArrival = arrival.coerceIn(0f, 1f)
    // Both the envelope and wave start/end at zero, so their product settles smoothly without
    // the squared envelope's long, almost invisible onset after the card has already landed.
    val smoothEnvelope = sin(Math.PI.toFloat() * safeArrival)
    val distanceAttenuation = (1f - distanceRatio).pow(0.72f)
    val damping = exp(-1.15f * safeArrival)
    val attenuation = distanceAttenuation * smoothEnvelope * damping
    // Two and a half deliberately slow Z cycles. The final half-cycle and zero-slope envelope
    // converge on the resting scale without a velocity discontinuity.
    val zWave = -sin(safeArrival * Math.PI.toFloat() * 5f) * attenuation
    return WeekEditNeighborRippleTransform(
        translationFactor = 0f,
        scale = 1f + zWave * 0.105f,
        rotationFactor = horizontalDirection.coerceIn(-1f, 1f) * zWave * 0f
    )
}

internal fun weekEditCommitTargetPresent(
    courses: List<CourseEntity>,
    targetKey: String?,
    targetWeek: Int
): Boolean {
    if (targetKey == null || targetWeek <= 0) return false
    return coursesVisibleInWeek(courses, targetWeek).any {
        it.occurrenceOverrideKey() == targetKey
    }
}

internal val LocalWeekEditMotionState = compositionLocalOf<WeekEditOverlayController?> { null }

@Composable
internal fun rememberWeekEditOverlayController(
    scrollState: ScrollState,
    scheduleId: Int
): WeekEditOverlayController {
    val scope = rememberCoroutineScope()
    return remember(scope, scrollState, scheduleId) {
        WeekEditOverlayController(scope, scrollState)
    }
}

@androidx.compose.runtime.Stable
internal class WeekEditOverlayController(
    private val scope: CoroutineScope,
    private val scrollState: ScrollState
) {
    var request by mutableStateOf<WeekEditOverlayRequest?>(null)
        private set
    var rootOwnsMoveGesture by mutableStateOf(false)

    fun moveToWeek(week: Int, courses: List<CourseEntity>) {
        val current = request ?: return
        if (gestureActive && current.mode == WeekEditOverlayMode.Move) {
            request = current.copy(destinationWeek = week, weekCourses = courses)
        }
    }
    var awaitingCommit by mutableStateOf(false)
        private set
    var committedTargetKey by mutableStateOf<String?>(null)
        private set
    var committedTargetWeek by mutableIntStateOf(0)
        private set

    private val overlayX = Animatable(0f)
    private val overlayY = Animatable(0f)
    private val overlayHeight = Animatable(0f)
    private val overlayScale = Animatable(1f)
    private val overlayAlpha = Animatable(1f)
    private val overlayLift = Animatable(0f)
    private val overlayRotation = Animatable(0f)
    private var scrollCompensationY by mutableFloatStateOf(0f)
    private var autoScrollDirection by mutableIntStateOf(0)
    private var dragPointerX by mutableFloatStateOf(0f)
    private var dragPointerY by mutableFloatStateOf(0f)
    private var gestureTargetX by mutableFloatStateOf(0f)
    private var gestureTargetY by mutableFloatStateOf(0f)
    private var gestureTargetHeight by mutableFloatStateOf(0f)
    private var releaseFrameX by mutableFloatStateOf(0f)
    private var releaseFrameY by mutableFloatStateOf(0f)
    private var releaseFrameHeight by mutableFloatStateOf(0f)
    private var releaseFrameActive by mutableStateOf(false)
    private var directManipulation by mutableStateOf(false)
    private var handoffRunning = false
    private var landingFlightRunning = false
    private var landingFlightComplete = false
    private var commitTargetReady = false
    private var realCardTargetMeasured = false
    private var realCardCenterInScrollContent = Offset.Zero
    private val landingRippleAnimation = Animatable(1f)
    private var landingRippleJob: Job? = null
    var landingRipple by mutableStateOf<WeekEditLandingRipple?>(null)
        private set
    val landingRippleProgress: Float get() = landingRippleAnimation.value
    var gestureActive by mutableStateOf(false)
        private set
    var realCardVisible by mutableStateOf(false)
        private set

    private val tuckUnderProgressAnim = Animatable(0f)
    var tuckUnderHostCourse by mutableStateOf<CourseEntity?>(null)
        private set
    var tuckUnderHostBounds by mutableStateOf<Rect?>(null)
        private set
    var tuckUnderHostWeek by mutableIntStateOf(0)
        private set
    var tuckUnderOverlapCount by mutableIntStateOf(0)
        private set
    private val visibleCourseBounds = mutableMapOf<Pair<Int, Long>, Rect>()

    val tuckUnderActive: Boolean
        get() = tuckUnderHostCourse != null && tuckUnderHostBounds != null
    val tuckUnderProgress: Float
        get() = tuckUnderProgressAnim.value

    fun liveTuckUnderHostBounds(): Rect? =
        tuckUnderHostBounds?.translate(0f, -scrollState.value.toFloat())

    fun reportCourseBounds(week: Int, courseId: Long, bounds: Rect?) {
        if (bounds == null || bounds.isEmpty) return
        visibleCourseBounds[week to courseId] = bounds.translate(0f, scrollState.value.toFloat())
    }

    fun hidesTuckedHost(courseId: Long, week: Int): Boolean =
        request != null &&
            !realCardVisible &&
            tuckUnderActive &&
            tuckUnderHostWeek == week &&
            tuckUnderHostCourse?.id == courseId

    val offsetX: Float get() =
        when {
            releaseFrameActive -> releaseFrameX
            directManipulation && request?.mode == WeekEditOverlayMode.Move -> gestureTargetX
            else -> overlayX.value
        }
    val offsetY: Float get() =
        when {
            releaseFrameActive -> releaseFrameY
            directManipulation && request?.mode == WeekEditOverlayMode.Move -> gestureTargetY
            else -> overlayY.value
        }
    val height: Float get() =
        when {
            releaseFrameActive -> releaseFrameHeight
            directManipulation -> gestureTargetHeight
            else -> overlayHeight.value
        }
    val scale: Float get() = overlayScale.value
    val alpha: Float get() = overlayAlpha.value
    val liftProgress: Float get() = overlayLift.value
    val rotation: Float get() = overlayRotation.value
    val pointerPosition: Offset get() = Offset(dragPointerX, dragPointerY)
    val gridOffsetY: Float get() = offsetY + scrollCompensationY
    val gridScrollCompensationY: Float get() = scrollCompensationY

    fun clear() {
        stopLandingRipple()
        awaitingCommit = false
        committedTargetKey = null
        committedTargetWeek = 0
        request = null
        gestureActive = false
        directManipulation = false
        handoffRunning = false
        landingFlightRunning = false
        landingFlightComplete = false
        commitTargetReady = false
        realCardTargetMeasured = false
        realCardVisible = false
        scrollCompensationY = 0f
        autoScrollDirection = 0
        dragPointerX = 0f
        dragPointerY = 0f
        gestureTargetX = 0f
        gestureTargetY = 0f
        gestureTargetHeight = 0f
        releaseFrameX = 0f
        releaseFrameY = 0f
        releaseFrameHeight = 0f
        releaseFrameActive = false
        tuckUnderHostCourse = null
        tuckUnderHostBounds = null
        tuckUnderHostWeek = 0
        tuckUnderOverlapCount = 0
        visibleCourseBounds.clear()
        scope.launch { tuckUnderProgressAnim.snapTo(0f) }
    }

    fun start(nextRequest: WeekEditOverlayRequest) {
        stopLandingRipple()
        rootOwnsMoveGesture = false
        awaitingCommit = false
        committedTargetKey = null
        committedTargetWeek = 0
        request = nextRequest
        gestureActive = true
        directManipulation = true
        handoffRunning = false
        landingFlightRunning = false
        landingFlightComplete = false
        commitTargetReady = false
        realCardTargetMeasured = false
        realCardVisible = false
        gestureTargetX = 0f
        gestureTargetY = 0f
        gestureTargetHeight = nextRequest.sourceBounds.height
        releaseFrameX = 0f
        releaseFrameY = 0f
        releaseFrameHeight = nextRequest.sourceBounds.height
        releaseFrameActive = false
        dragPointerX = nextRequest.initialPointerPosition.x
        dragPointerY = nextRequest.initialPointerPosition.y
        tuckUnderHostCourse = null
        tuckUnderHostBounds = null
        tuckUnderHostWeek = 0
        tuckUnderOverlapCount = 0
        scope.launch {
            tuckUnderProgressAnim.snapTo(0f)
            overlayX.snapTo(0f)
            overlayY.snapTo(0f)
            overlayHeight.snapTo(nextRequest.sourceBounds.height)
            overlayScale.snapTo(0.985f)
            overlayAlpha.snapTo(1f)
            overlayLift.snapTo(0f)
            overlayRotation.snapTo(0f)
            scrollCompensationY = 0f
            autoScrollDirection = 0
            launch {
                overlayScale.animateTo(
                    WeekEditLiftedScale,
                    spring(dampingRatio = 0.66f, stiffness = 980f)
                )
            }
            launch {
                overlayLift.animateTo(
                    1f,
                    spring(dampingRatio = 0.68f, stiffness = 720f)
                )
            }
        }
    }

    fun drag(
        delta: Offset,
        screenHeightPx: Float,
        edgePx: Float,
        resizePaddingPx: Float
    ) {
        val activeRequest = request ?: return
        when (activeRequest.mode) {
            WeekEditOverlayMode.Move -> {
                // Keep the logical target on the pointer event itself. Queuing these additions in
                // animation coroutines made fast drags accumulate a visible one-to-two-frame lag.
                gestureTargetX += delta.x
                gestureTargetY += delta.y
                dragPointerX += delta.x
                dragPointerY += delta.y
                scope.launch {
                    launch {
                        overlayRotation.animateTo(
                            (delta.x * 0.018f).coerceIn(-0.28f, 0.28f),
                            spring(dampingRatio = 0.96f, stiffness = 1_450f)
                        )
                    }
                    autoScroll(activeRequest, screenHeightPx, edgePx)
                }
            }
            WeekEditOverlayMode.Resize -> {
                val previousHeight = gestureTargetHeight
                gestureTargetHeight = (previousHeight + delta.y * WeekEditResizeDragGain).coerceIn(
                    activeRequest.heightForSpan(1, gap = resizePaddingPx),
                    activeRequest.heightForSpan(activeRequest.maxSpan, gap = resizePaddingPx)
                )
                dragPointerX += delta.x
                dragPointerY += gestureTargetHeight - previousHeight
            }
        }
    }

    fun cancelGesture() {
        val activeRequest = request ?: return
        gestureActive = false
        scope.launch {
            overlayX.snapTo(gestureTargetX)
            overlayY.snapTo(gestureTargetY)
            overlayHeight.snapTo(gestureTargetHeight)
            directManipulation = false
            val xJob = launch {
                overlayX.animateTo(0f, spring(dampingRatio = 0.66f, stiffness = 440f))
            }
            val yJob = launch {
                overlayY.animateTo(0f, spring(dampingRatio = 0.66f, stiffness = 440f))
            }
            val heightJob = launch {
                overlayHeight.animateTo(
                    activeRequest.sourceBounds.height,
                    spring(dampingRatio = 0.66f, stiffness = 440f)
                )
            }
            val scaleJob = launch {
                overlayScale.animateTo(1f, spring(dampingRatio = 0.60f, stiffness = 450f))
            }
            val liftJob = launch {
                overlayLift.animateTo(0f, spring(dampingRatio = 0.64f, stiffness = 470f))
            }
            val rotationJob = launch {
                overlayRotation.animateTo(0f, spring(dampingRatio = 0.58f, stiffness = 450f))
            }
            xJob.join()
            yJob.join()
            heightJob.join()
            scaleJob.join()
            liftJob.join()
            rotationJob.join()
            clear()
        }
    }

    fun finishMove(
        velocity: Velocity,
        canMoveToWeekday: (Int) -> Boolean = { true },
        onUpdateCourseSingleWeek: (CourseEntity, CourseEntity, Int) -> Unit
    ): Boolean {
        val activeRequest = request ?: return false
        gestureActive = false
        // The preview and persistence target must be calculated from the exact same finger frame.
        // A release-velocity projection made the saved cell differ from the one shown under the
        // card, especially near half-cell boundaries.
        val releaseGridX = gestureTargetX
        val releaseGridY = gestureTargetY + scrollCompensationY
        val target = weekCourseEditTarget(
            periodIndexes = activeRequest.periodIndexes,
            weekday = activeRequest.weekdayAfterMove(releaseGridX),
            startPeriod = activeRequest.startPeriodAfterMove(releaseGridY),
            span = activeRequest.currentSpan,
            weekdayCount = activeRequest.weekdayCount
        )
        val edited = activeRequest.course.copy(weekday = target.weekday, periods = target.periods,
            weeks = listOf(activeRequest.destinationWeek), weekParity = com.xiaomanjun.sleepdownschedule.WeekParity.ALL)
        val crossingWeek = activeRequest.destinationWeek != activeRequest.editWeek
        val canSave = canCommitWeekCourseEditMove(
            target = target,
            canMoveToWeekday = canMoveToWeekday(target.weekday),
            crossingWeek = crossingWeek,
            original = activeRequest.course,
            edited = edited
        )
        val targetX = if (canSave) activeRequest.columnDeltaTo(target.weekday) * activeRequest.gridColumnWidthPx else 0f
        val targetY = if (canSave) {
            activeRequest.rowTopFor(target.periods.firstOrNull() ?: activeRequest.periodIndex) -
                activeRequest.rowTopFor(activeRequest.periodIndex) - scrollCompensationY
        } else {
            0f
        }
        if (canSave) {
            val overlapOutcome = predictWeekEditOverlapOutcome(
                original = activeRequest.course,
                edited = edited,
                weekCourses = activeRequest.weekCourses,
                periodIndexes = activeRequest.periodIndexes,
                week = activeRequest.destinationWeek
            )
            committedTargetKey = edited.occurrenceOverrideKey()
            committedTargetWeek = activeRequest.destinationWeek
            awaitingCommit = true
            beginLandingFlight(
                targetX = targetX,
                targetY = targetY,
                targetHeight = activeRequest.heightForSpan(activeRequest.currentSpan, target.periods.first(), activeRequest.heightForSpan(activeRequest.currentSpan) - activeRequest.sourceBounds.height).coerceAtLeast(1f),
                releaseVelocity = velocity,
                overlappedHost = if (overlapOutcome.movedCardEndsBehindFront) overlapOutcome.frontHostCourse else null,
                hostWeek = activeRequest.destinationWeek,
                overlapCount = overlapOutcome.overlapCount
            )
            onUpdateCourseSingleWeek(activeRequest.course, edited, activeRequest.editWeek)
        } else {
            // The original occurrence is already a real card, so it can take over immediately.
            committedTargetKey = activeRequest.course.occurrenceOverrideKey()
            committedTargetWeek = activeRequest.editWeek
            awaitingCommit = false
            commitTargetReady = true
            beginLandingFlight(
                targetX = targetX,
                targetY = targetY,
                targetHeight = activeRequest.sourceBounds.height,
                releaseVelocity = velocity
            )
        }
        return canSave
    }

    fun finishResize(
        velocity: Velocity,
        resizePaddingPx: Float,
        onUpdateCourseSingleWeek: (CourseEntity, CourseEntity, Int) -> Unit
    ) {
        val activeRequest = request ?: return
        gestureActive = false
        val targetSpan = activeRequest.spanForHeight(gestureTargetHeight)
        val target = weekCourseEditTarget(
            periodIndexes = activeRequest.periodIndexes,
            weekday = activeRequest.dayIndex,
            startPeriod = activeRequest.periodIndex,
            span = targetSpan,
            weekdayCount = activeRequest.weekdayCount
        )
        val edited = activeRequest.course.copy(periods = target.periods)
        val canSave = canCommitWeekCourseEditResize(
            target = target,
            original = activeRequest.course,
            edited = edited
        )
        val targetHeight = if (canSave) {
            activeRequest.heightForSpan(targetSpan, gap = resizePaddingPx)
        } else {
            activeRequest.sourceBounds.height
        }
        if (canSave) {
            val overlapOutcome = predictWeekEditOverlapOutcome(
                original = activeRequest.course,
                edited = edited,
                weekCourses = activeRequest.weekCourses,
                periodIndexes = activeRequest.periodIndexes,
                week = activeRequest.editWeek
            )
            committedTargetKey = edited.copy(weeks = listOf(activeRequest.editWeek)).occurrenceOverrideKey()
            committedTargetWeek = activeRequest.editWeek
            awaitingCommit = true
            beginLandingFlight(
                targetX = 0f,
                targetY = 0f,
                targetHeight = targetHeight,
                releaseVelocity = Velocity(0f, velocity.y * WeekEditResizeDragGain),
                overlappedHost = if (overlapOutcome.movedCardEndsBehindFront) overlapOutcome.frontHostCourse else null,
                hostWeek = activeRequest.editWeek,
                overlapCount = overlapOutcome.overlapCount
            )
            onUpdateCourseSingleWeek(activeRequest.course, edited, activeRequest.editWeek)
        } else {
            committedTargetKey = activeRequest.course.occurrenceOverrideKey()
            committedTargetWeek = activeRequest.editWeek
            awaitingCommit = false
            commitTargetReady = true
            beginLandingFlight(
                targetX = 0f,
                targetY = 0f,
                targetHeight = targetHeight,
                releaseVelocity = Velocity(0f, velocity.y * WeekEditResizeDragGain)
            )
        }
    }

    fun completeCommitHandoff() {
        if (!awaitingCommit) return
        awaitingCommit = false
        commitTargetReady = true
        maybeHandoffOverlayToRealCard()
    }

    fun onRealCardPositioned(centerInRoot: Offset) {
        if (committedTargetKey == null || realCardVisible) return
        realCardCenterInScrollContent = centerInRoot + Offset(0f, scrollState.value.toFloat())
        if (realCardTargetMeasured) return
        realCardTargetMeasured = true
        maybeHandoffOverlayToRealCard()
    }

    private fun beginLandingFlight(
        targetX: Float,
        targetY: Float,
        targetHeight: Float,
        releaseVelocity: Velocity,
        overlappedHost: CourseEntity? = null,
        hostWeek: Int = 0,
        overlapCount: Int = 0
    ) {
        if (landingFlightRunning || landingFlightComplete) return
        val landedRequest = request ?: return
        val projectedCenter = Offset(
            landedRequest.sourceBounds.center.x + targetX,
            landedRequest.sourceBounds.top + targetY + targetHeight / 2f + scrollState.value
        )
        val landingWeek = committedTargetWeek
        val resolvedHostBounds = overlappedHost?.let { visibleCourseBounds[hostWeek to it.id] }
        if (overlappedHost != null && resolvedHostBounds != null) {
            tuckUnderHostCourse = overlappedHost
            tuckUnderHostBounds = resolvedHostBounds
            tuckUnderHostWeek = hostWeek
            tuckUnderOverlapCount = overlapCount.coerceAtLeast(2)
        } else {
            tuckUnderHostCourse = null
            tuckUnderHostBounds = null
            tuckUnderHostWeek = 0
            tuckUnderOverlapCount = 0
        }
        val tuckMode = tuckUnderHostCourse != null && tuckUnderHostBounds != null
        landingFlightRunning = true
        // Publish the exact release pose synchronously. Animatable.snapTo is suspendable, so
        // waiting to leave direct-manipulation mode inside the coroutine could leave one rendered
        // frame parked at its previous value before the landing spring became observable.
        releaseFrameX = gestureTargetX
        releaseFrameY = gestureTargetY
        releaseFrameHeight = gestureTargetHeight
        releaseFrameActive = true
        directManipulation = false
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            overlayX.snapTo(gestureTargetX)
            overlayY.snapTo(gestureTargetY)
            overlayHeight.snapTo(gestureTargetHeight)
            if (tuckMode) tuckUnderProgressAnim.snapTo(0f)
            releaseFrameActive = false
            var rippleStarted = false
            fun playContactRipple() {
                if (rippleStarted || request !== landedRequest) return
                rippleStarted = true
                startLandingRipple(WeekEditLandingRipple(
                    centerInScrollContent = if (realCardTargetMeasured) realCardCenterInScrollContent else projectedCenter,
                    radiusPx = maxOf(landedRequest.gridColumnWidthPx * 3.8f, landedRequest.periodRowHeightPx * 3f),
                    week = landingWeek
                ))
            }
            if (tuckMode) {
                // Phase 1: Dropped card lands squarely ON TOP of the front card first (zIndex = 3f).
                val alignSpec = tween<Float>(durationMillis = 130, easing = CubicBezierEasing(0.20f, 0.80f, 0.24f, 1.0f))
                coroutineScope {
                    launch { overlayX.animateTo(targetX, alignSpec) }
                    launch { overlayY.animateTo(targetY, alignSpec) }
                    launch { overlayHeight.animateTo(targetHeight, alignSpec) }
                    launch { overlayRotation.animateTo(0f, alignSpec) }
                    launch { overlayScale.animateTo(1f, alignSpec) }
                    launch { overlayLift.animateTo(0f, alignSpec) }
                }
                // Phase 2: Move noticeably downward while shrinking and fading to the bottom limit, then
                // fly back behind the front card into its underneath slot, followed by an elastic rebound on the top card.
                tuckUnderProgressAnim.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 600, easing = LinearEasing)
                ) {
                    if (!rippleStarted && value >= 0.74f) playContactRipple()
                }
                playContactRipple()
            } else {
                // Reuse the original conflict-return flight: the same damped spring handles a legal
                // destination and a rejected drop (whose target is the source). This keeps the release
                // continuous with the finger instead of pinning the clone through a new tween.
                val landingPositionSpec = spring<Float>(dampingRatio = 0.66f, stiffness = 390f)
                val landingHeightSpec = spring<Float>(dampingRatio = 0.66f, stiffness = 360f)
                val xJob = launch {
                    overlayX.animateTo(
                        targetX,
                        landingPositionSpec,
                        initialVelocity = releaseVelocity.x.coerceIn(-2_400f, 2_400f)
                    )
                }
                val yJob = launch {
                    overlayY.animateTo(
                        targetY,
                        landingPositionSpec,
                        initialVelocity = releaseVelocity.y.coerceIn(-2_400f, 2_400f)
                    )
                }
                val heightJob = launch {
                    overlayHeight.animateTo(
                        targetHeight,
                        landingHeightSpec,
                        initialVelocity = releaseVelocity.y.coerceIn(-1_800f, 1_800f)
                    )
                }
                val scaleJob = launch {
                    var reachedLiftPeak = false
                    overlayScale.animateTo(
                        1f,
                        keyframes {
                            // Land on the same spring as the card position, with a distinct
                            // compression and rebound entirely on the moving foreground card.
                            durationMillis = 350
                            1.03f at 115 using FastOutSlowInEasing
                            0.973f at 183 using FastOutSlowInEasing
                            1.014f at 265 using FastOutSlowInEasing
                        }
                    ) {
                        // Signal the first compression of the visible landing, even while the
                        // repository and the real card are still preparing the handoff.
                        if (value > 1.01f) reachedLiftPeak = true
                        if (reachedLiftPeak && value < 0.995f) playContactRipple()
                    }
                    playContactRipple()
                }
                val rotationJob = launch {
                    overlayRotation.animateTo(
                        0f,
                        spring(dampingRatio = 0.68f, stiffness = 390f)
                    )
                }
                // The clone must lose its lifted Z offset during the same landing flight. Room can
                // legitimately take longer than the spring; keeping liftProgress at 1f until the
                // repository-backed card appears makes the clone visibly hover at its destination.
                // Returning it to the grid plane here keeps the visual landing continuous while the
                // existing real-card handoff still waits for the persisted occurrence.
                val liftJob = launch {
                    overlayLift.animateTo(
                        0f,
                        spring(dampingRatio = 0.66f, stiffness = 360f)
                    )
                }
                xJob.join()
                yJob.join()
                heightJob.join()
                rotationJob.join()
                liftJob.join()
                scaleJob.join()
            }
            landingFlightRunning = false
            landingFlightComplete = true
            maybeHandoffOverlayToRealCard()
        }
    }

    private fun maybeHandoffOverlayToRealCard() {
        if (commitTargetReady && landingFlightComplete && realCardTargetMeasured) {
            handoffOverlayToRealCard()
        }
    }

    private fun handoffOverlayToRealCard() {
        if (handoffRunning || committedTargetKey == null) return
        val landedRequest = request ?: return
        handoffRunning = true
        if (tuckUnderActive) {
            // Atomic synchronous handoff when tucking behind a host card prevents any frame gap
            // on the front host card or duplicate overlap badge flicker.
            awaitingCommit = false
            realCardVisible = true
            request = null
            tuckUnderHostCourse = null
            tuckUnderHostBounds = null
            tuckUnderHostWeek = 0
            tuckUnderOverlapCount = 0
            gestureActive = false
            directManipulation = false
            releaseFrameActive = false
            scrollCompensationY = 0f
            autoScrollDirection = 0
            handoffRunning = false
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                overlayAlpha.snapTo(1f)
                tuckUnderProgressAnim.snapTo(0f)
            }
            return
        }
        scope.launch {
            awaitingCommit = false
            // The overlay has already completed the full visible flight and rebound. Room only
            // transfers ownership in place and must never gate or restart the landing motion.
            realCardVisible = true
            overlayAlpha.animateTo(
                0f,
                tween(
                    durationMillis = 135,
                    easing = CubicBezierEasing(0.20f, 0.72f, 0.22f, 1f)
                )
            )
            if (request !== landedRequest) return@launch
            request = null
            gestureActive = false
            directManipulation = false
            releaseFrameActive = false
            scrollCompensationY = 0f
            autoScrollDirection = 0
            handoffRunning = false
            overlayAlpha.snapTo(1f)
        }
    }

    fun landingRippleDistance(centerInRoot: Offset): Float {
        val ripple = landingRipple ?: return Float.POSITIVE_INFINITY
        return (centerInRoot + Offset(0f, scrollState.value.toFloat()) - ripple.centerInScrollContent).getDistance()
    }

    private fun startLandingRipple(ripple: WeekEditLandingRipple) {
        landingRippleJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            landingRippleAnimation.snapTo(0f)
            landingRipple = ripple
            landingRippleAnimation.animateTo(1f, tween(900, easing = LinearEasing))
            landingRipple = null
        }
    }

    private fun stopLandingRipple() {
        landingRippleJob?.cancel()
        landingRippleJob = null
        landingRipple = null
    }

    private suspend fun autoScroll(
        activeRequest: WeekEditOverlayRequest,
        screenHeightPx: Float,
        edgePx: Float
    ) {
        if (overlayHeight.value >= screenHeightPx - edgePx * 1.25f) {
            autoScrollDirection = 0
            return
        }
        val deadZone = edgePx * 0.22f
        val bottomLimit = screenHeightPx - edgePx
        val topLimit = edgePx
        val wantedDirection = when {
            dragPointerY > bottomLimit + deadZone -> 1
            dragPointerY < topLimit - deadZone -> -1
            dragPointerY < bottomLimit - deadZone && dragPointerY > topLimit + deadZone -> 0
            else -> autoScrollDirection
        }
        autoScrollDirection = wantedDirection
        val delta = when {
            wantedDirection > 0 -> {
                val pressure = ((dragPointerY - bottomLimit) / edgePx).coerceIn(0f, 1f)
                1.2f + 6.5f * pressure
            }
            wantedDirection < 0 -> {
                val pressure = ((topLimit - dragPointerY) / edgePx).coerceIn(0f, 1f)
                -(1.2f + 6.5f * pressure)
            }
            else -> 0f
        }
        if (delta == 0f) return
        val before = scrollState.value
        val next = (before + delta).roundToInt().coerceIn(0, scrollState.maxValue)
        if (next != before) {
            scrollState.scrollTo(next)
            scrollCompensationY += (scrollState.value - before).toFloat()
        }
    }
}

internal fun weekCourseEditTarget(
    periodIndexes: List<Int>,
    weekday: Int,
    startPeriod: Int,
    span: Int,
    weekdayCount: Int
): WeekCourseEditTarget {
    val safeWeekday = weekday.coerceIn(1, weekdayCount.coerceAtLeast(1))
    if (periodIndexes.isEmpty() || span <= 0) {
        return WeekCourseEditTarget(safeWeekday, emptyList(), false)
    }
    val requestedPosition = periodIndexes.indexOf(startPeriod).let { exactPosition ->
        when {
            exactPosition >= 0 -> exactPosition
            // A fast release can put the overlay a few pixels below the timetable. Treat that
            // overshoot as the last legal start row instead of rejecting an otherwise valid drop
            // on the bottom cell. Keep an above-top release invalid so accidental edge escapes
            // still spring back to the source card.
            startPeriod > periodIndexes.last() -> periodIndexes.lastIndex
            else -> -1
        }
    }
    if (requestedPosition < 0) {
        return WeekCourseEditTarget(safeWeekday, emptyList(), false)
    }
    val maxStartPosition = (periodIndexes.size - span).coerceAtLeast(0)
    val startPosition = requestedPosition.coerceIn(0, maxStartPosition)
    val targetPeriods = periodIndexes.drop(startPosition).take(span)
    return WeekCourseEditTarget(
        weekday = safeWeekday,
        periods = targetPeriods,
        valid = targetPeriods.size == span
    )
}

internal fun canCommitWeekCourseEditMove(
    target: WeekCourseEditTarget,
    canMoveToWeekday: Boolean,
    crossingWeek: Boolean,
    original: CourseEntity,
    edited: CourseEntity
): Boolean =
    target.valid &&
        canMoveToWeekday &&
        (crossingWeek || edited.weekday != original.weekday || edited.periods != original.periods)

internal fun canCommitWeekCourseEditResize(
    target: WeekCourseEditTarget,
    original: CourseEntity,
    edited: CourseEntity
): Boolean =
    target.valid && edited.periods != original.periods

internal data class WeekEditTuckTransform(
    val dipOffsetY: Float,
    val scale: Float,
    val alpha: Float,
    val behindFront: Boolean,
    val shadowFraction: Float
)

internal data class WeekEditFrontAbsorbTransform(
    val scale: Float,
    val liftOffsetY: Float
)

internal data class WeekEditOverlapOutcome(
    val movedCardEndsBehindFront: Boolean,
    val frontHostCourse: CourseEntity?,
    val overlapCount: Int
)

internal fun predictWeekEditOverlapOutcome(
    original: CourseEntity,
    edited: CourseEntity,
    weekCourses: List<CourseEntity>,
    periodIndexes: List<Int>,
    week: Int
): WeekEditOverlapOutcome {
    if (periodIndexes.isEmpty()) {
        return WeekEditOverlapOutcome(movedCardEndsBehindFront = false, frontHostCourse = null, overlapCount = 1)
    }
    val remainingDayCourses = weekCourses.filter { other ->
        other.id != original.id &&
            week in other.weeks &&
            parityMatches(other.weekParity, week) &&
            other.weekday == edited.weekday
    }
    val simulatedMovedId = (weekCourses.maxOfOrNull { it.id } ?: 0L) + 1L
    val simulatedMoved = edited.copy(
        id = simulatedMovedId,
        weeks = listOf(week),
        weekParity = WeekParity.ALL
    )
    val postDropCourses = remainingDayCourses + simulatedMoved
    val courseById = postDropCourses.associateBy { it.id }
    val conflictGroups = buildWeekConflictGroups(postDropCourses, periodIndexes)
    data class SlotCandidate(val key: String, val courseId: Long, val slot: ScheduleOverlapSlot)
    val candidates = mutableListOf<SlotCandidate>()
    conflictGroups.forEach { group ->
        val courseCount = group.courses.size
        group.courses.forEachIndexed { courseIndex, course ->
            val segments = if (course.hasCustomTime() && course.arrangementProjection == null) {
                group.segments.filter { it.course.id == course.id }.minByOrNull { it.startPosition }?.let(::listOf).orEmpty()
            } else {
                group.segments.filter { it.course.id == course.id }
            }
            segments.forEach { segment ->
                val startSlot = segment.startPosition.toDouble()
                val endSlot = (segment.endPosition + 1).toDouble()
                val key = "${overlapEntryKey(course, edited.weekday, startSlot, endSlot)}_${courseCount - courseIndex}"
                candidates += SlotCandidate(
                    key = key,
                    courseId = course.id,
                    slot = ScheduleOverlapSlot(
                        id = key,
                        weekday = edited.weekday,
                        start = startSlot,
                        end = endSlot,
                        stableOrder = courseCount - courseIndex
                    )
                )
            }
        }
    }
    if (candidates.isEmpty()) {
        return WeekEditOverlapOutcome(movedCardEndsBehindFront = false, frontHostCourse = null, overlapCount = 1)
    }
    val candidateByKey = candidates.associateBy { it.key }
    val placements = layoutScheduleOverlapSlots(candidates.map { it.slot })
    val placementById = placements.associateBy { it.slot.id }
    val movedPlacements = placements.filter { placement ->
        candidateByKey[placement.slot.id]?.courseId == simulatedMovedId
    }
    val editedPlacement = movedPlacements.firstOrNull { it.overlapCount > 1 }
        ?: return WeekEditOverlapOutcome(movedCardEndsBehindFront = false, frontHostCourse = null, overlapCount = 1)
    val topSlot = editedPlacement.overlapGroup.lastOrNull()
        ?: return WeekEditOverlapOutcome(movedCardEndsBehindFront = false, frontHostCourse = null, overlapCount = editedPlacement.overlapCount)
    if (topSlot.id == editedPlacement.slot.id) {
        return WeekEditOverlapOutcome(
            movedCardEndsBehindFront = false,
            frontHostCourse = null,
            overlapCount = editedPlacement.overlapCount
        )
    }
    val topCandidate = candidateByKey[topSlot.id]
    val frontHostCourse = topCandidate?.let { courseById[it.courseId] }
    val topCount = placementById[topSlot.id]?.overlapCount ?: editedPlacement.overlapCount
    return WeekEditOverlapOutcome(
        movedCardEndsBehindFront = frontHostCourse != null,
        frontHostCourse = frontHostCourse,
        overlapCount = topCount
    )
}

internal fun weekEditTuckUnderTransform(progress: Float, dipMaxPx: Float): WeekEditTuckTransform {
    val p = progress.coerceIn(0f, 1f)
    val dipOffsetY: Float
    val scale: Float
    val alpha: Float
    if (p <= 0.45f) {
        val t = (p / 0.45f).coerceIn(0f, 1f)
        val dipEase = CubicBezierEasing(0.18f, 0.0f, 0.22f, 1.0f).transform(t)
        dipOffsetY = dipMaxPx * dipEase
        scale = 1.0f + (0.78f - 1.0f) * dipEase
        alpha = 1.0f + (0.30f - 1.0f) * dipEase
    } else if (p <= 0.78f) {
        val t = ((p - 0.45f) / 0.33f).coerceIn(0f, 1f)
        val returnEase = CubicBezierEasing(0.18f, 0.86f, 0.24f, 1.0f).transform(t)
        dipOffsetY = dipMaxPx * (1f - returnEase)
        scale = 0.78f + (1.0f - 0.78f) * returnEase
        alpha = 0.30f + (1.0f - 0.30f) * returnEase
    } else {
        dipOffsetY = 0f
        scale = 1f
        alpha = 1f
    }
    return WeekEditTuckTransform(
        dipOffsetY = dipOffsetY,
        scale = scale,
        alpha = alpha,
        behindFront = p > 0.45f,
        shadowFraction = (1f - (p / 0.45f)).coerceIn(0f, 1f)
    )
}

internal fun weekEditFrontAbsorbTransform(progress: Float): WeekEditFrontAbsorbTransform {
    val p = progress.coerceIn(0f, 1f)
    fun smoothStep(a: Float, b: Float): Float {
        val u = ((p - a) / (b - a)).coerceIn(0f, 1f)
        return u * u * (3f - 2f * u)
    }
    return when {
        p < 0.58f -> WeekEditFrontAbsorbTransform(scale = 1f, liftOffsetY = 0f)
        p < 0.74f -> {
            val s = smoothStep(0.58f, 0.74f)
            WeekEditFrontAbsorbTransform(
                scale = 1.0f + (0.95f - 1.0f) * s,
                liftOffsetY = 0.5f * s
            )
        }
        p < 0.86f -> {
            val s = smoothStep(0.74f, 0.86f)
            WeekEditFrontAbsorbTransform(
                scale = 0.95f + (1.06f - 0.95f) * s,
                liftOffsetY = 0.5f + (-0.75f - 0.5f) * s
            )
        }
        p < 0.94f -> {
            val s = smoothStep(0.86f, 0.94f)
            WeekEditFrontAbsorbTransform(
                scale = 1.06f + (0.992f - 1.06f) * s,
                liftOffsetY = -0.75f * (1f - s)
            )
        }
        else -> {
            val s = smoothStep(0.94f, 1.0f)
            WeekEditFrontAbsorbTransform(
                scale = 0.992f + (1.0f - 0.992f) * s,
                liftOffsetY = 0f
            )
        }
    }
}

internal fun hasWeekCourseEditConflict(
    original: CourseEntity,
    edited: CourseEntity,
    weekCourses: List<CourseEntity>,
    week: Int
): Boolean {
    val editedPeriods = edited.periods.toSet()
    return weekCourses.any { other ->
        other.id != original.id &&
            week in other.weeks &&
            other.weekday == edited.weekday &&
            other.periods.any { it in editedPeriods }
    }
}
