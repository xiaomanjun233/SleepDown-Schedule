package com.xiaomanjun.sleepdownschedule.domain.schedule

import com.xiaomanjun.sleepdownschedule.model.*

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

fun todayCourses(state: AppState): List<CourseEntity> {
    val today = LocalDate.now()
    val weekday = today.dayOfWeek.toChineseWeekday()
    // Do not fold dates before/after the term into week 1/the final week. Widgets,
    // notifications and the live activity all consume this shared query.
    val currentWeek = scheduleWeekForDateOrNull(state.config, today) ?: return emptyList()
    return state.courses.filter { it.weekday == weekday && it.weeks.contains(currentWeek) && parityMatches(it.weekParity, currentWeek) }
        .sortedBy { courseStartTime(it, state.periods) ?: LocalTime.MAX }
}

/**
 * Resolves the period represented by the current point on the schedule timeline.
 * A normal break remains attached to the period that just started, up to the next
 * period's start. This keeps the marker stable for schedules whose stored end time
 * is intentionally short, while still hiding it before the first and after the last period.
 */
fun currentTimelinePeriod(periods: List<PeriodEntity>, now: LocalTime): PeriodEntity? {
    val timeline = periods.mapNotNull { period ->
        val start = runCatching { LocalTime.parse(period.startTime) }.getOrNull() ?: return@mapNotNull null
        val end = runCatching { LocalTime.parse(period.endTime) }.getOrNull() ?: return@mapNotNull null
        Triple(period, start, end)
    }.sortedBy { it.second }
    val index = timeline.indexOfLast { (_, start) -> !now.isBefore(start) }
    if (index < 0) return null
    val (period, _, end) = timeline[index]
    val nextStart = timeline.getOrNull(index + 1)?.second
    return if (now.isBefore(end) || (nextStart != null && now.isBefore(nextStart))) period else null
}

fun derivedScheduleTermState(
    config: ScheduleConfigEntity,
    today: LocalDate = LocalDate.now()
): ScheduleTermState {
    if (!config.autoCurrentWeek) return ScheduleTermState.MANUAL
    val startDate = parseScheduleDate(config.termStartDate) ?: return ScheduleTermState.INVALID
    if (today.isBefore(startDate)) return ScheduleTermState.UPCOMING
    val firstWeekMonday = startDate.minusDays((startDate.dayOfWeek.toChineseWeekday() - 1).toLong())
    val endDate = firstWeekMonday.plusWeeks(config.totalWeeks.coerceAtLeast(1).toLong()).minusDays(1)
    return if (today.isAfter(endDate)) ScheduleTermState.ENDED else ScheduleTermState.ACTIVE
}

internal fun ScheduleConfigEntity.withDerivedScheduleTermState(
    today: LocalDate = LocalDate.now()
): ScheduleConfigEntity {
    val derived = derivedScheduleTermState(this, today)
    return if (termState == derived) this else copy(termState = derived)
}
fun effectiveCurrentWeek(config: ScheduleConfigEntity, today: LocalDate = LocalDate.now()): Int {
    if (!config.autoCurrentWeek || config.termStartDate.isNullOrBlank()) return config.currentWeek.coerceIn(1, config.totalWeeks)
    val startDate = parseScheduleDate(config.termStartDate) ?: return config.currentWeek.coerceIn(1, config.totalWeeks)
    val start = startDate.minusDays((startDate.dayOfWeek.toChineseWeekday() - 1).toLong())
    val days = ChronoUnit.DAYS.between(start, today)
    val calculated = (Math.floorDiv(days, 7) + 1).toInt()
    return calculated.coerceIn(1, config.totalWeeks)
}

/**
 * Resolves the editable current-week fields through one rule shared by every settings surface.
 * In automatic mode the date is the only source of truth; otherwise the manual value is kept.
 */
