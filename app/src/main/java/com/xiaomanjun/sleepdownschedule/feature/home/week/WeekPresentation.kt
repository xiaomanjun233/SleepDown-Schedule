package com.xiaomanjun.sleepdownschedule.feature.home.week

import androidx.compose.ui.unit.dp
import com.xiaomanjun.sleepdownschedule.domain.course.buildWeekConflictGroups
import com.xiaomanjun.sleepdownschedule.domain.course.occupiedTimeIntervals
import com.xiaomanjun.sleepdownschedule.domain.schedule.*
import com.xiaomanjun.sleepdownschedule.feature.home.day.*
import com.xiaomanjun.sleepdownschedule.model.*

/** Only the timetable renderer consumes these; they never enter calendar/Widget course buckets. */
internal data class NonCurrentWeekCard(val course: CourseEntity, val top: Float, val bottom: Float)

/** References preview a future occurrence relative to the browsed week, including parity gaps. */
internal fun CourseEntity.isUpcomingWeekReference(week: Int): Boolean =
    !(week in weeks && parityMatches(weekParity, week)) &&
        weeks.any { it > week && parityMatches(weekParity, it) }

internal fun nonCurrentWeekCards(
    courses: List<CourseEntity>, actual: List<CourseEntity>, week: Int, day: Int,
    periods: List<PeriodEntity>, rowHeight: Float,
    renderedActualBounds: List<Pair<Float, Float>>? = null,
    timeGrid: WeekTimeSlotGrid? = null
): List<NonCurrentWeekCard> {
    fun cards(course: CourseEntity): List<NonCurrentWeekCard> =
        courseWeekDisplayFragments(course, periods).flatMap { fragment ->
            if (courseNeedsSupplementaryWeekRow(fragment, periods) && timeGrid?.clockRows.isNullOrEmpty()) emptyList() else {
                val segments = buildWeekConflictGroups(listOf(fragment), periods.map { it.periodIndex }, periods)
                    .flatMap { it.segments }
                val rendered = if (fragment.hasCustomTime() && fragment.arrangementProjection == null)
                    segments.take(1) else segments
                rendered.map { segment ->
                    val (top, bottom) = weekCardVerticalBounds(segment, periods, rowHeight.dp, timeGrid)
                    NonCurrentWeekCard(segment.course, top, bottom)
                }
            }
        }
    val occupied = (renderedActualBounds ?: actual.filter { it.weekday == day }.flatMap(::cards)
        .map { it.top to it.bottom }).toMutableList()
    val candidates = courses.filter { it.weekday == day && !it.isHiddenByPeriodAlignment() &&
        it.isUpcomingWeekReference(week) }
        .flatMap(::cards)
        .sortedWith(compareBy<NonCurrentWeekCard> { it.top }.thenBy { it.bottom }
            .thenBy { it.course.id }.thenBy { it.course.name })
    return buildList {
        candidates.forEach { candidate ->
            if (occupied.none { it.first < candidate.bottom && candidate.top < it.second }) {
                add(candidate)
                occupied += candidate.top to candidate.bottom
            }
        }
    }
}

internal fun weekPresentationWeekdays(
    buckets: WeekCourseBuckets, courses: List<CourseEntity>, config: ScheduleConfigEntity, week: Int
): List<Int> {
    val start = scheduleWeekStartDate(config, week)
    val hasWeekendPlaceholder = (6..7).any { day ->
        scheduleAdjustmentForDate(config, start.plusDays(com.xiaomanjun.sleepdownschedule.domain.schedule.scheduleWeekdayOffset(config, day)))?.let {
            it.sourceDate == null && it.allDayPlaceholder
        } == true
    }
    val hasWeekendSlots = runCatching { decodeTimeSlotCourses(config.timeSlotCoursesJson) }.getOrDefault(emptyList())
        .any { slot -> week >= slot.startWeek && (slot.endWeek == null || week <= slot.endWeek) && slot.weekdays.any { it >= 6 } }
    val hasWeekendReference = config.showNonCurrentWeekCourses && courses.any {
        it.weekday >= 6 && !it.isHiddenByPeriodAlignment() &&
            it.isUpcomingWeekReference(week)
    }
    return visibleWeekdaysForBuckets(buckets,
        config.hideEmptyWeekends && !hasWeekendPlaceholder && !hasWeekendReference && !hasWeekendSlots, config.weekStartsOnSunday)
}

internal fun nonCurrentSupplementaryCourses(
    courses: List<CourseEntity>, actual: List<CourseEntity>, week: Int,
    day: Int, periods: List<PeriodEntity>
): List<CourseEntity> {
    val occupied = actual.filter { it.weekday == day }.flatMap { it.occupiedTimeIntervals(periods) }.toMutableList()
    return buildList {
        courses.filter { it.weekday == day && !it.isHiddenByPeriodAlignment() &&
            it.isUpcomingWeekReference(week) }
            .flatMap { courseWeekDisplayFragments(it, periods) }
            .filter { courseNeedsSupplementaryWeekRow(it, periods) }
            .sortedWith(compareBy<CourseEntity> { it.customStartTime }.thenBy { it.id }).forEach { course ->
                val intervals = course.occupiedTimeIntervals(periods)
                if (intervals.isNotEmpty() && intervals.none { (start, end) ->
                    occupied.any { (otherStart, otherEnd) -> start < otherEnd && otherStart < end }
                }) {
                    add(course)
                    occupied += intervals
                }
            }
    }
}
