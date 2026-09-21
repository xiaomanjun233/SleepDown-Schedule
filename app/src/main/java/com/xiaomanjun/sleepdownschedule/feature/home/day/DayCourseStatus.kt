package com.xiaomanjun.sleepdownschedule.feature.home.day

import com.xiaomanjun.sleepdownschedule.domain.schedule.courseEndTime
import com.xiaomanjun.sleepdownschedule.model.CourseEntity
import com.xiaomanjun.sleepdownschedule.model.PeriodEntity
import java.time.LocalDate
import java.time.LocalDateTime

/** Use the displayed occurrence date, including make-up classes and the second day pane. */
internal fun hasDayCourseEnded(
    course: CourseEntity,
    periods: List<PeriodEntity>,
    occurrenceDate: LocalDate,
    now: LocalDateTime
): Boolean = occurrenceDate == now.toLocalDate() &&
    courseEndTime(course, periods)?.let { !now.toLocalTime().isBefore(it) } == true
