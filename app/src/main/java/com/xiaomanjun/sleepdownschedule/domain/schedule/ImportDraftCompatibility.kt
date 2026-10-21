package com.xiaomanjun.sleepdownschedule.domain.schedule

import com.xiaomanjun.sleepdownschedule.model.ImportDraft
import com.xiaomanjun.sleepdownschedule.model.PeriodEntity
import java.time.LocalTime

/** Repair only the shared display grid; retain the source numbers and exact course clocks. */
fun ImportDraft.withCompatiblePeriodGrid(): ImportDraft {
    val source = periods.distinct().sortedBy { it.periodIndex }
    require(source.isNotEmpty()) { "没有可用于显示课程的节次时间" }
    require(source.map { it.periodIndex }.distinct().size == source.size) { "同一节次存在不同时间，请先确认实际作息" }
    val clocks = source.map {
        require(it.periodIndex > 0) { "节次编号必须大于 0" }
        val start = LocalTime.parse(it.startTime)
        val end = LocalTime.parse(it.endTime)
        require(start < end) { "第 ${it.periodIndex} 节结束时间必须晚于开始时间" }
        start to end
    }
    val regular = source.map { it.periodIndex } == (1..source.size).toList() &&
        clocks.zipWithNext().all { (a, b) -> a.second <= b.first }
    if (regular) return copy(periods = source)

    val boundaries = clocks.flatMap { listOf(it.first, it.second) }.distinct().sorted()
    val grid = boundaries.zipWithNext().filter { (start, end) ->
        clocks.any { it.first <= start && it.second >= end }
    }.mapIndexed { index, (start, end) ->
        PeriodEntity(index + 1, start.toString(), end.toString(), config.id)
    }
    val preserved = courses.map { course ->
        val original = captureOriginalPeriodTimes(course, source)
        val segments = courseTimeSegments(original, source).sortedBy { it.start }
        require(segments.isNotEmpty()) { "${course.name} 缺少上课时间，请补充后导入" }
        val separate = segments.zipWithNext().all { (a, b) -> a.end <= b.start }
        original.copy(
            customStartTime = segments.minOf { it.start }.toString(),
            customEndTime = segments.maxOf { it.end }.toString(),
            customPeriodTimes = if (separate && segments.map { it.index }.toSet() == course.periods.toSet())
                encodeCoursePeriodTimes(segments) else null,
            arrangementProjection = null
        )
    }
    return copy(periods = grid, courses = preserved,
        notices = (notices + "已整理不连续或重叠的节次作为显示网格，课程原始节次和实际上课时间已保留。").distinct())
}