fun resolveScheduleCurrentWeek(
    baseConfig: ScheduleConfigEntity,
    totalWeeks: Int,
    manualCurrentWeek: Int,
    termStartDate: String?,
    autoCurrentWeek: Boolean,
    today: LocalDate = LocalDate.now()
): Int {
    val safeTotal = totalWeeks.coerceIn(1, 60)
    val safeManual = manualCurrentWeek.coerceIn(1, safeTotal)
    if (!autoCurrentWeek || parseScheduleDate(termStartDate) == null) return safeManual
    val automaticConfig = baseConfig.copy(
        totalWeeks = safeTotal,
        currentWeek = safeManual,
        termStartDate = termStartDate?.trim()?.ifBlank { null },
        autoCurrentWeek = true
    )
    // currentWeek is persisted as the manual fallback. Before the opening date and
    // after the final teaching day there is no current teaching week to write back.
    return scheduleWeekForDateOrNull(automaticConfig, today)
        ?: safeManual
}

fun isBeforeScheduleTerm(config: ScheduleConfigEntity, today: LocalDate = LocalDate.now()): Boolean {
    if (!config.autoCurrentWeek || config.termStartDate.isNullOrBlank()) return false
    val startDate = parseScheduleDate(config.termStartDate) ?: return false
    return today.isBefore(startDate)
}

fun scheduleTermEndDate(config: ScheduleConfigEntity): LocalDate? {
    if (!config.autoCurrentWeek || config.termStartDate.isNullOrBlank()) return null
    val startDate = parseScheduleDate(config.termStartDate) ?: return null
    val firstWeekMonday = startDate.minusDays((startDate.dayOfWeek.toChineseWeekday() - 1).toLong())
    return firstWeekMonday.plusWeeks(config.totalWeeks.coerceAtLeast(1).toLong()).minusDays(1)
}

fun isAfterScheduleTerm(
    config: ScheduleConfigEntity,
    today: LocalDate = LocalDate.now()
): Boolean = scheduleTermEndDate(config)?.let(today::isAfter) == true

fun scheduleTermStatusLabel(config: ScheduleConfigEntity, date: LocalDate): String? = when (derivedScheduleTermState(config, date)) {
    ScheduleTermState.UPCOMING -> "暂未开学"
    ScheduleTermState.ENDED -> "学期已结束"
    ScheduleTermState.INVALID -> "学期日期无效"
    else -> null
}

fun scheduleTermStatusDescription(config: ScheduleConfigEntity, date: LocalDate): String =
    when (derivedScheduleTermState(config, date)) {
        ScheduleTermState.MANUAL -> "手动设置 · 第 ${config.currentWeek.coerceIn(1, config.totalWeeks.coerceAtLeast(1))} 周"
        ScheduleTermState.UPCOMING -> "暂未开学"
        ScheduleTermState.ACTIVE -> "进行中 · 第 ${effectiveCurrentWeek(config, date)} 周"
        ScheduleTermState.ENDED -> "学期已结束"
        ScheduleTermState.INVALID -> "学期日期无效"
    }

/** Returns null outside the actual teaching-term date range instead of folding into week 1/N. */
fun scheduleWeekForDateOrNull(config: ScheduleConfigEntity, date: LocalDate): Int? {
    if (!config.autoCurrentWeek || config.termStartDate.isNullOrBlank()) {
        return effectiveCurrentWeek(config, date)
    }
    val startDate = parseScheduleDate(config.termStartDate) ?: return effectiveCurrentWeek(config, date)
    val endDate = scheduleTermEndDate(config) ?: return effectiveCurrentWeek(config, date)
    if (date.isBefore(startDate) || date.isAfter(endDate)) return null
    val firstWeekMonday = startDate.minusDays((startDate.dayOfWeek.toChineseWeekday() - 1).toLong())
    return (ChronoUnit.DAYS.between(firstWeekMonday, date) / 7L + 1L).toInt()
        .takeIf { it in 1..config.totalWeeks.coerceAtLeast(1) }
}

