package com.xiaomanjun.sleepdownschedule.domain.schedule

import com.xiaomanjun.sleepdownschedule.model.*
import java.time.LocalDate
import java.time.LocalTime

data class ResolvedTimetable(val periods: List<PeriodEntity>, val reviewReason: String? = null)
data class ResolvedCourseOccurrence(
    val source: CourseEntity, val date: LocalDate, val segments: List<CoursePeriodTime>, val reviewReason: String? = null
)

/** The base grid is stable; date rules select actual bells without changing that grid. */
fun resolveTimetable(config: ScheduleConfigEntity, base: List<PeriodEntity>, catalog: TimetableCatalog,
    date: LocalDate, independentSchemeId: Long? = null): ResolvedTimetable {
    fun times(id: Long) = catalog.times.filter { it.schemeId == id }.sortedBy { it.periodIndex }
        .map { PeriodEntity(it.periodIndex, it.startTime, it.endTime, config.id, it.alias) }
    if (independentSchemeId != null) {
        val selected = times(independentSchemeId)
        return ResolvedTimetable(selected, "指定作息缺少时间，请重新选择作息".takeIf { selected.isEmpty() })
    }
    val comboId = config.activeTimetableComboId ?: return ResolvedTimetable(base)
    val combo = catalog.combos.firstOrNull { it.id == comboId }
        ?: return ResolvedTimetable(emptyList(), "组合作息已不存在，请重新选择作息")
    val baseline = times(combo.baseSchemeId)
    if(baseline.isEmpty()) return ResolvedTimetable(emptyList(),"组合作息基准缺少时间，请重新选择作息")
    val target = catalog.rules.filter { it.comboId == comboId }.sortedBy { it.position }
        .firstOrNull { date.toString() in it.startDate..it.endDate } ?: return ResolvedTimetable(baseline)
    val replacement = times(target.schemeId).associateBy { it.periodIndex }
    if (replacement.isEmpty()) return ResolvedTimetable(emptyList(), "日期作息缺少时间，请检查组合作息")
    return ResolvedTimetable(baseline.map { replacement[it.periodIndex] ?: it })
}

/**
 * Selects the baseline timetable for a browsed week so multi-week seasonal schedules display
 * cleanly on the grid, while single-day exceptions still stand out against the rest of the week.
 */
fun resolveWeekTimetable(
    config: ScheduleConfigEntity,
    base: List<PeriodEntity>,
    catalog: TimetableCatalog,
    week: Int,
    today: LocalDate = LocalDate.now(),
): ResolvedTimetable {
    if (config.activeTimetableComboId == null) return ResolvedTimetable(base)
    val start = scheduleWeekStartDate(config, week, today)
    val candidates = (0L..6L).map { offset ->
        val date = start.plusDays(offset)
        date to resolveTimetable(config, base, catalog, date)
    }
    fun signature(periods: List<PeriodEntity>) = periods.map {
        Triple(it.periodIndex, it.startTime, it.endTime)
    }
    val valid = candidates.filter { it.second.periods.isNotEmpty() }
    if (valid.isEmpty()) return candidates.first().second
    val counts = valid.groupingBy { signature(it.second.periods) }.eachCount()
    val maxCount = counts.values.maxOrNull() ?: return valid.first().second
    val todaySignature = candidates.firstOrNull { it.first == today }
        ?.second?.periods?.takeIf { it.isNotEmpty() }?.let(::signature)
    val chosenSignature = counts.entries
        .filter { it.value == maxCount }
        .maxWithOrNull(compareBy<Map.Entry<List<Triple<Int, String, String>>, Int>> {
            it.key == todaySignature
        }.thenBy { entry ->
            -valid.indexOfFirst { signature(it.second.periods) == entry.key }
        })?.key
        ?: return valid.first().second
    return valid.first { signature(it.second.periods) == chosenSignature }.second
}

