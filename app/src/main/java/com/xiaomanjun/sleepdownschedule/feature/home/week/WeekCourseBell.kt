package com.xiaomanjun.sleepdownschedule.feature.home.week

import com.xiaomanjun.sleepdownschedule.CourseEntity
import com.xiaomanjun.sleepdownschedule.domain.schedule.CoursePeriodTime
import com.xiaomanjun.sleepdownschedule.domain.schedule.parseCoursePeriodTimes
import java.time.LocalTime

/** The rail follows a real imported bell only while that course is in the current section. */
internal fun activeWeekCourseBell(
    courses: List<CourseEntity>,
    weekday: Int,
    now: LocalTime
): CoursePeriodTime? = courses.asSequence()
    .filter { it.weekday == weekday && it.customPeriodTimes != null }
    .sortedBy(CourseEntity::id)
    .flatMap { course ->
        runCatching { parseCoursePeriodTimes(course.customPeriodTimes) }.getOrDefault(emptyList()).asSequence()
    }
    .firstOrNull { !now.isBefore(it.start) && now.isBefore(it.end) }

/** A break between imported bells must not fall back to another campus's active period. */
internal fun specialCourseCoversTime(
    courses: List<CourseEntity>,
    weekday: Int,
    now: LocalTime
): Boolean = courses.asSequence()
    .filter { it.weekday == weekday && it.customPeriodTimes != null }
    .map { runCatching { parseCoursePeriodTimes(it.customPeriodTimes) }.getOrDefault(emptyList()) }
    .any { bells -> bells.isNotEmpty() && !now.isBefore(bells.first().start) && now.isBefore(bells.last().end) }
