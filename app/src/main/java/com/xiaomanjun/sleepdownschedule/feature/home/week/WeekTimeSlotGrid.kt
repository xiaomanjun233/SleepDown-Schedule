package com.xiaomanjun.sleepdownschedule.feature.home.week

import androidx.compose.runtime.staticCompositionLocalOf
import com.xiaomanjun.sleepdownschedule.domain.schedule.TimeSlotCourse
import kotlin.math.floor

/** One immutable axis per page; special bands add space without changing lesson numbers. */
internal data class WeekTimeSlotBand(val slot: TimeSlotCourse, val top: Float, val height: Float)

internal data class WeekTimeSlotGrid(
    val rowHeight: Float,
    val rowTops: List<Float>,
    val totalHeight: Float,
    val bands: List<WeekTimeSlotBand>,
    val weekdays: List<Int> = (1..7).toList()
) {
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
    bandHeight: Float = 32f, visibleWeekdays: List<Int> = (1..7).toList()
): WeekTimeSlotGrid {
    val byAnchor = slots.filter { slot -> slot.weekdays.any { it in visibleWeekdays } }
        .sortedWith(compareBy<TimeSlotCourse> { it.startTime }.thenBy { it.id })
        .groupBy { it.afterPeriod.coerceIn(0, periodCount) }
    val tops = ArrayList<Float>(periodCount)
    val bands = ArrayList<WeekTimeSlotBand>()
    var cursor = 0f
    for (index in 0..periodCount) {
        byAnchor[index].orEmpty().forEach { slot ->
            bands += WeekTimeSlotBand(slot, cursor, bandHeight)
            cursor += bandHeight
        }
        if (index < periodCount) {
            tops += cursor
            cursor += rowHeight
        }
    }
    return WeekTimeSlotGrid(rowHeight, tops, cursor, bands, visibleWeekdays)
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
