package com.xiaomanjun.sleepdownschedule.feature.course.editor

import com.xiaomanjun.sleepdownschedule.domain.schedule.CourseLessonDuration
import com.xiaomanjun.sleepdownschedule.domain.schedule.CoursePeriodTime
import com.xiaomanjun.sleepdownschedule.domain.schedule.buildCourseLessonTimes
import com.xiaomanjun.sleepdownschedule.domain.schedule.parseCoursePeriodTimes
import java.time.Duration
import java.time.LocalTime

internal data class CourseLessonClockRow(
    val classMinutes: String,
    val breakMinutes: String,
    val startOverride: String = ""
)

internal data class CourseLessonClockDraft(
    val start: String,
    val count: String,
    val classMinutes: String,
    val breakMinutes: String,
    val individually: Boolean,
    val lessons: List<CourseLessonClockRow>
) {
    fun lesson(index: Int): CourseLessonClockRow = lessons.getOrNull(index)
        ?: CourseLessonClockRow(classMinutes, breakMinutes)

    fun withLesson(index: Int, row: CourseLessonClockRow): CourseLessonClockDraft =
        copy(lessons = List(maxOf(index + 1, lessons.size)) { if (it == index) row else lesson(it) })

    fun times(preferredIndices: List<Int>, availableIndices: List<Int> = preferredIndices): List<CoursePeriodTime> {
        val available = courseLessonPickerIndices(preferredIndices, availableIndices)
        require(available.isNotEmpty()) { "当前作息没有可选择的节次" }
        val size = count.toIntOrNull()?.takeIf { it in 1..available.size }
            ?: throw IllegalArgumentException("当前起始节次最多可选 ${available.size} 节")
        val indices = preferredIndices.takeIf { it.size == size && it.all(available::contains) } ?: available.take(size)
        val durations = List(size) { index ->
            val row = if (individually) lesson(index) else CourseLessonClockRow(classMinutes, breakMinutes)
            CourseLessonDuration(
                row.classMinutes.toIntOrNull() ?: throw IllegalArgumentException("请填写第 ${index + 1} 节课程时长"),
                row.breakMinutes.toIntOrNull() ?: throw IllegalArgumentException("请填写第 ${index + 1} 节课间时长"),
                row.startOverride.takeIf { it.isNotBlank() }?.let { parseLessonClock(it) }
            )
        }
        return buildCourseLessonTimes(parseLessonClock(start), indices, durations)
    }
}

internal fun courseLessonPickerIndices(preferred: List<Int>, available: List<Int>): List<Int> {
    val valid = available.filter { it > 0 }.distinct().sorted()
    val first = preferred.firstOrNull { it in valid } ?: valid.firstOrNull() ?: return emptyList()
    return valid.dropWhile { it < first }.take(40)
}

internal fun initialCourseLessonClockDraft(
    start: LocalTime,
    indices: List<Int>,
    customPeriodTimes: String?
): CourseLessonClockDraft {
    val times = parseCoursePeriodTimes(customPeriodTimes).takeIf { it.map { time -> time.index } == indices }.orEmpty()
    val durations = times.mapIndexed { index, time ->
        CourseLessonClockRow(
            Duration.between(time.start, time.end).toMinutes().toString(),
            times.getOrNull(index + 1)?.let { Duration.between(time.end, it.start).toMinutes().toString() } ?: "10"
        )
    }
    val classMinutes = durations.firstOrNull()?.classMinutes ?: "45"
    val breakMinutes = durations.firstOrNull()?.breakMinutes ?: "10"
    val individually = durations.any { it.classMinutes != classMinutes } ||
        durations.dropLast(1).any { it.breakMinutes != breakMinutes }
    return CourseLessonClockDraft(
        times.firstOrNull()?.start?.toString() ?: start.toString(),
        (times.size.takeIf { it > 0 } ?: indices.size.coerceAtLeast(1)).toString(),
        classMinutes, breakMinutes, individually, durations
    )
}

private fun parseLessonClock(value: String): LocalTime {
    require(Regex("\\d{1,2}:\\d{2}").matches(value.trim())) { "时间请填写为 HH:mm" }
    return runCatching { LocalTime.parse(value.trim().padStart(5, '0')) }
        .getOrElse { throw IllegalArgumentException("时间超出 00:00–23:59", it) }
}