fun scheduleDayNavigationRange(
    config: ScheduleConfigEntity,
    today: LocalDate = LocalDate.now()
): ClosedRange<LocalDate>? {
    if (!config.autoCurrentWeek || config.termStartDate.isNullOrBlank()) return null
    val start = parseScheduleDate(config.termStartDate) ?: return null
    val end = scheduleTermEndDate(config) ?: return null
    // Before/after the semester the current date remains a useful empty landing page, but users
    // cannot keep paging farther away and accidentally expose a clamped first/last teaching week.
    return minOf(today, start)..maxOf(today, end)
}

fun scheduleWeekStartDate(
    config: ScheduleConfigEntity,
    displayWeek: Int,
    today: LocalDate = LocalDate.now()
): LocalDate {
    val safeWeek = displayWeek.coerceAtLeast(1)
    val termStart = if (config.autoCurrentWeek) parseScheduleDate(config.termStartDate) else null
    if (termStart != null) {
        val termWeekStart = termStart.minusDays((termStart.dayOfWeek.toChineseWeekday() - 1).toLong())
        return termWeekStart.plusWeeks((safeWeek - 1).toLong())
    }
    val currentWeek = effectiveCurrentWeek(config, today)
    return today
        .minusDays((today.dayOfWeek.toChineseWeekday() - 1).toLong())
        .plusWeeks((safeWeek - currentWeek).toLong())
}

fun parseScheduleDate(value: String?): LocalDate? {
    val text = value?.trim().orEmpty()
    if (text.isBlank()) return null
    val normalized = text.replace('.', '-').replace('/', '-')
    return runCatching { LocalDate.parse(normalized) }.getOrNull()
}

fun formatScheduleDate(date: LocalDate): String {
    return DateTimeFormatter.ofPattern("yyyy.MM.dd").format(date)
}

fun DayOfWeek.toChineseWeekday(): Int = when (this) {
    DayOfWeek.MONDAY -> 1
    DayOfWeek.TUESDAY -> 2
    DayOfWeek.WEDNESDAY -> 3
    DayOfWeek.THURSDAY -> 4
    DayOfWeek.FRIDAY -> 5
    DayOfWeek.SATURDAY -> 6
    DayOfWeek.SUNDAY -> 7
}

fun parityMatches(parity: WeekParity, week: Int): Boolean = when (parity) {
    WeekParity.ALL -> true
    WeekParity.ODD -> week % 2 == 1
    WeekParity.EVEN -> week % 2 == 0
}

fun courseStartTime(course: CourseEntity, periods: List<PeriodEntity>): LocalTime? {
    course.customTimeRangeOrNull()?.let { return it.first }
    val first = course.periods.minOrNull() ?: return null
    return periods.firstOrNull { it.periodIndex == first }?.startTime?.let {
        runCatching { LocalTime.parse(it) }.getOrNull()
    }
}

fun courseEndTime(course: CourseEntity, periods: List<PeriodEntity>): LocalTime? {
    course.customTimeRangeOrNull()?.let { return it.second }
    val last = course.periods.maxOrNull() ?: return null
    return periods.firstOrNull { it.periodIndex == last }?.endTime?.let {
        runCatching { LocalTime.parse(it) }.getOrNull()
    }
}

fun courseTimeLabel(course: CourseEntity, periods: List<PeriodEntity>): String {
    course.customTimeRangeOrNull()?.let { (start, end) ->
        return start.toString() + " - " + end.toString()
    }
    val first = course.periods.minOrNull()
    val last = course.periods.maxOrNull()
    val start = periods.firstOrNull { it.periodIndex == first }?.startTime ?: "--:--"
    val end = periods.firstOrNull { it.periodIndex == last }?.endTime ?: "--:--"
    return start + " - " + end
}

