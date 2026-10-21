package com.xiaomanjun.sleepdownschedule.domain.schedule

import java.time.LocalTime

data class CourseLessonDuration(
    val classMinutes: Int,
    val breakMinutes: Int,
    val startOverride: LocalTime? = null
)

fun buildCourseLessonTimes(
    start: LocalTime,
    periodIndices: List<Int>,
    lessons: List<CourseLessonDuration>
): List<CoursePeriodTime> {
    require(lessons.size in 1..40 && lessons.size == periodIndices.size) { "节数必须为 1–40" }
    require(periodIndices.all { it > 0 } && periodIndices.zipWithNext().all { (a, b) -> a < b }) {
        "课程节次必须为递增的正整数"
    }
    var cursor = start.hour * 60 + start.minute
    var previousEnd = -1
    return lessons.mapIndexed { index, lesson ->
        require(lesson.classMinutes in 1..1439) { "第 ${index + 1} 节课程时长必须为 1–1439 分钟" }
        require(lesson.breakMinutes in 0..1439) { "第 ${index + 1} 节课间时长必须为 0–1439 分钟" }
        val begin = lesson.startOverride?.let { it.hour * 60 + it.minute } ?: cursor
        val end = begin + lesson.classMinutes
        require(begin >= previousEnd) { "第 ${index + 1} 节与上一节时间重叠" }
        require(begin in 0..1438 && end in 1..1439) { "第 ${index + 1} 节超出当天时间范围" }
        previousEnd = end
        cursor = end + lesson.breakMinutes
        CoursePeriodTime(periodIndices[index], LocalTime.of(begin / 60, begin % 60),
            LocalTime.of(end / 60, end % 60))
    }
}
