package com.xiaomanjun.sleepdownschedule.feature.home.week

import androidx.compose.runtime.staticCompositionLocalOf
import com.xiaomanjun.sleepdownschedule.domain.schedule.TimeSlotCourse
import com.xiaomanjun.sleepdownschedule.domain.schedule.TimeSlotDisplayMode
import com.xiaomanjun.sleepdownschedule.model.PeriodEntity
import java.time.LocalTime
import kotlin.math.floor

internal data class WeekTimeSlotBand(
    val slot: TimeSlotCourse, val top: Float, val height: Float,
    val collapsed: Boolean = false, val inGrid: Boolean = false
)
internal data class WeekClockBand(val start: LocalTime, val end: LocalTime, val top: Float, val height: Float)

/** One lesson axis. Exact clocks never insert rows or change lesson numbers. */
internal data class WeekTimeSlotGrid(
    val rowHeight: Float,
    val rowTops: List<Float>,
    val totalHeight: Float,
    val bands: List<WeekTimeSlotBand>,
    val weekdays: List<Int> = (1..7).toList(),
    val clockBands: List<WeekClockBand> = emptyList(),
    val clockRows: List<WeekClockBand> = emptyList(),
    val rowHeights: List<Float> = List(rowTops.size) { rowHeight },
    val collapsedRows: Set<Int> = emptySet()
) {
    fun clockPosition(time: LocalTime): Float? {
        if (clockRows.isEmpty()) return null
        val interval = clockRows.firstOrNull { time >= it.start && time <= it.end }
        if (interval != null) {
            val duration = java.time.Duration.between(interval.start, interval.end).toSeconds().coerceAtLeast(1)
            val elapsed = java.time.Duration.between(interval.start, time).toSeconds()
            return interval.top + interval.height * elapsed.toFloat() / duration
        }
        return clockRows.firstOrNull { it.start > time }?.top
            ?: clockRows.last().let { it.top + it.height }
    }
    fun top(row: Float): Float {
        val index = floor(row).toInt()
        if (index < 0) return (rowTops.firstOrNull() ?: 0f) + row * rowHeight
        val start = rowTops.getOrNull(index) ?: if (rowTops.isNotEmpty())
            rowTops.last() + rowHeights.last() + (index - rowTops.size) * rowHeight else index * rowHeight
        return start + (row - index) * (rowHeights.getOrNull(index) ?: rowHeight)
    }
    fun bottom(row: Float): Float = if (row > 0 && row == floor(row)) {
        val index = row.toInt() - 1
        top(index.toFloat()) + (rowHeights.getOrNull(index) ?: rowHeight)
    } else top(row)
}

internal fun weekTimeSlotGrid(
    slots: List<TimeSlotCourse>, periodCount: Int, rowHeight: Float,
    bandHeight: Float = 32f, visibleWeekdays: List<Int> = (1..7).toList(),
    periods: List<PeriodEntity> = emptyList(), clockIntervals: List<Pair<LocalTime, LocalTime>> = emptyList(),
    week: Int = 1
): WeekTimeSlotGrid {
    val visible = slots.filter { slot -> week >= slot.startWeek && (slot.endWeek == null || week <= slot.endWeek) &&
        slot.weekdays.any { it in visibleWeekdays } }
        .sortedWith(compareBy<TimeSlotCourse> { it.startTime }.thenBy { it.id })
    val bells = periods.sortedBy { it.periodIndex }.map { LocalTime.parse(it.startTime) to LocalTime.parse(it.endTime) }
    fun covered(slot: TimeSlotCourse) = bells.indices.filter { index ->
        LocalTime.parse(slot.startTime) < bells[index].second && LocalTime.parse(slot.endTime) > bells[index].first
    }
    val independent = visible.filter { it.displayMode == TimeSlotDisplayMode.INDEPENDENT || covered(it).isEmpty() }
        .groupBy { slot ->
            if (slot.afterPeriod == 0) 0 else periods.sortedBy { it.periodIndex }
                .indexOfFirst { it.periodIndex == slot.afterPeriod }.takeIf { it >= 0 }?.plus(1)
                ?: slot.afterPeriod.coerceIn(0, periodCount)
        }
    val heights = MutableList(periodCount) { rowHeight }
    val collapsed = linkedSetOf<Int>()
    val compactSlots = linkedSetOf<String>()
    visible.filter { it.displayMode == TimeSlotDisplayMode.SMART }.forEach { slot ->
        val rows = covered(slot)
        if (rows.isNotEmpty() && rows.none { it in collapsed } && clockIntervals.none { (start, end) ->
                start < bells[rows.last()].second && end > bells[rows.first()].first
            } && independent.keys.none { it > rows.first() && it <= rows.last() }) {
            rows.forEach { heights[it] = bandHeight / rows.size }
            collapsed += rows
            compactSlots += slot.id
        }
    }
    val tops = ArrayList<Float>(periodCount)
    val bands = ArrayList<WeekTimeSlotBand>()
    val clockRows = ArrayList<WeekClockBand>()
    var cursor = 0f
    for (index in 0..periodCount) {
        independent[index].orEmpty().forEach { slot ->
            bands += WeekTimeSlotBand(slot, cursor, bandHeight)
            cursor += bandHeight
        }
        if (index < periodCount) {
            tops += cursor
            bells.getOrNull(index)?.let { clockRows += WeekClockBand(it.first, it.second, cursor, heights[index]) }
            cursor += heights[index]
        }
    }
    val grid = WeekTimeSlotGrid(rowHeight, tops, cursor, bands, visibleWeekdays,
        clockRows = clockRows, rowHeights = heights, collapsedRows = collapsed)
    visible.filter { it.displayMode != TimeSlotDisplayMode.INDEPENDENT && covered(it).isNotEmpty() }.forEach { slot ->
        val rows = covered(slot)
        val top = grid.top(rows.first().toFloat())
        bands += WeekTimeSlotBand(slot, top, grid.bottom((rows.last() + 1).toFloat()) - top,
            collapsed = slot.id in compactSlots, inGrid = true)
    }
    return grid.copy(bands = bands.sortedBy { it.top })
}

internal val LocalWeekTimeSlotGrid = staticCompositionLocalOf<WeekTimeSlotGrid?> { null }
internal fun weekTimeSlotTailKey(id: String): String = "special-slot:$id"

internal fun timeSlotColumnRuns(weekdays: List<Int>, included: Set<Int>): List<IntRange> = buildList {
    var first: Int? = null
    weekdays.forEachIndexed { index, day ->
        if (day in included && first == null) first = index
        if (day !in included && first != null) { add(first!! until index); first = null }
    }
    first?.let { add(it..weekdays.lastIndex) }
}