fun CourseEntity.customTimeRangeOrNull(): Pair<LocalTime, LocalTime>? {
    val start = customStartTime?.let { runCatching { LocalTime.parse(it) }.getOrNull() } ?: return null
    val end = customEndTime?.let { runCatching { LocalTime.parse(it) }.getOrNull() } ?: return null
    return (start to end).takeIf { end.isAfter(start) }
}

fun CourseEntity.hasCustomTime(): Boolean = customTimeRangeOrNull() != null

internal fun courseAllowsWeekPeriodDrag(course: CourseEntity): Boolean = !course.hasCustomTime()

/**
 * Keeps exact-time courses compatible with the existing period-backed grid and notifications.
 * Every period touched by the exact interval becomes an anchor; if the interval sits completely
 * inside a break, the nearest period start is used as a stable fallback.
 */
fun courseAnchorPeriodsForTimeRange(
    start: LocalTime,
    end: LocalTime,
    periods: List<PeriodEntity>
): List<Int> {
    val parsed = periods.mapNotNull { period ->
        val periodStart = runCatching { LocalTime.parse(period.startTime) }.getOrNull() ?: return@mapNotNull null
        val periodEnd = runCatching { LocalTime.parse(period.endTime) }.getOrNull() ?: return@mapNotNull null
        Triple(period.periodIndex, periodStart, periodEnd)
    }.sortedBy { it.first }
    val overlaps = parsed.filter { (_, periodStart, periodEnd) -> periodStart < end && periodEnd > start }
        .map { it.first }
    if (overlaps.isNotEmpty()) return overlaps
    val startMinute = start.hour * 60 + start.minute
    return parsed.minByOrNull { (_, periodStart, _) ->
        kotlin.math.abs(periodStart.hour * 60 + periodStart.minute - startMinute)
    }?.let { listOf(it.first) }.orEmpty()
}

data class WeekExactTimePlacement(
    val topRows: Float,
    val heightRows: Float
)

internal fun exactTimeWeekPlacement(
    course: CourseEntity,
    periods: List<PeriodEntity>
): WeekExactTimePlacement? {
    val (courseStart, courseEnd) = course.customTimeRangeOrNull() ?: return null
    val parsed = periods.sortedBy { it.periodIndex }.mapNotNull { period ->
        val start = runCatching { LocalTime.parse(period.startTime) }.getOrNull() ?: return@mapNotNull null
        val end = runCatching { LocalTime.parse(period.endTime) }.getOrNull() ?: return@mapNotNull null
        Triple(period.periodIndex, start, end)
    }
    if (parsed.isEmpty()) return null

    fun position(time: LocalTime): Float {
        if (!time.isAfter(parsed.first().second)) return 0f
        parsed.forEachIndexed { index, (_, rowStart, rowEnd) ->
            if (!time.isAfter(rowEnd)) {
                val rowMinutes = java.time.Duration.between(rowStart, rowEnd).toMinutes().coerceAtLeast(1L)
                val elapsed = java.time.Duration.between(rowStart, time).toMinutes()
                return index + (elapsed.toFloat() / rowMinutes.toFloat()).coerceIn(0f, 1f)
            }
            val nextStart = parsed.getOrNull(index + 1)?.second
            if (nextStart == null || time.isBefore(nextStart)) {
                // Breaks have no dedicated grid height. Collapse them onto the boundary between
                // two period rows while keeping every in-period minute continuously mappable.
                return (index + 1).toFloat()
            }
        }
        return parsed.size.toFloat()
    }

    val top = position(courseStart).coerceIn(0f, parsed.size.toFloat())
    val bottom = position(courseEnd).coerceIn(top, parsed.size.toFloat())
    return WeekExactTimePlacement(
        topRows = top,
        heightRows = (bottom - top).coerceAtLeast(0f)
    )
}

fun parityLabel(parity: WeekParity): String = when (parity) {
    WeekParity.ALL -> "每周"
    WeekParity.ODD -> "单周"
    WeekParity.EVEN -> "双周"
}