/** Actual intervals are resolved once, independently of display positions and data provenance. */
fun resolveCourseOccurrence(course: CourseEntity, config: ScheduleConfigEntity, base: List<PeriodEntity>,
    catalog: TimetableCatalog = TimetableCatalog(), date: LocalDate = LocalDate.now()): ResolvedCourseOccurrence {
    val source = course.arrangementForWrite()
    source.timingReviewReason?.let { return ResolvedCourseOccurrence(source, date, emptyList(), it) }
    return runCatching {
        val overrides = parseCoursePeriodTimes(source.customPeriodTimes).associateBy { it.index }
        if (overrides.isEmpty() && source.customStartTime != null && source.customEndTime != null) {
            val start = LocalTime.parse(source.customStartTime)
            val end = LocalTime.parse(source.customEndTime)
            require(start < end) { "课程结束时间须晚于开始时间" }
            return@runCatching ResolvedCourseOccurrence(source, date,
                listOf(CoursePeriodTime(source.periods.firstOrNull() ?: 0, start, end)))
        }
        val selected = resolveTimetable(config, base, catalog, date, source.independentPeriodSchemeId)
        val definitions = selected.periods.associateBy { it.periodIndex }
        val numbers = source.periods.distinct().sorted()
        require(numbers.isNotEmpty()) { "课程没有节次或真实时间，请补充" }
        val missing = mutableListOf<Int>()
        val actual = numbers.mapNotNull { number -> overrides[number] ?: run {
            val bell = definitions[number]
            if (bell == null) { missing += number; null }
            else CoursePeriodTime(number, LocalTime.parse(bell.startTime), LocalTime.parse(bell.endTime))
        } }
        require(actual.all { it.start < it.end }) { "课程真实时间无效" }
        ResolvedCourseOccurrence(source, date, actual.sortedBy { it.start },selected.reviewReason ?:
            "第 ${missing.joinToString("、")} 节没有对应作息，请确认课程时间".takeIf { missing.isNotEmpty() })
    }.getOrElse { ResolvedCourseOccurrence(source, date, emptyList(), it.message ?: "课程时间需要确认") }
}

/** A display result references its unchanged source. Source lesson numbers never become grid indices. */
fun projectWeekGrid(occurrence: ResolvedCourseOccurrence, grid: List<PeriodEntity>): EffectiveCourseArrangement {
    val positions = linkedSetOf<Int>()
    val times = occurrence.segments.map { segment ->
        val matching = matchingPeriodIndicesForTimeRange(segment.start, segment.end, grid)
        val ordinary = matching.isNotEmpty()
        positions += matching.ifEmpty { courseAnchorPeriodsForTimeRange(segment.start, segment.end, grid) }
        AlignedCourseTime(segment, !ordinary)
    }
    val exact = times.any { it.exact }
    return EffectiveCourseArrangement(occurrence.source, positions.sorted(), times,
        if (occurrence.reviewReason == null) emptyList() else occurrence.source.periods.filter { number -> occurrence.segments.none { it.index==number } }, PeriodAlignmentMode.INDEX,
        displayStart = occurrence.segments.minOfOrNull { it.start }?.toString()?.takeIf { exact },
        displayEnd = occurrence.segments.maxOfOrNull { it.end }?.toString()?.takeIf { exact },
        displayPeriodTimes = null, reviewReason = occurrence.reviewReason)
}

fun CourseEntity.displayPeriodIndices(): List<Int> = effectiveArrangementOrNull()?.periods ?: periods

/** Published TIME alignment meant original clocks; preserve that intent when retiring the mode. */
fun freezeLegacyTimeAlignment(course: CourseEntity): CourseEntity {
    if (course.customStartTime != null || !course.customPeriodTimes.isNullOrBlank()) return course
    return runCatching {
        val original = parseCoursePeriodTimes(course.originalPeriodTimes)
        require(original.isNotEmpty() && original.map { it.index } == course.periods.distinct().sorted())
        course.copy(customPeriodTimes = course.originalPeriodTimes)
    }.getOrElse { course.copy(timingReviewReason = "原始时间不完整，请确认课程时间") }
}

/** A stable axis across week swipes, including seasonal bells and makeup dates. */
fun scheduleTimeAxisIntervals(state: AppState, today: LocalDate = LocalDate.now()): List<Pair<LocalTime, LocalTime>> {
    val intervals = state.courses.flatMap { course ->
        course.weeks.filter { it in 1..state.config.totalWeeks && parityMatches(course.weekParity, it) }.flatMap { week ->
            val date = scheduleWeekStartDate(state.config, week, today)
                .plusDays(scheduleWeekdayOffset(state.config, course.weekday))
            resolveCourseOccurrence(course, state.config, state.periods, state.timetableCatalog, date).segments
                .map { it.start to it.end }
        }
    }
    val adjusted = decodeScheduleAdjustments(state.config.scheduleAdjustmentsJson).flatMap { adjustment ->
        coursesForDate(state, LocalDate.parse(adjustment.date)).flatMap { course ->
            courseTimeSegments(course, state.periods).map { it.start to it.end }
        }
    }
    return (intervals + adjusted).distinct()
}
