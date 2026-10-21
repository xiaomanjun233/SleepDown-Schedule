package com.xiaomanjun.sleepdownschedule.feature.home.day

import com.xiaomanjun.sleepdownschedule.domain.schedule.courseTimeSegments
import com.xiaomanjun.sleepdownschedule.domain.schedule.customTimeRangeOrNull
import com.xiaomanjun.sleepdownschedule.domain.schedule.effectiveArrangementOrNull
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
): Boolean {
    if (occurrenceDate != now.toLocalDate()) return false
    val segments = courseTimeSegments(course, periods)
    val wholeCourseClock = course.customPeriodTimes.isNullOrBlank() && course.customTimeRangeOrNull() != null
    if (!wholeCourseClock && course.effectiveArrangementOrNull() == null) {
        val lastPeriod = course.periods.maxOrNull() ?: return false
        // Missing final bells must not turn an earlier, known segment into the course's end.
        if (segments.none { it.index == lastPeriod }) return false
    }
    return segments.maxOfOrNull { it.end }?.let { !now.toLocalTime().isBefore(it) } == true
}
