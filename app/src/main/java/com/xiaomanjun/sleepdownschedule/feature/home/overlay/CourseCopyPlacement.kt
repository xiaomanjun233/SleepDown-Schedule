package com.xiaomanjun.sleepdownschedule.feature.home.overlay

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.xiaomanjun.sleepdownschedule.model.CourseEntity
import com.xiaomanjun.sleepdownschedule.model.PeriodEntity
import com.xiaomanjun.sleepdownschedule.domain.schedule.*
import com.xiaomanjun.sleepdownschedule.feature.course.editor.CourseEditorWeekGrid
import com.xiaomanjun.sleepdownschedule.feature.home.day.occurrenceOverrideKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalTime

internal val LocalCourseCopy = staticCompositionLocalOf<CourseCopyController?> { null }

internal data class CourseCopyTarget(
    val course: CourseEntity,
    val week: Int,
    val periodIndex: Int,
    val bounds: List<Rect>,
    val sourceBounds: Rect
)

internal enum class CourseCopyPhase { Idle, Selecting, Saving, Flying, AwaitingCard }
internal enum class CourseCopyTap { Selected, Confirmed, Rejected }

/** Move the selected pattern; exact-time copies preserve duration instead of converting to periods. */
internal fun courseCopyAtSlot(
    draft: CourseEntity,
    weekday: Int,
    periodIndex: Int,
    definitions: List<PeriodEntity>
): CourseEntity? {
    if (weekday !in 1..7) return null
    val periods = definitions.sortedBy { it.periodIndex }
    val destination = periods.indexOfFirst { it.periodIndex == periodIndex }
    if (destination < 0) return null
    val exact = draft.customTimeRangeOrNull()
    if (exact != null) {
        val start = runCatching { LocalTime.parse(periods[destination].startTime) }.getOrNull() ?: return null
        val seconds = Duration.between(exact.first, exact.second).seconds
        val lastEnd = periods.lastOrNull()?.endTime?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
            ?: return null
        if (seconds <= 0 || start.toSecondOfDay() + seconds > lastEnd.toSecondOfDay()) return null
        val end = start.plusSeconds(seconds)
        return draft.copy(
            id = 0, weekday = weekday,
            periods = courseAnchorPeriodsForTimeRange(start, end, periods),
            customStartTime = start.toString(),
            customEndTime = end.toString()
        )
    }
    val indexes = draft.periods.distinct().map { index ->
        periods.indexOfFirst { it.periodIndex == index }.takeIf { it >= 0 } ?: return null
    }.sorted()
    val first = indexes.firstOrNull() ?: return null
    val shifted = indexes.map { index ->
        periods.getOrNull(destination + index - first)?.periodIndex ?: return null
    }
    return draft.copy(id = 0, weekday = weekday, periods = shifted)
}

internal fun courseCopyBounds(
    course: CourseEntity,
    grid: CourseEditorWeekGrid,
    weekdays: List<Int>,
    periods: List<PeriodEntity>
): List<Rect> {
    val column = weekdays.indexOf(course.weekday).takeIf { it >= 0 } ?: return emptyList()
    val physicalColumn = if (grid.rightToLeft) weekdays.lastIndex - column else column
    val width = grid.width / weekdays.size
    val left = grid.origin.x + physicalColumn * width + grid.gap / 2f
    val top = grid.origin.y - ((grid.scroll?.value ?: grid.scrollAtCapture) - grid.scrollAtCapture)
    fun bounds(row: Float, count: Float) = Rect(
        left, top + row * grid.rowHeight + grid.gap / 2f,
        left + (width - grid.gap).coerceAtLeast(1f),
        top + (row + count) * grid.rowHeight - grid.gap / 2f
    )
    if (course.hasCustomTime()) {
        val placement = exactTimeWeekPlacement(course, periods) ?: return emptyList()
        return if (placement.heightRows > 0f) listOf(Rect(
            left, top + placement.topRows * grid.rowHeight,
            left + (width - grid.gap).coerceAtLeast(1f),
            top + (placement.topRows + placement.heightRows) * grid.rowHeight
        )) else emptyList()
    }
    val rows = course.periods.map(grid.periodIndexes::indexOf).filter { it >= 0 }.distinct().sorted()
    val runs = mutableListOf<Rect>()
    var start = 0
    while (start < rows.size) {
        var end = start
        while (end + 1 < rows.size && rows[end + 1] == rows[end] + 1) end++
        runs += bounds(rows[start].toFloat(), (end - start + 1).toFloat())
        start = end + 1
    }
    return runs
}

