package com.xiaomanjun.sleepdownschedule.model

import java.time.LocalTime

/** A course clock value shared by saved courses and domain projections. */
data class CoursePeriodTime(val index: Int, val start: LocalTime, val end: LocalTime)

/** Only this immutable, non-Room projection contains clipped/rebound period numbers. */
data class AlignedCourseTime(val time: CoursePeriodTime, val exact: Boolean)

data class EffectiveCourseArrangement(
    val source: CourseEntity,
    val periods: List<Int>,
    val times: List<AlignedCourseTime>,
    val hiddenPeriods: List<Int>,
    val mode: PeriodAlignmentMode,
    val displayStart: String? = source.customStartTime,
    val displayEnd: String? = source.customEndTime,
    val displayPeriodTimes: String? = source.customPeriodTimes
) {
    fun matches(course: CourseEntity): Boolean = course.periods == periods &&
        course.customStartTime == displayStart && course.customEndTime == displayEnd &&
        course.customPeriodTimes == displayPeriodTimes
}
