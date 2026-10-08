package com.xiaomanjun.sleepdownschedule.domain.schedule

import com.xiaomanjun.sleepdownschedule.model.CourseEntity
import com.xiaomanjun.sleepdownschedule.model.PeriodEntity
import java.time.LocalTime

/** A course can have its own bell times even when its period numbers match the default scheme. */
data class CoursePeriodTime(val index: Int, val start: LocalTime, val end: LocalTime)

/** The same lesson boundaries drive the live activity and the home assistant. */
fun courseTimeSegments(course: CourseEntity, periods: List<PeriodEntity>): List<CoursePeriodTime> {
    course.effectiveArrangementOrNull()?.let { return it.times.map(AlignedCourseTime::time) }
    val custom = runCatching { parseCoursePeriodTimes(course.customPeriodTimes) }.getOrDefault(emptyList())
    if (custom.isNotEmpty() && custom.map(CoursePeriodTime::index) == course.periods.distinct().sorted()) {
        return custom
    }
    if (custom.isEmpty()) course.customTimeRangeOrNull()?.let { (start, end) ->
        return listOf(CoursePeriodTime(course.periods.minOrNull() ?: 0, start, end))
    }
    val periodByIndex = periods.associateBy(PeriodEntity::periodIndex)
    return course.periods.distinct().sorted().mapNotNull { index ->
        custom.firstOrNull { it.index == index }?.let { return@mapNotNull it }
        val period = periodByIndex[index] ?: return@mapNotNull null
        val start = runCatching { LocalTime.parse(period.startTime) }.getOrNull() ?: return@mapNotNull null
        val end = runCatching { LocalTime.parse(period.endTime) }.getOrNull() ?: return@mapNotNull null
        CoursePeriodTime(index, start, end).takeIf { end.isAfter(start) }
    }.sortedBy(CoursePeriodTime::start)
}

data class NormalizedCourseClock(
    val start: String?,
    val end: String?,
    val periodTimes: String?
)

/**
 * `3,10:10-10:50;4,11:05-11:45` is a compact, stable field shared by Room, backups and SDCT1.
 * Partial lists override only their named lessons; uncovered lessons use the selected scheme.
 */
fun normalizeCourseClock(
    start: String?,
    end: String?,
    periodTimes: String?,
    periods: List<Int>
): NormalizedCourseClock {
    val bells = parseCoursePeriodTimes(periodTimes)
    if (bells.isNotEmpty()) {
        require(bells.all { it.index in periods }) {
            "课程逐节时间包含该课程之外的节次"
        }
        bells.zipWithNext().forEach { (previous, next) ->
            require(!next.start.isBefore(previous.end)) { "课程逐节时间相互重叠" }
        }
    }
    val providedStart = start?.trim()?.takeIf(String::isNotEmpty)
    val providedEnd = end?.trim()?.takeIf(String::isNotEmpty)
    require((providedStart == null) == (providedEnd == null)) { "课程真实起止时间必须同时提供" }
    val parsedStart = providedStart?.let { parseClock(it) }
    val parsedEnd = providedEnd?.let { parseClock(it) }
    if (parsedStart != null && parsedEnd != null) {
        require(parsedStart < parsedEnd) { "课程结束时间必须晚于开始时间" }
    }
    val complete = bells.isNotEmpty() && bells.map(CoursePeriodTime::index) == periods.distinct().sorted()
    if (complete) {
        require(parsedStart == null || parsedStart == bells.first().start) { "课程起始时间与逐节铃声不一致" }
        require(parsedEnd == null || parsedEnd == bells.last().end) { "课程结束时间与逐节铃声不一致" }
    }
    return NormalizedCourseClock(
        start = (parsedStart ?: bells.firstOrNull()?.start?.takeIf { complete })?.toString(),
        end = (parsedEnd ?: bells.lastOrNull()?.end?.takeIf { complete })?.toString(),
        periodTimes = bells.takeIf { it.isNotEmpty() }?.joinToString(";") {
            "${it.index},${it.start}-${it.end}"
        }
    )
}

fun parseCoursePeriodTimes(value: String?): List<CoursePeriodTime> {
    if (value.isNullOrBlank()) return emptyList()
    val parsed = value.split(';').map { item ->
        val parts = item.trim().split(',', limit = 2)
        require(parts.size == 2) { "课程逐节时间格式应为 节次,HH:mm-HH:mm" }
        val index = parts[0].trim().toIntOrNull()
        require(index != null && index > 0) { "课程逐节时间的节次无效" }
        val bounds = parts[1].trim().split('-', limit = 2)
        require(bounds.size == 2) { "课程逐节时间格式应为 节次,HH:mm-HH:mm" }
        require(Regex("\\d{2}:\\d{2}").matches(bounds[0].trim()) &&
            Regex("\\d{2}:\\d{2}").matches(bounds[1].trim())) { "逐节铃声必须是 HH:mm" }
        val start = parseClock(bounds[0].trim())
        val end = parseClock(bounds[1].trim())
        require(start < end) { "第 $index 节结束时间必须晚于开始时间" }
        CoursePeriodTime(index, start, end)
    }.sortedBy(CoursePeriodTime::index)
    require(parsed.map(CoursePeriodTime::index).distinct().size == parsed.size) { "课程逐节时间有重复节次" }
    return parsed
}

private fun parseClock(value: String): LocalTime {
    return runCatching { LocalTime.parse(value) }
        .getOrElse { throw IllegalArgumentException("无效时间: $value", it) }
}