@Stable
internal class CourseCopyController(
    private val scope: CoroutineScope,
    private val validate: (CourseEntity) -> String?,
    private val save: (CourseEntity, (Boolean) -> Unit) -> Unit
) {
    var source by mutableStateOf<CourseShortcutRequest?>(null)
        private set
    var draft by mutableStateOf<CourseEntity?>(null)
        private set
    var target by mutableStateOf<CourseCopyTarget?>(null)
        private set
    var phase by mutableStateOf(CourseCopyPhase.Idle)
        private set
    var message by mutableStateOf("点击目标位置复制课程")
        private set
    var landed by mutableStateOf(false)
        private set
    var rippleCenter by mutableStateOf<Offset?>(null)
        private set
    var rippleRadius by mutableFloatStateOf(1f)
        private set
    val flight = Animatable(0f)
    val impact = Animatable(1f)
    val ripple = Animatable(1f)
    private var motion: Job? = null
    private var session = 0
    private var measuredTarget = false
    private val sourceBounds = mutableMapOf<Int, Rect>()
    val active: Boolean get() = phase != CourseCopyPhase.Idle
    val busy: Boolean get() = active && phase != CourseCopyPhase.Selecting

    fun begin(source: CourseShortcutRequest, draft: CourseEntity) {
        reset()
        this.source = source
        this.draft = draft
        phase = CourseCopyPhase.Selecting
    }

    fun reset() {
        session++
        motion?.cancel()
        source = null
        draft = null
        target = null
        phase = CourseCopyPhase.Idle
        landed = false
        measuredTarget = false
        rippleCenter = null
        sourceBounds.clear()
        message = "点击目标位置复制课程"
    }

    fun clearSelection() {
        if (phase != CourseCopyPhase.Selecting) return
        target = null
        message = "点击目标位置复制课程"
    }

    fun reject(reason: String): CourseCopyTap {
        message = reason
        return CourseCopyTap.Rejected
    }

    fun select(value: CourseCopyTarget): CourseCopyTap {
        if (phase != CourseCopyPhase.Selecting) return CourseCopyTap.Rejected
        validate(value.course)?.let { return reject(it) }
        val previous = target
        target = value
        if (previous?.course != value.course || previous.week != value.week) {
            message = "再点一次，确认复制"
            return CourseCopyTap.Selected
        }
        phase = CourseCopyPhase.Saving
        message = "正在复制课程"
        val token = session
        save(value.course) { success ->
            if (token == session) {
                if (success) fly(value, token) else {
                    phase = CourseCopyPhase.Selecting
                    message = "复制失败，请再点一次"
                }
            }
        }
        return CourseCopyTap.Confirmed
    }

    fun reportSource(course: CourseEntity, week: Int, bounds: Rect) {
        if (course.id == source?.course?.id && phase == CourseCopyPhase.Selecting) {
            sourceBounds[week] = bounds
        }
    }

    fun sourceBounds(week: Int, fallback: Rect): Rect = sourceBounds[week] ?: fallback

    fun isTarget(course: CourseEntity, week: Int): Boolean = target?.let {
        it.week == week && course.scheduleId == it.course.scheduleId &&
            week in course.weeks && parityMatches(course.weekParity, week) &&
            course.occurrenceOverrideKey() == it.course.occurrenceOverrideKey()
    } == true

    fun hides(course: CourseEntity, week: Int): Boolean = busy && isTarget(course, week)

    fun reportTarget(course: CourseEntity, week: Int) {
        if (!busy || !isTarget(course, week)) return
        measuredTarget = true
        if (landed) phase = CourseCopyPhase.Idle
    }

    private fun fly(destination: CourseCopyTarget, token: Int) {
        motion = scope.launch {
            flight.snapTo(0f)
            impact.snapTo(0f)
            phase = CourseCopyPhase.Flying
            flight.animateTo(1f, tween(680, easing = LinearEasing))
            if (session != token) return@launch
            landed = true
            phase = if (measuredTarget) CourseCopyPhase.Idle else CourseCopyPhase.AwaitingCard
            rippleCenter = destination.bounds.first().center
            rippleRadius = maxOf(destination.bounds.first().width * 4.5f, destination.bounds.first().height * 2f)
            launch { impact.animateTo(1f, tween(360, easing = LinearEasing)) }
            ripple.snapTo(0f)
            ripple.animateTo(1f, tween(900, easing = LinearEasing))
            rippleCenter = null
        }
    }
}
