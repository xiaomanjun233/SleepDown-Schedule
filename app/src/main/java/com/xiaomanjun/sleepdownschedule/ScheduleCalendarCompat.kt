package com.xiaomanjun.sleepdownschedule

import com.xiaomanjun.sleepdownschedule.domain.schedule.courseAllowsWeekPeriodDrag as domainCourseAllowsWeekPeriodDrag
import com.xiaomanjun.sleepdownschedule.domain.schedule.courseAnchorPeriodsForTimeRange as domainCourseAnchorPeriodsForTimeRange
import com.xiaomanjun.sleepdownschedule.domain.schedule.courseEndTime as domainCourseEndTime
import com.xiaomanjun.sleepdownschedule.domain.schedule.coursesForDate as domainCoursesForDate
import com.xiaomanjun.sleepdownschedule.domain.schedule.courseStartTime as domainCourseStartTime
import com.xiaomanjun.sleepdownschedule.domain.schedule.courseTimeLabel as domainCourseTimeLabel
import com.xiaomanjun.sleepdownschedule.domain.schedule.currentTimelinePeriod as domainCurrentTimelinePeriod
import com.xiaomanjun.sleepdownschedule.domain.schedule.customTimeRangeOrNull as domainCustomTimeRangeOrNull
import com.xiaomanjun.sleepdownschedule.domain.schedule.derivedScheduleTermState as domainDerivedScheduleTermState
import com.xiaomanjun.sleepdownschedule.domain.schedule.effectiveCurrentWeek as domainEffectiveCurrentWeek
import com.xiaomanjun.sleepdownschedule.domain.schedule.exactTimeWeekPlacement as domainExactTimeWeekPlacement
import com.xiaomanjun.sleepdownschedule.domain.schedule.formatScheduleDate as domainFormatScheduleDate
import com.xiaomanjun.sleepdownschedule.domain.schedule.hasCustomTime as domainHasCustomTime
import com.xiaomanjun.sleepdownschedule.domain.schedule.isAfterScheduleTerm as domainIsAfterScheduleTerm
import com.xiaomanjun.sleepdownschedule.domain.schedule.isBeforeScheduleTerm as domainIsBeforeScheduleTerm
import com.xiaomanjun.sleepdownschedule.domain.schedule.parityLabel as domainParityLabel
import com.xiaomanjun.sleepdownschedule.domain.schedule.parityMatches as domainParityMatches
import com.xiaomanjun.sleepdownschedule.domain.schedule.parseScheduleDate as domainParseScheduleDate
import com.xiaomanjun.sleepdownschedule.domain.schedule.resolveScheduleCurrentWeek as domainResolveScheduleCurrentWeek
import com.xiaomanjun.sleepdownschedule.domain.schedule.scheduleDayNavigationRange as domainScheduleDayNavigationRange
import com.xiaomanjun.sleepdownschedule.domain.schedule.scheduleTermEndDate as domainScheduleTermEndDate
import com.xiaomanjun.sleepdownschedule.domain.schedule.scheduleTermStatusDescription as domainScheduleTermStatusDescription
import com.xiaomanjun.sleepdownschedule.domain.schedule.scheduleTermStatusLabel as domainScheduleTermStatusLabel
import com.xiaomanjun.sleepdownschedule.domain.schedule.scheduleWeekForDateOrNull as domainScheduleWeekForDateOrNull
import com.xiaomanjun.sleepdownschedule.domain.schedule.scheduleWeekStartDate as domainScheduleWeekStartDate
import com.xiaomanjun.sleepdownschedule.domain.schedule.toChineseWeekday as domainToChineseWeekday
import com.xiaomanjun.sleepdownschedule.domain.schedule.todayCourses as domainTodayCourses
import com.xiaomanjun.sleepdownschedule.domain.schedule.withDerivedScheduleTermState as domainWithDerivedScheduleTermState

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/** Source-compatible facade while root-package screens migrate to the schedule domain. */
typealias WeekExactTimePlacement =
    com.xiaomanjun.sleepdownschedule.domain.schedule.WeekExactTimePlacement

fun todayCourses(state: AppState): List<CourseEntity> = domainTodayCourses(state)

fun coursesForDate(state: AppState, date: LocalDate): List<CourseEntity> = domainCoursesForDate(state, date)

