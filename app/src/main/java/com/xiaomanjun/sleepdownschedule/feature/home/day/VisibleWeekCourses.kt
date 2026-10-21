package com.xiaomanjun.sleepdownschedule.feature.home.day

import androidx.compose.foundation.layout.only
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.core.graphics.get
import com.xiaomanjun.sleepdownschedule.CourseEntity
import com.xiaomanjun.sleepdownschedule.ScheduleConfigEntity
import com.xiaomanjun.sleepdownschedule.domain.schedule.isHiddenByPeriodAlignment
import com.xiaomanjun.sleepdownschedule.parityMatches
import com.xiaomanjun.sleepdownschedule.scheduleWeekStartDate
import java.time.LocalDate
import java.util.Date
import kotlinx.coroutines.flow.map

internal fun courseWeeksChanged(original: CourseEntity, edited: CourseEntity): Boolean {
    return original.weeks.sorted() != edited.weeks.sorted() || original.weekParity != edited.weekParity
}

internal fun coursesVisibleInWeek(courses: List<CourseEntity>, week: Int): List<CourseEntity> {
    val visible = courses.filter { course ->
        !course.isHiddenByPeriodAlignment() && week in course.weeks && parityMatches(course.weekParity, week)
    }
    val singleWeekOverrideKeys = visible
        .filter { it.weeks.distinct() == listOf(week) }
        .map { it.occurrenceOverrideKey() }
        .toSet()
    if (singleWeekOverrideKeys.isEmpty()) return visible
    return visible.filter { course ->
        course.weeks.distinct() == listOf(week) || course.occurrenceOverrideKey() !in singleWeekOverrideKeys
    }
}

internal data class WeekCourseBuckets(
    val visibleCourses: List<CourseEntity>,
    val byWeekday: Map<Int, List<CourseEntity>>,
    val weekendHasCourse: Boolean,
    /** Days taken off by an adjustment: their regular cards stay visible but read-only and greyed. */
    val cancelledWeekdays: Set<Int> = emptySet(),
    /** Days that host lessons moved from another date: shown normally, but never edited in place. */
    val makeupWeekdays: Set<Int> = emptySet()
)

/** Neutral wash that marks a course occurrence as an adjustment placeholder instead of a real class. */

internal val SchoolWeekdays = (1..5).toList()
internal val FullWeekdays = (1..7).toList()

internal fun weekCourseBuckets(courses: List<CourseEntity>, week: Int): WeekCourseBuckets {
    val visibleCourses = coursesVisibleInWeek(courses, week)
    return WeekCourseBuckets(
        visibleCourses = visibleCourses,
        byWeekday = visibleCourses.groupBy { it.weekday },
        weekendHasCourse = visibleCourses.any { it.weekday == 6 || it.weekday == 7 }
    )
}

/** Date adjustments affect rendered occurrences, never the stored weekday/weeks. */
internal fun weekCourseBuckets(
    courses: List<CourseEntity>, week: Int, config: ScheduleConfigEntity, today: LocalDate = LocalDate.now()
): WeekCourseBuckets {
    if (config.scheduleAdjustmentsJson.isBlank()) return weekCourseBuckets(courses, week)
    val start = scheduleWeekStartDate(config, week, today)
    val regular = coursesVisibleInWeek(courses, week).groupBy { it.weekday }
    val sourceWeeks = mutableMapOf(week to regular)
    val cancelled = mutableSetOf<Int>()
    val makeup = mutableSetOf<Int>()
    val visible = (1..7).flatMap { weekday ->
        val adjustment = com.xiaomanjun.sleepdownschedule.domain.schedule.scheduleAdjustmentForDate(config, start.plusDays((weekday - 1).toLong()))
        if (adjustment == null) {
            regular[weekday].orEmpty()
        } else if (adjustment.sourceDate == null) {
            // The day is off. Keep the regular cards so the timetable still shows what was planned,
            // and let the views grey them out while editing still resolves the original course.
            cancelled += weekday
            regular[weekday].orEmpty()
        } else {
            makeup += weekday
            val origin = LocalDate.parse(adjustment.sourceDate)
            val originWeek = com.xiaomanjun.sleepdownschedule.domain.schedule.adjustedTeachingWeekForDate(config, origin, today)
            if (originWeek == null) emptyList() else sourceWeeks.getOrPut(originWeek) {
                coursesVisibleInWeek(courses, originWeek).groupBy { it.weekday }
            }[origin.dayOfWeek.value].orEmpty().map { it.copy(weekday = weekday) }
        }
    }
    return WeekCourseBuckets(
        visibleCourses = visible,
        byWeekday = visible.groupBy { it.weekday },
        // Cancelled classes must not make an otherwise empty weekend claim to have courses.
        weekendHasCourse = visible.any { it.weekday >= 6 && it.weekday !in cancelled },
        cancelledWeekdays = cancelled,
        makeupWeekdays = makeup
    )
}

internal fun visibleWeekdaysForBuckets(
    buckets: WeekCourseBuckets,
    hideEmptyWeekends: Boolean
): List<Int> = if (hideEmptyWeekends && !buckets.weekendHasCourse) {
    SchoolWeekdays
} else {
    FullWeekdays
}

internal fun CourseEntity.occurrenceOverrideKey(): String {
    return listOf(
        weekday.toString(),
        periods.distinct().sorted().joinToString(","),
        customStartTime.orEmpty(),
        customEndTime.orEmpty(),
        name.trim()
    ).joinToString("|")
}
