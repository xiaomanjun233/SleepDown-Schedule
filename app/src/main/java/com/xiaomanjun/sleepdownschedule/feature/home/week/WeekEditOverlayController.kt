package com.xiaomanjun.sleepdownschedule.feature.home.week

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
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
import com.xiaomanjun.sleepdownschedule.feature.home.day.coursesVisibleInWeek
import com.xiaomanjun.sleepdownschedule.feature.home.day.occurrenceOverrideKey
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
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
    val destinationWeek: Int = editWeek
)

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

internal data class WeekEditLandingImpactTransform(
    val translationFactor: Float,
    val scaleX: Float,
    val scaleY: Float
)

internal data class WeekEditRealCardLandingTransform(
    val liftFactor: Float,
    val scale: Float
)

/**
 * Maps the destination card from the lifted finger pose to its real timetable geometry.
 * Progress intentionally accepts a small spring overshoot so the physical card can pass the
 * surface by a fraction before settling, without ever changing its logical grid position.
 */
internal fun weekEditRealCardLandingTransform(progress: Float): WeekEditRealCardLandingTransform {
    val springProgress = progress.coerceIn(-0.20f, 1.22f)
    val remaining = 1f - springProgress
    return WeekEditRealCardLandingTransform(
        liftFactor = remaining,
        scale = 1f + (WeekEditLiftedScale - 1f) * remaining
    )
}

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
    private val overlayReveal = Animatable(0f)
    private val overlayLift = Animatable(0f)
    private val realCardLandingAnimation = Animatable(1f)
    private val landingImpactAnimation = Animatable(1f)
    private val overlayRotation = Animatable(0f)
    private val landingRippleAnimation = Animatable(1f)
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
    private var pendingLandingCenter = Offset.Zero
    private var pendingLandingRadius = 1f
    private var landingRippleStarted = false
    var gestureActive by mutableStateOf(false)
        private set
    var realCardVisible by mutableStateOf(false)
        private set
    var realCardLandingActive by mutableStateOf(false)
        private set
    var landingRippleCenter by mutableStateOf<Offset?>(null)
        private set
    var landingRippleRadius by mutableFloatStateOf(1f)
        private set

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
            directManipulation && request?.mode == WeekEditOverlayMode.Resize -> gestureTargetHeight
            else -> overlayHeight.value
        }
    val scale: Float get() = overlayScale.value
    val alpha: Float get() = overlayAlpha.value
    val revealProgress: Float get() = overlayReveal.value
    val liftProgress: Float get() = overlayLift.value
    val realCardLandingProgress: Float get() = realCardLandingAnimation.value
    val impactProgress: Float get() = landingImpactAnimation.value
    val rotation: Float get() = overlayRotation.value
    val pointerPosition: Offset get() = Offset(dragPointerX, dragPointerY)
    val landingRippleProgress: Float get() = landingRippleAnimation.value
    val gridOffsetY: Float get() = offsetY + scrollCompensationY
    val gridScrollCompensationY: Float get() = scrollCompensationY

    fun clear(preserveLandingRipple: Boolean = false) {
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
        realCardLandingActive = false
        pendingLandingCenter = Offset.Zero
        pendingLandingRadius = 1f
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
        landingRippleStarted = false
        if (!preserveLandingRipple) {
            landingRippleCenter = null
            landingRippleRadius = 1f
        }
    }

    fun start(nextRequest: WeekEditOverlayRequest) {
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
        realCardLandingActive = false
        pendingLandingCenter = Offset.Zero
        pendingLandingRadius = 1f
        landingRippleStarted = false
        landingRippleCenter = null
        landingRippleRadius = 1f
        gestureTargetX = 0f
        gestureTargetY = 0f
        gestureTargetHeight = nextRequest.sourceBounds.height
        releaseFrameX = 0f
        releaseFrameY = 0f
        releaseFrameHeight = nextRequest.sourceBounds.height
        releaseFrameActive = false
        dragPointerX = nextRequest.initialPointerPosition.x
        dragPointerY = nextRequest.initialPointerPosition.y
        scope.launch {
            overlayX.snapTo(0f)
            overlayY.snapTo(0f)
            overlayHeight.snapTo(nextRequest.sourceBounds.height)
            overlayScale.snapTo(0.985f)
            overlayAlpha.snapTo(1f)
            overlayReveal.snapTo(0f)
            overlayLift.snapTo(0f)
            realCardLandingAnimation.snapTo(1f)
            landingImpactAnimation.snapTo(1f)
            overlayRotation.snapTo(0f)
            landingRippleAnimation.snapTo(1f)
            scrollCompensationY = 0f
            autoScrollDirection = 0
            launch {
                overlayReveal.animateTo(
                    1f,
                    tween(durationMillis = 90, easing = CubicBezierEasing(0.20f, 0.78f, 0.18f, 1f))
                )
            }
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
                gestureTargetHeight = weekEditResizeHeightAfterDrag(
                    currentHeightPx = previousHeight,
                    dragDeltaYPx = delta.y,
                    periodRowHeightPx = activeRequest.periodRowHeightPx,
                    maxSpan = activeRequest.maxSpan,
                    resizePaddingPx = resizePaddingPx
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
            weekday = activeRequest.dayIndex + (releaseGridX / activeRequest.gridColumnWidthPx).roundToInt(),
            startPeriod = activeRequest.periodIndex + (releaseGridY / activeRequest.periodRowHeightPx).roundToInt(),
            span = activeRequest.currentSpan,
            weekdayCount = activeRequest.weekdayCount
        )
        val edited = activeRequest.course.copy(weekday = target.weekday, periods = target.periods,
            weeks = listOf(activeRequest.destinationWeek), weekParity = com.xiaomanjun.sleepdownschedule.WeekParity.ALL)
        val crossingWeek = activeRequest.destinationWeek != activeRequest.editWeek
        val canSave = target.valid && canMoveToWeekday(target.weekday) &&
            !hasWeekCourseEditConflict(if (crossingWeek) activeRequest.course.copy(id = -1) else activeRequest.course,
                edited, activeRequest.weekCourses, activeRequest.destinationWeek) &&
            (crossingWeek || edited.weekday != activeRequest.course.weekday || edited.periods != activeRequest.course.periods)
        val targetX = if (canSave) (target.weekday - activeRequest.dayIndex) * activeRequest.gridColumnWidthPx else 0f
        val targetY = if (canSave) {
            ((target.periods.firstOrNull() ?: activeRequest.periodIndex) - activeRequest.periodIndex) *
                activeRequest.periodRowHeightPx - scrollCompensationY
        } else {
            0f
        }
        pendingLandingCenter = activeRequest.sourceBounds.center + Offset(targetX, targetY)
        pendingLandingRadius = maxOf(
            activeRequest.gridColumnWidthPx * 3.8f,
            activeRequest.periodRowHeightPx * 3.0f
        )
        if (canSave) {
            committedTargetKey = edited.occurrenceOverrideKey()
            committedTargetWeek = activeRequest.destinationWeek
            awaitingCommit = true
            beginLandingFlight(
                targetX = targetX,
                targetY = targetY,
                targetHeight = activeRequest.sourceBounds.height,
                releaseVelocity = velocity
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
        val targetSpan = weekEditResizeTargetSpan(
            heightPx = gestureTargetHeight,
            periodRowHeightPx = activeRequest.periodRowHeightPx,
            maxSpan = activeRequest.maxSpan
        )
        val target = weekCourseEditTarget(
            periodIndexes = activeRequest.periodIndexes,
            weekday = activeRequest.dayIndex,
            startPeriod = activeRequest.periodIndex,
            span = targetSpan,
            weekdayCount = activeRequest.weekdayCount
        )
        val edited = activeRequest.course.copy(periods = target.periods)
        val canSave = target.valid &&
            !hasWeekCourseEditConflict(activeRequest.course, edited, activeRequest.weekCourses, activeRequest.editWeek) &&
            edited.periods != activeRequest.course.periods
        val targetHeight = if (canSave) {
            activeRequest.periodRowHeightPx * targetSpan - resizePaddingPx
        } else {
            activeRequest.sourceBounds.height
        }
        pendingLandingCenter = Offset(
            x = activeRequest.sourceBounds.center.x,
            y = activeRequest.sourceBounds.top + targetHeight / 2f
        )
        pendingLandingRadius = maxOf(
            activeRequest.gridColumnWidthPx * 3.8f,
            activeRequest.periodRowHeightPx * 3.0f
        )
        if (canSave) {
            committedTargetKey = edited.copy(weeks = listOf(activeRequest.editWeek)).occurrenceOverrideKey()
            committedTargetWeek = activeRequest.editWeek
            awaitingCommit = true
            beginLandingFlight(
                targetX = 0f,
                targetY = 0f,
                targetHeight = targetHeight,
                releaseVelocity = Velocity(0f, velocity.y * WeekEditResizeDragGain)
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

    fun updateRealLandingCenter(center: Offset) {
        if (committedTargetKey != null) {
            pendingLandingCenter = center
            realCardTargetMeasured = true
            maybeHandoffOverlayToRealCard()
        }
    }

    private fun beginLandingFlight(
        targetX: Float,
        targetY: Float,
        targetHeight: Float,
        releaseVelocity: Velocity
    ) {
        if (landingFlightRunning || landingFlightComplete) return
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
            releaseFrameActive = false
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
                // Keep the impact on the floating card and start it in the release frame. The
                // lower stiffness preserves one readable compression/rebound instead of finishing
                // before the clone reaches its snapped cell.
                overlayScale.animateTo(
                    1f,
                    spring(dampingRatio = 0.46f, stiffness = 300f)
                )
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
                ) {
                    // First contact with the grid plane is the impact frame. Waiting for all
                    // position/height springs to settle delays the wave through their rebound.
                    if (value <= 0f && !landingRippleStarted) {
                        landingRippleStarted = true
                        startLandingRipple(pendingLandingCenter, pendingLandingRadius)
                    }
                }
            }
            xJob.join()
            yJob.join()
            heightJob.join()
            rotationJob.join()
            liftJob.join()
            scaleJob.join()
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
        handoffRunning = true
        scope.launch {
            awaitingCommit = false
            // The overlay has already completed the full visible flight and rebound. Room only
            // transfers ownership in place and must never gate or restart the landing motion.
            realCardLandingAnimation.snapTo(1f)
            landingImpactAnimation.snapTo(1f)
            realCardLandingActive = false
            realCardVisible = true
            overlayAlpha.animateTo(
                0f,
                tween(
                    durationMillis = 135,
                    easing = CubicBezierEasing(0.20f, 0.72f, 0.22f, 1f)
                )
            )
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

    private fun startLandingRipple(center: Offset, radius: Float) {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            landingRippleAnimation.snapTo(0f)
            landingRippleCenter = center
            landingRippleRadius = radius.coerceAtLeast(1f)
            landingRippleAnimation.animateTo(
                1f,
                tween(durationMillis = 900, easing = LinearEasing)
            )
            landingRippleCenter = null
            landingRippleRadius = 1f
        }
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
