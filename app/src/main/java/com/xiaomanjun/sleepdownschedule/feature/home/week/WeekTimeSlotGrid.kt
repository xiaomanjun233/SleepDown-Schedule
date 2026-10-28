package com.xiaomanjun.sleepdownschedule.feature.home.week

import androidx.compose.runtime.staticCompositionLocalOf
import com.xiaomanjun.sleepdownschedule.domain.schedule.TimeSlotCourse
import kotlin.math.floor
import com.xiaomanjun.sleepdownschedule.model.PeriodEntity
import java.time.LocalTime

/** One immutable axis per page; special bands add space without changing lesson numbers. */
internal data class WeekTimeSlotBand(val slot: TimeSlotCourse, val top: Float, val height: Float)
internal data class WeekClockBand(val start: LocalTime, val end: LocalTime, val top: Float, val height: Float)

internal data class WeekTimeSlotGrid(
    val rowHeight: Float,
    val rowTops: List<Float>,
    val totalHeight: Float,
    val bands: List<WeekTimeSlotBand>,
    val weekdays: List<Int> = (1..7).toList(),
    val clockBands: List<WeekClockBand> = emptyList(),
    val clockRows: List<WeekClockBand> = emptyList()
) {
    fun clockPosition(time: LocalTime): Float? {
        if (clockRows.isEmpty()) return null
        val interval = clockRows.firstOrNull { time >= it.start && time <= it.end }
            ?: clockBands.firstOrNull { time >= it.start && time <= it.end }
        if (interval != null) {
            val duration = java.time.Duration.between(interval.start,interval.end).toSeconds().coerceAtLeast(1)
            val elapsed = java.time.Duration.between(interval.start,time).toSeconds()
            return interval.top + interval.height * elapsed.toFloat() / duration
        }
        return clockRows.firstOrNull { it.start > time }?.top ?: totalHeight
    }
    fun top(row: Float): Float {
        val index = floor(row).toInt()
        if (index < 0) return (rowTops.firstOrNull() ?: 0f) + row * rowHeight
        val start = rowTops.getOrNull(index) ?: if (rowTops.isNotEmpty())
            rowTops.last() + (index - rowTops.lastIndex) * rowHeight else index * rowHeight
        return start + (row - index) * rowHeight
    }

    fun bottom(row: Float): Float = if (row > 0 && row == floor(row)) {
        top(row - 1) + rowHeight
    } else top(row)
}

internal fun weekTimeSlotGrid(
    slots: List<TimeSlotCourse>, periodCount: Int, rowHeight: Float,
    bandHeight: Float = 32f, visibleWeekdays: List<Int> = (1..7).toList(),
    periods: List<PeriodEntity> = emptyList(), clockIntervals: List<Pair<LocalTime, LocalTime>> = emptyList()
): WeekTimeSlotGrid {
    val byAnchor = slots.filter { slot -> slot.weekdays.any { it in visibleWeekdays } }
        .sortedWith(compareBy<TimeSlotCourse> { it.startTime }.thenBy { it.id })
        .groupBy { it.afterPeriod.coerceIn(0, periodCount) }
    val tops = ArrayList<Float>(periodCount)
    val bands = ArrayList<WeekTimeSlotBand>()
    val clockBands = ArrayList<WeekClockBand>()
    val rows = ArrayList<WeekClockBand>()
    val bells = periods.sortedBy { it.periodIndex }.map { LocalTime.parse(it.startTime) to LocalTime.parse(it.endTime) }
    var cursor = 0f
    for (index in 0..periodCount) {
        if (bells.size == periodCount && bells.isNotEmpty() && clockIntervals.isNotEmpty()) {
            val start = if (index == 0) minOf(clockIntervals.minOf { it.first },bells.first().first) else bells[index-1].second
            val end = if (index == periodCount) maxOf(clockIntervals.maxOf { it.second },bells.last().second) else bells[index].first
            if (start < end) {
                val occupied = clockIntervals.filter { (from,to) ->
                    (from >= start && from < end) || (to > start && to <= end)
                }.map { maxOf(start,it.first) to minOf(end,it.second) }.filter { it.first < it.second }.sortedBy { it.first }
                val clusters = mutableListOf<Pair<LocalTime,LocalTime>>()
                occupied.forEach { interval ->
                    val previous=clusters.lastOrNull()
                    if(previous != null && interval.first <= previous.second)
                        clusters[clusters.lastIndex]=previous.first to maxOf(previous.second,interval.second)
                    else clusters+=interval
                }
                clusters.forEach { (from,to) ->
                    val height=maxOf(bandHeight,rowHeight)
                    clockBands += WeekClockBand(from,to,cursor,height)
                    cursor+=height
                }
            }
        }
        byAnchor[index].orEmpty().forEach { slot ->
            bands += WeekTimeSlotBand(slot, cursor, bandHeight)
            cursor += bandHeight
        }
        if (index < periodCount) {
            tops += cursor
            bells.getOrNull(index)?.let { rows += WeekClockBand(it.first,it.second,cursor,rowHeight) }
            cursor += rowHeight
        }
    }
    return WeekTimeSlotGrid(rowHeight, tops, cursor, bands, visibleWeekdays, clockBands, rows)
}

internal val LocalWeekTimeSlotGrid = staticCompositionLocalOf<WeekTimeSlotGrid?> { null }

internal fun weekTimeSlotTailKey(id: String): String = "special-slot:$id"

/** Contiguous visible columns share one capsule, including Sunday-first and hidden weekends. */
internal fun timeSlotColumnRuns(weekdays: List<Int>, included: Set<Int>): List<IntRange> = buildList {
    var first: Int? = null
    weekdays.forEachIndexed { index, day ->
        if (day in included && first == null) first = index
        if (day !in included && first != null) { add(first!! until index); first = null }
    }
    first?.let { add(it..weekdays.lastIndex) }
}