fun currentTimelinePeriod(periods: List<PeriodEntity>, now: LocalTime): PeriodEntity? =
    domainCurrentTimelinePeriod(periods, now)

fun derivedScheduleTermState(
    config: ScheduleConfigEntity,
    today: LocalDate = LocalDate.now()
): ScheduleTermState = domainDerivedScheduleTermState(config, today)

internal fun ScheduleConfigEntity.withDerivedScheduleTermState(
    today: LocalDate = LocalDate.now()
): ScheduleConfigEntity = domainWithDerivedScheduleTermState(today)

fun effectiveCurrentWeek(
    config: ScheduleConfigEntity,
    today: LocalDate = LocalDate.now()
): Int = domainEffectiveCurrentWeek(config, today)

fun resolveScheduleCurrentWeek(
    baseConfig: ScheduleConfigEntity,
    totalWeeks: Int,
    manualCurrentWeek: Int,
    termStartDate: String?,
    autoCurrentWeek: Boolean,
    today: LocalDate = LocalDate.now()
): Int = domainResolveScheduleCurrentWeek(
    baseConfig,
    totalWeeks,
    manualCurrentWeek,
    termStartDate,
    autoCurrentWeek,
    today
)

fun isBeforeScheduleTerm(
    config: ScheduleConfigEntity,
    today: LocalDate = LocalDate.now()
): Boolean = domainIsBeforeScheduleTerm(config, today)

fun scheduleTermEndDate(config: ScheduleConfigEntity): LocalDate? =
    domainScheduleTermEndDate(config)

fun isAfterScheduleTerm(
    config: ScheduleConfigEntity,
    today: LocalDate = LocalDate.now()
): Boolean = domainIsAfterScheduleTerm(config, today)

fun scheduleTermStatusLabel(config: ScheduleConfigEntity, date: LocalDate): String? =
    domainScheduleTermStatusLabel(config, date)

fun scheduleTermStatusDescription(config: ScheduleConfigEntity, date: LocalDate): String =
    domainScheduleTermStatusDescription(config, date)

fun scheduleWeekForDateOrNull(config: ScheduleConfigEntity, date: LocalDate): Int? =
    domainScheduleWeekForDateOrNull(config, date)

fun scheduleDayNavigationRange(
    config: ScheduleConfigEntity,
    today: LocalDate = LocalDate.now()
): ClosedRange<LocalDate>? = domainScheduleDayNavigationRange(config, today)

fun scheduleWeekStartDate(
    config: ScheduleConfigEntity,
    displayWeek: Int,
    today: LocalDate = LocalDate.now()
): LocalDate = domainScheduleWeekStartDate(config, displayWeek, today)

fun parseScheduleDate(value: String?): LocalDate? = domainParseScheduleDate(value)

fun formatScheduleDate(date: LocalDate): String = domainFormatScheduleDate(date)

fun DayOfWeek.toChineseWeekday(): Int = domainToChineseWeekday()

fun parityMatches(parity: WeekParity, week: Int): Boolean = domainParityMatches(parity, week)

fun courseStartTime(course: CourseEntity, periods: List<PeriodEntity>): LocalTime? =
    domainCourseStartTime(course, periods)

fun courseEndTime(course: CourseEntity, periods: List<PeriodEntity>): LocalTime? =
    domainCourseEndTime(course, periods)

fun courseTimeLabel(course: CourseEntity, periods: List<PeriodEntity>): String =
    domainCourseTimeLabel(course, periods)

fun CourseEntity.customTimeRangeOrNull(): Pair<LocalTime, LocalTime>? =
    domainCustomTimeRangeOrNull()

fun CourseEntity.hasCustomTime(): Boolean = domainHasCustomTime()

internal fun courseAllowsWeekPeriodDrag(course: CourseEntity): Boolean =
    domainCourseAllowsWeekPeriodDrag(course)

fun courseAnchorPeriodsForTimeRange(
    start: LocalTime,
    end: LocalTime,
    periods: List<PeriodEntity>
): List<Int> = domainCourseAnchorPeriodsForTimeRange(start, end, periods)

internal fun exactTimeWeekPlacement(
    course: CourseEntity,
    periods: List<PeriodEntity>
): WeekExactTimePlacement? = domainExactTimeWeekPlacement(course, periods)

fun parityLabel(parity: WeekParity): String = domainParityLabel(parity)
